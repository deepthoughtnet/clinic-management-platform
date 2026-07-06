package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.TenantOnboardingEntity;
import com.deepthoughtnet.clinic.identity.db.TenantOnboardingRepository;
import com.deepthoughtnet.clinic.identity.service.model.TenantOnboardingRecord;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantOnboardingService {
    private final TenantOnboardingRepository repository;

    public TenantOnboardingService(TenantOnboardingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TenantOnboardingRecord getStatus(UUID tenantId) {
        return repository.findByTenantId(tenantId)
                .map(this::toRecord)
                .orElseGet(() -> toRecord(repository.saveAndFlush(TenantOnboardingEntity.create(tenantId, false))));
    }

    @Transactional
    public TenantOnboardingRecord initializeForNewTenant(UUID tenantId) {
        return repository.findByTenantId(tenantId)
                .map(this::toRecord)
                .orElseGet(() -> toRecord(repository.saveAndFlush(TenantOnboardingEntity.create(tenantId, false))));
    }

    @Transactional
    public TenantOnboardingRecord markCompleted(UUID tenantId) {
        TenantOnboardingEntity entity = repository.findByTenantId(tenantId)
                .orElseGet(() -> repository.saveAndFlush(TenantOnboardingEntity.create(tenantId, false)));
        entity.markCompleted();
        return toRecord(entity);
    }

    @Transactional
    public TenantOnboardingRecord markSkipped(UUID tenantId) {
        TenantOnboardingEntity entity = repository.findByTenantId(tenantId)
                .orElseGet(() -> repository.saveAndFlush(TenantOnboardingEntity.create(tenantId, false)));
        entity.markSkipped();
        return toRecord(entity);
    }

    private TenantOnboardingRecord toRecord(TenantOnboardingEntity entity) {
        return new TenantOnboardingRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.isCompleted(),
                entity.isSkipped(),
                entity.getCompletedAt(),
                entity.getSkippedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
