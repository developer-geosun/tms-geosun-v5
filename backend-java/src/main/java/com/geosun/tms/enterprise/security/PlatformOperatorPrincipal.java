package com.geosun.tms.enterprise.security;

import java.util.Collection;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Принципал оператора платформи після перевірки access JWT (kind=operator). */
public class PlatformOperatorPrincipal implements UserDetails {

  public static final String ROLE = "PLATFORM_OPERATOR";

  private final @NonNull String operatorId;
  private final String email;
  private final String refreshSessionId;

  public PlatformOperatorPrincipal(
      @NonNull String operatorId, String email, String refreshSessionId) {
    this.operatorId = operatorId;
    this.email = email;
    this.refreshSessionId = refreshSessionId;
  }

  public @NonNull String getOperatorId() {
    return operatorId;
  }

  public String getEmail() {
    return email;
  }

  public String getRefreshSessionId() {
    return refreshSessionId;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + ROLE));
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
