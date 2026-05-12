package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantModuleRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.db.TenantSubscriptionEntity;
import com.deepthoughtnet.clinic.identity.db.TenantSubscriptionRepository;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import com.deepthoughtnet.clinic.platform.core.module.SaasModuleCode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSubscriptionService {
    private final TenantRepository tenantRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final TenantModuleEntitlementService tenantModuleEntitlementService;

    public TenantSubscriptionService(
            TenantRepository tenantRepository,
            TenantSubscriptionRepository tenantSubscriptionRepository,
            TenantModuleRepository tenantModuleRepository,
            TenantModuleEntitlementService tenantModuleEntitlementService
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.tenantModuleRepository = tenantModuleRepository;
        this.tenantModuleEntitlementService = tenantModuleEntitlementService;
    }

    @Transactional(readOnly = true)
    public void requireTenantActive(UUID tenantId) {
        if (!isTenantActive(tenantId)) {
            throw new ForbiddenException("Tenant is not active");
        }
    }

    @Transactional(readOnly = true)
    public boolean isTenantActive(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            return false;
        }
        var subscriptions = tenantSubscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (subscriptions.isEmpty()) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return subscriptions.stream().anyMatch(subscription -> isSubscriptionActive(subscription, today));
    }

    @Transactional(readOnly = true)
    public void requireModuleEnabled(UUID tenantId, String moduleCode) {
        if (!isModuleEnabled(tenantId, moduleCode)) {
            throw new ForbiddenException("Module is disabled: " + moduleCode);
        }
    }

    @Transactional(readOnly = true)
    public boolean isModuleEnabled(UUID tenantId, String moduleCode) {
        if (tenantId == null || moduleCode == null || moduleCode.isBlank()) {
            return false;
        }
        String normalized = SaasModuleCode.normalize(moduleCode);

        var override = tenantModuleRepository.findByTenantIdAndModuleCode(tenantId, normalized);
        if (override.isPresent()) {
            return override.get().isEnabled();
        }

        String legacyModuleKey = toLegacyModuleKey(normalized);
        if (legacyModuleKey != null) {
            return tenantModuleEntitlementService.isModuleEnabled(tenantId, legacyModuleKey);
        }
        if ("CAREPILOT".equals(normalized)) {
            return isCarePilotEnabledWithTransitionFallback(tenantId);
        }

        return true;
    }

    /**
     * During the CarePilot entitlement migration window, tenants may still have tele-calling enabled
     * while the dedicated CarePilot flag has not been flipped yet. Prefer the dedicated flag first and
     * only fall back to tele-calling for backward compatibility.
     */
    private boolean isCarePilotEnabledWithTransitionFallback(UUID tenantId) {
        if (tenantModuleEntitlementService.isModuleEnabled(tenantId, ModuleKeys.CAREPILOT)) {
            return true;
        }
        return tenantModuleEntitlementService.isModuleEnabled(tenantId, ModuleKeys.TELE_CALLING);
    }

    private boolean isSubscriptionActive(TenantSubscriptionEntity subscription, LocalDate today) {
        String status = subscription.getStatus() == null ? "" : subscription.getStatus().toUpperCase(Locale.ROOT);
        boolean activeStatus = "ACTIVE".equals(status) || "TRIAL".equals(status);
        if (!activeStatus) {
            return false;
        }
        if (subscription.getStartDate() != null && today.isBefore(subscription.getStartDate())) {
            return false;
        }
        return subscription.getEndDate() == null || !today.isAfter(subscription.getEndDate());
    }

    private String toLegacyModuleKey(String moduleCode) {
        return switch (moduleCode) {
            case "APPOINTMENTS", "CONSULTATION", "PRESCRIPTION", "BILLING", "VACCINATION", "INVENTORY" -> ModuleKeys.CLINIC_AUTOMATION;
            case "AI_COPILOT" -> ModuleKeys.AI_COPILOT;
            case "CAREPILOT" -> null;
            default -> null;
        };
    }
}
