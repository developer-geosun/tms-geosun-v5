package com.geosun.tms.enterprise.service;

import com.geosun.tms.auth.domain.EmailNormalizer;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.response.LogoutResponse;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.auth.security.config.JwtProperties;
import com.geosun.tms.auth.security.crypto.OpaqueTokenGenerator;
import com.geosun.tms.auth.security.crypto.TokenHasher;
import com.geosun.tms.auth.security.jwt.JwtService;
import com.geosun.tms.enterprise.domain.PlatformOperator;
import com.geosun.tms.enterprise.domain.PlatformOperatorRefreshToken;
import com.geosun.tms.enterprise.dto.PlatformAuthTokensResponse;
import com.geosun.tms.enterprise.dto.PlatformLoginRequest;
import com.geosun.tms.enterprise.dto.PlatformOperatorDto;
import com.geosun.tms.enterprise.repository.PlatformOperatorRefreshTokenRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import com.geosun.tms.enterprise.security.PlatformOperatorPrincipal;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Вхід, refresh і вихід оператора платформи. Окремі токени від співробітників. */
@Service
public class PlatformAuthService {

  private static final Logger log = LoggerFactory.getLogger(PlatformAuthService.class);

  /** Префікс ключа rate limit, щоб не змішувати з email співробітників. */
  private static final String RATE_KEY_PREFIX = "platform:";

  private final PlatformOperatorRepository operatorRepository;
  private final PlatformOperatorRefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final RateLimitService rateLimitService;

  public PlatformAuthService(
      PlatformOperatorRepository operatorRepository,
      PlatformOperatorRefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtProperties jwtProperties,
      RateLimitService rateLimitService) {
    this.operatorRepository = operatorRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.rateLimitService = rateLimitService;
  }

  @Transactional
  public PlatformAuthTokensResponse login(PlatformLoginRequest request, String clientIp) {
    String email = EmailNormalizer.normalize(request.email());
    String rateKey = RATE_KEY_PREFIX + email;
    rateLimitService.checkLoginNotBlocked(clientIp, rateKey);

    PlatformOperator operator = operatorRepository.findByEmail(email).orElse(null);
    if (operator == null
        || !passwordEncoder.matches(request.password(), operator.getPasswordHash())) {
      rateLimitService.recordLoginFailure(clientIp, rateKey);
      throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials");
    }
    rateLimitService.clearLoginFailures(clientIp, rateKey);

    if (!operator.isActive()) {
      throw ApiException.forbidden("ACCOUNT_DISABLED", "Account is disabled");
    }
    return issueTokens(operator);
  }

  /** Без rollback на ApiException: відкликання сесій при reuse має зберегтися. */
  @Transactional(noRollbackFor = ApiException.class)
  public PlatformAuthTokensResponse refresh(RefreshRequest request, String clientIp) {
    rateLimitService.checkRefresh(clientIp);
    String raw = request.refreshToken() != null ? request.refreshToken().trim() : "";
    if (raw.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Refresh token is required");
    }
    PlatformOperatorRefreshToken current =
        refreshTokenRepository
            .findByTokenHashWithOperator(TokenHasher.sha256Hex(raw))
            .orElseThrow(
                () -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid refresh token"));
    PlatformOperator operator = current.getOperator();

    if (current.getRevokedAt() != null) {
      // Повторне використання відкликаного токена: закриваємо всі сесії оператора
      log.warn("Possible platform refresh token reuse for operator {}", operator.getId());
      refreshTokenRepository.revokeAllActiveByOperatorId(operator.getId(), Instant.now());
      throw ApiException.unauthorized("INVALID_SESSION", "Invalid refresh token");
    }
    if (!current.getExpiresAt().isAfter(Instant.now())) {
      throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid refresh token");
    }
    if (!operator.isActive()) {
      throw ApiException.forbidden("ACCOUNT_DISABLED", "Account is disabled");
    }

    String rawNext = OpaqueTokenGenerator.generate();
    PlatformOperatorRefreshToken next = newRefreshToken(operator, rawNext);
    refreshTokenRepository.save(Objects.requireNonNull(next));
    refreshTokenRepository.flush();

    current.setRevokedAt(Instant.now());
    current.setReplacedBy(next);
    refreshTokenRepository.save(current);

    return toResponse(operator, next, rawNext);
  }

  @Transactional
  public LogoutResponse logout(@NonNull PlatformOperatorPrincipal principal) {
    PlatformOperatorRefreshToken session =
        refreshTokenRepository
            .findByIdWithOperator(Objects.requireNonNull(principal.getRefreshSessionId()))
            .orElseThrow(
                () ->
                    ApiException.unauthorized(
                        "INVALID_SESSION", "Refresh session is invalid or expired"));
    if (!session.getOperator().getId().equals(principal.getOperatorId())
        || session.getRevokedAt() != null) {
      throw ApiException.unauthorized("INVALID_SESSION", "Refresh session is invalid or expired");
    }
    session.setRevokedAt(Instant.now());
    refreshTokenRepository.save(session);
    return new LogoutResponse(true, "Logged out successfully");
  }

  @Transactional(readOnly = true)
  public PlatformOperatorDto me(@NonNull PlatformOperatorPrincipal principal) {
    PlatformOperator operator =
        operatorRepository
            .findById(principal.getOperatorId())
            .orElseThrow(() -> ApiException.notFound("Operator not found"));
    return toDto(operator);
  }

  private PlatformAuthTokensResponse issueTokens(PlatformOperator operator) {
    String raw = OpaqueTokenGenerator.generate();
    PlatformOperatorRefreshToken refresh = newRefreshToken(operator, raw);
    refreshTokenRepository.save(Objects.requireNonNull(refresh));
    refreshTokenRepository.flush();
    return toResponse(operator, refresh, raw);
  }

  /** Створює сесію; у БД зберігається лише хеш сирого токена. */
  private PlatformOperatorRefreshToken newRefreshToken(PlatformOperator operator, String raw) {
    PlatformOperatorRefreshToken token = new PlatformOperatorRefreshToken();
    token.setOperator(operator);
    token.setTokenHash(TokenHasher.sha256Hex(raw));
    token.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshExpiresSeconds()));
    return token;
  }

  private PlatformAuthTokensResponse toResponse(
      PlatformOperator operator, PlatformOperatorRefreshToken session, String rawRefresh) {
    String access = jwtService.createOperatorAccessToken(operator.getId(), session.getId());
    return new PlatformAuthTokensResponse(
        access, rawRefresh, "Bearer", jwtProperties.getExpiresSeconds(), toDto(operator));
  }

  private static PlatformOperatorDto toDto(PlatformOperator operator) {
    return new PlatformOperatorDto(operator.getId(), operator.getEmail());
  }
}
