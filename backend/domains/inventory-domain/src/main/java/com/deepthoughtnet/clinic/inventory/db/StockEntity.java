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
        name = "inventory_stocks",
        indexes = {
                @Index(name = "ix_inventory_stocks_tenant_medicine", columnList = "tenant_id,medicine_id"),
                @Index(name = "ix_inventory_stocks_tenant_active", columnList = "tenant_id,active")
        }
)
public class StockEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "medicine_id", nullable = false)
    private UUID medicineId;

    @Column(name = "batch_number", length = 128)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "supplier_name", length = 256)
    private String supplierName;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Column(name = "unit_cost", precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "purchase_price", precision = 18, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "selling_price", precision = 18, scale = 2)
    private BigDecimal sellingPrice;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected StockEntity() {
    }

    public static StockEntity create(UUID tenantId, UUID medicineId) {
        OffsetDateTime now = OffsetDateTime.now();
        StockEntity entity = new StockEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.medicineId = medicineId;
        entity.quantityOnHand = 0;
        entity.quantityReceived = 0;
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(
            String batchNumber,
            LocalDate expiryDate,
            LocalDate purchaseDate,
            String supplierName,
            int quantityReceived,
            int quantityOnHand,
            Integer lowStockThreshold,
            BigDecimal unitCost,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            boolean active
    ) {
        this.batchNumber = batchNumber;
        this.expiryDate = expiryDate;
        this.purchaseDate = purchaseDate;
        this.supplierName = supplierName;
        this.quantityReceived = quantityReceived;
        this.quantityOnHand = quantityOnHand;
        this.lowStockThreshold = lowStockThreshold;
        this.unitCost = unitCost;
        this.purchasePrice = purchasePrice;
        this.sellingPrice = sellingPrice;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getMedicineId() { return medicineId; }
    public String getBatchNumber() { return batchNumber; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public String getSupplierName() { return supplierName; }
    public int getQuantityReceived() { return quantityReceived; }
    public int getQuantityOnHand() { return quantityOnHand; }
    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setQuantityOnHand(int quantityOnHand) { this.quantityOnHand = quantityOnHand; this.updatedAt = OffsetDateTime.now(); }
}
