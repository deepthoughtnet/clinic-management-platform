package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.identity.service.keycloak.KeycloakAdminProvisioner;
import com.deepthoughtnet.clinic.identity.service.model.CreateTenantUserCommand;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TenantUserManagementService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "TENANT_ADMIN",
            "ADMIN",
            "CLINIC_ADMIN",
            "CLINIC_REVIEWER",
            "CLINIC_APPROVER",
            "CLINIC_AUDITOR",
            "CLINIC_GENERATION_CREATOR",
            "CLINIC_GENERATION_APPROVER",
            "CLINIC_GENERATION_MANAGER",
            "CLINIC_GENERATION_VIEWER",
            "RECONCILIATION_OPERATOR",
            "RECONCILIATION_REVIEWER",
            "RECONCILIATION_MANAGER",
            "RECONCILIATION_VIEWER",
            "AGENT_OPERATOR",
            "DECISIONING_MANAGER",
            "DECISIONING_VIEWER",
            "AUDITOR",
            "CLINIC_VIEWER",
            "VIEWER",
            "SERVICE_AGENT"
    );

    private final AppUserRepository appUserRepository;
    private final TenantMembershipRepository membershipRepository;
    private final KeycloakAdminProvisioner keycloakAdminProvisioner;

    public TenantUserManagementService(
            AppUserRepository appUserRepository,
            TenantMembershipRepository membershipRepository,
            KeycloakAdminProvisioner keycloakAdminProvisioner
    ) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.keycloakAdminProvisioner = keycloakAdminProvisioner;
    }

    @Transactional(readOnly = true)
    public List<TenantUserRecord> list(UUID tenantId) {
        requireTenant(tenantId);

        List<TenantMembershipEntity> memberships = membershipRepository.findByTenantId(tenantId);
        List<UUID> appUserIds = memberships.stream()
                .map(TenantMembershipEntity::getAppUserId)
                .distinct()
                .toList();

        if (appUserIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, AppUserEntity> users = appUserRepository.findByTenantIdAndIdIn(tenantId, appUserIds)
                .stream()
                .collect(Collectors.toMap(AppUserEntity::getId, Function.identity(), (a, b) -> a));

        return memberships.stream()
                .map(membership -> {
                    AppUserEntity user = users.get(membership.getAppUserId());
                    return user == null ? null : toRecord(user, membership, "EXISTING");
                })
                .filter(record -> record != null)
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    @Transactional
    public TenantUserRecord createOrInvite(CreateTenantUserCommand command) {
        validate(command);

        String role = normalizeRole(command.role());
        String email = normalizeNullable(command.email());
        String username = normalizeNullable(command.username());
        String displayName = normalizeNullable(command.displayName());

        String keycloakSub = keycloakAdminProvisioner.createOrGetTenantUserId(
                command.tenantId(),
                email,
                username,
                displayName,
                command.tempPassword(),
                StringUtils.hasText(email)
        );

        AppUserEntity user = appUserRepository.findByTenantIdAndKeycloakSub(command.tenantId(), keycloakSub)
                .map(existing -> {
                    existing.updateProfile(email, displayNameFor(email, username, displayName));
                    return existing;
                })
                .orElseGet(() -> appUserRepository.save(AppUserEntity.create(
                        command.tenantId(),
                        keycloakSub,
                        email,
                        displayNameFor(email, username, displayName)
                )));

        TenantMembershipEntity membership = membershipRepository.findByTenantIdAndAppUserId(
                        command.tenantId(),
                        user.getId()
                )
                .orElseGet(() -> membershipRepository.save(TenantMembershipEntity.create(
                        command.tenantId(),
                        user.getId(),
                        role
                )));

        membership.setRole(role);
        membership.setStatus("ACTIVE");

        return toRecord(user, membership, "KEYCLOAK_USER_READY");
    }

    @Transactional
    public TenantUserRecord updateRole(UUID tenantId, UUID appUserId, String role) {
        requireTenant(tenantId);
        if (appUserId == null) {
            throw new IllegalArgumentException("appUserId is required");
        }

        String normalizedRole = normalizeRole(role);
        AppUserEntity user = findTenantUser(tenantId, appUserId);
        TenantMembershipEntity membership = findMembership(tenantId, appUserId);
        membership.setRole(normalizedRole);
        return toRecord(user, membership, "ROLE_UPDATED");
    }

    @Transactional
    public TenantUserRecord updateStatus(UUID tenantId, UUID appUserId, boolean active) {
        requireTenant(tenantId);
        if (appUserId == null) {
            throw new IllegalArgumentException("appUserId is required");
        }

        AppUserEntity user = findTenantUser(tenantId, appUserId);
        TenantMembershipEntity membership = findMembership(tenantId, appUserId);
        membership.setStatus(active ? "ACTIVE" : "DISABLED");
        return toRecord(user, membership, active ? "MEMBERSHIP_REACTIVATED" : "MEMBERSHIP_DISABLED");
    }

    private AppUserEntity findTenantUser(UUID tenantId, UUID appUserId) {
        return appUserRepository.findByTenantIdAndId(tenantId, appUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found for tenant"));
    }

    private TenantMembershipEntity findMembership(UUID tenantId, UUID appUserId) {
        return membershipRepository.findByTenantIdAndAppUserId(tenantId, appUserId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant membership not found"));
    }

    private TenantUserRecord toRecord(
            AppUserEntity user,
            TenantMembershipEntity membership,
            String provisioningStatus
    ) {
        return new TenantUserRecord(
                user.getId(),
                user.getTenantId(),
                user.getKeycloakSub(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                membership == null ? null : membership.getRole(),
                membership == null ? null : membership.getStatus(),
                user.getCreatedAt(),
                membership == null ? user.getUpdatedAt() : membership.getUpdatedAt(),
                provisioningStatus
        );
    }

    private void validate(CreateTenantUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireTenant(command.tenantId());
        if (!StringUtils.hasText(command.email()) && !StringUtils.hasText(command.username())) {
            throw new IllegalArgumentException("email or username is required");
        }
        normalizeRole(command.role());
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("role is required");
        }
        String normalized = role.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported tenant role: " + role);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String displayNameFor(String email, String username, String displayName) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        if (StringUtils.hasText(email)) {
            return email.trim();
        }
        return username == null ? "User" : username.trim();
    }
}
