package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketLine;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.SendComandaResponse;
import com.tpv.pos_service.dto.SendPreviewResponse;
import com.tpv.pos_service.dto.TicketLineResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class ComandaService {

    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final IdempotencyService idempotencyService;

    public ComandaService(TicketRepository ticketRepo, TicketLineRepository lineRepo, IdempotencyService idempotencyService) {
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.idempotencyService = idempotencyService;
    }

    @Transactional(readOnly = true)
    public SendPreviewResponse preview(Long ticketId) {
        Ticket t = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
        if (t.getStatus() != TicketStatus.OPEN) {
            throw new ConflictException("Ticket is not OPEN: " + ticketId);
        }
        List<TicketLineResponse> pending = lineRepo.findAllByTicketIdAndSentFalseOrderByIdAsc(ticketId)
                .stream()
                .map(this::toLineResponse)
                .toList();
        return new SendPreviewResponse(ticketId, pending);
    }

    @Transactional
    public SendComandaResponse send(Long ticketId, String destination, String idempotencyKey) {
        return idempotencyService.execute(
                "ticket-send",
                ticketId,
                idempotencyKey,
                SendComandaResponse.class,
                () -> doSend(ticketId, destination)
        );
    }

    @Transactional
    public SendComandaResponse send(Long ticketId, String destination) {
        return send(ticketId, destination, null);
    }

    private SendComandaResponse doSend(Long ticketId, String destination) {
        Ticket t = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
        if (t.getStatus() != TicketStatus.OPEN) {
            throw new ConflictException("Ticket is not OPEN: " + ticketId);
        }

        String normalized = normalizeDestination(destination);
        List<TicketLine> pending = lineRepo.findAllByTicketIdAndSentFalseOrderByIdAsc(ticketId);
        List<TicketLine> selected = pending.stream()
                .filter(l -> "ALL".equals(normalized) || normalized.equals(destinationFor(l)))
                .toList();

        selected.forEach(TicketLine::markSent);
        List<Long> ids = selected.stream().map(TicketLine::getId).toList();
        return new SendComandaResponse(ticketId, normalized, ids.size(), ids);
    }

    private TicketLineResponse toLineResponse(TicketLine l) {
        return new TicketLineResponse(
                l.getId(),
                l.getProduct().getId(),
                l.getProductNameSnapshot(),
                destinationFor(l),
                l.isSent(),
                l.getUnitPriceCentsSnapshot(),
                l.getQty(),
                l.getLineTotalCents(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }

    private String normalizeDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return "ALL";
        }
        String d = destination.trim().toUpperCase();
        if (!d.equals("ALL") && !d.equals("BAR") && !d.equals("COCINA")) {
            throw new ConflictException("Unsupported destination: " + destination);
        }
        return d;
    }

    private String destinationFor(TicketLine line) {
        String p = line.getProductNameSnapshot() == null ? "" : line.getProductNameSnapshot().toLowerCase();
        if (p.contains("cerveza") || p.contains("refresco") || p.contains("vino")
                || p.contains("cafe") || p.contains("agua") || p.contains("cola")
                || p.contains("beer") || p.contains("drink")) {
            return "BAR";
        }
        return "COCINA";
    }
}
