package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.SalonArea;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.CreateSalonAreaRequest;
import com.tpv.pos_service.dto.SalonAreaResponse;
import com.tpv.pos_service.dto.UpdateSalonAreaRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.SalonAreaRepository;
import com.tpv.pos_service.repository.TableLockRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalonAreaService {

    private final SalonAreaRepository salonAreaRepo;
    private final TicketRepository ticketRepo;
    private final TableLockRepository tableLockRepo;
    private final TableAliasService tableAliasService;

    public SalonAreaService(
            SalonAreaRepository salonAreaRepo,
            TicketRepository ticketRepo,
            TableLockRepository tableLockRepo,
            TableAliasService tableAliasService
    ) {
        this.salonAreaRepo = salonAreaRepo;
        this.ticketRepo = ticketRepo;
        this.tableLockRepo = tableLockRepo;
        this.tableAliasService = tableAliasService;
    }

    @Transactional(readOnly = true)
    public List<SalonAreaResponse> listActive() {
        return salonAreaRepo.findAllByActiveTrueOrderByFirstTableNumberAsc()
                .stream()
                .map(SalonAreaService::toResponse)
                .toList();
    }

    @Transactional
    public List<SalonArea> ensureDefaultIfEmpty() {
        List<SalonArea> salons = salonAreaRepo.findAllByActiveTrueOrderByFirstTableNumberAsc();
        if (!salons.isEmpty()) {
            return salons;
        }
        SalonArea fallback = salonAreaRepo.save(new SalonArea("Salon", 1, 12));
        return List.of(fallback);
    }

    @Transactional
    public SalonAreaResponse create(CreateSalonAreaRequest request) {
        if (request.tableCount() == null) {
            throw new IllegalArgumentException("tableCount is required");
        }
        String name = normalizeName(request.name());
        if (salonAreaRepo.existsByNameIgnoreCaseAndActiveTrue(name)) {
            throw new ConflictException("Salon name already exists: " + name);
        }

        int tableCount = request.tableCount();
        int firstTable = request.firstTableNumber() == null
                ? nextAvailableTableNumber()
                : request.firstTableNumber();
        int lastTable = firstTable + tableCount - 1;
        validateRange(firstTable, lastTable);

        List<SalonArea> overlaps = salonAreaRepo.findOverlappingActiveRange(firstTable, lastTable);
        if (!overlaps.isEmpty()) {
            throw new ConflictException("Table range overlaps with another salon");
        }

        SalonArea created = salonAreaRepo.save(new SalonArea(name, firstTable, tableCount));
        return toResponse(created);
    }

    @Transactional
    public SalonAreaResponse rename(Long id, UpdateSalonAreaRequest request) {
        String name = normalizeName(request.name());
        SalonArea salon = salonAreaRepo.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Salon not found: " + id));

        if (!salon.getName().equalsIgnoreCase(name)
                && salonAreaRepo.existsByNameIgnoreCaseAndActiveTrue(name)) {
            throw new ConflictException("Salon name already exists: " + name);
        }

        salon.rename(name);
        return toResponse(salon);
    }

    @Transactional
    public void delete(Long id) {
        SalonArea salon = salonAreaRepo.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Salon not found: " + id));

        List<Integer> tableNumbers = tableNumbersOf(salon);
        for (Integer table : tableNumbers) {
            if (ticketRepo.existsByTableNumberAndStatus(table, TicketStatus.OPEN)) {
                throw new ConflictException("Cannot delete salon with OPEN tickets (table " + table + ")");
            }
            if (tableLockRepo.findByTableNumber(table).isPresent()) {
                throw new ConflictException("Cannot delete salon with active locks (table " + table + ")");
            }
        }

        tableAliasService.clearAliasesForTables(tableNumbers);
        salon.deactivate();
    }

    private int nextAvailableTableNumber() {
        List<SalonArea> salons = salonAreaRepo.findAllByActiveTrueOrderByFirstTableNumberAsc();
        if (salons.isEmpty()) {
            return 1;
        }
        int max = salons.get(salons.size() - 1).getLastTableNumber();
        return max + 1;
    }

    private static List<Integer> tableNumbersOf(SalonArea salon) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = salon.getFirstTableNumber(); i <= salon.getLastTableNumber(); i++) {
            numbers.add(i);
        }
        return numbers;
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.trim().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        return raw.trim();
    }

    private static void validateRange(int firstTable, int lastTable) {
        if (firstTable < 1 || firstTable > 500 || lastTable < firstTable || lastTable > 500) {
            throw new ConflictException("Invalid table range");
        }
    }

    private static SalonAreaResponse toResponse(SalonArea salon) {
        return new SalonAreaResponse(
                salon.getId(),
                salon.getName(),
                salon.getFirstTableNumber(),
                salon.getTableCount(),
                salon.getLastTableNumber(),
                salon.isActive(),
                salon.getCreatedAt(),
                salon.getUpdatedAt()
        );
    }
}
