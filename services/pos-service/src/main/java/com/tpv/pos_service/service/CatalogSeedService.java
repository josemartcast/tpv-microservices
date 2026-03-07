package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.dto.SeedCatalogResponse;
import com.tpv.pos_service.repository.CategoryRepository;
import com.tpv.pos_service.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
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
        repairKnownEncodingIssues();

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

    private void repairKnownEncodingIssues() {
        deactivateUnknownQuestionMarkProducts();
        repairMojibakeProductNames();
        repairProductName("Cerveza Ca\u00F1a", List.of(
                "Cerveza Ca\u00C3\u00B1a",
                "Cerveza Ca??a"
        ));
    }

    private void deactivateUnknownQuestionMarkProducts() {
        for (Product product : productRepository.findAllByNameContaining("??")) {
            if (product.isActive()) {
                product.deactivate();
            }
        }
    }

    private void repairMojibakeProductNames() {
        for (Product product : productRepository.findAll()) {
            String currentName = product.getName();
            String repairedName = repairText(currentName);
            if (repairedName.equals(currentName)) {
                continue;
            }
            Product existing = productRepository.findByNameIgnoreCase(repairedName).orElse(null);
            if (existing != null && !existing.getId().equals(product.getId())) {
                if (product.isActive()) {
                    product.deactivate();
                }
                continue;
            }
            product.rename(repairedName);
        }
    }

    private void repairProductName(String canonicalName, List<String> variants) {
        Product canonical = productRepository.findByNameIgnoreCase(canonicalName).orElse(null);
        if (canonical != null && !canonical.isActive()) {
            canonical.activate();
        }

        for (String variantName : variants) {
            Product variant = productRepository.findByNameIgnoreCase(variantName).orElse(null);
            if (variant == null) {
                continue;
            }
            if (canonical == null) {
                variant.rename(canonicalName);
                if (!variant.isActive()) {
                    variant.activate();
                }
                canonical = variant;
                continue;
            }
            if (variant.isActive()) {
                variant.deactivate();
            }
        }
    }

    private static String repairText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String repaired = value;
        if (repaired.contains("\u00C3") || repaired.contains("\u00C2")) {
            repaired = new String(repaired.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        }
        if (repaired.contains("??")) {
            repaired = repaired.replace("??", "\u00F1");
        }
        return repaired;
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
            new SeedProduct("Cerveza Ca\u00F1a", 220, "Cervezas", 1000),
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
