package com.geosun.tms.trips.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "trips",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_trips_enterprise_trip_number",
            columnNames = {"enterprise_id", "trip_number"}))
public class Trip {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @Column(name = "trip_number", nullable = false, length = 32)
  private String tripNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private TripStatus status = TripStatus.DRAFT;

  @Column(name = "route_request_id")
  private Long routeRequestId;

  @Column(name = "title", length = 255)
  private String title;

  @Column(name = "comment", length = 2000)
  private String comment;

  @Column(name = "origin_text", length = 512)
  private String originText;

  @Column(name = "destination_text", length = 512)
  private String destinationText;

  @Column(name = "planned_start_at")
  private Instant plannedStartAt;

  @Column(name = "planned_end_at")
  private Instant plannedEndAt;

  @Column(name = "actual_start_at")
  private Instant actualStartAt;

  @Column(name = "actual_end_at")
  private Instant actualEndAt;

  @Column(name = "driver_id", length = 36)
  private String driverId;

  @Column(name = "combination_id", length = 36)
  private String combinationId;

  @Column(name = "tractor_id", length = 36)
  private String tractorId;

  @Column(name = "trailer_id", length = 36)
  private String trailerId;

  @Column(name = "driver_name", length = 384)
  private String driverName;

  @Column(name = "tractor_plate", length = 32)
  private String tractorPlate;

  @Column(name = "trailer_plate", length = 32)
  private String trailerPlate;

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

  public String getTripNumber() {
    return tripNumber;
  }

  public void setTripNumber(String tripNumber) {
    this.tripNumber = tripNumber;
  }

  public TripStatus getStatus() {
    return status;
  }

  public void setStatus(TripStatus status) {
    this.status = status;
  }

  public Long getRouteRequestId() {
    return routeRequestId;
  }

  public void setRouteRequestId(Long routeRequestId) {
    this.routeRequestId = routeRequestId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public String getOriginText() {
    return originText;
  }

  public void setOriginText(String originText) {
    this.originText = originText;
  }

  public String getDestinationText() {
    return destinationText;
  }

  public void setDestinationText(String destinationText) {
    this.destinationText = destinationText;
  }

  public Instant getPlannedStartAt() {
    return plannedStartAt;
  }

  public void setPlannedStartAt(Instant plannedStartAt) {
    this.plannedStartAt = plannedStartAt;
  }

  public Instant getPlannedEndAt() {
    return plannedEndAt;
  }

  public void setPlannedEndAt(Instant plannedEndAt) {
    this.plannedEndAt = plannedEndAt;
  }

  public Instant getActualStartAt() {
    return actualStartAt;
  }

  public void setActualStartAt(Instant actualStartAt) {
    this.actualStartAt = actualStartAt;
  }

  public Instant getActualEndAt() {
    return actualEndAt;
  }

  public void setActualEndAt(Instant actualEndAt) {
    this.actualEndAt = actualEndAt;
  }

  public String getDriverId() {
    return driverId;
  }

  public void setDriverId(String driverId) {
    this.driverId = driverId;
  }

  public String getCombinationId() {
    return combinationId;
  }

  public void setCombinationId(String combinationId) {
    this.combinationId = combinationId;
  }

  public String getTractorId() {
    return tractorId;
  }

  public void setTractorId(String tractorId) {
    this.tractorId = tractorId;
  }

  public String getTrailerId() {
    return trailerId;
  }

  public void setTrailerId(String trailerId) {
    this.trailerId = trailerId;
  }

  public String getDriverName() {
    return driverName;
  }

  public void setDriverName(String driverName) {
    this.driverName = driverName;
  }

  public String getTractorPlate() {
    return tractorPlate;
  }

  public void setTractorPlate(String tractorPlate) {
    this.tractorPlate = tractorPlate;
  }

  public String getTrailerPlate() {
    return trailerPlate;
  }

  public void setTrailerPlate(String trailerPlate) {
    this.trailerPlate = trailerPlate;
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

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }
}
