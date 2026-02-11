package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.*;
import com.tpv.pos_service.dto.*;
import com.tpv.pos_service.exception.*;
import com.tpv.pos_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@SuppressWarnings("null")
public class PaymentService {

    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final PaymentRepository paymentRepo;

    public PaymentService(TicketRepository ticketRepo, TicketLineRepository lineRepo, PaymentRepository paymentRepo) {
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.paymentRepo = paymentRepo;
    }

    @Transactional
    public PaymentResponse addPayment(Long ticketId, CreatePaymentRequest req, String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            var existing = paymentRepo.findByTicketIdAndIdempotencyKey(ticketId, key).orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        if (ticket.getCashSession().getStatus() != CashSessionStatus.OPEN) {
            throw new ConflictException("Cash session is CLOSED. Cannot add payments.");
        }

        if (!ticket.isOpen()) {
            throw new ConflictException("Ticket is not open");
        }
        if (req.amountCents() <= 0) {
            throw new ConflictException("Payment amount must be > 0");
        }

        int total = lineRepo.sumGrossByTicketId(ticketId);
        ticket.setTotalCents(total);

        if (total <= 0) {
            throw new ConflictException("Cannot pay an empty ticket");
        }

        int paidSoFar = paymentRepo.sumAmountCentsByTicketId(ticketId);
        int remaining = total - paidSoFar;

        if (remaining <= 0) {
            throw new ConflictException("Ticket is already fully paid");
        }
        if (req.amountCents() > remaining) {
            throw new ConflictException("Payment exceeds remaining amount");
        }

        Payment payment = new Payment(ticket, req.method(), req.amountCents(), key);
        try {
            paymentRepo.save(payment);
        } catch (DataIntegrityViolationException duplicate) {
            if (key == null) {
                throw duplicate;
            }
            var existing = paymentRepo.findByTicketIdAndIdempotencyKey(ticketId, key).orElseThrow(() -> duplicate);
            return toResponse(existing);
        }
        if (payment.getMethod() == PaymentMethod.CASH) {
            ticket.getCashSession().registerSale(payment.getAmountCents());
        }
        if (paidSoFar + req.amountCents() == total) {
            ticket.markPaid();
        }

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse addPayment(Long ticketId, CreatePaymentRequest req) {
        return addPayment(ticketId, req, null);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getMethod(), payment.getAmountCents(), payment.getCreatedAt());
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > 80) {
            throw new ConflictException("Idempotency-Key too long (max 80)");
        }
        return normalized;
    }
}
