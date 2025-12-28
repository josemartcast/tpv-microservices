
package com.tpv.pos_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/v1/pos/health")
    public String health(){
        return "POS SERVICE OK";
    }
}
