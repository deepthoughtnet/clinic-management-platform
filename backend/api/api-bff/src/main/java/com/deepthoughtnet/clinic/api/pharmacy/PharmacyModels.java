package com.deepthoughtnet.clinic.api.pharmacy;

import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.LowStockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record SupplierUpsertRequest(
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String gstNumber,
        String address,
        String notes,
        boolean active
) {
}

record InventoryLocationUpsertRequest(
        String locationName,
        String locationCode,
        String locationType,
        boolean defaultLocation,
        boolean active
) {
}

record InventoryLocationRecord(
        UUID id,
        UUID tenantId,
        String locationName,
        String locationCode,
        String locationType,
        boolean defaultLocation,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

record InventoryTransferRequest(
        UUID medicineId,
        UUID stockBatchId,
        UUID fromLocationId,
        UUID toLocationId,
        int quantity,
        String reason
) {
}

record OcrExtractionRowRecord(
        int rowNumber,
        String medicineCode,
        String medicineName,
        String batchNumber,
        Integer physicalQuantity,
        String expiryDate,
        String notes,
        BigDecimal confidence,
        boolean needsReview
) {
}

record StockSheetReviewRequest(
        List<OcrExtractionRowRecord> rows,
        String reviewNote
) {
}

record ProcurementLineRequest(
        UUID medicineId,
        String medicineName,
        int quantity,
        BigDecimal expectedUnitCost,
        BigDecimal unitCost,
        BigDecimal taxPercent,
        String batchNumber,
        String expiryDate,
        BigDecimal sellingPrice
) {
}

record PurchaseOrderRequest(
        UUID supplierId,
        String poNumber,
        String orderDate,
        String expectedDeliveryDate,
        List<ProcurementLineRequest> items,
        String approvalNote
) {
}

record PurchaseOrderRecord(
        UUID id,
        UUID tenantId,
        UUID supplierId,
        String supplierName,
        String poNumber,
        String orderDate,
        String expectedDeliveryDate,
        String itemsJson,
        String matchingStatus,
        String varianceSummary,
        String approvalNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

record SupplierInvoiceRequest(
        UUID supplierId,
        UUID purchaseOrderId,
        String invoiceNumber,
        String invoiceDate,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        List<ProcurementLineRequest> items,
        String approvalNote
) {
}

record SupplierInvoiceRecord(
        UUID id,
        UUID tenantId,
        UUID supplierId,
        String supplierName,
        UUID purchaseOrderId,
        String invoiceNumber,
        String invoiceDate,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String itemsJson,
        String matchingStatus,
        String varianceSummary,
        String approvalNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

record GoodsReceiptRequest(
        UUID supplierId,
        UUID purchaseOrderId,
        UUID supplierInvoiceId,
        String receiptNumber,
        String receivedAt,
        UUID locationId,
        List<ProcurementLineRequest> items,
        String approvalNote
) {
}

record GoodsReceiptRecord(
        UUID id,
        UUID tenantId,
        UUID supplierId,
        String supplierName,
        UUID purchaseOrderId,
        UUID supplierInvoiceId,
        String receiptNumber,
        String receivedAt,
        UUID locationId,
        String locationName,
        String itemsJson,
        String matchingStatus,
        String varianceSummary,
        String approvalNote,
        OffsetDateTime confirmedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

record SupplierRecord(
        UUID id,
        UUID tenantId,
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String gstNumber,
        String address,
        String notes,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

record StockInwardRequest(
        UUID medicineId,
        UUID supplierId,
        UUID locationId,
        String purchaseReferenceNumber,
        String batchNumber,
        String barcode,
        String qrCode,
        String externalCode,
        String expiryDate,
        String purchaseDate,
        int quantity,
        BigDecimal unitCost,
        BigDecimal sellingPrice,
        Integer lowStockThreshold
) {
}

record ReconciliationCreateRequest(
        UUID medicineId,
        UUID stockBatchId,
        UUID supplierId,
        UUID locationId,
        Integer physicalQuantity,
        String reason
) {
}

record ReconciliationConfirmRequest(
        Integer physicalQuantity,
        String reason,
        boolean adjustStock
) {
}

record ReconciliationDecisionRequest(
        String reason
) {
}

record ReconciliationPostRequest(
        Integer physicalQuantity,
        String reason
) {
}

record ReconciliationUploadResponse(
        UUID reconciliationId,
        String fileName,
        String mediaType,
        String storageKey,
        String extractionStatus,
        String extractionProvider,
        BigDecimal extractionConfidence,
        List<OcrExtractionRowRecord> extractedRows
) {
}

record PharmacyReconciliationRecord(
        UUID id,
        UUID tenantId,
        UUID medicineId,
        String medicineName,
        UUID stockBatchId,
        String batchNumber,
        UUID supplierId,
        String supplierName,
        int systemQuantity,
        Integer physicalQuantity,
        Integer varianceQuantity,
        String reason,
        String status,
        UUID createdBy,
        UUID submittedBy,
        OffsetDateTime submittedAt,
        UUID reviewedBy,
        String reviewDecision,
        String reviewReason,
        UUID postedBy,
        OffsetDateTime postedAt,
        UUID adjustedBy,
        UUID sheetDocumentId,
        String sheetFilename,
        String sheetMediaType,
        String sheetStorageKey,
        String extractionStatus,
        String extractionProvider,
        BigDecimal extractionConfidence,
        List<OcrExtractionRowRecord> extractedRows,
        UUID locationId,
        String locationName,
        OffsetDateTime confirmedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime appliedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

record FastMovingMedicineRecord(
        UUID medicineId,
        String medicineName,
        int dispensedQuantity,
        int availableQuantity
) {
}

record PharmacyDashboardResponse(
        int medicinesCount,
        int stockBatchesCount,
        int lowStockCount,
        int expiredCount,
        int nearExpiryCount,
        int pendingDispensingCount,
        int partiallyDispensedCount,
        int todayDispensedCount,
        List<InventoryTransactionRecord> recentStockMovements
) {
}

record PharmacyAnalyticsResponse(
        List<FastMovingMedicineRecord> fastMovingMedicines,
        List<LowStockRecord> lowStockMedicines,
        List<StockRecord> expiryRiskMedicines,
        BigDecimal stockValueEstimate
) {
}

record SubstituteSuggestionRecord(
        UUID medicineId,
        String medicineName,
        String genericName,
        String brandName,
        String dosageForm,
        String strength,
        int availableQuantity,
        String availabilityStatus,
        String expiryStatus,
        String nearestExpiryDate
) {
}
