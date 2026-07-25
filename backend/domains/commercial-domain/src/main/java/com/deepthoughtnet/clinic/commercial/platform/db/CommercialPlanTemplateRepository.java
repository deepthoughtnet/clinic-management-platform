package com.deepthoughtnet.clinic.commercial.platform.db;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TemplateStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommercialPlanTemplateRepository extends JpaRepository<CommercialPlanTemplateEntity, UUID>, JpaSpecificationExecutor<CommercialPlanTemplateEntity> {
    boolean existsByCodeIgnoreCase(String code);
    Optional<CommercialPlanTemplateEntity> findByCodeIgnoreCase(String code);
    Page<CommercialPlanTemplateEntity> findAllByStatus(TemplateStatus status, Pageable pageable);
}
