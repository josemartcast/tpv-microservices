package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(@NotBlank
        @Size(max = 80)
        String name) {

}
