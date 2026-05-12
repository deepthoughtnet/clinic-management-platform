package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesCommand;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantManagementService {

    private final TenantRepository tenantRepository;

    public PlatformTenantManagementService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public List<PlatformTenantRecord> list() {
        return tenantRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformTenantRecord get(UUID tenantId) {
        return toRecord(findTenant(tenantId));
    }

    @Transactional
    public PlatformTenantRecord activate(UUID tenantId) {
        TenantEntity tenant = findTenant(tenantId);
        tenant.activate();
        return toRecord(tenant);
    }

    @Transactional
    public PlatformTenantRecord suspend(UUID tenantId) {
        TenantEntity tenant = findTenant(tenantId);
        tenant.suspend();
        return toRecord(tenant);
    }

    @Transactional
    public PlatformTenantRecord configureModules(UUID tenantId, TenantModulesCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("module configuration is required");
        }

        TenantEntity tenant = findTenant(tenantId);
        tenant.configureModules(
                command.clinicAutomation(),
                command.clinicGeneration(),
                command.reconciliation(),
                command.decisioning(),
                command.aiCopilot(),
                command.agentIntake(),
                command.gstFiling(),
                command.doctorIntelligence(),
                command.teleCalling(),
                command.carePilot()
        );
        return toRecord(tenant);
    }

    private TenantEntity findTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
    }

    private PlatformTenantRecord toRecord(TenantEntity tenant) {
        return new PlatformTenantRecord(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getPlanId(),
                tenant.getStatus(),
                new TenantModulesRecord(
                        tenant.isClinicAutomationEnabled(),
                        tenant.isClinicGenerationEnabled(),
                        tenant.isReconciliationEnabled(),
                        tenant.isDecisioningEnabled(),
                        tenant.isAiCopilotEnabled(),
                        tenant.isAgentIntakeEnabled(),
                        tenant.isGstFilingEnabled(),
                        tenant.isDoctorIntelligenceEnabled(),
                        tenant.isTeleCallingEnabled(),
                        tenant.isCarePilotEnabled()
                ),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
