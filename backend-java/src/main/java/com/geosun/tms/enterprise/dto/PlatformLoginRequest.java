package com.geosun.tms.enterprise.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Тіло {@code POST /api/v1/platform/auth/login}. */
public record PlatformLoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
