package com.geosun.tms.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Тіло {@code POST /api/v1/auth/register}. */
public record RegisterRequest(
    @NotBlank String enterpriseCode,
    @NotBlank @Email String email,
    @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "Password must contain at least one letter and one digit")
        String password) {}
