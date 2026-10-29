package com.geosun.tms.auth.security;

import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.exception.ApiException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Принципал після успішної перевірки access JWT (для SecurityContext).
 */
public class UserPrincipal implements UserDetails {

  public static final String CLAIM_SESSION_ID = "sessionId";

  private final @NonNull String userId;
  private final String email;
  private final Role role;
  private final String refreshSessionId;

  /** Підприємство співробітника; null лише для рядків до переносу в перше підприємство. */
  private final String enterpriseId;

  public UserPrincipal(
      @NonNull String userId,
      String email,
      Role role,
      String refreshSessionId,
      String enterpriseId) {
    this.userId = userId;
    this.email = email;
    this.role = role;
    this.refreshSessionId = refreshSessionId;
    this.enterpriseId = enterpriseId;
  }

  public String getEnterpriseId() {
    return enterpriseId;
  }

  /** Підприємство для scope адмін-операцій; без нього (старі рядки до переносу) — 403. */
  public @NonNull String requireEnterpriseId() {
    if (enterpriseId == null) {
      throw ApiException.forbidden(
          "ENTERPRISE_SCOPE_REQUIRED", "User is not assigned to an enterprise");
    }
    return Objects.requireNonNull(enterpriseId);
  }

  public @NonNull String getUserId() {
    return userId;
  }

  public String getRefreshSessionId() {
    return refreshSessionId;
  }

  public Role getRole() {
    return role;
  }

  public String getEmail() {
    return email;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
