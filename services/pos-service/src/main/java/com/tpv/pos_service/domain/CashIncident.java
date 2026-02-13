package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "cash_incidents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cash_incident_idempotency",
                        columnNames = {"cash_session_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_cash_incident_cash_session", columnList = "cash_session_id")
        }
)
public class CashIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cash_incident_cash_session"))
    private CashSession cashSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashIncidentDirection direction;

    @Column(nullable = false)
    private int amountCents;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CashIncident() {
    }

    public CashIncident(
            CashSession cashSession,
            CashIncidentDirection direction,
            int amountCents,
            String note,
            String createdBy,
            String idempotencyKey
    ) {
        this.cashSession = cashSession;
        this.direction = direction;
        this.amountCents = amountCents;
        this.note = note;
        this.createdBy = createdBy;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CashSession getCashSession() {
        return cashSession;
    }

    public CashIncidentDirection getDirection() {
        return direction;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
