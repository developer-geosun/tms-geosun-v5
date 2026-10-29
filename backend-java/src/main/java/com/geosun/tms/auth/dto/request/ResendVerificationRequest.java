package com.geosun.tms.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Тіло {@code POST /api/v1/auth/resend-verification}. */
public record ResendVerificationRequest(
    @NotBlank String enterpriseCode, @NotBlank @Email String email) {}
