
package com.tpv.auth_service.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LoginResponse(
        @JsonProperty("accessToken") String accessToken,
        long expiresInSeconds,
        List<String> roles
) {}
