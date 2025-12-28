
package com.tpv.pos_service.controller;

import com.tpv.pos_service.CategoryService;
import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.dto.CreateCategoryRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/categories")
public class CategoryController {
    private final CategoryService service;
    
    public CategoryController(CategoryService service){
        this.service = service;
    }
    
    @GetMapping
    public List<Category> list(){
        return service.findAll();
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Category create(@Valid @RequestBody CreateCategoryRequest req){
        return service.create(req.name());
    }
}
