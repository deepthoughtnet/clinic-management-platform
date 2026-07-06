package com.deepthoughtnet.clinic.identity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_onboarding_statuses", indexes = {
        @Index(name = "ix_tenant_onboarding_statuses_tenant", columnList = "tenant_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_tenant_onboarding_statuses_tenant", columnNames = {"tenant_id"})
})
public class TenantOnboardingEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(nullable = false)
    private boolean skipped = false;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "skipped_at")
    private OffsetDateTime skippedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantOnboardingEntity() {
    }

    public static TenantOnboardingEntity create(UUID tenantId, boolean completed) {
        TenantOnboardingEntity entity = new TenantOnboardingEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.completed = completed;
        entity.skipped = false;
        entity.completedAt = completed ? OffsetDateTime.now() : null;
        entity.skippedAt = null;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getSkippedAt() {
        return skippedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markCompleted() {
        this.completed = true;
        this.skipped = false;
        this.completedAt = OffsetDateTime.now();
        this.updatedAt = this.completedAt;
    }

    public void markSkipped() {
        this.skipped = true;
        this.completed = false;
        this.skippedAt = OffsetDateTime.now();
        this.updatedAt = this.skippedAt;
    }
}
