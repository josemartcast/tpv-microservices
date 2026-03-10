package com.tpv.desktop.tpv.services;

import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Product;

import java.util.List;

public interface CatalogService {
    List<Category> categories();
    List<Product> productsByCategory(long categoryId);
    Product productById(long productId);
    default Category createCategory(String name) {
        return createCategory(name, "COCINA");
    }
    Category createCategory(String name, String printDestination);
    default Category updateCategory(long id, String name) {
        return updateCategory(id, name, "COCINA");
    }
    Category updateCategory(long id, String name, String printDestination);
    void deleteCategory(long id);
    Product createProduct(long categoryId, String name, int priceCents, int vatRateBps);
    Product updateProduct(long productId, long categoryId, String name, int priceCents, int vatRateBps);
    void deleteProduct(long productId);
}

