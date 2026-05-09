package com.deepthoughtnet.clinic.billing.db;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "bills",
        indexes = {
                @Index(name = "ix_bills_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_bills_tenant_status", columnList = "tenant_id,status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_bills_tenant_bill_number", columnNames = {"tenant_id", "bill_number"})
        }
)
public class BillEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bill_number", nullable = false, length = 64)
    private String billNumber;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BillStatus status = BillStatus.DRAFT;

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "due_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal dueAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private int version;

    protected BillEntity() {
    }

    public static BillEntity create(UUID tenantId, String billNumber, UUID patientId, UUID consultationId, UUID appointmentId, LocalDate billDate) {
        BillEntity entity = new BillEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.billNumber = billNumber;
        entity.patientId = patientId;
        entity.consultationId = consultationId;
        entity.appointmentId = appointmentId;
        entity.billDate = billDate == null ? LocalDate.now() : billDate;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(UUID patientId, UUID consultationId, UUID appointmentId, LocalDate billDate, String notes, BigDecimal discountAmount, BigDecimal taxAmount) {
        this.patientId = patientId;
        this.consultationId = consultationId;
        this.appointmentId = appointmentId;
        this.billDate = billDate == null ? LocalDate.now() : billDate;
        this.notes = notes;
        this.discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        this.taxAmount = taxAmount == null ? BigDecimal.ZERO : taxAmount;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setFinancials(BigDecimal subtotalAmount, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal dueAmount) {
        this.subtotalAmount = subtotalAmount == null ? BigDecimal.ZERO : subtotalAmount;
        this.discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        this.taxAmount = taxAmount == null ? BigDecimal.ZERO : taxAmount;
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        this.paidAmount = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        this.dueAmount = dueAmount == null ? BigDecimal.ZERO : dueAmount;
        this.updatedAt = OffsetDateTime.now();
    }

    public void issue() {
        this.status = BillStatus.UNPAID;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markPartiallyPaid() {
        this.status = BillStatus.PARTIALLY_PAID;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markPaid() {
        this.status = BillStatus.PAID;
        this.updatedAt = OffsetDateTime.now();
    }

    public void cancel() {
        this.status = BillStatus.CANCELLED;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getBillNumber() { return billNumber; }
    public UUID getPatientId() { return patientId; }
    public UUID getConsultationId() { return consultationId; }
    public UUID getAppointmentId() { return appointmentId; }
    public LocalDate getBillDate() { return billDate; }
    public BillStatus getStatus() { return status; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getDueAmount() { return dueAmount; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
