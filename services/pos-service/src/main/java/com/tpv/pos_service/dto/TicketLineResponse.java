package com.tpv.pos_service.dto;

public record TicketLineResponse(
    long id,
    long productId,
    String productName,
    int unitPriceCents,
    int qty,
    int lineTotalCents
) {}
