package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByActiveTrueOrderByDisplayNameAsc();
    Optional<Customer> findByIdAndActiveTrue(Long id);
    boolean existsByDisplayNameIgnoreCase(String displayName);
}
