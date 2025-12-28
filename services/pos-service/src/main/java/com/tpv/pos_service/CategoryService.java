package com.tpv.pos_service;

import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.repository.CategoryRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    private final CategoryRepository repo;

    public CategoryService(CategoryRepository repo) {
        this.repo = repo;
    }

    public List<Category> findAll() {
        return repo.findAll();
    }

    @Transactional
    public Category create(String name) {
        repo.findByNameIgnoreCase(name).ifPresent(c -> {
            throw new IllegalArgumentException("El nombre de la categoría ya existe.");
        });
        return repo.save(new Category(name));
    }

}
