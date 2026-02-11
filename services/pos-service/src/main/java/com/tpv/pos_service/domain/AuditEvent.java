package com.tpv.pos_service.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "audit_events",
        indexes = {
            @Index(name = "idx_audit_created_at", columnList = "createdAt"),
            @Index(name = "idx_audit_action", columnList = "action"),
            @Index(name = "idx_audit_actor", columnList = "actor"),
            @Index(name = "idx_audit_resource", columnList = "resourceType,resourceId")
        }
)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 40)
    private String resourceType;

    @Column
    private Long resourceId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(length = 80)
    private String terminalId;

    @Column(length = 500)
    private String message;

    @Column(length = 4000)
    private String requestJson;

    @Column(length = 4000)
    private String responseJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            String action,
            String resourceType,
            Long resourceId,
            String status,
            String actor,
            String terminalId,
            String message,
            String requestJson,
            String responseJson
    ) {
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.status = status;
        this.actor = actor;
        this.terminalId = terminalId;
        this.message = message;
        this.requestJson = requestJson;
        this.responseJson = responseJson;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public String getStatus() {
        return status;
    }

    public String getActor() {
        return actor;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
