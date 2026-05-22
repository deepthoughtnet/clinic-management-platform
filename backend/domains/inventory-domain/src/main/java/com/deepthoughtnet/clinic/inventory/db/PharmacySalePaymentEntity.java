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
        name = "pharmacy_sale_payments",
        indexes = {
                @Index(name = "ix_pharmacy_sale_payments_tenant_sale", columnList = "tenant_id,sale_id,created_at")
        }
)
public class PharmacySalePaymentEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "payment_date_time")
    private OffsetDateTime paymentDateTime;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_mode", nullable = false, length = 32)
    private String paymentMode;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(name = "receipt_number", nullable = false, length = 64)
    private String receiptNumber;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PharmacySalePaymentEntity() {
    }

    public static PharmacySalePaymentEntity create(
            UUID tenantId,
            UUID saleId,
            LocalDate paymentDate,
            OffsetDateTime paymentDateTime,
            BigDecimal amount,
            String paymentMode,
            String referenceNumber,
            String receiptNumber,
            String notes,
            UUID receivedBy
    ) {
        PharmacySalePaymentEntity entity = new PharmacySalePaymentEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.saleId = saleId;
        entity.paymentDate = paymentDate;
        entity.paymentDateTime = paymentDateTime;
        entity.amount = amount;
        entity.paymentMode = paymentMode;
        entity.referenceNumber = referenceNumber;
        entity.receiptNumber = receiptNumber;
        entity.notes = notes;
        entity.receivedBy = receivedBy;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSaleId() { return saleId; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public OffsetDateTime getPaymentDateTime() { return paymentDateTime; }
    public BigDecimal getAmount() { return amount; }
    public String getPaymentMode() { return paymentMode; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getNotes() { return notes; }
    public UUID getReceivedBy() { return receivedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
