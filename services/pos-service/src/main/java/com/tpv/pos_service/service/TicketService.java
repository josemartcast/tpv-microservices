package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketLine;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.*;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.ProductRepository;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public CreateTicketResponse create() {
        Ticket t = new Ticket();
        t = ticketRepo.save(t);
        return toCreateResponse(t);
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long id) {
        Ticket t = ticketRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Ticket not found: " + id));

        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(id);
        return toResponse(t, lines);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listByStatus(String status) {
        TicketStatus st;
        try {
            st = TicketStatus.valueOf(status.toUpperCase());
        } catch (Exception e) {
            throw new ConflictException("Invalid status: " + status);
        }

        return ticketRepo.findAllByStatusOrderByCreatedAtDesc(st).stream()
            .map(t -> toResponse(t, lineRepo.findAllByTicketIdOrderByIdAsc(t.getId())))
            .toList();
    }

    @Transactional
    public TicketResponse addLine(Long ticketId, AddLineRequest req) {
        Ticket t = mustBeOpen(ticketId);

        Product p = productRepo.findById(req.productId())
            .orElseThrow(() -> new NotFoundException("Product not found: " + req.productId()));

        if (!p.isActive()) {
            throw new ConflictException("Product is inactive: " + p.getId());
        }

        TicketLine line = new TicketLine(t, p, req.qty());
        lineRepo.save(line);

        recalcTotal(t);

        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse updateLineQty(Long ticketId, Long lineId, UpdateLineQtyRequest req) {
        Ticket t = mustBeOpen(ticketId);

        TicketLine line = lineRepo.findByIdAndTicketId(lineId, ticketId)
            .orElseThrow(() -> new NotFoundException("Line not found: " + lineId + " for ticket " + ticketId));

        line.changeQty(req.qty());
        recalcTotal(t);

        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse deleteLine(Long ticketId, Long lineId) {
        Ticket t = mustBeOpen(ticketId);

        TicketLine line = lineRepo.findByIdAndTicketId(lineId, ticketId)
            .orElseThrow(() -> new NotFoundException("Line not found: " + lineId + " for ticket " + ticketId));

        lineRepo.delete(line);
        recalcTotal(t);

        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse pay(Long ticketId) {
        Ticket t = mustBeOpen(ticketId);

        // opcional: impedir pagar ticket vacío
        if (t.getTotalCents() <= 0) {
            throw new ConflictException("Cannot pay an empty ticket.");
        }

        t.markPaid();
        // ticketRepo.save(t); // no hace falta, está gestionado por JPA
        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    @Transactional
    public TicketResponse cancel(Long ticketId) {
        Ticket t = mustBeOpen(ticketId);
        t.cancel();

        List<TicketLine> lines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        return toResponse(t, lines);
    }

    private Ticket mustBeOpen(Long ticketId) {
        Ticket t = ticketRepo.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));

        if (!t.isOpen()) {
            throw new ConflictException("Ticket is not OPEN: " + t.getStatus());
        }
        return t;
    }

    private void recalcTotal(Ticket t) {
        int total = lineRepo.findAllByTicketIdOrderByIdAsc(t.getId()).stream()
            .mapToInt(TicketLine::getLineTotalCents)
            .sum();
        t.setTotalCents(total);
    }

    private CreateTicketResponse toCreateResponse(Ticket t) {
        return new CreateTicketResponse(
            t.getId(),
            t.getStatus().name(),
            t.getTotalCents(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
    }

    private TicketResponse toResponse(Ticket t, List<TicketLine> lines) {
        List<TicketLineResponse> lr = lines.stream()
            .map(l -> new TicketLineResponse(
                l.getId(),
                l.getProduct().getId(),
                l.getProductNameSnapshot(),
                l.getUnitPriceCentsSnapshot(),
                l.getQty(),
                l.getLineTotalCents()
            ))
            .toList();

        return new TicketResponse(
            t.getId(),
            t.getStatus().name(),
            t.getTotalCents(),
            t.getCreatedAt(),
            t.getUpdatedAt(),
            lr
        );
    }
}
