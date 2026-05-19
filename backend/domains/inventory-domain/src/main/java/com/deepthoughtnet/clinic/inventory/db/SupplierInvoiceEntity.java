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

    @Column(name = "tax_amount", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "items_json", nullable = false, columnDefinition = "text")
    private String itemsJson;

    @Column(name = "matching_status", nullable = false, length = 24)
    private String matchingStatus;

    @Column(name = "variance_summary", columnDefinition = "text")
    private String varianceSummary;

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

    public static SupplierInvoiceEntity create(UUID tenantId, UUID supplierId, UUID purchaseOrderId, String invoiceNumber, LocalDate invoiceDate, BigDecimal taxAmount, BigDecimal totalAmount, String itemsJson, UUID createdBy) {
        OffsetDateTime now = OffsetDateTime.now();
        SupplierInvoiceEntity entity = new SupplierInvoiceEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.supplierId = supplierId;
        entity.purchaseOrderId = purchaseOrderId;
        entity.invoiceNumber = invoiceNumber;
        entity.invoiceDate = invoiceDate;
        entity.taxAmount = taxAmount;
        entity.totalAmount = totalAmount;
        entity.itemsJson = itemsJson;
        entity.matchingStatus = "PENDING";
        entity.createdBy = createdBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void review(String matchingStatus, String varianceSummary, String approvalNote) {
        this.matchingStatus = matchingStatus;
        this.varianceSummary = varianceSummary;
        this.approvalNote = approvalNote;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getItemsJson() { return itemsJson; }
    public String getMatchingStatus() { return matchingStatus; }
    public String getVarianceSummary() { return varianceSummary; }
    public String getApprovalNote() { return approvalNote; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
