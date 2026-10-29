package com.geosun.tms.enterprise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Refresh-токен оператора платформи. Не змішується з refresh_tokens співробітників. */
@Entity
@Table(name = "platform_operator_refresh_tokens")
public class PlatformOperatorRefreshToken {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "operator_id", nullable = false)
  private PlatformOperator operator;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "replaced_by_token_id")
  private PlatformOperatorRefreshToken replacedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }

  public String getId() {
    return id;
  }

  public PlatformOperator getOperator() {
    return operator;
  }

  public void setOperator(PlatformOperator operator) {
    this.operator = operator;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public PlatformOperatorRefreshToken getReplacedBy() {
    return replacedBy;
  }

  public void setReplacedBy(PlatformOperatorRefreshToken replacedBy) {
    this.replacedBy = replacedBy;
  }
}
