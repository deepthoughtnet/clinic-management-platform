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
        name = "lab_report_publication_tests",
        indexes = {
                @Index(name = "ix_lab_report_publication_tests_item", columnList = "tenant_id,lab_order_item_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_report_publication_test", columnNames = {"tenant_id", "publication_artifact_id", "lab_order_item_id"})
        }
)
public class LaboratoryReportPublicationTestEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "publication_artifact_id", nullable = false)
    private UUID publicationArtifactId;

    @Column(name = "lab_order_item_id", nullable = false)
    private UUID labOrderItemId;

    @Column(name = "result_revision_number", nullable = false)
    private int resultRevisionNumber;

    @Column(name = "included_at", nullable = false)
    private OffsetDateTime includedAt;

    protected LaboratoryReportPublicationTestEntity() {
    }

    public static LaboratoryReportPublicationTestEntity create(UUID tenantId, UUID publicationArtifactId, UUID labOrderItemId, int resultRevisionNumber) {
        LaboratoryReportPublicationTestEntity entity = new LaboratoryReportPublicationTestEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.publicationArtifactId = publicationArtifactId;
        entity.labOrderItemId = labOrderItemId;
        entity.resultRevisionNumber = resultRevisionNumber;
        entity.includedAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPublicationArtifactId() {
        return publicationArtifactId;
    }

    public UUID getLabOrderItemId() {
        return labOrderItemId;
    }

    public int getResultRevisionNumber() {
        return resultRevisionNumber;
    }

    public OffsetDateTime getIncludedAt() {
        return includedAt;
    }
}
