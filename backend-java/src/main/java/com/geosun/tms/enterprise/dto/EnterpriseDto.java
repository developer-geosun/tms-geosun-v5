package com.geosun.tms.enterprise.dto;

import java.time.Instant;

/** Підприємство для списку оператора платформи. */
public record EnterpriseDto(
    String id, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {}
