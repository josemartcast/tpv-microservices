package com.tpv.pos_service.dto;

import java.time.Instant;

public record CustomerResponse(
        long id,
        String displayName,
        String legalName,
        String taxId,
        String fiscalAddress,
        String postalCode,
        String city,
        String province,
        String country,
        String phone,
        String email,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
