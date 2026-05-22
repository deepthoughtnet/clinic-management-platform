package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_sale_items",
        indexes = {
                @Index(name = "ix_pharmacy_sale_items_tenant_sale", columnList = "tenant_id,sale_id"),
                @Index(name = "ix_pharmacy_sale_items_tenant_medicine", columnList = "tenant_id,medicine_id")
        }
)
public class PharmacySaleItemEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "medicine_id", nullable = false)
    private UUID medicineId;

    @Column(name = "stock_batch_id")
    private UUID stockBatchId;

    @Column(name = "batch_number", length = 128)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "returned_quantity", nullable = false)
    private int returnedQuantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal tax;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PharmacySaleItemEntity() {
    }

    public static PharmacySaleItemEntity create(
            UUID tenantId,
            UUID saleId,
            UUID medicineId,
            UUID stockBatchId,
            String batchNumber,
            LocalDate expiryDate,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal lineTotal
    ) {
        PharmacySaleItemEntity entity = new PharmacySaleItemEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.saleId = saleId;
        entity.medicineId = medicineId;
        entity.stockBatchId = stockBatchId;
        entity.batchNumber = batchNumber;
        entity.expiryDate = expiryDate;
        entity.quantity = quantity;
        entity.returnedQuantity = 0;
        entity.unitPrice = unitPrice;
        entity.discount = discount;
        entity.tax = tax;
        entity.lineTotal = lineTotal;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public void addReturnedQuantity(int quantity) {
        this.returnedQuantity += quantity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSaleId() { return saleId; }
    public UUID getMedicineId() { return medicineId; }
    public UUID getStockBatchId() { return stockBatchId; }
    public String getBatchNumber() { return batchNumber; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public int getQuantity() { return quantity; }
    public int getReturnedQuantity() { return returnedQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
