package com.deepthoughtnet.clinic.identity.service;

import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantPlanEntity;
import com.deepthoughtnet.clinic.identity.db.TenantPlanRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantPlanService {
    private final TenantRepository tenantRepository;
    private final TenantPlanRepository tenantPlanRepository;

    public TenantPlanService(TenantRepository tenantRepository, TenantPlanRepository tenantPlanRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantPlanRepository = tenantPlanRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TenantPlanEntity> findTenantPlan(UUID tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return tenantRepository.findById(tenantId)
                .map(TenantEntity::getPlanId)
                .flatMap(tenantPlanRepository::findById);
    }
}
