package com.deepthoughtnet.clinic.api.lab.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderOrigin;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleStatusRecord;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

public record LabOrderResponse(
        String id,
        String tenantId,
        String orderNumber,
        String patientId,
        String patientNumber,
        String patientName,
        String doctorUserId,
        String doctorName,
        String consultationId,
        LabOrderOrigin orderOrigin,
        String requestedByInternalDoctorId,
        String externalDoctorName,
        String externalDoctorMobile,
        String externalClinicName,
        String referralSource,
        String notes,
        LabOrderStatusRecord status,
        OffsetDateTime orderedAt,
        String billId,
        String billNumber,
        BillStatus billStatus,
        BigDecimal billTotalAmount,
        BigDecimal billDueAmount,
        String externalLabVendor,
        String externalReferenceNumber,
        OffsetDateTime deliveredAt,
        String deliveredByUserId,
        OffsetDateTime paymentCollectedAt,
        OffsetDateTime readyForCollectionAt,
        String sampleAccessionNumber,
        String sampleBarcodeValue,
        LabSampleStatusRecord sampleSummaryStatus,
        String sampleType,
        OffsetDateTime sampleCollectedAt,
        String sampleCollectedByUserId,
        String sampleCollectedBy,
        String sampleCollectionNotes,
        OffsetDateTime processingStartedAt,
        OffsetDateTime resultEnteredAt,
        String resultEnteredByUserId,
        String resultEnteredBy,
        String resultComments,
        OffsetDateTime reportGeneratedAt,
        String reportGeneratedByUserId,
        String reportGeneratedBy,
        String reportFilename,
        OffsetDateTime reportPublishedAt,
        String reportPublishedByUserId,
        String reportVerificationToken,
        String reportDeliveryStatus,
        List<String> reportDeliveryChannels,
        String reportDeliveryNotes,
        List<ReportDeliveryAuditResponse> reportDeliveryHistory,
        List<ReportArtifactResponse> reportArtifacts,
        OffsetDateTime doctorReviewedAt,
        String doctorReviewedByUserId,
        String doctorReviewedBy,
        String doctorReviewDecision,
        String doctorReviewReason,
        String doctorComments,
        OffsetDateTime labVerifiedAt,
        String labVerifiedBy,
        String labVerifiedByName,
        String labVerificationDecision,
        String labVerificationComments,
        String labVerificationReason,
        List<LabOrderAttachmentResponse> attachments,
        List<LabOrderItemResponse> items,
        List<OrderedTestResponse> orderedTests,
        List<LabSampleResponse> samples,
        List<LabOrderResultResponse> results,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String paymentId,
        String receiptId,
        String receiptNumber,
        LocalDate receiptDate,
        LocalDate paymentDate,
        OffsetDateTime paymentDateTime,
        BigDecimal paymentAmount,
        PaymentMode paymentMode,
        String referenceNumber,
        String receivedBy,
        PaymentReceiptResponse paymentReceipt
) {
    public record PaymentReceiptResponse(
            String receiptId,
            String receiptNumber,
            String billId,
            String billNumber,
            BigDecimal amount,
            PaymentMode paymentMode,
            String referenceNumber,
            String collectedBy,
            OffsetDateTime collectedAt,
            String printUrl,
            String downloadUrl
    ) {
    }

    public record ReportDeliveryAuditResponse(
            String action,
            String label,
            String channel,
            OffsetDateTime occurredAt,
            String actorAppUserId,
            String summary
    ) {
    }

    public record ReportArtifactResponse(
            String id,
            int versionNumber,
            String reportMode,
            String reportType,
            String reportStatus,
            String filename,
            String storageReference,
            String verificationToken,
            String verificationUrl,
            List<String> deliveryChannels,
            List<String> selectedOrderedTestIds,
            OffsetDateTime generatedAt,
            String generatedByUserId,
            OffsetDateTime publishedAt,
            String publishedByUserId,
            String supersededByArtifactId,
            OffsetDateTime supersededAt,
            String notes
    ) {
    }

    public record OrderedTestSpecimenResponse(
            String labOrderSampleId,
            String accessionNumber,
            String barcodeValue,
            String specimenType,
            String containerType,
            String sampleStatus,
            boolean active,
            OffsetDateTime collectedAt,
            OffsetDateTime receivedAt,
            OffsetDateTime linkedAt,
            OffsetDateTime unlinkedAt
    ) {
    }

    public record OrderedTestResponse(
            String labOrderItemId,
            String labTestId,
            String testCode,
            String testName,
            String category,
            String department,
            String sampleType,
            String unit,
            String referenceRange,
            String turnaroundTime,
            BigDecimal price,
            int sortOrder,
            String state,
            int latestResultRevision,
            String latestVerificationDecision,
            OffsetDateTime latestVerificationAt,
            String latestVerificationBy,
            Integer latestPublicationArtifactNumber,
            OffsetDateTime latestPublicationAt,
            String latestPublicationBy,
            List<String> publicationChannels,
            String latestResultSnapshotJson,
            OffsetDateTime latestResultEnteredAt,
            String latestResultEnteredBy,
            List<OrderedTestSpecimenResponse> specimenLinks
    ) {
    }

    public record LabOrderAttachmentResponse(
            String id,
            String labOrderId,
            String attachmentType,
            String originalFilename,
            String mediaType,
            String storageKey,
            Long sizeBytes,
            String checksumSha256,
            String dicomMetadataJson,
            String uploadedByUserId,
            OffsetDateTime createdAt
    ) {
    }
}
