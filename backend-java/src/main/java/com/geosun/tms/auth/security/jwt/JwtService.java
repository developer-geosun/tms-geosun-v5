package com.geosun.tms.auth.security.jwt;

import com.geosun.tms.auth.security.UserPrincipal;
import com.geosun.tms.auth.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Підпис та перевірка access JWT (HS256, claims згідно ТЗ).
 */
@Component
public class JwtService {

  public static final String CLAIM_KIND = "kind";
  public static final String KIND_EMPLOYEE = "employee";
  public static final String KIND_OPERATOR = "operator";

  private final JwtProperties properties;
  private SecretKey signingKey;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void initKey() {
    byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < 32) {
      throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
    }
    this.signingKey = Keys.hmacShaKeyFor(secretBytes);
  }

  /** Access-токен співробітника підприємства. */
  public String createAccessToken(String userId, String refreshSessionId) {
    return createToken(userId, refreshSessionId, KIND_EMPLOYEE);
  }

  /** Access-токен оператора платформи (окремий вид принципала). */
  public String createOperatorAccessToken(String operatorId, String refreshSessionId) {
    return createToken(operatorId, refreshSessionId, KIND_OPERATOR);
  }

  private String createToken(String subjectId, String refreshSessionId, String kind) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(properties.getExpiresSeconds());
    var builder =
        Jwts.builder()
            .subject(subjectId)
            .claim(UserPrincipal.CLAIM_SESSION_ID, refreshSessionId)
            .claim(CLAIM_KIND, kind)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp));

    String issuer = properties.getIssuer();
    if (issuer != null && !issuer.isBlank()) {
      builder = builder.issuer(issuer.trim());
    }
    String audience = properties.getAudience();
    if (audience != null && !audience.isBlank()) {
      builder = builder.audience().add(audience.trim()).and();
    }

    return builder.signWith(signingKey).compact();
  }

  public Optional<JwtAccessClaims> parseAccessToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      var parserBuilder = Jwts.parser().verifyWith(signingKey);
      String issuer = properties.getIssuer();
      if (issuer != null && !issuer.isBlank()) {
        parserBuilder = parserBuilder.requireIssuer(issuer.trim());
      }
      String audience = properties.getAudience();
      if (audience != null && !audience.isBlank()) {
        parserBuilder = parserBuilder.requireAudience(audience.trim());
      }
      Claims claims = parserBuilder.build().parseSignedClaims(token).getPayload();
      String sessionId = claims.get(UserPrincipal.CLAIM_SESSION_ID, String.class);
      if (sessionId == null || sessionId.isBlank()) {
        return Optional.empty();
      }
      String sub = claims.getSubject();
      if (sub == null || sub.isBlank()) {
        return Optional.empty();
      }
      Instant iat = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
      Instant exp = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;
      // Токени без claim kind (видані до multi-enterprise) вважаємо токенами співробітника
      String rawKind = claims.get(CLAIM_KIND, String.class);
      String kind = KIND_OPERATOR.equals(rawKind) ? KIND_OPERATOR : KIND_EMPLOYEE;
      return Optional.of(
          new JwtAccessClaims(
              Objects.requireNonNull(sub), Objects.requireNonNull(sessionId), iat, exp, kind));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
