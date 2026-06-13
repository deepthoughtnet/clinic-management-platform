package com.deepthoughtnet.clinic.api.lab.db;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "lab_order_results",
        indexes = {
                @Index(name = "ix_lab_order_results_tenant_order", columnList = "tenant_id,lab_order_id"),
                @Index(name = "ix_lab_order_results_tenant_item", columnList = "tenant_id,lab_order_item_id")
        }
)
public class LabOrderResultEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "lab_order_item_id")
    private UUID labOrderItemId;

    @Column(name = "test_code", nullable = false, length = 64)
    private String testCode;

    @Column(name = "test_name", nullable = false, length = 256)
    private String testName;

    @Column(name = "parameter_name", length = 256)
    private String parameterName;

    @Column(name = "component_name", length = 256)
    private String componentName;

    @Column(name = "result_value", length = 256)
    private String resultValue;

    @Column(length = 64)
    private String unit;

    @Column(name = "reference_range", length = 256)
    private String referenceRange;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "result_flag", length = 32)
    private String resultFlag;

    @Column(name = "critical_result", nullable = false)
    private boolean criticalResult;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected LabOrderResultEntity() {
    }

    public static LabOrderResultEntity create(
            UUID tenantId,
            UUID labOrderId,
            UUID labOrderItemId,
            String testCode,
            String testName,
            String parameterName,
            String componentName,
            String resultValue,
            String unit,
            String referenceRange,
            Integer sortOrder,
            String resultFlag,
            boolean criticalResult
    ) {
        LabOrderResultEntity entity = new LabOrderResultEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.labOrderItemId = labOrderItemId;
        entity.testCode = testCode;
        entity.testName = testName;
        entity.parameterName = parameterName;
        entity.componentName = componentName;
        entity.resultValue = resultValue;
        entity.unit = unit;
        entity.referenceRange = referenceRange;
        entity.sortOrder = sortOrder;
        entity.resultFlag = resultFlag;
        entity.criticalResult = criticalResult;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLabOrderId() { return labOrderId; }
    public UUID getLabOrderItemId() { return labOrderItemId; }
    public String getTestCode() { return testCode; }
    public String getTestName() { return testName; }
    public String getParameterName() { return parameterName; }
    public String getComponentName() { return componentName; }
    public String getResultValue() { return resultValue; }
    public String getUnit() { return unit; }
    public String getReferenceRange() { return referenceRange; }
    public Integer getSortOrder() { return sortOrder; }
    public String getResultFlag() { return resultFlag; }
    public boolean isCriticalResult() { return criticalResult; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
