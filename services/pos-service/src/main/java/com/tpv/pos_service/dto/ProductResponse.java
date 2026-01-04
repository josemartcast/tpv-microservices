package com.tpv.pos_service.dto;

import java.time.Instant;

public record ProductResponse(long id, String name, int priceCents, boolean active, long categoryId, String categoryName, Instant createdAt, Instant updatedAt){

}