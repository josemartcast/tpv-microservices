package com.tpv.pos_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpv.pos_service.domain.IdempotencyRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.IdempotencyRequestRepository;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final IdempotencyRequestRepository repository;
    private final ObjectMapper mapper;

    public IdempotencyService(IdempotencyRequestRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public <T> T execute(String scope, Long resourceId, String idempotencyKey, Class<T> responseType, Supplier<T> operation) {
        String key = normalize(idempotencyKey);
        if (key == null) {
            return operation.get();
        }

        IdempotencyRequest existing = repository
                .findByScopeAndResourceIdAndIdempotencyKey(scope, resourceId, key)
                .orElse(null);
        if (existing != null) {
            return deserialize(existing.getResponseJson(), responseType);
        }

        T response = operation.get();
        String json = serialize(response);

        try {
            repository.save(new IdempotencyRequest(scope, resourceId, key, json));
        } catch (DataIntegrityViolationException duplicate) {
            IdempotencyRequest concurrent = repository
                    .findByScopeAndResourceIdAndIdempotencyKey(scope, resourceId, key)
                    .orElseThrow(() -> duplicate);
            return deserialize(concurrent.getResponseJson(), responseType);
        }

        return response;
    }

    private String normalize(String key) {
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

    private String serialize(Object response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize idempotent response", e);
        }
    }

    private <T> T deserialize(String responseJson, Class<T> responseType) {
        try {
            return mapper.readValue(responseJson, responseType);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize idempotent response", e);
        }
    }
}
