
package com.tpv.pos_service.dto;

import java.time.Instant;

public record CategoryResponse (
        long id,
        String name,
        String printDestination,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    
}
