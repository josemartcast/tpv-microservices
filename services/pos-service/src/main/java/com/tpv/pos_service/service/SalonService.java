package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.SalonArea;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.domain.TableLock;
import com.tpv.pos_service.dto.SalonTableResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalonService {

    private final TicketRepository ticketRepo;
    private final TicketLineRepository lineRepo;
    private final TicketService ticketService;
    private final TableLockService lockService;
    private final SalonAreaService salonAreaService;
    private final TableAliasService tableAliasService;

    public SalonService(
            TicketRepository ticketRepo,
            TicketLineRepository lineRepo,
            TicketService ticketService,
            TableLockService lockService,
            SalonAreaService salonAreaService,
            TableAliasService tableAliasService
    ) {
        this.ticketRepo = ticketRepo;
        this.lineRepo = lineRepo;
        this.ticketService = ticketService;
        this.lockService = lockService;
        this.salonAreaService = salonAreaService;
        this.tableAliasService = tableAliasService;
    }

    @Transactional
    public List<SalonTableResponse> listTables() {
        List<SalonTableResponse> result = new ArrayList<>();
        List<SalonArea> salons = salonAreaService.ensureDefaultIfEmpty();
        List<Integer> allTables = new ArrayList<>();
        for (SalonArea salon : salons) {
            for (int i = salon.getFirstTableNumber(); i <= salon.getLastTableNumber(); i++) {
                allTables.add(i);
            }
        }
        Map<Integer, String> aliases = new HashMap<>(tableAliasService.aliasesByTables(allTables));

        for (SalonArea salon : salons) {
            for (int i = salon.getFirstTableNumber(); i <= salon.getLastTableNumber(); i++) {
                Ticket open = ticketRepo.findByTableNumberAndStatus(i, TicketStatus.OPEN).orElse(null);
                TableLock lock = lockService.activeLock(i);
                String alias = aliases.getOrDefault(i, "");
                if (open == null) {
                    String status = lock == null ? "FREE" : "LOCKED";
                    result.add(new SalonTableResponse(
                            i, salon.getName(), alias, status, null, 0, 0, 0,
                            null,
                            null,
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
                        : (open.isBillRequested() ? "PRECUENTA_PEDIDA" : (pendingLines > 0 ? "PENDING_SEND" : "OCCUPIED"));
                result.add(new SalonTableResponse(
                        i, salon.getName(), alias, status, open.getId(), open.getTotalCents(), elapsed, pendingLines,
                        open.getBillRequestedBy(),
                        open.getBillRequestedTerminalId(),
                        lock == null ? null : lock.getLockedBy(),
                        lock == null ? null : lock.getTerminalId(),
                        lock == null ? null : lock.getExpiresAt()
                ));
            }
        }
        return result;
    }

    @Transactional
    public boolean tableExistsInActiveSalon(int tableNumber) {
        List<SalonArea> salons = salonAreaService.ensureDefaultIfEmpty();
        for (SalonArea salon : salons) {
            if (salon.containsTable(tableNumber)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public String salonNameByTable(int tableNumber) {
        List<SalonArea> salons = salonAreaService.ensureDefaultIfEmpty();
        for (SalonArea salon : salons) {
            if (salon.containsTable(tableNumber)) {
                return salon.getName();
            }
        }
        return "Salon";
    }

    @Transactional
    public com.tpv.pos_service.dto.TicketResponse openTicketForTable(int tableNumber) {
        if (!tableExistsInActiveSalon(tableNumber)) {
            throw new ConflictException("Table is not configured in any active salon: " + tableNumber);
        }
        if (ticketRepo.existsByTableNumberAndStatus(tableNumber, TicketStatus.OPEN)) {
            throw new ConflictException("Table already has an OPEN ticket: " + tableNumber);
        }
        return ticketService.create(tableNumber);
    }
}
