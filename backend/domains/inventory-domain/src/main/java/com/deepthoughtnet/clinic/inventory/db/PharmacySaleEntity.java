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
        name = "pharmacy_sales",
        indexes = {
                @Index(name = "ix_pharmacy_sales_tenant_created", columnList = "tenant_id,created_at"),
                @Index(name = "ix_pharmacy_sales_tenant_patient", columnList = "tenant_id,patient_id")
        }
)
public class PharmacySaleEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sale_number", nullable = false, length = 64)
    private String saleNumber;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "customer_name", length = 256)
    private String customerName;

    @Column(name = "customer_mobile", length = 64)
    private String customerMobile;

    @Column(name = "prescription_document_id")
    private UUID prescriptionDocumentId;

    @Column(name = "prescription_file_name", length = 512)
    private String prescriptionFileName;

    @Column(name = "prescription_uploaded_at")
    private OffsetDateTime prescriptionUploadedAt;

    @Column(name = "sale_date_time", nullable = false)
    private OffsetDateTime saleDateTime;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "due_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal dueAmount;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PharmacySaleEntity() {
    }

    public static PharmacySaleEntity create(
            UUID tenantId,
            String saleNumber,
            UUID patientId,
            String customerName,
            String customerMobile,
            OffsetDateTime saleDateTime,
            UUID locationId,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            BigDecimal paidAmount,
            BigDecimal dueAmount,
            String status,
            String notes,
            UUID createdBy
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        PharmacySaleEntity entity = new PharmacySaleEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.saleNumber = saleNumber;
        entity.patientId = patientId;
        entity.customerName = customerName;
        entity.customerMobile = customerMobile;
        entity.prescriptionDocumentId = null;
        entity.prescriptionFileName = null;
        entity.prescriptionUploadedAt = null;
        entity.saleDateTime = saleDateTime;
        entity.locationId = locationId;
        entity.subtotal = subtotal;
        entity.discount = discount;
        entity.tax = tax;
        entity.total = total;
        entity.paidAmount = paidAmount;
        entity.dueAmount = dueAmount;
        entity.status = status;
        entity.notes = notes;
        entity.createdBy = createdBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void updateFinancials(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total, BigDecimal paidAmount, BigDecimal dueAmount, String status) {
        this.subtotal = subtotal;
        this.discount = discount;
        this.tax = tax;
        this.total = total;
        this.paidAmount = paidAmount;
        this.dueAmount = dueAmount;
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public void attachPrescription(UUID prescriptionDocumentId, String prescriptionFileName, OffsetDateTime prescriptionUploadedAt) {
        this.prescriptionDocumentId = prescriptionDocumentId;
        this.prescriptionFileName = prescriptionFileName;
        this.prescriptionUploadedAt = prescriptionUploadedAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSaleNumber() { return saleNumber; }
    public UUID getPatientId() { return patientId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerMobile() { return customerMobile; }
    public UUID getPrescriptionDocumentId() { return prescriptionDocumentId; }
    public String getPrescriptionFileName() { return prescriptionFileName; }
    public OffsetDateTime getPrescriptionUploadedAt() { return prescriptionUploadedAt; }
    public OffsetDateTime getSaleDateTime() { return saleDateTime; }
    public UUID getLocationId() { return locationId; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getDueAmount() { return dueAmount; }
    public String getStatus() { return status; }
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
