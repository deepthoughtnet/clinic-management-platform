package com.deepthoughtnet.clinic.commercial.platform;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionRepository;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.DraftStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationSeverity;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationState;
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
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PricingValidationResultResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.ValidationMessageResponse;
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
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventAction;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommercialPricingService {
    private static final UUID PLATFORM_AUDIT_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final CommercialPlanTemplateRepository templateRepository;
    private final CommercialPlanDraftRepository draftRepository;
    private final CommercialPlanVersionRepository versionRepository;
    private final CommercialPlanPricingRepository pricingRepository;
    private final CommercialPlanMeteredRateRepository meteredRateRepository;
    private final CommercialPlanAddonPricingRepository addonPricingRepository;
    private final CommercialPricingHistoryRepository historyRepository;
    private final CommercialLimitDefinitionRepository limitRepository;
    private final CommercialAddonOfferRepository addonRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public CommercialPricingService(
            CommercialPlanTemplateRepository templateRepository,
            CommercialPlanDraftRepository draftRepository,
            CommercialPlanVersionRepository versionRepository,
            CommercialPlanPricingRepository pricingRepository,
            CommercialPlanMeteredRateRepository meteredRateRepository,
            CommercialPlanAddonPricingRepository addonPricingRepository,
            CommercialPricingHistoryRepository historyRepository,
            CommercialLimitDefinitionRepository limitRepository,
            CommercialAddonOfferRepository addonRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.templateRepository = templateRepository;
        this.draftRepository = draftRepository;
        this.versionRepository = versionRepository;
        this.pricingRepository = pricingRepository;
        this.meteredRateRepository = meteredRateRepository;
        this.addonPricingRepository = addonPricingRepository;
        this.historyRepository = historyRepository;
        this.limitRepository = limitRepository;
        this.addonRepository = addonRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PlanPricingResponse getPricing(UUID templateId) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        PlanConfigurationSnapshot snapshot = parseSnapshot(draft.getConfigJson());
        return toPricingResponse(null, snapshot.pricing());
    }

    @Transactional
    public PlanPricingResponse savePricing(UUID templateId, CommercialPlatformModels.SavePlanPricingRequest request) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        PlanConfigurationSnapshot current = parseSnapshot(draft.getConfigJson());
        PlanPricingSnapshot nextPricing = request == null ? current.pricing() : request.pricing();
        PlanConfigurationSnapshot nextSnapshot = new PlanConfigurationSnapshot(
                current.templateCode(),
                current.templateName(),
                current.templateDescription(),
                current.targetSegment(),
                current.templateStatus(),
                current.displayOrder(),
                current.capabilities(),
                current.modules(),
                current.features(),
                current.limits(),
                current.addons(),
                nextPricing
        );
        OffsetDateTime now = now();
        UUID actor = currentActor();
        template.incrementDraftRevision(now, actor);
        templateRepository.save(template);
        List<ValidationMessageResponse> findings = validatePricingConfiguration(template, nextPricing, template.getCurrentDraftRevision(), true);
        draft.update(
                template.getCurrentDraftRevision(),
                draft.getDraftNotes(),
                serialize(nextSnapshot),
                hash(serialize(nextSnapshot)),
                serialize(findings),
                DraftStatus.BLOCKED,
                false,
                now,
                actor
        );
        draftRepository.save(draft);
        audit(
                template.getId(),
                AuditEntityType.COMMERCIAL_PLAN_DRAFT,
                AuditEventAction.COMMERCIAL_PLAN_DRAFT_SAVED,
                "Saved commercial plan pricing",
                Map.of("code", template.getCode(), "revision", draft.getRevision(), "billingCycle", nextPricing == null ? null : nextPricing.billingCycle())
        );
        return toPricingResponse(null, nextPricing);
    }

    @Transactional(readOnly = true)
    public PricingValidationResultResponse validatePricing(UUID templateId) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        PlanPricingSnapshot pricing = parseSnapshot(draft.getConfigJson()).pricing();
        List<ValidationMessageResponse> findings = validatePricingConfiguration(template, pricing, draft.getRevision(), false);
        int blockingCount = (int) findings.stream().filter(ValidationMessageResponse::blocking).count();
        int warningCount = (int) findings.stream().filter(message -> message.severity() == ValidationSeverity.WARNING).count();
        return new PricingValidationResultResponse(blockingCount == 0 ? ValidationState.VALID : ValidationState.INVALID, blockingCount == 0, blockingCount, warningCount, findings, draft.getRevision(), now());
    }

    @Transactional(readOnly = true)
    public PricingComparisonResponse comparePricing(UUID templateId, UUID leftVersionId, UUID rightVersionId) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanVersionEntity left = leftVersionId == null ? null : versionRepository.findById(leftVersionId).orElseThrow(() -> notFound("Plan version", leftVersionId));
        CommercialPlanVersionEntity right = rightVersionId == null ? null : versionRepository.findById(rightVersionId).orElseThrow(() -> notFound("Plan version", rightVersionId));
        PlanPricingSnapshot leftPricing = left == null ? parseSnapshot(ensureDraft(template).getConfigJson()).pricing() : parseSnapshot(left.getSnapshotJson()).pricing();
        PlanPricingSnapshot rightPricing = right == null ? parseSnapshot(ensureDraft(template).getConfigJson()).pricing() : parseSnapshot(right.getSnapshotJson()).pricing();
        return new PricingComparisonResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                left == null ? "Current Draft" : left.getVersionLabel(),
                right == null ? "Current Draft" : right.getVersionLabel(),
                compareSubscriptionPricing(leftPricing, rightPricing),
                compareMeteredPricing(leftPricing, rightPricing),
                compareAddonPricing(leftPricing, rightPricing)
        );
    }

    @Transactional
    public void freezePricing(CommercialPlanVersionEntity publishedVersion, PlanPricingSnapshot pricing, OffsetDateTime now, UUID actor) {
        if (pricing == null) {
            return;
        }
        CommercialPlanPricingEntity pricingEntity = CommercialPlanPricingEntity.create(
                UUID.randomUUID(),
                publishedVersion,
                normalizeCurrency(pricing.currency()),
                pricing.billingCycle() == null ? BillingCycle.MONTHLY : pricing.billingCycle(),
                parseMoney(pricing.monthlyPrice()),
                parseMoney(pricing.annualPrice()),
                parseOptionalMoney(pricing.setupFee()),
                pricing.trialDays(),
                pricing.taxModel() == null ? TaxModel.NONE : pricing.taxModel(),
                parseOptionalMoney(pricing.taxPercentage()),
                pricing.discountAllowed(),
                PricingStatus.PUBLISHED,
                now,
                actor
        );
        pricingRepository.save(pricingEntity);
        for (PlanPricingMeteredRateResponse rate : pricing.meteredRates() == null ? List.<PlanPricingMeteredRateResponse>of() : pricing.meteredRates()) {
            CommercialLimitDefinitionEntity limit = limitRepository.findById(rate.limitDefinitionId()).orElseThrow(() -> notFound("Limit definition", rate.limitDefinitionId()));
            meteredRateRepository.save(CommercialPlanMeteredRateEntity.create(
                    UUID.randomUUID(),
                    pricingEntity,
                    limit,
                    parseMoney(rate.includedQuantity()),
                    rate.overageEnabled(),
                    parseMoney(rate.unitPrice()),
                    requireText(rate.unitName(), "unit name is required"),
                    blankToNull(rate.billingRounding()),
                    PricingStatus.PUBLISHED,
                    now,
                    actor
            ));
        }
        for (PlanPricingAddonResponse addonPricing : pricing.addonPricing() == null ? List.<PlanPricingAddonResponse>of() : pricing.addonPricing()) {
            CommercialAddonOfferEntity addon = addonRepository.findById(addonPricing.addonOfferId()).orElseThrow(() -> notFound("Addon offer", addonPricing.addonOfferId()));
            addonPricingRepository.save(CommercialPlanAddonPricingEntity.create(
                    UUID.randomUUID(),
                    pricingEntity,
                    addon,
                    addonPricing.purchaseType() == null ? AddonPurchaseType.MONTHLY : addonPricing.purchaseType(),
                    parseMoney(addonPricing.monthlyPrice()),
                    parseMoney(addonPricing.annualPrice()),
                    parseMoney(addonPricing.oneTimePrice()),
                    addonPricing.maxQuantity(),
                    PricingStatus.PUBLISHED,
                    now,
                    actor
            ));
        }
        historyRepository.save(CommercialPricingHistoryEntity.create(
                UUID.randomUUID(),
                pricingEntity,
                publishedVersion,
                hash(serialize(pricing)),
                serialize(pricing),
                "Published pricing snapshot for version " + publishedVersion.getVersionNumber(),
                now,
                actor
        ));
    }

    @Transactional(readOnly = true)
    public PlanPricingResponse latestPricing(CommercialPlanTemplateEntity template) {
        CommercialPlanVersionEntity latestVersion = versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(template.getId()).orElse(null);
        if (latestVersion != null) {
            return getPublishedPricing(latestVersion.getId()).orElse(toPricingResponse(null, parseSnapshot(ensureDraft(template).getConfigJson()).pricing()));
        }
        return toPricingResponse(null, parseSnapshot(ensureDraft(template).getConfigJson()).pricing());
    }

    @Transactional(readOnly = true)
    public Optional<PlanPricingResponse> getPublishedPricing(UUID versionId) {
        CommercialPlanPricingEntity pricing = pricingRepository.findByPublishedVersion_Id(versionId).orElse(null);
        if (pricing == null) {
            CommercialPlanVersionEntity version = versionRepository.findById(versionId).orElse(null);
            if (version == null) {
                return Optional.empty();
            }
            return Optional.of(toPricingResponse(null, parseSnapshot(version.getSnapshotJson()).pricing()));
        }
        return Optional.of(toPricingResponse(pricing, null));
    }

    public List<ValidationMessageResponse> validatePricingConfiguration(CommercialPlanTemplateEntity template, PlanPricingSnapshot pricing, int revision, boolean publishing) {
        List<ValidationMessageResponse> findings = new ArrayList<>();
        if (pricing == null) {
            findings.add(finding("pricing", "MISSING_SUBSCRIPTION_PRICE", "Missing subscription price", "Configure pricing for the plan before publishing.", "Set monthly and annual pricing.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), "No pricing configured", "Pricing required", "pricing", "Configure Pricing"));
            return findings;
        }
        if (pricing.billingCycle() == null) {
            findings.add(finding("pricing.billingCycle", "MISSING_BILLING_CYCLE", "Missing billing cycle", "Choose how this plan is billed.", "Select monthly, annual, quarterly, one-time, or trial.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), "Billing cycle not set", "Billing cycle required", "pricing", "Configure Pricing"));
        }
        if (!StringUtils.hasText(pricing.currency())) {
            findings.add(finding("pricing.currency", "MISSING_CURRENCY", "Missing currency", "Choose a billing currency.", "Select INR, USD, or EUR.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), "Currency not set", "Currency required", "pricing", "Configure Pricing"));
        } else {
            String currency = pricing.currency().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("INR", "USD", "EUR").contains(currency)) {
                findings.add(finding("pricing.currency", "INVALID_CURRENCY", "Unsupported currency", "Commercial pricing currently supports INR, USD, and EUR.", "Choose INR, USD, or EUR.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), pricing.currency(), "INR, USD, or EUR", "pricing", "Configure Pricing"));
            }
        }
        if (pricing.taxModel() == null) {
            findings.add(finding("pricing.taxModel", "MISSING_TAX_MODEL", "Missing tax model", "Choose how tax should be applied.", "Select exclusive, inclusive, or none.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), "Tax model not set", "Tax model required", "pricing", "Configure Pricing"));
        }
        BigDecimal monthly = parseOptionalMoney(pricing.monthlyPrice());
        BigDecimal annual = parseOptionalMoney(pricing.annualPrice());
        BigDecimal setupFee = parseOptionalMoney(pricing.setupFee());
        BigDecimal taxPercentage = parseOptionalMoney(pricing.taxPercentage());
        if (monthly == null || monthly.compareTo(BigDecimal.ZERO) <= 0) {
            findings.add(finding("pricing.monthlyPrice", "INVALID_MONTHLY_PRICE", "Missing subscription price", "Monthly pricing must be a positive amount.", "Enter a positive monthly price.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), stringify(pricing.monthlyPrice()), "Positive amount", "pricing", "Configure Pricing"));
        }
        if (annual == null || annual.compareTo(BigDecimal.ZERO) <= 0) {
            findings.add(finding("pricing.annualPrice", "INVALID_ANNUAL_PRICE", "Invalid annual price", "Annual pricing must be a positive amount.", "Enter a positive annual price.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), stringify(pricing.annualPrice()), "Positive amount", "pricing", "Configure Pricing"));
        } else if (monthly != null && annual.compareTo(monthly.multiply(BigDecimal.valueOf(12))) > 0) {
            findings.add(finding("pricing.annualPrice", "INVALID_ANNUAL_PRICE", "Invalid annual price", "Annual pricing cannot exceed monthly pricing multiplied by 12.", "Reduce the annual price or increase the monthly price.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), stringify(pricing.annualPrice()), stringify(monthly.multiply(BigDecimal.valueOf(12))), "pricing", "Configure Pricing"));
        }
        if (setupFee != null && setupFee.compareTo(BigDecimal.ZERO) < 0) {
            findings.add(finding("pricing.setupFee", "INVALID_SETUP_FEE", "Invalid setup fee", "Setup fee cannot be negative.", "Enter zero or a positive setup fee.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), stringify(pricing.setupFee()), "Zero or positive", "pricing", "Configure Pricing"));
        }
        if (taxPercentage != null && taxPercentage.compareTo(BigDecimal.ZERO) < 0) {
            findings.add(finding("pricing.taxPercentage", "INVALID_TAX_PERCENTAGE", "Invalid tax percentage", "Tax percentage cannot be negative.", "Enter zero or a positive tax percentage.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), stringify(pricing.taxPercentage()), "Zero or positive", "pricing", "Configure Pricing"));
        }
        if (pricing.trialDays() != null && (pricing.trialDays() < 0 || pricing.trialDays() > 365)) {
            findings.add(finding("pricing.trialDays", "INVALID_TRIAL_DAYS", "Invalid trial period", "Trial days cannot exceed 365.", "Reduce the trial period.", ValidationSeverity.BLOCKING, true, "PRICING", "PLAN_TEMPLATE", template.getCode(), template.getName(), "PLAN_TEMPLATE", template.getCode(), template.getName(), String.valueOf(pricing.trialDays()), "365 or fewer", "pricing", "Configure Pricing"));
        }
        Map<UUID, Integer> addonCounts = new HashMap<>();
        for (PlanPricingAddonResponse addon : pricing.addonPricing() == null ? List.<PlanPricingAddonResponse>of() : pricing.addonPricing()) {
            if (addon.addonOfferId() == null) {
                findings.add(finding("pricing.addonPricing", "MISSING_ADDON", "Duplicate addon pricing", "Each add-on pricing row must reference an active catalog add-on.", "Select an active add-on offer.", ValidationSeverity.BLOCKING, true, "PRICING", "ADDON", null, null, "ADDON", null, null, "Unresolved add-on", "Catalog add-on required", "pricing", "Configure Pricing"));
                continue;
            }
            addonCounts.put(addon.addonOfferId(), addonCounts.getOrDefault(addon.addonOfferId(), 0) + 1);
            if (addonCounts.get(addon.addonOfferId()) > 1) {
                findings.add(finding("pricing.addonPricing", "DUPLICATE_ADDON_PRICING", "Duplicate addon pricing", "An add-on cannot have duplicate pricing entries.", "Remove the duplicate add-on pricing row.", ValidationSeverity.BLOCKING, true, "PRICING", "ADDON", String.valueOf(addon.addonOfferId()), null, "ADDON", String.valueOf(addon.addonOfferId()), null, "Duplicate pricing", "Unique pricing row", "pricing", "Configure Pricing"));
            }
            if (addon.purchaseType() == null) {
                findings.add(finding("pricing.addonPricing.purchaseType", "MISSING_ADDON_PURCHASE_TYPE", "Missing add-on purchase type", "Each add-on price must specify how it can be purchased.", "Choose monthly, annual, or one-time pricing.", ValidationSeverity.BLOCKING, true, "PRICING", "ADDON", String.valueOf(addon.addonOfferId()), null, "ADDON", String.valueOf(addon.addonOfferId()), null, "Purchase type not set", "Purchase type required", "pricing", "Configure Pricing"));
            }
            if (parseOptionalMoney(addon.monthlyPrice()) != null && parseOptionalMoney(addon.monthlyPrice()).compareTo(BigDecimal.ZERO) < 0) {
                findings.add(finding("pricing.addonPricing.monthly", "INVALID_ADDON_PRICING", "Invalid add-on pricing", "Addon monthly pricing must be positive.", "Enter a positive add-on monthly price.", ValidationSeverity.BLOCKING, true, "PRICING", "ADDON", String.valueOf(addon.addonOfferId()), null, "ADDON", String.valueOf(addon.addonOfferId()), null, stringify(addon.monthlyPrice()), "Positive amount", "pricing", "Configure Pricing"));
            }
            if (parseOptionalMoney(addon.annualPrice()) != null && parseOptionalMoney(addon.annualPrice()).compareTo(BigDecimal.ZERO) < 0) {
                findings.add(finding("pricing.addonPricing.annual", "INVALID_ADDON_PRICING", "Invalid add-on pricing", "Addon annual pricing must be positive.", "Enter a positive add-on annual price.", ValidationSeverity.BLOCKING, true, "PRICING", "ADDON", String.valueOf(addon.addonOfferId()), null, "ADDON", String.valueOf(addon.addonOfferId()), null, stringify(addon.annualPrice()), "Positive amount", "pricing", "Configure Pricing"));
            }
            if (parseOptionalMoney(addon.oneTimePrice()) != null && parseOptionalMoney(addon.oneTimePrice()).compareTo(BigDecimal.ZERO) < 0) {
                findings.add(finding("pricing.addonPricing.oneTime", "INVALID_ADDON_PRICING", "Invalid add-on pricing", "Addon one-time pricing must be positive.", "Enter a positive add-on one-time price.", ValidationSeverity.BLOCKING, true, "PRICING", "ADDON", String.valueOf(addon.addonOfferId()), null, "ADDON", String.valueOf(addon.addonOfferId()), null, stringify(addon.oneTimePrice()), "Positive amount", "pricing", "Configure Pricing"));
            }
            if (addon.maxQuantity() != null && addon.maxQuantity() < 0) {
                findings.add(finding("pricing.addonPricing.maxQuantity", "INVALID_ADDON_MAX_QUANTITY", "Invalid add-on quantity", "Add-on quantity cannot be negative.", "Enter zero or a positive quantity.", ValidationSeverity.BLOCKING, true, "PRICING", "ADDON", String.valueOf(addon.addonOfferId()), null, "ADDON", String.valueOf(addon.addonOfferId()), null, String.valueOf(addon.maxQuantity()), "Zero or positive", "pricing", "Configure Pricing"));
            }
        }
        for (PlanPricingMeteredRateResponse meteredRate : pricing.meteredRates() == null ? List.<PlanPricingMeteredRateResponse>of() : pricing.meteredRates()) {
            if (meteredRate.limitDefinitionId() == null) {
                findings.add(finding("pricing.meteredRates", "MISSING_LIMIT", "Missing metered limit", "Each metered rate must reference a limit definition.", "Select an active limit definition.", ValidationSeverity.BLOCKING, true, "PRICING", "LIMIT", null, null, "LIMIT", null, null, "Missing limit", "Limit required", "pricing", "Configure Pricing"));
                continue;
            }
            if (!StringUtils.hasText(meteredRate.unitName())) {
                findings.add(finding("pricing.meteredRates.unitName", "MISSING_UNIT_NAME", "Missing unit name", "Metered pricing needs a unit name for display and billing.", "Provide a unit name such as per request or per page.", ValidationSeverity.BLOCKING, true, "PRICING", "LIMIT", String.valueOf(meteredRate.limitDefinitionId()), null, "LIMIT", String.valueOf(meteredRate.limitDefinitionId()), null, "Unit name not set", "Unit name required", "pricing", "Configure Pricing"));
            }
            if (parseOptionalMoney(meteredRate.unitPrice()) == null || parseOptionalMoney(meteredRate.unitPrice()).compareTo(BigDecimal.ZERO) <= 0) {
                findings.add(finding("pricing.meteredRates.unitPrice", "METERED_LIMIT_WITHOUT_UNIT_PRICE", "Metered limit without unit price", "Metered pricing requires a positive unit price.", "Enter a positive unit price.", ValidationSeverity.BLOCKING, true, "PRICING", "LIMIT", String.valueOf(meteredRate.limitDefinitionId()), null, "LIMIT", String.valueOf(meteredRate.limitDefinitionId()), null, stringify(meteredRate.unitPrice()), "Positive amount", "pricing", "Configure Pricing"));
            }
            if (parseOptionalMoney(meteredRate.includedQuantity()) != null && parseOptionalMoney(meteredRate.includedQuantity()).compareTo(BigDecimal.ZERO) < 0) {
                findings.add(finding("pricing.meteredRates.includedQuantity", "NEGATIVE_INCLUDED_QUANTITY", "Included quantity negative", "Included quantity cannot be negative.", "Enter zero or a positive included quantity.", ValidationSeverity.BLOCKING, true, "PRICING", "LIMIT", String.valueOf(meteredRate.limitDefinitionId()), null, "LIMIT", String.valueOf(meteredRate.limitDefinitionId()), null, stringify(meteredRate.includedQuantity()), "Zero or positive", "pricing", "Configure Pricing"));
            }
            if (meteredRate.overageEnabled() && parseOptionalMoney(meteredRate.unitPrice()) != null && parseOptionalMoney(meteredRate.unitPrice()).compareTo(BigDecimal.ZERO) <= 0) {
                findings.add(finding("pricing.meteredRates.overageEnabled", "INVALID_OVERAGE_CONFIG", "Invalid overage configuration", "Overage billing requires a positive unit price.", "Set a positive unit price or disable overage billing.", ValidationSeverity.BLOCKING, true, "PRICING", "LIMIT", String.valueOf(meteredRate.limitDefinitionId()), null, "LIMIT", String.valueOf(meteredRate.limitDefinitionId()), null, "Overage enabled", "Positive unit price", "pricing", "Configure Pricing"));
            }
        }
        return findings;
    }

    private PlanPricingResponse toPricingResponse(CommercialPlanPricingEntity entity, PlanPricingSnapshot fallback) {
        PlanPricingSnapshot pricing = fallback;
        if (entity == null && pricing == null) {
            pricing = new PlanPricingSnapshot(null, null, null, null, null, null, null, null, false, List.of(), List.of());
        }
        List<PlanPricingMeteredRateResponse> meteredRates = new ArrayList<>();
        List<PlanPricingAddonResponse> addonPricing = new ArrayList<>();
        UUID pricingId = entity == null ? null : entity.getId();
        UUID publishedVersionId = entity == null ? null : entity.getPublishedVersion().getId();
        String currency = entity == null ? pricing.currency() : entity.getCurrency();
        BillingCycle billingCycle = entity == null ? pricing.billingCycle() : entity.getBillingCycle();
        String monthlyPrice = entity == null ? pricing.monthlyPrice() : stringify(entity.getMonthlyPrice());
        String annualPrice = entity == null ? pricing.annualPrice() : stringify(entity.getAnnualPrice());
        String setupFee = entity == null ? pricing.setupFee() : stringifyNullable(entity.getSetupFee());
        Integer trialDays = entity == null ? pricing.trialDays() : entity.getTrialDays();
        TaxModel taxModel = entity == null ? pricing.taxModel() : entity.getTaxModel();
        String taxPercentage = entity == null ? pricing.taxPercentage() : stringifyNullable(entity.getTaxPercentage());
        boolean discountAllowed = entity == null ? pricing.discountAllowed() : entity.isDiscountAllowed();
        PricingStatus status = entity == null ? PricingStatus.DRAFT : entity.getStatus();
        OffsetDateTime createdAt = entity == null ? null : entity.getCreatedAt();
        UUID createdBy = entity == null ? null : entity.getCreatedBy();
        if (entity != null) {
            meteredRates = meteredRateRepository.findByPricing_IdOrderById(entity.getId()).stream()
                    .map(rate -> new PlanPricingMeteredRateResponse(
                            rate.getId(),
                            rate.getLimitDefinition().getId(),
                            rate.getLimitDefinition().getCode(),
                            rate.getLimitDefinition().getName(),
                            stringify(rate.getIncludedQuantity()),
                            rate.isOverageEnabled(),
                            stringify(rate.getUnitPrice()),
                            rate.getUnitName(),
                            rate.getBillingRounding(),
                            rate.getStatus()
                    ))
                    .toList();
            addonPricing = addonPricingRepository.findByPricing_IdOrderById(entity.getId()).stream()
                    .map(addon -> new PlanPricingAddonResponse(
                            addon.getId(),
                            addon.getAddonOffer().getId(),
                            addon.getAddonOffer().getCode(),
                            addon.getAddonOffer().getName(),
                            addon.getPurchaseType(),
                            stringify(addon.getMonthlyPrice()),
                            stringify(addon.getAnnualPrice()),
                            stringify(addon.getOneTimePrice()),
                            addon.getMaxQuantity(),
                            addon.getStatus()
                    ))
                    .toList();
        } else if (pricing != null) {
            meteredRates = pricing.meteredRates() == null ? List.of() : pricing.meteredRates();
            addonPricing = pricing.addonPricing() == null ? List.of() : pricing.addonPricing();
        }
        return new PlanPricingResponse(pricingId, publishedVersionId, currency, billingCycle, monthlyPrice, annualPrice, setupFee, trialDays, taxModel, taxPercentage, discountAllowed, status, createdAt, createdBy, meteredRates, addonPricing);
    }

    private List<PricingComparisonEntry> compareSubscriptionPricing(PlanPricingSnapshot left, PlanPricingSnapshot right) {
        List<PricingComparisonEntry> entries = new ArrayList<>();
        compareScalar(entries, "monthly", "Monthly", left == null ? null : left.monthlyPrice(), right == null ? null : right.monthlyPrice());
        compareScalar(entries, "annual", "Annual", left == null ? null : left.annualPrice(), right == null ? null : right.annualPrice());
        compareScalar(entries, "currency", "Currency", left == null ? null : left.currency(), right == null ? null : right.currency());
        compareScalar(entries, "trial", "Trial Days", left == null ? null : left.trialDays(), right == null ? null : right.trialDays());
        compareScalar(entries, "setup", "Setup Fee", left == null ? null : left.setupFee(), right == null ? null : right.setupFee());
        compareScalar(entries, "tax", "Tax Model", left == null ? null : left.taxModel(), right == null ? null : right.taxModel());
        compareScalar(entries, "taxPercentage", "Tax %", left == null ? null : left.taxPercentage(), right == null ? null : right.taxPercentage());
        return entries;
    }

    private List<PricingComparisonEntry> compareMeteredPricing(PlanPricingSnapshot left, PlanPricingSnapshot right) {
        Map<String, PlanPricingMeteredRateResponse> leftMap = mapByCode(left == null ? List.of() : left.meteredRates(), PlanPricingMeteredRateResponse::limitCode);
        Map<String, PlanPricingMeteredRateResponse> rightMap = mapByCode(right == null ? List.of() : right.meteredRates(), PlanPricingMeteredRateResponse::limitCode);
        List<PricingComparisonEntry> entries = new ArrayList<>();
        for (String code : rightMap.keySet()) {
            PlanPricingMeteredRateResponse next = rightMap.get(code);
            PlanPricingMeteredRateResponse prev = leftMap.get(code);
            if (prev == null) {
                entries.add(new PricingComparisonEntry(code, next.limitName(), "Added " + next.unitPrice()));
            } else if (!Objects.equals(prev.unitPrice(), next.unitPrice()) || !Objects.equals(prev.includedQuantity(), next.includedQuantity()) || prev.overageEnabled() != next.overageEnabled()) {
                entries.add(new PricingComparisonEntry(code, next.limitName(), stringify(prev.includedQuantity()) + " @ " + stringify(prev.unitPrice()) + " -> " + stringify(next.includedQuantity()) + " @ " + stringify(next.unitPrice())));
            }
        }
        for (String code : leftMap.keySet()) {
            if (!rightMap.containsKey(code)) {
                PlanPricingMeteredRateResponse prev = leftMap.get(code);
                entries.add(new PricingComparisonEntry(code, prev.limitName(), "Removed " + prev.unitPrice()));
            }
        }
        return entries;
    }

    private List<PricingComparisonEntry> compareAddonPricing(PlanPricingSnapshot left, PlanPricingSnapshot right) {
        Map<String, PlanPricingAddonResponse> leftMap = mapByCode(left == null ? List.of() : left.addonPricing(), PlanPricingAddonResponse::addonCode);
        Map<String, PlanPricingAddonResponse> rightMap = mapByCode(right == null ? List.of() : right.addonPricing(), PlanPricingAddonResponse::addonCode);
        List<PricingComparisonEntry> entries = new ArrayList<>();
        for (String code : rightMap.keySet()) {
            PlanPricingAddonResponse next = rightMap.get(code);
            PlanPricingAddonResponse prev = leftMap.get(code);
            if (prev == null) {
                entries.add(new PricingComparisonEntry(code, next.addonName(), "Added " + next.purchaseType()));
            } else if (!Objects.equals(prev.monthlyPrice(), next.monthlyPrice()) || !Objects.equals(prev.annualPrice(), next.annualPrice()) || !Objects.equals(prev.oneTimePrice(), next.oneTimePrice()) || !Objects.equals(prev.maxQuantity(), next.maxQuantity())) {
                entries.add(new PricingComparisonEntry(code, next.addonName(), stringify(prev.monthlyPrice()) + " / " + stringify(prev.annualPrice()) + " / " + stringify(prev.oneTimePrice()) + " -> " + stringify(next.monthlyPrice()) + " / " + stringify(next.annualPrice()) + " / " + stringify(next.oneTimePrice())));
            }
        }
        for (String code : leftMap.keySet()) {
            if (!rightMap.containsKey(code)) {
                PlanPricingAddonResponse prev = leftMap.get(code);
                entries.add(new PricingComparisonEntry(code, prev.addonName(), "Removed " + prev.purchaseType()));
            }
        }
        return entries;
    }

    private void compareScalar(List<PricingComparisonEntry> entries, String code, String name, Object left, Object right) {
        if (!Objects.equals(left, right)) {
            entries.add(new PricingComparisonEntry(code, name, stringify(left) + " -> " + stringify(right)));
        }
    }

    private <T> Map<String, T> mapByCode(List<T> items, java.util.function.Function<T, String> codeFn) {
        Map<String, T> map = new java.util.LinkedHashMap<>();
        for (T item : items) {
            map.put(codeFn.apply(item), item);
        }
        return map;
    }

    private CommercialPlanDraftEntity ensureDraft(CommercialPlanTemplateEntity template) {
        return draftRepository.findByTemplate_Id(template.getId()).orElseGet(() -> {
            PlanConfigurationSnapshot snapshot = new PlanConfigurationSnapshot(
                    template.getCode(),
                    template.getName(),
                    template.getDescription(),
                    template.getTargetSegment(),
                    template.getStatus(),
                    template.getDisplayOrder(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    new PlanPricingSnapshot(null, null, null, null, null, null, null, null, false, List.of(), List.of())
            );
            String configJson = serialize(snapshot);
            CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(template, template.getCurrentDraftRevision(), configJson, hash(configJson), serialize(List.of()), DraftStatus.DRAFT, false, now(), currentActor());
            return draftRepository.save(draft);
        });
    }

    private PlanConfigurationSnapshot parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return new PlanConfigurationSnapshot(null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), new PlanPricingSnapshot(null, null, null, null, null, null, null, null, false, List.of(), List.of()));
        }
        try {
            PlanConfigurationSnapshot snapshot = objectMapper.readValue(json, PlanConfigurationSnapshot.class);
            if (snapshot.pricing() == null) {
                return new PlanConfigurationSnapshot(snapshot.templateCode(), snapshot.templateName(), snapshot.templateDescription(), snapshot.targetSegment(), snapshot.templateStatus(), snapshot.displayOrder(), snapshot.capabilities(), snapshot.modules(), snapshot.features(), snapshot.limits(), snapshot.addons(), new PlanPricingSnapshot(null, null, null, null, null, null, null, null, false, List.of(), List.of()));
            }
            return snapshot;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse commercial plan snapshot", ex);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize commercial pricing snapshot", ex);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash commercial pricing snapshot", ex);
        }
    }

    private BigDecimal parseMoney(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid monetary value: " + value, ex);
        }
    }

    private BigDecimal parseOptionalMoney(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return parseMoney(value);
    }

    private String stringifyNullable(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeCurrency(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private ValidationMessageResponse finding(
            String field,
            String code,
            String title,
            String message,
            String remediation,
            ValidationSeverity severity,
            boolean blocking,
            String category,
            String affectedItemType,
            String affectedItemCode,
            String affectedItemName,
            String expectedItemType,
            String expectedItemCode,
            String expectedItemName,
            String currentValue,
            String expectedValue,
            String targetBuilderTab,
            String actionLabel
    ) {
        return new ValidationMessageResponse(
                field,
                code,
                title,
                message,
                remediation,
                severity,
                blocking,
                category,
                affectedItemType,
                affectedItemCode,
                affectedItemName,
                expectedItemType,
                expectedItemCode,
                expectedItemName,
                currentValue,
                expectedValue,
                targetBuilderTab,
                actionLabel
        );
    }

    private void audit(UUID entityId, String entityType, String action, String summary, Map<String, Object> details) {
        try {
            auditEventPublisher.record(new AuditEventCommand(
                    platformAuditTenantId(),
                    entityType,
                    entityId,
                    action,
                    currentActor(),
                    now(),
                    summary,
                    serialize(details == null ? Map.of() : details)
            ));
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private UUID platformAuditTenantId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null && ctx.tenantId() != null) {
            return ctx.tenantId().value();
        }
        return PLATFORM_AUDIT_TENANT_ID;
    }

    private UUID currentActor() {
        RequestContext ctx = RequestContextHolder.get();
        return ctx == null ? null : ctx.appUserId();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private ResponseStatusException notFound(String type, UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found: " + id);
    }
}
