package com.deepthoughtnet.clinic.billing.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "bill_receipts",
        indexes = {
                @Index(name = "ix_bill_receipts_tenant_bill", columnList = "tenant_id,bill_id"),
                @Index(name = "ix_bill_receipts_tenant_payment", columnList = "tenant_id,payment_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_bill_receipts_tenant_receipt_number", columnNames = {"tenant_id", "receipt_number"})
        }
)
public class ReceiptEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "receipt_number", nullable = false, length = 64)
    private String receiptNumber;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ReceiptEntity() {
    }

    public static ReceiptEntity create(UUID tenantId, String receiptNumber, UUID billId, UUID paymentId, LocalDate receiptDate, BigDecimal amount) {
        ReceiptEntity entity = new ReceiptEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.receiptNumber = receiptNumber;
        entity.billId = billId;
        entity.paymentId = paymentId;
        entity.receiptDate = receiptDate == null ? LocalDate.now() : receiptDate;
        entity.amount = amount;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getReceiptNumber() { return receiptNumber; }
    public UUID getBillId() { return billId; }
    public UUID getPaymentId() { return paymentId; }
    public LocalDate getReceiptDate() { return receiptDate; }
    public BigDecimal getAmount() { return amount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
