package com.tpv.desktop.tpv.services.fake;

import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.services.CatalogService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FakeCatalogService implements CatalogService {
    private final FakeDataStore store;

    public FakeCatalogService(FakeDataStore store) {
        this.store = store;
    }

    @Override
    public List<Category> categories() {
        return List.copyOf(store.categories);
    }

    @Override
    public List<Product> productsByCategory(long categoryId) {
        return store.products.values().stream()
                .filter(p -> p.categoryId() == categoryId)
                .sorted(Comparator.comparing(Product::name))
                .toList();
    }

    @Override
    public Product productById(long productId) {
        return store.products.get(productId);
    }

    @Override
    public Category createCategory(String name, String printDestination) {
        return store.createCategory(name, printDestination);
    }

    @Override
    public Category updateCategory(long id, String name, String printDestination) {
        return store.updateCategory(id, name, printDestination);
    }

    @Override
    public void deleteCategory(long id) {
        store.deleteCategory(id);
    }

    @Override
    public Product createProduct(long categoryId, String name, int priceCents, int vatRateBps) {
        Destination destination = inferDestination(categoryId, name);
        return store.createProduct(categoryId, name, priceCents, vatRateBps, destination, colorClass(destination));
    }

    @Override
    public Product updateProduct(long productId, long categoryId, String name, int priceCents, int vatRateBps) {
        Destination destination = inferDestination(categoryId, name);
        return store.updateProduct(productId, categoryId, name, priceCents, vatRateBps, destination, colorClass(destination));
    }

    @Override
    public void deleteProduct(long productId) {
        store.deleteProduct(productId);
    }

    private Destination inferDestination(long categoryId, String productName) {
        Category category = store.categories.stream()
                .filter(c -> c.id() == categoryId)
                .findFirst()
                .orElse(null);
        if (category != null) {
            String destination = category.printDestination() == null ? "" : category.printDestination().trim().toUpperCase(Locale.ROOT);
            if ("BAR".equals(destination)) return Destination.BAR;
            if ("POSTRES".equals(destination)) return Destination.POSTRES;
            if ("COCINA".equals(destination)) return Destination.COCINA;
        }

        String categoryName = category == null ? "" : category.name();
        String c = categoryName.toLowerCase(Locale.ROOT);
        String p = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        if (c.contains("bebida") || c.contains("bar")) return Destination.BAR;
        if (c.contains("postre") || p.contains("tarta") || p.contains("helado")) return Destination.POSTRES;
        return Destination.COCINA;
    }

    private static String colorClass(Destination destination) {
        return switch (destination) {
            case BAR -> "prod-teal";
            case COCINA -> "prod-dark";
            case POSTRES -> "prod-orange";
        };
    }
}

