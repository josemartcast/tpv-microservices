package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.domain.FiscalExercise;
import com.tpv.pos_service.domain.FiscalExerciseStatus;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.CloseFiscalExerciseRequest;
import com.tpv.pos_service.dto.FiscalExerciseResponse;
import com.tpv.pos_service.dto.OpenFiscalExerciseRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.FiscalExerciseRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.time.Year;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class FiscalExerciseService {

    private final FiscalExerciseRepository repo;
    private final CashSessionRepository cashSessionRepo;
    private final TicketRepository ticketRepo;

    public FiscalExerciseService(
            FiscalExerciseRepository repo,
            CashSessionRepository cashSessionRepo,
            TicketRepository ticketRepo
    ) {
        this.repo = repo;
        this.cashSessionRepo = cashSessionRepo;
        this.ticketRepo = ticketRepo;
    }

    @Transactional(readOnly = true)
    public FiscalExerciseResponse current() {
        FiscalExercise open = repo.findFirstByStatusOrderByFiscalYearDesc(FiscalExerciseStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("No open fiscal exercise"));
        return toResponse(open);
    }

    @Transactional(readOnly = true)
    public List<FiscalExerciseResponse> list() {
        return repo.findAllByOrderByFiscalYearDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FiscalExerciseResponse open(OpenFiscalExerciseRequest req, String actor) {
        int year = req.fiscalYear();
        if (year < 2000 || year > 2100) {
            throw new ConflictException("Fiscal year out of range: " + year);
        }
        if (year > Year.now().getValue() + 1) {
            throw new ConflictException("Fiscal year cannot be greater than next calendar year");
        }
        if (repo.existsByStatus(FiscalExerciseStatus.OPEN)) {
            throw new ConflictException("There is already an open fiscal exercise");
        }
        if (repo.existsByFiscalYear(year)) {
            throw new ConflictException("Fiscal exercise already exists: " + year);
        }
        FiscalExercise created = repo.save(new FiscalExercise(year, actor, req.note()));
        return toResponse(created);
    }

    @Transactional
    public FiscalExerciseResponse close(Long id, CloseFiscalExerciseRequest req, String actor) {
        FiscalExercise exercise = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Fiscal exercise not found: " + id));

        if (exercise.getStatus() != FiscalExerciseStatus.OPEN) {
            throw new ConflictException("Fiscal exercise is already closed: " + exercise.getFiscalYear());
        }
        if (cashSessionRepo.existsByStatus(CashSessionStatus.OPEN)) {
            throw new ConflictException("Cannot close fiscal exercise with OPEN cash session");
        }
        if (ticketRepo.existsByStatus(TicketStatus.OPEN)) {
            throw new ConflictException("Cannot close fiscal exercise with OPEN tickets");
        }

        exercise.close(actor, req == null ? null : req.note());
        return toResponse(exercise);
    }

    private FiscalExerciseResponse toResponse(FiscalExercise exercise) {
        return new FiscalExerciseResponse(
                exercise.getId(),
                exercise.getFiscalYear(),
                exercise.getStatus(),
                exercise.getOpenedAt(),
                exercise.getClosedAt(),
                exercise.getOpenedBy(),
                exercise.getClosedBy(),
                exercise.getNote()
        );
    }
}

