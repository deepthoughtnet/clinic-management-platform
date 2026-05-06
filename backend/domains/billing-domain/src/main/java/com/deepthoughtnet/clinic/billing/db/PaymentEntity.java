package com.deepthoughtnet.clinic.billing.db;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "bill_payments",
        indexes = {
                @Index(name = "ix_bill_payments_tenant_bill", columnList = "tenant_id,bill_id"),
                @Index(name = "ix_bill_payments_tenant_date", columnList = "tenant_id,payment_date")
        }
)
public class PaymentEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 24)
    private PaymentMode paymentMode;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected PaymentEntity() {
    }

    public static PaymentEntity create(UUID tenantId, UUID billId, LocalDate paymentDate, BigDecimal amount, PaymentMode paymentMode, String referenceNumber, String notes) {
        PaymentEntity entity = new PaymentEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.billId = billId;
        entity.paymentDate = paymentDate == null ? LocalDate.now() : paymentDate;
        entity.amount = amount;
        entity.paymentMode = paymentMode;
        entity.referenceNumber = referenceNumber;
        entity.notes = notes;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBillId() { return billId; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
