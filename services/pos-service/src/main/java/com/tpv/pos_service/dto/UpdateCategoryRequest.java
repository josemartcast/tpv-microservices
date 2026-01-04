package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(@NotBlank(message = "name is required")
        @Size(min = 2, max = 80, message = "name must be between 2 and 80 chars")
        String name) {

}
