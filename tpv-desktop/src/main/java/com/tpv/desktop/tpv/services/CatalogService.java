package com.tpv.desktop.tpv.services;

import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Product;

import java.util.List;

public interface CatalogService {
    List<Category> categories();
    List<Product> productsByCategory(long categoryId);
    Product productById(long productId);
    Category createCategory(String name);
    Category updateCategory(long id, String name);
    void deleteCategory(long id);
    Product createProduct(long categoryId, String name, int priceCents, int vatRateBps);
    Product updateProduct(long productId, long categoryId, String name, int priceCents, int vatRateBps);
    void deleteProduct(long productId);
}

