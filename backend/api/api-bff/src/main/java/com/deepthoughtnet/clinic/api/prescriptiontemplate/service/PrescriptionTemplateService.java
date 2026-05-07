package com.deepthoughtnet.clinic.api.prescriptiontemplate.service;

import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateEntity;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateRepository;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateSettings;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTemplateConfig;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PrescriptionTemplateService {
    private final PrescriptionTemplateRepository repository;
    private final AuditEventPublisher auditEventPublisher;

    public PrescriptionTemplateService(PrescriptionTemplateRepository repository, AuditEventPublisher auditEventPublisher) {
        this.repository = repository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public PrescriptionTemplateRecord getActive(UUID tenantId) {
        return repository.findFirstByTenantIdAndActiveTrueOrderByTemplateVersionDesc(tenantId)
                .map(this::toRecord)
                .orElse(defaultRecord(tenantId));
    }

    @Transactional(readOnly = true)
    public List<PrescriptionTemplateRecord> history(UUID tenantId) {
        return repository.findByTenantIdOrderByTemplateVersionDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional
    public PrescriptionTemplateRecord save(UUID tenantId, UUID actorAppUserId, PrescriptionTemplateSettings settings) {
        repository.findByTenantIdAndActiveTrue(tenantId).forEach(PrescriptionTemplateEntity::deactivate);
        int nextVersion = repository.findByTenantIdOrderByTemplateVersionDesc(tenantId).stream().findFirst().map(row -> row.getTemplateVersion() + 1).orElse(1);
        PrescriptionTemplateEntity saved = repository.save(PrescriptionTemplateEntity.create(tenantId, nextVersion, actorAppUserId, sanitize(settings)));
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.CLINIC,
                tenantId,
                "PRESCRIPTION_TEMPLATE_UPDATED",
                actorAppUserId,
                OffsetDateTime.now(),
                "Prescription template updated",
                "{\"templateVersion\":" + nextVersion + "}"
        ));
        return toRecord(saved);
    }

    public PrescriptionTemplateConfig toPdfConfig(PrescriptionTemplateRecord record) {
        if (record == null) return PrescriptionTemplateConfig.defaults();
        return new PrescriptionTemplateConfig(
                record.clinicLogoDocumentId() == null ? null : record.clinicLogoDocumentId().toString(),
                record.headerText(), record.footerText(), record.primaryColor(), record.accentColor(), record.disclaimer(),
                record.doctorSignatureText(), record.showQrCode(), record.watermarkText()
        );
    }

    private PrescriptionTemplateSettings sanitize(PrescriptionTemplateSettings settings) {
        return new PrescriptionTemplateSettings(
                settings.clinicLogoDocumentId(), clean(settings.headerText()), clean(settings.footerText()), color(settings.primaryColor(), "#0f766e"),
                color(settings.accentColor(), "#14b8a6"), clean(settings.disclaimer()), clean(settings.doctorSignatureText()), settings.showQrCode(), clean(settings.watermarkText())
        );
    }

    private String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String color(String value, String fallback) { return StringUtils.hasText(value) && value.trim().matches("^#[0-9A-Fa-f]{6}$") ? value.trim() : fallback; }

    private PrescriptionTemplateRecord defaultRecord(UUID tenantId) {
        return new PrescriptionTemplateRecord(null, tenantId, 0, true, null, null, null, "#0f766e", "#14b8a6", null, null, true, null, null, null, null);
    }

    private PrescriptionTemplateRecord toRecord(PrescriptionTemplateEntity e) {
        return new PrescriptionTemplateRecord(e.getId(), e.getTenantId(), e.getTemplateVersion(), e.isActive(), e.getClinicLogoDocumentId(), e.getHeaderText(), e.getFooterText(), e.getPrimaryColor(), e.getAccentColor(), e.getDisclaimer(), e.getDoctorSignatureText(), e.isShowQrCode(), e.getWatermarkText(), e.getChangedByAppUserId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
