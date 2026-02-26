package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.FiscalExercise;
import com.tpv.pos_service.domain.FiscalExerciseStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalExerciseRepository extends JpaRepository<FiscalExercise, Long> {
    Optional<FiscalExercise> findFirstByStatusOrderByFiscalYearDesc(FiscalExerciseStatus status);
    Optional<FiscalExercise> findByFiscalYear(int fiscalYear);
    boolean existsByFiscalYear(int fiscalYear);
    boolean existsByStatus(FiscalExerciseStatus status);
    List<FiscalExercise> findAllByOrderByFiscalYearDesc();
}

