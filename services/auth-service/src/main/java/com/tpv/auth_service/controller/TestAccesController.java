
package com.tpv.auth_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestAccesController {
    
    @GetMapping("/api/v1/admin/ping")
    public String adminPing(){
        return "Admin ok";
    }
    
    @GetMapping("/api/v1/user/ping")
    public String userPing(){
        return "User ok";
    }
}
