package com.deepthoughtnet.clinic.api.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "prescription_dispensations", indexes = {
        @Index(name = "ix_prescription_dispensations_tenant_patient", columnList = "tenant_id,patient_id")
})
public class PrescriptionDispensationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "billing_status", nullable = false, length = 24)
    private String billingStatus;
    @Column(name = "billed_bill_id")
    private UUID billedBillId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PrescriptionDispensationEntity() {}

    public static PrescriptionDispensationEntity create(UUID tenantId, UUID prescriptionId, UUID patientId) {
        OffsetDateTime now = OffsetDateTime.now();
        PrescriptionDispensationEntity e = new PrescriptionDispensationEntity();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.prescriptionId = prescriptionId;
        e.patientId = patientId;
        e.billingStatus = "NOT_BILLED";
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void markBilled(UUID billId, String status) {
        this.billedBillId = billId;
        this.billingStatus = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public UUID getPatientId() { return patientId; }
    public String getBillingStatus() { return billingStatus; }
    public UUID getBilledBillId() { return billedBillId; }
}
