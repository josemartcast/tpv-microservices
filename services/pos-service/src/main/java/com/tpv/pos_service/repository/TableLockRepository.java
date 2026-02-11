package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.TableLock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableLockRepository extends JpaRepository<TableLock, Long> {
    Optional<TableLock> findByTableNumber(int tableNumber);
    void deleteByTableNumber(int tableNumber);
    void deleteByExpiresAtBefore(Instant instant);
}
