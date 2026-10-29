package com.geosun.tms.routes.domain;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.freight.cost.domain.FreightCostCalculation;
import com.geosun.tms.routes.dto.QuoteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "freight_quotes")
public class FreightQuote {
  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id", nullable = false)
  private RouteRequest request;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "admin_user_id", nullable = false)
  private User adminUser;

  @Column(name = "currency", nullable = false, length = 8)
  private String currency;

  @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "transit_days_min")
  private Integer transitDaysMin;

  @Column(name = "transit_days_max")
  private Integer transitDaysMax;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private QuoteStatus status;

  @Column(name = "public_note", columnDefinition = "text")
  private String publicNote;

  @Column(name = "internal_note", columnDefinition = "text")
  private String internalNote;

  /** Як у Flyway V22: ON DELETE SET NULL (потрібно і для H2 у тестах). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "freight_cost_calculation_id")
  @OnDelete(action = OnDeleteAction.SET_NULL)
  private FreightCostCalculation freightCostCalculation;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }

  public String getId() {
    return id;
  }

  public RouteRequest getRequest() {
    return request;
  }

  public void setRequest(RouteRequest request) {
    this.request = request;
  }

  public User getAdminUser() {
    return adminUser;
  }

  public void setAdminUser(User adminUser) {
    this.adminUser = adminUser;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public Integer getTransitDaysMin() {
    return transitDaysMin;
  }

  public void setTransitDaysMin(Integer transitDaysMin) {
    this.transitDaysMin = transitDaysMin;
  }

  public Integer getTransitDaysMax() {
    return transitDaysMax;
  }

  public void setTransitDaysMax(Integer transitDaysMax) {
    this.transitDaysMax = transitDaysMax;
  }

  public LocalDate getValidUntil() {
    return validUntil;
  }

  public void setValidUntil(LocalDate validUntil) {
    this.validUntil = validUntil;
  }

  public QuoteStatus getStatus() {
    return status;
  }

  public void setStatus(QuoteStatus status) {
    this.status = status;
  }

  public String getPublicNote() {
    return publicNote;
  }

  public void setPublicNote(String publicNote) {
    this.publicNote = publicNote;
  }

  public String getInternalNote() {
    return internalNote;
  }

  public void setInternalNote(String internalNote) {
    this.internalNote = internalNote;
  }

  public FreightCostCalculation getFreightCostCalculation() {
    return freightCostCalculation;
  }

  public void setFreightCostCalculation(FreightCostCalculation freightCostCalculation) {
    this.freightCostCalculation = freightCostCalculation;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }
}
