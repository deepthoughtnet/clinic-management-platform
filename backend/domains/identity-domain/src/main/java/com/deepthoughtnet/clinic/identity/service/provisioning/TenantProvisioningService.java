package com.deepthoughtnet.clinic.identity.service.provisioning;

import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.identity.db.TenantPlanRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.service.keycloak.KeycloakAdminProvisioner;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import java.util.Locale;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

public class TenantProvisioningService {

    private final TenantRepository tenantRepo;
    private final TenantPlanRepository planRepo;
    private final AppUserProvisioner appUserProvisioner;
    private final TenantMembershipRepository membershipRepo;
    private final KeycloakAdminProvisioner keycloak;

    public TenantProvisioningService(
            TenantRepository tenantRepo,
            TenantPlanRepository planRepo,
            AppUserProvisioner appUserProvisioner,
            TenantMembershipRepository membershipRepo,
            KeycloakAdminProvisioner keycloak
    ) {
        this.tenantRepo = tenantRepo;
        this.planRepo = planRepo;
        this.appUserProvisioner = appUserProvisioner;
        this.membershipRepo = membershipRepo;
        this.keycloak = keycloak;
    }

    /**
     * PLATFORM_ADMIN operation. No tenant context required to call this service.
     *
     * Idempotency: tenantCode is globally unique (tenants.code unique).
     * - If tenant already exists, we re-ensure the bootstrap admin user + membership exists.
     */
    @Transactional
    public TenantProvisioningResult provisionTenant(TenantProvisioningRequest req) {
        validate(req);

        String code = req.tenantCode().trim().toLowerCase(Locale.ROOT);
        String planId = normalizePlan(req.planId());

        // Validate plan exists
        planRepo.findById(planId).orElseThrow(() -> new IllegalArgumentException("Unknown planId: " + planId));

        TenantEntity tenant = tenantRepo.findByCode(code)
                .orElseGet(() -> tenantRepo.save(TenantEntity.create(code, req.tenantName().trim(), planId)));

        // Create/find Keycloak user (userId == sub)
        String kcUserId = keycloak.createOrGetTenantAdminUserId(
                tenant.getId(),
                req.adminEmail().trim(),
                req.adminDisplayName(),
                req.tempPassword()
        );

        // Ensure realm ADMIN role
        keycloak.ensureRealmRole(kcUserId, "ADMIN");

        // Upsert app_user
        UUID appUserId = appUserProvisioner.upsertAndReturnId(
                tenant.getId(),
                kcUserId,
                req.adminEmail().trim(),
                req.adminDisplayName()
        );

        // Ensure tenant_membership ADMIN exists
        membershipRepo.findByTenantIdAndAppUserId(tenant.getId(), appUserId)
                .orElseGet(() -> membershipRepo.save(TenantMembershipEntity.create(tenant.getId(), appUserId, "ADMIN")));

        return new TenantProvisioningResult(
                tenant.getId(),
                tenant.getCode(),
                tenant.getPlanId(),
                req.adminEmail().trim(),
                kcUserId,
                appUserId
        );
    }

    private void validate(TenantProvisioningRequest req) {
        if (req == null) throw new IllegalArgumentException("request is required");
        if (!StringUtils.hasText(req.tenantCode())) throw new IllegalArgumentException("tenantCode is required");
        if (!StringUtils.hasText(req.tenantName())) throw new IllegalArgumentException("tenantName is required");
        if (!StringUtils.hasText(req.adminEmail())) throw new IllegalArgumentException("adminEmail is required");
    }

    private String normalizePlan(String planId) {
        if (!StringUtils.hasText(planId)) return "TRIAL";
        return planId.trim().toUpperCase(Locale.ROOT);
    }
}
