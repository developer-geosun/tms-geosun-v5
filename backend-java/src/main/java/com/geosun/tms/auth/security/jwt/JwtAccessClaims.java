package com.geosun.tms.auth.security.jwt;

import java.time.Instant;
import org.springframework.lang.NonNull;

/**
 * Валідовані claims access JWT (sub + sessionId + час + вид принципала).
 *
 * <p>{@code subjectUserId} — id співробітника або оператора платформи залежно від {@code kind}.
 */
public record JwtAccessClaims(
    @NonNull String subjectUserId,
    @NonNull String sessionId,
    Instant issuedAt,
    Instant expiresAt,
    @NonNull String kind) {

  public boolean isOperator() {
    return JwtService.KIND_OPERATOR.equals(kind);
  }
}
