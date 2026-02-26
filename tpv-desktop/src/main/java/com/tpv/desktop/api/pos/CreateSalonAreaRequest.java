package com.tpv.desktop.api.pos;

public record CreateSalonAreaRequest(
        String name,
        Integer tableCount,
        Integer firstTableNumber
) {}
