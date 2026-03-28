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

    @Column(nullable = false)
    private int discountCents = 0;

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

    @Column
    private Integer tableNumber;

    @Column(nullable = false)
    private boolean billRequested = false;

    @Column(length = 80)
    private String billRequestedBy;

    @Column(length = 64)
    private String billRequestedTerminalId;

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

    public Ticket(CashSession cashSession, Integer tableNumber) {
        this.cashSession = cashSession;
        this.tableNumber = tableNumber;
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

    public void reopen() {
        this.status = TicketStatus.OPEN;
    }

    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }

    public void setTotalCents(int totalCents) {
        this.totalCents = totalCents;
    }

    public int getDiscountCents() {
        return discountCents;
    }

    public void setDiscountCents(int discountCents) {
        this.discountCents = Math.max(0, discountCents);
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

    public Integer getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(Integer tableNumber) {
        this.tableNumber = tableNumber;
    }

    public boolean isBillRequested() {
        return billRequested;
    }

    public void setBillRequested(boolean billRequested) {
        this.billRequested = billRequested;
        if (!billRequested) {
            this.billRequestedBy = null;
            this.billRequestedTerminalId = null;
        }
    }

    public String getBillRequestedBy() {
        return billRequestedBy;
    }

    public void setBillRequestedBy(String billRequestedBy) {
        this.billRequestedBy = billRequestedBy;
    }

    public String getBillRequestedTerminalId() {
        return billRequestedTerminalId;
    }

    public void setBillRequestedTerminalId(String billRequestedTerminalId) {
        this.billRequestedTerminalId = billRequestedTerminalId;
    }
}
