package com.deepthoughtnet.clinic.commercial.platform;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AddonType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AggregationPeriod;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.EnforcementMode;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.LimitValueType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonFeatureEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonLimitIncrementEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonModuleEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialCapabilityEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialCapabilityModuleEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialCapabilityRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialFeatureEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialFeatureRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionRepository;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialModuleEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialModuleRepository;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.DraftStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.PublicationStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.SelectionSource;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.SelectionState;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TargetSegment;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TemplateStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationState;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationSeverity;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.*;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementService;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionService;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionStatusCountsResponse;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanDraftEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanDraftRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventAction;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommercialPlatformService {
    private static final UUID PLATFORM_AUDIT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Set<String> ACTIVE_RUNTIME_MODULE_CODES = Set.of(
            "APPOINTMENTS",
            "PATIENTS",
            "CONSULTATION",
            "PRESCRIPTION",
            "BILLING",
            "VACCINATION",
            "INVENTORY",
            "PHARMACY_POS",
            "LABORATORY",
            "REPORTS",
            "AI_COPILOT",
            "CAREPILOT",
            "NOTIFICATIONS"
    );

    private final CommercialCapabilityRepository capabilityRepository;
    private final CommercialModuleRepository moduleRepository;
    private final CommercialFeatureRepository featureRepository;
    private final CommercialLimitDefinitionRepository limitRepository;
    private final CommercialAddonOfferRepository addonRepository;
    private final CommercialPlanTemplateRepository templateRepository;
    private final CommercialPlanDraftRepository draftRepository;
    private final CommercialPlanVersionRepository versionRepository;
    private final CommercialSubscriptionService subscriptionService;
    private final CommercialEffectiveEntitlementService effectiveEntitlementService;
    private final CommercialRuntimeProperties runtimeProperties;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public CommercialPlatformService(
            CommercialCapabilityRepository capabilityRepository,
            CommercialModuleRepository moduleRepository,
            CommercialFeatureRepository featureRepository,
            CommercialLimitDefinitionRepository limitRepository,
            CommercialAddonOfferRepository addonRepository,
            CommercialPlanTemplateRepository templateRepository,
            CommercialPlanDraftRepository draftRepository,
            CommercialPlanVersionRepository versionRepository,
            CommercialSubscriptionService subscriptionService,
            CommercialEffectiveEntitlementService effectiveEntitlementService,
            CommercialRuntimeProperties runtimeProperties,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.capabilityRepository = capabilityRepository;
        this.moduleRepository = moduleRepository;
        this.featureRepository = featureRepository;
        this.limitRepository = limitRepository;
        this.addonRepository = addonRepository;
        this.templateRepository = templateRepository;
        this.draftRepository = draftRepository;
        this.versionRepository = versionRepository;
        this.subscriptionService = subscriptionService;
        this.effectiveEntitlementService = effectiveEntitlementService;
        this.runtimeProperties = runtimeProperties;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper.copy().findAndRegisterModules().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional(readOnly = true)
    public OverviewResponse getOverview() {
        long activeCapabilities = capabilityRepository.count(activeCapabilitySpec());
        long activeModules = moduleRepository.count(activeModuleSpec());
        long activeFeatures = featureRepository.count(activeFeatureSpec());
        long activeLimits = limitRepository.count(activeLimitSpec());
        long activeAddons = addonRepository.count(activeAddonSpec());
        long publishedPlans = versionRepository.countByStatus(PublicationStatus.PUBLISHED);
        SubscriptionStatusCountsResponse subscriptionCounts = subscriptionService.getStatusCounts();
        long planTemplates = templateRepository.count();
        long draftPlans = draftRepository.count();
        long currentSnapshots = effectiveEntitlementService.countCurrentSnapshots();
        long generationFailures = effectiveEntitlementService.countGenerationFailures();
        long activeOverrides = effectiveEntitlementService.countActiveOverrides();
        long runtimeMismatches = effectiveEntitlementService.countRuntimeMismatches();
        return new OverviewResponse(
                List.of(
                        new KpiCardResponse("active-capabilities", "Active Capabilities", activeCapabilities, "Catalog records currently available"),
                        new KpiCardResponse("active-modules", "Active Modules", activeModules, "Runtime modules mapped from catalog"),
                        new KpiCardResponse("active-features", "Active Features", activeFeatures, "Feature definitions available for packaging"),
                        new KpiCardResponse("active-limits", "Active Limits", activeLimits, "Limit definitions available for plan configuration"),
                        new KpiCardResponse("active-addons", "Active Add-ons", activeAddons, "Add-on offers available for packaging"),
                        new KpiCardResponse("published-plans", "Published Plans", publishedPlans, "Immutable commercial plan versions"),
                        new KpiCardResponse("plan-templates", "Plan Templates", planTemplates, "Commercial plan containers"),
                        new KpiCardResponse("draft-plans", "Draft Plans", draftPlans, "Editable working configurations"),
                        new KpiCardResponse("active-subscriptions", "Active Subscriptions", subscriptionCounts.activeCount(), "Commercial subscriptions currently active"),
                        new KpiCardResponse("scheduled-subscriptions", "Scheduled", subscriptionCounts.scheduledCount(), "Commercial subscriptions scheduled for future activation"),
                        new KpiCardResponse("paused-subscriptions", "Paused", subscriptionCounts.pausedCount(), "Commercial subscriptions temporarily paused"),
                        new KpiCardResponse("expired-subscriptions", "Expired", subscriptionCounts.expiredCount(), "Commercial subscriptions that have ended"),
                        new KpiCardResponse("cancelled-subscriptions", "Cancelled", subscriptionCounts.cancelledCount(), "Commercial subscriptions that were cancelled"),
                        new KpiCardResponse("current-snapshots", "Tenants with Current Snapshots", currentSnapshots, "Commercial effective entitlement snapshots currently current"),
                        new KpiCardResponse("snapshot-failures", "Snapshot Generation Failures", generationFailures, "Commercial entitlement generations that need attention"),
                        new KpiCardResponse("active-overrides", "Active Overrides", activeOverrides, "Tenant-level commercial overrides currently in effect"),
                        new KpiCardResponse("legacy-commercial-mismatches", "Legacy/Commercial Mismatches", runtimeMismatches, "Shadow compare differences captured"),
                        new KpiCardResponse("commercial-runtime-enabled", "Commercial Runtime Enabled", runtimeProperties.isEnabled() ? 1 : 0, "Commercial effective entitlements are authoritative when enabled")
                ),
                List.of(
                        new LifecycleStageResponse("catalog", "Catalog", true, false),
                        new LifecycleStageResponse("plan-template", "Plan Template", true, false),
                        new LifecycleStageResponse("draft-configuration", "Draft Configuration", true, false),
                        new LifecycleStageResponse("published-version", "Published Version", true, false),
                        new LifecycleStageResponse("tenant-subscription", "Tenant Subscription", true, false),
                        new LifecycleStageResponse("effective-entitlements", "Effective Entitlements", true, false),
                        new LifecycleStageResponse("usage-billing", "Usage and Billing", false, true)
                ),
                List.of(
                        new QuickActionResponse("manage-catalog", "Manage Catalog", "/platform/commercial/catalog", true, true),
                        new QuickActionResponse("create-plan-template", "Create Plan Template", "/platform/commercial/plans", true, true),
                        new QuickActionResponse("review-draft-plans", "Review Draft Plans", "/platform/commercial/plans", true, false),
                        new QuickActionResponse("view-published-versions", "View Published Versions", "/platform/commercial/plans", true, false),
                        new QuickActionResponse("manage-subscriptions", "Manage Subscriptions", "/platform/commercial/subscriptions", true, false),
                        new QuickActionResponse("effective-entitlements", "Effective Entitlements", "/platform/commercial/entitlements", true, false)
                )
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateSummaryResponse> listTemplates(String search, TemplateStatus status, TargetSegment targetSegment, int page, int size) {
        Page<CommercialPlanTemplateEntity> result = templateRepository.findAll(templateSpec(search, status, targetSegment),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("name"), Sort.Order.asc("code"))));
        return new PageResponse<>(
                result.map(this::toTemplateSummary).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public TemplateDetailResponse getTemplate(UUID templateId) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        CommercialPlanVersionEntity latestVersion = versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(templateId).orElse(null);
        return toTemplateDetail(template, draft, latestVersion);
    }

    @Transactional
    public TemplateDetailResponse createTemplate(CreatePlanTemplateRequest request) {
        String code = normalizeCode(blankToNull(request.code()) == null ? request.name() : request.code());
        if (templateRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("Plan template code already exists: " + code);
        }
        TargetSegment targetSegment = request.targetSegment() == null ? TargetSegment.CUSTOM : request.targetSegment();
        TemplateStatus status = request.status() == null ? TemplateStatus.DRAFT : request.status();
        OffsetDateTime now = now();
        UUID actor = currentActor();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(UUID.randomUUID(), code, requireText(request.name(), "name is required"), blankToNull(request.description()), targetSegment, status, request.displayOrder() == null ? 0 : request.displayOrder(), now, actor);
        templateRepository.save(template);
        CommercialPlanDraftEntity draft = ensureDraft(template);
        audit(template.getId(), AuditEntityType.COMMERCIAL_PLAN_TEMPLATE, AuditEventAction.COMMERCIAL_PLAN_TEMPLATE_CREATED, "Created commercial plan template", Map.of("code", code, "targetSegment", targetSegment.name()));
        return toTemplateDetail(template, draft, null);
    }

    @Transactional
    public TemplateDetailResponse cloneTemplate(UUID sourceTemplateId, ClonePlanTemplateRequest request) {
        if (request != null && request.sourceTemplateId() != null && !request.sourceTemplateId().equals(sourceTemplateId)) {
            throw conflict("Source template mismatch");
        }
        CommercialPlanTemplateEntity sourceTemplate = templateRepository.findById(sourceTemplateId).orElseThrow(() -> notFound("Plan template", sourceTemplateId));
        PlanConfigurationSnapshot sourceSnapshot = resolveCloneSnapshot(sourceTemplate, request == null ? null : request.sourceVersionId());
        String requestedCode = request == null ? null : blankToNull(request.code());
        String requestedName = request == null ? null : blankToNull(request.name());
        String code = normalizeCode(requestedCode == null ? (requestedName == null ? sourceTemplate.getName() : requestedName) : requestedCode);
        if (templateRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("Plan template code already exists: " + code);
        }
        TargetSegment targetSegment = request != null && request.targetSegment() != null ? request.targetSegment() : sourceTemplate.getTargetSegment();
        TemplateStatus status = request != null && request.status() != null ? request.status() : TemplateStatus.DRAFT;
        int displayOrder = request != null && request.displayOrder() != null ? request.displayOrder() : sourceTemplate.getDisplayOrder();
        OffsetDateTime now = now();
        UUID actor = currentActor();
        CommercialPlanTemplateEntity template = CommercialPlanTemplateEntity.create(
                UUID.randomUUID(),
                code,
                requireText(request != null && StringUtils.hasText(request.name()) ? request.name() : sourceTemplate.getName(), "name is required"),
                blankToNull(request == null ? null : request.description()),
                targetSegment,
                status,
                displayOrder,
                now,
                actor
        );
        templateRepository.save(template);
        String configJson = serialize(sourceSnapshot);
        CommercialPlanDraftEntity draft = CommercialPlanDraftEntity.create(
                template,
                template.getCurrentDraftRevision(),
                configJson,
                hash(configJson),
                serialize(List.of()),
                DraftStatus.DRAFT,
                false,
                now,
                actor
        );
        draftRepository.save(draft);
        Map<String, Object> details = new HashMap<>();
        details.put("code", code);
        details.put("targetSegment", targetSegment.name());
        details.put("sourceTemplateCode", sourceTemplate.getCode());
        if (request != null && request.sourceVersionId() != null) {
            details.put("sourceVersionId", request.sourceVersionId());
        }
        audit(
                template.getId(),
                AuditEntityType.COMMERCIAL_PLAN_TEMPLATE,
                AuditEventAction.COMMERCIAL_PLAN_TEMPLATE_CREATED,
                "Cloned commercial plan template",
                details
        );
        return toTemplateDetail(template, draft, null);
    }

    @Transactional
    public TemplateDetailResponse updateTemplate(UUID templateId, UpdatePlanTemplateRequest request) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        template.update(
                requireText(request.name(), "name is required"),
                blankToNull(request.description()),
                request.targetSegment() == null ? template.getTargetSegment() : request.targetSegment(),
                request.status() == null ? template.getStatus() : request.status(),
                request.displayOrder() == null ? template.getDisplayOrder() : request.displayOrder(),
                now(),
                currentActor()
        );
        templateRepository.save(template);
        CommercialPlanDraftEntity draft = ensureDraft(template);
        audit(template.getId(), AuditEntityType.COMMERCIAL_PLAN_TEMPLATE, AuditEventAction.COMMERCIAL_PLAN_TEMPLATE_UPDATED, "Updated commercial plan template", Map.of("code", template.getCode(), "targetSegment", template.getTargetSegment().name()));
        return toTemplateDetail(template, draft, versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(templateId).orElse(null));
    }

    @Transactional
    public TemplateDetailResponse retireTemplate(UUID templateId) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        template.retire(now(), currentActor());
        templateRepository.save(template);
        CommercialPlanDraftEntity draft = ensureDraft(template);
        audit(template.getId(), AuditEntityType.COMMERCIAL_PLAN_TEMPLATE, AuditEventAction.COMMERCIAL_PLAN_TEMPLATE_RETIRED, "Retired commercial plan template", Map.of("code", template.getCode()));
        return toTemplateDetail(template, draft, versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(templateId).orElse(null));
    }

    @Transactional(readOnly = true)
    public PlanDraftResponse getDraft(UUID templateId) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        ValidationResult validation = buildValidationResult(template, draft, parseSnapshot(draft.getConfigJson()), false);
        return toDraftResponse(template, draft, validation);
    }

    @Transactional
    public PlanDraftResponse saveDraft(UUID templateId, SavePlanDraftRequest request) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        PlanConfigurationSnapshot snapshot = buildSnapshot(template, request);
        OffsetDateTime now = now();
        UUID actor = currentActor();
        template.incrementDraftRevision(now, actor);
        templateRepository.save(template);
        String configJson = serialize(snapshot);
        List<ValidationMessageResponse> findings = validateDraftConfiguration(template, snapshot, template.getCurrentDraftRevision(), true);
        String validationJson = serialize(findings);
        draft.update(template.getCurrentDraftRevision(), blankToNull(request.draftNotes()), configJson, hash(configJson), validationJson, DraftStatus.BLOCKED, false, now, actor);
        draftRepository.save(draft);
        ValidationResult validation = buildValidationResult(template, draft, snapshot, true);
        audit(template.getId(), AuditEntityType.COMMERCIAL_PLAN_DRAFT, AuditEventAction.COMMERCIAL_PLAN_DRAFT_SAVED, "Saved commercial plan draft", Map.of("code", template.getCode(), "revision", draft.getRevision(), "blockingWarnings", validation.blockingFindingCount()));
        return toDraftResponse(template, draft, validation);
    }

    @Transactional
    public ValidatePlanDraftResponse validateDraft(UUID templateId) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        PlanConfigurationSnapshot snapshot = parseSnapshot(draft.getConfigJson());
        List<ValidationMessageResponse> findings = validateDraftConfiguration(template, snapshot, draft.getRevision(), false);
        int blockingCount = (int) findings.stream().filter(ValidationMessageResponse::blocking).count();
        int warningCount = (int) findings.stream().filter(message -> message.severity() == ValidationSeverity.WARNING).count();
        OffsetDateTime validatedAt = now();
        draft.markValidated(blockingCount == 0 ? "READY" : "BLOCKED", blockingCount == 0, serialize(findings), validatedAt, currentActor());
        draftRepository.save(draft);
        ValidationResult validation = new ValidationResult(findings, blockingCount == 0 ? ValidationState.VALID : ValidationState.INVALID, blockingCount == 0, blockingCount, warningCount, draft.getRevision(), validatedAt);
        audit(template.getId(), AuditEntityType.COMMERCIAL_PLAN_DRAFT, AuditEventAction.COMMERCIAL_PLAN_DRAFT_VALIDATED, "Validated commercial plan draft", Map.of("code", template.getCode(), "revision", draft.getRevision(), "blockingWarnings", validation.blockingFindingCount()));
        return new ValidatePlanDraftResponse(toDraftResponse(template, draft, validation), validation.toResponse(), validation.messages(), validation.readyToPublish());
    }

    @Transactional
    public PlanVersionDetailResponse publishVersion(UUID templateId, PublishPlanVersionRequest request) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        PlanConfigurationSnapshot snapshot = parseSnapshot(draft.getConfigJson());
        ValidationResult validation = buildValidationResult(template, draft, snapshot, true);
        if (validation.validationState() != ValidationState.VALID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plan draft must be validated and free of blocking findings before publishing");
        }
        int nextVersion = versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(templateId).map(version -> version.getVersionNumber() + 1).orElse(1);
        String snapshotJson = serialize(buildPublishedSnapshot(template, draft, snapshot, request));
        String contentHash = hash(snapshotJson);
        OffsetDateTime now = now();
        CommercialPlanVersionEntity version = CommercialPlanVersionEntity.create(
                template,
                nextVersion,
                "v" + nextVersion,
                PublicationStatus.PUBLISHED,
                now,
                currentActor(),
                blankToNull(request == null ? null : request.publicationNotes()),
                draft.getRevision(),
                contentHash,
                snapshotJson,
                snapshot.capabilities().size(),
                snapshot.modules().size(),
                snapshot.features().size(),
                snapshot.limits().size(),
                snapshot.addons().size(),
                currentActor()
        );
        versionRepository.save(version);
        template.markPublished(nextVersion, now, currentActor());
        templateRepository.save(template);
        draft.markPublished(now, currentActor());
        draftRepository.save(draft);
        audit(template.getId(), AuditEntityType.COMMERCIAL_PLAN_VERSION, AuditEventAction.COMMERCIAL_PLAN_VERSION_PUBLISHED, "Published commercial plan version", Map.of("code", template.getCode(), "version", nextVersion, "contentHash", contentHash));
        return toVersionDetail(version);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlanVersionSummaryResponse> listVersions(UUID templateId) {
        List<CommercialPlanVersionEntity> versions = versionRepository.findByTemplate_IdOrderByVersionNumberDesc(templateId);
        List<PlanVersionSummaryResponse> items = new ArrayList<>(versions.size());
        CommercialPlanVersionEntity previous = null;
        for (int index = versions.size() - 1; index >= 0; index--) {
            CommercialPlanVersionEntity current = versions.get(index);
            items.add(0, toVersionSummary(current, previous));
            previous = current;
        }
        return new PageResponse<>(items, 0, items.size(), items.size(), 1);
    }

    @Transactional(readOnly = true)
    public PlanVersionDetailResponse getVersion(UUID templateId, UUID versionId) {
        CommercialPlanVersionEntity version = versionRepository.findById(versionId).orElseThrow(() -> notFound("Plan version", versionId));
        if (!version.getTemplate().getId().equals(templateId)) {
            throw notFound("Plan version", versionId);
        }
        return toVersionDetail(version);
    }

    @Transactional(readOnly = true)
    public CompareVersionsResponse compareVersions(UUID templateId, UUID leftVersionId, UUID rightVersionId) {
        CommercialPlanTemplateEntity template = templateRepository.findById(templateId).orElseThrow(() -> notFound("Plan template", templateId));
        CommercialPlanDraftEntity draft = ensureDraft(template);
        CommercialPlanVersionEntity left = leftVersionId == null ? null : versionRepository.findById(leftVersionId).orElseThrow(() -> notFound("Plan version", leftVersionId));
        CommercialPlanVersionEntity right = rightVersionId == null ? null : versionRepository.findById(rightVersionId).orElseThrow(() -> notFound("Plan version", rightVersionId));
        PlanConfigurationSnapshot leftSnapshot;
        PlanConfigurationSnapshot rightSnapshot;
        String leftLabel;
        String rightLabel;
        if (left == null && right == null) {
            leftSnapshot = parseSnapshot(draft.getConfigJson());
            rightSnapshot = versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(templateId).map(version -> parseSnapshot(version.getSnapshotJson())).orElse(parseSnapshot(draft.getConfigJson()));
            leftLabel = "Current Draft";
            rightLabel = "Latest Published";
        } else {
            leftSnapshot = left == null ? parseSnapshot(draft.getConfigJson()) : parseSnapshot(left.getSnapshotJson());
            rightSnapshot = right == null ? parseSnapshot(draft.getConfigJson()) : parseSnapshot(right.getSnapshotJson());
            leftLabel = left == null ? "Current Draft" : left.getVersionLabel();
            rightLabel = right == null ? "Current Draft" : right.getVersionLabel();
        }
        return new CompareVersionsResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                leftLabel,
                rightLabel,
                compareMetadata(leftSnapshot, rightSnapshot),
                compareSection(leftSnapshot.capabilities(), rightSnapshot.capabilities(), SelectedCapability::capabilityCode, SelectedCapability::capabilityName, SelectedCapability::description, "capability"),
                compareSection(leftSnapshot.modules(), rightSnapshot.modules(), SelectedModule::moduleCode, SelectedModule::moduleName, SelectedModule::description, "module"),
                compareSection(leftSnapshot.features(), rightSnapshot.features(), SelectedFeature::featureCode, SelectedFeature::featureName, SelectedFeature::description, "feature"),
                compareLimitSection(leftSnapshot.limits(), rightSnapshot.limits()),
                compareAddonSection(leftSnapshot.addons(), rightSnapshot.addons())
        );
    }

    private List<ValidationMessageResponse> validateDraftConfiguration(CommercialPlanTemplateEntity template, PlanConfigurationSnapshot snapshot, int revision, boolean publishing) {
        List<ValidationMessageResponse> messages = new ArrayList<>();
        Set<UUID> selectedCapabilityIds = snapshot.capabilities() == null ? Set.of() : snapshot.capabilities().stream().map(SelectedCapability::capabilityId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> selectedModuleIds = snapshot.modules() == null ? Set.of() : snapshot.modules().stream().map(SelectedModule::moduleId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> selectedFeatureIds = snapshot.features() == null ? Set.of() : snapshot.features().stream().map(SelectedFeature::featureId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> selectedLimitIds = snapshot.limits() == null ? Set.of() : snapshot.limits().stream().map(SelectedLimit::limitDefinitionId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> selectedAddonIds = snapshot.addons() == null ? Set.of() : snapshot.addons().stream().map(SelectedAddon::addonId).collect(Collectors.toCollection(LinkedHashSet::new));

        if (template.getStatus() == TemplateStatus.RETIRED && publishing) {
            messages.add(planFinding(
                    template,
                    "TEMPLATE_RETIRED",
                    "Retired plan templates cannot be published",
                    "The selected plan template is retired and cannot publish new versions.",
                    "Create a new draft from an active template.",
                    ValidationSeverity.BLOCKING,
                    true,
                    "CATALOG_STATUS",
                    "Retired",
                    "Active template",
                    "summary",
                    "Review Summary"
            ));
        }
        if (selectedCapabilityIds.isEmpty()) {
            messages.add(finding(
                    "capabilities",
                    "PLAN_CAPABILITY_REQUIRED",
                    "At least one capability is required",
                    "No capabilities are selected, so this plan cannot be published.",
                    "Select at least one commercial capability.",
                    ValidationSeverity.BLOCKING,
                    true,
                    "PLAN_CAPABILITY_REQUIRED",
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "CAPABILITY",
                    null,
                    "At least one commercial capability",
                    "No capabilities selected",
                    "At least one capability selected",
                    "capabilities",
                    "Configure Capabilities"
            ));
        }
        if (selectedModuleIds.isEmpty()) {
            messages.add(finding(
                    "modules",
                    "PLAN_MODULE_REQUIRED",
                    "At least one module is required",
                    "No application modules are included in this draft.",
                    "Include at least one application module.",
                    ValidationSeverity.BLOCKING,
                    true,
                    "PLAN_MODULE_REQUIRED",
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "MODULE",
                    null,
                    "At least one application module",
                    "No modules selected",
                    "At least one module selected",
                    "modules",
                    "Configure Modules"
            ));
        }

        if (selectedCapabilityIds.size() != (snapshot.capabilities() == null ? 0 : snapshot.capabilities().size())) {
            messages.add(finding(
                    "capabilities",
                    "DUPLICATE_CAPABILITY",
                    "Duplicate capability selections are not allowed",
                    "The capability selection list contains repeated entries.",
                    "Remove repeated capability selections.",
                    ValidationSeverity.BLOCKING,
                    true,
                    "DUPLICATE_SELECTION",
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "Duplicate capability selections are present",
                    "Each capability selected once",
                    "capabilities",
                    "Review Capabilities"
            ));
        }
        if (selectedModuleIds.size() != (snapshot.modules() == null ? 0 : snapshot.modules().size())) {
            messages.add(finding(
                    "modules",
                    "DUPLICATE_MODULE",
                    "Duplicate module selections are not allowed",
                    "The module selection list contains repeated entries.",
                    "Remove repeated module selections.",
                    ValidationSeverity.BLOCKING,
                    true,
                    "DUPLICATE_SELECTION",
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "Duplicate module selections are present",
                    "Each module selected once",
                    "modules",
                    "Review Modules"
            ));
        }
        if (selectedFeatureIds.size() != (snapshot.features() == null ? 0 : snapshot.features().size())) {
            messages.add(finding(
                    "features",
                    "DUPLICATE_FEATURE",
                    "Duplicate feature selections are not allowed",
                    "The feature selection list contains repeated entries.",
                    "Remove repeated feature selections.",
                    ValidationSeverity.BLOCKING,
                    true,
                    "DUPLICATE_SELECTION",
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "Duplicate feature selections are present",
                    "Each feature selected once",
                    "features",
                    "Review Features"
            ));
        }
        if (selectedLimitIds.size() != (snapshot.limits() == null ? 0 : snapshot.limits().size())) {
            messages.add(finding(
                    "limits",
                    "DUPLICATE_LIMIT",
                    "Duplicate limit selections are not allowed",
                    "The limit selection list contains repeated entries.",
                    "Remove repeated limit selections.",
                    ValidationSeverity.BLOCKING,
                    true,
                    "DUPLICATE_SELECTION",
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "Duplicate limit selections are present",
                    "Each limit selected once",
                    "limits",
                    "Review Limits"
            ));
        }
        if (selectedAddonIds.size() != (snapshot.addons() == null ? 0 : snapshot.addons().size())) {
            messages.add(finding(
                    "addons",
                    "DUPLICATE_ADDON",
                    "Duplicate add-on selections are not allowed",
                    "The add-on selection list contains repeated entries.",
                    "Remove repeated add-on selections.",
                    ValidationSeverity.BLOCKING,
                    true,
                    "DUPLICATE_SELECTION",
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "PLAN_TEMPLATE",
                    template.getCode(),
                    template.getName(),
                    "Duplicate add-on selections are present",
                    "Each add-on selected once",
                    "addons",
                    "Review Add-ons"
            ));
        }

        Map<UUID, CommercialCapabilityEntity> capabilities = capabilityRepository.findAllById(selectedCapabilityIds).stream().collect(Collectors.toMap(CommercialCapabilityEntity::getId, Function.identity()));
        Map<UUID, CommercialModuleEntity> modules = moduleRepository.findAllById(selectedModuleIds).stream().collect(Collectors.toMap(CommercialModuleEntity::getId, Function.identity()));
        Map<UUID, CommercialFeatureEntity> features = featureRepository.findAllById(selectedFeatureIds).stream().collect(Collectors.toMap(CommercialFeatureEntity::getId, Function.identity()));
        Map<UUID, CommercialLimitDefinitionEntity> limits = limitRepository.findAllById(selectedLimitIds).stream().collect(Collectors.toMap(CommercialLimitDefinitionEntity::getId, Function.identity()));
        Map<UUID, CommercialAddonOfferEntity> addons = addonRepository.findAllById(selectedAddonIds).stream().collect(Collectors.toMap(CommercialAddonOfferEntity::getId, Function.identity()));

        for (SelectedCapability selected : snapshot.capabilities() == null ? List.<SelectedCapability>of() : snapshot.capabilities()) {
            CommercialCapabilityEntity entity = capabilities.get(selected.capabilityId());
            if (entity == null) {
                messages.add(planFinding(
                        template,
                        "UNKNOWN_CAPABILITY",
                        "Selected capability is no longer available",
                        "The selected capability could not be found in the catalog.",
                        "Select a capability that still exists in the catalog.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        selected.capabilityName(),
                        "Active capability",
                        "capabilities",
                        "Review Capabilities"
                ));
                continue;
            }
            if (entity.getStatus() == Status.RETIRED && !selectedPreviously(template.getId(), selected.capabilityId(), "capabilities")) {
                messages.add(catalogFinding(
                        "capabilities",
                        "RETIRED_CAPABILITY",
                        selected.capabilityName() + " is retired",
                        "The selected capability “" + selected.capabilityName() + "” is retired and cannot be newly published.",
                        "Choose an active capability.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        "CAPABILITY",
                        selected.capabilityCode(),
                        selected.capabilityName(),
                        "CAPABILITY",
                        "ACTIVE",
                        "Active catalog capability",
                        "capabilities",
                        "Review Capabilities"
                ));
            } else if (entity.getStatus() == Status.RETIRED) {
                messages.add(catalogFinding(
                        "capabilities",
                        "RETIRED_CAPABILITY",
                        selected.capabilityName() + " is retired",
                        "The selected capability “" + selected.capabilityName() + "” was retired after it was added to this draft.",
                        "Review the draft before publishing or replace the retired capability.",
                        ValidationSeverity.WARNING,
                        false,
                        "CATALOG_STATUS",
                        "CAPABILITY",
                        selected.capabilityCode(),
                        selected.capabilityName(),
                        "CAPABILITY",
                        "ACTIVE",
                        "Active catalog capability",
                        "capabilities",
                        "Review Capabilities"
                ));
            }
        }

        for (SelectedModule selected : snapshot.modules() == null ? List.<SelectedModule>of() : snapshot.modules()) {
            CommercialModuleEntity entity = modules.get(selected.moduleId());
            if (entity == null) {
                messages.add(planFinding(
                        template,
                        "UNKNOWN_MODULE",
                        "Selected module is no longer available",
                        "The selected module could not be found in the catalog.",
                        "Select a module that still exists in the catalog.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        selected.moduleName(),
                        "Active module",
                        "modules",
                        "Review Modules"
                ));
                continue;
            }
            if (entity.getStatus() == Status.RETIRED && !selectedPreviously(template.getId(), selected.moduleId(), "modules")) {
                messages.add(catalogFinding(
                        "modules",
                        "RETIRED_MODULE",
                        selected.moduleName() + " is retired",
                        "The selected module “" + selected.moduleName() + "” is retired and cannot be newly published.",
                        "Choose an active module.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        "MODULE",
                        selected.moduleCode(),
                        selected.moduleName(),
                        "MODULE",
                        "ACTIVE",
                        "Active catalog module",
                        "modules",
                        "Review Modules"
                ));
            } else if (entity.getStatus() == Status.RETIRED) {
                messages.add(catalogFinding(
                        "modules",
                        "RETIRED_MODULE",
                        selected.moduleName() + " is retired",
                        "The selected module “" + selected.moduleName() + "” was retired after it was added to this draft.",
                        "Review the draft before publishing or replace the retired module.",
                        ValidationSeverity.WARNING,
                        false,
                        "CATALOG_STATUS",
                        "MODULE",
                        selected.moduleCode(),
                        selected.moduleName(),
                        "MODULE",
                        "ACTIVE",
                        "Active catalog module",
                        "modules",
                        "Review Modules"
                ));
            }
        }

        Set<UUID> includedModuleIds = snapshot.modules() == null ? Set.of() : snapshot.modules().stream()
                .filter(module -> module.selectionSource() == SelectionSource.EXPLICIT || Boolean.TRUE.equals(module.inherited()) || module.selectionSource() == SelectionSource.INHERITED)
                .map(SelectedModule::moduleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (SelectedFeature selected : snapshot.features() == null ? List.<SelectedFeature>of() : snapshot.features()) {
            CommercialFeatureEntity entity = features.get(selected.featureId());
            if (entity == null) {
                messages.add(planFinding(
                        template,
                        "UNKNOWN_FEATURE",
                        "Selected feature is no longer available",
                        "The selected feature could not be found in the catalog.",
                        "Select a feature that still exists in the catalog.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        selected.featureName(),
                        "Active feature",
                        "features",
                        "Review Features"
                ));
                continue;
            }
            if (!includedModuleIds.contains(entity.getModule().getId())) {
                messages.add(featureDependencyFinding(template, selected, entity));
            }
            if (entity.getStatus() == Status.RETIRED && !selectedPreviously(template.getId(), selected.featureId(), "features")) {
                messages.add(catalogFinding(
                        "features",
                        "RETIRED_FEATURE",
                        selected.featureName() + " is retired",
                        "The selected feature “" + selected.featureName() + "” is retired and cannot be newly published.",
                        "Choose an active feature.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        "FEATURE",
                        selected.featureCode(),
                        selected.featureName(),
                        "FEATURE",
                        "ACTIVE",
                        "Active catalog feature",
                        "features",
                        "Review Features"
                ));
            } else if (entity.getStatus() == Status.RETIRED) {
                messages.add(catalogFinding(
                        "features",
                        "RETIRED_FEATURE",
                        selected.featureName() + " is retired",
                        "The selected feature “" + selected.featureName() + "” was retired after it was added to this draft.",
                        "Review the draft before publishing or replace the retired feature.",
                        ValidationSeverity.WARNING,
                        false,
                        "CATALOG_STATUS",
                        "FEATURE",
                        selected.featureCode(),
                        selected.featureName(),
                        "FEATURE",
                        "ACTIVE",
                        "Active catalog feature",
                        "features",
                        "Review Features"
                ));
            }
        }

        for (SelectedLimit selected : snapshot.limits() == null ? List.<SelectedLimit>of() : snapshot.limits()) {
            CommercialLimitDefinitionEntity entity = limits.get(selected.limitDefinitionId());
            if (entity == null) {
                messages.add(planFinding(
                        template,
                        "UNKNOWN_LIMIT",
                        "Selected limit definition is no longer available",
                        "The selected limit definition could not be found in the catalog.",
                        "Select a limit that still exists in the catalog.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        selected.limitName(),
                        "Active limit definition",
                        "limits",
                        "Review Limits"
                ));
                continue;
            }
            if (entity.getStatus() == Status.RETIRED && !selectedPreviously(template.getId(), selected.limitDefinitionId(), "limits")) {
                messages.add(catalogFinding(
                        "limits",
                        "RETIRED_LIMIT",
                        selected.limitName() + " is retired",
                        "The selected limit definition “" + selected.limitName() + "” is retired and cannot be newly published.",
                        "Choose an active limit definition.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        "LIMIT",
                        selected.limitCode(),
                        selected.limitName(),
                        "LIMIT",
                        "ACTIVE",
                        "Active catalog limit definition",
                        "limits",
                        "Review Limits"
                ));
            } else if (entity.getStatus() == Status.RETIRED) {
                messages.add(catalogFinding(
                        "limits",
                        "RETIRED_LIMIT",
                        selected.limitName() + " is retired",
                        "The selected limit definition “" + selected.limitName() + "” was retired after it was added to this draft.",
                        "Review the draft before publishing or replace the retired limit.",
                        ValidationSeverity.WARNING,
                        false,
                        "CATALOG_STATUS",
                        "LIMIT",
                        selected.limitCode(),
                        selected.limitName(),
                        "LIMIT",
                        "ACTIVE",
                        "Active catalog limit definition",
                        "limits",
                        "Review Limits"
                ));
            }
            if (!isValueCompatible(entity.getValueType(), selected.configuredValue())) {
                messages.add(limitValueFinding(template, selected, entity));
            }
        }

        for (SelectedAddon selected : snapshot.addons() == null ? List.<SelectedAddon>of() : snapshot.addons()) {
            CommercialAddonOfferEntity entity = addons.get(selected.addonId());
            if (entity == null) {
                messages.add(planFinding(
                        template,
                        "UNKNOWN_ADDON",
                        "Selected add-on is no longer available",
                        "The selected add-on could not be found in the catalog.",
                        "Select an add-on that still exists in the catalog.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        selected.addonName(),
                        "Active add-on",
                        "addons",
                        "Review Add-ons"
                ));
                continue;
            }
            if (entity.getStatus() == Status.RETIRED && !selectedPreviously(template.getId(), selected.addonId(), "addons")) {
                messages.add(catalogFinding(
                        "addons",
                        "RETIRED_ADDON",
                        selected.addonName() + " is retired",
                        "The selected add-on “" + selected.addonName() + "” is retired and cannot be newly published.",
                        "Choose an active add-on.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "CATALOG_STATUS",
                        "ADDON",
                        selected.addonCode(),
                        selected.addonName(),
                        "ADDON",
                        "ACTIVE",
                        "Active catalog add-on",
                        "addons",
                        "Review Add-ons"
                ));
            } else if (entity.getStatus() == Status.RETIRED) {
                messages.add(catalogFinding(
                        "addons",
                        "RETIRED_ADDON",
                        selected.addonName() + " is retired",
                        "The selected add-on “" + selected.addonName() + "” was retired after it was added to this draft.",
                        "Review the draft before publishing or replace the retired add-on.",
                        ValidationSeverity.WARNING,
                        false,
                        "CATALOG_STATUS",
                        "ADDON",
                        selected.addonCode(),
                        selected.addonName(),
                        "ADDON",
                        "ACTIVE",
                        "Active catalog add-on",
                        "addons",
                        "Review Add-ons"
                ));
            }
            if (selected.selectionState() == SelectionState.UNAVAILABLE && publishing) {
                messages.add(catalogFinding(
                        "addons",
                        "UNAVAILABLE_ADDON",
                        selected.addonName() + " is unavailable",
                        "The selected add-on “" + selected.addonName() + "” is marked unavailable in this draft.",
                        "Remove the unavailable add-on or mark it available.",
                        ValidationSeverity.BLOCKING,
                        true,
                        "ADDON_COMPATIBILITY",
                        "ADDON",
                        selected.addonCode(),
                        selected.addonName(),
                        "ADDON",
                        "AVAILABLE_OR_INCLUDED",
                        "Set the add-on to Available or Included",
                        "addons",
                        "Review Add-ons"
                ));
            }
        }

        return messages;
    }

    private ValidationResult buildValidationResult(CommercialPlanTemplateEntity template, CommercialPlanDraftEntity draft, PlanConfigurationSnapshot snapshot, boolean publishing) {
        List<ValidationMessageResponse> messages = validateDraftConfiguration(template, snapshot, draft.getRevision(), publishing);
        int blockingCount = (int) messages.stream().filter(ValidationMessageResponse::blocking).count();
        int warningCount = (int) messages.stream().filter(message -> message.severity() == ValidationSeverity.WARNING).count();
        OffsetDateTime validatedAt = draft.getLastValidatedAt();
        ValidationState validationState;
        if (validatedAt == null) {
            validationState = ValidationState.NOT_VALIDATED;
        } else if (draft.getUpdatedAt() != null && draft.getUpdatedAt().isAfter(validatedAt)) {
            validationState = ValidationState.STALE;
        } else if (blockingCount == 0) {
            validationState = ValidationState.VALID;
        } else {
            validationState = ValidationState.INVALID;
        }
        return new ValidationResult(messages, validationState, validationState == ValidationState.VALID, blockingCount, warningCount, draft.getRevision(), validatedAt);
    }

    private PlanConfigurationSnapshot buildSnapshot(CommercialPlanTemplateEntity template, SavePlanDraftRequest request) {
        return new PlanConfigurationSnapshot(
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getTargetSegment(),
                template.getStatus(),
                template.getDisplayOrder(),
                mapCapabilities(request == null ? List.of() : request.capabilities()),
                mapModules(request == null ? List.of() : request.modules()),
                mapFeatures(request == null ? List.of() : request.features()),
                mapLimits(request == null ? List.of() : request.limits()),
                mapAddons(request == null ? List.of() : request.addons())
        );
    }

    private PlanConfigurationSnapshot buildPublishedSnapshot(CommercialPlanTemplateEntity template, CommercialPlanDraftEntity draft, PlanConfigurationSnapshot snapshot, PublishPlanVersionRequest request) {
        return new PlanConfigurationSnapshot(
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getTargetSegment(),
                template.getStatus(),
                template.getDisplayOrder(),
                snapshot.capabilities(),
                snapshot.modules(),
                snapshot.features(),
                snapshot.limits(),
                snapshot.addons()
        );
    }

    private List<SelectedCapability> mapCapabilities(List<SelectedCapabilityRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        Map<UUID, CommercialCapabilityEntity> lookup = capabilityRepository.findAllById(requests.stream().map(SelectedCapabilityRequest::capabilityId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CommercialCapabilityEntity::getId, Function.identity()));
        return requests.stream()
                .map(request -> {
                    CommercialCapabilityEntity entity = lookup.get(request.capabilityId());
                    if (entity == null) {
                        return null;
                    }
                    return new SelectedCapability(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getDisplayOrder(), entity.getStatus() == Status.RETIRED);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SelectedCapability::capabilityCode))
                .toList();
    }

    private List<SelectedModule> mapModules(List<SelectedModuleRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        Map<UUID, CommercialModuleEntity> lookup = moduleRepository.findAllById(requests.stream().map(SelectedModuleRequest::moduleId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CommercialModuleEntity::getId, Function.identity()));
        return requests.stream()
                .map(request -> {
                    CommercialModuleEntity entity = lookup.get(request.moduleId());
                    if (entity == null) {
                        return null;
                    }
                    SelectionSource selectionSource = request.selectionSource() == null ? SelectionSource.EXPLICIT : request.selectionSource();
                    return new SelectedModule(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getRuntimeModuleCode(), entity.getDisplayOrder(), Boolean.TRUE.equals(request.inherited()), selectionSource, entity.getStatus() == Status.RETIRED);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SelectedModule::moduleCode))
                .toList();
    }

    private List<SelectedFeature> mapFeatures(List<SelectedFeatureRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        Map<UUID, CommercialFeatureEntity> lookup = featureRepository.findAllById(requests.stream().map(SelectedFeatureRequest::featureId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CommercialFeatureEntity::getId, Function.identity()));
        return requests.stream()
                .map(request -> {
                    CommercialFeatureEntity entity = lookup.get(request.featureId());
                    if (entity == null) {
                        return null;
                    }
                    return new SelectedFeature(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getModule().getId(), entity.getModule().getCode(), entity.getModule().getName(), entity.getDisplayOrder(), entity.getStatus() == Status.RETIRED);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SelectedFeature::featureCode))
                .toList();
    }

    private List<SelectedLimit> mapLimits(List<ConfiguredLimitRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        Map<UUID, CommercialLimitDefinitionEntity> lookup = limitRepository.findAllById(requests.stream().map(ConfiguredLimitRequest::limitDefinitionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CommercialLimitDefinitionEntity::getId, Function.identity()));
        return requests.stream()
                .map(request -> {
                    CommercialLimitDefinitionEntity entity = lookup.get(request.limitDefinitionId());
                    if (entity == null) {
                        return null;
                    }
                    return new SelectedLimit(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getUnit(), entity.getValueType(), entity.getAggregationPeriod(), entity.getEnforcementMode(), blankToNull(request.configuredValue()), entity.getDisplayOrder(), entity.getStatus() == Status.RETIRED);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SelectedLimit::limitCode))
                .toList();
    }

    private List<SelectedAddon> mapAddons(List<SelectedAddonRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        Map<UUID, CommercialAddonOfferEntity> lookup = addonRepository.findAllById(requests.stream().map(SelectedAddonRequest::addonId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CommercialAddonOfferEntity::getId, Function.identity()));
        return requests.stream()
                .map(request -> {
                    CommercialAddonOfferEntity entity = lookup.get(request.addonId());
                    if (entity == null) {
                        return null;
                    }
                    return new SelectedAddon(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getAddonType(), entity.getDisplayOrder(), request.selectionState() == null ? SelectionState.AVAILABLE : request.selectionState(), entity.getStatus() == Status.RETIRED);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SelectedAddon::addonCode))
                .toList();
    }

    private DraftConfigurationResponse toDraftConfiguration(PlanConfigurationSnapshot snapshot) {
        return new DraftConfigurationResponse(
                snapshot.capabilities() == null ? List.of() : snapshot.capabilities().stream().map(this::toCapabilityDraft).sorted(Comparator.comparing(DraftCapabilityResponse::capabilityCode)).toList(),
                snapshot.modules() == null ? List.of() : snapshot.modules().stream().map(this::toModuleDraft).sorted(Comparator.comparing(DraftModuleResponse::moduleCode)).toList(),
                snapshot.features() == null ? List.of() : snapshot.features().stream().map(this::toFeatureDraft).sorted(Comparator.comparing(DraftFeatureResponse::featureCode)).toList(),
                snapshot.limits() == null ? List.of() : snapshot.limits().stream().map(this::toLimitDraft).sorted(Comparator.comparing(DraftLimitResponse::limitCode)).toList(),
                snapshot.addons() == null ? List.of() : snapshot.addons().stream().map(this::toAddonDraft).sorted(Comparator.comparing(DraftAddonResponse::addonCode)).toList()
        );
    }

    private DraftCapabilityResponse toCapabilityDraft(SelectedCapability capability) {
        return new DraftCapabilityResponse(capability.capabilityId(), capability.capabilityCode(), capability.capabilityName(), capability.description(), capability.displayOrder(), true, capability.retired());
    }

    private DraftModuleResponse toModuleDraft(SelectedModule module) {
        return new DraftModuleResponse(module.moduleId(), module.moduleCode(), module.moduleName(), module.description(), module.runtimeModuleCode(), module.displayOrder(), true, module.selectionSource() == SelectionSource.INHERITED, module.selectionSource(), module.retired());
    }

    private DraftFeatureResponse toFeatureDraft(SelectedFeature feature) {
        return new DraftFeatureResponse(feature.featureId(), feature.featureCode(), feature.featureName(), feature.description(), feature.moduleId(), feature.moduleCode(), feature.moduleName(), feature.displayOrder(), true, feature.retired());
    }

    private DraftLimitResponse toLimitDraft(SelectedLimit limit) {
        return new DraftLimitResponse(limit.limitDefinitionId(), limit.limitCode(), limit.limitName(), limit.description(), limit.unit(), limit.valueType(), limit.aggregationPeriod(), limit.enforcementMode(), limit.configuredValue(), limit.displayOrder(), true, limit.retired());
    }

    private DraftAddonResponse toAddonDraft(SelectedAddon addon) {
        return new DraftAddonResponse(addon.addonId(), addon.addonCode(), addon.addonName(), addon.description(), addon.addonType(), addon.displayOrder(), addon.selectionState(), addon.retired());
    }

    private PlanDraftResponse toDraftResponse(CommercialPlanTemplateEntity template, CommercialPlanDraftEntity draft, ValidationResult validation) {
        PlanConfigurationSnapshot snapshot = parseSnapshot(draft.getConfigJson());
        return new PlanDraftResponse(
                draft.getId(),
                template.getId(),
                draft.getRevision(),
                draft.getStatus(),
                draft.getDraftNotes(),
                validation.validationState().name(),
                validation.readyToPublish(),
                validation.toResponse(),
                draft.getUpdatedAt(),
                draft.getUpdatedBy(),
                toDraftConfiguration(snapshot),
                validation.messages()
        );
    }

    private TemplateSummaryResponse toTemplateSummary(CommercialPlanTemplateEntity template) {
        CommercialPlanDraftEntity draft = ensureDraft(template);
        ValidationResult validation = buildValidationResult(template, draft, parseSnapshot(draft.getConfigJson()), false);
        return new TemplateSummaryResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getTargetSegment(),
                template.getStatus(),
                template.getDisplayOrder(),
                draft.getRevision(),
                template.getLatestPublishedVersionNumber(),
                draft.getStatus(),
                validation.readyToPublish(),
                validation.toResponse(),
                countItems(draft.getConfigJson(), "capabilities"),
                countItems(draft.getConfigJson(), "modules"),
                countItems(draft.getConfigJson(), "features"),
                countItems(draft.getConfigJson(), "limits"),
                countItems(draft.getConfigJson(), "addons"),
                template.getUpdatedAt()
        );
    }

    private TemplateDetailResponse toTemplateDetail(CommercialPlanTemplateEntity template, CommercialPlanDraftEntity draft, CommercialPlanVersionEntity latestVersion) {
        ValidationResult validation = buildValidationResult(template, draft, parseSnapshot(draft.getConfigJson()), false);
        return new TemplateDetailResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getTargetSegment(),
                template.getStatus(),
                template.getDisplayOrder(),
                draft.getRevision(),
                template.getLatestPublishedVersionNumber(),
                draft.getStatus(),
                validation.readyToPublish(),
                validation.toResponse(),
                template.getUpdatedAt(),
                toDraftResponse(template, draft, validation),
                latestVersion == null ? null : toVersionSummary(latestVersion, null)
        );
    }

    private PlanVersionSummaryResponse toVersionSummary(CommercialPlanVersionEntity version, CommercialPlanVersionEntity previous) {
        return new PlanVersionSummaryResponse(
                version.getId(),
                version.getTemplate().getId(),
                version.getVersionNumber(),
                version.getVersionLabel(),
                version.getStatus(),
                version.getPublishedAt(),
                version.getPublishedBy(),
                version.getPublicationNotes(),
                version.getSourceDraftRevision(),
                shortenHash(version.getContentHash()),
                version.getCapabilityCount(),
                version.getModuleCount(),
                version.getFeatureCount(),
                version.getLimitCount(),
                version.getAddonCount(),
                buildChangeSummary(version, previous)
        );
    }

    private PlanVersionDetailResponse toVersionDetail(CommercialPlanVersionEntity version) {
        return new PlanVersionDetailResponse(
                version.getId(),
                version.getTemplate().getId(),
                version.getVersionNumber(),
                version.getVersionLabel(),
                version.getStatus(),
                version.getPublishedAt(),
                version.getPublishedBy(),
                version.getPublicationNotes(),
                version.getSourceDraftRevision(),
                version.getContentHash(),
                version.getCapabilityCount(),
                version.getModuleCount(),
                version.getFeatureCount(),
                version.getLimitCount(),
                version.getAddonCount(),
                version.getSnapshotJson()
        );
    }

    private <T> ComparisonSectionResponse compareSection(List<T> left, List<T> right, Function<T, String> codeFn, Function<T, String> nameFn, Function<T, String> detailFn, String noun) {
        Map<String, T> leftMap = mapByCode(left, codeFn);
        Map<String, T> rightMap = mapByCode(right, codeFn);
        List<ComparisonEntryResponse> added = new ArrayList<>();
        List<ComparisonEntryResponse> removed = new ArrayList<>();
        List<ComparisonEntryResponse> changed = new ArrayList<>();
        for (String code : rightMap.keySet()) {
            if (!leftMap.containsKey(code)) {
                T item = rightMap.get(code);
                added.add(new ComparisonEntryResponse(code, nameFn.apply(item), detailFn.apply(item)));
            }
        }
        for (String code : leftMap.keySet()) {
            if (!rightMap.containsKey(code)) {
                T item = leftMap.get(code);
                removed.add(new ComparisonEntryResponse(code, nameFn.apply(item), detailFn.apply(item)));
            }
        }
        return new ComparisonSectionResponse(added, removed, changed);
    }

    private ComparisonSectionResponse compareLimitSection(List<SelectedLimit> left, List<SelectedLimit> right) {
        Map<String, SelectedLimit> leftMap = mapByCode(left, SelectedLimit::limitCode);
        Map<String, SelectedLimit> rightMap = mapByCode(right, SelectedLimit::limitCode);
        List<ComparisonEntryResponse> added = new ArrayList<>();
        List<ComparisonEntryResponse> removed = new ArrayList<>();
        List<ComparisonEntryResponse> changed = new ArrayList<>();
        for (String code : rightMap.keySet()) {
            if (!leftMap.containsKey(code)) {
                SelectedLimit item = rightMap.get(code);
                added.add(new ComparisonEntryResponse(code, item.limitName(), item.configuredValue()));
            } else {
                SelectedLimit leftItem = leftMap.get(code);
                SelectedLimit rightItem = rightMap.get(code);
                if (!Objects.equals(leftItem.configuredValue(), rightItem.configuredValue())) {
                    changed.add(new ComparisonEntryResponse(code, rightItem.limitName(), stringify(leftItem.configuredValue()) + " -> " + stringify(rightItem.configuredValue())));
                }
            }
        }
        for (String code : leftMap.keySet()) {
            if (!rightMap.containsKey(code)) {
                SelectedLimit item = leftMap.get(code);
                removed.add(new ComparisonEntryResponse(code, item.limitName(), stringify(item.configuredValue())));
            }
        }
        return new ComparisonSectionResponse(added, removed, changed);
    }

    private ComparisonSectionResponse compareAddonSection(List<SelectedAddon> left, List<SelectedAddon> right) {
        Map<String, SelectedAddon> leftMap = mapByCode(left, SelectedAddon::addonCode);
        Map<String, SelectedAddon> rightMap = mapByCode(right, SelectedAddon::addonCode);
        List<ComparisonEntryResponse> added = new ArrayList<>();
        List<ComparisonEntryResponse> removed = new ArrayList<>();
        List<ComparisonEntryResponse> changed = new ArrayList<>();
        for (String code : rightMap.keySet()) {
            if (!leftMap.containsKey(code)) {
                SelectedAddon item = rightMap.get(code);
                added.add(new ComparisonEntryResponse(code, item.addonName(), item.selectionState().name()));
            } else if (leftMap.get(code).selectionState() != rightMap.get(code).selectionState()) {
                changed.add(new ComparisonEntryResponse(code, rightMap.get(code).addonName(), leftMap.get(code).selectionState().name() + " -> " + rightMap.get(code).selectionState().name()));
            }
        }
        for (String code : leftMap.keySet()) {
            if (!rightMap.containsKey(code)) {
                SelectedAddon item = leftMap.get(code);
                removed.add(new ComparisonEntryResponse(code, item.addonName(), item.selectionState().name()));
            }
        }
        return new ComparisonSectionResponse(added, removed, changed);
    }

    private TemplateMetadataComparisonResponse compareMetadata(PlanConfigurationSnapshot left, PlanConfigurationSnapshot right) {
        List<ComparisonEntryResponse> changed = new ArrayList<>();
        compareMetadataField(changed, "name", left.templateName(), right.templateName());
        compareMetadataField(changed, "description", left.templateDescription(), right.templateDescription());
        compareMetadataField(changed, "targetSegment", stringValue(left.targetSegment()), stringValue(right.targetSegment()));
        compareMetadataField(changed, "status", stringValue(left.templateStatus()), stringValue(right.templateStatus()));
        compareMetadataField(changed, "displayOrder", stringValue(left.displayOrder()), stringValue(right.displayOrder()));
        return new TemplateMetadataComparisonResponse(changed);
    }

    private void compareMetadataField(List<ComparisonEntryResponse> changed, String code, String left, String right) {
        if (!Objects.equals(left, right)) {
            changed.add(new ComparisonEntryResponse(code, code, stringify(left) + " -> " + stringify(right)));
        }
    }

    private String buildChangeSummary(CommercialPlanVersionEntity current, CommercialPlanVersionEntity previous) {
        if (previous == null) {
            return "Initial version";
        }
        List<String> parts = new ArrayList<>();
        appendChange(parts, "Capabilities", previous.getCapabilityCount(), current.getCapabilityCount());
        appendChange(parts, "Modules", previous.getModuleCount(), current.getModuleCount());
        appendChange(parts, "Features", previous.getFeatureCount(), current.getFeatureCount());
        appendChange(parts, "Limits", previous.getLimitCount(), current.getLimitCount());
        appendChange(parts, "Add-ons", previous.getAddonCount(), current.getAddonCount());
        return parts.isEmpty() ? "No structural changes" : String.join(" · ", parts);
    }

    private void appendChange(List<String> parts, String label, int before, int after) {
        if (before != after) {
            parts.add(label + " " + before + "→" + after);
        }
    }

    private <T> Map<String, T> mapByCode(List<T> items, Function<T, String> codeFn) {
        Map<String, T> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (T item : items) {
            if (item == null) continue;
            result.put(codeFn.apply(item), item);
        }
        return result;
    }

    private boolean selectedPreviously(UUID templateId, UUID selectionId, String category) {
        return draftRepository.findByTemplate_Id(templateId)
                .map(CommercialPlanDraftEntity::getConfigJson)
                .map(this::parseSnapshot)
                .map(snapshot -> switch (category) {
                    case "capabilities" -> snapshot.capabilities().stream().anyMatch(item -> item.capabilityId().equals(selectionId));
                    case "modules" -> snapshot.modules().stream().anyMatch(item -> item.moduleId().equals(selectionId));
                    case "features" -> snapshot.features().stream().anyMatch(item -> item.featureId().equals(selectionId));
                    case "limits" -> snapshot.limits().stream().anyMatch(item -> item.limitDefinitionId().equals(selectionId));
                    case "addons" -> snapshot.addons().stream().anyMatch(item -> item.addonId().equals(selectionId));
                    default -> false;
                })
                .orElse(false);
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
                    List.of()
            );
            List<ValidationMessageResponse> findings = validateDraftConfiguration(template, snapshot, template.getCurrentDraftRevision(), false);
            CommercialPlanDraftEntity created = CommercialPlanDraftEntity.create(
                    template,
                    template.getCurrentDraftRevision(),
                    serialize(snapshot),
                    hash(serialize(snapshot)),
                    serialize(findings),
                    DraftStatus.BLOCKED,
                    false,
                    now(),
                    currentActor()
            );
            return draftRepository.save(created);
        });
    }

    private Specification<CommercialPlanTemplateEntity> templateSpec(String search, TemplateStatus status, TargetSegment targetSegment) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (targetSegment != null) {
                predicates.add(cb.equal(root.get("targetSegment"), targetSegment));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Specification<CommercialCapabilityEntity> activeCapabilitySpec() {
        return (root, query, cb) -> cb.equal(root.get("status"), Status.ACTIVE);
    }

    private Specification<CommercialModuleEntity> activeModuleSpec() {
        return (root, query, cb) -> cb.equal(root.get("status"), Status.ACTIVE);
    }

    private Specification<CommercialFeatureEntity> activeFeatureSpec() {
        return (root, query, cb) -> cb.equal(root.get("status"), Status.ACTIVE);
    }

    private Specification<CommercialLimitDefinitionEntity> activeLimitSpec() {
        return (root, query, cb) -> cb.equal(root.get("status"), Status.ACTIVE);
    }

    private Specification<CommercialAddonOfferEntity> activeAddonSpec() {
        return (root, query, cb) -> cb.equal(root.get("status"), Status.ACTIVE);
    }

    private boolean isValueCompatible(LimitValueType valueType, String configuredValue) {
        if (configuredValue == null || configuredValue.isBlank()) {
            return false;
        }
        return switch (valueType) {
            case INTEGER -> configuredValue.matches("^\\d+$");
            case DECIMAL -> configuredValue.matches("^\\d+(\\.\\d+)?$");
            case BOOLEAN -> "true".equalsIgnoreCase(configuredValue) || "false".equalsIgnoreCase(configuredValue);
        };
    }

    private ValidationMessageResponse planFinding(
            CommercialPlanTemplateEntity template,
            String code,
            String title,
            String message,
            String remediation,
            ValidationSeverity severity,
            boolean blocking,
            String category,
            String currentValue,
            String expectedValue,
            String targetBuilderTab,
            String actionLabel
    ) {
        return finding(
                "plan",
                code,
                title,
                message,
                remediation,
                severity,
                blocking,
                category,
                "PLAN_TEMPLATE",
                template.getCode(),
                template.getName(),
                "PLAN_TEMPLATE",
                template.getCode(),
                template.getName(),
                currentValue,
                expectedValue,
                targetBuilderTab,
                actionLabel
        );
    }

    private ValidationMessageResponse catalogFinding(
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
            String targetBuilderTab,
            String actionLabel
    ) {
        return finding(
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
                affectedItemName,
                expectedItemName,
                targetBuilderTab,
                actionLabel
        );
    }

    private ValidationMessageResponse featureDependencyFinding(CommercialPlanTemplateEntity template, SelectedFeature selected, CommercialFeatureEntity entity) {
        return finding(
                "features",
                "FEATURE_PARENT_MODULE_REQUIRED",
                selected.featureName() + " requires " + entity.getModule().getName(),
                "The feature “" + selected.featureName() + "” is selected, but its parent module “" + entity.getModule().getName() + "” is not included in this plan.",
                "Add the " + entity.getModule().getName() + " module or remove " + selected.featureName() + " from the selected features.",
                ValidationSeverity.BLOCKING,
                true,
                "FEATURE_DEPENDENCY",
                "FEATURE",
                selected.featureCode(),
                selected.featureName(),
                "MODULE",
                entity.getModule().getCode(),
                entity.getModule().getName(),
                "Parent module not included",
                "Module included",
                "modules",
                "Add Required Module"
        );
    }

    private ValidationMessageResponse limitValueFinding(CommercialPlanTemplateEntity template, SelectedLimit selected, CommercialLimitDefinitionEntity entity) {
        String expectedValue = switch (entity.getValueType()) {
            case INTEGER -> "A non-negative whole number";
            case DECIMAL -> "A non-negative decimal value";
            case BOOLEAN -> "A true or false value";
        };
        String title = selected.limitName() + " requires a " + switch (entity.getValueType()) {
            case INTEGER -> "whole-number";
            case DECIMAL -> "decimal";
            case BOOLEAN -> "boolean";
        } + " value";
        String currentValue = StringUtils.hasText(selected.configuredValue()) ? "Configured value: " + selected.configuredValue() : "No value configured";
        return finding(
                "limits",
                "LIMIT_VALUE_TYPE",
                title,
                "The limit “" + selected.limitName() + "” has a value that does not match its configured type.",
                "Enter a value that matches the configured type.",
                ValidationSeverity.BLOCKING,
                true,
                "LIMIT_CONFIGURATION",
                "LIMIT",
                selected.limitCode(),
                selected.limitName(),
                "LIMIT",
                selected.limitCode(),
                selected.limitName(),
                currentValue,
                expectedValue,
                "limits",
                "Configure Limits"
        );
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

    private PlanConfigurationSnapshot parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return new PlanConfigurationSnapshot(null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        try {
            return objectMapper.readValue(json, PlanConfigurationSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse commercial plan snapshot", ex);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize commercial plan snapshot", ex);
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
            throw new IllegalStateException("Unable to hash commercial plan snapshot", ex);
        }
    }

    private String shortenHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return "";
        }
        return hash.length() <= 12 ? hash : hash.substring(0, 12);
    }

    private String stringify(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private UUID currentActor() {
        return RequestContextHolder.get() == null ? null : RequestContextHolder.get().appUserId();
    }

    private String normalizeCode(String input) {
        return requireText(input, "code is required")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private ResponseStatusException notFound(String type, UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found: " + id);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record ValidationResult(
            List<ValidationMessageResponse> messages,
            ValidationState validationState,
            boolean readyToPublish,
            int blockingFindingCount,
            int warningFindingCount,
            int validatedDraftRevision,
            OffsetDateTime validatedAt
    ) {
        List<ValidationMessageResponse> blockingMessages() {
            return messages.stream().filter(ValidationMessageResponse::blocking).toList();
        }

        PlanValidationResultResponse toResponse() {
            return new PlanValidationResultResponse(validationState, readyToPublish, blockingFindingCount, warningFindingCount, messages, validatedDraftRevision, validatedAt);
        }
    }

    private int countItems(String json, String key) {
        PlanConfigurationSnapshot snapshot = parseSnapshot(json);
        return switch (key) {
            case "capabilities" -> snapshot.capabilities() == null ? 0 : snapshot.capabilities().size();
            case "modules" -> snapshot.modules() == null ? 0 : snapshot.modules().size();
            case "features" -> snapshot.features() == null ? 0 : snapshot.features().size();
            case "limits" -> snapshot.limits() == null ? 0 : snapshot.limits().size();
            case "addons" -> snapshot.addons() == null ? 0 : snapshot.addons().size();
            default -> 0;
        };
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
        var ctx = RequestContextHolder.get();
        if (ctx != null && ctx.tenantId() != null) {
            return ctx.tenantId().value();
        }
        return PLATFORM_AUDIT_TENANT_ID;
    }

    private PlanConfigurationSnapshot resolveCloneSnapshot(CommercialPlanTemplateEntity sourceTemplate, UUID sourceVersionId) {
        if (sourceVersionId != null) {
            CommercialPlanVersionEntity version = versionRepository.findById(sourceVersionId).orElseThrow(() -> notFound("Plan version", sourceVersionId));
            if (!version.getTemplate().getId().equals(sourceTemplate.getId())) {
                throw conflict("Source version does not belong to the selected template");
            }
            return parseSnapshot(version.getSnapshotJson());
        }
        return versionRepository.findTopByTemplate_IdOrderByVersionNumberDesc(sourceTemplate.getId())
                .map(version -> parseSnapshot(version.getSnapshotJson()))
                .orElseGet(() -> parseSnapshot(ensureDraft(sourceTemplate).getConfigJson()));
    }
}
