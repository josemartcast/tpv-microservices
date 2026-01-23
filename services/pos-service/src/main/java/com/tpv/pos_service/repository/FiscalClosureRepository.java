package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.FiscalClosure;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalClosureRepository extends JpaRepository<FiscalClosure, Long> {

    Optional<FiscalClosure> findByCashSession_Id(Long cashSessionId);

    boolean existsByCashSession_Id(Long cashSessionId);
}
