package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.InvoiceSummaryResponse;
import com.tpv.pos_service.service.InvoiceService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public List<InvoiceSummaryResponse> list(
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedTo,
            @RequestParam(required = false) Integer limit
    ) {
        return invoiceService.list(invoiceNumber, customer, issuedFrom, issuedTo, limit);
    }
}
