package com.deepthoughtnet.clinic.api.lab.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "lab_test_parameters",
        indexes = {
                @Index(name = "ix_lab_test_parameters_tenant_test", columnList = "tenant_id,lab_test_id,sort_order")
        }
)
public class LabTestParameterEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_test_id", nullable = false)
    private UUID labTestId;

    @Column(name = "parameter_name", nullable = false, length = 256)
    private String parameterName;

    @Column(length = 64)
    private String unit;

    @Column(name = "normal_range", length = 256)
    private String normalRange;

    @Column(name = "critical_range", length = 256)
    private String criticalRange;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected LabTestParameterEntity() {
    }

    public static LabTestParameterEntity create(UUID tenantId, UUID labTestId, String parameterName, String unit, String normalRange, String criticalRange, int sortOrder) {
        LabTestParameterEntity entity = new LabTestParameterEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labTestId = labTestId;
        entity.parameterName = parameterName;
        entity.unit = unit;
        entity.normalRange = normalRange;
        entity.criticalRange = criticalRange;
        entity.sortOrder = sortOrder;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLabTestId() { return labTestId; }
    public String getParameterName() { return parameterName; }
    public String getUnit() { return unit; }
    public String getNormalRange() { return normalRange; }
    public String getCriticalRange() { return criticalRange; }
    public int getSortOrder() { return sortOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
