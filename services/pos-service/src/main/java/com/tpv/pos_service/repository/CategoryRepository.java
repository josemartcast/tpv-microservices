
package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository <Category,Long>{
Optional<Category>findByNameIgnoreCase(String name);

List<Category> findAllByActiveTrueOrderByNameAsc();

Optional<Category> findByIdAndActiveTrue(Long id);

boolean existsByNameIgnoreCase(String name);
    
}
