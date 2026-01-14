package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.*;
import com.tpv.pos_service.dto.*;
import com.tpv.pos_service.exception.*;
import com.tpv.pos_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final TicketRepository ticketRepo;
    private final PaymentRepository paymentRepo;

    public PaymentService(TicketRepository ticketRepo, PaymentRepository paymentRepo) {
        this.ticketRepo = ticketRepo;
        this.paymentRepo = paymentRepo;
    }

    @Transactional
    public PaymentResponse addPayment(Long ticketId, CreatePaymentRequest req) {

        Ticket ticket = ticketRepo.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        if (!ticket.isOpen()) {
            throw new ConflictException("Ticket is not open");
        }

        if (req.amountCents() <= 0) {
            throw new ConflictException("Payment amount must be > 0");
        }

        int paidSoFar = paymentRepo.sumAmountCentsByTicketId(ticketId);
        int remaining = ticket.getTotalGrossCents() - paidSoFar;

        if (req.amountCents() > remaining) {
            throw new ConflictException("Payment exceeds remaining amount");
        }

        Payment payment = new Payment(ticket, req.method(), req.amountCents());
        paymentRepo.save(payment);

        if (paidSoFar + req.amountCents() == ticket.getTotalGrossCents()) {
            ticket.markPaid();
        }

        return new PaymentResponse(
            payment.getId(),
            payment.getMethod(),
            payment.getAmountCents(),
            payment.getCreatedAt()
        );
    }
}
