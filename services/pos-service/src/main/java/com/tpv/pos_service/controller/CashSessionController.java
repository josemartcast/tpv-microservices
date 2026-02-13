package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.*;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.CashIncidentService;
import com.tpv.pos_service.service.CashSessionService;
import com.tpv.pos_service.util.ActorResolver;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pos/cash-sessions")
public class CashSessionController {

    private final CashSessionService service;
    private final CashIncidentService incidentService;
    private final AuditService auditService;

    public CashSessionController(CashSessionService service, CashIncidentService incidentService, AuditService auditService) {
        this.service = service;
        this.incidentService = incidentService;
        this.auditService = auditService;
    }

    @GetMapping("/current")
    public CashSessionResponse current() {
        return service.current();
    }

    @GetMapping("/{id}/close-summary")
    public CashSessionCloseSummaryResponse closeSummary(@PathVariable Long id) {
        return service.closeSummary(id);
    }

    @GetMapping("/{id}/incidents")
    public List<CashIncidentResponse> listIncidents(@PathVariable Long id) {
        return incidentService.listForCashSession(id);
    }

    @GetMapping("/{id}/open-tickets")
    public List<CashSessionOpenTicketResponse> openTickets(@PathVariable Long id) {
        return service.openTickets(id);
    }

    @PostMapping("/{id}/resolve-open-tickets")
    public ResolveOpenTicketsResponse resolveOpenTickets(
            @PathVariable Long id,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            ResolveOpenTicketsResponse response = service.resolveOpenTickets(id);
            auditService.recordSuccess("CASH_RESOLVE_OPEN_TICKETS", "CASH_SESSION", id, actor, term, null, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("CASH_RESOLVE_OPEN_TICKETS", "CASH_SESSION", id, actor, term, null, e);
            throw e;
        }
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

    @PostMapping("/{id}/incidents")
    public CashIncidentResponse addIncident(
            @PathVariable Long id,
            @Valid @RequestBody CreateCashIncidentRequest req,
            Authentication auth,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            CashIncidentResponse response = incidentService.addIncident(id, req, actor, idempotencyKey);
            auditService.recordSuccess("CASH_INCIDENT_ADD", "CASH_SESSION", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("CASH_INCIDENT_ADD", "CASH_SESSION", id, actor, term, req, e);
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
