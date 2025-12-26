
package com.tpv.auth_service.controller.dto;

import jakarta.validation.constraints.NotBlank;


public record LoginRequest(@NotBlank String username, @NotBlank String password){
    
}
