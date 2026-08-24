package com.deepthoughtnet.clinic.laboratory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "lab_result_revisions",
        indexes = {
                @Index(name = "ix_lab_result_revisions_order_item", columnList = "tenant_id,lab_order_id,lab_order_item_id,revision_number")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_result_revision", columnNames = {"tenant_id", "lab_order_item_id", "revision_number"})
        }
)
public class LaboratoryResultRevisionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "lab_order_item_id", nullable = false)
    private UUID labOrderItemId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "source_result_id")
    private UUID sourceResultId;

    @Column(name = "result_snapshot", nullable = false, columnDefinition = "text")
    private String resultSnapshot;

    @Column(columnDefinition = "text")
    private String comments;

    @Column(name = "submission_state", nullable = false, length = 24)
    private String submissionState;

    @Column(name = "entered_at", nullable = false)
    private OffsetDateTime enteredAt;

    @Column(name = "entered_by")
    private UUID enteredBy;

    protected LaboratoryResultRevisionEntity() {
    }

    public static LaboratoryResultRevisionEntity create(
            UUID tenantId,
            UUID labOrderId,
            UUID labOrderItemId,
            int revisionNumber,
            UUID sourceResultId,
            String resultSnapshot,
            String comments,
            String submissionState,
            OffsetDateTime enteredAt,
            UUID enteredBy
    ) {
        LaboratoryResultRevisionEntity entity = new LaboratoryResultRevisionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.labOrderItemId = labOrderItemId;
        entity.revisionNumber = revisionNumber;
        entity.sourceResultId = sourceResultId;
        entity.resultSnapshot = resultSnapshot;
        entity.comments = comments;
        entity.submissionState = submissionState;
        entity.enteredAt = enteredAt == null ? OffsetDateTime.now() : enteredAt;
        entity.enteredBy = enteredBy;
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

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public UUID getSourceResultId() {
        return sourceResultId;
    }

    public String getResultSnapshot() {
        return resultSnapshot;
    }

    public String getComments() {
        return comments;
    }

    public String getSubmissionState() {
        return submissionState;
    }

    public OffsetDateTime getEnteredAt() {
        return enteredAt;
    }

    public UUID getEnteredBy() {
        return enteredBy;
    }
}
