package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.TableAlias;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableAliasRepository extends JpaRepository<TableAlias, Long> {
    Optional<TableAlias> findByTableNumber(int tableNumber);
    List<TableAlias> findByTableNumberIn(List<Integer> tableNumbers);
    void deleteByTableNumber(int tableNumber);
}

