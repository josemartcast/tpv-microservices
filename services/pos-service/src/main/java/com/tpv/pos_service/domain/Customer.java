package com.tpv.pos_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "customers",
        uniqueConstraints = @UniqueConstraint(name = "uk_customer_display_name", columnNames = "display_name")
)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 120, unique = true)
    private String displayName;

    @Column(name = "legal_name", nullable = false, length = 160)
    private String legalName = "";

    @Column(name = "tax_id", nullable = false, length = 32)
    private String taxId = "";

    @Column(name = "fiscal_address", nullable = false, length = 200)
    private String fiscalAddress = "";

    @Column(name = "postal_code", nullable = false, length = 16)
    private String postalCode = "";

    @Column(nullable = false, length = 120)
    private String city = "";

    @Column(nullable = false, length = 120)
    private String province = "";

    @Column(nullable = false, length = 64)
    private String country = "";

    @Column(nullable = false, length = 32)
    private String phone = "";

    @Column(nullable = false, length = 160)
    private String email = "";

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Customer() {
    }

    public Customer(String displayName) {
        this.displayName = displayName;
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

    public String getDisplayName() {
        return displayName;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTaxId() {
        return taxId;
    }

    public String getFiscalAddress() {
        return fiscalAddress;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getProvince() {
        return province;
    }

    public String getCountry() {
        return country;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void updateFiscalData(
            String legalName,
            String taxId,
            String fiscalAddress,
            String postalCode,
            String city,
            String province,
            String country,
            String phone,
            String email
    ) {
        this.legalName = legalName == null ? "" : legalName;
        this.taxId = taxId == null ? "" : taxId;
        this.fiscalAddress = fiscalAddress == null ? "" : fiscalAddress;
        this.postalCode = postalCode == null ? "" : postalCode;
        this.city = city == null ? "" : city;
        this.province = province == null ? "" : province;
        this.country = country == null ? "" : country;
        this.phone = phone == null ? "" : phone;
        this.email = email == null ? "" : email;
    }

    public void deactivate() {
        this.active = false;
    }
}
