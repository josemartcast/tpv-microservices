package com.tpv.desktop.tpv.services.real;

import com.tpv.desktop.api.pos.CatalogApi;
import com.tpv.desktop.api.pos.CategoryResponse;
import com.tpv.desktop.api.pos.ProductResponse;
import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.services.CatalogService;

import java.util.List;

public class RealCatalogService implements CatalogService {

    @Override
    public List<Category> categories() {
        try {
            CategoryResponse[] response = CatalogApi.categories();
            return java.util.Arrays.stream(response)
                    .map(c -> new Category(c.id(), c.name()))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar catálogo (categorías): " + e.getMessage(), e);
        }
    }

    @Override
    public List<Product> productsByCategory(long categoryId) {
        try {
            ProductResponse[] response = CatalogApi.products(categoryId);
            return java.util.Arrays.stream(response)
                    .map(this::toDomain)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar catálogo (productos): " + e.getMessage(), e);
        }
    }

    @Override
    public Product productById(long productId) {
        try {
            ProductResponse[] all = CatalogApi.products(null);
            return java.util.Arrays.stream(all)
                    .filter(p -> p.id() == productId)
                    .findFirst()
                    .map(this::toDomain)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + productId));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo resolver producto: " + e.getMessage(), e);
        }
    }

    private Product toDomain(ProductResponse p) {
        Destination destination = inferDestination(p.categoryName(), p.name());
        String colorClass = switch (destination) {
            case BAR -> "prod-teal";
            case COCINA -> "prod-dark";
            case POSTRES -> "prod-orange";
        };
        return new Product(p.id(), p.categoryId(), p.name(), p.priceCents(), destination, colorClass);
    }

    private static Destination inferDestination(String categoryName, String productName) {
        String c = categoryName == null ? "" : categoryName.toLowerCase();
        String p = productName == null ? "" : productName.toLowerCase();
        if (c.contains("bebida") || c.contains("bar")) return Destination.BAR;
        if (c.contains("postre") || p.contains("tarta") || p.contains("helado")) return Destination.POSTRES;
        return Destination.COCINA;
    }
}

