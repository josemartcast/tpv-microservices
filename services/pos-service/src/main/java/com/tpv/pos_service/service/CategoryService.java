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

        if (repo.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category name already exists: " + name);
        }

        Category c = new Category(name);
        c = repo.save(c);
        return toResponse(c);
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest req) {
        Category c = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("Category not found: " + id));

        String newName = normalize(req.name());

        // Si cambia el nombre, revisa conflicto
        if (!c.getName().equalsIgnoreCase(newName) && repo.existsByNameIgnoreCase(newName)) {
            throw new ConflictException("Category name already exists: " + newName);
        }

        c.rename(newName);
        return toResponse(c);
    }

    @Transactional
    public void delete(Long id) {
        Category c = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("Category not found: " + id));

        // Soft delete
        c.deactivate();
    }

    private String normalize(String name) {
        return name == null ? null : name.trim();
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
