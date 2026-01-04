
package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


    public record CreateProductRequest(@NotBlank String name, @Min(0) int priceCents, @NotNull Long categoryId){
        
    }

