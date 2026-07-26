package com.deepthoughtnet.clinic.commercial.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionRepository;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.AddonPurchaseType;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.BillingCycle;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.PricingStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.TaxModel;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanConfigurationSnapshot;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanPricingAddonResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanPricingMeteredRateResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanPricingResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanPricingSnapshot;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PricingComparisonResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PricingComparisonEntry;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanAddonPricingEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanAddonPricingRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanDraftEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanDraftRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanMeteredRateEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanMeteredRateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanPricingEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanPricingRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPricingHistoryEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPricingHistoryRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class CommercialPricingServiceTest {
    private CommercialPlanTemplateRepository templateRepository;
    private CommercialPlanDraftRepository draftRepository;
    private CommercialPlanVersionRepository versionRepository;
    private CommercialPlanPricingRepository pricingRepository;
    private CommercialPlanMeteredRateRepository meteredRateRepository;
    private CommercialPlanAddonPricingRepository addonPricingRepository;
    private CommercialPricingHistoryRepository historyRepository;
    private CommercialLimitDefinitionRepository limitRepository;
    private CommercialAddonOfferRepository addonRepository;
    private AuditEventPublisher auditEventPublisher;
    private CommercialPricingService service;
    private CommercialPlanTemplateEntity template;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        templateRepository = mock(CommercialPlanTemplateRepository.class);
        draftRepository = mock(CommercialPlanDraftRepository.class);
        versionRepository = mock(CommercialPlanVersionRepository.class);
        pricingRepository = mock(CommercialPlanPricingRepository.class);
        meteredRateRepository = mock(CommercialPlanMeteredRateRepository.class);
        addonPricingRepository = mock(CommercialPlanAddonPricingRepository.class);
        historyRepository = mock(CommercialPricingHistoryRepository.class);
        limitRepository = mock(CommercialLimitDefinitionRepository.class);
        addonRepository = mock(CommercialAddonOfferRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        service = new CommercialPricingService(
                templateRepository,
                draftRepository,
                versionRepository,
                pricingRepository,
                meteredRateRepository,
                addonPricingRepository,
                historyRepository,
                limitRepository,
                addonRepository,
                auditEventPublisher,
                new ObjectMapper().findAndRegisterModules()
        );
        actorId = UUID.randomUUID();
        template = CommercialPlanTemplateEntity.create(
                UUID.randomUUID(),
                "SOLO_CLINIC",
                "Solo Clinic",
                null,
                CommercialPlatformEnums.TargetSegment.SOLO,
                CommercialPlatformEnums.TemplateStatus.DRAFT,
                1,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), actorId, "platform.admin@jeeva.test", Set.of("PLATFORM_ADMIN"), "PLATFORM_ADMIN", "commercial-pricing-test"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void validatePricingConfigurationFlagsInvalidPricingFields() {
        PlanPricingSnapshot pricing = new PlanPricingSnapshot(
                null,
                null,
                "0",
                "99999",
                "-1",
                400,
                null,
                null,
                false,
                List.of(),
                List.of()
        );

        List<com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.ValidationMessageResponse> findings =
                service.validatePricingConfiguration(template, pricing, 3, false);

        assertThat(findings).extracting(com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.ValidationMessageResponse::code)
                .contains(
                        "MISSING_BILLING_CYCLE",
                        "MISSING_CURRENCY",
                        "MISSING_TAX_MODEL",
                        "INVALID_MONTHLY_PRICE",
                        "INVALID_ANNUAL_PRICE",
                        "INVALID_TRIAL_DAYS"
                );
    }

    @Test
    void freezePricingPersistsPublishedPricingSnapshotAndHistory() {
        CommercialPlanVersionEntity publishedVersion = CommercialPlanVersionEntity.create(
                template,
                1,
                "v1",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId,
                "Initial publish",
                1,
                "content-hash",
                "{}",
                0,
                0,
                0,
                0,
                0,
                actorId
        );
        CommercialLimitDefinitionEntity limit = mock(CommercialLimitDefinitionEntity.class);
        when(limit.getId()).thenReturn(UUID.randomUUID());
        when(limit.getCode()).thenReturn("MAX_DOCTORS");
        when(limit.getName()).thenReturn("Maximum Doctors");
        CommercialAddonOfferEntity addon = mock(CommercialAddonOfferEntity.class);
        when(addon.getId()).thenReturn(UUID.randomUUID());
        when(addon.getCode()).thenReturn("AI_COPILOT");
        when(addon.getName()).thenReturn("AI Copilot");
        when(limitRepository.findById(any())).thenReturn(Optional.of(limit));
        when(addonRepository.findById(any())).thenReturn(Optional.of(addon));

        PlanPricingSnapshot pricing = new PlanPricingSnapshot(
                "INR",
                BillingCycle.MONTHLY,
                "499.00",
                "4999.00",
                "0",
                14,
                TaxModel.EXCLUSIVE,
                "18",
                true,
                List.of(new PlanPricingMeteredRateResponse(UUID.randomUUID(), limit.getId(), limit.getCode(), limit.getName(), "100", true, "1.50", "per doctor", "HALF_UP", PricingStatus.PUBLISHED)),
                List.of(new PlanPricingAddonResponse(UUID.randomUUID(), addon.getId(), addon.getCode(), addon.getName(), AddonPurchaseType.MONTHLY, "199.00", "1999.00", "0", 5, PricingStatus.PUBLISHED))
        );

        service.freezePricing(publishedVersion, pricing, OffsetDateTime.parse("2026-07-24T00:00:00Z"), actorId);

        ArgumentCaptor<CommercialPlanPricingEntity> pricingCaptor = ArgumentCaptor.forClass(CommercialPlanPricingEntity.class);
        verify(pricingRepository).save(pricingCaptor.capture());
        assertThat(pricingCaptor.getValue().getPublishedVersion()).isSameAs(publishedVersion);
        assertThat(pricingCaptor.getValue().getCurrency()).isEqualTo("INR");
        assertThat(pricingCaptor.getValue().getMonthlyPrice()).isEqualByComparingTo("499.00");

        verify(meteredRateRepository).save(any(CommercialPlanMeteredRateEntity.class));
        verify(addonPricingRepository).save(any(CommercialPlanAddonPricingEntity.class));

        ArgumentCaptor<CommercialPricingHistoryEntity> historyCaptor = ArgumentCaptor.forClass(CommercialPricingHistoryEntity.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPublishedVersion()).isSameAs(publishedVersion);
        assertThat(historyCaptor.getValue().getSnapshotJson()).contains("\"currency\":\"INR\"");
        assertThat(pricingCaptor.getValue().getTrialDays()).isEqualTo(14);
    }

    @Test
    void freezePricingPreservesNullTrialDays() {
        CommercialPlanVersionEntity publishedVersion = CommercialPlanVersionEntity.create(
                template,
                1,
                "v1",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId,
                "Initial publish",
                1,
                "content-hash",
                "{}",
                0,
                0,
                0,
                0,
                0,
                actorId
        );

        PlanPricingSnapshot pricing = new PlanPricingSnapshot(
                "INR",
                BillingCycle.MONTHLY,
                "499.00",
                "4999.00",
                "0",
                null,
                TaxModel.EXCLUSIVE,
                "18",
                true,
                List.of(),
                List.of()
        );

        service.freezePricing(publishedVersion, pricing, OffsetDateTime.parse("2026-07-24T00:00:00Z"), actorId);

        ArgumentCaptor<CommercialPlanPricingEntity> pricingCaptor = ArgumentCaptor.forClass(CommercialPlanPricingEntity.class);
        verify(pricingRepository).save(pricingCaptor.capture());
        assertThat(pricingCaptor.getValue().getTrialDays()).isNull();
    }

    @Test
    void freezePricingPreservesNullOptionalMoneyFields() {
        CommercialPlanVersionEntity publishedVersion = CommercialPlanVersionEntity.create(
                template,
                1,
                "v1",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId,
                "Initial publish",
                1,
                "content-hash",
                "{}",
                0,
                0,
                0,
                0,
                0,
                actorId
        );

        PlanPricingSnapshot pricing = new PlanPricingSnapshot(
                "INR",
                BillingCycle.MONTHLY,
                "499.00",
                "4999.00",
                null,
                14,
                TaxModel.EXCLUSIVE,
                null,
                true,
                List.of(),
                List.of()
        );

        service.freezePricing(publishedVersion, pricing, OffsetDateTime.parse("2026-07-24T00:00:00Z"), actorId);

        ArgumentCaptor<CommercialPlanPricingEntity> pricingCaptor = ArgumentCaptor.forClass(CommercialPlanPricingEntity.class);
        verify(pricingRepository).save(pricingCaptor.capture());
        assertThat(pricingCaptor.getValue().getSetupFee()).isNull();
        assertThat(pricingCaptor.getValue().getTaxPercentage()).isNull();
    }

    @Test
    void getPricingReturnsNullTrialDaysWithoutThrowing() throws Exception {
        PlanPricingSnapshot pricing = new PlanPricingSnapshot(
                "INR",
                BillingCycle.MONTHLY,
                "499.00",
                "4999.00",
                "0",
                null,
                TaxModel.EXCLUSIVE,
                "18",
                true,
                List.of(),
                List.of()
        );
        String configJson = snapshotJson(pricing);
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                configJson,
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.DRAFT,
                false,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(template.getId())).thenReturn(Optional.of(draft));

        PlanPricingResponse response = service.getPricing(template.getId());

        assertThat(response.trialDays()).isNull();
        assertThat(response.monthlyPrice()).isEqualTo("499.00");
    }

    @Test
    void getPricingReturnsNullOptionalMoneyFieldsWithoutThrowing() throws Exception {
        PlanPricingSnapshot pricing = new PlanPricingSnapshot(
                "INR",
                BillingCycle.MONTHLY,
                "499.00",
                "4999.00",
                null,
                null,
                TaxModel.EXCLUSIVE,
                null,
                true,
                List.of(),
                List.of()
        );
        String configJson = snapshotJson(pricing);
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                1,
                configJson,
                "draft-hash",
                "[]",
                CommercialPlatformEnums.DraftStatus.DRAFT,
                false,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(draftRepository.findByTemplate_Id(template.getId())).thenReturn(Optional.of(draft));

        PlanPricingResponse response = service.getPricing(template.getId());

        assertThat(response.setupFee()).isNull();
        assertThat(response.taxPercentage()).isNull();
    }

    @Test
    void planPricingEntityCanRepresentNullTrialDays() {
        CommercialPlanVersionEntity publishedVersion = CommercialPlanVersionEntity.create(
                template,
                1,
                "v1",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId,
                "Initial publish",
                1,
                "content-hash",
                "{}",
                0,
                0,
                0,
                0,
                0,
                actorId
        );

        CommercialPlanPricingEntity entity = CommercialPlanPricingEntity.create(
                UUID.randomUUID(),
                publishedVersion,
                "INR",
                BillingCycle.MONTHLY,
                new BigDecimal("499.00"),
                new BigDecimal("4999.00"),
                new BigDecimal("0"),
                null,
                TaxModel.EXCLUSIVE,
                new BigDecimal("18"),
                true,
                PricingStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );

        assertThat(entity.getTrialDays()).isNull();
    }

    @Test
    void comparePricingHighlightsDifferencesBetweenVersions() {
        CommercialPlanVersionEntity left = CommercialPlanVersionEntity.create(
                template,
                1,
                "v1",
                CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId,
                "Initial publish",
                1,
                "left-hash",
                snapshotJson(new PlanPricingSnapshot(
                        "INR",
                        BillingCycle.MONTHLY,
                        "499.00",
                        "4999.00",
                        "0",
                        14,
                        TaxModel.EXCLUSIVE,
                        "18",
                        true,
                        List.of(new PlanPricingMeteredRateResponse(UUID.randomUUID(), UUID.randomUUID(), "MAX_DOCTORS", "Maximum Doctors", "100", true, "1.50", "per doctor", "HALF_UP", PricingStatus.PUBLISHED)),
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
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                actorId,
                "Price update",
                2,
                "right-hash",
                snapshotJson(new PlanPricingSnapshot(
                        "USD",
                        BillingCycle.ANNUAL,
                        "599.00",
                        "5999.00",
                        "99.00",
                        30,
                        TaxModel.INCLUSIVE,
                        "12",
                        false,
                        List.of(new PlanPricingMeteredRateResponse(UUID.randomUUID(), UUID.randomUUID(), "MAX_DOCTORS", "Maximum Doctors", "250", true, "2.00", "per doctor", "HALF_UP", PricingStatus.PUBLISHED)),
                        List.of(new PlanPricingAddonResponse(UUID.randomUUID(), UUID.randomUUID(), "AI_COPILOT", "AI Copilot", AddonPurchaseType.MONTHLY, "299.00", "2999.00", "0", 2, PricingStatus.PUBLISHED))
                )),
                0,
                0,
                0,
                0,
                0,
                actorId
        );
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(versionRepository.findById(left.getId())).thenReturn(Optional.of(left));
        when(versionRepository.findById(right.getId())).thenReturn(Optional.of(right));

        PricingComparisonResponse comparison = service.comparePricing(template.getId(), left.getId(), right.getId());

        assertThat(comparison.leftLabel()).isEqualTo("v1");
        assertThat(comparison.rightLabel()).isEqualTo("v2");
        assertThat(comparison.subscriptionPricing()).extracting(PricingComparisonEntry::code)
                .contains("monthly", "annual", "currency", "trial", "setup", "tax", "taxPercentage");
        assertThat(comparison.meteredRates()).isNotEmpty();
        assertThat(comparison.addonPricing()).isNotEmpty();
    }

    private String snapshotJson(PlanPricingSnapshot pricing) {
        try {
            return new ObjectMapper().findAndRegisterModules().writeValueAsString(new PlanConfigurationSnapshot(
                    "SOLO_CLINIC",
                    "Solo Clinic",
                    null,
                    CommercialPlatformEnums.TargetSegment.SOLO,
                    CommercialPlatformEnums.TemplateStatus.DRAFT,
                    1,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    pricing
            ));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
