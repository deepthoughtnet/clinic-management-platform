package com.deepthoughtnet.clinic.api.lab.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "lab_tests",
        indexes = {
                @Index(name = "ix_lab_tests_tenant_active", columnList = "tenant_id,active"),
                @Index(name = "ix_lab_tests_tenant_category", columnList = "tenant_id,category")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_tests_tenant_code", columnNames = {"tenant_id", "test_code"}),
                @UniqueConstraint(name = "uq_lab_tests_tenant_name", columnNames = {"tenant_id", "test_name"})
        }
)
public class LabTestMasterEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "test_code", length = 64)
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

    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected LabTestMasterEntity() {
    }

    public static LabTestMasterEntity create(UUID tenantId, String testCode, String testName) {
        LabTestMasterEntity entity = new LabTestMasterEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.testCode = testCode;
        entity.testName = testName;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(
            String testCode,
            String testName,
            String category,
            String department,
            String sampleType,
            String unit,
            String referenceRange,
            String turnaroundTime,
            BigDecimal price,
            boolean active
    ) {
        this.testCode = testCode;
        this.testName = testName;
        this.category = category;
        this.department = department;
        this.sampleType = sampleType;
        this.unit = unit;
        this.referenceRange = referenceRange;
        this.turnaroundTime = turnaroundTime;
        this.price = price;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTestCode() { return testCode; }
    public String getTestName() { return testName; }
    public String getCategory() { return category; }
    public String getDepartment() { return department; }
    public String getSampleType() { return sampleType; }
    public String getUnit() { return unit; }
    public String getReferenceRange() { return referenceRange; }
    public String getTurnaroundTime() { return turnaroundTime; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
