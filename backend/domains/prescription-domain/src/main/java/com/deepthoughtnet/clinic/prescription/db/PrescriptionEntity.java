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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "prescriptions",
        indexes = {
                @Index(name = "ix_prescriptions_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_prescriptions_tenant_consultation", columnList = "tenant_id,consultation_id"),
                @Index(name = "ix_prescriptions_tenant_parent", columnList = "tenant_id,parent_prescription_id"),
                @Index(name = "ix_prescriptions_tenant_superseded_by", columnList = "tenant_id,superseded_by_prescription_id"),
                @Index(name = "ix_prescriptions_tenant_doctor", columnList = "tenant_id,doctor_user_id"),
                @Index(name = "ix_prescriptions_tenant_status", columnList = "tenant_id,status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_prescriptions_tenant_number", columnNames = {"tenant_id", "prescription_number"})
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

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber = 1;

    @Column(name = "parent_prescription_id")
    private UUID parentPrescriptionId;

    @Column(name = "correction_reason", columnDefinition = "text")
    private String correctionReason;

    @Column(name = "flow_type", length = 32)
    private String flowType;

    @Column(name = "corrected_at")
    private OffsetDateTime correctedAt;

    @Column(name = "superseded_by_prescription_id")
    private UUID supersededByPrescriptionId;

    @Column(name = "superseded_at")
    private OffsetDateTime supersededAt;

    @Column(name = "finalized_by_doctor_user_id")
    private UUID finalizedByDoctorUserId;

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

    @Version
    @Column(nullable = false)
    private int version;

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

    public void preview() {
        if (this.status == PrescriptionStatus.DRAFT) {
            this.status = PrescriptionStatus.PREVIEWED;
            this.updatedAt = OffsetDateTime.now();
        }
    }

    public void makeCorrectionVersion(UUID parentPrescriptionId, int versionNumber, String correctionReason, String flowType) {
        this.parentPrescriptionId = parentPrescriptionId;
        this.versionNumber = versionNumber;
        this.correctionReason = correctionReason;
        this.flowType = flowType;
    }

    public void markCorrected() {
        this.status = PrescriptionStatus.CORRECTED;
        this.correctedAt = OffsetDateTime.now();
        this.updatedAt = this.correctedAt;
    }

    public void markSuperseded(UUID supersededByPrescriptionId) {
        this.status = PrescriptionStatus.SUPERSEDED;
        this.supersededByPrescriptionId = supersededByPrescriptionId;
        this.supersededAt = OffsetDateTime.now();
        this.updatedAt = this.supersededAt;
    }

    public void finalizePrescription(UUID finalizedByDoctorUserId) {
        this.status = PrescriptionStatus.FINALIZED;
        this.finalizedByDoctorUserId = finalizedByDoctorUserId;
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
    public Integer getVersionNumber() { return versionNumber; }
    public UUID getParentPrescriptionId() { return parentPrescriptionId; }
    public String getCorrectionReason() { return correctionReason; }
    public String getFlowType() { return flowType; }
    public OffsetDateTime getCorrectedAt() { return correctedAt; }
    public UUID getSupersededByPrescriptionId() { return supersededByPrescriptionId; }
    public OffsetDateTime getSupersededAt() { return supersededAt; }
    public UUID getFinalizedByDoctorUserId() { return finalizedByDoctorUserId; }
    public String getDiagnosisSnapshot() { return diagnosisSnapshot; }
    public String getAdvice() { return advice; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public PrescriptionStatus getStatus() { return status; }
    public OffsetDateTime getFinalizedAt() { return finalizedAt; }
    public OffsetDateTime getPrintedAt() { return printedAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
