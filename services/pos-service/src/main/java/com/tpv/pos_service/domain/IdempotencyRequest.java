package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "idempotency_requests",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_idempotency_scope_resource_key",
                    columnNames = {"scope", "resource_id", "idempotency_key"}
            )
        },
        indexes = {
            @Index(name = "idx_idempotency_lookup", columnList = "scope,resource_id,idempotency_key")
        }
)
public class IdempotencyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String scope;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "response_json", nullable = false, length = 8000)
    private String responseJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRequest() {
    }

    public IdempotencyRequest(String scope, Long resourceId, String idempotencyKey, String responseJson) {
        this.scope = scope;
        this.resourceId = resourceId;
        this.idempotencyKey = idempotencyKey;
        this.responseJson = responseJson;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getResponseJson() {
        return responseJson;
    }
}
