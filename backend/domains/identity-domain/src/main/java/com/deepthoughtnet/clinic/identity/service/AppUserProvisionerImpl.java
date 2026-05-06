package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class AppUserProvisionerImpl implements AppUserProvisioner {

    private final AppUserRepository repo;

    public AppUserProvisionerImpl(AppUserRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public UUID upsertAndReturnId(UUID tenantId, String keycloakSub, String email, String displayName) {
        String safeName = (displayName == null || displayName.isBlank())
                ? (email != null ? email : "User")
                : displayName;

        return repo.findByTenantIdAndKeycloakSub(tenantId, keycloakSub)
                .map(u -> {
                    u.updateProfile(email, safeName);
                    return u.getId();
                })
                .orElseGet(() -> {
                    if (email != null && !email.isBlank()) {
                        return repo.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                                .map(u -> {
                                    u.setKeycloakSub(keycloakSub);
                                    u.updateProfile(email, safeName);
                                    return u.getId();
                                })
                                .orElseGet(() -> repo.save(AppUserEntity.create(tenantId, keycloakSub, email, safeName)).getId());
                    }
                    return repo.save(AppUserEntity.create(tenantId, keycloakSub, email, safeName)).getId();
                });
    }
}
