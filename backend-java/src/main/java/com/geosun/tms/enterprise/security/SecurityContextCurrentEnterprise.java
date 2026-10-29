package com.geosun.tms.enterprise.security;

import com.geosun.tms.auth.exception.ApiException;
import com.geosun.tms.auth.security.UserPrincipal;
import com.geosun.tms.enterprise.port.CurrentEnterprise;
import java.util.Objects;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Поточне підприємство з {@link UserPrincipal} у SecurityContext. */
@Component
public class SecurityContextCurrentEnterprise implements CurrentEnterprise {

  @Override
  public @NonNull String requireId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof UserPrincipal principal) {
      return Objects.requireNonNull(principal.requireEnterpriseId());
    }
    throw ApiException.forbidden(
        "ENTERPRISE_SCOPE_REQUIRED", "Enterprise scope is required for this operation");
  }
}
