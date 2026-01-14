
package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


    public record UpdateProductRequest(@NotBlank String name, @Min(0) int priceCents, @NotNull Long categoryId, @Min(0) @Max(3000) int vatRateBps){
        
    }
