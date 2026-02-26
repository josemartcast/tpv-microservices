package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSalonAreaRequest(
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 80, message = "name must be between 2 and 80 chars")
        String name,

        @Min(value = 1, message = "tableCount must be >= 1")
        @Max(value = 200, message = "tableCount must be <= 200")
        Integer tableCount,

        @Min(value = 1, message = "firstTableNumber must be >= 1")
        @Max(value = 500, message = "firstTableNumber must be <= 500")
        Integer firstTableNumber
) {}
