package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.dto.CreateProductRequest;
import com.tpv.pos_service.dto.ProductResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.CategoryRepository;
import com.tpv.pos_service.repository.ProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepo;
    @Mock
    private CategoryRepository categoryRepo;

    @InjectMocks
    private ProductService service;

    @Test
    void create_reactivatesInactiveProductWithSameName() {
        Category category = new Category("Bebidas", "BAR");
        ReflectionTestUtils.setField(category, "id", 5L);

        Product inactive = new Product("Refresco", 250, category, 1000);
        ReflectionTestUtils.setField(inactive, "id", 9L);
        inactive.deactivate();

        when(productRepo.findByNameIgnoreCase("Refresco")).thenReturn(Optional.of(inactive));
        when(categoryRepo.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(category));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse out = service.create(new CreateProductRequest("Refresco", 300, 5L, 1000));

        assertEquals(9L, out.id());
        assertEquals("Refresco", out.name());
        assertEquals(300, out.priceCents());
        assertEquals(5L, out.categoryId());
        assertTrue(out.active());
        assertTrue(inactive.isActive());
        verify(productRepo).save(inactive);
    }

    @Test
    void create_rejectsWhenActiveProductWithSameNameExists() {
        Category category = new Category("Bebidas", "BAR");
        ReflectionTestUtils.setField(category, "id", 5L);

        Product active = new Product("Refresco", 250, category, 1000);
        ReflectionTestUtils.setField(active, "id", 3L);

        when(productRepo.findByNameIgnoreCase("Refresco")).thenReturn(Optional.of(active));
        when(categoryRepo.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(category));

        assertThrows(
                ConflictException.class,
                () -> service.create(new CreateProductRequest("Refresco", 300, 5L, 1000))
        );

        verify(productRepo, never()).save(any(Product.class));
    }
}
