package com.deepthoughtnet.clinic.api.lab.db;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "lab_orders",
        indexes = {
                @Index(name = "ix_lab_orders_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_lab_orders_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_lab_orders_tenant_consultation", columnList = "tenant_id,consultation_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_orders_tenant_order_number", columnNames = {"tenant_id", "order_number"})
        }
)
public class LabOrderEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_number", nullable = false, length = 64)
    private String orderNumber;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_number", length = 64)
    private String patientNumber;

    @Column(name = "patient_name", length = 256)
    private String patientName;

    @Column(name = "doctor_user_id")
    private UUID doctorUserId;

    @Column(name = "doctor_name", length = 256)
    private String doctorName;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_origin", nullable = false, length = 32)
    private LabOrderOrigin orderOrigin = LabOrderOrigin.WALK_IN;

    @Column(name = "requested_by_internal_doctor_id")
    private UUID requestedByInternalDoctorId;

    @Column(name = "external_doctor_name", length = 256)
    private String externalDoctorName;

    @Column(name = "external_doctor_mobile", length = 32)
    private String externalDoctorMobile;

    @Column(name = "external_clinic_name", length = 256)
    private String externalClinicName;

    @Column(name = "referral_source", length = 128)
    private String referralSource;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LabOrderStatus status = LabOrderStatus.ORDERED;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt = OffsetDateTime.now();

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "external_lab_vendor", length = 256)
    private String externalLabVendor;

    @Column(name = "external_reference_number", length = 128)
    private String externalReferenceNumber;

    @Column(name = "payment_collected_at")
    private OffsetDateTime paymentCollectedAt;

    @Column(name = "ready_for_collection_at")
    private OffsetDateTime readyForCollectionAt;

    @Column(name = "sample_type", length = 128)
    private String sampleType;

    @Column(name = "sample_collected_at")
    private OffsetDateTime sampleCollectedAt;

    @Column(name = "sample_collected_by_user_id")
    private UUID sampleCollectedByUserId;

    @Column(name = "sample_collected_by", length = 256)
    private String sampleCollectedBy;

    @Column(name = "sample_collection_notes", columnDefinition = "text")
    private String sampleCollectionNotes;

    @Column(name = "processing_started_at")
    private OffsetDateTime processingStartedAt;

    @Column(name = "result_entered_at")
    private OffsetDateTime resultEnteredAt;

    @Column(name = "result_comments", columnDefinition = "text")
    private String resultComments;

    @Column(name = "report_generated_at")
    private OffsetDateTime reportGeneratedAt;

    @Column(name = "report_generated_by_user_id")
    private UUID reportGeneratedByUserId;

    @Column(name = "report_filename", length = 256)
    private String reportFilename;

    @Column(name = "report_published_at")
    private OffsetDateTime reportPublishedAt;

    @Column(name = "report_published_by_user_id")
    private UUID reportPublishedByUserId;

    @Column(name = "report_delivery_status", length = 32)
    private String reportDeliveryStatus;

    @Column(name = "report_delivery_channels", columnDefinition = "text")
    private String reportDeliveryChannels;

    @Column(name = "report_delivery_notes", columnDefinition = "text")
    private String reportDeliveryNotes;

    @Column(name = "doctor_reviewed_at")
    private OffsetDateTime doctorReviewedAt;

    @Column(name = "doctor_reviewed_by_user_id")
    private UUID doctorReviewedByUserId;

    @Column(name = "doctor_reviewed_by", length = 256)
    private String doctorReviewedBy;

    @Column(name = "doctor_review_decision", length = 32)
    private String doctorReviewDecision;

    @Column(name = "doctor_review_reason", length = 128)
    private String doctorReviewReason;

    @Column(name = "doctor_comments", columnDefinition = "text")
    private String doctorComments;

    @Column(name = "lab_verified_at")
    private OffsetDateTime labVerifiedAt;

    @Column(name = "lab_verified_by")
    private UUID labVerifiedBy;

    @Column(name = "lab_verification_decision", length = 32)
    private String labVerificationDecision;

    @Column(name = "lab_verification_comments", columnDefinition = "text")
    private String labVerificationComments;

    @Column(name = "lab_verification_reason", length = 128)
    private String labVerificationReason;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "delivered_by_user_id")
    private UUID deliveredByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected LabOrderEntity() {
    }

    public static LabOrderEntity create(
            UUID tenantId,
            String orderNumber,
            UUID patientId,
            String patientNumber,
            String patientName,
            UUID doctorUserId,
            String doctorName,
            UUID consultationId,
            LabOrderOrigin orderOrigin,
            UUID requestedByInternalDoctorId,
            String externalDoctorName,
            String externalDoctorMobile,
            String externalClinicName,
            String referralSource,
            String notes
    ) {
        LabOrderEntity entity = new LabOrderEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.orderNumber = orderNumber;
        entity.patientId = patientId;
        entity.patientNumber = patientNumber;
        entity.patientName = patientName;
        entity.doctorUserId = doctorUserId;
        entity.doctorName = doctorName;
        entity.consultationId = consultationId;
        entity.orderOrigin = orderOrigin == null ? LabOrderOrigin.WALK_IN : orderOrigin;
        entity.requestedByInternalDoctorId = requestedByInternalDoctorId;
        entity.externalDoctorName = externalDoctorName;
        entity.externalDoctorMobile = externalDoctorMobile;
        entity.externalClinicName = externalClinicName;
        entity.referralSource = referralSource;
        entity.notes = notes;
        entity.status = LabOrderStatus.ORDERED;
        entity.orderedAt = OffsetDateTime.now();
        entity.createdAt = entity.orderedAt;
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void linkBill(UUID billId) {
        this.billId = billId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void linkExternalLab(String vendor, String referenceNumber) {
        this.externalLabVendor = vendor;
        this.externalReferenceNumber = referenceNumber;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markStatus(LabOrderStatus status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markPaymentCollected() {
        this.paymentCollectedAt = OffsetDateTime.now();
        this.updatedAt = this.paymentCollectedAt;
    }

    public void markReadyForCollection() {
        this.status = LabOrderStatus.READY_FOR_COLLECTION;
        this.readyForCollectionAt = OffsetDateTime.now();
        this.updatedAt = this.readyForCollectionAt;
    }

    public void markSampleCollected(OffsetDateTime collectedAt, UUID collectedByUserId, String collectedBy, String sampleType, String notes) {
        this.status = LabOrderStatus.SAMPLE_COLLECTED;
        this.sampleCollectedAt = collectedAt == null ? OffsetDateTime.now() : collectedAt;
        this.sampleCollectedByUserId = collectedByUserId;
        this.sampleCollectedBy = collectedBy;
        this.sampleType = sampleType;
        this.sampleCollectionNotes = notes;
        this.updatedAt = this.sampleCollectedAt;
    }

    public void markProcessingStarted() {
        if (this.processingStartedAt == null) {
            this.processingStartedAt = OffsetDateTime.now();
        }
        this.status = LabOrderStatus.PROCESSING;
        this.updatedAt = this.processingStartedAt;
    }

    public void markResultsEntered(String comments) {
        this.status = LabOrderStatus.RESULT_ENTERED;
        this.resultEnteredAt = OffsetDateTime.now();
        this.resultComments = comments;
        this.updatedAt = this.resultEnteredAt;
    }

    public void markReportGenerated(UUID reportGeneratedByUserId, String reportFilename) {
        this.status = LabOrderStatus.REPORT_READY;
        this.reportGeneratedAt = OffsetDateTime.now();
        this.reportGeneratedByUserId = reportGeneratedByUserId;
        this.reportFilename = reportFilename;
        this.reportPublishedAt = this.reportGeneratedAt;
        this.reportPublishedByUserId = reportGeneratedByUserId;
        this.reportDeliveryStatus = "PUBLISHED";
        this.updatedAt = this.reportGeneratedAt;
    }

    public void markReportPublished(
            UUID reportPublishedByUserId,
            String reportFilename,
            String reportDeliveryStatus,
            String reportDeliveryChannels,
            String reportDeliveryNotes
    ) {
        this.status = LabOrderStatus.REPORT_GENERATED;
        this.reportGeneratedAt = OffsetDateTime.now();
        this.reportGeneratedByUserId = reportPublishedByUserId;
        this.reportPublishedAt = this.reportGeneratedAt;
        this.reportPublishedByUserId = reportPublishedByUserId;
        this.reportFilename = reportFilename;
        this.reportDeliveryStatus = reportDeliveryStatus;
        this.reportDeliveryChannels = reportDeliveryChannels;
        this.reportDeliveryNotes = reportDeliveryNotes;
        this.updatedAt = this.reportGeneratedAt;
    }

    public void markDoctorReviewed(UUID doctorReviewedByUserId, String doctorReviewedBy, String doctorReviewDecision, String doctorReviewReason, String doctorComments) {
        this.status = LabOrderStatus.DOCTOR_REVIEWED;
        this.doctorReviewedAt = OffsetDateTime.now();
        this.doctorReviewedByUserId = doctorReviewedByUserId;
        this.doctorReviewedBy = doctorReviewedBy;
        this.doctorReviewDecision = doctorReviewDecision;
        this.doctorReviewReason = doctorReviewReason;
        this.doctorComments = doctorComments;
        this.updatedAt = this.doctorReviewedAt;
    }

    public void markLabVerified(UUID labVerifiedBy, String decision, String reason, String comments) {
        this.status = LabOrderStatus.REPORT_READY;
        this.labVerifiedAt = OffsetDateTime.now();
        this.labVerifiedBy = labVerifiedBy;
        this.labVerificationDecision = decision;
        this.labVerificationReason = reason;
        this.labVerificationComments = comments;
        this.updatedAt = this.labVerifiedAt;
    }

    public void markLabVerificationSentBack(UUID labVerifiedBy, String decision, String reason, String comments) {
        this.status = LabOrderStatus.RESULT_ENTERED;
        this.labVerifiedAt = OffsetDateTime.now();
        this.labVerifiedBy = labVerifiedBy;
        this.labVerificationDecision = decision;
        this.labVerificationReason = reason;
        this.labVerificationComments = comments;
        this.updatedAt = this.labVerifiedAt;
    }

    public void markResultReturned(UUID doctorReviewedByUserId, String doctorReviewedBy, String doctorReviewDecision, String doctorReviewReason, String doctorComments) {
        this.status = LabOrderStatus.RESULT_ENTERED;
        this.doctorReviewedAt = OffsetDateTime.now();
        this.doctorReviewedByUserId = doctorReviewedByUserId;
        this.doctorReviewedBy = doctorReviewedBy;
        this.doctorReviewDecision = doctorReviewDecision;
        this.doctorReviewReason = doctorReviewReason;
        this.doctorComments = doctorComments;
        this.updatedAt = this.doctorReviewedAt;
    }

    public void markDelivered(UUID deliveredByUserId) {
        this.status = LabOrderStatus.DELIVERED;
        this.deliveredAt = OffsetDateTime.now();
        this.deliveredByUserId = deliveredByUserId;
        this.reportDeliveryStatus = "DELIVERED";
        this.updatedAt = this.deliveredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getOrderNumber() { return orderNumber; }
    public UUID getPatientId() { return patientId; }
    public String getPatientNumber() { return patientNumber; }
    public String getPatientName() { return patientName; }
    public UUID getDoctorUserId() { return doctorUserId; }
    public String getDoctorName() { return doctorName; }
    public UUID getConsultationId() { return consultationId; }
    public LabOrderOrigin getOrderOrigin() { return orderOrigin; }
    public UUID getRequestedByInternalDoctorId() { return requestedByInternalDoctorId; }
    public String getExternalDoctorName() { return externalDoctorName; }
    public String getExternalDoctorMobile() { return externalDoctorMobile; }
    public String getExternalClinicName() { return externalClinicName; }
    public String getReferralSource() { return referralSource; }
    public String getNotes() { return notes; }
    public LabOrderStatus getStatus() { return status; }
    public OffsetDateTime getOrderedAt() { return orderedAt; }
    public UUID getBillId() { return billId; }
    public String getExternalLabVendor() { return externalLabVendor; }
    public String getExternalReferenceNumber() { return externalReferenceNumber; }
    public OffsetDateTime getPaymentCollectedAt() { return paymentCollectedAt; }
    public OffsetDateTime getReadyForCollectionAt() { return readyForCollectionAt; }
    public String getSampleType() { return sampleType; }
    public OffsetDateTime getSampleCollectedAt() { return sampleCollectedAt; }
    public UUID getSampleCollectedByUserId() { return sampleCollectedByUserId; }
    public String getSampleCollectedBy() { return sampleCollectedBy; }
    public String getSampleCollectionNotes() { return sampleCollectionNotes; }
    public OffsetDateTime getProcessingStartedAt() { return processingStartedAt; }
    public OffsetDateTime getResultEnteredAt() { return resultEnteredAt; }
    public String getResultComments() { return resultComments; }
    public OffsetDateTime getReportGeneratedAt() { return reportGeneratedAt; }
    public UUID getReportGeneratedByUserId() { return reportGeneratedByUserId; }
    public String getReportFilename() { return reportFilename; }
    public OffsetDateTime getReportPublishedAt() { return reportPublishedAt; }
    public UUID getReportPublishedByUserId() { return reportPublishedByUserId; }
    public String getReportDeliveryStatus() { return reportDeliveryStatus; }
    public String getReportDeliveryChannels() { return reportDeliveryChannels; }
    public String getReportDeliveryNotes() { return reportDeliveryNotes; }
    public OffsetDateTime getDoctorReviewedAt() { return doctorReviewedAt; }
    public UUID getDoctorReviewedByUserId() { return doctorReviewedByUserId; }
    public String getDoctorReviewedBy() { return doctorReviewedBy; }
    public String getDoctorReviewDecision() { return doctorReviewDecision; }
    public String getDoctorReviewReason() { return doctorReviewReason; }
    public String getDoctorComments() { return doctorComments; }
    public OffsetDateTime getLabVerifiedAt() { return labVerifiedAt; }
    public UUID getLabVerifiedBy() { return labVerifiedBy; }
    public String getLabVerificationDecision() { return labVerificationDecision; }
    public String getLabVerificationComments() { return labVerificationComments; }
    public String getLabVerificationReason() { return labVerificationReason; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public UUID getDeliveredByUserId() { return deliveredByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
