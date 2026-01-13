package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.CreatePaymentRequest;
import com.tpv.pos_service.dto.PaymentResponse;
import com.tpv.pos_service.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pos")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/tickets/{ticketId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse addPayment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreatePaymentRequest req
    ) {
        return service.addPayment(ticketId, req);
    }
}
