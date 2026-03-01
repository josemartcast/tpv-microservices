package com.tpv.pos_service.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invoice_ticket", columnNames = "ticket_id"),
                @UniqueConstraint(name = "uk_invoice_number", columnNames = "invoice_number")
        }
)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, length = 40)
    private String invoiceNumber = "";

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invoice_ticket"))
    private Ticket ticket;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invoice_customer"))
    private Customer customer;

    @Column(nullable = false, length = 120)
    private String businessName = "";
    @Column(nullable = false, length = 160)
    private String businessLegalName = "";
    @Column(nullable = false, length = 32)
    private String businessTaxId = "";
    @Column(nullable = false, length = 200)
    private String businessAddress = "";
    @Column(nullable = false, length = 16)
    private String businessPostalCode = "";
    @Column(nullable = false, length = 80)
    private String businessCity = "";
    @Column(nullable = false, length = 80)
    private String businessProvince = "";
    @Column(nullable = false, length = 2)
    private String businessCountry = "";
    @Column(nullable = false, length = 32)
    private String businessPhone = "";
    @Column(nullable = false, length = 120)
    private String businessEmail = "";

    @Column(nullable = false, length = 120)
    private String customerDisplayName = "";
    @Column(nullable = false, length = 160)
    private String customerLegalName = "";
    @Column(nullable = false, length = 32)
    private String customerTaxId = "";
    @Column(nullable = false, length = 200)
    private String customerAddress = "";
    @Column(nullable = false, length = 16)
    private String customerPostalCode = "";
    @Column(nullable = false, length = 120)
    private String customerCity = "";
    @Column(nullable = false, length = 120)
    private String customerProvince = "";
    @Column(nullable = false, length = 64)
    private String customerCountry = "";
    @Column(nullable = false, length = 32)
    private String customerPhone = "";
    @Column(nullable = false, length = 160)
    private String customerEmail = "";

    @Column(nullable = false)
    private int totalGrossCents = 0;
    @Column(nullable = false)
    private int totalNetCents = 0;
    @Column(nullable = false)
    private int totalVatCents = 0;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(nullable = false, updatable = false, length = 80)
    private String issuedBy = "system";

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {
    }

    public Invoice(Ticket ticket, Customer customer, String issuedBy) {
        this.ticket = ticket;
        this.customer = customer;
        this.issuedBy = issuedBy == null || issuedBy.isBlank() ? "system" : issuedBy;
    }

    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber == null ? "" : invoiceNumber;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessLegalName() {
        return businessLegalName;
    }

    public String getBusinessTaxId() {
        return businessTaxId;
    }

    public String getBusinessAddress() {
        return businessAddress;
    }

    public String getBusinessPostalCode() {
        return businessPostalCode;
    }

    public String getBusinessCity() {
        return businessCity;
    }

    public String getBusinessProvince() {
        return businessProvince;
    }

    public String getBusinessCountry() {
        return businessCountry;
    }

    public String getBusinessPhone() {
        return businessPhone;
    }

    public String getBusinessEmail() {
        return businessEmail;
    }

    public String getCustomerDisplayName() {
        return customerDisplayName;
    }

    public String getCustomerLegalName() {
        return customerLegalName;
    }

    public String getCustomerTaxId() {
        return customerTaxId;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public String getCustomerPostalCode() {
        return customerPostalCode;
    }

    public String getCustomerCity() {
        return customerCity;
    }

    public String getCustomerProvince() {
        return customerProvince;
    }

    public String getCustomerCountry() {
        return customerCountry;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
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

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public List<InvoiceLine> getLines() {
        return lines;
    }

    public void setBusinessSnapshot(
            String businessName,
            String businessLegalName,
            String businessTaxId,
            String businessAddress,
            String businessPostalCode,
            String businessCity,
            String businessProvince,
            String businessCountry,
            String businessPhone,
            String businessEmail
    ) {
        this.businessName = safe(businessName);
        this.businessLegalName = safe(businessLegalName);
        this.businessTaxId = safe(businessTaxId);
        this.businessAddress = safe(businessAddress);
        this.businessPostalCode = safe(businessPostalCode);
        this.businessCity = safe(businessCity);
        this.businessProvince = safe(businessProvince);
        this.businessCountry = safe(businessCountry);
        this.businessPhone = safe(businessPhone);
        this.businessEmail = safe(businessEmail);
    }

    public void setCustomerSnapshot(
            String customerDisplayName,
            String customerLegalName,
            String customerTaxId,
            String customerAddress,
            String customerPostalCode,
            String customerCity,
            String customerProvince,
            String customerCountry,
            String customerPhone,
            String customerEmail
    ) {
        this.customerDisplayName = safe(customerDisplayName);
        this.customerLegalName = safe(customerLegalName);
        this.customerTaxId = safe(customerTaxId);
        this.customerAddress = safe(customerAddress);
        this.customerPostalCode = safe(customerPostalCode);
        this.customerCity = safe(customerCity);
        this.customerProvince = safe(customerProvince);
        this.customerCountry = safe(customerCountry);
        this.customerPhone = safe(customerPhone);
        this.customerEmail = safe(customerEmail);
    }

    public void replaceLines(List<InvoiceLine> newLines) {
        this.lines.clear();
        if (newLines == null) {
            recalcTotals();
            return;
        }
        for (InvoiceLine line : newLines) {
            line.attach(this);
            this.lines.add(line);
        }
        recalcTotals();
    }

    public void recalcTotals() {
        this.totalGrossCents = this.lines.stream().mapToInt(InvoiceLine::getLineGrossCents).sum();
        this.totalNetCents = this.lines.stream().mapToInt(InvoiceLine::getLineNetCents).sum();
        this.totalVatCents = this.lines.stream().mapToInt(InvoiceLine::getLineVatCents).sum();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
