package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.domain.TableLock;
import com.tpv.pos_service.dto.SalonTableResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalonService {

    private static final int MAX_TABLES = 12;

    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final TicketService ticketService;
    private final TableLockService lockService;

    public SalonService(TicketRepository ticketRepo, TicketLineRepository lineRepo, TicketService ticketService, TableLockService lockService) {
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.ticketService = ticketService;
        this.lockService = lockService;
    }

    @Transactional(readOnly = true)
    public List<SalonTableResponse> listTables() {
        List<SalonTableResponse> result = new ArrayList<>();
        for (int i = 1; i <= MAX_TABLES; i++) {
            Ticket open = ticketRepo.findByTableNumberAndStatus(i, TicketStatus.OPEN).orElse(null);
            TableLock lock = lockService.activeLock(i);
            if (open == null) {
                String status = lock == null ? "FREE" : "LOCKED";
                result.add(new SalonTableResponse(
                        i, status, null, 0, 0, 0,
                        lock == null ? null : lock.getLockedBy(),
                        lock == null ? null : lock.getTerminalId(),
                        lock == null ? null : lock.getExpiresAt()
                ));
                continue;
            }
            int elapsed = open.getCreatedAt() == null
                    ? 0
                    : (int) Math.max(0, Duration.between(open.getCreatedAt(), Instant.now()).toMinutes());
            int pendingLines = (int) lineRepo.countPendingByTicketId(open.getId());
            String status = lock != null
                    ? "LOCKED"
                    : (open.isBillRequested() ? "BILL_REQUESTED" : (pendingLines > 0 ? "PENDING_SEND" : "OCCUPIED"));
            result.add(new SalonTableResponse(
                    i, status, open.getId(), open.getTotalCents(), elapsed, pendingLines,
                    lock == null ? null : lock.getLockedBy(),
                    lock == null ? null : lock.getTerminalId(),
                    lock == null ? null : lock.getExpiresAt()
            ));
        }
        return result;
    }

    @Transactional
    public com.tpv.pos_service.dto.TicketResponse openTicketForTable(int tableNumber) {
        if (tableNumber < 1 || tableNumber > MAX_TABLES) {
            throw new ConflictException("Table out of range: " + tableNumber);
        }
        if (ticketRepo.existsByTableNumberAndStatus(tableNumber, TicketStatus.OPEN)) {
            throw new ConflictException("Table already has an OPEN ticket: " + tableNumber);
        }
        return ticketService.create(tableNumber);
    }
}
