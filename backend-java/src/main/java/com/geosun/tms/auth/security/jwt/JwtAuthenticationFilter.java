package com.geosun.tms.auth.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.domain.token.RefreshToken;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.security.SecurityErrorWriter;
import com.geosun.tms.auth.security.SecurityPathRules;
import com.geosun.tms.auth.security.UserPrincipal;
import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.domain.PlatformOperator;
import com.geosun.tms.enterprise.domain.PlatformOperatorRefreshToken;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRefreshTokenRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import com.geosun.tms.enterprise.security.PlatformOperatorPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Витягує Bearer JWT і перевіряє принципал: співробітника (користувач, refresh-сесія, статус
 * облікового запису і підприємства) або оператора платформи (окрема таблиця сесій).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final EnterpriseRepository enterpriseRepository;
  private final PlatformOperatorRepository platformOperatorRepository;
  private final PlatformOperatorRefreshTokenRepository platformRefreshTokenRepository;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      EnterpriseRepository enterpriseRepository,
      PlatformOperatorRepository platformOperatorRepository,
      PlatformOperatorRefreshTokenRepository platformRefreshTokenRepository,
      ObjectMapper objectMapper) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.enterpriseRepository = enterpriseRepository;
    this.platformOperatorRepository = platformOperatorRepository;
    this.platformRefreshTokenRepository = platformRefreshTokenRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityPathRules.isPublic(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      writeUnauthorized(response, request, "Missing or invalid Authorization header");
      return;
    }

    String token = header.substring(7).trim();
    if (token.isEmpty()) {
      writeUnauthorized(response, request, "Missing or invalid Authorization header");
      return;
    }

    Optional<JwtAccessClaims> parsed = jwtService.parseAccessToken(token);
    if (parsed.isEmpty()) {
      writeUnauthorized(response, request, "Invalid or expired access token");
      return;
    }

    JwtAccessClaims claims = parsed.get();
    boolean platformPath = SecurityPathRules.isPlatformPath(request);

    if (claims.isOperator()) {
      if (!platformPath) {
        // Оператор платформи не працює з операційними API підприємства
        writeForbidden(
            response,
            request,
            "ENTERPRISE_SCOPE_REQUIRED",
            "Platform operator token cannot access enterprise API");
        return;
      }
      PlatformOperatorPrincipal operatorPrincipal = authenticateOperator(claims, request, response);
      if (operatorPrincipal == null) {
        return;
      }
      setAuthentication(operatorPrincipal, request);
      filterChain.doFilter(request, response);
      return;
    }

    UserPrincipal principal = authenticateEmployee(claims, request, response);
    if (principal == null) {
      return;
    }
    if (platformPath) {
      writeForbidden(
          response,
          request,
          "PLATFORM_SCOPE_REQUIRED",
          "Platform operator token is required for this API");
      return;
    }
    setAuthentication(principal, request);
    filterChain.doFilter(request, response);
  }

  /** @return принципал співробітника або null, якщо відповідь з помилкою вже записана */
  private UserPrincipal authenticateEmployee(
      JwtAccessClaims claims, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Optional<User> userOpt = userRepository.findById(claims.subjectUserId());
    if (userOpt.isEmpty()) {
      writeUnauthorized(response, request, "Invalid or expired access token");
      return null;
    }

    User user = userOpt.get();
    if (user.isDeleted()) {
      writeForbidden(response, request, "USER_DELETED", "User account has been deleted");
      return null;
    }
    if (!user.isActive()) {
      writeForbidden(response, request, "ACCOUNT_DISABLED", "Account is disabled");
      return null;
    }
    if (isEnterpriseSuspended(user)) {
      writeForbidden(response, request, "ENTERPRISE_SUSPENDED", "Enterprise is suspended");
      return null;
    }

    Optional<RefreshToken> sessionOpt = refreshTokenRepository.findByIdWithUser(claims.sessionId());
    if (sessionOpt.isEmpty()) {
      writeInvalidSession(response, request);
      return null;
    }
    RefreshToken session = sessionOpt.get();
    if (!session.getUser().getId().equals(user.getId())) {
      writeInvalidSession(response, request);
      return null;
    }
    if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())) {
      writeInvalidSession(response, request);
      return null;
    }

    return new UserPrincipal(
        Objects.requireNonNull(user.getId()),
        user.getEmail(),
        user.getRole(),
        claims.sessionId(),
        user.getEnterpriseId());
  }

  /** Призупинене підприємство блокує і вже виданий access-токен. */
  private boolean isEnterpriseSuspended(User user) {
    String enterpriseId = user.getEnterpriseId();
    if (enterpriseId == null) {
      return false;
    }
    Optional<Enterprise> enterprise =
        enterpriseRepository.findById(Objects.requireNonNull(enterpriseId));
    return enterprise.isPresent() && !enterprise.get().isActive();
  }

  /** @return принципал оператора або null, якщо відповідь з помилкою вже записана */
  private PlatformOperatorPrincipal authenticateOperator(
      JwtAccessClaims claims, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Optional<PlatformOperator> operatorOpt =
        platformOperatorRepository.findById(claims.subjectUserId());
    if (operatorOpt.isEmpty()) {
      writeUnauthorized(response, request, "Invalid or expired access token");
      return null;
    }
    PlatformOperator operator = operatorOpt.get();
    if (!operator.isActive()) {
      writeForbidden(response, request, "ACCOUNT_DISABLED", "Account is disabled");
      return null;
    }

    Optional<PlatformOperatorRefreshToken> sessionOpt =
        platformRefreshTokenRepository.findByIdWithOperator(claims.sessionId());
    if (sessionOpt.isEmpty()) {
      writeInvalidSession(response, request);
      return null;
    }
    PlatformOperatorRefreshToken session = sessionOpt.get();
    if (!session.getOperator().getId().equals(operator.getId())
        || session.getRevokedAt() != null
        || !session.getExpiresAt().isAfter(Instant.now())) {
      writeInvalidSession(response, request);
      return null;
    }

    return new PlatformOperatorPrincipal(
        Objects.requireNonNull(operator.getId()), operator.getEmail(), claims.sessionId());
  }

  private void setAuthentication(UserDetails principal, HttpServletRequest request) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private void writeUnauthorized(
      HttpServletResponse response, HttpServletRequest request, String message) throws IOException {
    SecurityErrorWriter.writeJson(
        response,
        objectMapper,
        HttpServletResponse.SC_UNAUTHORIZED,
        "Unauthorized",
        "UNAUTHORIZED",
        message,
        request.getRequestURI());
  }

  private void writeForbidden(
      HttpServletResponse response, HttpServletRequest request, String code, String message)
      throws IOException {
    SecurityErrorWriter.writeJson(
        response,
        objectMapper,
        HttpServletResponse.SC_FORBIDDEN,
        "Forbidden",
        code,
        message,
        request.getRequestURI());
  }

  private void writeInvalidSession(HttpServletResponse response, HttpServletRequest request)
      throws IOException {
    SecurityErrorWriter.writeJson(
        response,
        objectMapper,
        HttpServletResponse.SC_UNAUTHORIZED,
        "Unauthorized",
        "INVALID_SESSION",
        "Refresh session is invalid or expired",
        request.getRequestURI());
  }
}
