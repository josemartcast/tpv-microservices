package com.tpv.desktop.tpv.services;

import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Product;

import java.util.List;

public interface CatalogService {
    List<Category> categories();
    List<Product> productsByCategory(long categoryId);
    Product productById(long productId);
}

