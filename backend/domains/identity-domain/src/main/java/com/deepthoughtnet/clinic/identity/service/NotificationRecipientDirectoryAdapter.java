package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.platform.contracts.notification.NotificationRecipientDirectoryPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class NotificationRecipientDirectoryAdapter implements NotificationRecipientDirectoryPort {
    private final TenantMembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;

    public NotificationRecipientDirectoryAdapter(TenantMembershipRepository membershipRepository,
                                                 AppUserRepository appUserRepository) {
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public List<String> resolveEmailsByRoles(UUID tenantId, List<String> roles) {
        if (tenantId == null || roles == null || roles.isEmpty()) {
            return List.of();
        }

        Set<String> normalizedRoles = roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalizedRoles.isEmpty()) {
            return List.of();
        }

        List<TenantMembershipEntity> memberships = membershipRepository.findByTenantId(tenantId)
                .stream()
                .filter(membership -> "ACTIVE".equalsIgnoreCase(membership.getStatus()))
                .filter(membership -> normalizedRoles.contains(normalizeRole(membership.getRole())))
                .toList();
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<UUID> appUserIds = memberships.stream()
                .map(TenantMembershipEntity::getAppUserId)
                .distinct()
                .toList();
        Map<UUID, AppUserEntity> usersById = appUserRepository.findByTenantIdAndIdIn(tenantId, appUserIds)
                .stream()
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .collect(Collectors.toMap(AppUserEntity::getId, Function.identity()));

        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (TenantMembershipEntity membership : memberships) {
            AppUserEntity user = usersById.get(membership.getAppUserId());
            if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                emails.add(user.getEmail().trim());
            }
        }
        return List.copyOf(emails);
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
