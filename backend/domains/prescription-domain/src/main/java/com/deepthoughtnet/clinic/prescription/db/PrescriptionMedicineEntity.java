package com.deepthoughtnet.clinic.prescription.db;

import com.deepthoughtnet.clinic.prescription.service.model.MedicineType;
import com.deepthoughtnet.clinic.prescription.service.model.Timing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(
        name = "prescription_medicines",
        indexes = {
                @Index(name = "ix_prescription_medicines_tenant_prescription", columnList = "tenant_id,prescription_id"),
                @Index(name = "ix_prescription_medicines_tenant_sort", columnList = "tenant_id,prescription_id,sort_order")
        }
)
public class PrescriptionMedicineEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "medicine_name", nullable = false, length = 256)
    private String medicineName;

    @Enumerated(EnumType.STRING)
    @Column(name = "medicine_type", length = 24)
    private MedicineType medicineType;

    @Column(length = 128)
    private String strength;

    @Column(nullable = false, length = 128)
    private String dosage;

    @Column(nullable = false, length = 64)
    private String frequency;

    @Column(nullable = false, length = 64)
    private String duration;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private Timing timing;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected PrescriptionMedicineEntity() {
    }

    public static PrescriptionMedicineEntity create(
            UUID tenantId,
            UUID prescriptionId,
            String medicineName,
            MedicineType medicineType,
            String strength,
            String dosage,
            String frequency,
            String duration,
            Timing timing,
            String instructions,
            Integer sortOrder
    ) {
        PrescriptionMedicineEntity entity = new PrescriptionMedicineEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.prescriptionId = prescriptionId;
        entity.medicineName = medicineName;
        entity.medicineType = medicineType;
        entity.strength = strength;
        entity.dosage = dosage;
        entity.frequency = frequency;
        entity.duration = duration;
        entity.timing = timing;
        entity.instructions = instructions;
        entity.sortOrder = sortOrder == null ? 0 : sortOrder;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public String getMedicineName() { return medicineName; }
    public MedicineType getMedicineType() { return medicineType; }
    public String getStrength() { return strength; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public String getDuration() { return duration; }
    public Timing getTiming() { return timing; }
    public String getInstructions() { return instructions; }
    public Integer getSortOrder() { return sortOrder; }
}
