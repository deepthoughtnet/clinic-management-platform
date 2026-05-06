package com.deepthoughtnet.clinic.identity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_subscriptions", indexes = {
        @Index(name = "ix_tenant_subscriptions_tenant", columnList = "tenant_id"),
        @Index(name = "ix_tenant_subscriptions_tenant_status", columnList = "tenant_id,status")
})
public class TenantSubscriptionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false, length = 32)
    private String planId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private boolean trial;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TenantSubscriptionEntity() {
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPlanId() { return planId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public boolean isTrial() { return trial; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
