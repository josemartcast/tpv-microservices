package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.*;
import com.tpv.pos_service.dto.FiscalSummaryResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FiscalService {

    private final CashSessionRepository cashRepo;
    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final PaymentRepository payRepo;
    private final FiscalClosureRepository closureRepo;

    public FiscalService(
            CashSessionRepository cashRepo,
            TicketRepository ticketRepo,
            TicketLineRepository lineRepo,
            PaymentRepository payRepo,
            FiscalClosureRepository closureRepo
    ) {
        this.cashRepo = cashRepo;
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.payRepo = payRepo;
        this.closureRepo = closureRepo;
    }

    // ================= SUMMARY =================
    @Transactional(readOnly = true)
    public FiscalSummaryResponse summary(Long cashSessionId) {

        cashRepo.findById(cashSessionId)
                .orElseThrow(() -> new NotFoundException(
                        "Cash session not found: " + cashSessionId));

        int paid = ticketRepo.countByCashSession_IdAndStatus(
                cashSessionId, TicketStatus.PAID);

        int cancelled = ticketRepo.countByCashSession_IdAndStatus(
                cashSessionId, TicketStatus.CANCELLED);

        int gross = safe(lineRepo.sumGrossByCashSession(cashSessionId));
        int net = safe(lineRepo.sumNetByCashSession(cashSessionId));
        int vat = safe(lineRepo.sumVatByCashSession(cashSessionId));

        int cash = safe(payRepo.sumByCashSessionAndMethod(
                cashSessionId, PaymentMethod.CASH));
        int card = safe(payRepo.sumByCashSessionAndMethod(
                cashSessionId, PaymentMethod.CARD));
        int bizum = safe(payRepo.sumByCashSessionAndMethod(
                cashSessionId, PaymentMethod.BIZUM));

        return new FiscalSummaryResponse(
                cashSessionId,
                paid,
                cancelled,
                gross,
                net,
                vat,
                cash,
                card,
                bizum
        );
    }

    // ================= CLOSURE =================
    @Transactional
    public FiscalClosure ensureClosure(Long cashSessionId) {

        return closureRepo.findByCashSession_Id(cashSessionId)
                .orElseGet(() -> {

                    CashSession cs = cashRepo.findById(cashSessionId)
                            .orElseThrow(() -> new NotFoundException(
                                    "Cash session not found: " + cashSessionId));

                    if (cs.getStatus() != CashSessionStatus.CLOSED) {
                        throw new ConflictException(
                                "Cash session must be CLOSED to generate fiscal closure");
                    }

                    FiscalSummaryResponse s = summary(cashSessionId);

                    FiscalClosure fc = new FiscalClosure(
                            cs,
                            s.paidTicketsCount(),
                            s.cancelledTicketsCount(),
                            s.grossSalesCents(),
                            s.netSalesCents(),
                            s.vatSalesCents(),
                            s.cashPaymentsCents(),
                            s.cardPaymentsCents(),
                            s.bizumPaymentsCents()
                    );

                    return closureRepo.save(fc);
                });
    }

    private int safe(Integer v) {
        return v == null ? 0 : v;
    }
}
