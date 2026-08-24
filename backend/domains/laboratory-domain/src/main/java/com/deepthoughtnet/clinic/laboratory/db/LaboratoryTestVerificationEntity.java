package com.deepthoughtnet.clinic.laboratory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "lab_test_verifications",
        indexes = {
                @Index(name = "ix_lab_test_verifications_item", columnList = "tenant_id,lab_order_item_id,verified_at")
        }
)
public class LaboratoryTestVerificationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "lab_order_item_id", nullable = false)
    private UUID labOrderItemId;

    @Column(name = "result_revision_number")
    private Integer resultRevisionNumber;

    @Column(nullable = false, length = 32)
    private String decision;

    @Column(length = 128)
    private String reason;

    @Column(columnDefinition = "text")
    private String comments;

    @Column(name = "verified_at", nullable = false)
    private OffsetDateTime verifiedAt;

    @Column(name = "verified_by", nullable = false)
    private UUID verifiedBy;

    protected LaboratoryTestVerificationEntity() {
    }

    public static LaboratoryTestVerificationEntity create(
            UUID tenantId,
            UUID labOrderId,
            UUID labOrderItemId,
            Integer resultRevisionNumber,
            String decision,
            String reason,
            String comments,
            UUID verifiedBy
    ) {
        LaboratoryTestVerificationEntity entity = new LaboratoryTestVerificationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.labOrderItemId = labOrderItemId;
        entity.resultRevisionNumber = resultRevisionNumber;
        entity.decision = decision;
        entity.reason = reason;
        entity.comments = comments;
        entity.verifiedAt = OffsetDateTime.now();
        entity.verifiedBy = verifiedBy;
        return entity;
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

    public Integer getResultRevisionNumber() {
        return resultRevisionNumber;
    }

    public String getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public String getComments() {
        return comments;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }
}
