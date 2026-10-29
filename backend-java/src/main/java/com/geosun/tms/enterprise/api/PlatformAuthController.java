package com.geosun.tms.enterprise.api;

import com.geosun.tms.auth.config.OpenApiConfig;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.response.LogoutResponse;
import com.geosun.tms.auth.infrastructure.web.ClientIpResolver;
import com.geosun.tms.enterprise.dto.PlatformAuthTokensResponse;
import com.geosun.tms.enterprise.dto.PlatformLoginRequest;
import com.geosun.tms.enterprise.dto.PlatformOperatorDto;
import com.geosun.tms.enterprise.security.PlatformOperatorPrincipal;
import com.geosun.tms.enterprise.service.PlatformAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Вхід оператора платформи (префікс /api/v1/platform/auth). */
@Tag(name = "Platform Authentication")
@RestController
@RequestMapping("/api/v1/platform/auth")
public class PlatformAuthController {

  private final PlatformAuthService platformAuthService;
  private final ClientIpResolver clientIpResolver;

  public PlatformAuthController(
      PlatformAuthService platformAuthService, ClientIpResolver clientIpResolver) {
    this.platformAuthService = platformAuthService;
    this.clientIpResolver = clientIpResolver;
  }

  @Operation(summary = "Platform operator login")
  @PostMapping("/login")
  public PlatformAuthTokensResponse login(
      @Valid @RequestBody @NonNull PlatformLoginRequest request, HttpServletRequest httpRequest) {
    return platformAuthService.login(request, clientIpResolver.resolve(httpRequest));
  }

  @Operation(summary = "Platform operator refresh", description = "Rotates the refresh token.")
  @PostMapping("/refresh")
  public PlatformAuthTokensResponse refresh(
      @Valid @RequestBody @NonNull RefreshRequest request, HttpServletRequest httpRequest) {
    return platformAuthService.refresh(request, clientIpResolver.resolve(httpRequest));
  }

  @Operation(summary = "Platform operator logout")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasRole('PLATFORM_OPERATOR')")
  @PostMapping("/logout")
  public LogoutResponse logout(
      @AuthenticationPrincipal @NonNull PlatformOperatorPrincipal principal) {
    return platformAuthService.logout(principal);
  }

  @Operation(summary = "Current platform operator")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @PreAuthorize("hasRole('PLATFORM_OPERATOR')")
  @GetMapping("/me")
  public PlatformOperatorDto me(
      @AuthenticationPrincipal @NonNull PlatformOperatorPrincipal principal) {
    return platformAuthService.me(principal);
  }
}
