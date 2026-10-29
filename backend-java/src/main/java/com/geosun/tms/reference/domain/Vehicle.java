package com.geosun.tms.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "vehicles")
public class Vehicle {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @Column(name = "plate_number", nullable = false, length = 32)
  private String plateNumber;

  @Column(name = "vin", nullable = false, length = 17)
  private String vin;

  @Column(name = "make", nullable = false, length = 64)
  private String make;

  @Column(name = "model", nullable = false, length = 64)
  private String model;

  @Column(name = "manufacture_year", nullable = false)
  private short manufactureYear;

  @Column(name = "owner", nullable = false, length = 255)
  private String owner;

  @Column(name = "registration_series", nullable = false, length = 16)
  private String registrationSeries;

  @Column(name = "registration_number", nullable = false, length = 32)
  private String registrationNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "vehicle_type", nullable = false, length = 32)
  private VehicleType vehicleType;

  /** Чи є рефрижератор (актуально для напівпричепа). */
  @Column(name = "has_refrigerator", nullable = false)
  private boolean hasRefrigerator;

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

  public String getPlateNumber() {
    return plateNumber;
  }

  public void setPlateNumber(String plateNumber) {
    this.plateNumber = plateNumber;
  }

  public String getVin() {
    return vin;
  }

  public void setVin(String vin) {
    this.vin = vin;
  }

  public String getMake() {
    return make;
  }

  public void setMake(String make) {
    this.make = make;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public short getManufactureYear() {
    return manufactureYear;
  }

  public void setManufactureYear(short manufactureYear) {
    this.manufactureYear = manufactureYear;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getRegistrationSeries() {
    return registrationSeries;
  }

  public void setRegistrationSeries(String registrationSeries) {
    this.registrationSeries = registrationSeries;
  }

  public String getRegistrationNumber() {
    return registrationNumber;
  }

  public void setRegistrationNumber(String registrationNumber) {
    this.registrationNumber = registrationNumber;
  }

  public VehicleType getVehicleType() {
    return vehicleType;
  }

  public void setVehicleType(VehicleType vehicleType) {
    this.vehicleType = vehicleType;
  }

  public boolean isHasRefrigerator() {
    return hasRefrigerator;
  }

  public void setHasRefrigerator(boolean hasRefrigerator) {
    this.hasRefrigerator = hasRefrigerator;
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
