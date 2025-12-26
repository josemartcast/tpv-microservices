
package com.tpv.auth_service.controller.dto;

import java.util.List;

public record LoginResponse(String accesToken, long expiresInSeconds, List<String> roles){
    
}