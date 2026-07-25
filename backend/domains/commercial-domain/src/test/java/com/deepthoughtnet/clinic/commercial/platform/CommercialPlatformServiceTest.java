package com.deepthoughtnet.clinic.commercial.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AddonType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AggregationPeriod;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.EnforcementMode;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.LimitValueType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialCapabilityEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialCapabilityRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialFeatureEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialFeatureRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialModuleEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialModuleRepository;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.ConfiguredLimitRequest;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.CreatePlanTemplateRequest;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.DraftAddonResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.DraftConfigurationResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.DraftFeatureResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.DraftLimitResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.DraftModuleResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.LifecycleStageResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.KpiCardResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.OverviewResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PageResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanConfigurationSnapshot;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanDraftResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanVersionDetailResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PublishPlanVersionRequest;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.QuickActionResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SavePlanDraftRequest;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedAddon;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedAddonRequest;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedCapability;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedCapabilityRequest;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedFeature;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedFeatureRequest;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedLimit;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedModule;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedModuleRequest;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.TemplateDetailResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.TemplateSummaryResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.ValidationMessageResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.ValidatePlanDraftResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationState;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionService;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionStatusCountsResponse;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanDraftEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanDraftRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

class CommercialPlatformServiceTest {
    private CommercialCapabilityRepository capabilityRepository;
    private CommercialModuleRepository moduleRepository;
    private CommercialFeatureRepository featureRepository;
    private CommercialLimitDefinitionRepository limitRepository;
    private CommercialAddonOfferRepository addonRepository;
    private CommercialPlanTemplateRepository templateRepository;
    private CommercialPlanDraftRepository draftRepository;
    private CommercialPlanVersionRepository versionRepository;
    private CommercialSubscriptionService subscriptionService;
    private AuditEventPublisher auditEventPublisher;
    private CommercialPlatformService service;
    private ObjectMapper objectMapper;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        capabilityRepository = mock(CommercialCapabilityRepository.class);
        moduleRepository = mock(CommercialModuleRepository.class);
        featureRepository = mock(CommercialFeatureRepository.class);
        limitRepository = mock(CommercialLimitDefinitionRepository.class);
        addonRepository = mock(CommercialAddonOfferRepository.class);
        templateRepository = mock(CommercialPlanTemplateRepository.class);
        draftRepository = mock(CommercialPlanDraftRepository.class);
        versionRepository = mock(CommercialPlanVersionRepository.class);
        subscriptionService = mock(CommercialSubscriptionService.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new CommercialPlatformService(
                capabilityRepository,
                moduleRepository,
                featureRepository,
                limitRepository,
                addonRepository,
                templateRepository,
                draftRepository,
                versionRepository,
                subscriptionService,
                auditEventPublisher,
                objectMapper
        );
        actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), actorId, "platform.admin@jeeva.test", Set.of("PLATFORM_ADMIN"), "PLATFORM_ADMIN", "commercial-platform-test"));
        when(auditEventPublisher.record(any())).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void overviewReflectsCurrentCatalogAndLifecycleCounts() {
        when(capabilityRepository.count(any(Specification.class))).thenReturn(4L);
        when(moduleRepository.count(any(Specification.class))).thenReturn(6L);
        when(featureRepository.count(any(Specification.class))).thenReturn(8L);
        when(limitRepository.count(any(Specification.class))).thenReturn(5L);
        when(addonRepository.count(any(Specification.class))).thenReturn(3L);
        when(templateRepository.count()).thenReturn(2L);
        when(versionRepository.countByStatus(com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.PublicationStatus.PUBLISHED)).thenReturn(7L);
        when(draftRepository.count()).thenReturn(2L);
        when(subscriptionService.getStatusCounts()).thenReturn(new SubscriptionStatusCountsResponse(5L, 2L, 1L, 3L, 4L));

        OverviewResponse response = service.getOverview();

        assertThat(response.kpis()).extracting(KpiCardResponse::label).containsExactly(
                "Active Capabilities",
                "Active Modules",
                "Active Features",
                "Active Limits",
                "Active Add-ons",
                "Published Plans",
                "Plan Templates",
                "Draft Plans",
                "Active Subscriptions",
                "Scheduled",
                "Paused",
                "Expired",
                "Cancelled"
        );
        assertThat(response.lifecycle()).extracting(LifecycleStageResponse::label).containsExactly(
                "Catalog",
                "Plan Template",
                "Draft Configuration",
                "Published Version",
                "Tenant Subscription",
                "Effective Entitlements",
                "Usage and Billing"
        );
        assertThat(response.actions()).extracting(QuickActionResponse::path).containsExactly(
                "/platform/commercial/catalog",
                "/platform/commercial/plans",
                "/platform/commercial/plans",
                "/platform/commercial/plans",
                "/platform/commercial/subscriptions"
        );
    }

    @Test
    void publishVersionCreatesImmutableSnapshotAndAdvancesSequence() {
        UUID templateId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        UUID addonId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "BASIC_CLINIC",
                "Basic Clinic",
                "Commercial package for a small clinic",
                CommercialPlatformEnums.TargetSegment.SMALL_CLINIC,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                1,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                serialize(snapshot(capabilityId, moduleId, limitId, addonId)),
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.READY_TO_PUBLISH,
                true,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        setField(draft, "lastValidatedAt", OffsetDateTime.parse("2026-07-24T00:00:00Z"));
        setField(draft, "validationStatus", "READY");
        CommercialPlanVersionEntity previousVersion = CommercialPlanVersionEntity.create(
                template,
                1,
                "v1",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-23T00:00:00Z"),
                actorId,
                "Initial publish",
                1,
                "previous-hash",
                "{\"templateCode\":\"BASIC_CLINIC\"}",
                1,
                1,
                0,
                1,
                1,
                actorId
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.of(draft));
        when(versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(templateId)).thenReturn(Optional.of(previousVersion));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of(capability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core")));
        when(moduleRepository.findAllById(any())).thenReturn(List.of(module(moduleId, "APPOINTMENTS", "Appointments", "APPOINTMENTS")));
        when(limitRepository.findAllById(any())).thenReturn(List.of(limit(limitId, "MAX_DOCTORS", "Maximum Doctors")));
        when(addonRepository.findAllById(any())).thenReturn(List.of(addon(addonId, "PHARMACY_ADDON", "Pharmacy Add-on")));
        when(versionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlanVersionDetailResponse response = service.publishVersion(templateId, new PublishPlanVersionRequest("Initial publish"));

        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.versionLabel()).isEqualTo("v2");
        assertThat(response.snapshotJson()).contains("BASIC_CLINIC", "Healthcare Core", "Appointments", "Maximum Doctors", "Pharmacy Add-on");
        verify(auditEventPublisher).record(any());
    }

    @Test
    void validateDraftBlocksFeatureWithoutParentModule() {
        UUID templateId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID includedModuleId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "BASIC_CLINIC",
                "Basic Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.CUSTOM,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                1,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                serialize(new PlanConfigurationSnapshot(
                        "BASIC_CLINIC",
                        "Basic Clinic",
                        null,
                        CommercialPlatformEnums.TargetSegment.CUSTOM,
                        CommercialPlatformEnums.TemplateStatus.DRAFT,
                        1,
                        List.of(new SelectedCapability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core", "Core clinical packaging", 1, false)),
                        List.of(new SelectedModule(includedModuleId, "APPOINTMENTS", "Appointments", "OPD scheduling", "APPOINTMENTS", 1, false, CommercialPlatformEnums.SelectionSource.EXPLICIT, false)),
                        List.of(new SelectedFeature(featureId, "REPORT_OCR", "Report OCR", "OCR extraction", moduleId, "REPORTS", "Reports", 1, false)),
                        List.of(),
                        List.of()
                )),
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.BLOCKED,
                false,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialFeatureEntity feature = feature(featureId, moduleId, "REPORT_OCR", "Report OCR");

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.of(draft));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of(capability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core")));
        when(moduleRepository.findAllById(any())).thenReturn(List.of(module(includedModuleId, "APPOINTMENTS", "Appointments", "APPOINTMENTS")));
        when(limitRepository.findAllById(any())).thenReturn(List.of());
        when(addonRepository.findAllById(any())).thenReturn(List.of());
        when(featureRepository.findAllById(any())).thenReturn(List.of(feature));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ValidatePlanDraftResponse response = service.validateDraft(templateId);

        assertThat(response.publicationReady()).isFalse();
        assertThat(response.messages()).hasSize(1);
        ValidationMessageResponse finding = response.messages().get(0);
        assertThat(finding.code()).isEqualTo("FEATURE_PARENT_MODULE_REQUIRED");
        assertThat(finding.title()).isEqualTo("Report OCR requires Reports");
        assertThat(finding.message()).contains("Report OCR", "Reports");
        assertThat(finding.remediation()).contains("Reports", "Report OCR");
        assertThat(finding.category()).isEqualTo("FEATURE_DEPENDENCY");
        assertThat(finding.affectedItemType()).isEqualTo("FEATURE");
        assertThat(finding.affectedItemCode()).isEqualTo("REPORT_OCR");
        assertThat(finding.affectedItemName()).isEqualTo("Report OCR");
        assertThat(finding.expectedItemType()).isEqualTo("MODULE");
        assertThat(finding.expectedItemCode()).isEqualTo("REPORTS");
        assertThat(finding.expectedItemName()).isEqualTo("Reports");
        assertThat(finding.currentValue()).contains("Parent module not included");
        assertThat(finding.expectedValue()).contains("Module included");
        assertThat(finding.targetBuilderTab()).isEqualTo("modules");
        assertThat(finding.actionLabel()).isEqualTo("Add Required Module");
    }

    @Test
    void validateDraftReportsDistinctFeatureDependencyFindingsForDistinctMissingParents() {
        UUID templateId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID includedModuleId = UUID.randomUUID();
        UUID reportsModuleId = UUID.randomUUID();
        UUID aiModuleId = UUID.randomUUID();
        UUID reportFeatureId = UUID.randomUUID();
        UUID aiFeatureId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "BASIC_CLINIC",
                "Basic Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.CUSTOM,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                1,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                serialize(new PlanConfigurationSnapshot(
                        "BASIC_CLINIC",
                        "Basic Clinic",
                        null,
                        CommercialPlatformEnums.TargetSegment.CUSTOM,
                        CommercialPlatformEnums.TemplateStatus.DRAFT,
                        1,
                        List.of(new SelectedCapability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core", "Core clinical packaging", 1, false)),
                        List.of(new SelectedModule(includedModuleId, "APPOINTMENTS", "Appointments", "OPD scheduling", "APPOINTMENTS", 1, false, CommercialPlatformEnums.SelectionSource.EXPLICIT, false)),
                        List.of(
                                new SelectedFeature(reportFeatureId, "REPORT_OCR", "Report OCR", "OCR extraction", reportsModuleId, "REPORTS", "Reports", 1, false),
                                new SelectedFeature(aiFeatureId, "AIVA_VOICE", "Aiva Voice", "Voice assistance", aiModuleId, "AI_COPILOT", "AI Copilot", 2, false)
                        ),
                        List.of(),
                        List.of()
                )),
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.BLOCKED,
                false,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.of(draft));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of(capability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core")));
        when(moduleRepository.findAllById(any())).thenReturn(List.of(module(includedModuleId, "APPOINTMENTS", "Appointments", "APPOINTMENTS")));
        when(limitRepository.findAllById(any())).thenReturn(List.of());
        when(addonRepository.findAllById(any())).thenReturn(List.of());
        when(featureRepository.findAllById(any())).thenReturn(List.of(
                feature(reportFeatureId, reportsModuleId, "REPORTS", "Reports", "REPORT_OCR", "Report OCR"),
                feature(aiFeatureId, aiModuleId, "AI_COPILOT", "AI Copilot", "AIVA_VOICE", "Aiva Voice")
        ));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ValidatePlanDraftResponse response = service.validateDraft(templateId);

        assertThat(response.messages()).extracting(ValidationMessageResponse::code).containsExactlyInAnyOrder(
                "FEATURE_PARENT_MODULE_REQUIRED",
                "FEATURE_PARENT_MODULE_REQUIRED"
        );
        assertThat(response.messages()).extracting(ValidationMessageResponse::affectedItemCode).containsExactlyInAnyOrder("REPORT_OCR", "AIVA_VOICE");
        assertThat(response.messages()).extracting(ValidationMessageResponse::expectedItemCode).containsExactlyInAnyOrder("REPORTS", "AI_COPILOT");
        assertThat(response.messages()).allMatch(message -> "modules".equals(message.targetBuilderTab()));
    }

    @Test
    void validateDraftAllowsFeatureWhenParentModuleIncluded() {
        UUID templateId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID reportsModuleId = UUID.randomUUID();
        UUID reportFeatureId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "BASIC_CLINIC",
                "Basic Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.CUSTOM,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                1,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                serialize(new PlanConfigurationSnapshot(
                        "BASIC_CLINIC",
                        "Basic Clinic",
                        null,
                        CommercialPlatformEnums.TargetSegment.CUSTOM,
                        CommercialPlatformEnums.TemplateStatus.DRAFT,
                        1,
                        List.of(new SelectedCapability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core", "Core clinical packaging", 1, false)),
                        List.of(new SelectedModule(reportsModuleId, "REPORTS", "Reports", "Reporting module", "REPORTS", 1, false, CommercialPlatformEnums.SelectionSource.EXPLICIT, false)),
                        List.of(new SelectedFeature(reportFeatureId, "REPORT_OCR", "Report OCR", "OCR extraction", reportsModuleId, "REPORTS", "Reports", 1, false)),
                        List.of(),
                        List.of()
                )),
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.READY_TO_PUBLISH,
                true,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.of(draft));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of(capability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core")));
        when(moduleRepository.findAllById(any())).thenReturn(List.of(module(reportsModuleId, "REPORTS", "Reports", "REPORTS")));
        when(limitRepository.findAllById(any())).thenReturn(List.of());
        when(addonRepository.findAllById(any())).thenReturn(List.of());
        when(featureRepository.findAllById(any())).thenReturn(List.of(feature(reportFeatureId, reportsModuleId, "REPORTS", "Reports", "REPORT_OCR", "Report OCR")));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ValidatePlanDraftResponse response = service.validateDraft(templateId);

        assertThat(response.messages()).extracting(ValidationMessageResponse::code).doesNotContain("FEATURE_PARENT_MODULE_REQUIRED");
    }

    @Test
    void validateDraftDoesNotReportFeatureDependencyWhenFeatureRemoved() {
        UUID templateId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "BASIC_CLINIC",
                "Basic Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.CUSTOM,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                1,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                serialize(new PlanConfigurationSnapshot(
                        "BASIC_CLINIC",
                        "Basic Clinic",
                        null,
                        CommercialPlatformEnums.TargetSegment.CUSTOM,
                        CommercialPlatformEnums.TemplateStatus.DRAFT,
                        1,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.BLOCKED,
                false,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.of(draft));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of());
        when(moduleRepository.findAllById(any())).thenReturn(List.of());
        when(limitRepository.findAllById(any())).thenReturn(List.of());
        when(addonRepository.findAllById(any())).thenReturn(List.of());
        when(featureRepository.findAllById(any())).thenReturn(List.of());
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ValidatePlanDraftResponse response = service.validateDraft(templateId);

        assertThat(response.messages()).extracting(ValidationMessageResponse::code).doesNotContain("FEATURE_PARENT_MODULE_REQUIRED");
    }

    @Test
    void createTemplateSeedsBlockingValidationForEmptyDraft() {
        when(templateRepository.existsByCodeIgnoreCase("SOLO_CLINIC")).thenReturn(false);
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.findByTemplate_Id(any())).thenReturn(Optional.empty());
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of());
        when(moduleRepository.findAllById(any())).thenReturn(List.of());
        when(featureRepository.findAllById(any())).thenReturn(List.of());
        when(limitRepository.findAllById(any())).thenReturn(List.of());
        when(addonRepository.findAllById(any())).thenReturn(List.of());

        TemplateDetailResponse response = service.createTemplate(new CreatePlanTemplateRequest(
                "SOLO_CLINIC",
                "Solo Clinic",
                "Single doctor clinic",
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                0
        ));

        assertThat(response.validation().validationState()).isEqualTo(ValidationState.NOT_VALIDATED);
        assertThat(response.validation().readyToPublish()).isFalse();
        assertThat(response.validation().blockingFindingCount()).isEqualTo(2);
        assertThat(response.validation().findings()).extracting(ValidationMessageResponse::code)
                .contains("PLAN_CAPABILITY_REQUIRED", "PLAN_MODULE_REQUIRED");
    }

    @Test
    void cloneTemplateCopiesSourceSnapshotAndStartsUnvalidated() {
        UUID sourceTemplateId = UUID.randomUUID();
        UUID sourceVersionId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        UUID addonId = UUID.randomUUID();
        CommercialPlanTemplateEntity sourceTemplate = CommercialPlanTemplateEntity.create(
                sourceTemplateId,
                "SOLO_CLINIC",
                "Solo Clinic",
                "Commercial package for a solo clinic",
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.ACTIVE,
                10,
                OffsetDateTime.parse("2026-07-23T00:00:00Z"),
                actorId
        );
        CommercialPlanVersionEntity sourceVersion = CommercialPlanVersionEntity.create(
                sourceTemplate,
                2,
                "v2",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId,
                "Published source",
                2,
                "source-hash",
                serialize(new PlanConfigurationSnapshot(
                        "SOLO_CLINIC",
                        "Solo Clinic",
                        "Commercial package for a solo clinic",
                        CommercialPlatformEnums.TargetSegment.SOLO,
                        CommercialPlatformEnums.TemplateStatus.ACTIVE,
                        10,
                        List.of(new SelectedCapability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core", "Core clinical packaging", 1, false)),
                        List.of(new SelectedModule(moduleId, "APPOINTMENTS", "Appointments", "OPD appointment scheduling", "APPOINTMENTS", 1, false, CommercialPlatformEnums.SelectionSource.EXPLICIT, false)),
                        List.of(),
                        List.of(new SelectedLimit(limitId, "MAX_DOCTORS", "Maximum Doctors", "Max doctors", "count", LimitValueType.INTEGER, AggregationPeriod.MONTHLY, EnforcementMode.SOFT, "10", 1, false)),
                        List.of(new SelectedAddon(addonId, "PHARMACY_ADDON", "Pharmacy Add-on", "Pharmacy packaging", AddonType.CAPABILITY, 1, CommercialPlatformEnums.SelectionState.INCLUDED, false))
                )),
                1,
                1,
                0,
                1,
                1,
                actorId
        );

        when(templateRepository.findById(sourceTemplateId)).thenReturn(Optional.of(sourceTemplate));
        when(templateRepository.existsByCodeIgnoreCase("SOLO_CLINIC_PLUS")).thenReturn(false);
        when(versionRepository.findById(sourceVersionId)).thenReturn(Optional.of(sourceVersion));
        when(versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(sourceTemplateId)).thenReturn(Optional.of(sourceVersion));
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateDetailResponse cloned = service.cloneTemplate(sourceTemplateId, new com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.ClonePlanTemplateRequest(
                sourceTemplateId,
                sourceVersionId,
                "SOLO_CLINIC_PLUS",
                "Solo Clinic Plus",
                "Cloned from v2",
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                12
        ));

        assertThat(cloned.code()).isEqualTo("SOLO_CLINIC_PLUS");
        assertThat(cloned.name()).isEqualTo("Solo Clinic Plus");
        assertThat(cloned.draft().validation().validationState()).isEqualTo(ValidationState.NOT_VALIDATED);
        assertThat(cloned.draft().configuration().capabilities()).extracting("capabilityCode").containsExactly("HEALTHCARE_CORE");
        assertThat(cloned.draft().configuration().modules()).extracting("moduleCode").containsExactly("APPOINTMENTS");
        assertThat(cloned.draft().configuration().limits()).extracting("limitCode").containsExactly("MAX_DOCTORS");
        assertThat(cloned.draft().configuration().addons()).extracting("addonCode").containsExactly("PHARMACY_ADDON");
    }

    @Test
    void createTemplateRejectsDuplicateCode() {
        when(templateRepository.existsByCodeIgnoreCase("SOLO_CLINIC")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(new CreatePlanTemplateRequest(
                "SOLO_CLINIC",
                "Solo Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                0
        ))).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("code already exists");
        verify(templateRepository, never()).save(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void updateTemplateKeepsImmutableCode() {
        UUID templateId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "SOLO_CLINIC",
                "Solo Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                0,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.empty());
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of());
        when(moduleRepository.findAllById(any())).thenReturn(List.of());
        when(featureRepository.findAllById(any())).thenReturn(List.of());
        when(limitRepository.findAllById(any())).thenReturn(List.of());
        when(addonRepository.findAllById(any())).thenReturn(List.of());

        TemplateDetailResponse response = service.updateTemplate(templateId, new com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.UpdatePlanTemplateRequest(
                "Solo Clinic Updated",
                "Updated description",
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.ACTIVE,
                1
        ));

        assertThat(response.code()).isEqualTo("SOLO_CLINIC");
        assertThat(response.name()).isEqualTo("Solo Clinic Updated");
    }

    @Test
    void publishVersionRejectsUnvalidatedDraft() {
        UUID templateId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "SOLO_CLINIC",
                "Solo Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                0,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                serialize(new PlanConfigurationSnapshot("SOLO_CLINIC", "Solo Clinic", null, CommercialPlatformEnums.TargetSegment.SOLO, CommercialPlatformEnums.TemplateStatus.DRAFT, 0, List.of(), List.of(), List.of(), List.of(), List.of())),
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.BLOCKED,
                false,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.of(draft));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of());
        when(moduleRepository.findAllById(any())).thenReturn(List.of());
        when(featureRepository.findAllById(any())).thenReturn(List.of());
        when(limitRepository.findAllById(any())).thenReturn(List.of());
        when(addonRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.publishVersion(templateId, new PublishPlanVersionRequest("Publish")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("validated and free of blocking findings");
    }

    @Test
    void saveDraftMarksPreviouslyValidatedDraftAsStale() {
        UUID templateId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID limitId = UUID.randomUUID();
        UUID addonId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "BASIC_CLINIC",
                "Basic Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.SMALL_CLINIC,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                1,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                serialize(snapshot(capabilityId, moduleId, limitId, addonId)),
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.READY_TO_PUBLISH,
                true,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        setField(draft, "lastValidatedAt", OffsetDateTime.parse("2026-07-23T00:00:00Z"));
        setField(draft, "validationStatus", "READY");

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.of(draft));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(capabilityRepository.findAllById(any())).thenReturn(List.of(capability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core")));
        when(moduleRepository.findAllById(any())).thenReturn(List.of(module(moduleId, "APPOINTMENTS", "Appointments", "APPOINTMENTS")));
        when(limitRepository.findAllById(any())).thenReturn(List.of(limit(limitId, "MAX_DOCTORS", "Maximum Doctors")));
        when(addonRepository.findAllById(any())).thenReturn(List.of(addon(addonId, "PHARMACY_ADDON", "Pharmacy Add-on")));

        PlanDraftResponse response = service.saveDraft(templateId, new SavePlanDraftRequest(
                "Updated",
                List.of(new SelectedCapabilityRequest(capabilityId)),
                List.of(new SelectedModuleRequest(moduleId, CommercialPlatformEnums.SelectionSource.EXPLICIT, false, 1)),
                List.of(),
                List.of(new ConfiguredLimitRequest(limitId, "10")),
                List.of(new SelectedAddonRequest(addonId, CommercialPlatformEnums.SelectionState.INCLUDED))
        ));

        assertThat(response.validation().validationState()).isEqualTo(ValidationState.STALE);
        assertThat(response.validation().readyToPublish()).isFalse();
    }

    @Test
    void compareVersionsIncludesMetadataChanges() {
        UUID templateId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                templateId,
                "SOLO_CLINIC",
                "Solo Clinic",
                "Commercial package for a solo clinic",
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.ACTIVE,
                10,
                OffsetDateTime.parse("2026-07-23T00:00:00Z"),
                actorId
        );
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                2,
                serialize(new PlanConfigurationSnapshot(
                        "SOLO_CLINIC",
                        "Solo Clinic Plus",
                        "Updated solo clinic package",
                        CommercialPlatformEnums.TargetSegment.SOLO,
                        CommercialPlatformEnums.TemplateStatus.ACTIVE,
                        12,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.READY_TO_PUBLISH,
                true,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        setField(draft, "lastValidatedAt", OffsetDateTime.parse("2026-07-24T00:00:00Z"));
        setField(draft, "validationStatus", "READY");
        CommercialPlanVersionEntity left = CommercialPlanVersionEntity.create(
                template,
                1,
                "v1",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-23T00:00:00Z"),
                actorId,
                "Initial publish",
                1,
                "left-hash",
                serialize(new PlanConfigurationSnapshot(
                        "SOLO_CLINIC",
                        "Solo Clinic",
                        "Commercial package for a solo clinic",
                        CommercialPlatformEnums.TargetSegment.SOLO,
                        CommercialPlatformEnums.TemplateStatus.ACTIVE,
                        10,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                0,
                0,
                0,
                0,
                0,
                actorId
        );
        CommercialPlanVersionEntity right = CommercialPlanVersionEntity.create(
                template,
                2,
                "v2",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId,
                "Second publish",
                2,
                "right-hash",
                serialize(new PlanConfigurationSnapshot(
                        "SOLO_CLINIC",
                        "Solo Clinic Plus",
                        "Updated solo clinic package",
                        CommercialPlatformEnums.TargetSegment.SOLO,
                        CommercialPlatformEnums.TemplateStatus.ACTIVE,
                        12,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                0,
                0,
                0,
                0,
                0,
                actorId
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(templateId)).thenReturn(Optional.of(draft));
        when(versionRepository.findById(left.getId())).thenReturn(Optional.of(left));
        when(versionRepository.findById(right.getId())).thenReturn(Optional.of(right));

        com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.CompareVersionsResponse response = service.compareVersions(templateId, left.getId(), right.getId());

        assertThat(response.metadata().changed()).extracting("code").contains("name", "description", "displayOrder");
    }

    private PlanConfigurationSnapshot snapshot(UUID capabilityId, UUID moduleId, UUID limitId, UUID addonId) {
        return new PlanConfigurationSnapshot(
                "BASIC_CLINIC",
                "Basic Clinic",
                "Commercial package for a small clinic",
                CommercialPlatformEnums.TargetSegment.SMALL_CLINIC,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                1,
                List.of(new SelectedCapability(capabilityId, "HEALTHCARE_CORE", "Healthcare Core", "Core clinical packaging", 1, false)),
                List.of(new SelectedModule(moduleId, "APPOINTMENTS", "Appointments", "OPD appointment scheduling", "APPOINTMENTS", 1, false, CommercialPlatformEnums.SelectionSource.EXPLICIT, false)),
                List.of(),
                List.of(new SelectedLimit(limitId, "MAX_DOCTORS", "Maximum Doctors", "Max doctors", "count", LimitValueType.INTEGER, AggregationPeriod.MONTHLY, EnforcementMode.SOFT, "10", 1, false)),
                List.of(new SelectedAddon(addonId, "PHARMACY_ADDON", "Pharmacy Add-on", "Pharmacy packaging", AddonType.CAPABILITY, 1, CommercialPlatformEnums.SelectionState.INCLUDED, false))
        );
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private CommercialCapabilityEntity capability(UUID id, String code, String name) {
        CommercialCapabilityEntity entity = new CommercialCapabilityEntity();
        setField(entity, "id", id);
        setField(entity, "code", code);
        setField(entity, "name", name);
        setField(entity, "status", Status.ACTIVE);
        setField(entity, "displayOrder", 1);
        setField(entity, "standaloneAllowed", true);
        setField(entity, "addonAllowed", true);
        return entity;
    }

    private CommercialModuleEntity module(UUID id, String code, String name, String runtimeModuleCode) {
        CommercialModuleEntity entity = new CommercialModuleEntity();
        setField(entity, "id", id);
        setField(entity, "code", code);
        setField(entity, "name", name);
        setField(entity, "status", Status.ACTIVE);
        setField(entity, "displayOrder", 1);
        setField(entity, "runtimeModuleCode", runtimeModuleCode);
        return entity;
    }

    private CommercialFeatureEntity feature(UUID featureId, UUID moduleId, String code, String name) {
        return feature(featureId, moduleId, "REPORTS", "Reports", code, name);
    }

    private CommercialFeatureEntity feature(UUID featureId, UUID moduleId, String moduleCode, String moduleName, String code, String name) {
        CommercialFeatureEntity entity = new CommercialFeatureEntity();
        setField(entity, "id", featureId);
        setField(entity, "code", code);
        setField(entity, "name", name);
        setField(entity, "status", Status.ACTIVE);
        setField(entity, "displayOrder", 1);
        CommercialModuleEntity module = new CommercialModuleEntity();
        setField(module, "id", moduleId);
        setField(module, "code", moduleCode);
        setField(module, "name", moduleName);
        setField(module, "status", Status.ACTIVE);
        setField(module, "displayOrder", 1);
        setField(module, "runtimeModuleCode", moduleCode);
        setField(entity, "module", module);
        return entity;
    }

    private CommercialLimitDefinitionEntity limit(UUID id, String code, String name) {
        CommercialLimitDefinitionEntity entity = new CommercialLimitDefinitionEntity();
        setField(entity, "id", id);
        setField(entity, "code", code);
        setField(entity, "name", name);
        setField(entity, "status", Status.ACTIVE);
        setField(entity, "displayOrder", 1);
        setField(entity, "unit", "count");
        setField(entity, "valueType", LimitValueType.INTEGER);
        setField(entity, "aggregationPeriod", AggregationPeriod.MONTHLY);
        setField(entity, "enforcementMode", EnforcementMode.SOFT);
        return entity;
    }

    private CommercialAddonOfferEntity addon(UUID id, String code, String name) {
        CommercialAddonOfferEntity entity = new CommercialAddonOfferEntity();
        setField(entity, "id", id);
        setField(entity, "code", code);
        setField(entity, "name", name);
        setField(entity, "status", Status.ACTIVE);
        setField(entity, "addonType", AddonType.CAPABILITY);
        setField(entity, "displayOrder", 1);
        setField(entity, "repeatable", false);
        return entity;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
