package com.tpv.pos_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpv.pos_service.dto.CategoryResponse;
import com.tpv.pos_service.dto.CreateCategoryRequest;
import com.tpv.pos_service.dto.UpdateCategoryRequest;
import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository repo;

    public CategoryService(CategoryRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return repo.findAllByActiveTrueOrderByNameAsc()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category c = repo.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new NotFoundException("Category not found: " + id));
        return toResponse(c);
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest req) {
        String name = normalize(req.name());

        if (name == null) {
            throw new IllegalArgumentException("Category name cannot be blank");
        }

        if (repo.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category name already exists: " + name);
        }

        Category c = repo.save(new Category(name));
        return toResponse(c);
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest req) {
        Category c = repo.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new NotFoundException("Category not found: " + id));

        String newName = normalize(req.name());
        if (newName == null) {
            throw new IllegalArgumentException("Category name cannot be blank");
        }

        if (!c.getName().equalsIgnoreCase(newName) && repo.existsByNameIgnoreCase(newName)) {
            throw new ConflictException("Category name already exists: " + newName);
        }

        c.rename(newName);
        return toResponse(c);
    }

    @Transactional
    public void delete(Long id) {
        Category c = repo.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new NotFoundException("Category not found: " + id));

        c.deactivate();
    }

    // ADMIN
    @Transactional
    public CategoryResponse activate(Long id) {
        Category c = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("Category not found: " + id));

        c.activate();
        return toResponse(c);
    }

    // ADMIN
    @Transactional
    public CategoryResponse deactivate(Long id) {
        Category c = repo.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new NotFoundException("Category not found: " + id));

        c.deactivate();
        return toResponse(c);
    }

    private String normalize(String name) {
        if (name == null) return null;
        String n = name.trim();
        return n.isBlank() ? null : n;
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
            c.getId(),
            c.getName(),
            c.isActive(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}
