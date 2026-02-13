package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.domain.PaymentMethod;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.CashSessionOpenTicketResponse;
import com.tpv.pos_service.dto.CashSessionCloseSummaryResponse;
import com.tpv.pos_service.dto.CashSessionResponse;
import com.tpv.pos_service.dto.CloseCashSessionRequest;
import com.tpv.pos_service.dto.OpenCashSessionRequest;
import com.tpv.pos_service.dto.ResolveOpenTicketsResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.PaymentRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class CashSessionService {

    private final CashSessionRepository repo;
    private final PaymentRepository paymentRepo;
    private final TicketRepository ticketRepo;
    private final CashIncidentService cashIncidentService;
    private final FiscalService fiscalService;
    private final IdempotencyService idempotencyService;

    public CashSessionService(
            CashSessionRepository repo,
            PaymentRepository paymentRepo,
            TicketRepository ticketRepo,
            CashIncidentService cashIncidentService,
            FiscalService fiscalService,
            IdempotencyService idempotencyService
    ) {
        this.repo = repo;
        this.paymentRepo = paymentRepo;
        this.ticketRepo = ticketRepo;
        this.cashIncidentService = cashIncidentService;
        this.fiscalService = fiscalService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional(readOnly = true)
    public CashSessionResponse current() {
        CashSession cs = repo.findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("No open cash session"));
        int cashPaidCents = paymentRepo.sumByCashSessionAndMethod(cs.getId(), PaymentMethod.CASH);
        int incidentsNetCents = cashIncidentService.sumNetIncidentsCents(cs.getId());
        int expected = cs.getOpeningCashCents() + cashPaidCents + incidentsNetCents;
        return toResponse(cs, expected);
    }

    @Transactional
    public CashSessionResponse open(OpenCashSessionRequest req, String openedBy) {
        if (repo.existsByStatus(CashSessionStatus.OPEN)) {
            throw new ConflictException("There is already an open cash session");
        }
        if (req.openingCashCents() < 0) {
            throw new ConflictException("openingCashCents must be >= 0");
        }
        CashSession cs = new CashSession(req.openingCashCents(), openedBy, req.note());
        int expected = req.openingCashCents();
        cs.setExpectedCashCents(expected);
        cs = repo.save(cs);
        return toResponse(cs, expected);
    }

    @Transactional
    public CashSessionResponse close(Long id, CloseCashSessionRequest req, String closedBy, String idempotencyKey) {
        return idempotencyService.execute(
                "cash-close",
                id,
                idempotencyKey,
                CashSessionResponse.class,
                () -> doClose(id, req, closedBy)
        );
    }

    @Transactional
    public CashSessionResponse close(Long id, CloseCashSessionRequest req, String closedBy) {
        return close(id, req, closedBy, null);
    }

    @Transactional(readOnly = true)
    public CashSessionCloseSummaryResponse closeSummary(Long id) {
        CashSession cs = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cash session not found: " + id));

        int cashPaidCents = paymentRepo.sumByCashSessionAndMethod(id, PaymentMethod.CASH);
        int incidentsIn = cashIncidentService.sumIncidentsInCents(id);
        int incidentsOut = cashIncidentService.sumIncidentsOutCents(id);
        int incidentsNet = incidentsIn - incidentsOut;
        int expected = cs.getOpeningCashCents() + cashPaidCents + incidentsNet;

        return new CashSessionCloseSummaryResponse(
                cs.getId(),
                cs.getOpenedAt(),
                cs.getClosedAt(),
                cs.getOpeningCashCents(),
                expected,
                cs.getClosingCashCents(),
                cs.getClosingCashCents() == null ? null : (cs.getClosingCashCents() - expected),
                cashPaidCents,
                incidentsIn,
                incidentsOut,
                incidentsNet,
                fiscalService.summary(id)
        );
    }

    @Transactional(readOnly = true)
    public List<CashSessionOpenTicketResponse> openTickets(Long id) {
        repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cash session not found: " + id));

        return ticketRepo.findAllByCashSession_IdAndStatus(id, TicketStatus.OPEN).stream()
                .map(t -> new CashSessionOpenTicketResponse(
                        t.getId(),
                        t.getTableNumber(),
                        t.getTotalCents(),
                        t.getCreatedAt()))
                .toList();
    }

    @Transactional
    public ResolveOpenTicketsResponse resolveOpenTickets(Long id) {
        repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cash session not found: " + id));

        List<Long> autoCancelled = new ArrayList<>();
        List<CashSessionOpenTicketResponse> remaining = new ArrayList<>();

        var openTickets = ticketRepo.findAllByCashSession_IdAndStatus(id, TicketStatus.OPEN);
        int openBefore = openTickets.size();

        for (var ticket : openTickets) {
            if (ticket.getTotalCents() <= 0) {
                ticket.cancel();
                autoCancelled.add(ticket.getId());
            } else {
                remaining.add(new CashSessionOpenTicketResponse(
                        ticket.getId(),
                        ticket.getTableNumber(),
                        ticket.getTotalCents(),
                        ticket.getCreatedAt()));
            }
        }

        return new ResolveOpenTicketsResponse(
                id,
                openBefore,
                autoCancelled.size(),
                remaining.size(),
                autoCancelled,
                remaining
        );
    }

    private CashSessionResponse doClose(Long id, CloseCashSessionRequest req, String closedBy) {
        CashSession cs = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cash session not found: " + id));

        if (cs.getStatus() != CashSessionStatus.OPEN) {
            throw new ConflictException("Only OPEN cash session can be closed");
        }
        if (req.closingCashCents() < 0) {
            throw new ConflictException("closingCashCents must be >= 0");
        }
        if (ticketRepo.existsByCashSession_IdAndStatus(id, TicketStatus.OPEN)) {
            throw new ConflictException("Cannot close cash session with OPEN tickets");
        }

        int cashPaidCents = paymentRepo.sumByCashSessionAndMethod(id, PaymentMethod.CASH);
        int incidentsNetCents = cashIncidentService.sumNetIncidentsCents(id);
        int expected = cs.getOpeningCashCents() + cashPaidCents + incidentsNetCents;

        cs.setExpectedCashCents(expected);
        cs.close(req.closingCashCents(), closedBy, req.note());
        return toResponse(cs, expected);
    }

    private CashSessionResponse toResponse(CashSession cs, int expectedCashCents) {
        Integer diff = (cs.getClosingCashCents() == null)
                ? null
                : (cs.getClosingCashCents() - expectedCashCents);

        return new CashSessionResponse(
                cs.getId(),
                cs.getStatus(),
                cs.getOpeningCashCents(),
                expectedCashCents,
                cs.getClosingCashCents(),
                diff,
                cs.getOpenedAt(),
                cs.getClosedAt(),
                cs.getOpenedBy(),
                cs.getClosedBy(),
                cs.getNote()
        );
    }
}
