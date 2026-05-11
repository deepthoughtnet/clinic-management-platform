package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "medicine_catalogue",
        indexes = {
                @Index(name = "ix_medicine_catalogue_tenant_active", columnList = "tenant_id,active"),
                @Index(name = "ix_medicine_catalogue_tenant_name", columnList = "tenant_id,medicine_name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_medicine_catalogue_tenant_name", columnNames = {"tenant_id", "medicine_name"})
        }
)
public class MedicineEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "medicine_name", nullable = false, length = 256)
    private String medicineName;

    @Column(name = "medicine_type", nullable = false, length = 24)
    private String medicineType;

    @Column(name = "generic_name", length = 256)
    private String genericName;

    @Column(name = "brand_name", length = 256)
    private String brandName;

    @Column(length = 128)
    private String category;

    @Column(name = "dosage_form", length = 64)
    private String dosageForm;

    @Column(length = 128)
    private String strength;

    @Column(length = 32)
    private String unit;

    @Column(length = 256)
    private String manufacturer;

    @Column(name = "default_dosage", length = 128)
    private String defaultDosage;

    @Column(name = "default_frequency", length = 64)
    private String defaultFrequency;

    @Column(name = "default_duration_days")
    private Integer defaultDurationDays;

    @Column(name = "default_timing", length = 24)
    private String defaultTiming;

    @Column(name = "default_instructions", columnDefinition = "text")
    private String defaultInstructions;

    @Column(name = "default_price", precision = 18, scale = 2)
    private BigDecimal defaultPrice;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected MedicineEntity() {
    }

    public static MedicineEntity create(UUID tenantId, String medicineName, String medicineType) {
        OffsetDateTime now = OffsetDateTime.now();
        MedicineEntity entity = new MedicineEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.medicineName = medicineName;
        entity.medicineType = medicineType;
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(
            String medicineName,
            String medicineType,
            String genericName,
            String brandName,
            String category,
            String dosageForm,
            String strength,
            String unit,
            String manufacturer,
            String defaultDosage,
            String defaultFrequency,
            Integer defaultDurationDays,
            String defaultTiming,
            String defaultInstructions,
            BigDecimal defaultPrice,
            BigDecimal taxRate,
            boolean active
    ) {
        this.medicineName = medicineName;
        this.medicineType = medicineType;
        this.genericName = genericName;
        this.brandName = brandName;
        this.category = category;
        this.dosageForm = dosageForm;
        this.strength = strength;
        this.unit = unit;
        this.manufacturer = manufacturer;
        this.defaultDosage = defaultDosage;
        this.defaultFrequency = defaultFrequency;
        this.defaultDurationDays = defaultDurationDays;
        this.defaultTiming = defaultTiming;
        this.defaultInstructions = defaultInstructions;
        this.defaultPrice = defaultPrice;
        this.taxRate = taxRate;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getMedicineName() { return medicineName; }
    public String getMedicineType() { return medicineType; }
    public String getGenericName() { return genericName; }
    public String getBrandName() { return brandName; }
    public String getCategory() { return category; }
    public String getDosageForm() { return dosageForm; }
    public String getStrength() { return strength; }
    public String getUnit() { return unit; }
    public String getManufacturer() { return manufacturer; }
    public String getDefaultDosage() { return defaultDosage; }
    public String getDefaultFrequency() { return defaultFrequency; }
    public Integer getDefaultDurationDays() { return defaultDurationDays; }
    public String getDefaultTiming() { return defaultTiming; }
    public String getDefaultInstructions() { return defaultInstructions; }
    public BigDecimal getDefaultPrice() { return defaultPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
