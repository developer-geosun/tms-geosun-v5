package com.geosun.tms.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "drivers")
public class Driver {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @Column(name = "last_name", nullable = false, length = 128)
  private String lastName;

  @Column(name = "first_name", nullable = false, length = 128)
  private String firstName;

  @Column(name = "patronymic", length = 128)
  private String patronymic;

  @Column(name = "phone", nullable = false, length = 32)
  private String phone;

  @Column(name = "license_number", nullable = false, length = 64)
  private String licenseNumber;

  @Column(name = "license_categories", nullable = false, length = 64)
  private String licenseCategories;

  @Column(name = "license_expires_on", nullable = false)
  private LocalDate licenseExpiresOn;

  @Column(name = "user_id", length = 36, unique = true)
  private String userId;

  @Column(name = "comment", length = 1000)
  private String comment;

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

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getPatronymic() {
    return patronymic;
  }

  public void setPatronymic(String patronymic) {
    this.patronymic = patronymic;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getLicenseNumber() {
    return licenseNumber;
  }

  public void setLicenseNumber(String licenseNumber) {
    this.licenseNumber = licenseNumber;
  }

  public String getLicenseCategories() {
    return licenseCategories;
  }

  public void setLicenseCategories(String licenseCategories) {
    this.licenseCategories = licenseCategories;
  }

  public LocalDate getLicenseExpiresOn() {
    return licenseExpiresOn;
  }

  public void setLicenseExpiresOn(LocalDate licenseExpiresOn) {
    this.licenseExpiresOn = licenseExpiresOn;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
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
