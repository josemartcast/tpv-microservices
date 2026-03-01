package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @NotBlank(message = "displayName is required")
        @Size(min = 2, max = 120, message = "displayName must be between 2 and 120 chars")
        String displayName,

        @Size(max = 160, message = "legalName max length is 160")
        String legalName,

        @Size(max = 32, message = "taxId max length is 32")
        String taxId,

        @Size(max = 200, message = "fiscalAddress max length is 200")
        String fiscalAddress,

        @Size(max = 16, message = "postalCode max length is 16")
        String postalCode,

        @Size(max = 120, message = "city max length is 120")
        String city,

        @Size(max = 120, message = "province max length is 120")
        String province,

        @Size(max = 64, message = "country max length is 64")
        String country,

        @Size(max = 32, message = "phone max length is 32")
        String phone,

        @Size(max = 160, message = "email max length is 160")
        String email
) {
}
