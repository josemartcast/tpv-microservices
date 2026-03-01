package com.tpv.pos_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invoice_line_invoice"))
    private Invoice invoice;

    @Column(name = "ticket_line_id", nullable = false)
    private Long ticketLineId;

    @Column(nullable = false, length = 120)
    private String productName = "";

    @Column(nullable = false)
    private int qty = 0;

    @Column(nullable = false)
    private int unitGrossCents = 0;

    @Column(nullable = false)
    private int lineGrossCents = 0;

    @Column(nullable = false)
    private int vatRateBps = 0;

    @Column(nullable = false)
    private int lineNetCents = 0;

    @Column(nullable = false)
    private int lineVatCents = 0;

    protected InvoiceLine() {
    }

    public InvoiceLine(
            Long ticketLineId,
            String productName,
            int qty,
            int unitGrossCents,
            int lineGrossCents,
            int vatRateBps,
            int lineNetCents,
            int lineVatCents
    ) {
        this.ticketLineId = ticketLineId;
        this.productName = productName == null ? "" : productName;
        this.qty = qty;
        this.unitGrossCents = unitGrossCents;
        this.lineGrossCents = lineGrossCents;
        this.vatRateBps = vatRateBps;
        this.lineNetCents = lineNetCents;
        this.lineVatCents = lineVatCents;
    }

    public void attach(Invoice invoice) {
        this.invoice = invoice;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketLineId() {
        return ticketLineId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQty() {
        return qty;
    }

    public int getUnitGrossCents() {
        return unitGrossCents;
    }

    public int getLineGrossCents() {
        return lineGrossCents;
    }

    public int getVatRateBps() {
        return vatRateBps;
    }

    public int getLineNetCents() {
        return lineNetCents;
    }

    public int getLineVatCents() {
        return lineVatCents;
    }
}
