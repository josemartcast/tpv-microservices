package com.tpv.pos_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "business_profile")
public class BusinessProfile {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 120)
    private String businessName;

    @Column(nullable = false, length = 160)
    private String legalName;

    @Column(nullable = false, length = 32)
    private String taxId;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 16)
    private String postalCode;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String province;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false)
    private Instant updatedAt;

    protected BusinessProfile() {}

    public BusinessProfile(Long id, String businessName) {
        this.id = id;
        this.businessName = businessName;
        this.legalName = "";
        this.taxId = "";
        this.address = "";
        this.postalCode = "";
        this.city = "";
        this.province = "";
        this.country = "ES";
        this.phone = "";
        this.email = "";
    }

    @PrePersist
    @PreUpdate
    void touch() {
        if (businessName == null || businessName.isBlank()) {
            businessName = "Restaurante EL GUSTO";
        }
        if (legalName == null) legalName = "";
        if (taxId == null) taxId = "";
        if (address == null) address = "";
        if (postalCode == null) postalCode = "";
        if (city == null) city = "";
        if (province == null) province = "";
        if (phone == null) phone = "";
        if (email == null) email = "";
        if (country == null || country.isBlank()) {
            country = "ES";
        }
        country = country.trim().toUpperCase(Locale.ROOT);
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getBusinessName() { return businessName; }
    public String getLegalName() { return legalName; }
    public String getTaxId() { return taxId; }
    public String getAddress() { return address; }
    public String getPostalCode() { return postalCode; }
    public String getCity() { return city; }
    public String getProvince() { return province; }
    public String getCountry() { return country; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void apply(
            String businessName,
            String legalName,
            String taxId,
            String address,
            String postalCode,
            String city,
            String province,
            String country,
            String phone,
            String email
    ) {
        this.businessName = businessName;
        this.legalName = legalName;
        this.taxId = taxId;
        this.address = address;
        this.postalCode = postalCode;
        this.city = city;
        this.province = province;
        this.country = country;
        this.phone = phone;
        this.email = email;
    }
}
