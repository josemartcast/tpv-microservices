package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashIncident;
import com.tpv.pos_service.domain.CashIncidentDirection;
import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.dto.CashIncidentResponse;
import com.tpv.pos_service.dto.CreateCashIncidentRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CashIncidentRepository;
import com.tpv.pos_service.repository.CashSessionRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class CashIncidentService {

    private final CashSessionRepository cashSessionRepository;
    private final CashIncidentRepository cashIncidentRepository;

    public CashIncidentService(
            CashSessionRepository cashSessionRepository,
            CashIncidentRepository cashIncidentRepository
    ) {
        this.cashSessionRepository = cashSessionRepository;
        this.cashIncidentRepository = cashIncidentRepository;
    }

    @Transactional(readOnly = true)
    public List<CashIncidentResponse> listForCashSession(Long cashSessionId) {
        cashSessionRepository.findById(cashSessionId)
                .orElseThrow(() -> new NotFoundException("Cash session not found: " + cashSessionId));
        return cashIncidentRepository.findAllByCashSession_IdOrderByCreatedAtAsc(cashSessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CashIncidentResponse addIncident(
            Long cashSessionId,
            CreateCashIncidentRequest req,
            String actor,
            String idempotencyKey
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            CashIncident existing = cashIncidentRepository
                    .findByCashSession_IdAndIdempotencyKey(cashSessionId, normalizedKey)
                    .orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        CashSession cashSession = cashSessionRepository.findById(cashSessionId)
                .orElseThrow(() -> new NotFoundException("Cash session not found: " + cashSessionId));
        if (cashSession.getStatus() != CashSessionStatus.OPEN) {
            throw new ConflictException("Cash session is CLOSED. Cannot register incidents.");
        }
        if (req.amountCents() <= 0) {
            throw new ConflictException("amountCents must be > 0");
        }
        if (req.direction() == null) {
            throw new ConflictException("direction is required");
        }

        CashIncident incident = new CashIncident(
                cashSession,
                req.direction(),
                req.amountCents(),
                req.note(),
                actor == null || actor.isBlank() ? "system" : actor,
                normalizedKey
        );
        try {
            cashIncidentRepository.save(incident);
        } catch (DataIntegrityViolationException duplicate) {
            if (normalizedKey == null) {
                throw duplicate;
            }
            CashIncident existing = cashIncidentRepository
                    .findByCashSession_IdAndIdempotencyKey(cashSessionId, normalizedKey)
                    .orElseThrow(() -> duplicate);
            return toResponse(existing);
        }
        return toResponse(incident);
    }

    @Transactional(readOnly = true)
    public int sumNetIncidentsCents(Long cashSessionId) {
        int in = cashIncidentRepository.sumByCashSessionAndDirection(cashSessionId, CashIncidentDirection.IN);
        int out = cashIncidentRepository.sumByCashSessionAndDirection(cashSessionId, CashIncidentDirection.OUT);
        return in - out;
    }

    @Transactional(readOnly = true)
    public int sumIncidentsInCents(Long cashSessionId) {
        return cashIncidentRepository.sumByCashSessionAndDirection(cashSessionId, CashIncidentDirection.IN);
    }

    @Transactional(readOnly = true)
    public int sumIncidentsOutCents(Long cashSessionId) {
        return cashIncidentRepository.sumByCashSessionAndDirection(cashSessionId, CashIncidentDirection.OUT);
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > 80) {
            throw new ConflictException("Idempotency-Key too long (max 80)");
        }
        return normalized;
    }

    private CashIncidentResponse toResponse(CashIncident incident) {
        return new CashIncidentResponse(
                incident.getId(),
                incident.getCashSession().getId(),
                incident.getDirection(),
                incident.getAmountCents(),
                incident.getNote(),
                incident.getCreatedBy(),
                incident.getCreatedAt()
        );
    }
}
