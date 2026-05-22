package com.deepthoughtnet.clinic.api.pharmacy;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record PharmacyPosMedicineResponse(
        UUID medicineId,
        String medicineName,
        String genericName,
        String brandName,
        String barcode,
        String qrCode,
        String externalCode,
        int totalAvailableQuantity,
        BigDecimal defaultUnitPrice,
        BigDecimal taxRate,
        LocalDate earliestExpiryDate
) {
}

record PharmacyPosBatchResponse(
        UUID stockBatchId,
        UUID medicineId,
        String batchNumber,
        LocalDate expiryDate,
        int availableQuantity,
        BigDecimal unitPrice,
        boolean expired,
        String locationName
) {
}

record PharmacyPosSaleLineRequest(
        UUID medicineId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal tax
) {
}

record PharmacyPosCreateSaleRequest(
        UUID patientId,
        String customerName,
        String customerMobile,
        UUID prescriptionDocumentId,
        OffsetDateTime saleDateTime,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal paidAmount,
        PaymentMode paymentMode,
        String paymentReference,
        String paymentNotes,
        String notes,
        List<PharmacyPosSaleLineRequest> items
) {
}

record PharmacyPosPrescriptionUploadResponse(
        UUID documentId,
        String fileName,
        String mediaType,
        long sizeBytes,
        OffsetDateTime uploadedAt
) {
}

record PharmacyPosPrescriptionDownloadUrlResponse(
        String url,
        String expiresInSeconds
) {
}

record PharmacyPosPaymentRequest(
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes,
        LocalDate paymentDate,
        OffsetDateTime paymentDateTime
) {
}

record PharmacyPosReturnLineRequest(
        UUID saleItemId,
        Integer quantity,
        boolean reusable
) {
}

record PharmacyPosReturnRequest(
        String reason,
        PaymentMode refundMode,
        String referenceNumber,
        String notes,
        List<PharmacyPosReturnLineRequest> items
) {
}

record PharmacyPosPaymentResponse(
        UUID id,
        UUID cashierShiftId,
        BigDecimal amount,
        PaymentMode paymentMode,
        String referenceNumber,
        String receiptNumber,
        LocalDate paymentDate,
        OffsetDateTime paymentDateTime,
        OffsetDateTime createdAt
) {
}

record PharmacyPosReturnResponse(
        UUID id,
        String returnNumber,
        UUID saleItemId,
        UUID medicineId,
        UUID stockBatchId,
        int quantity,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal refundAmount,
        boolean reusable,
        String reason,
        PaymentMode refundMode,
        String referenceNumber,
        String notes,
        OffsetDateTime createdAt
) {
}

record PharmacyPosSaleItemResponse(
        UUID id,
        UUID medicineId,
        String medicineName,
        UUID stockBatchId,
        String batchNumber,
        LocalDate expiryDate,
        int quantity,
        int returnedQuantity,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal lineTotal
) {
}

record PharmacyPosSaleResponse(
        UUID id,
        String saleNumber,
        UUID patientId,
        String patientName,
        String customerName,
        String customerMobile,
        UUID prescriptionDocumentId,
        String prescriptionFileName,
        OffsetDateTime prescriptionUploadedAt,
        OffsetDateTime saleDateTime,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal dueAmount,
        String status,
        String notes,
        String fefoExplanation,
        OffsetDateTime createdAt,
        List<PharmacyPosSaleItemResponse> items,
        List<PharmacyPosPaymentResponse> payments,
        List<PharmacyPosReturnResponse> returns
) {
}

record PharmacyPosReceiptPdf(
        String filename,
        byte[] content
) {
}

record PharmacyPosOpenShiftRequest(
        BigDecimal openingCashAmount,
        String notes
) {
}

record PharmacyPosCloseShiftRequest(
        BigDecimal actualCashAmount,
        BigDecimal actualUpiAmount,
        BigDecimal actualCardAmount,
        BigDecimal actualOtherAmount,
        String closeNotes
) {
}

record PharmacyPosShiftResponse(
        UUID id,
        UUID cashierUserId,
        OffsetDateTime openedAt,
        UUID openedBy,
        BigDecimal openingCashAmount,
        OffsetDateTime closedAt,
        UUID closedBy,
        String status,
        BigDecimal expectedCashAmount,
        BigDecimal expectedUpiAmount,
        BigDecimal expectedCardAmount,
        BigDecimal expectedOtherAmount,
        BigDecimal expectedTotalAmount,
        BigDecimal actualCashAmount,
        BigDecimal actualUpiAmount,
        BigDecimal actualCardAmount,
        BigDecimal actualOtherAmount,
        BigDecimal actualTotalAmount,
        BigDecimal varianceAmount,
        String openNotes,
        String closeNotes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
