package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_name", columnNames = "name"))
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Column(nullable = false)
    private int priceCents;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant updatedAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_category"))
    private Category category;

    @Column(nullable = false)
    private int vatRateBps = 2100; // 21% por defecto

    protected Product() {

    }

    public Product(String name, int priceCents, Category category, int vatRateBps) {
        this.name = name;
        this.category = category;
        this.priceCents = priceCents;
        this.vatRateBps = vatRateBps;
    }

    @PrePersist
    void onCreated() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;

    }

    @PreUpdate
    void onUpdated() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public boolean isActive() {
        return active;
    }

    public Category getCategory() {
        return category;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changePrice(int priceCents) {
        this.priceCents = priceCents;
    }

    public void changeCategory(Category category) {
        this.category = category;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public int getVatRateBps() {
        return vatRateBps;
    }

    public void changeVatRateBps(int vatRateBps) {
        this.vatRateBps = vatRateBps;
    }

}
