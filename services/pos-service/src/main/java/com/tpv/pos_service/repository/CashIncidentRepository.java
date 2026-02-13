package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.CashIncident;
import com.tpv.pos_service.domain.CashIncidentDirection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashIncidentRepository extends JpaRepository<CashIncident, Long> {

    List<CashIncident> findAllByCashSession_IdOrderByCreatedAtAsc(Long cashSessionId);

    Optional<CashIncident> findByCashSession_IdAndIdempotencyKey(Long cashSessionId, String idempotencyKey);

    @Query("""
        select coalesce(sum(ci.amountCents), 0)
        from CashIncident ci
        where ci.cashSession.id = :cashSessionId
          and ci.direction = :direction
        """)
    int sumByCashSessionAndDirection(
            @Param("cashSessionId") Long cashSessionId,
            @Param("direction") CashIncidentDirection direction);
}
