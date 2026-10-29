package com.geosun.tms.routes.domain;

import com.geosun.tms.auth.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "routes")
public class Route {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "routing_profile", nullable = false, length = 64)
  private String routingProfile;

  @Column(name = "routing_mode", nullable = false, length = 64)
  private String routingMode;

  @Column(name = "route_polyline", nullable = false, columnDefinition = "longtext")
  private String routePolyline;

  @Column(name = "distance_km", precision = 12, scale = 3)
  private BigDecimal distanceKm;

  @Column(name = "duration_min")
  private Integer durationMin;

  @Column(name = "route_comment", columnDefinition = "text")
  private String routeComment;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_opened_at")
  private Instant lastOpenedAt;

  @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RoutePoint> points = new ArrayList<>();

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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getRoutingProfile() {
    return routingProfile;
  }

  public void setRoutingProfile(String routingProfile) {
    this.routingProfile = routingProfile;
  }

  public String getRoutingMode() {
    return routingMode;
  }

  public void setRoutingMode(String routingMode) {
    this.routingMode = routingMode;
  }

  public String getRoutePolyline() {
    return routePolyline;
  }

  public void setRoutePolyline(String routePolyline) {
    this.routePolyline = routePolyline;
  }

  public BigDecimal getDistanceKm() {
    return distanceKm;
  }

  public void setDistanceKm(BigDecimal distanceKm) {
    this.distanceKm = distanceKm;
  }

  public Integer getDurationMin() {
    return durationMin;
  }

  public void setDurationMin(Integer durationMin) {
    this.durationMin = durationMin;
  }

  public String getRouteComment() {
    return routeComment;
  }

  public void setRouteComment(String routeComment) {
    this.routeComment = routeComment;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getLastOpenedAt() {
    return lastOpenedAt;
  }

  public void setLastOpenedAt(Instant lastOpenedAt) {
    this.lastOpenedAt = lastOpenedAt;
  }

  public List<RoutePoint> getPoints() {
    return points;
  }

  public void setPoints(List<RoutePoint> points) {
    this.points = points;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }
}
