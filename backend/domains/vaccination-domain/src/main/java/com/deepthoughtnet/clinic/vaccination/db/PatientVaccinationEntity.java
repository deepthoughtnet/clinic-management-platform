package com.deepthoughtnet.clinic.vaccination.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "patient_vaccinations",
        indexes = {
                @Index(name = "ix_patient_vaccinations_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_patient_vaccinations_tenant_due", columnList = "tenant_id,next_due_date")
        }
)
public class PatientVaccinationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "vaccine_id")
    private UUID vaccineId;

    @Column(name = "vaccine_name_snapshot", nullable = false, length = 256)
    private String vaccineNameSnapshot;

    @Column(name = "source", nullable = false, length = 16)
    private String source = "INTERNAL";

    @Column(name = "external_place", length = 256)
    private String externalPlace;

    @Column(name = "proof_document_id")
    private UUID proofDocumentId;

    @Column(name = "verified_status", nullable = false, length = 32)
    private String verifiedStatus = "VERIFIED";

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(name = "given_date", nullable = false)
    private LocalDate givenDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "batch_number", length = 128)
    private String batchNumber;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "administered_by_user_id")
    private UUID administeredByUserId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "bill_line_id")
    private UUID billLineId;

    @Column(name = "bill_number_snapshot", length = 64)
    private String billNumberSnapshot;

    @Column(name = "bill_status_snapshot", length = 32)
    private String billStatusSnapshot;

    @Column(name = "inventory_transaction_id")
    private UUID inventoryTransactionId;

    @Column(name = "inventory_stock_batch_id")
    private UUID inventoryStockBatchId;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "inventory_item_code", length = 128)
    private String inventoryItemCode;

    @Column(name = "inventory_batch_number_snapshot", length = 128)
    private String inventoryBatchNumberSnapshot;

    @Column(name = "inventory_batch_manufacturer_snapshot", length = 256)
    private String inventoryBatchManufacturerSnapshot;

    @Column(name = "inventory_batch_expiry_date")
    private LocalDate inventoryBatchExpiryDate;

    @Column(name = "reminder_notification_id")
    private UUID reminderNotificationId;

    @Column(name = "reminder_queued_at")
    private OffsetDateTime reminderQueuedAt;

    @Column(name = "reminder_status", length = 32)
    private String reminderStatus;

    @Column(name = "aefi_status", length = 32)
    private String aefiStatus;

    @Column(name = "aefi_event_datetime")
    private OffsetDateTime aefiEventDateTime;

    @Column(name = "aefi_onset_time_after_vaccination", length = 128)
    private String aefiOnsetTimeAfterVaccination;

    @Column(name = "aefi_severity", length = 32)
    private String aefiSeverity;

    @Column(name = "aefi_symptoms", columnDefinition = "text")
    private String aefiSymptoms;

    @Column(name = "aefi_other_symptoms", columnDefinition = "text")
    private String aefiOtherSymptoms;

    @Column(name = "aefi_action_taken", length = 128)
    private String aefiActionTaken;

    @Column(name = "aefi_treatment_notes", columnDefinition = "text")
    private String aefiTreatmentNotes;

    @Column(name = "aefi_outcome", length = 64)
    private String aefiOutcome;

    @Column(name = "aefi_follow_up_required")
    private Boolean aefiFollowUpRequired;

    @Column(name = "aefi_follow_up_date")
    private LocalDate aefiFollowUpDate;

    @Column(name = "aefi_reported_to_authority")
    private Boolean aefiReportedToAuthority;

    @Column(name = "aefi_report_reference_number", length = 128)
    private String aefiReportReferenceNumber;

    @Column(name = "aefi_notes", columnDefinition = "text")
    private String aefiNotes;

    @Column(name = "aefi_follow_up_notification_id")
    private UUID aefiFollowUpNotificationId;

    @Column(name = "aefi_follow_up_queued_at")
    private OffsetDateTime aefiFollowUpQueuedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected PatientVaccinationEntity() {
    }

    public static PatientVaccinationEntity create(
            UUID tenantId,
            UUID patientId,
            UUID vaccineId,
            String vaccineNameSnapshot,
            String source,
            String externalPlace,
            UUID proofDocumentId,
            String verifiedStatus,
            UUID verifiedByUserId,
            OffsetDateTime verifiedAt,
            Integer doseNumber,
            LocalDate givenDate,
            LocalDate nextDueDate,
            String batchNumber,
            String notes,
            UUID inventoryItemId,
            String inventoryItemCode,
            UUID administeredByUserId,
            UUID createdByUserId
    ) {
        PatientVaccinationEntity entity = new PatientVaccinationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.vaccineId = vaccineId;
        entity.vaccineNameSnapshot = vaccineNameSnapshot;
        entity.source = source == null || source.isBlank() ? "INTERNAL" : source;
        entity.externalPlace = externalPlace;
        entity.proofDocumentId = proofDocumentId;
        entity.verifiedStatus = verifiedStatus == null || verifiedStatus.isBlank() ? ("EXTERNAL".equalsIgnoreCase(entity.source) ? "UNVERIFIED" : "VERIFIED") : verifiedStatus;
        entity.verifiedByUserId = verifiedByUserId;
        entity.verifiedAt = verifiedAt;
        entity.doseNumber = doseNumber;
        entity.givenDate = givenDate == null ? LocalDate.now() : givenDate;
        entity.nextDueDate = nextDueDate;
        entity.batchNumber = batchNumber;
        entity.notes = notes;
        entity.inventoryItemId = inventoryItemId;
        entity.inventoryItemCode = inventoryItemCode;
        entity.administeredByUserId = administeredByUserId;
        entity.createdByUserId = createdByUserId;
        entity.updatedByUserId = createdByUserId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getVaccineId() { return vaccineId; }
    public String getVaccineNameSnapshot() { return vaccineNameSnapshot; }
    public String getSource() { return source; }
    public String getExternalPlace() { return externalPlace; }
    public UUID getProofDocumentId() { return proofDocumentId; }
    public String getVerifiedStatus() { return verifiedStatus; }
    public UUID getVerifiedByUserId() { return verifiedByUserId; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public Integer getDoseNumber() { return doseNumber; }
    public LocalDate getGivenDate() { return givenDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public String getBatchNumber() { return batchNumber; }
    public String getNotes() { return notes; }
    public UUID getAdministeredByUserId() { return administeredByUserId; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getBillId() { return billId; }
    public UUID getBillLineId() { return billLineId; }
    public String getBillNumberSnapshot() { return billNumberSnapshot; }
    public String getBillStatusSnapshot() { return billStatusSnapshot; }
    public UUID getInventoryTransactionId() { return inventoryTransactionId; }
    public UUID getInventoryStockBatchId() { return inventoryStockBatchId; }
    public UUID getInventoryItemId() { return inventoryItemId; }
    public String getInventoryItemCode() { return inventoryItemCode; }
    public String getInventoryBatchNumberSnapshot() { return inventoryBatchNumberSnapshot; }
    public String getInventoryBatchManufacturerSnapshot() { return inventoryBatchManufacturerSnapshot; }
    public LocalDate getInventoryBatchExpiryDate() { return inventoryBatchExpiryDate; }
    public UUID getReminderNotificationId() { return reminderNotificationId; }
    public OffsetDateTime getReminderQueuedAt() { return reminderQueuedAt; }
    public String getReminderStatus() { return reminderStatus; }
    public String getAefiStatus() { return aefiStatus; }
    public OffsetDateTime getAefiEventDateTime() { return aefiEventDateTime; }
    public String getAefiOnsetTimeAfterVaccination() { return aefiOnsetTimeAfterVaccination; }
    public String getAefiSeverity() { return aefiSeverity; }
    public String getAefiSymptoms() { return aefiSymptoms; }
    public String getAefiOtherSymptoms() { return aefiOtherSymptoms; }
    public String getAefiActionTaken() { return aefiActionTaken; }
    public String getAefiTreatmentNotes() { return aefiTreatmentNotes; }
    public String getAefiOutcome() { return aefiOutcome; }
    public Boolean getAefiFollowUpRequired() { return aefiFollowUpRequired; }
    public LocalDate getAefiFollowUpDate() { return aefiFollowUpDate; }
    public Boolean getAefiReportedToAuthority() { return aefiReportedToAuthority; }
    public String getAefiReportReferenceNumber() { return aefiReportReferenceNumber; }
    public String getAefiNotes() { return aefiNotes; }
    public UUID getAefiFollowUpNotificationId() { return aefiFollowUpNotificationId; }
    public OffsetDateTime getAefiFollowUpQueuedAt() { return aefiFollowUpQueuedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void linkBill(UUID billId, UUID billLineId, String billNumberSnapshot, String billStatusSnapshot, UUID updatedByUserId) {
        this.billId = billId;
        this.billLineId = billLineId;
        this.billNumberSnapshot = billNumberSnapshot;
        this.billStatusSnapshot = billStatusSnapshot;
        touch(updatedByUserId);
    }

    public void linkInventory(
            UUID inventoryTransactionId,
            UUID inventoryStockBatchId,
            String inventoryBatchNumberSnapshot,
            String inventoryBatchManufacturerSnapshot,
            LocalDate inventoryBatchExpiryDate,
            UUID updatedByUserId
    ) {
        this.inventoryTransactionId = inventoryTransactionId;
        this.inventoryStockBatchId = inventoryStockBatchId;
        this.inventoryBatchNumberSnapshot = inventoryBatchNumberSnapshot;
        this.inventoryBatchManufacturerSnapshot = inventoryBatchManufacturerSnapshot;
        this.inventoryBatchExpiryDate = inventoryBatchExpiryDate;
        touch(updatedByUserId);
    }

    public void linkReminder(UUID reminderNotificationId, String reminderStatus, OffsetDateTime reminderQueuedAt, UUID updatedByUserId) {
        this.reminderNotificationId = reminderNotificationId;
        this.reminderStatus = reminderStatus;
        this.reminderQueuedAt = reminderQueuedAt;
        touch(updatedByUserId);
    }

    public void updateAefi(
            String aefiStatus,
            OffsetDateTime aefiEventDateTime,
            String aefiOnsetTimeAfterVaccination,
            String aefiSeverity,
            String aefiSymptoms,
            String aefiOtherSymptoms,
            String aefiActionTaken,
            String aefiTreatmentNotes,
            String aefiOutcome,
            Boolean aefiFollowUpRequired,
            LocalDate aefiFollowUpDate,
            Boolean aefiReportedToAuthority,
            String aefiReportReferenceNumber,
            String aefiNotes,
            UUID aefiFollowUpNotificationId,
            OffsetDateTime aefiFollowUpQueuedAt,
            UUID updatedByUserId
    ) {
        this.aefiStatus = aefiStatus;
        this.aefiEventDateTime = aefiEventDateTime;
        this.aefiOnsetTimeAfterVaccination = aefiOnsetTimeAfterVaccination;
        this.aefiSeverity = aefiSeverity;
        this.aefiSymptoms = aefiSymptoms;
        this.aefiOtherSymptoms = aefiOtherSymptoms;
        this.aefiActionTaken = aefiActionTaken;
        this.aefiTreatmentNotes = aefiTreatmentNotes;
        this.aefiOutcome = aefiOutcome;
        this.aefiFollowUpRequired = aefiFollowUpRequired;
        this.aefiFollowUpDate = aefiFollowUpDate;
        this.aefiReportedToAuthority = aefiReportedToAuthority;
        this.aefiReportReferenceNumber = aefiReportReferenceNumber;
        this.aefiNotes = aefiNotes;
        this.aefiFollowUpNotificationId = aefiFollowUpNotificationId;
        this.aefiFollowUpQueuedAt = aefiFollowUpQueuedAt;
        touch(updatedByUserId);
    }

    public void updateExternalDetails(String externalPlace, UUID proofDocumentId, String verifiedStatus, UUID verifiedByUserId, OffsetDateTime verifiedAt, UUID updatedByUserId) {
        this.externalPlace = externalPlace;
        this.proofDocumentId = proofDocumentId;
        if (verifiedStatus != null && !verifiedStatus.isBlank()) {
            this.verifiedStatus = verifiedStatus;
        }
        this.verifiedByUserId = verifiedByUserId;
        this.verifiedAt = verifiedAt;
        touch(updatedByUserId);
    }

    private void touch(UUID updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = OffsetDateTime.now();
    }
}
