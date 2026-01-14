package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Payment;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketLine;
import com.tpv.pos_service.dto.TicketSummaryResponse;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.PaymentRepository;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketSummaryService {

    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final PaymentRepository paymentRepo;

    public TicketSummaryService(TicketRepository ticketRepo, TicketLineRepository lineRepo, PaymentRepository paymentRepo) {
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.paymentRepo = paymentRepo;
    }

    @Transactional(readOnly = true)
    public TicketSummaryResponse getSummary(Long ticketId) {
        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        List<Payment> payments = paymentRepo.findByTicketId(ticketId);

        int paid = paymentRepo.sumAmountCentsByTicketId(ticketId);
        int remaining = Math.max(0, ticket.getTotalCents() - paid);

        var lineDtos = lines.stream().map(l ->
                new TicketSummaryResponse.TicketLineSummary(
                        l.getId(),
                        l.getProduct().getId(),
                        l.getProductNameSnapshot(),
                        l.getUnitPriceCentsSnapshot(),
                        l.getQty(),
                        l.getLineTotalCents()
                )
        ).toList();

        var payDtos = payments.stream().map(p ->
                new TicketSummaryResponse.PaymentSummary(
                        p.getId(),
                        p.getMethod(),
                        p.getAmountCents(),
                        p.getCreatedAt()
                )
        ).toList();

        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getTotalCents(),
                paid,
                remaining,
                ticket.getCreatedAt(),
                lineDtos,
                payDtos
        );
    }
}
