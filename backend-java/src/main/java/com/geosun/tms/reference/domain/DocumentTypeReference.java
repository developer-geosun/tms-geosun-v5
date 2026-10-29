package com.geosun.tms.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "document_types")
public class DocumentTypeReference {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @Column(name = "name_uk", nullable = false, length = 128)
  private String nameUk;

  @Column(name = "name_en", nullable = false, length = 128)
  private String nameEn;

  @Column(name = "name_ru", nullable = false, length = 128)
  private String nameRu;

  @Column(name = "country_code", nullable = false, length = 2)
  private String countryCode;

  @Convert(converter = DocumentTypeScanPagesConverter.class)
  @Column(name = "planned_scan_pages", nullable = false, columnDefinition = "JSON")
  private List<DocumentTypeScanPage> plannedScanPages = new ArrayList<>();

  @Convert(converter = DocumentTypeFieldDefinitionsConverter.class)
  @Column(name = "field_definitions", nullable = false, columnDefinition = "JSON")
  private List<DocumentTypeFieldDefinition> fieldDefinitions = new ArrayList<>();

  @Column(name = "comment", nullable = false, length = 512)
  private String comment = "";

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

  public String getNameUk() {
    return nameUk;
  }

  public void setNameUk(String nameUk) {
    this.nameUk = nameUk;
  }

  public String getNameEn() {
    return nameEn;
  }

  public void setNameEn(String nameEn) {
    this.nameEn = nameEn;
  }

  public String getNameRu() {
    return nameRu;
  }

  public void setNameRu(String nameRu) {
    this.nameRu = nameRu;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public List<DocumentTypeScanPage> getPlannedScanPages() {
    return plannedScanPages;
  }

  public void setPlannedScanPages(List<DocumentTypeScanPage> plannedScanPages) {
    this.plannedScanPages = plannedScanPages == null ? new ArrayList<>() : plannedScanPages;
  }

  public List<DocumentTypeFieldDefinition> getFieldDefinitions() {
    return fieldDefinitions;
  }

  public void setFieldDefinitions(List<DocumentTypeFieldDefinition> fieldDefinitions) {
    this.fieldDefinitions = fieldDefinitions == null ? new ArrayList<>() : fieldDefinitions;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment == null ? "" : comment;
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

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }
}
