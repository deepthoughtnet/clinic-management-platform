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
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "bill_refunds",
        indexes = {
                @Index(name = "ix_bill_refunds_tenant_bill", columnList = "tenant_id,bill_id"),
                @Index(name = "ix_bill_refunds_tenant_payment", columnList = "tenant_id,payment_id")
        }
)
public class BillRefundEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_mode", length = 24)
    private PaymentMode refundMode;

    @Column(name = "refunded_by")
    private UUID refundedBy;

    @Column(name = "refunded_at", nullable = false)
    private OffsetDateTime refundedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected BillRefundEntity() {}

    public static BillRefundEntity create(
            UUID tenantId,
            UUID billId,
            UUID paymentId,
            BigDecimal amount,
            String reason,
            PaymentMode refundMode,
            UUID refundedBy,
            OffsetDateTime refundedAt,
            String notes
    ) {
        BillRefundEntity entity = new BillRefundEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.billId = billId;
        entity.paymentId = paymentId;
        entity.amount = amount;
        entity.reason = reason;
        entity.refundMode = refundMode;
        entity.refundedBy = refundedBy;
        entity.refundedAt = refundedAt == null ? OffsetDateTime.now() : refundedAt;
        entity.notes = notes;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBillId() { return billId; }
    public UUID getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public PaymentMode getRefundMode() { return refundMode; }
    public UUID getRefundedBy() { return refundedBy; }
    public OffsetDateTime getRefundedAt() { return refundedAt; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
