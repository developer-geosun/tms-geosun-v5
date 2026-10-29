package com.geosun.tms.auth.domain.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(name = "id", nullable = false, updatable = false, length = 36)
  private String id;

  /**
   * Підприємство співробітника. У MySQL після V49 колонка NOT NULL. У мапінгу nullable лишається,
   * щоб тест переносу встиг вставити рядок до раннера (H2 не ганяє Flyway).
   */
  @Column(name = "enterprise_id", length = 36)
  private String enterpriseId;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  /** Активна роль для RBAC (має бути в {@link #availableRoles}). */
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 32)
  private Role role = Role.USER;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id", nullable = false))
  @Column(name = "role", nullable = false, length = 32)
  @Enumerated(EnumType.STRING)
  private Set<Role> availableRoles = new LinkedHashSet<>();

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "is_deleted", nullable = false)
  private boolean deleted;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

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
    ensureAvailableRolesInitialized();
  }

  @PostLoad
  void afterLoad() {
    ensureAvailableRolesInitialized();
  }

  /** Якщо available порожній — підставляємо active (реєстрація / старі тести). */
  private void ensureAvailableRolesInitialized() {
    if (availableRoles == null) {
      availableRoles = new LinkedHashSet<>();
    }
    if (availableRoles.isEmpty() && role != null) {
      availableRoles.add(role);
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  public void setEnterpriseId(String enterpriseId) {
    this.enterpriseId = enterpriseId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public @NonNull Set<Role> getAvailableRoles() {
    if (availableRoles == null) {
      availableRoles = new LinkedHashSet<>();
    }
    return Objects.requireNonNull(availableRoles);
  }

  public boolean hasAvailableRole(@NonNull Role candidate) {
    return getAvailableRoles().contains(candidate);
  }

  /** Повністю замінює набір доступних ролей (непорожній). */
  public void replaceAvailableRoles(@NonNull Collection<Role> roles) {
    Set<Role> normalized = UserRoleRules.normalize(roles);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("available roles must not be empty");
    }
    getAvailableRoles().clear();
    getAvailableRoles().addAll(normalized);
    if (role == null || !normalized.contains(role)) {
      role = UserRoleRules.pickPreferredActive(normalized);
    }
  }

  /** Додає роль до available без зняття інших. */
  public void ensureRoleAssigned(@NonNull Role candidate) {
    getAvailableRoles().add(Objects.requireNonNull(candidate));
  }

  /** Знімок available як EnumSet. */
  public @NonNull Set<Role> snapshotAvailableRoles() {
    Set<Role> snapshot = EnumSet.noneOf(Role.class);
    snapshot.addAll(getAvailableRoles());
    if (snapshot.isEmpty() && role != null) {
      snapshot.add(role);
    }
    return Objects.requireNonNull(snapshot);
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
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

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public void setEmailVerifiedAt(Instant emailVerifiedAt) {
    this.emailVerifiedAt = emailVerifiedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
