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
import java.util.ArrayList;
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
        List<TicketLineResponse> pending = buildPendingItems(ticketId)
                .stream()
                .map(PendingComandaItem::preview)
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
        List<PendingComandaItem> selected = buildPendingItems(ticketId).stream()
                .filter(item -> "ALL".equals(normalized) || normalized.equals(item.preview().destination()))
                .toList();

        selected.forEach(item -> {
            TicketLine line = item.line();
            if (!line.isSent()) {
                line.markSent();
                return;
            }
            if (line.isRemovedAfterSent() && line.getQty() == 0) {
                lineRepo.delete(line);
                return;
            }
            line.markSent();
        });

        List<Long> ids = selected.stream().map(item -> item.line().getId()).toList();
        return new SendComandaResponse(ticketId, normalized, ids.size(), ids);
    }

    private List<PendingComandaItem> buildPendingItems(Long ticketId) {
        List<TicketLine> allLines = lineRepo.findAllByTicketIdOrderByIdAsc(ticketId);
        List<PendingComandaItem> out = new ArrayList<>();
        for (TicketLine line : allLines) {
            if (!line.isSent()) {
                out.add(new PendingComandaItem(line, toLineResponse(line)));
                continue;
            }
            if (line.hasPendingComandaAdjustment()) {
                out.add(new PendingComandaItem(line, toAdjustmentPreview(line)));
            }
        }
        return out;
    }

    private TicketLineResponse toLineResponse(TicketLine l) {
        return new TicketLineResponse(
                l.getId(),
                l.getProduct().getId(),
                l.getProductNameSnapshot(),
                l.getNote(),
                l.getDestinationSnapshot(),
                l.isSent(),
                l.getUnitPriceCentsSnapshot(),
                l.getQty(),
                l.getLineTotalCents(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }

    private TicketLineResponse toAdjustmentPreview(TicketLine line) {
        String baseName = line.getProductNameSnapshot() == null ? "-" : line.getProductNameSnapshot();
        int deltaQty = line.getQty() - line.getSentQtySnapshot();
        boolean priceChanged = line.getUnitPriceCentsSnapshot() != line.getSentUnitPriceCentsSnapshot();

        String actionPrefix;
        int adjustmentQty;
        if (line.isRemovedAfterSent() && line.getQty() == 0) {
            actionPrefix = "ELIM ";
            adjustmentQty = Math.max(1, line.getSentQtySnapshot());
        } else if (deltaQty > 0) {
            actionPrefix = "";
            adjustmentQty = deltaQty;
        } else if (deltaQty < 0) {
            actionPrefix = "ELIM ";
            adjustmentQty = Math.abs(deltaQty);
        } else {
            actionPrefix = "MOD PRECIO ";
            adjustmentQty = Math.max(1, line.getQty());
        }

        String productName = actionPrefix + baseName + (priceChanged ? " (precio)" : "");
        int unitPrice = Math.max(0, line.getUnitPriceCentsSnapshot());
        int lineTotal = adjustmentQty * unitPrice;

        return new TicketLineResponse(
                line.getId(),
                line.getProduct().getId(),
                productName,
                line.getNote(),
                line.getDestinationSnapshot(),
                false,
                unitPrice,
                adjustmentQty,
                lineTotal,
                line.getCreatedAt(),
                line.getUpdatedAt()
        );
    }

    private String normalizeDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return "ALL";
        }
        String d = destination.trim().toUpperCase();
        if (!d.equals("ALL") && !d.equals("BAR") && !d.equals("COCINA") && !d.equals("POSTRES")) {
            throw new ConflictException("Unsupported destination: " + destination);
        }
        return d;
    }

    private record PendingComandaItem(TicketLine line, TicketLineResponse preview) {
    }
}
