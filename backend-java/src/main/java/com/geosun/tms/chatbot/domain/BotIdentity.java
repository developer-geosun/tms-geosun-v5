package com.geosun.tms.chatbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/** Прив'язка обліковки TMS до зовнішнього користувача месенджера. */
@Entity
@Table(name = "bot_identities")
public class BotIdentity {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 16)
  private ChatbotChannel channel;

  @Column(name = "external_user_id", nullable = false, length = 128)
  private String externalUserId;

  @Column(name = "locale", nullable = false, length = 8)
  private String locale = "ua";

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private BotIdentityStatus status;

  @Column(name = "linked_at")
  private Instant linkedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public ChatbotChannel getChannel() {
    return channel;
  }

  public void setChannel(ChatbotChannel channel) {
    this.channel = channel;
  }

  public String getExternalUserId() {
    return externalUserId;
  }

  public void setExternalUserId(String externalUserId) {
    this.externalUserId = externalUserId;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public BotIdentityStatus getStatus() {
    return status;
  }

  public void setStatus(BotIdentityStatus status) {
    this.status = status;
  }

  public Instant getLinkedAt() {
    return linkedAt;
  }

  public void setLinkedAt(Instant linkedAt) {
    this.linkedAt = linkedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }
}
