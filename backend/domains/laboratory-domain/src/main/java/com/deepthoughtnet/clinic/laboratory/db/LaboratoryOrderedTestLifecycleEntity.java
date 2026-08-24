package com.deepthoughtnet.clinic.laboratory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "lab_order_test_lifecycles",
        indexes = {
                @Index(name = "ix_lab_order_test_lifecycle_order_state", columnList = "tenant_id,lab_order_id,state")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_order_test_lifecycle_item", columnNames = {"tenant_id", "lab_order_item_id"})
        }
)
public class LaboratoryOrderedTestLifecycleEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "lab_order_item_id", nullable = false)
    private UUID labOrderItemId;

    @Column(nullable = false, length = 40)
    private String state;

    @Column(name = "latest_result_revision", nullable = false)
    private int latestResultRevision;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected LaboratoryOrderedTestLifecycleEntity() {
    }

    public static LaboratoryOrderedTestLifecycleEntity create(UUID tenantId, UUID labOrderId, UUID labOrderItemId, String state, UUID actorAppUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        LaboratoryOrderedTestLifecycleEntity entity = new LaboratoryOrderedTestLifecycleEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.labOrderItemId = labOrderItemId;
        entity.state = state;
        entity.latestResultRevision = 0;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.updatedBy = actorAppUserId;
        return entity;
    }

    public void markState(String state, UUID actorAppUserId) {
        this.state = state;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = actorAppUserId;
    }

    public void markResultRevision(int revisionNumber, String state, UUID actorAppUserId) {
        this.latestResultRevision = Math.max(revisionNumber, this.latestResultRevision);
        this.state = state;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = actorAppUserId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getLabOrderId() {
        return labOrderId;
    }

    public UUID getLabOrderItemId() {
        return labOrderItemId;
    }

    public String getState() {
        return state;
    }

    public int getLatestResultRevision() {
        return latestResultRevision;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
