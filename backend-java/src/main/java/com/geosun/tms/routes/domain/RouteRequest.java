package com.geosun.tms.routes.domain;

import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.routes.dto.RouteRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "route_requests")
public class RouteRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "route_id", nullable = false)
  private Route route;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private RouteRequestStatus status = RouteRequestStatus.NEW;

  @Column(name = "cargo_type", length = 64)
  private String cargoType;

  @Column(name = "weight_kg", precision = 12, scale = 3)
  private BigDecimal weightKg;

  @Column(name = "volume_m3", precision = 12, scale = 3)
  private BigDecimal volumeM3;

  @Column(name = "preferred_start_date")
  private LocalDate preferredStartDate;

  @Column(name = "comment", columnDefinition = "text")
  private String comment;

  @Column(name = "nbu_breakdown_scenario_id", length = 36)
  private String nbuBreakdownScenarioId;

  @Column(name = "nbu_breakdown_at")
  private Instant nbuBreakdownAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Route getRoute() {
    return route;
  }

  public void setRoute(Route route) {
    this.route = route;
  }

  public RouteRequestStatus getStatus() {
    return status;
  }

  public void setStatus(RouteRequestStatus status) {
    this.status = status;
  }

  public String getCargoType() {
    return cargoType;
  }

  public void setCargoType(String cargoType) {
    this.cargoType = cargoType;
  }

  public BigDecimal getWeightKg() {
    return weightKg;
  }

  public void setWeightKg(BigDecimal weightKg) {
    this.weightKg = weightKg;
  }

  public BigDecimal getVolumeM3() {
    return volumeM3;
  }

  public void setVolumeM3(BigDecimal volumeM3) {
    this.volumeM3 = volumeM3;
  }

  public LocalDate getPreferredStartDate() {
    return preferredStartDate;
  }

  public void setPreferredStartDate(LocalDate preferredStartDate) {
    this.preferredStartDate = preferredStartDate;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public String getNbuBreakdownScenarioId() {
    return nbuBreakdownScenarioId;
  }

  public void setNbuBreakdownScenarioId(String nbuBreakdownScenarioId) {
    this.nbuBreakdownScenarioId = nbuBreakdownScenarioId;
  }

  public Instant getNbuBreakdownAt() {
    return nbuBreakdownAt;
  }

  public void setNbuBreakdownAt(Instant nbuBreakdownAt) {
    this.nbuBreakdownAt = nbuBreakdownAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
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
