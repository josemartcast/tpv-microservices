package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {
}
