package com.tpv.pos_service.dto;

import java.time.Instant;

public record BusinessProfileResponse(
        long id,
        String businessName,
        String legalName,
        String taxId,
        String address,
        String postalCode,
        String city,
        String province,
        String country,
        String phone,
        String email,
        Instant updatedAt
) {}
