package com.tpv.desktop.api.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(
    @JsonProperty("accesToken") String accessToken,   // backend viene con typo
    Integer expiresInSeconds,
    List<String> roles
) {}