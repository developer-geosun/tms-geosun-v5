package com.geosun.tms.enterprise.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Тіло {@code POST /api/v1/platform/enterprises}: підприємство і його перший ADMIN. */
public record CreateEnterpriseRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 32) String code,
    @NotBlank @Email String adminEmail,
    @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "Password must contain at least one letter and one digit")
        String adminPassword) {}
