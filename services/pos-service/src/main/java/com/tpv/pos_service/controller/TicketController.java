package com.tpv.pos_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.tpv.pos_service.dto.AddTicketLineRequest;
import com.tpv.pos_service.dto.PaymentSummaryResponse;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.dto.UpdateLineQtyRequest;
import com.tpv.pos_service.service.TicketService;
import com.tpv.pos_service.dto.TicketSummaryResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/pos/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create() {
        return service.create();
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/open")
    public List<TicketResponse> listOpen() {
        return service.listOpen();
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
    public TicketResponse addLine(@PathVariable Long id, @Valid @RequestBody AddTicketLineRequest req) {
        return service.addLine(id, req.productId(), req.qty());
    }

    @PatchMapping("/{id}/lines/{lineId}")
    public TicketResponse updateQty(@PathVariable Long id, @PathVariable Long lineId,
            @Valid @RequestBody UpdateLineQtyRequest req) {
        return service.updateLineQty(id, lineId, req.qty());
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    public TicketResponse deleteLine(@PathVariable Long id, @PathVariable Long lineId) {
        return service.removeLine(id, lineId);
    }

    @PostMapping("/{id}/pay")
    public TicketResponse pay(@PathVariable Long id) {
        return service.pay(id);
    }

    @PostMapping("/{id}/cancel")
    public TicketResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

}
