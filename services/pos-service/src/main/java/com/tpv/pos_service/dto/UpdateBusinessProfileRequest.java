package com.tpv.pos_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBusinessProfileRequest(
        @NotBlank(message = "businessName is required")
        @Size(min = 2, max = 120, message = "businessName must be between 2 and 120 chars")
        String businessName,

        @Size(max = 160, message = "legalName max length is 160")
        String legalName,

        @Size(max = 32, message = "taxId max length is 32")
        String taxId,

        @Size(max = 200, message = "address max length is 200")
        String address,

        @Size(max = 16, message = "postalCode max length is 16")
        String postalCode,

        @Size(max = 80, message = "city max length is 80")
        String city,

        @Size(max = 80, message = "province max length is 80")
        String province,

        @Pattern(regexp = "^$|[A-Za-z]{2}$", message = "country must be ISO-2 code")
        String country,

        @Pattern(regexp = "^$|[0-9+()\\-\\s]{6,24}$", message = "phone format is invalid")
        String phone,

        @Pattern(regexp = "^$|[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "email format is invalid")
        String email
) {}
