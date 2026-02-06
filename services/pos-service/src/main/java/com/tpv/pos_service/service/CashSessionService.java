package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.domain.PaymentMethod;
import com.tpv.pos_service.dto.*;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashSessionService {

    private final CashSessionRepository repo;
    private final PaymentRepository paymentRepo;

    public CashSessionService(CashSessionRepository repo, PaymentRepository paymentRepo) {
        this.repo = repo;
        this.paymentRepo = paymentRepo;
    }

    @Transactional(readOnly = true)
    public CashSessionResponse current() {
        CashSession cs = repo.findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("No open cash session"));
        int cashPaidCents = paymentRepo.sumByCashSessionAndMethod(cs.getId(), PaymentMethod.CASH);
        int expected = cs.getOpeningCashCents() + cashPaidCents;
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
    public CashSessionResponse close(Long id, CloseCashSessionRequest req, String closedBy) {
        CashSession cs = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cash session not found: " + id));

        if (cs.getStatus() != CashSessionStatus.OPEN) {
            throw new ConflictException("Only OPEN cash session can be closed");
        }
        if (req.closingCashCents() < 0) {
            throw new ConflictException("closingCashCents must be >= 0");
        }

        int cashPaidCents = paymentRepo.sumByCashSessionAndMethod(id, PaymentMethod.CASH);
        int expected = cs.getOpeningCashCents() + cashPaidCents;

        cs.setExpectedCashCents(expected); // lo guardas para auditoría

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
