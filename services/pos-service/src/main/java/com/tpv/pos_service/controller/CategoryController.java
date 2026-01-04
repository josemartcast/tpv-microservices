package com.tpv.pos_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.tpv.pos_service.dto.CategoryResponse;
import com.tpv.pos_service.dto.CreateCategoryRequest;
import com.tpv.pos_service.dto.UpdateCategoryRequest;
import com.tpv.pos_service.service.CategoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/pos/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    // USER o ADMIN
    @GetMapping
    public List<CategoryResponse> list() {
        return service.listActive();
    }

    // USER o ADMIN
    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Long id) {
        return service.getById(id);
    }

    // ADMIN
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest req) {
        return service.create(req);
    }

    // ADMIN
    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest req) {
        return service.update(id, req);
    }

    // ADMIN (soft delete)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ADMIN (reactivar)
    @PatchMapping("/{id}/activate")
    public CategoryResponse activate(@PathVariable Long id) {
        return service.activate(id);
    }

    // ADMIN (desactivar)
    @PatchMapping("/{id}/deactivate")
    public CategoryResponse deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
