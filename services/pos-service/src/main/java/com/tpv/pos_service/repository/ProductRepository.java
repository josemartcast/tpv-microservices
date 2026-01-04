package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository <Product, Long>{
    boolean existsByNameIgnoreCase(String name);

    Optional<Product> findByIdAndActiveTrue(Long id);

    List<Product> findAllByActiveTrueOrderByNameAsc();

    List<Product> findAllByActiveTrueAndCategory_IdOrderByNameAsc(Long categoryId);
}