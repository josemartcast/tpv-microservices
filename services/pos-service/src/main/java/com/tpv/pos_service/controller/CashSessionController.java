package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.*;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.CashSessionService;
import com.tpv.pos_service.util.ActorResolver;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pos/cash-sessions")
public class CashSessionController {

    private final CashSessionService service;
    private final AuditService auditService;

    public CashSessionController(CashSessionService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping("/current")
    public CashSessionResponse current() {
        return service.current();
    }

    @PostMapping("/open")
    public CashSessionResponse open(
            @Valid @RequestBody OpenCashSessionRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            CashSessionResponse response = service.open(req, actor);
            auditService.recordSuccess("CASH_OPEN", "CASH_SESSION", response.id(), actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("CASH_OPEN", "CASH_SESSION", null, actor, term, req, e);
            throw e;
        }
    }

    @PostMapping("/{id}/close")
    public CashSessionResponse close(@PathVariable Long id,
                                    @Valid @RequestBody CloseCashSessionRequest req,
                                    Authentication auth,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            CashSessionResponse response = service.close(id, req, actor, idempotencyKey);
            auditService.recordSuccess("CASH_CLOSE", "CASH_SESSION", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("CASH_CLOSE", "CASH_SESSION", id, actor, term, req, e);
            throw e;
        }
    }
}
