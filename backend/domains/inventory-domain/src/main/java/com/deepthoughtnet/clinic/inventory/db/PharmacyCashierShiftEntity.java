package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_cashier_shifts",
        indexes = {
                @Index(name = "ix_pharmacy_cashier_shifts_tenant", columnList = "tenant_id"),
                @Index(name = "ix_pharmacy_cashier_shifts_tenant_cashier_status", columnList = "tenant_id,cashier_user_id,status"),
                @Index(name = "ix_pharmacy_cashier_shifts_tenant_opened", columnList = "tenant_id,opened_at")
        }
)
public class PharmacyCashierShiftEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cashier_user_id", nullable = false)
    private UUID cashierUserId;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;

    @Column(name = "opening_cash_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal openingCashAmount;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "expected_cash_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedCashAmount;

    @Column(name = "expected_upi_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedUpiAmount;

    @Column(name = "expected_card_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedCardAmount;

    @Column(name = "expected_other_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedOtherAmount;

    @Column(name = "actual_cash_amount", precision = 18, scale = 2)
    private BigDecimal actualCashAmount;

    @Column(name = "actual_upi_amount", precision = 18, scale = 2)
    private BigDecimal actualUpiAmount;

    @Column(name = "actual_card_amount", precision = 18, scale = 2)
    private BigDecimal actualCardAmount;

    @Column(name = "actual_other_amount", precision = 18, scale = 2)
    private BigDecimal actualOtherAmount;

    @Column(name = "variance_amount", precision = 18, scale = 2)
    private BigDecimal varianceAmount;

    @Column(name = "open_notes", columnDefinition = "text")
    private String openNotes;

    @Column(name = "close_notes", columnDefinition = "text")
    private String closeNotes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected PharmacyCashierShiftEntity() {
    }

    public static PharmacyCashierShiftEntity open(
            UUID tenantId,
            UUID cashierUserId,
            UUID openedBy,
            BigDecimal openingCashAmount,
            String openNotes
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        PharmacyCashierShiftEntity entity = new PharmacyCashierShiftEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.cashierUserId = cashierUserId;
        entity.openedAt = now;
        entity.openedBy = openedBy;
        entity.openingCashAmount = openingCashAmount;
        entity.closedAt = null;
        entity.closedBy = null;
        entity.status = "OPEN";
        entity.expectedCashAmount = BigDecimal.ZERO.setScale(2);
        entity.expectedUpiAmount = BigDecimal.ZERO.setScale(2);
        entity.expectedCardAmount = BigDecimal.ZERO.setScale(2);
        entity.expectedOtherAmount = BigDecimal.ZERO.setScale(2);
        entity.actualCashAmount = null;
        entity.actualUpiAmount = null;
        entity.actualCardAmount = null;
        entity.actualOtherAmount = null;
        entity.varianceAmount = null;
        entity.openNotes = openNotes;
        entity.closeNotes = null;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void close(
            UUID closedBy,
            BigDecimal expectedCashAmount,
            BigDecimal expectedUpiAmount,
            BigDecimal expectedCardAmount,
            BigDecimal expectedOtherAmount,
            BigDecimal actualCashAmount,
            BigDecimal actualUpiAmount,
            BigDecimal actualCardAmount,
            BigDecimal actualOtherAmount,
            BigDecimal varianceAmount,
            String closeNotes
    ) {
        this.closedAt = OffsetDateTime.now();
        this.closedBy = closedBy;
        this.status = "CLOSED";
        this.expectedCashAmount = expectedCashAmount;
        this.expectedUpiAmount = expectedUpiAmount;
        this.expectedCardAmount = expectedCardAmount;
        this.expectedOtherAmount = expectedOtherAmount;
        this.actualCashAmount = actualCashAmount;
        this.actualUpiAmount = actualUpiAmount;
        this.actualCardAmount = actualCardAmount;
        this.actualOtherAmount = actualOtherAmount;
        this.varianceAmount = varianceAmount;
        this.closeNotes = closeNotes;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCashierUserId() { return cashierUserId; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public UUID getOpenedBy() { return openedBy; }
    public BigDecimal getOpeningCashAmount() { return openingCashAmount; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public UUID getClosedBy() { return closedBy; }
    public String getStatus() { return status; }
    public BigDecimal getExpectedCashAmount() { return expectedCashAmount; }
    public BigDecimal getExpectedUpiAmount() { return expectedUpiAmount; }
    public BigDecimal getExpectedCardAmount() { return expectedCardAmount; }
    public BigDecimal getExpectedOtherAmount() { return expectedOtherAmount; }
    public BigDecimal getActualCashAmount() { return actualCashAmount; }
    public BigDecimal getActualUpiAmount() { return actualUpiAmount; }
    public BigDecimal getActualCardAmount() { return actualCardAmount; }
    public BigDecimal getActualOtherAmount() { return actualOtherAmount; }
    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public String getOpenNotes() { return openNotes; }
    public String getCloseNotes() { return closeNotes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
