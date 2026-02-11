package com.tpv.desktop.tpv.services.fake;

import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.services.CatalogService;

import java.util.Comparator;
import java.util.List;

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
}

