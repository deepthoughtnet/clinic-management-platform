package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.service.model.ActiveTenantMembershipRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ActiveTenantMembershipService {

    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;

    public ActiveTenantMembershipService(
            TenantMembershipRepository membershipRepository,
            TenantRepository tenantRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public List<ActiveTenantMembershipRecord> listActiveMemberships(String keycloakSub) {
        if (!StringUtils.hasText(keycloakSub)) {
            return List.of();
        }

        var memberships = membershipRepository.findActiveByKeycloakSub(keycloakSub.trim());
        var tenantsById = tenantRepository.findAllById(
                        memberships.stream().map(membership -> membership.getTenantId()).toList()
                )
                .stream()
                .collect(Collectors.toMap(tenant -> tenant.getId(), Function.identity()));

        return memberships.stream()
                .map(membership -> {
                    var tenant = tenantsById.get(membership.getTenantId());
                    return new ActiveTenantMembershipRecord(
                            membership.getTenantId(),
                            tenant == null ? null : tenant.getCode(),
                            tenant == null ? null : tenant.getName(),
                            membership.getRole(),
                            membership.getStatus(),
                            tenant == null ? null : new TenantModulesRecord(
                                    tenant.isClinicAutomationEnabled(),
                                    tenant.isClinicGenerationEnabled(),
                                    tenant.isReconciliationEnabled(),
                                    tenant.isDecisioningEnabled(),
                                    tenant.isAiCopilotEnabled(),
                                    tenant.isAgentIntakeEnabled(),
                                    tenant.isGstFilingEnabled(),
                                    tenant.isDoctorIntelligenceEnabled(),
                                    tenant.isTeleCallingEnabled()
                            )
                    );
                })
                .toList();
    }
}
