package com.deepthoughtnet.clinic.api.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "prescription_dispense_items", indexes = {
        @Index(name = "ix_prescription_dispense_items_tenant_disp", columnList = "tenant_id,dispensation_id"),
        @Index(name = "ix_prescription_dispense_items_tenant_presc", columnList = "tenant_id,prescription_id")
})
public class PrescriptionDispenseItemEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "dispensation_id", nullable = false)
    private UUID dispensationId;
    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;
    @Column(name = "prescription_medicine_id")
    private UUID prescriptionMedicineId;
    @Column(name = "medicine_id", nullable = false)
    private UUID medicineId;
    @Column(name = "prescribed_medicine_name", nullable = false, length = 256)
    private String prescribedMedicineName;
    @Column(name = "prescribed_sort_order")
    private Integer prescribedSortOrder;
    @Column(name = "prescribed_quantity", nullable = false)
    private int prescribedQuantity;
    @Column(name = "dispensed_quantity", nullable = false)
    private int dispensedQuantity;
    @Column(name = "last_batch_id")
    private UUID lastBatchId;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PrescriptionDispenseItemEntity() {}

    public static PrescriptionDispenseItemEntity create(UUID tenantId, UUID dispensationId, UUID prescriptionId, UUID prescriptionMedicineId, UUID medicineId, String name, Integer sortOrder, int prescribedQuantity) {
        OffsetDateTime now = OffsetDateTime.now();
        PrescriptionDispenseItemEntity e = new PrescriptionDispenseItemEntity();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.dispensationId = dispensationId;
        e.prescriptionId = prescriptionId;
        e.prescriptionMedicineId = prescriptionMedicineId;
        e.medicineId = medicineId;
        e.prescribedMedicineName = name;
        e.prescribedSortOrder = sortOrder;
        e.prescribedQuantity = Math.max(0, prescribedQuantity);
        e.dispensedQuantity = 0;
        e.status = "NOT_DISPENSED";
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void addDispense(int qty, UUID batchId) {
        this.dispensedQuantity = this.dispensedQuantity + Math.max(0, qty);
        this.lastBatchId = batchId;
        if (dispensedQuantity <= 0) status = "NOT_DISPENSED";
        else if (prescribedQuantity > 0 && dispensedQuantity >= prescribedQuantity) status = "DISPENSED";
        else status = "PARTIALLY_DISPENSED";
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDispensationId() { return dispensationId; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public UUID getPrescriptionMedicineId() { return prescriptionMedicineId; }
    public UUID getMedicineId() { return medicineId; }
    public String getPrescribedMedicineName() { return prescribedMedicineName; }
    public Integer getPrescribedSortOrder() { return prescribedSortOrder; }
    public int getPrescribedQuantity() { return prescribedQuantity; }
    public int getDispensedQuantity() { return dispensedQuantity; }
    public UUID getLastBatchId() { return lastBatchId; }
    public String getStatus() { return status; }
}
