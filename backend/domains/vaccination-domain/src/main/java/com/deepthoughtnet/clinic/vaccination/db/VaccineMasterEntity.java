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

    @Column(name = "age_group", length = 128)
    private String ageGroup;

    @Column(name = "recommended_gap_days")
    private Integer recommendedGapDays;

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

    public void update(String vaccineName, String description, String ageGroup, Integer recommendedGapDays, BigDecimal defaultPrice, boolean active) {
        this.vaccineName = vaccineName;
        this.description = description;
        this.ageGroup = ageGroup;
        this.recommendedGapDays = recommendedGapDays;
        this.defaultPrice = defaultPrice;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getVaccineName() { return vaccineName; }
    public String getDescription() { return description; }
    public String getAgeGroup() { return ageGroup; }
    public Integer getRecommendedGapDays() { return recommendedGapDays; }
    public BigDecimal getDefaultPrice() { return defaultPrice; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
