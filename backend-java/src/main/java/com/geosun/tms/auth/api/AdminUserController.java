package com.geosun.tms.auth.api;

import com.geosun.tms.auth.config.OpenApiConfig;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.dto.request.AdminUserListQuery;
import com.geosun.tms.auth.dto.request.UpdateUserActiveRequest;
import com.geosun.tms.auth.dto.request.UpdateUserProfileRequest;
import com.geosun.tms.auth.dto.request.UpdateUserRoleRequest;
import com.geosun.tms.auth.dto.request.UpdateUserRolesRequest;
import com.geosun.tms.auth.dto.response.PageResponse;
import com.geosun.tms.auth.dto.response.UserAdminDto;
import com.geosun.tms.auth.dto.response.UserProfileDto;
import com.geosun.tms.auth.security.UserPrincipal;
import com.geosun.tms.auth.service.AdminUserService;
import com.geosun.tms.auth.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Адмін-управління користувачами: список/картка — ADMIN і MANAGER; мутації — лише ADMIN.
 */
@Tag(name = "Admin Users")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

  private final AdminUserService adminUserService;
  private final UserProfileService userProfileService;

  public AdminUserController(
      AdminUserService adminUserService, UserProfileService userProfileService) {
    this.adminUserService = adminUserService;
    this.userProfileService = userProfileService;
  }

  @Operation(summary = "List users with filters and pagination")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @GetMapping
  public PageResponse<UserAdminDto> list(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) Boolean deleted,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(defaultValue = "desc") String order,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return adminUserService.list(
        principal.requireEnterpriseId(),
        new AdminUserListQuery(email, name, role, active, deleted, sort, order, page, size));
  }

  @Operation(summary = "Get user by id")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @GetMapping("/{id}")
  public UserAdminDto getById(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @PathVariable("id") @NonNull String id) {
    return adminUserService.getById(principal.requireEnterpriseId(), id);
  }

  @Operation(summary = "Replace user profile (ADMIN)")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{id}/profile")
  public UserProfileDto putProfile(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @PathVariable("id") @NonNull String id,
      @Valid @RequestBody @NonNull UpdateUserProfileRequest body) {
    return userProfileService.putAdmin(principal.requireEnterpriseId(), id, body);
  }

  @Operation(
      summary = "Update user role (singleton)",
      description =
          "Compatibility wrapper: sets available roles to a single role. Demoting ADMIN requires"
              + " superAdminPassword (SUPER_ADMIN_PASSWORD). Prefer PUT /roles for multi-role.")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{id}/role")
  public UserAdminDto updateRole(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @PathVariable("id") @NonNull String id,
      @Valid @RequestBody @NonNull UpdateUserRoleRequest body) {
    return adminUserService.updateRole(
        principal.requireEnterpriseId(),
        principal.getUserId(),
        id,
        body.role(),
        body.superAdminPassword());
  }

  @Operation(
      summary = "Replace available roles",
      description =
          "Assigns the full set of available roles. Removing ADMIN requires superAdminPassword.")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{id}/roles")
  public UserAdminDto updateRoles(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @PathVariable("id") @NonNull String id,
      @Valid @RequestBody @NonNull UpdateUserRolesRequest body) {
    return adminUserService.updateRoles(
        principal.requireEnterpriseId(),
        principal.getUserId(),
        id,
        body.roles(),
        body.superAdminPassword());
  }

  @Operation(summary = "Activate or deactivate user")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{id}/active")
  public UserAdminDto setActive(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @PathVariable("id") @NonNull String id,
      @Valid @RequestBody @NonNull UpdateUserActiveRequest body) {
    return adminUserService.setActive(
        principal.requireEnterpriseId(), principal.getUserId(), id, body.active());
  }

  @Operation(summary = "Soft-delete user", description = "ADMIN only; idempotent 204.")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> softDelete(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @PathVariable("id") @NonNull String id) {
    adminUserService.softDelete(principal.requireEnterpriseId(), principal.getUserId(), id);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Restore soft-deleted user",
      description = "ADMIN only; clears deleted flag and reactivates. Idempotent if not deleted.")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/restore")
  public UserAdminDto restore(
      @AuthenticationPrincipal @NonNull UserPrincipal principal,
      @PathVariable("id") @NonNull String id) {
    return adminUserService.restore(principal.requireEnterpriseId(), principal.getUserId(), id);
  }
}
