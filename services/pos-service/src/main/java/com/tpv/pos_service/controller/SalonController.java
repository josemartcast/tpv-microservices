package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.SalonTableResponse;
import com.tpv.pos_service.dto.TableLockRequest;
import com.tpv.pos_service.dto.TableLockResponse;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.SalonService;
import com.tpv.pos_service.service.TableLockService;
import com.tpv.pos_service.util.ActorResolver;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/salon")
public class SalonController {

    private final SalonService service;
    private final TableLockService lockService;
    private final AuditService auditService;

    public SalonController(SalonService service, TableLockService lockService, AuditService auditService) {
        this.service = service;
        this.lockService = lockService;
        this.auditService = auditService;
    }

    @GetMapping("/tables")
    public List<SalonTableResponse> listTables() {
        return service.listTables();
    }

    @PostMapping("/tables/{tableNumber}/open-ticket")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse openTicket(
            @PathVariable int tableNumber,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.openTicketForTable(tableNumber);
            auditService.recordSuccess("SALON_OPEN_TICKET", "TABLE", (long) tableNumber, actor, term, null, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("SALON_OPEN_TICKET", "TABLE", (long) tableNumber, actor, term, null, e);
            throw e;
        }
    }

    @PostMapping("/tables/{tableNumber}/lock")
    public TableLockResponse lock(
            @PathVariable int tableNumber,
            @Valid @RequestBody TableLockRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            var lock = lockService.lock(tableNumber, req.terminalId(), actor);
            TableLockResponse response = new TableLockResponse(lock.getTableNumber(), lock.getTerminalId(), lock.getLockedBy(), lock.getExpiresAt());
            auditService.recordSuccess("TABLE_LOCK", "TABLE", (long) tableNumber, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TABLE_LOCK", "TABLE", (long) tableNumber, actor, term, req, e);
            throw e;
        }
    }

    @PostMapping("/tables/{tableNumber}/heartbeat")
    public TableLockResponse heartbeat(
            @PathVariable int tableNumber,
            @Valid @RequestBody TableLockRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            var lock = lockService.heartbeat(tableNumber, req.terminalId(), actor);
            TableLockResponse response = new TableLockResponse(lock.getTableNumber(), lock.getTerminalId(), lock.getLockedBy(), lock.getExpiresAt());
            auditService.recordSuccess("TABLE_HEARTBEAT", "TABLE", (long) tableNumber, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TABLE_HEARTBEAT", "TABLE", (long) tableNumber, actor, term, req, e);
            throw e;
        }
    }

    @PostMapping("/tables/{tableNumber}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(
            @PathVariable int tableNumber,
            @Valid @RequestBody TableLockRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            lockService.unlock(tableNumber, req.terminalId(), actor);
            auditService.recordSuccess("TABLE_UNLOCK", "TABLE", (long) tableNumber, actor, term, req, null);
        } catch (RuntimeException e) {
            auditService.recordFailure("TABLE_UNLOCK", "TABLE", (long) tableNumber, actor, term, req, e);
            throw e;
        }
    }
}
