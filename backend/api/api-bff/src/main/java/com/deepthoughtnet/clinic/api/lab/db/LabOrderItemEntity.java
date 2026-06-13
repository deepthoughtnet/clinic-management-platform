package com.deepthoughtnet.clinic.api.lab.db;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "lab_order_items",
        indexes = {
                @Index(name = "ix_lab_order_items_tenant_order", columnList = "tenant_id,lab_order_id")
        }
)
public class LabOrderItemEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "lab_test_id")
    private UUID labTestId;

    @Column(name = "test_code", nullable = false, length = 64)
    private String testCode;

    @Column(name = "test_name", nullable = false, length = 256)
    private String testName;

    @Column(nullable = false, length = 128)
    private String category;

    @Column(length = 128)
    private String department;

    @Column(name = "sample_type", length = 128)
    private String sampleType;

    @Column(length = 64)
    private String unit;

    @Column(name = "reference_range", length = 256)
    private String referenceRange;

    @Column(name = "turnaround_time", length = 128)
    private String turnaroundTime;

    @Column(name = "price", precision = 18, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected LabOrderItemEntity() {
    }

    public static LabOrderItemEntity create(
            UUID tenantId,
            UUID labOrderId,
            UUID labTestId,
            String testCode,
            String testName,
            String category,
            String department,
            String sampleType,
            String unit,
            String referenceRange,
            String turnaroundTime,
            BigDecimal price,
            int sortOrder
    ) {
        LabOrderItemEntity entity = new LabOrderItemEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.labTestId = labTestId;
        entity.testCode = testCode;
        entity.testName = testName;
        entity.category = category;
        entity.department = department;
        entity.sampleType = sampleType;
        entity.unit = unit;
        entity.referenceRange = referenceRange;
        entity.turnaroundTime = turnaroundTime;
        entity.price = price;
        entity.sortOrder = sortOrder;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLabOrderId() { return labOrderId; }
    public UUID getLabTestId() { return labTestId; }
    public String getTestCode() { return testCode; }
    public String getTestName() { return testName; }
    public String getCategory() { return category; }
    public String getDepartment() { return department; }
    public String getSampleType() { return sampleType; }
    public String getUnit() { return unit; }
    public String getReferenceRange() { return referenceRange; }
    public String getTurnaroundTime() { return turnaroundTime; }
    public BigDecimal getPrice() { return price; }
    public int getSortOrder() { return sortOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
