package com.deepthoughtnet.clinic.prescription.db;

import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "prescriptions",
        indexes = {
                @Index(name = "ix_prescriptions_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_prescriptions_tenant_consultation", columnList = "tenant_id,consultation_id"),
                @Index(name = "ix_prescriptions_tenant_doctor", columnList = "tenant_id,doctor_user_id"),
                @Index(name = "ix_prescriptions_tenant_status", columnList = "tenant_id,status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_prescriptions_tenant_number", columnNames = {"tenant_id", "prescription_number"}),
                @UniqueConstraint(name = "uq_prescriptions_tenant_consultation", columnNames = {"tenant_id", "consultation_id"})
        }
)
public class PrescriptionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_user_id", nullable = false)
    private UUID doctorUserId;

    @Column(name = "consultation_id", nullable = false)
    private UUID consultationId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "prescription_number", nullable = false, length = 64)
    private String prescriptionNumber;

    @Column(name = "diagnosis_snapshot", columnDefinition = "text")
    private String diagnosisSnapshot;

    @Column(columnDefinition = "text")
    private String advice;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PrescriptionStatus status = PrescriptionStatus.DRAFT;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "printed_at")
    private OffsetDateTime printedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected PrescriptionEntity() {
    }

    public static PrescriptionEntity create(
            UUID tenantId,
            UUID patientId,
            UUID doctorUserId,
            UUID consultationId,
            UUID appointmentId,
            String prescriptionNumber
    ) {
        PrescriptionEntity entity = new PrescriptionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.doctorUserId = doctorUserId;
        entity.consultationId = consultationId;
        entity.appointmentId = appointmentId;
        entity.prescriptionNumber = prescriptionNumber;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String diagnosisSnapshot, String advice, LocalDate followUpDate) {
        this.diagnosisSnapshot = diagnosisSnapshot;
        this.advice = advice;
        this.followUpDate = followUpDate;
        this.updatedAt = OffsetDateTime.now();
    }

    public void finalizePrescription() {
        this.status = PrescriptionStatus.FINALIZED;
        this.finalizedAt = OffsetDateTime.now();
        this.updatedAt = this.finalizedAt;
    }

    public void markPrinted() {
        this.status = PrescriptionStatus.PRINTED;
        this.printedAt = OffsetDateTime.now();
        this.updatedAt = this.printedAt;
    }

    public void markSent() {
        this.status = PrescriptionStatus.SENT;
        this.sentAt = OffsetDateTime.now();
        this.updatedAt = this.sentAt;
    }

    public void cancel() {
        this.status = PrescriptionStatus.CANCELLED;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorUserId() { return doctorUserId; }
    public UUID getConsultationId() { return consultationId; }
    public UUID getAppointmentId() { return appointmentId; }
    public String getPrescriptionNumber() { return prescriptionNumber; }
    public String getDiagnosisSnapshot() { return diagnosisSnapshot; }
    public String getAdvice() { return advice; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public PrescriptionStatus getStatus() { return status; }
    public OffsetDateTime getFinalizedAt() { return finalizedAt; }
    public OffsetDateTime getPrintedAt() { return printedAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
