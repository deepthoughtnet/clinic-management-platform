package com.deepthoughtnet.clinic.commercial.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.*;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AddonType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AggregationPeriod;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.EnforcementMode;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.LimitValueType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import com.deepthoughtnet.clinic.commercial.catalog.db.*;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

class CommercialCatalogServiceTest {
    private CommercialCapabilityRepository capabilityRepository;
    private CommercialModuleRepository moduleRepository;
    private CommercialCapabilityModuleRepository capabilityModuleRepository;
    private CommercialFeatureRepository featureRepository;
    private CommercialLimitDefinitionRepository limitRepository;
    private CommercialAddonOfferRepository addonRepository;
    private CommercialAddonCapabilityRepository addonCapabilityRepository;
    private CommercialAddonModuleRepository addonModuleRepository;
    private CommercialAddonFeatureRepository addonFeatureRepository;
    private CommercialAddonLimitIncrementRepository addonLimitIncrementRepository;
    private AuditEventPublisher auditEventPublisher;
    private CommercialCatalogService service;

    @BeforeEach
    void setUp() {
        capabilityRepository = mock(CommercialCapabilityRepository.class);
        moduleRepository = mock(CommercialModuleRepository.class);
        capabilityModuleRepository = mock(CommercialCapabilityModuleRepository.class);
        featureRepository = mock(CommercialFeatureRepository.class);
        limitRepository = mock(CommercialLimitDefinitionRepository.class);
        addonRepository = mock(CommercialAddonOfferRepository.class);
        addonCapabilityRepository = mock(CommercialAddonCapabilityRepository.class);
        addonModuleRepository = mock(CommercialAddonModuleRepository.class);
        addonFeatureRepository = mock(CommercialAddonFeatureRepository.class);
        addonLimitIncrementRepository = mock(CommercialAddonLimitIncrementRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        service = new CommercialCatalogService(
                capabilityRepository,
                moduleRepository,
                capabilityModuleRepository,
                featureRepository,
                limitRepository,
                addonRepository,
                addonCapabilityRepository,
                addonModuleRepository,
                addonFeatureRepository,
                addonLimitIncrementRepository,
                auditEventPublisher,
                new ObjectMapper()
        );
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "platform.admin@jeeva.test", Set.of("PLATFORM_ADMIN"), "PLATFORM_ADMIN", "commercial-catalog-test"));
        when(auditEventPublisher.record(any())).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void duplicateCapabilityCodeIsRejected() {
        when(capabilityRepository.existsByCodeIgnoreCase("HEALTHCARE_CORE")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.createCapability(new CreateCapabilityRequest("HEALTHCARE_CORE", "Healthcare Core", null, Status.ACTIVE, 1, true, true))
        );

        assertThat(ex.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void invalidRuntimeModuleCodeIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.createModule(new CreateModuleRequest("VISION", "Vision", null, Status.ACTIVE, 1, "UNKNOWN"))
        );

        assertThat(ex.getMessage()).contains("Unknown runtime module code");
    }

    @Test
    void featureRequiresValidModule() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.createFeature(new CreateFeatureRequest("REPORT_OCR", "Report OCR", null, Status.ACTIVE, 1, null, "report.ocr"))
        );

        assertThat(ex.getMessage()).contains("moduleId is required");
    }

    @Test
    void retiredCapabilityCannotBeAssociatedToNewModuleLinks() {
        CommercialCapabilityEntity capability = capability("HEALTHCARE_CORE", Status.RETIRED);
        when(capabilityRepository.findById(any())).thenReturn(Optional.of(capability));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.updateCapabilityModules(capability.getId(), new UpdateCapabilityModulesRequest(List.of()))
        );

        assertThat(ex.getMessage()).contains("Retired capability");
    }

    @Test
    void createsAddonAndAuditsIt() {
        when(addonRepository.existsByCodeIgnoreCase("PHARMACY_ADDON")).thenReturn(false);
        when(addonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AddonDetailResponse response = service.createAddon(new CreateAddonOfferRequest("PHARMACY_ADDON", "Pharmacy Add-on", null, Status.ACTIVE, AddonType.CAPABILITY, 1, false));

        assertThat(response.code()).isEqualTo("PHARMACY_ADDON");
        verify(auditEventPublisher).record(any(AuditEventCommand.class));
    }

    @Test
    void listsCapabilitiesInStableOrder() {
        CommercialCapabilityEntity one = capability("A_CAPABILITY", Status.ACTIVE);
        CommercialCapabilityEntity two = capability("B_CAPABILITY", Status.ACTIVE);
        when(capabilityRepository.findAll(org.mockito.ArgumentMatchers.<Specification<CommercialCapabilityEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(one, two), PageRequest.of(0, 20), 2));

        PageResponse<CapabilitySummaryResponse> page = service.listCapabilities(null, null, 0, 20);

        assertThat(page.items()).extracting(CapabilitySummaryResponse::code).containsExactly("A_CAPABILITY", "B_CAPABILITY");
    }

    @Test
    void addonLimitIncrementMustBePositive() {
        CommercialAddonOfferEntity addon = addon("EXTRA_DOCTOR_PACK", Status.ACTIVE);
        CommercialLimitDefinitionEntity limit = limit("MAX_DOCTORS", Status.ACTIVE);
        when(addonRepository.findById(any())).thenReturn(Optional.of(addon));
        when(limitRepository.findById(any())).thenReturn(Optional.of(limit));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.updateAddonLimitIncrements(addon.getId(), new UpdateAddonLimitIncrementsRequest(List.of(new AddonLimitIncrementAssignmentRequest(limit.getId(), java.math.BigDecimal.ZERO))))
        );

        assertThat(ex.getMessage()).contains("incrementValue must be positive");
    }

    private CommercialCapabilityEntity capability(String code, Status status) {
        CommercialCapabilityEntity entity = new CommercialCapabilityEntity();
        setField(entity, "id", UUID.randomUUID());
        setField(entity, "code", code);
        setField(entity, "name", code);
        setField(entity, "status", status);
        setField(entity, "displayOrder", 1);
        setField(entity, "standaloneAllowed", true);
        setField(entity, "addonAllowed", true);
        setField(entity, "modules", Set.of());
        return entity;
    }

    private CommercialAddonOfferEntity addon(String code, Status status) {
        CommercialAddonOfferEntity entity = new CommercialAddonOfferEntity();
        setField(entity, "id", UUID.randomUUID());
        setField(entity, "code", code);
        setField(entity, "name", code);
        setField(entity, "status", status);
        setField(entity, "addonType", AddonType.CAPABILITY);
        setField(entity, "displayOrder", 1);
        setField(entity, "repeatable", false);
        setField(entity, "capabilities", Set.of());
        setField(entity, "modules", Set.of());
        setField(entity, "features", Set.of());
        setField(entity, "limitIncrements", Set.of());
        return entity;
    }

    private CommercialLimitDefinitionEntity limit(String code, Status status) {
        CommercialLimitDefinitionEntity entity = new CommercialLimitDefinitionEntity();
        setField(entity, "id", UUID.randomUUID());
        setField(entity, "code", code);
        setField(entity, "name", code);
        setField(entity, "unit", "units");
        setField(entity, "valueType", LimitValueType.INTEGER);
        setField(entity, "aggregationPeriod", AggregationPeriod.MONTHLY);
        setField(entity, "enforcementMode", EnforcementMode.SOFT);
        setField(entity, "status", status);
        setField(entity, "displayOrder", 1);
        return entity;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
