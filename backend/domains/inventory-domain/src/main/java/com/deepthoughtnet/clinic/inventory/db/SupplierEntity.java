package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_suppliers",
        indexes = {
                @Index(name = "ix_pharmacy_suppliers_tenant_active", columnList = "tenant_id,active"),
                @Index(name = "ix_pharmacy_suppliers_tenant_name", columnList = "tenant_id,supplier_name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_pharmacy_suppliers_tenant_name", columnNames = {"tenant_id", "supplier_name"})
        }
)
public class SupplierEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "supplier_name", nullable = false, length = 256)
    private String supplierName;

    @Column(name = "contact_person", length = 256)
    private String contactPerson;

    @Column(length = 32)
    private String phone;

    @Column(length = 256)
    private String email;

    @Column(name = "gst_number", length = 64)
    private String gstNumber;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected SupplierEntity() {}

    public static SupplierEntity create(UUID tenantId, String supplierName) {
        OffsetDateTime now = OffsetDateTime.now();
        SupplierEntity entity = new SupplierEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.supplierName = supplierName;
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(String supplierName, String contactPerson, String phone, String email, String gstNumber, String address, String notes, boolean active) {
        this.supplierName = supplierName;
        this.contactPerson = contactPerson;
        this.phone = phone;
        this.email = email;
        this.gstNumber = gstNumber;
        this.address = address;
        this.notes = notes;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSupplierName() { return supplierName; }
    public String getContactPerson() { return contactPerson; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getGstNumber() { return gstNumber; }
    public String getAddress() { return address; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
