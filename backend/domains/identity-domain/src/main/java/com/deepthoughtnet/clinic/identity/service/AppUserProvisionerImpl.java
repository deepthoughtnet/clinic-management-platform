package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

public class AppUserProvisionerImpl implements AppUserProvisioner {

    private final AppUserRepository repo;

    public AppUserProvisionerImpl(AppUserRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public UUID upsertAndReturnId(UUID tenantId, String keycloakSub, String email, String displayName) {
        String normalizedSub = StringUtils.hasText(keycloakSub) ? keycloakSub.trim() : null;
        String normalizedEmail = StringUtils.hasText(email) ? email.trim() : null;
        String safeName = (displayName == null || displayName.isBlank())
                ? (normalizedEmail != null ? normalizedEmail : "User")
                : displayName;

        if (normalizedSub != null) {
            var bySub = repo.findByTenantIdAndKeycloakSub(tenantId, normalizedSub);
            if (bySub.isPresent()) {
                AppUserEntity user = bySub.get();
                user.updateProfile(normalizedEmail, safeName);
                return user.getId();
            }
        }

        if (normalizedEmail != null) {
            var byEmail = repo.findByTenantIdAndEmailIgnoreCase(tenantId, normalizedEmail);
            if (byEmail.isPresent()) {
                AppUserEntity user = byEmail.get();
                if (normalizedSub != null) {
                    user.setKeycloakSub(normalizedSub);
                }
                user.updateProfile(normalizedEmail, safeName);
                return user.getId();
            }
        }

        try {
            return repo.save(AppUserEntity.create(tenantId, normalizedSub, normalizedEmail, safeName)).getId();
        } catch (DataIntegrityViolationException ex) {
            // Idempotency under retries/concurrency: resolve existing row and reuse it.
            if (normalizedSub != null) {
                var existingBySub = repo.findByTenantIdAndKeycloakSub(tenantId, normalizedSub);
                if (existingBySub.isPresent()) {
                    AppUserEntity user = existingBySub.get();
                    user.updateProfile(normalizedEmail, safeName);
                    return user.getId();
                }
            }
            if (normalizedEmail != null) {
                var existingByEmail = repo.findByTenantIdAndEmailIgnoreCase(tenantId, normalizedEmail);
                if (existingByEmail.isPresent()) {
                    AppUserEntity user = existingByEmail.get();
                    if (normalizedSub != null) {
                        user.setKeycloakSub(normalizedSub);
                    }
                    user.updateProfile(normalizedEmail, safeName);
                    return user.getId();
                }
            }
            throw ex;
        }
    }
}
