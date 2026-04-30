package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.AutoPrintClaimRequest;
import com.tpv.pos_service.dto.AutoPrintClaimResponse;
import com.tpv.pos_service.dto.SendComandaRequest;
import com.tpv.pos_service.dto.SendComandaResponse;
import com.tpv.pos_service.dto.SendPreviewResponse;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.ComandaService;
import com.tpv.pos_service.util.ActorResolver;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/pos/tickets")
public class ComandaController {

    private final ComandaService service;
    private final AuditService auditService;

    public ComandaController(ComandaService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping("/{id}/send-preview")
    public SendPreviewResponse preview(@PathVariable Long id) {
        return service.preview(id);
    }

    @PostMapping("/{id}/send")
    public SendComandaResponse send(
            @PathVariable Long id,
            @Valid @RequestBody SendComandaRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
            Authentication auth
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            SendComandaResponse response = service.send(id, req.destination(), idempotencyKey);
            auditService.recordSuccess("COMANDA_SEND", "TICKET", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("COMANDA_SEND", "TICKET", id, actor, term, req, e);
            throw e;
        }
    }

    @PostMapping("/{id}/autoprint-claim")
    public AutoPrintClaimResponse claimAutoPrint(
            @PathVariable Long id,
            @Valid @RequestBody AutoPrintClaimRequest req,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
            Authentication auth
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            AutoPrintClaimResponse response = service.claimAutoPrint(id, req.destination(), req.printJobId());
            auditService.recordSuccess("COMANDA_AUTOPRINT_CLAIM", "TICKET", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("COMANDA_AUTOPRINT_CLAIM", "TICKET", id, actor, term, req, e);
            throw e;
        }
    }
}
