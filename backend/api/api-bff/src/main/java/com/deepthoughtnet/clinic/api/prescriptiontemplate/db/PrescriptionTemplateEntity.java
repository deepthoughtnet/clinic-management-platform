package com.deepthoughtnet.clinic.api.prescriptiontemplate.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "prescription_template_configs", indexes = {
        @Index(name = "ix_prescription_template_configs_tenant", columnList = "tenant_id,active")
})
public class PrescriptionTemplateEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "clinic_logo_document_id")
    private UUID clinicLogoDocumentId;

    @Column(name = "header_text", columnDefinition = "text")
    private String headerText;

    @Column(name = "footer_text", columnDefinition = "text")
    private String footerText;

    @Column(name = "primary_color", length = 16)
    private String primaryColor;

    @Column(name = "accent_color", length = 16)
    private String accentColor;

    @Column(columnDefinition = "text")
    private String disclaimer;

    @Column(name = "doctor_signature_text", columnDefinition = "text")
    private String doctorSignatureText;

    @Column(name = "show_qr_code", nullable = false)
    private boolean showQrCode;

    @Column(name = "watermark_text", length = 255)
    private String watermarkText;

    @Column(name = "changed_by_app_user_id", nullable = false)
    private UUID changedByAppUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected PrescriptionTemplateEntity() {}

    public static PrescriptionTemplateEntity create(UUID tenantId, int templateVersion, UUID changedByAppUserId, PrescriptionTemplateSettings settings) {
        OffsetDateTime now = OffsetDateTime.now();
        PrescriptionTemplateEntity entity = new PrescriptionTemplateEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.templateVersion = templateVersion;
        entity.active = true;
        entity.changedByAppUserId = changedByAppUserId;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.apply(settings);
        return entity;
    }

    public void deactivate() {
        active = false;
        updatedAt = OffsetDateTime.now();
    }

    private void apply(PrescriptionTemplateSettings settings) {
        clinicLogoDocumentId = settings.clinicLogoDocumentId();
        headerText = settings.headerText();
        footerText = settings.footerText();
        primaryColor = settings.primaryColor();
        accentColor = settings.accentColor();
        disclaimer = settings.disclaimer();
        doctorSignatureText = settings.doctorSignatureText();
        showQrCode = settings.showQrCode();
        watermarkText = settings.watermarkText();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public int getTemplateVersion() { return templateVersion; }
    public boolean isActive() { return active; }
    public UUID getClinicLogoDocumentId() { return clinicLogoDocumentId; }
    public String getHeaderText() { return headerText; }
    public String getFooterText() { return footerText; }
    public String getPrimaryColor() { return primaryColor; }
    public String getAccentColor() { return accentColor; }
    public String getDisclaimer() { return disclaimer; }
    public String getDoctorSignatureText() { return doctorSignatureText; }
    public boolean isShowQrCode() { return showQrCode; }
    public String getWatermarkText() { return watermarkText; }
    public UUID getChangedByAppUserId() { return changedByAppUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
