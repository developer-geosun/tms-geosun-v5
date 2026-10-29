package com.geosun.tms.auth.api;

import com.geosun.tms.auth.config.OpenApiConfig;
import com.geosun.tms.auth.security.UserPrincipal;
import com.geosun.tms.auth.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy admin soft-delete: делегує в {@link AdminUserService}.
 *
 * @deprecated Використовуйте {@code DELETE /api/v1/admin/users/{id}}.
 */
@Tag(name = "Users (admin)")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final AdminUserService adminUserService;

  public UserController(AdminUserService adminUserService) {
    this.adminUserService = adminUserService;
  }

  @Operation(
      summary = "Soft-delete user (legacy)",
      description = "ADMIN only; idempotent 204. Prefer DELETE /api/v1/admin/users/{id}.",
      deprecated = true)
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> softDelete(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @PathVariable("id") @NonNull String id) {
    adminUserService.softDelete(principal.requireEnterpriseId(), principal.getUserId(), id);
    return ResponseEntity.noContent().build();
  }
}
