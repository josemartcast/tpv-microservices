package com.tpv.desktop.api.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(
    @JsonProperty("accessToken") String accessToken,
    Integer expiresInSeconds,
    List<String> roles
) {}
