package com.tpv.pos_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketLine;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.TicketLineResponse;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.ProductRepository;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;

@Service
public class TicketService {

    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final ProductRepository productRepo;

    public TicketService(TicketRepository ticketRepo, TicketLineRepository lineRepo, ProductRepository productRepo) {
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.productRepo = productRepo;
    }

    @Transactional
    public TicketResponse create() {
        Ticket t = ticketRepo.save(new Ticket());
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

    // ================= helpers =================

    private Ticket getOpenTicket(Long id) {
        Ticket t = ticketRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Ticket not found: " + id));

        if (t.getStatus() != TicketStatus.OPEN) {
            throw new ConflictException("Ticket is not OPEN: " + id);
        }
        return t;
    }

    private void recalcTotal(Long ticketId) {
        Ticket t = ticketRepo.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        int total = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId)
            .stream()
            .mapToInt(TicketLine::getLineTotalCents)
            .sum();

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
}
