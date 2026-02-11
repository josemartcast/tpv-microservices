package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.IdempotencyRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, Long> {
    Optional<IdempotencyRequest> findByScopeAndResourceIdAndIdempotencyKey(String scope, Long resourceId, String idempotencyKey);
}
