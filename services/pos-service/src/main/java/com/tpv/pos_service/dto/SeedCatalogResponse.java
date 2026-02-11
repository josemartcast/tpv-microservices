package com.tpv.pos_service.dto;

public record SeedCatalogResponse(
        int categoriesCreated,
        int categoriesReused,
        int categoriesReactivated,
        int productsCreated,
        int productsReused,
        int productsReactivated
) {
}
