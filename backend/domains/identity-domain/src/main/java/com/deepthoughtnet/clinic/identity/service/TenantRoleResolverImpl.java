package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.platform.core.security.TenantRoleResolver;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class TenantRoleResolverImpl implements TenantRoleResolver {

    private final TenantMembershipService membershipService;

    public TenantRoleResolverImpl(TenantMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Override
    public String resolveTenantRole(UUID tenantId, UUID appUserId, Set<String> tokenRolesUpper) {
        return membershipService.getOrCreateRole(tenantId, appUserId, tokenRolesUpper);
    }
}