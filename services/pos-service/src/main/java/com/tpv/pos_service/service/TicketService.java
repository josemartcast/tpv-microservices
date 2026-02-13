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
import com.tpv.pos_service.dto.ApplyDiscountRequest;
import com.tpv.pos_service.domain.TableLock;

@Service
@SuppressWarnings("null")
public class TicketService {

    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final ProductRepository productRepo;
    private final PaymentRepository paymentRepo;
    private final CashSessionRepository cashSessionRepo;
    private final TableLockService tableLockService;

    public TicketService(TicketRepository ticketRepo, TicketLineRepository lineRepo, ProductRepository productRepo, PaymentRepository paymentRepo, CashSessionRepository cashSessionRepo, TableLockService tableLockService) {
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.productRepo = productRepo;
        this.paymentRepo = paymentRepo;
        this.cashSessionRepo = cashSessionRepo;
        this.tableLockService = tableLockService;
    }

    @Transactional
    public TicketResponse create() {
        return create(null);
    }

    @Transactional
    public TicketResponse create(Integer tableNumber) {
        CashSession openSession = cashSessionRepo
                .findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN)
                .orElseThrow(() -> new ConflictException("No open cash session. Open a cash session first."));

        if (tableNumber != null && ticketRepo.existsByTableNumberAndStatus(tableNumber, TicketStatus.OPEN)) {
            throw new ConflictException("Table already has an OPEN ticket: " + tableNumber);
        }

        Ticket t = ticketRepo.save(new Ticket(openSession, tableNumber));
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
        if (line.isSent()) {
            throw new ConflictException("Cannot edit a sent line: " + lineId);
        }

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
        if (line.isSent()) {
            throw new ConflictException("Cannot delete a sent line: " + lineId);
        }

        lineRepo.delete(line);

        recalcTotal(ticketId);
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse pay(Long ticketId) {
        Ticket t = getOpenTicket(ticketId);

        recalcTotal(ticketId);
        if (t.getTotalCents() <= 0) {
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

    @Transactional
    public TicketResponse setBillRequested(Long ticketId, boolean requested) {
        Ticket t = getOpenTicket(ticketId);
        t.setBillRequested(requested);
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse moveTable(Long ticketId, Integer newTableNumber) {
        return moveTable(ticketId, newTableNumber, null, "system");
    }

    @Transactional
    public TicketResponse moveTable(Long ticketId, Integer newTableNumber, String terminalId, String actor) {
        if (newTableNumber == null || newTableNumber < 1 || newTableNumber > 200) {
            throw new ConflictException("Target table out of range: " + newTableNumber);
        }
        Ticket t = getOpenTicket(ticketId);
        Integer currentTable = t.getTableNumber();
        if (currentTable != null && currentTable.equals(newTableNumber)) {
            List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
            return toResponse(t, lines);
        }

        String moverTerminal = (terminalId == null || terminalId.isBlank())
                ? ("MOVE-" + ticketId)
                : terminalId.trim();
        String moverActor = (actor == null || actor.isBlank()) ? "system" : actor;

        TableLock activeLock = tableLockService.activeLock(newTableNumber);
        boolean releaseMutexAfterMove = activeLock == null;

        tableLockService.lock(newTableNumber, moverTerminal, moverActor);
        try {
            boolean targetInUse = ticketRepo.existsByTableNumberAndStatus(newTableNumber, TicketStatus.OPEN);
            if (targetInUse) {
                throw new ConflictException("Target table already has an OPEN ticket: " + newTableNumber);
            }
            t.setTableNumber(newTableNumber);
            List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
            return toResponse(t, lines);
        } finally {
            if (releaseMutexAfterMove) {
                try {
                    tableLockService.unlock(newTableNumber, moverTerminal, moverActor);
                } catch (RuntimeException _ignored) {
                    // best-effort unlock for transient move mutex
                }
            }
        }
    }

    @Transactional
    public TicketResponse applyDiscount(Long ticketId, ApplyDiscountRequest req) {
        Ticket t = getOpenTicket(ticketId);

        Integer percent = req == null ? null : req.percent();
        Integer amountCents = req == null ? null : req.amountCents();
        if (percent != null && amountCents != null) {
            throw new ConflictException("Use either percent or amountCents, not both.");
        }

        int gross = lineRepo.sumGrossByTicketId(ticketId);
        int net = lineRepo.sumNetByTicketId(ticketId);
        if (gross <= 0) {
            t.setDiscountCents(0);
            applyTotalsWithDiscount(t, 0, 0);
            List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
            return toResponse(t, lines);
        }

        int discount = 0;
        if (percent != null) {
            if (percent < 0 || percent > 100) {
                throw new ConflictException("Percent out of range: " + percent);
            }
            discount = (gross * percent) / 100;
        } else if (amountCents != null) {
            if (amountCents < 0) {
                throw new ConflictException("amountCents must be >= 0");
            }
            if (amountCents > gross) {
                throw new ConflictException("Discount exceeds ticket total.");
            }
            discount = amountCents;
        }

        t.setDiscountCents(discount);
        applyTotalsWithDiscount(t, gross, net);
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

        int gross = lineRepo.sumGrossByTicketId(ticketId);
        int net = lineRepo.sumNetByTicketId(ticketId);
        applyTotalsWithDiscount(t, gross, net);
    }

    private void applyTotalsWithDiscount(Ticket ticket, int gross, int net) {
        int safeGross = Math.max(0, gross);
        int safeNet = Math.max(0, Math.min(net, safeGross));
        int discount = Math.max(0, Math.min(ticket.getDiscountCents(), safeGross));

        int discountedGross = Math.max(0, safeGross - discount);
        int discountedNet;
        if (safeGross == 0) {
            discountedNet = 0;
        } else {
            discountedNet = (int) Math.round((safeNet * (double) discountedGross) / safeGross);
            discountedNet = Math.max(0, Math.min(discountedNet, discountedGross));
        }

        ticket.setTotalCents(discountedGross);
        ticket.setTotals(discountedGross, discountedNet);
    }

    private TicketResponse toResponse(Ticket t, List<TicketLine> lines) {
        List<TicketLineResponse> lineDtos = lines.stream()
                .map(this::toLineResponse)
                .toList();

        return new TicketResponse(
                t.getId(),
                t.getTableNumber(),
                t.getStatus(),
                t.isBillRequested(),
                lineDtos.stream().mapToInt(TicketLineResponse::lineTotalCents).sum(),
                t.getDiscountCents(),
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
                destinationFor(l.getProductNameSnapshot()),
                l.isSent(),
                l.getUnitPriceCentsSnapshot(),
                l.getQty(),
                l.getLineTotalCents(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }

    private String destinationFor(String productName) {
        if (productName == null) {
            return "COCINA";
        }
        String p = productName.toLowerCase();
        if (p.contains("cerveza") || p.contains("refresco") || p.contains("vino")
                || p.contains("cafe") || p.contains("agua") || p.contains("cola")
                || p.contains("beer") || p.contains("drink")) {
            return "BAR";
        }
        return "COCINA";
    }

    @Transactional(readOnly = true)
    public PaymentSummaryResponse paymentSummary(Long ticketId) {
        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        int gross = lineRepo.sumGrossByTicketId(ticketId);
        int discount = Math.max(0, Math.min(ticket.getDiscountCents(), gross));
        int total = Math.max(0, gross - discount);
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
        int gross = lineRepo.sumGrossByTicketId(ticketId);
        int discount = Math.max(0, Math.min(ticket.getDiscountCents(), gross));
        int total = Math.max(0, gross - discount);
        int paid = paymentRepo.sumAmountCentsByTicketId(ticketId);
        int remaining = Math.max(0, total - paid);

        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getStatus(),
                total,
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
