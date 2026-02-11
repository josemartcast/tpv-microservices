package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.CreatePaymentRequest;
import com.tpv.pos_service.dto.PaymentResponse;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.PaymentService;
import com.tpv.pos_service.util.ActorResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pos")
public class PaymentController {

    private final PaymentService service;
    private final AuditService auditService;

    public PaymentController(PaymentService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping("/tickets/{ticketId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse addPayment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreatePaymentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
            Authentication auth
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            PaymentResponse response = service.addPayment(ticketId, req, idempotencyKey);
            auditService.recordSuccess("PAYMENT_ADD", "TICKET", ticketId, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("PAYMENT_ADD", "TICKET", ticketId, actor, term, req, e);
            throw e;
        }
    }
}
