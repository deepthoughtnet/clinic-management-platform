package com.deepthoughtnet.clinic.commercial.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideOperation;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideStatus;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideTargetType;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialEffectiveEntitlementEventRepository;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialEffectiveEntitlementSnapshotRepository;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialTenantEntitlementOverrideEntity;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialTenantEntitlementOverrideRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialCapabilityRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialFeatureRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialModuleRepository;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommercialOverrideServiceTest {
    private CommercialEffectiveEntitlementService service;
    private CommercialTenantEntitlementOverrideRepository overrideRepository;
    private CommercialEffectiveEntitlementEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        overrideRepository = mock(CommercialTenantEntitlementOverrideRepository.class);
        eventRepository = mock(CommercialEffectiveEntitlementEventRepository.class);
        service = new CommercialEffectiveEntitlementService(
                mock(CommercialTenantSubscriptionRepository.class),
                mock(CommercialPlanTemplateRepository.class),
                mock(CommercialPlanVersionRepository.class),
                mock(CommercialCapabilityRepository.class),
                mock(CommercialModuleRepository.class),
                mock(CommercialFeatureRepository.class),
                mock(CommercialLimitDefinitionRepository.class),
                mock(CommercialAddonOfferRepository.class),
                overrideRepository,
                mock(CommercialEffectiveEntitlementSnapshotRepository.class),
                eventRepository,
                mock(AuditEventPublisher.class),
                new CommercialRuntimeProperties(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void lifecycleMethodsTransitionOverrideStateWithoutRemovingHistory() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID overrideId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID subscriptionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID actorId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        OffsetDateTime now = OffsetDateTime.parse("2026-07-25T00:00:00Z");

        CommercialTenantEntitlementOverrideEntity entity = CommercialTenantEntitlementOverrideEntity.create(
                overrideId,
                tenantId,
                subscriptionId,
                OverrideTargetType.MODULE,
                "AI_COPILOT",
                OverrideOperation.ENABLE,
                null,
                null,
                LocalDate.parse("2026-07-25"),
                null,
                OverrideStatus.DRAFT,
                "Pilot AI access",
                null,
                now,
                actorId
        );

        when(overrideRepository.findById(overrideId)).thenReturn(Optional.of(entity));
        when(overrideRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.submitOverride(tenantId, overrideId).status()).isEqualTo("PENDING_APPROVAL");
        assertThat(service.approveOverride(tenantId, overrideId, "Reviewed").status()).isEqualTo("APPROVED");
        assertThat(service.cancelOverride(tenantId, overrideId).status()).isEqualTo("CANCELLED");
        assertThat(service.rollbackOverride(tenantId, overrideId, "Rollback requested").status()).isEqualTo("SUPERSEDED");
    }
}
