package com.tpv.pos_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "salon_areas",
        uniqueConstraints = @UniqueConstraint(name = "uk_salon_area_name", columnNames = "name")
)
public class SalonArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String name;

    @Column(name = "first_table_number", nullable = false)
    private int firstTableNumber;

    @Column(name = "table_count", nullable = false)
    private int tableCount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SalonArea() {}

    public SalonArea(String name, int firstTableNumber, int tableCount) {
        this.name = name;
        this.firstTableNumber = firstTableNumber;
        this.tableCount = tableCount;
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
    public int getFirstTableNumber() { return firstTableNumber; }
    public int getTableCount() { return tableCount; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public int getLastTableNumber() {
        return firstTableNumber + tableCount - 1;
    }

    public boolean containsTable(int tableNumber) {
        return tableNumber >= firstTableNumber && tableNumber <= getLastTableNumber();
    }

    public void rename(String value) {
        this.name = value;
    }

    public void deactivate() {
        this.active = false;
    }
}
