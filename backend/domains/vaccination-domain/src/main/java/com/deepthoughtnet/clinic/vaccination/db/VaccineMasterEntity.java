package com.deepthoughtnet.clinic.vaccination.db;

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
        name = "vaccines",
        indexes = {
                @Index(name = "ix_vaccines_tenant_active", columnList = "tenant_id,active")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_vaccines_tenant_name", columnNames = {"tenant_id", "vaccine_name"})
        }
)
public class VaccineMasterEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vaccine_name", nullable = false, length = 256)
    private String vaccineName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "manufacturer", length = 256)
    private String manufacturer;

    @Column(name = "brand_name", length = 256)
    private String brandName;

    @Column(name = "vaccine_group", length = 128)
    private String vaccineGroup;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(length = 32)
    private String route;

    @Column(name = "administration_site", length = 128)
    private String administrationSite;

    @Column(name = "storage_temperature", length = 128)
    private String storageTemperature;

    @Column(name = "ndc_barcode", length = 128)
    private String ndcBarcode;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "inventory_item_code", length = 128)
    private String inventoryItemCode;

    @Column(name = "stock_tracking_enabled", nullable = false)
    private boolean stockTrackingEnabled = false;

    @Column(name = "schedule_type", length = 32)
    private String scheduleType;

    @Column(name = "age_group", length = 128)
    private String ageGroup;

    @Column(name = "min_age_days")
    private Integer minAgeDays;

    @Column(name = "recommended_age_days")
    private Integer recommendedAgeDays;

    @Column(name = "max_age_days")
    private Integer maxAgeDays;

    @Column(name = "recommended_gap_days")
    private Integer recommendedGapDays;

    @Column(name = "booster_gap_days")
    private Integer boosterGapDays;

    @Column(name = "booster_rules", columnDefinition = "text")
    private String boosterRules;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = false;

    @Column(name = "recurrence_days")
    private Integer recurrenceDays;

    @Column(name = "recommendation_policy", length = 32)
    private String recommendationPolicy;

    @Column(name = "catch_up_policy", length = 32)
    private String catchUpPolicy;

    @Column(name = "catch_up_max_age_days")
    private Integer catchUpMaxAgeDays;

    @Column(name = "applicable_age_group", length = 32)
    private String applicableAgeGroup;

    @Column(name = "clinical_indications", columnDefinition = "text")
    private String clinicalIndications;

    @Column(name = "default_price", precision = 18, scale = 2)
    private BigDecimal defaultPrice;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected VaccineMasterEntity() {
    }

    public static VaccineMasterEntity create(UUID tenantId, String vaccineName) {
        VaccineMasterEntity entity = new VaccineMasterEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.vaccineName = vaccineName;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(
            String vaccineName,
            String description,
            String manufacturer,
            String brandName,
            String vaccineGroup,
            Integer doseNumber,
            String route,
            String administrationSite,
            String storageTemperature,
            String ndcBarcode,
            UUID inventoryItemId,
            String inventoryItemCode,
            boolean stockTrackingEnabled,
            String scheduleType,
            String ageGroup,
            Integer minAgeDays,
            Integer recommendedAgeDays,
            Integer maxAgeDays,
            Integer recommendedGapDays,
            Integer boosterGapDays,
            String boosterRules,
            boolean recurring,
            Integer recurrenceDays,
            String recommendationPolicy,
            String catchUpPolicy,
            Integer catchUpMaxAgeDays,
            String applicableAgeGroup,
            String clinicalIndications,
            BigDecimal defaultPrice,
            boolean active
    ) {
        this.vaccineName = vaccineName;
        this.description = description;
        this.manufacturer = manufacturer;
        this.brandName = brandName;
        this.vaccineGroup = vaccineGroup;
        this.doseNumber = doseNumber;
        this.route = route;
        this.administrationSite = administrationSite;
        this.storageTemperature = storageTemperature;
        this.ndcBarcode = ndcBarcode;
        this.inventoryItemId = inventoryItemId;
        this.inventoryItemCode = inventoryItemCode;
        this.stockTrackingEnabled = stockTrackingEnabled;
        this.scheduleType = scheduleType;
        this.ageGroup = ageGroup;
        this.minAgeDays = minAgeDays;
        this.recommendedAgeDays = recommendedAgeDays;
        this.maxAgeDays = maxAgeDays;
        this.recommendedGapDays = recommendedGapDays;
        this.boosterGapDays = boosterGapDays;
        this.boosterRules = boosterRules;
        this.recurring = recurring;
        this.recurrenceDays = recurrenceDays;
        this.recommendationPolicy = recommendationPolicy;
        this.catchUpPolicy = catchUpPolicy;
        this.catchUpMaxAgeDays = catchUpMaxAgeDays;
        this.applicableAgeGroup = applicableAgeGroup;
        this.clinicalIndications = clinicalIndications;
        this.defaultPrice = defaultPrice;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getVaccineName() { return vaccineName; }
    public String getDescription() { return description; }
    public String getManufacturer() { return manufacturer; }
    public String getBrandName() { return brandName; }
    public String getVaccineGroup() { return vaccineGroup; }
    public Integer getDoseNumber() { return doseNumber; }
    public String getRoute() { return route; }
    public String getAdministrationSite() { return administrationSite; }
    public String getStorageTemperature() { return storageTemperature; }
    public String getNdcBarcode() { return ndcBarcode; }
    public UUID getInventoryItemId() { return inventoryItemId; }
    public String getInventoryItemCode() { return inventoryItemCode; }
    public boolean isStockTrackingEnabled() { return stockTrackingEnabled; }
    public String getScheduleType() { return scheduleType; }
    public String getAgeGroup() { return ageGroup; }
    public Integer getMinAgeDays() { return minAgeDays; }
    public Integer getRecommendedAgeDays() { return recommendedAgeDays; }
    public Integer getMaxAgeDays() { return maxAgeDays; }
    public Integer getRecommendedGapDays() { return recommendedGapDays; }
    public Integer getGapDays() { return recommendedGapDays; }
    public Integer getBoosterGapDays() { return boosterGapDays; }
    public String getBoosterRules() { return boosterRules; }
    public boolean isRecurring() { return recurring; }
    public Integer getRecurrenceDays() { return recurrenceDays; }
    public String getRecommendationPolicy() { return recommendationPolicy; }
    public String getCatchUpPolicy() { return catchUpPolicy; }
    public Integer getCatchUpMaxAgeDays() { return catchUpMaxAgeDays; }
    public String getApplicableAgeGroup() { return applicableAgeGroup; }
    public String getClinicalIndications() { return clinicalIndications; }
    public BigDecimal getDefaultPrice() { return defaultPrice; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
