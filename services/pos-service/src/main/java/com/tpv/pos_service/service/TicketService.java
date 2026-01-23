package com.tpv.pos_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketLine;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.PaymentSummaryResponse;
import com.tpv.pos_service.dto.TicketLineResponse;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.PaymentRepository;
import com.tpv.pos_service.repository.ProductRepository;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import com.tpv.pos_service.domain.Payment;
import com.tpv.pos_service.dto.TicketSummaryResponse;
import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.repository.CashSessionRepository;

@Service
public class TicketService {

    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final ProductRepository productRepo;
    private final PaymentRepository paymentRepo;
    private final CashSessionRepository cashSessionRepo;

    public TicketService(TicketRepository ticketRepo, TicketLineRepository lineRepo, ProductRepository productRepo, PaymentRepository paymentRepo, CashSessionRepository cashSessionRepo) {
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.productRepo = productRepo;
        this.paymentRepo = paymentRepo;
        this.cashSessionRepo = cashSessionRepo;
    }

    @Transactional
    public TicketResponse create() {

        CashSession openSession = cashSessionRepo
                .findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN)
                .orElseThrow(() -> new ConflictException("No open cash session. Open a cash session first."));

        Ticket t = ticketRepo.save(new Ticket(openSession));
        return toResponse(t, List.of());
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long ticketId) {
        Ticket t = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listOpen() {
        return ticketRepo.findAllByStatusOrderByCreatedAtDesc(TicketStatus.OPEN)
                .stream()
                .map(t -> {
                    List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(t.getId());
                    return toResponse(t, lines);
                })
                .toList();
    }

    @Transactional
    public TicketResponse addLine(Long ticketId, Long productId, int qty) {
        Ticket t = getOpenTicket(ticketId);

        Product p = productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

        if (!p.isActive()) {
            throw new ConflictException("Product is inactive: " + productId);
        }

        TicketLine line = new TicketLine(t, p, qty);
        lineRepo.save(line);

        recalcTotal(t.getId());
        // refrescamos
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse updateLineQty(Long ticketId, Long lineId, int qty) {
        Ticket t = getOpenTicket(ticketId);

        TicketLine line = lineRepo.findByIdAndTicketId(lineId, ticketId)
                .orElseThrow(() -> new NotFoundException("Line not found: " + lineId + " (ticket " + ticketId + ")"));

        line.changeQty(qty);

        recalcTotal(ticketId);
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse removeLine(Long ticketId, Long lineId) {
        Ticket t = getOpenTicket(ticketId);

        TicketLine line = lineRepo.findByIdAndTicketId(lineId, ticketId)
                .orElseThrow(() -> new NotFoundException("Line not found: " + lineId + " (ticket " + ticketId + ")"));

        lineRepo.delete(line);

        recalcTotal(ticketId);
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse pay(Long ticketId) {
        Ticket t = getOpenTicket(ticketId);

        recalcTotal(ticketId);
        if (t.getTotalGrossCents() <= 0) {
            throw new ConflictException("Cannot pay an empty ticket");
        }

        t.markPaid();
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse cancel(Long ticketId) {
        Ticket t = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        if (t.getStatus() == TicketStatus.PAID) {
            throw new ConflictException("Cannot cancel a PAID ticket");
        }

        t.cancel();
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    // ================= helpers =================
    private Ticket getOpenTicket(Long id) {
        Ticket t = ticketRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + id));

        if (t.getStatus() != TicketStatus.OPEN) {
            throw new ConflictException("Ticket is not OPEN: " + id);
        }
        if (t.getCashSession().getStatus() != CashSessionStatus.OPEN) {
            throw new ConflictException("Cash session is closed for this ticket");
        }
        return t;
    }

    private void recalcTotal(Long ticketId) {
        Ticket t = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        int total = lineRepo.sumGrossByTicketId(ticketId);
        t.setTotalCents(total);
    }

    private TicketResponse toResponse(Ticket t, List<TicketLine> lines) {
        List<TicketLineResponse> lineDtos = lines.stream()
                .map(this::toLineResponse)
                .toList();

        return new TicketResponse(
                t.getId(),
                t.getStatus(),
                t.getTotalCents(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                lineDtos
        );
    }

    private TicketLineResponse toLineResponse(TicketLine l) {
        return new TicketLineResponse(
                l.getId(),
                l.getProduct().getId(),
                l.getProductNameSnapshot(),
                l.getUnitPriceCentsSnapshot(),
                l.getQty(),
                l.getLineTotalCents(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PaymentSummaryResponse paymentSummary(Long ticketId) {
        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        int total = lineRepo.sumGrossByTicketId(ticketId);
        int paid = paymentRepo.sumAmountCentsByTicketId(ticketId);
        int pending = Math.max(0, total - paid);

        return new PaymentSummaryResponse(ticket.getId(), total, paid, pending);

    }

    @Transactional(readOnly = true)
    public TicketSummaryResponse ticketSummary(Long ticketId) {

        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        // Líneas
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);

        // Pagos
        List<Payment> payments = paymentRepo.findByTicketId(ticketId);

        // Totales
        int paid = paymentRepo.sumAmountCentsByTicketId(ticketId);
        int remaining = Math.max(0, ticket.getTotalCents() - paid);

        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getTotalCents(),
                paid,
                remaining,
                ticket.getCreatedAt(),
                lines.stream()
                        .map(l -> new TicketSummaryResponse.TicketLineSummary(
                        l.getId(),
                        l.getProduct().getId(),
                        l.getProductNameSnapshot(),
                        l.getUnitPriceCentsSnapshot(),
                        l.getQty(),
                        l.getLineTotalCents()
                ))
                        .toList(),
                payments.stream()
                        .map(p -> new TicketSummaryResponse.PaymentSummary(
                        p.getId(),
                        p.getMethod(),
                        p.getAmountCents(),
                        p.getCreatedAt()
                ))
                        .toList()
        );
    }
}
