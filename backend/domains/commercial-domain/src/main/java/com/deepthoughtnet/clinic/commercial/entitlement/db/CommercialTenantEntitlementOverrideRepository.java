package com.deepthoughtnet.clinic.commercial.entitlement.db;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideStatus;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideTargetType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialTenantEntitlementOverrideRepository extends JpaRepository<CommercialTenantEntitlementOverrideEntity, UUID> {
    List<CommercialTenantEntitlementOverrideEntity> findByTenantIdOrderByEffectiveFromAscUpdatedAtAsc(UUID tenantId);

    List<CommercialTenantEntitlementOverrideEntity> findByTenantIdAndStatusInOrderByEffectiveFromAscUpdatedAtAsc(UUID tenantId, List<OverrideStatus> statuses);

    List<CommercialTenantEntitlementOverrideEntity> findByTenantIdAndTargetTypeAndTargetCodeOrderByUpdatedAtDesc(UUID tenantId, OverrideTargetType targetType, String targetCode);
}
