package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.CashSessionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {

    boolean existsByStatus(CashSessionStatus status);

    Optional<CashSession> findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus status);
}
