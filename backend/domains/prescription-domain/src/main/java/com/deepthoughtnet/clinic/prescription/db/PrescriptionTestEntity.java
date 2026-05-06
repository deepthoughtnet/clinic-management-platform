package com.deepthoughtnet.clinic.prescription.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(
        name = "prescription_tests",
        indexes = {
                @Index(name = "ix_prescription_tests_tenant_prescription", columnList = "tenant_id,prescription_id"),
                @Index(name = "ix_prescription_tests_tenant_sort", columnList = "tenant_id,prescription_id,sort_order")
        }
)
public class PrescriptionTestEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "test_name", nullable = false, length = 256)
    private String testName;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected PrescriptionTestEntity() {
    }

    public static PrescriptionTestEntity create(UUID tenantId, UUID prescriptionId, String testName, String instructions, Integer sortOrder) {
        PrescriptionTestEntity entity = new PrescriptionTestEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.prescriptionId = prescriptionId;
        entity.testName = testName;
        entity.instructions = instructions;
        entity.sortOrder = sortOrder == null ? 0 : sortOrder;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public String getTestName() { return testName; }
    public String getInstructions() { return instructions; }
    public Integer getSortOrder() { return sortOrder; }
}
