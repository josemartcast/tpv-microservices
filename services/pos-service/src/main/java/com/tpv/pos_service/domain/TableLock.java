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
        name = "table_locks",
        uniqueConstraints = @UniqueConstraint(name = "uk_table_lock_number", columnNames = "table_number")
)
public class TableLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_number", nullable = false)
    private int tableNumber;

    @Column(name = "terminal_id", nullable = false, length = 100)
    private String terminalId;

    @Column(name = "locked_by", nullable = false, length = 100)
    private String lockedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TableLock() {
    }

    public TableLock(int tableNumber, String terminalId, String lockedBy, Instant expiresAt) {
        this.tableNumber = tableNumber;
        this.terminalId = terminalId;
        this.lockedBy = lockedBy;
        this.expiresAt = expiresAt;
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

    public int getTableNumber() {
        return tableNumber;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void renew(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || expiresAt.isBefore(now);
    }
}
