package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_sale_returns",
        indexes = {
                @Index(name = "ix_pharmacy_sale_returns_tenant_sale", columnList = "tenant_id,sale_id,created_at"),
                @Index(name = "ix_pharmacy_sale_returns_tenant_number", columnList = "tenant_id,return_number")
        }
)
public class PharmacySaleReturnEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "sale_item_id", nullable = false)
    private UUID saleItemId;

    @Column(name = "return_number", nullable = false, length = 64)
    private String returnNumber;

    @Column(name = "medicine_id", nullable = false)
    private UUID medicineId;

    @Column(name = "stock_batch_id")
    private UUID stockBatchId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "refund_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false)
    private boolean reusable;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "refund_mode", length = 32)
    private String refundMode;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PharmacySaleReturnEntity() {
    }

    public static PharmacySaleReturnEntity create(
            UUID tenantId,
            UUID saleId,
            UUID saleItemId,
            String returnNumber,
            UUID medicineId,
            UUID stockBatchId,
            int quantity,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal refundAmount,
            boolean reusable,
            String reason,
            String refundMode,
            String referenceNumber,
            String notes,
            UUID createdBy
    ) {
        PharmacySaleReturnEntity entity = new PharmacySaleReturnEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.saleId = saleId;
        entity.saleItemId = saleItemId;
        entity.returnNumber = returnNumber;
        entity.medicineId = medicineId;
        entity.stockBatchId = stockBatchId;
        entity.quantity = quantity;
        entity.grossAmount = grossAmount;
        entity.discountAmount = discountAmount;
        entity.taxAmount = taxAmount;
        entity.refundAmount = refundAmount;
        entity.reusable = reusable;
        entity.reason = reason;
        entity.refundMode = refundMode;
        entity.referenceNumber = referenceNumber;
        entity.notes = notes;
        entity.createdBy = createdBy;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSaleId() { return saleId; }
    public UUID getSaleItemId() { return saleItemId; }
    public String getReturnNumber() { return returnNumber; }
    public UUID getMedicineId() { return medicineId; }
    public UUID getStockBatchId() { return stockBatchId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public boolean isReusable() { return reusable; }
    public String getReason() { return reason; }
    public String getRefundMode() { return refundMode; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
