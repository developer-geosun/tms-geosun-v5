package com.geosun.tms.auth.service;

import com.geosun.tms.auth.config.AppClient;
import com.geosun.tms.auth.config.AppEmailProperties;
import com.geosun.tms.auth.domain.EmailNormalizer;
import com.geosun.tms.auth.domain.token.EmailVerificationToken;
import com.geosun.tms.auth.domain.token.PasswordResetToken;
import com.geosun.tms.auth.domain.token.RefreshToken;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.mapper.UserDtoMapper;
import com.geosun.tms.auth.dto.request.ForgotPasswordRequest;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.dto.request.PasswordResetInfoRequest;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.request.RegisterRequest;
import com.geosun.tms.auth.dto.request.ResendVerificationRequest;
import com.geosun.tms.auth.dto.request.ResetPasswordRequest;
import com.geosun.tms.auth.dto.request.VerifyEmailRequest;
import com.geosun.tms.auth.dto.response.AuthTokensResponse;
import com.geosun.tms.auth.dto.response.LogoutResponse;
import com.geosun.tms.auth.dto.response.OperationSuccessResponse;
import com.geosun.tms.auth.dto.response.PasswordResetInfoResponse;
import com.geosun.tms.auth.dto.response.RegisterResponse;
import com.geosun.tms.auth.dto.response.UserPublicDto;
import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.mail.PasswordResetMailSender;
import com.geosun.tms.auth.mail.VerificationMailSender;
import com.geosun.tms.auth.notify.AdminNewUserNotifier;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.auth.repository.EmailVerificationTokenRepository;
import com.geosun.tms.auth.repository.PasswordResetTokenRepository;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.security.UserPrincipal;
import com.geosun.tms.auth.security.config.JwtProperties;
import com.geosun.tms.auth.security.crypto.OpaqueTokenGenerator;
import com.geosun.tms.auth.security.crypto.TokenHasher;
import com.geosun.tms.auth.security.jwt.JwtService;
import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.domain.EnterpriseCodeRules;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Реєстрація, вхід, верифікація email, скидання пароля, refresh/logout та профіль.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final AppEmailProperties appEmailProperties;
  private final VerificationMailSender verificationMailSender;
  private final PasswordResetMailSender passwordResetMailSender;
  private final RateLimitService rateLimitService;
  private final UserProfileService userProfileService;
  private final AdminNewUserNotifier adminNewUserNotifier;
  private final EnterpriseRepository enterpriseRepository;
  private final PlatformOperatorRepository platformOperatorRepository;

  public AuthService(
      UserRepository userRepository,
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtProperties jwtProperties,
      AppEmailProperties appEmailProperties,
      VerificationMailSender verificationMailSender,
      PasswordResetMailSender passwordResetMailSender,
      RateLimitService rateLimitService,
      UserProfileService userProfileService,
      AdminNewUserNotifier adminNewUserNotifier,
      EnterpriseRepository enterpriseRepository,
      PlatformOperatorRepository platformOperatorRepository) {
    this.userRepository = userRepository;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.appEmailProperties = appEmailProperties;
    this.verificationMailSender = verificationMailSender;
    this.passwordResetMailSender = passwordResetMailSender;
    this.rateLimitService = rateLimitService;
    this.userProfileService = userProfileService;
    this.adminNewUserNotifier = adminNewUserNotifier;
    this.enterpriseRepository = enterpriseRepository;
    this.platformOperatorRepository = platformOperatorRepository;
  }

  /** Підприємство за кодом із запиту (нормалізація за {@link EnterpriseCodeRules}) або null. */
  private Enterprise findEnterprise(String rawCode) {
    String code = EnterpriseCodeRules.normalize(rawCode);
    if (code == null) {
      return null;
    }
    return enterpriseRepository.findByCode(code).orElse(null);
  }

  /** Ключ rate limit: код підприємства + email, щоб підприємства не блокували одне одного. */
  private static String loginRateKey(String rawCode, String email) {
    String code = rawCode == null ? "" : rawCode.trim().toLowerCase(java.util.Locale.ROOT);
    return code + "|" + email;
  }

  @Transactional
  public RegisterResponse register(
      RegisterRequest request, String clientIp, @NonNull AppClient appClient) {
    rateLimitService.checkRegister(clientIp);
    String email = EmailNormalizer.normalize(request.email());
    Enterprise enterprise = findEnterprise(request.enterpriseCode());
    if (enterprise == null || !enterprise.isActive()) {
      throw ApiException.badRequest("ENTERPRISE_UNAVAILABLE", "Enterprise is unavailable");
    }
    String enterpriseId = Objects.requireNonNull(enterprise.getId());
    if (platformOperatorRepository.existsByEmail(email)
        || userRepository.existsByEnterpriseIdAndEmailAndDeletedFalse(enterpriseId, email)) {
      throw ApiException.conflict("Email is already registered");
    }

    User user = new User();
    user.setEnterpriseId(enterpriseId);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRole(Role.USER);
    user.replaceAvailableRoles(Objects.requireNonNull(EnumSet.of(Role.USER)));
    user.setEmailVerified(false);
    userRepository.save(user);

    String rawVerification = OpaqueTokenGenerator.generate();
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setTokenHash(TokenHasher.sha256Hex(rawVerification));
    token.setExpiresAt(
        Instant.now().plusSeconds(appEmailProperties.getVerificationExpiresSeconds()));
    emailVerificationTokenRepository.save(token);

    try {
      verificationMailSender.sendVerificationEmail(
          email, rawVerification, Objects.requireNonNull(appClient));
    } catch (MailException ex) {
      log.error("Failed to send verification email after registration");
    }

    scheduleAdminNotifyAfterCommit(user);

    return UserDtoMapper.toRegisterResponse(user);
  }

  /** Сповіщення ADMIN лише після успішного commit реєстрації. */
  private void scheduleAdminNotifyAfterCommit(@NonNull User user) {
    String newUserId = Objects.requireNonNull(user.getId());
    String newUserEmail = Objects.requireNonNull(user.getEmail());
    Instant createdAt =
        Objects.requireNonNull(user.getCreatedAt() != null ? user.getCreatedAt() : Instant.now());
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      adminNewUserNotifier.notifyAdminsAsync(newUserId, newUserEmail, createdAt);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            adminNewUserNotifier.notifyAdminsAsync(newUserId, newUserEmail, createdAt);
          }
        });
  }

  @Transactional
  public AuthTokensResponse login(LoginRequest request, String clientIp) {
    String email = EmailNormalizer.normalize(request.email());
    String rateKey = loginRateKey(request.enterpriseCode(), email);
    rateLimitService.checkLoginNotBlocked(clientIp, rateKey);

    // Невідоме підприємство і невірна пара не розрізняються (anti-enumeration)
    Enterprise enterprise = findEnterprise(request.enterpriseCode());
    User user =
        enterprise == null
            ? null
            : userRepository
                .findTopByEnterpriseIdAndEmailOrderByDeletedAsc(
                    Objects.requireNonNull(enterprise.getId()), email)
                .orElse(null);
    if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      rateLimitService.recordLoginFailure(clientIp, rateKey);
      throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials");
    }

    rateLimitService.clearLoginFailures(clientIp, rateKey);

    // Пароль вірний, тож статус підприємства можна повідомити
    if (!Objects.requireNonNull(enterprise).isActive()) {
      throw ApiException.forbidden("ENTERPRISE_SUSPENDED", "Enterprise is suspended");
    }

    if (user.isDeleted()) {
      throw ApiException.forbidden("USER_DELETED", "User account has been deleted");
    }
    if (!user.isActive()) {
      throw ApiException.forbidden("ACCOUNT_DISABLED", "Account is disabled");
    }
    if (!user.isEmailVerified()) {
      throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Email is not verified");
    }

    return issueTokens(user);
  }

  @Transactional
  public OperationSuccessResponse verifyEmail(VerifyEmailRequest request) {
    String raw = request.token() != null ? request.token().trim() : "";
    if (raw.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Token is required");
    }
    String hash = TokenHasher.sha256Hex(raw);
    EmailVerificationToken token =
        emailVerificationTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "INVALID_TOKEN", "Invalid or expired verification token"));

    if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
      throw ApiException.badRequest("INVALID_TOKEN", "Invalid or expired verification token");
    }

    User user = token.getUser();
    user.setEmailVerified(true);
    user.setEmailVerifiedAt(Instant.now());
    token.setUsedAt(Instant.now());
    userRepository.save(user);
    emailVerificationTokenRepository.save(token);

    return new OperationSuccessResponse(true, "Email verified successfully");
  }

  @Transactional
  public OperationSuccessResponse resendVerification(
      ResendVerificationRequest request, @NonNull AppClient appClient) {
    String email = EmailNormalizer.normalize(request.email());
    rateLimitService.checkResend(email);

    Enterprise enterprise = findEnterprise(request.enterpriseCode());
    User user =
        enterprise == null || !enterprise.isActive()
            ? null
            : userRepository
                .findByEnterpriseIdAndEmailAndDeletedFalse(
                    Objects.requireNonNull(enterprise.getId()), email)
                .orElse(null);
    if (user == null || user.isEmailVerified()) {
      return new OperationSuccessResponse(true, "Verification email sent");
    }

    emailVerificationTokenRepository.deletePendingByUserId(user.getId());
    emailVerificationTokenRepository.flush();

    String raw = OpaqueTokenGenerator.generate();
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setTokenHash(TokenHasher.sha256Hex(raw));
    token.setExpiresAt(
        Instant.now().plusSeconds(appEmailProperties.getVerificationExpiresSeconds()));
    emailVerificationTokenRepository.save(token);

    try {
      verificationMailSender.sendVerificationEmail(email, raw, Objects.requireNonNull(appClient));
    } catch (MailException ex) {
      log.error("Failed to resend verification email");
      throw ApiException.serviceUnavailable("EMAIL_DELIVERY_FAILED", "Email delivery failed");
    }

    return new OperationSuccessResponse(true, "Verification email sent");
  }

  @Transactional
  public OperationSuccessResponse forgotPassword(
      ForgotPasswordRequest request, @NonNull AppClient appClient) {
    String email = EmailNormalizer.normalize(request.email());
    rateLimitService.checkForgotPassword(email);

    // Знаходимо і невидалені, і soft-deleted — щоб відрізнити USER_DELETED від «немає акаунта».
    Enterprise enterprise = findEnterprise(request.enterpriseCode());
    User user =
        enterprise == null || !enterprise.isActive()
            ? null
            : userRepository
                .findTopByEnterpriseIdAndEmailOrderByDeletedAsc(
                    Objects.requireNonNull(enterprise.getId()), email)
                .orElse(null);
    if (user == null) {
      // Anti-enumeration: невідомий email або підприємство — той самий успішний ответ.
      return new OperationSuccessResponse(true, "Password reset email sent");
    }
    if (user.isDeleted()) {
      throw ApiException.forbidden("USER_DELETED", "User account has been deleted");
    }
    if (!user.isActive()) {
      throw ApiException.forbidden("ACCOUNT_DISABLED", "Account is disabled");
    }
    if (!user.isEmailVerified()) {
      throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Email is not verified");
    }

    passwordResetTokenRepository.deletePendingByUserId(user.getId());
    passwordResetTokenRepository.flush();

    String raw = OpaqueTokenGenerator.generate();
    PasswordResetToken token = new PasswordResetToken();
    token.setUser(user);
    token.setTokenHash(TokenHasher.sha256Hex(raw));
    token.setExpiresAt(
        Instant.now().plusSeconds(appEmailProperties.getPasswordResetExpiresSeconds()));
    passwordResetTokenRepository.save(token);

    try {
      passwordResetMailSender.sendPasswordResetEmail(email, raw, Objects.requireNonNull(appClient));
    } catch (MailException ex) {
      log.error("Failed to send password reset email");
    }

    return new OperationSuccessResponse(true, "Password reset email sent");
  }

  @Transactional(readOnly = true)
  public PasswordResetInfoResponse passwordResetInfo(PasswordResetInfoRequest request) {
    PasswordResetToken token = requireValidPasswordResetToken(request.token());
    return new PasswordResetInfoResponse(token.getUser().getEmail());
  }

  @Transactional
  public OperationSuccessResponse resetPassword(ResetPasswordRequest request) {
    PasswordResetToken token = requireValidPasswordResetToken(request.token());
    User user = token.getUser();

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    token.setUsedAt(Instant.now());
    userRepository.save(user);
    passwordResetTokenRepository.save(token);

    passwordResetTokenRepository.deletePendingByUserId(user.getId());
    refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());

    return new OperationSuccessResponse(true, "Password reset successfully");
  }

  private PasswordResetToken requireValidPasswordResetToken(String rawToken) {
    String raw = rawToken != null ? rawToken.trim() : "";
    if (raw.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Token is required");
    }
    String hash = TokenHasher.sha256Hex(raw);
    PasswordResetToken token =
        passwordResetTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "INVALID_TOKEN", "Invalid or expired password reset token"));

    if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
      throw ApiException.badRequest("INVALID_TOKEN", "Invalid or expired password reset token");
    }

    User user = token.getUser();
    if (user.isDeleted() || !user.isActive()) {
      throw ApiException.badRequest("INVALID_TOKEN", "Invalid or expired password reset token");
    }
    return token;
  }

  @Transactional
  public AuthTokensResponse refresh(RefreshRequest request, String clientIp) {
    rateLimitService.checkRefresh(clientIp);
    String raw = request.refreshToken() != null ? request.refreshToken().trim() : "";
    if (raw.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_ERROR", "Refresh token is required");
    }
    String hash = TokenHasher.sha256Hex(raw);
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHashWithUser(hash)
            .orElseThrow(
                () -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid refresh token"));

    User user = current.getUser();

    if (current.getRevokedAt() != null) {
      RefreshToken successor = current.getReplacedBy();
      if (isWithinReuseGrace_(current) && isValidGraceSuccessor_(successor, user)) {
        return rotate(Objects.requireNonNull(successor), user);
      }
      log.warn("Possible refresh token reuse for user {}", user.getId());
      refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
      throw ApiException.unauthorized("INVALID_SESSION", "Invalid refresh token");
    }

    if (!current.getExpiresAt().isAfter(Instant.now())) {
      throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid refresh token");
    }

    if (user.isDeleted()) {
      throw ApiException.forbidden("USER_DELETED", "User account has been deleted");
    }
    if (!user.isActive()) {
      throw ApiException.forbidden("ACCOUNT_DISABLED", "Account is disabled");
    }
    assertEnterpriseActive(user);

    return rotate(current, user);
  }

  private void assertEnterpriseActive(User user) {
    String enterpriseId = user.getEnterpriseId();
    if (enterpriseId == null) {
      return;
    }
    Enterprise enterprise =
        enterpriseRepository.findById(Objects.requireNonNull(enterpriseId)).orElse(null);
    if (enterprise != null && !enterprise.isActive()) {
      throw ApiException.forbidden("ENTERPRISE_SUSPENDED", "Enterprise is suspended");
    }
  }

  private AuthTokensResponse rotate(RefreshToken current, User user) {
    String newRaw = OpaqueTokenGenerator.generate();
    RefreshToken next = new RefreshToken();
    next.setUser(user);
    next.setTokenHash(TokenHasher.sha256Hex(newRaw));
    next.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshExpiresSeconds()));
    refreshTokenRepository.save(next);
    refreshTokenRepository.flush();

    current.setRevokedAt(Instant.now());
    current.setReplacedBy(next);
    refreshTokenRepository.save(current);

    String access = jwtService.createAccessToken(user.getId(), next.getId());
    return new AuthTokensResponse(
        access, newRaw, "Bearer", jwtProperties.getExpiresSeconds(), toPublicUser(user));
  }

  private boolean isWithinReuseGrace_(RefreshToken token) {
    long graceSeconds = jwtProperties.getRefreshReuseGraceSeconds();
    if (graceSeconds <= 0) {
      return false;
    }
    Instant revokedAt = token.getRevokedAt();
    if (revokedAt == null) {
      return false;
    }
    return revokedAt.plusSeconds(graceSeconds).isAfter(Instant.now());
  }

  private boolean isValidGraceSuccessor_(RefreshToken successor, User user) {
    if (successor == null) {
      return false;
    }
    if (successor.getRevokedAt() != null) {
      return false;
    }
    if (!successor.getExpiresAt().isAfter(Instant.now())) {
      return false;
    }
    return successor.getUser().getId().equals(user.getId());
  }

  @Transactional
  public LogoutResponse logout(UserPrincipal principal) {
    RefreshToken session =
        refreshTokenRepository
            .findByIdWithUser(principal.getRefreshSessionId())
            .orElseThrow(
                () ->
                    ApiException.unauthorized(
                        "INVALID_SESSION", "Refresh session is invalid or expired"));

    if (!session.getUser().getId().equals(principal.getUserId())) {
      throw ApiException.unauthorized("INVALID_SESSION", "Refresh session is invalid or expired");
    }
    if (session.getRevokedAt() != null) {
      throw ApiException.unauthorized("INVALID_SESSION", "Refresh session is invalid or expired");
    }

    session.setRevokedAt(Instant.now());
    refreshTokenRepository.save(session);
    return new LogoutResponse(true, "Logged out successfully");
  }

  @Transactional(readOnly = true)
  public UserPublicDto me(UserPrincipal principal) {
    User user =
        userRepository
            .findById(Objects.requireNonNull(principal.getUserId()))
            .orElseThrow(() -> ApiException.notFound("User not found"));
    return toPublicUser(user);
  }

  /** Перемикає active role в межах available; сесії не revoke (фільтр читає роль з БД). */
  @Transactional
  public UserPublicDto switchRole(@NonNull UserPrincipal principal, @NonNull Role role) {
    User user =
        userRepository
            .findById(Objects.requireNonNull(principal.getUserId()))
            .orElseThrow(() -> ApiException.notFound("User not found"));
    if (!user.hasAvailableRole(role)) {
      throw ApiException.badRequest("ROLE_NOT_ASSIGNED", "Role is not assigned to this user");
    }
    if (user.getRole() != role) {
      user.setRole(role);
      userRepository.save(Objects.requireNonNull(user));
    }
    return toPublicUser(user);
  }

  private AuthTokensResponse issueTokens(User user) {
    String raw = OpaqueTokenGenerator.generate();
    RefreshToken refresh = new RefreshToken();
    refresh.setUser(user);
    refresh.setTokenHash(TokenHasher.sha256Hex(raw));
    refresh.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshExpiresSeconds()));
    refreshTokenRepository.save(refresh);
    refreshTokenRepository.flush();

    String access = jwtService.createAccessToken(user.getId(), refresh.getId());
    return new AuthTokensResponse(
        access, raw, "Bearer", jwtProperties.getExpiresSeconds(), toPublicUser(user));
  }

  private UserPublicDto toPublicUser(User user) {
    String enterpriseId = user.getEnterpriseId();
    String enterpriseCode =
        enterpriseId == null
            ? null
            : enterpriseRepository
                .findById(Objects.requireNonNull(enterpriseId))
                .map(e -> e.getCode())
                .orElse(null);
    return UserDtoMapper.toPublicDto(
        user, userProfileService.getByUserId(Objects.requireNonNull(user.getId())), enterpriseCode);
  }
}
