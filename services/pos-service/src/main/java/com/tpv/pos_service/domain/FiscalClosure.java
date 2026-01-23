package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "fiscal_closures",
        uniqueConstraints = @UniqueConstraint(name = "uk_fiscal_closure_cash_session", columnNames = "cash_session_id")
)
public class FiscalClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fiscal_closure_cash_session"))
    private CashSession cashSession;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // tickets
    @Column(nullable = false)
    private int paidTicketsCount;
    @Column(nullable = false)
    private int cancelledTicketsCount;

    // totals (PAID tickets)
    @Column(nullable = false)
    private int grossSalesCents;
    @Column(nullable = false)
    private int netSalesCents;
    @Column(nullable = false)
    private int vatSalesCents;

    // payments by method (PAID tickets)
    @Column(nullable = false)
    private int cashPaymentsCents;
    @Column(nullable = false)
    private int cardPaymentsCents;
    @Column(nullable = false)
    private int bizumPaymentsCents;

    protected FiscalClosure() {
    }

    public FiscalClosure(CashSession cashSession,
            int paidTicketsCount,
            int cancelledTicketsCount,
            int grossSalesCents,
            int netSalesCents,
            int vatSalesCents,
            int cashPaymentsCents,
            int cardPaymentsCents,
            int bizumPaymentsCents) {
        this.cashSession = cashSession;
        this.paidTicketsCount = paidTicketsCount;
        this.cancelledTicketsCount = cancelledTicketsCount;
        this.grossSalesCents = grossSalesCents;
        this.netSalesCents = netSalesCents;
        this.vatSalesCents = vatSalesCents;
        this.cashPaymentsCents = cashPaymentsCents;
        this.cardPaymentsCents = cardPaymentsCents;
        this.bizumPaymentsCents = bizumPaymentsCents;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getPaidTicketsCount() {
        return paidTicketsCount;
    }

    public int getCancelledTicketsCount() {
        return cancelledTicketsCount;
    }

    public int getGrossSalesCents() {
        return grossSalesCents;
    }

    public int getNetSalesCents() {
        return netSalesCents;
    }

    public int getVatSalesCents() {
        return vatSalesCents;
    }

    public int getCashPaymentsCents() {
        return cashPaymentsCents;
    }

    public int getCardPaymentsCents() {
        return cardPaymentsCents;
    }

    public int getBizumPaymentsCents() {
        return bizumPaymentsCents;
    }

}
