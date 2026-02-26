package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.SalonArea;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalonAreaRepository extends JpaRepository<SalonArea, Long> {
    List<SalonArea> findAllByActiveTrueOrderByFirstTableNumberAsc();
    Optional<SalonArea> findByIdAndActiveTrue(Long id);
    boolean existsByNameIgnoreCaseAndActiveTrue(String name);

    @Query("""
            select s from SalonArea s
            where s.active = true
              and :startTable <= (s.firstTableNumber + s.tableCount - 1)
              and :endTable >= s.firstTableNumber
            order by s.firstTableNumber asc
            """)
    List<SalonArea> findOverlappingActiveRange(
            @Param("startTable") int startTable,
            @Param("endTable") int endTable
    );
}
