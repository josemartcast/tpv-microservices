package com.tpv.pos_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpv.pos_service.domain.AuditEvent;
import com.tpv.pos_service.dto.AuditEventResponse;
import com.tpv.pos_service.repository.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final int JSON_MAX_LEN = 4000;
    private static final int MSG_MAX_LEN = 500;

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            String action,
            String resourceType,
            Long resourceId,
            String actor,
            String terminalId,
            Object request,
            Object response
    ) {
        save(action, resourceType, resourceId, "SUCCESS", actor, terminalId, null, request, response);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String action,
            String resourceType,
            Long resourceId,
            String actor,
            String terminalId,
            Object request,
            Exception error
    ) {
        String message = error == null ? "Unknown error" : error.getMessage();
        save(action, resourceType, resourceId, "FAILED", actor, terminalId, message, request, null);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> list(
            Instant from,
            Instant to,
            String action,
            String status,
            String actor,
            String resourceType,
            Long resourceId,
            Integer limit
    ) {
        int max = limit == null ? 200 : Math.max(1, Math.min(limit, 1000));
        var page = repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.equal(root.get("actor"), actor.trim()));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType.trim().toUpperCase()));
            }
            if (resourceId != null) {
                predicates.add(cb.equal(root.get("resourceId"), resourceId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "createdAt")));

        return page.stream().map(this::toResponse).toList();
    }

    private void save(
            String action,
            String resourceType,
            Long resourceId,
            String status,
            String actor,
            String terminalId,
            String message,
            Object request,
            Object response
    ) {
        AuditEvent event = new AuditEvent(
                safe(action, 80, "UNKNOWN_ACTION"),
                safe(resourceType, 40, "UNKNOWN_RESOURCE"),
                resourceId,
                safe(status, 20, "FAILED"),
                safe(actor, 120, "unknown"),
                safeNullable(terminalId, 80),
                safeNullable(message, MSG_MAX_LEN),
                serialize(request),
                serialize(response)
        );
        repository.save(event);
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(
                e.getId(),
                e.getAction(),
                e.getResourceType(),
                e.getResourceId(),
                e.getStatus(),
                e.getActor(),
                e.getTerminalId(),
                e.getMessage(),
                e.getRequestJson(),
                e.getResponseJson(),
                e.getCreatedAt()
        );
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return safeNullable(objectMapper.writeValueAsString(value), JSON_MAX_LEN);
        } catch (Exception e) {
            return safeNullable("{\"serializationError\":\"" + e.getClass().getSimpleName() + "\"}", JSON_MAX_LEN);
        }
    }

    private String safe(String value, int max, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }

    private String safeNullable(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }
}
