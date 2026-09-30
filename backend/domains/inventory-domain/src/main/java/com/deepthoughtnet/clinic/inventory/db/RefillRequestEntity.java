package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Patient pharmacy intake request; deliberately not a sale or dispense record. */
@Entity
@Table(name = "pharmacy_refill_requests", indexes = {
        @Index(name = "ux_refill_request_cycle", columnList = "tenant_id,patient_id,prescription_id,refill_cycle", unique = true),
        @Index(name = "ix_refill_request_patient", columnList = "tenant_id,patient_id,created_at")
})
public class RefillRequestEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;
    @Column(name = "refill_cycle", nullable = false, length = 96)
    private String refillCycle;
    @Column(name = "medicine_summary", length = 512)
    private String medicineSummary;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "source", nullable = false, length = 40)
    private String source;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RefillRequestEntity() { }

    public static RefillRequestEntity requested(UUID tenantId, UUID patientId, UUID prescriptionId,
                                                String refillCycle, String medicineSummary,
                                                LocalDate dueDate, String source) {
        RefillRequestEntity entity = new RefillRequestEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.prescriptionId = prescriptionId;
        entity.refillCycle = refillCycle;
        entity.medicineSummary = medicineSummary;
        entity.dueDate = dueDate;
        entity.status = "REQUESTED";
        entity.source = source == null || source.isBlank() ? "PATIENT" : source;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public String getRefillCycle() { return refillCycle; }
    public String getMedicineSummary() { return medicineSummary; }
    public LocalDate getDueDate() { return dueDate; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
