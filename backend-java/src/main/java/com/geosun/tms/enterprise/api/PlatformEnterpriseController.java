package com.geosun.tms.enterprise.api;

import com.geosun.tms.auth.config.OpenApiConfig;
import com.geosun.tms.enterprise.dto.CreateEnterpriseRequest;
import com.geosun.tms.enterprise.dto.EnterpriseDto;
import com.geosun.tms.enterprise.service.PlatformEnterpriseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Підприємства для оператора платформи (префікс /api/v1/platform/enterprises). */
@Tag(name = "Platform Enterprises")
@RestController
@RequestMapping("/api/v1/platform/enterprises")
@PreAuthorize("hasRole('PLATFORM_OPERATOR')")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class PlatformEnterpriseController {

  private final PlatformEnterpriseService platformEnterpriseService;

  public PlatformEnterpriseController(PlatformEnterpriseService platformEnterpriseService) {
    this.platformEnterpriseService = platformEnterpriseService;
  }

  @Operation(summary = "List enterprises")
  @GetMapping
  public List<EnterpriseDto> list() {
    return platformEnterpriseService.list();
  }

  @Operation(
      summary = "Create enterprise with its first ADMIN",
      description = "The ADMIN is created with a verified email and can log in immediately.")
  @PostMapping
  public ResponseEntity<EnterpriseDto> create(
      @Valid @RequestBody @NonNull CreateEnterpriseRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(platformEnterpriseService.create(request));
  }

  @Operation(summary = "Suspend enterprise", description = "Idempotent; blocks employee access.")
  @PostMapping("/{id}/suspend")
  public EnterpriseDto suspend(@PathVariable("id") @NonNull String id) {
    return platformEnterpriseService.suspend(id);
  }

  @Operation(summary = "Activate enterprise", description = "Idempotent.")
  @PostMapping("/{id}/activate")
  public EnterpriseDto activate(@PathVariable("id") @NonNull String id) {
    return platformEnterpriseService.activate(id);
  }
}
