package com.tpv.pos_service.dto;

import java.time.Instant;

public record AuditEventResponse(
        long id,
        String action,
        String resourceType,
        Long resourceId,
        String status,
        String actor,
        String terminalId,
        String message,
        String requestJson,
        String responseJson,
        Instant createdAt
) {
}
