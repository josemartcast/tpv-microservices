package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.*;
import com.tpv.pos_service.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pos/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTicketResponse create() {
        return service.create();
    }

    @GetMapping("/{id}")
    public TicketResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping
    public List<TicketResponse> list(@RequestParam(defaultValue = "OPEN") String status) {
        return service.listByStatus(status);
    }

    @PostMapping("/{id}/lines")
    public TicketResponse addLine(@PathVariable Long id, @Valid @RequestBody AddLineRequest req) {
        return service.addLine(id, req);
    }

    @PatchMapping("/{id}/lines/{lineId}")
    public TicketResponse updateQty(@PathVariable Long id, @PathVariable Long lineId, @Valid @RequestBody UpdateLineQtyRequest req) {
        return service.updateLineQty(id, lineId, req);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    public TicketResponse deleteLine(@PathVariable Long id, @PathVariable Long lineId) {
        return service.deleteLine(id, lineId);
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
