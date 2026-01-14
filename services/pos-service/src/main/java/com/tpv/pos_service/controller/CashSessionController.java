package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.*;
import com.tpv.pos_service.service.CashSessionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pos/cash-sessions")
public class CashSessionController {

    private final CashSessionService service;

    public CashSessionController(CashSessionService service) {
        this.service = service;
    }

    @GetMapping("/current")
    public CashSessionResponse current() {
        return service.current();
    }

    @PostMapping("/open")
    public CashSessionResponse open(@RequestBody OpenCashSessionRequest req, Authentication auth) {
        return service.open(req, usernameFrom(auth));
    }

    @PostMapping("/{id}/close")
    public CashSessionResponse close(@PathVariable Long id,
                                    @RequestBody CloseCashSessionRequest req,
                                    Authentication auth) {
        return service.close(id, req, usernameFrom(auth));
    }

    private String usernameFrom(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            String u = jwt.getClaimAsString("username");
            return (u != null && !u.isBlank()) ? u : jwt.getSubject();
        }
        return auth.getName();
    }
}
