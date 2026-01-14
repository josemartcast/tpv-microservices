package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(nullable = false)
    private int totalCents = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private int totalGrossCents = 0;

    @Column(nullable = false)
    private int totalNetCents = 0;

    @Column(nullable = false)
    private int totalVatCents = 0;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "cash_session_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ticket_cash_session")
    )

    private CashSession cashSession;

    public Ticket(CashSession cashSession) {
        this.cashSession = cashSession;
    }

    public Ticket() {

    }

    public CashSession getCashSession() {
        return cashSession;
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

    public Long getId() {
        return id;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public int getTotalCents() {
        return totalCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isOpen() {
        return status == TicketStatus.OPEN;
    }

    public void markPaid() {
        this.status = TicketStatus.PAID;
    }

    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }

    public void setTotalCents(int totalCents) {
        this.totalCents = totalCents;
    }

    public int getTotalGrossCents() {
        return totalGrossCents;
    }

    public int getTotalNetCents() {
        return totalNetCents;
    }

    public int getTotalVatCents() {
        return totalVatCents;
    }

    public void setTotals(int gross, int net) {
        this.totalGrossCents = gross;
        this.totalNetCents = net;
        this.totalVatCents = Math.max(0, gross - net);
    }
}
