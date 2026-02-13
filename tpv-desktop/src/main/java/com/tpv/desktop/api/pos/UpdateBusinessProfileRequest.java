package com.tpv.desktop.api.pos;

public record UpdateBusinessProfileRequest(
        String businessName,
        String legalName,
        String taxId,
        String address,
        String postalCode,
        String city,
        String province,
        String country,
        String phone,
        String email
) {}
