package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_supplier_invoices",
        indexes = {
                @Index(name = "ix_pharmacy_supplier_invoices_tenant_supplier", columnList = "tenant_id,supplier_id"),
                @Index(name = "ix_pharmacy_supplier_invoices_tenant_status", columnList = "tenant_id,matching_status")
        }
)
public class SupplierInvoiceEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "invoice_number", nullable = false, length = 128)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "invoice_amount", precision = 18, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(name = "tax_amount", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "items_json", nullable = false, columnDefinition = "text")
    private String itemsJson;

    @Column(name = "matching_status", nullable = false, length = 24)
    private String matchingStatus;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "variance_amount", precision = 18, scale = 2)
    private BigDecimal varianceAmount;

    @Column(name = "variance_reason", columnDefinition = "text")
    private String varianceReason;

    @Column(name = "variance_summary", columnDefinition = "text")
    private String varianceSummary;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Column(name = "attachment_file_name", length = 255)
    private String attachmentFileName;

    @Column(name = "attachment_media_type", length = 128)
    private String attachmentMediaType;

    @Column(name = "attachment_storage_key", length = 512)
    private String attachmentStorageKey;

    @Column(name = "attachment_size_bytes")
    private Long attachmentSizeBytes;

    @Column(name = "approval_note", columnDefinition = "text")
    private String approvalNote;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected SupplierInvoiceEntity() {
    }

    public static SupplierInvoiceEntity create(UUID tenantId, UUID supplierId, UUID purchaseOrderId, String invoiceNumber, LocalDate invoiceDate, BigDecimal invoiceAmount, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal totalAmount, String itemsJson, UUID createdBy) {
        OffsetDateTime now = OffsetDateTime.now();
        SupplierInvoiceEntity entity = new SupplierInvoiceEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.supplierId = supplierId;
        entity.purchaseOrderId = purchaseOrderId;
        entity.invoiceNumber = invoiceNumber;
        entity.invoiceDate = invoiceDate;
        entity.invoiceAmount = invoiceAmount;
        entity.taxAmount = taxAmount;
        entity.discountAmount = discountAmount;
        entity.totalAmount = totalAmount;
        entity.itemsJson = itemsJson;
        entity.matchingStatus = "PENDING";
        entity.status = "DRAFT";
        entity.createdBy = createdBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(UUID supplierId, UUID purchaseOrderId, String invoiceNumber, LocalDate invoiceDate, BigDecimal invoiceAmount, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal totalAmount, String itemsJson, String varianceReason, String approvalNote) {
        this.supplierId = supplierId;
        this.purchaseOrderId = purchaseOrderId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.invoiceAmount = invoiceAmount;
        this.taxAmount = taxAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.itemsJson = itemsJson;
        this.varianceReason = varianceReason;
        this.approvalNote = approvalNote;
        this.cancelReason = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void review(String matchingStatus, BigDecimal varianceAmount, String varianceReason, String varianceSummary, String approvalNote) {
        this.matchingStatus = matchingStatus;
        this.varianceAmount = varianceAmount;
        this.varianceReason = varianceReason;
        this.varianceSummary = varianceSummary;
        this.approvalNote = approvalNote;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markMatched() {
        this.status = "MATCHED";
        this.updatedAt = OffsetDateTime.now();
    }

    public void approveForPayment() {
        this.status = "APPROVED_FOR_PAYMENT";
        this.updatedAt = OffsetDateTime.now();
    }

    public void cancel(String reason) {
        this.status = "CANCELLED";
        this.cancelReason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public void attachDocument(String fileName, String mediaType, String storageKey, Long sizeBytes) {
        this.attachmentFileName = fileName;
        this.attachmentMediaType = mediaType;
        this.attachmentStorageKey = storageKey;
        this.attachmentSizeBytes = sizeBytes;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public BigDecimal getInvoiceAmount() { return invoiceAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getItemsJson() { return itemsJson; }
    public String getMatchingStatus() { return matchingStatus; }
    public String getStatus() { return status; }
    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public String getVarianceReason() { return varianceReason; }
    public String getVarianceSummary() { return varianceSummary; }
    public String getCancelReason() { return cancelReason; }
    public String getAttachmentFileName() { return attachmentFileName; }
    public String getAttachmentMediaType() { return attachmentMediaType; }
    public String getAttachmentStorageKey() { return attachmentStorageKey; }
    public Long getAttachmentSizeBytes() { return attachmentSizeBytes; }
    public String getApprovalNote() { return approvalNote; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
