package com.tpv.desktop.api.pos;

import java.time.Instant;

public record CategoryResponse(long id, String name, boolean active, Instant createdAt, Instant updatedAt) {}
