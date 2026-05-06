package com.deepthoughtnet.clinic.billing.db;

import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "bill_line_items",
        indexes = {
                @Index(name = "ix_bill_line_items_tenant_bill", columnList = "tenant_id,bill_id"),
                @Index(name = "ix_bill_line_items_tenant_sort", columnList = "tenant_id,bill_id,sort_order")
        }
)
public class BillLineEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 24)
    private BillItemType itemType;

    @Column(name = "item_name", nullable = false, length = 256)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected BillLineEntity() {
    }

    public static BillLineEntity create(
            UUID tenantId,
            UUID billId,
            BillItemType itemType,
            String itemName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            UUID referenceId,
            Integer sortOrder
    ) {
        BillLineEntity entity = new BillLineEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.billId = billId;
        entity.itemType = itemType;
        entity.itemName = itemName;
        entity.quantity = quantity;
        entity.unitPrice = unitPrice;
        entity.totalPrice = totalPrice;
        entity.referenceId = referenceId;
        entity.sortOrder = sortOrder == null ? 0 : sortOrder;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBillId() { return billId; }
    public BillItemType getItemType() { return itemType; }
    public String getItemName() { return itemName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public UUID getReferenceId() { return referenceId; }
    public Integer getSortOrder() { return sortOrder; }
}
