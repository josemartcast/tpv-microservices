package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "ticket_lines",
    indexes = {
        @Index(name = "idx_ticket_lines_ticket", columnList = "ticket_id"),
        @Index(name = "idx_ticket_lines_product", columnList = "product_id")
    }
)
public class TicketLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_line_ticket"))
    private Ticket ticket;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_line_product"))
    private Product product;

    // Snapshot (histórico)
    @Column(nullable = false, length = 120)
    private String productNameSnapshot;

    @Column(nullable = false)
    private int unitPriceCentsSnapshot;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private int lineTotalCents;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected TicketLine() {}

    public TicketLine(Ticket ticket, Product product, int qty) {
        this.ticket = ticket;
        this.product = product;

        this.productNameSnapshot = product.getName();
        this.unitPriceCentsSnapshot = product.getPriceCents();

        this.qty = qty;
        recalc();
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
    public Ticket getTicket() { return ticket; }
    public Product getProduct() { return product; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public int getUnitPriceCentsSnapshot() { return unitPriceCentsSnapshot; }
    public int getQty() { return qty; }
    public int getLineTotalCents() { return lineTotalCents; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void changeQty(int qty) {
        this.qty = qty;
        recalc();
    }

    private void recalc() {
        this.lineTotalCents = this.unitPriceCentsSnapshot * this.qty;
    }
}
