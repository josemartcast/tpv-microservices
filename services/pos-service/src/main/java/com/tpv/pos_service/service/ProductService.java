package com.tpv.pos_service.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.dto.CreateProductRequest;
import com.tpv.pos_service.dto.ProductResponse;
import com.tpv.pos_service.dto.UpdateProductRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CategoryRepository;
import com.tpv.pos_service.repository.ProductRepository;

@Service
@SuppressWarnings("null")
public class ProductService {
    private static final String SPECIAL_TAPAS_CATEGORY = "TAPAS";

    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;

    public ProductService(ProductRepository productRepo, CategoryRepository categoryRepo) {
        this.productRepo = productRepo;
        this.categoryRepo = categoryRepo;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listActive(Long categoryId) {
        return (categoryId == null
                ? productRepo.findAllByActiveTrueOrderByNameAsc()
                : productRepo.findAllByActiveTrueAndCategory_IdOrderByNameAsc(categoryId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product p = productRepo.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        return toResponse(p);
    }

    @Transactional
    public ProductResponse create(CreateProductRequest req) {
        String name = normalize(req.name());
        Product existingByName = productRepo.findByNameIgnoreCase(name).orElse(null);

        Category category = categoryRepo.findByIdAndActiveTrue(req.categoryId())
                .orElseThrow(() -> new NotFoundException("Category not found/active: " + req.categoryId()));
        int safePriceCents = normalizePriceForCategory(req.priceCents(), category);
        int safeVatRateBps = normalizeVatForCategory(req.vatRateBps(), category);

        if (existingByName != null) {
            if (existingByName.isActive()) {
                throw new ConflictException("Product name already exists: " + name);
            }
            existingByName.rename(name);
            existingByName.changePrice(safePriceCents);
            existingByName.changeCategory(category);
            existingByName.changeVatRateBps(safeVatRateBps);
            existingByName.activate();
            Product reactivated = productRepo.save(existingByName);
            return toResponse(reactivated);
        }

        Product p = new Product(name, safePriceCents, category, safeVatRateBps);
        p = productRepo.save(p);
        return toResponse(p);
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest req) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));

        String newName = normalize(req.name());

        if (!p.getName().equalsIgnoreCase(newName) && productRepo.existsByNameIgnoreCase(newName)) {
            throw new ConflictException("Product name already exists: " + newName);
        }

        Category category = categoryRepo.findByIdAndActiveTrue(req.categoryId())
                .orElseThrow(() -> new NotFoundException("Category not found/active: " + req.categoryId()));
        int safePriceCents = normalizePriceForCategory(req.priceCents(), category);
        int safeVatRateBps = normalizeVatForCategory(req.vatRateBps(), category);

        p.rename(newName);
        p.changePrice(safePriceCents);
        p.changeCategory(category);
        p.changeVatRateBps(safeVatRateBps);

        return toResponse(p);
    }

    @Transactional
    public void delete(Long id) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        p.deactivate();
    }

    @Transactional
    public void activate(Long id) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        p.activate();
    }

    @Transactional
    public void deactivate(Long id) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        p.deactivate();
    }

    private String normalize(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static int normalizePriceForCategory(int requestedPriceCents, Category category) {
        if (isTapasCategory(category)) {
            return 0;
        }
        return Math.max(0, requestedPriceCents);
    }

    private static int normalizeVatForCategory(int requestedVatRateBps, Category category) {
        if (isTapasCategory(category)) {
            return 0;
        }
        return Math.max(0, requestedVatRateBps);
    }

    private static boolean isTapasCategory(Category category) {
        if (category == null || category.getName() == null) {
            return false;
        }
        return SPECIAL_TAPAS_CATEGORY.equalsIgnoreCase(category.getName().trim());
    }

    private ProductResponse toResponse(Product p) {
        // Ojo: category es LAZY. Como estamos en @Transactional, OK.
        Category c = p.getCategory();

        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getPriceCents(),
                p.isActive(),
                c.getId(),
                c.getName(),
                c.getPrintDestination(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getVatRateBps()
        );
    }
}
