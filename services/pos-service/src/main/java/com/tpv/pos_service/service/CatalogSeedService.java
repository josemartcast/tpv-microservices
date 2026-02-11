package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.dto.SeedCatalogResponse;
import com.tpv.pos_service.repository.CategoryRepository;
import com.tpv.pos_service.repository.ProductRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSeedService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CatalogSeedService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public SeedCatalogResponse seedDefaultCatalog() {
        SeedCounter counter = new SeedCounter();

        Map<String, Category> categoriesByName = new LinkedHashMap<>();
        for (String categoryName : DEFAULT_CATEGORIES) {
            Category category = categoryRepository.findByNameIgnoreCase(categoryName).orElse(null);
            if (category == null) {
                category = categoryRepository.save(new Category(categoryName));
                counter.categoriesCreated++;
            } else {
                counter.categoriesReused++;
                if (!category.isActive()) {
                    category.activate();
                    counter.categoriesReactivated++;
                }
            }
            categoriesByName.put(categoryName, category);
        }

        for (SeedProduct seedProduct : DEFAULT_PRODUCTS) {
            Product product = productRepository.findByNameIgnoreCase(seedProduct.name()).orElse(null);
            if (product == null) {
                Category category = categoriesByName.get(seedProduct.categoryName());
                Product created = new Product(seedProduct.name(), seedProduct.priceCents(), category, seedProduct.vatRateBps());
                productRepository.save(created);
                counter.productsCreated++;
            } else {
                counter.productsReused++;
                if (!product.isActive()) {
                    product.activate();
                    counter.productsReactivated++;
                }
            }
        }

        return new SeedCatalogResponse(
                counter.categoriesCreated,
                counter.categoriesReused,
                counter.categoriesReactivated,
                counter.productsCreated,
                counter.productsReused,
                counter.productsReactivated
        );
    }

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Bebidas",
            "Cervezas",
            "Entrantes",
            "Platos",
            "Postres",
            "Cafes"
    );

    private static final List<SeedProduct> DEFAULT_PRODUCTS = List.of(
            new SeedProduct("Agua 50cl", 150, "Bebidas", 1000),
            new SeedProduct("Refresco", 250, "Bebidas", 1000),
            new SeedProduct("Zumo Naranja", 280, "Bebidas", 1000),
            new SeedProduct("Cerveza Caña", 220, "Cervezas", 1000),
            new SeedProduct("Cerveza Doble", 320, "Cervezas", 1000),
            new SeedProduct("Cerveza 0,0", 250, "Cervezas", 1000),
            new SeedProduct("Bravas", 700, "Entrantes", 1000),
            new SeedProduct("Ensaladilla", 650, "Entrantes", 1000),
            new SeedProduct("Calamares", 950, "Entrantes", 1000),
            new SeedProduct("Hamburguesa", 1150, "Platos", 1000),
            new SeedProduct("Pizza Margarita", 1200, "Platos", 1000),
            new SeedProduct("Entrecot", 1800, "Platos", 1000),
            new SeedProduct("Tarta Queso", 550, "Postres", 1000),
            new SeedProduct("Helado", 450, "Postres", 1000),
            new SeedProduct("Cafe Solo", 150, "Cafes", 1000),
            new SeedProduct("Cafe con Leche", 180, "Cafes", 1000)
    );

    private record SeedProduct(String name, int priceCents, String categoryName, int vatRateBps) {
    }

    private static final class SeedCounter {
        private int categoriesCreated;
        private int categoriesReused;
        private int categoriesReactivated;
        private int productsCreated;
        private int productsReused;
        private int productsReactivated;
    }
}
