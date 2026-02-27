package com.tpv.pos_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.tpv.pos_service.dto.AddTicketLineRequest;
import com.tpv.pos_service.dto.ApplyDiscountRequest;
import com.tpv.pos_service.dto.CreateTicketRequest;
import com.tpv.pos_service.dto.MoveTableRequest;
import com.tpv.pos_service.dto.PaymentSummaryResponse;
import com.tpv.pos_service.dto.ReopenPaidTicketRequest;
import com.tpv.pos_service.dto.SetBillRequestedRequest;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.dto.UpdateLineQtyRequest;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.TicketService;
import com.tpv.pos_service.dto.TicketSummaryResponse;
import com.tpv.pos_service.util.ActorResolver;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/pos/tickets")
public class TicketController {

    private final TicketService service;
    private final AuditService auditService;

    public TicketController(TicketService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(
            @RequestBody(required = false) @Valid CreateTicketRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.create(req == null ? null : req.tableNumber());
            auditService.recordSuccess("TICKET_CREATE", "TICKET", response.id(), actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_CREATE", "TICKET", null, actor, term, req, e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/open")
    public List<TicketResponse> listOpen() {
        return service.listOpen();
    }

    @GetMapping("/history/current-cash")
    public List<TicketResponse> listCurrentCashHistory() {
        return service.listCurrentCashSessionAllStatuses();
    }

    @GetMapping("/{id}/payment-summary")
    public PaymentSummaryResponse paymentSummary(@PathVariable Long id) {
        return service.paymentSummary(id);
    }

    @GetMapping("/{id}/summary")
    public TicketSummaryResponse summary(@PathVariable Long id) {
        return service.ticketSummary(id);
    }

    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse addLine(
            @PathVariable Long id,
            @Valid @RequestBody AddTicketLineRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.addLine(id, req.productId(), req.qty());
            auditService.recordSuccess("TICKET_ADD_LINE", "TICKET", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_ADD_LINE", "TICKET", id, actor, term, req, e);
            throw e;
        }
    }

    @PatchMapping("/{id}/lines/{lineId}")
    public TicketResponse updateQty(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @Valid @RequestBody UpdateLineQtyRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.updateLineQty(id, lineId, req.qty());
            auditService.recordSuccess("TICKET_UPDATE_LINE", "TICKET", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_UPDATE_LINE", "TICKET", id, actor, term, req, e);
            throw e;
        }
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    public TicketResponse deleteLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.removeLine(id, lineId);
            auditService.recordSuccess("TICKET_DELETE_LINE", "TICKET", id, actor, term, lineId, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_DELETE_LINE", "TICKET", id, actor, term, lineId, e);
            throw e;
        }
    }

    @PostMapping("/{id}/cancel")
    public TicketResponse cancel(
            @PathVariable Long id,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.cancel(id);
            auditService.recordSuccess("TICKET_CANCEL", "TICKET", id, actor, term, null, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_CANCEL", "TICKET", id, actor, term, null, e);
            throw e;
        }
    }

    @PostMapping("/{id}/bill-requested")
    public TicketResponse setBillRequested(
            @PathVariable Long id,
            @Valid @RequestBody SetBillRequestedRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.setBillRequested(id, Boolean.TRUE.equals(req.requested()));
            auditService.recordSuccess("TICKET_SET_BILL_REQUESTED", "TICKET", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_SET_BILL_REQUESTED", "TICKET", id, actor, term, req, e);
            throw e;
        }
    }

    @PostMapping("/{id}/move-table")
    public TicketResponse moveTable(
            @PathVariable Long id,
            @Valid @RequestBody MoveTableRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.moveTable(id, req.tableNumber(), term, actor);
            auditService.recordSuccess("TICKET_MOVE_TABLE", "TICKET", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_MOVE_TABLE", "TICKET", id, actor, term, req, e);
            throw e;
        }
    }

    @PostMapping("/{id}/discount")
    public TicketResponse applyDiscount(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApplyDiscountRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.applyDiscount(id, req);
            auditService.recordSuccess("TICKET_APPLY_DISCOUNT", "TICKET", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_APPLY_DISCOUNT", "TICKET", id, actor, term, req, e);
            throw e;
        }
    }

    @PostMapping("/{id}/reopen-paid")
    public TicketResponse reopenPaid(
            @PathVariable Long id,
            @Valid @RequestBody ReopenPaidTicketRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            TicketResponse response = service.reopenPaid(id, req.reason(), actor);
            auditService.recordSuccess("TICKET_REOPEN_PAID", "TICKET", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("TICKET_REOPEN_PAID", "TICKET", id, actor, term, req, e);
            throw e;
        }
    }

}
