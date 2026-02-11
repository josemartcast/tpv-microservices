package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_payment_ticket_idempotency", columnNames = {"ticket_id", "idempotency_key"})
        },
        indexes = {
            @Index(name = "idx_payment_ticket", columnList = "ticket_id"),
            @Index(name = "idx_payment_method", columnList = "method")
        }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ticket_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_ticket")
    )
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Column(nullable = false)
    private int amountCents;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(Ticket ticket, PaymentMethod method, int amountCents, String idempotencyKey) {
        this.ticket = ticket;
        this.method = method;
        this.amountCents = amountCents;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void onCreatedAt() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
