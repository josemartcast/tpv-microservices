package com.tpv.pos_service.controller;

import com.tpv.pos_service.domain.FiscalClosure;
import com.tpv.pos_service.dto.FiscalClosureResponse;
import com.tpv.pos_service.dto.FiscalSummaryResponse;
import com.tpv.pos_service.service.FiscalService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pos/cash-sessions")
public class FiscalController {

    private final FiscalService summaryService;
    private final FiscalService fiscalService; 

    public FiscalController(FiscalService summaryService, FiscalService fiscalService) {
        this.summaryService = summaryService;
        this.fiscalService = fiscalService;
    }


    @GetMapping("/{id}/fiscal-summary")
    public FiscalSummaryResponse fiscalSummary(@PathVariable Long id) {
        return summaryService.summary(id);
    }


    @GetMapping("/{id}/fiscal-closure")
    public FiscalClosureResponse fiscalClosure(@PathVariable Long id) {
        FiscalClosure fc = fiscalService.ensureClosure(id);
        return new FiscalClosureResponse(
            fc.getCashSession().getId(),
            fc.getCreatedAt(),
            fc.getPaidTicketsCount(),
            fc.getCancelledTicketsCount(),
            fc.getGrossSalesCents(),
            fc.getNetSalesCents(),
            fc.getVatSalesCents(),
            fc.getCashPaymentsCents(),
            fc.getCardPaymentsCents(),
            fc.getBizumPaymentsCents()
        );
    }
}
