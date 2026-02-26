package com.tpv.pos_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "fiscal_exercises",
        indexes = {
            @Index(name = "idx_fiscal_exercise_year", columnList = "fiscalYear"),
            @Index(name = "idx_fiscal_exercise_status", columnList = "status")
        }
)
public class FiscalExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int fiscalYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FiscalExerciseStatus status = FiscalExerciseStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant openedAt;

    @Column
    private Instant closedAt;

    @Column(nullable = false, length = 80)
    private String openedBy;

    @Column(length = 80)
    private String closedBy;

    @Column(length = 255)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected FiscalExercise() {
    }

    public FiscalExercise(int fiscalYear, String openedBy, String note) {
        this.fiscalYear = fiscalYear;
        this.openedBy = openedBy;
        this.note = note;
        this.status = FiscalExerciseStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.openedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void close(String closedBy, String note) {
        this.status = FiscalExerciseStatus.CLOSED;
        this.closedBy = closedBy;
        this.closedAt = Instant.now();
        if (note != null && !note.isBlank()) {
            this.note = note.trim();
        }
    }

    public void reopen(String openedBy, String note) {
        this.status = FiscalExerciseStatus.OPEN;
        this.closedBy = null;
        this.closedAt = null;
        this.openedBy = openedBy;
        if (note != null && !note.isBlank()) {
            this.note = note.trim();
        }
    }

    public Long getId() {
        return id;
    }

    public int getFiscalYear() {
        return fiscalYear;
    }

    public FiscalExerciseStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public String getNote() {
        return note;
    }
}
