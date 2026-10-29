package com.geosun.tms.enterprise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Відповідь login / refresh оператора платформи. */
public record PlatformAuthTokensResponse(
    @JsonProperty("accessToken") String accessToken,
    @JsonProperty("refreshToken") String refreshToken,
    @JsonProperty("tokenType") String tokenType,
    @JsonProperty("expiresIn") long expiresInSeconds,
    PlatformOperatorDto operator) {}
