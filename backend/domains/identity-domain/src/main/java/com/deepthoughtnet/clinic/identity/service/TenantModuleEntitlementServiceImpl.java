package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.exception.TenantModuleDisabledException;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantModuleEntitlementServiceImpl implements TenantModuleEntitlementService {
    private final TenantRepository tenantRepository;

    public TenantModuleEntitlementServiceImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isModuleEnabled(UUID tenantId, String moduleKey) {
        if (tenantId == null || moduleKey == null || moduleKey.isBlank()) {
            return false;
        }
        return tenantRepository.findById(tenantId)
                .map(tenant -> isEnabled(tenant, moduleKey))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireModuleEnabled(UUID tenantId, String moduleKey) {
        if (!isModuleEnabled(tenantId, moduleKey)) {
            throw new TenantModuleDisabledException(moduleKey);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> listEnabledModules(UUID tenantId) {
        if (tenantId == null) {
            return Set.of();
        }
        return tenantRepository.findById(tenantId)
                .map(this::enabledModules)
                .orElse(Set.of());
    }

    private Set<String> enabledModules(TenantEntity tenant) {
        Set<String> modules = new LinkedHashSet<>();
        addIf(modules, ModuleKeys.CLINIC_AUTOMATION, tenant.isClinicAutomationEnabled());
        addIf(modules, ModuleKeys.CLINIC_GENERATION, tenant.isClinicGenerationEnabled());
        addIf(modules, ModuleKeys.RECONCILIATION, tenant.isReconciliationEnabled());
        addIf(modules, ModuleKeys.DECISIONING, tenant.isDecisioningEnabled());
        addIf(modules, ModuleKeys.AI_COPILOT, tenant.isAiCopilotEnabled());
        addIf(modules, ModuleKeys.AGENT_INTAKE, tenant.isAgentIntakeEnabled());
        addIf(modules, ModuleKeys.GST_FILING, tenant.isGstFilingEnabled());
        addIf(modules, ModuleKeys.DOCTOR_INTELLIGENCE, tenant.isDoctorIntelligenceEnabled());
        addIf(modules, ModuleKeys.TELE_CALLING, tenant.isTeleCallingEnabled());
        return Set.copyOf(modules);
    }

    private boolean isEnabled(TenantEntity tenant, String moduleKey) {
        String normalized = normalize(moduleKey);
        return switch (normalized) {
            case ModuleKeys.CLINIC_AUTOMATION -> tenant.isClinicAutomationEnabled();
            case ModuleKeys.CLINIC_GENERATION -> tenant.isClinicGenerationEnabled();
            case ModuleKeys.RECONCILIATION -> tenant.isReconciliationEnabled();
            case ModuleKeys.DECISIONING -> tenant.isDecisioningEnabled();
            case ModuleKeys.AI_COPILOT -> tenant.isAiCopilotEnabled();
            case ModuleKeys.AGENT_INTAKE -> tenant.isAgentIntakeEnabled();
            case ModuleKeys.GST_FILING -> tenant.isGstFilingEnabled();
            case ModuleKeys.DOCTOR_INTELLIGENCE -> tenant.isDoctorIntelligenceEnabled();
            case ModuleKeys.TELE_CALLING -> tenant.isTeleCallingEnabled();
            default -> false;
        };
    }

    private void addIf(Set<String> modules, String moduleKey, boolean enabled) {
        if (enabled) {
            modules.add(moduleKey);
        }
    }

    private String normalize(String moduleKey) {
        String trimmed = moduleKey.trim();
        if (trimmed.indexOf('_') < 0 && trimmed.indexOf('-') < 0) {
            return trimmed;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "clinic_automation", "clinic-automation" -> ModuleKeys.CLINIC_AUTOMATION;
            case "clinic_generation", "clinic-generation" -> ModuleKeys.CLINIC_GENERATION;
            case "ai_copilot", "ai-copilot" -> ModuleKeys.AI_COPILOT;
            case "agent_intake", "agent-intake" -> ModuleKeys.AGENT_INTAKE;
            case "gst_filing", "gst-filing" -> ModuleKeys.GST_FILING;
            case "doctor_intelligence", "doctor-intelligence" -> ModuleKeys.DOCTOR_INTELLIGENCE;
            case "tele_calling", "tele-calling" -> ModuleKeys.TELE_CALLING;
            default -> trimmed;
        };
    }
}
