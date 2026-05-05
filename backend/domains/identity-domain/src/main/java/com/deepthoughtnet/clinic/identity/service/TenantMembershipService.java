package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class TenantMembershipService {

    private final TenantMembershipRepository repo;

    public TenantMembershipService(TenantMembershipRepository repo) {
        this.repo = repo;
    }

    /**
     * Returns authoritative tenant role from tenant_memberships.
     * Missing membership means the request has no tenant role.
     */
    @Transactional(readOnly = true)
    public String getOrCreateRole(UUID tenantId, UUID appUserId, Set<String> tokenRolesUpper) {
        return repo.findByTenantIdAndAppUserId(tenantId, appUserId)
                .map(membership -> "ACTIVE".equalsIgnoreCase(membership.getStatus())
                        ? membership.getRole()
                        : "DISABLED")
                .orElse(null);
    }
}
