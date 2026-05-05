package com.deepthoughtnet.clinic.identity.db;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "tenants", uniqueConstraints = {
        @UniqueConstraint(name = "tenants_code_key", columnNames = {"code"})
})
public class TenantEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 256)
    private String name;

    /**
     * Control plane: current plan for tenant (TRIAL/BASIC/PRO/ENTERPRISE).
     */
    @Column(name = "plan_id", nullable = false, length = 32)
    private String planId = "TRIAL";

    /**
     * ACTIVE / SUSPENDED / CANCELLED
     */
    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "module_clinic_automation", nullable = false)
    private boolean clinicAutomationEnabled = true;

    @Column(name = "module_clinic_generation", nullable = false)
    private boolean clinicGenerationEnabled = false;

    @Column(name = "module_reconciliation", nullable = false)
    private boolean reconciliationEnabled = false;

    @Column(name = "module_decisioning", nullable = false)
    private boolean decisioningEnabled = false;

    @Column(name = "module_ai_copilot", nullable = false)
    private boolean aiCopilotEnabled = false;

    @Column(name = "module_agent_intake", nullable = false)
    private boolean agentIntakeEnabled = false;

    @Column(name = "module_gst_filing", nullable = false)
    private boolean gstFilingEnabled = false;

    @Column(name = "module_doctor_intelligence", nullable = false)
    private boolean doctorIntelligenceEnabled = false;

    @Column(name = "module_tele_calling", nullable = false)
    private boolean teleCallingEnabled = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected TenantEntity() {}

    public static TenantEntity create(String code, String name, String planId) {
        TenantEntity t = new TenantEntity();
        t.id = UUID.randomUUID();
        t.code = code;
        t.name = name;
        t.planId = (planId == null || planId.isBlank()) ? "TRIAL" : planId.trim().toUpperCase(Locale.ROOT);
        t.status = "ACTIVE";
        t.applyDefaultModulesForPlan(t.planId);
        t.createdAt = OffsetDateTime.now();
        t.updatedAt = OffsetDateTime.now();
        return t;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getPlanId() { return planId; }
    public String getStatus() { return status; }
    public boolean isClinicAutomationEnabled() { return clinicAutomationEnabled; }
    public boolean isClinicGenerationEnabled() { return clinicGenerationEnabled; }
    public boolean isReconciliationEnabled() { return reconciliationEnabled; }
    public boolean isDecisioningEnabled() { return decisioningEnabled; }
    public boolean isAiCopilotEnabled() { return aiCopilotEnabled; }
    public boolean isAgentIntakeEnabled() { return agentIntakeEnabled; }
    public boolean isGstFilingEnabled() { return gstFilingEnabled; }
    public boolean isDoctorIntelligenceEnabled() { return doctorIntelligenceEnabled; }
    public boolean isTeleCallingEnabled() { return teleCallingEnabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void activate() {
        this.status = "ACTIVE";
        touch();
    }

    public void suspend() {
        this.status = "SUSPENDED";
        touch();
    }

    public void configureModules(
            boolean clinicAutomation,
            boolean clinicGeneration,
            boolean reconciliation,
            boolean decisioning,
            boolean aiCopilot,
            boolean agentIntake,
            boolean gstFiling,
            boolean doctorIntelligence,
            boolean teleCalling
    ) {
        this.clinicAutomationEnabled = clinicAutomation;
        this.clinicGenerationEnabled = clinicGeneration;
        this.reconciliationEnabled = reconciliation;
        this.decisioningEnabled = decisioning;
        this.aiCopilotEnabled = aiCopilot;
        this.agentIntakeEnabled = agentIntake;
        this.gstFilingEnabled = gstFiling;
        this.doctorIntelligenceEnabled = doctorIntelligence;
        this.teleCallingEnabled = teleCalling;
        touch();
    }

    public void configureModules(
            boolean clinicAutomation,
            boolean clinicGeneration,
            boolean reconciliation,
            boolean gstFiling,
            boolean doctorIntelligence,
            boolean teleCalling
    ) {
        configureModules(
                clinicAutomation,
                clinicGeneration,
                reconciliation,
                clinicAutomation,
                clinicAutomation || reconciliation,
                clinicAutomation,
                gstFiling,
                doctorIntelligence,
                teleCalling
        );
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    private void applyDefaultModulesForPlan(String planId) {
        this.clinicAutomationEnabled = true;
        this.clinicGenerationEnabled = false;
        this.reconciliationEnabled = false;
        this.decisioningEnabled = true;
        this.aiCopilotEnabled = true;
        this.agentIntakeEnabled = true;
        this.gstFilingEnabled = false;
        this.doctorIntelligenceEnabled = false;
        this.teleCallingEnabled = false;

        if ("PRO".equalsIgnoreCase(planId)) {
            this.clinicGenerationEnabled = true;
            this.reconciliationEnabled = true;
        } else if ("ENTERPRISE".equalsIgnoreCase(planId)) {
            this.clinicGenerationEnabled = true;
            this.reconciliationEnabled = true;
            this.gstFilingEnabled = true;
            this.doctorIntelligenceEnabled = true;
            this.teleCallingEnabled = true;
        }
    }
}
