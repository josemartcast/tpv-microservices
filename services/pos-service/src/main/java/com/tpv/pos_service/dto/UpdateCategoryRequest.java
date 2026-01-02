package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(@NotBlank(message = "Nombre obligatorio")
        @Size(min = 2, max = 80, message = "Nombre debe contener entre 2 y 80 caracteres")
        String name) {

}
