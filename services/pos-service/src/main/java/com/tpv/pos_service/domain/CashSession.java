package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cash_sessions")
public class CashSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashSessionStatus status = CashSessionStatus.OPEN;

    @Column(nullable = false)
    private int openingCashCents;

    @Column
    private Integer closingCashCents; // null hasta cierre

    @Column(nullable = false, updatable = false)
    private Instant openedAt;

    @Column
    private Instant closedAt;

    // Para auditoría (preparado para multiusuario/PDA)
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

    @Column(nullable = false)
    private int expectedCashCents = 0;

    protected CashSession() {
    }

    public CashSession(int openingCashCents, String openedBy, String note) {
        this.openingCashCents = openingCashCents;
        this.openedBy = openedBy;
        this.note = note;
        this.status = CashSessionStatus.OPEN;
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

    public void registerSale(int amountCents) {
        this.expectedCashCents += amountCents;
    }

    public Long getId() {
        return id;
    }

    public CashSessionStatus getStatus() {
        return status;
    }

    public int getOpeningCashCents() {
        return openingCashCents;
    }

    public Integer getClosingCashCents() {
        return closingCashCents;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void close(int closingCashCents, String closedBy, String note) {
        this.status = CashSessionStatus.CLOSED;
        this.closingCashCents = closingCashCents;
        this.closedBy = closedBy;
        this.closedAt = Instant.now();
        if (note != null && !note.isBlank()) {
            this.note = note;
        }
    }
}
