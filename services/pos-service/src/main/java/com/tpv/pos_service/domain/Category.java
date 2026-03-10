package com.tpv.pos_service.domain;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.*;

@Entity
@Table(
    name = "categories",
    uniqueConstraints = @UniqueConstraint(name = "uk_category_name", columnNames = "name")
)
public class Category {
    public static final String DEST_BAR = "BAR";
    public static final String DEST_COCINA = "COCINA";
    public static final String DEST_POSTRES = "POSTRES";
    public static final String DEFAULT_DESTINATION = DEST_COCINA;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 24)
    private String printDestination = DEFAULT_DESTINATION;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Category() {}

    public Category(String name) {
        this(name, DEFAULT_DESTINATION);
    }

    public Category(String name, String printDestination) {
        this.name = name;
        this.printDestination = normalizeDestination(printDestination);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public String getPrintDestination() { return normalizeDestination(printDestination); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void rename(String name) { this.name = name; }
    public void changePrintDestination(String printDestination) { this.printDestination = normalizeDestination(printDestination); }
    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    private static String normalizeDestination(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_DESTINATION;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (DEST_BAR.equals(normalized) || DEST_COCINA.equals(normalized) || DEST_POSTRES.equals(normalized)) {
            return normalized;
        }
        return DEFAULT_DESTINATION;
    }
}
