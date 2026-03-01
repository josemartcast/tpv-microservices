package com.tpv.desktop.api.pos;

public record UpdateCustomerRequest(
        String displayName,
        String legalName,
        String taxId,
        String fiscalAddress,
        String postalCode,
        String city,
        String province,
        String country,
        String phone,
        String email
) {
}
