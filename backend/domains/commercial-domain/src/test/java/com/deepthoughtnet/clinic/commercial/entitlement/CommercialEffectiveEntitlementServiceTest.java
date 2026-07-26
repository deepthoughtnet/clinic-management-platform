package com.deepthoughtnet.clinic.commercial.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.GenerationReason;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.AddOnEffectiveState;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.AddOnContributionResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.CapabilityResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.FeatureResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.LimitResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.ModuleResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.ProvenanceResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.SourceType;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialCapabilityRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialFeatureRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialModuleRepository;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialEffectiveEntitlementEventRepository;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialEffectiveEntitlementSnapshotRepository;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialTenantEntitlementOverrideRepository;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommercialEffectiveEntitlementServiceTest {
    private CommercialEffectiveEntitlementService service;
    private CommercialRuntimeProperties runtimeProperties;

    @BeforeEach
    void setUp() {
        service = new CommercialEffectiveEntitlementService(
                mock(CommercialTenantSubscriptionRepository.class),
                mock(CommercialPlanTemplateRepository.class),
                mock(CommercialPlanVersionRepository.class),
                mock(CommercialCapabilityRepository.class),
                mock(CommercialModuleRepository.class),
                mock(CommercialFeatureRepository.class),
                mock(CommercialLimitDefinitionRepository.class),
                mock(CommercialAddonOfferRepository.class),
                mock(CommercialTenantEntitlementOverrideRepository.class),
                mock(CommercialEffectiveEntitlementSnapshotRepository.class),
                mock(CommercialEffectiveEntitlementEventRepository.class),
                mock(AuditEventPublisher.class),
                runtimeProperties = new CommercialRuntimeProperties(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void runtimeAllowlistControlsCommercialCutover() {
        runtimeProperties.setEnabled(true);
        runtimeProperties.setTenantAllowlist(Set.of("00000000-0000-0000-0000-000000000001"));

        assertThat(service.isCommercialRuntimeEnabledForTenant(UUID.fromString("00000000-0000-0000-0000-000000000001"))).isTrue();
        assertThat(service.isCommercialRuntimeEnabledForTenant(UUID.fromString("00000000-0000-0000-0000-000000000002"))).isFalse();
    }

    @Test
    void canonicalHashRemainsStableAfterSortingAndChangesWhenContentChanges() throws Exception {
        Map<String, CapabilityResponse> capabilitiesA = new LinkedHashMap<>();
        capabilitiesA.put("B", new CapabilityResponse("B", "Beta", true, SourceType.PLAN, "plan"));
        capabilitiesA.put("A", new CapabilityResponse("A", "Alpha", true, SourceType.PLAN, "plan"));

        Map<String, CapabilityResponse> capabilitiesB = new LinkedHashMap<>();
        capabilitiesB.put("A", new CapabilityResponse("A", "Alpha", true, SourceType.PLAN, "plan"));
        capabilitiesB.put("B", new CapabilityResponse("B", "Beta", true, SourceType.PLAN, "plan"));

        Map<String, ModuleResponse> modulesA = new LinkedHashMap<>();
        modulesA.put("B", new ModuleResponse("B", "Beta Module", "BETA", true, SourceType.PLAN, "plan", "A"));
        modulesA.put("A", new ModuleResponse("A", "Alpha Module", "ALPHA", true, SourceType.PLAN, "plan", "A"));

        Map<String, ModuleResponse> modulesB = new LinkedHashMap<>();
        modulesB.put("A", new ModuleResponse("A", "Alpha Module", "ALPHA", true, SourceType.PLAN, "plan", "A"));
        modulesB.put("B", new ModuleResponse("B", "Beta Module", "BETA", true, SourceType.PLAN, "plan", "A"));

        Map<String, FeatureResponse> features = new LinkedHashMap<>();
        features.put("B", new FeatureResponse("B", "Beta Feature", "feature.beta", "A", true, SourceType.PLAN, "plan"));
        features.put("A", new FeatureResponse("A", "Alpha Feature", "feature.alpha", "A", true, SourceType.PLAN, "plan"));

        Map<String, LimitResponse> limits = new LinkedHashMap<>();
        limits.put("B", new LimitResponse("B", "Beta Limit", "INTEGER", "10", false, "count", "MONTH", "SOFT", SourceType.PLAN, null));
        limits.put("A", new LimitResponse("A", "Alpha Limit", "INTEGER", "5", false, "count", "MONTH", "SOFT", SourceType.PLAN, null));

        Map<String, AddOnContributionResponse> addOns = new LinkedHashMap<>();
        addOns.put("B", new AddOnContributionResponse("B", "Beta Add-on", AddOnEffectiveState.INCLUDED, SourceType.PLAN, List.of()));
        addOns.put("A", new AddOnContributionResponse("A", "Alpha Add-on", AddOnEffectiveState.AVAILABLE_FOR_PURCHASE, SourceType.PLAN, List.of()));

        List<ProvenanceResponse> provenance = new ArrayList<>(List.of(
                new ProvenanceResponse("MODULE", "B", "PLAN", "plan", null),
                new ProvenanceResponse("MODULE", "A", "PLAN", "plan", null)
        ));

        invokeSortCanonical(capabilitiesA, modulesA, features, limits, addOns, provenance);
        invokeSortCanonical(capabilitiesB, modulesB, features, limits, addOns, provenance);

        String hashA = invokeHashContent(capabilitiesA, modulesA, features, limits, addOns, provenance);
        String hashB = invokeHashContent(capabilitiesB, modulesB, features, limits, addOns, provenance);

        assertThat(hashA).isEqualTo(hashB);
        capabilitiesB.put("C", new CapabilityResponse("C", "Gamma", true, SourceType.PLAN, "plan"));
        invokeSortCanonical(capabilitiesB, modulesB, features, limits, addOns, provenance);
        String hashC = invokeHashContent(capabilitiesB, modulesB, features, limits, addOns, provenance);
        assertThat(hashC).isNotEqualTo(hashA);
    }

    private void invokeSortCanonical(
            Map<String, CapabilityResponse> capabilities,
            Map<String, ModuleResponse> modules,
            Map<String, FeatureResponse> features,
            Map<String, LimitResponse> limits,
            Map<String, AddOnContributionResponse> addOns,
            List<ProvenanceResponse> provenance
    ) throws Exception {
        Method method = CommercialEffectiveEntitlementService.class.getDeclaredMethod("sortCanonical", Map.class, Map.class, Map.class, Map.class, Map.class, List.class);
        method.setAccessible(true);
        method.invoke(service, capabilities, modules, features, limits, addOns, provenance);
    }

    private String invokeHashContent(
            Map<String, CapabilityResponse> capabilities,
            Map<String, ModuleResponse> modules,
            Map<String, FeatureResponse> features,
            Map<String, LimitResponse> limits,
            Map<String, AddOnContributionResponse> addOns,
            List<ProvenanceResponse> provenance
    ) throws Exception {
        Method method = CommercialEffectiveEntitlementService.class.getDeclaredMethod(
                "hashContent",
                UUID.class,
                com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionEntity.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                List.class,
                GenerationReason.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, UUID.randomUUID(), null, capabilities, modules, features, limits, addOns, provenance, GenerationReason.MANUAL_REGENERATE);
    }
}
