package com.deepthoughtnet.clinic.commercial.entitlement;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AddonType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AggregationPeriod;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.EnforcementMode;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.LimitValueType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonFeatureEntity;
import com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonLimitIncrementEntity;
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
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.AddOnEffectiveState;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.ComparisonCategory;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.GenerationReason;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideOperation;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideStatus;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideTargetType;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.SnapshotStatus;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.SourceType;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.AddOnContributionResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.CapabilityResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.ComparisonItemResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.CreateOverrideRequest;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.EffectiveEntitlementSnapshotResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.EffectiveLimitResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.FeatureResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.LegacyComparisonResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.LimitResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.ModuleResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.OverrideHistoryResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.OverrideResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.OverrideImpactPreviewResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.PageResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.ProvenanceResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.RuntimeDiffDashboardResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.RuntimeDiffSummaryResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.RuntimeDiffTenantResponse;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.UpdateOverrideRequest;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.ValidationFinding;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialEffectiveEntitlementEventEntity;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialEffectiveEntitlementEventRepository;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialEffectiveEntitlementSnapshotEntity;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialEffectiveEntitlementSnapshotRepository;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialTenantEntitlementOverrideEntity;
import com.deepthoughtnet.clinic.commercial.entitlement.db.CommercialTenantEntitlementOverrideRepository;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PlanConfigurationSnapshot;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedAddon;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedCapability;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedFeature;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedLimit;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SelectedModule;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionRepository;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionEntity;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventAction;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommercialEffectiveEntitlementService {
    private static final UUID PLATFORM_AUDIT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final CommercialTenantSubscriptionRepository subscriptionRepository;
    private final CommercialPlanTemplateRepository templateRepository;
    private final CommercialPlanVersionRepository versionRepository;
    private final CommercialCapabilityRepository capabilityRepository;
    private final CommercialModuleRepository moduleRepository;
    private final CommercialFeatureRepository featureRepository;
    private final CommercialLimitDefinitionRepository limitRepository;
    private final CommercialAddonOfferRepository addonRepository;
    private final CommercialTenantEntitlementOverrideRepository overrideRepository;
    private final CommercialEffectiveEntitlementSnapshotRepository snapshotRepository;
    private final CommercialEffectiveEntitlementEventRepository eventRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final CommercialRuntimeProperties runtimeProperties;
    private final ObjectMapper objectMapper;

    public CommercialEffectiveEntitlementService(
            CommercialTenantSubscriptionRepository subscriptionRepository,
            CommercialPlanTemplateRepository templateRepository,
            CommercialPlanVersionRepository versionRepository,
            CommercialCapabilityRepository capabilityRepository,
            CommercialModuleRepository moduleRepository,
            CommercialFeatureRepository featureRepository,
            CommercialLimitDefinitionRepository limitRepository,
            CommercialAddonOfferRepository addonRepository,
            CommercialTenantEntitlementOverrideRepository overrideRepository,
            CommercialEffectiveEntitlementSnapshotRepository snapshotRepository,
            CommercialEffectiveEntitlementEventRepository eventRepository,
            AuditEventPublisher auditEventPublisher,
            CommercialRuntimeProperties runtimeProperties,
            ObjectMapper objectMapper
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.capabilityRepository = capabilityRepository;
        this.moduleRepository = moduleRepository;
        this.featureRepository = featureRepository;
        this.limitRepository = limitRepository;
        this.addonRepository = addonRepository;
        this.overrideRepository = overrideRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.runtimeProperties = runtimeProperties;
        this.objectMapper = objectMapper.copy().findAndRegisterModules().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional(readOnly = true)
    public EffectiveEntitlementSnapshotResponse getCurrentSnapshot(UUID tenantId) {
        return snapshotRepository.findTopByTenantIdAndSnapshotStatusOrderByGeneratedAtDesc(tenantId, SnapshotStatus.CURRENT)
                .map(this::toResponse)
                .orElseGet(() -> calculateForTenant(tenantId, GenerationReason.MANUAL_REGENERATE, false));
    }

    @Transactional(readOnly = true)
    public PageResponse<EffectiveEntitlementSnapshotResponse> getSnapshotHistory(UUID tenantId, int page, int size) {
        var all = snapshotRepository.findByTenantIdOrderByGeneratedAtDesc(tenantId).stream().map(this::toResponse).toList();
        int fromIndex = Math.min(page * size, all.size());
        int toIndex = Math.min(fromIndex + size, all.size());
        return new PageResponse<>(all.subList(fromIndex, toIndex), page, size, all.size(), size == 0 ? 0 : (int) Math.ceil(all.size() / (double) size));
    }

    @Transactional(readOnly = true)
    public List<OverrideResponse> listOverrides(UUID tenantId) {
        return overrideRepository.findByTenantIdOrderByEffectiveFromAscUpdatedAtAsc(tenantId).stream().map(this::toOverrideResponse).toList();
    }

    @Transactional(readOnly = true)
    public EffectiveLimitResponse getLimit(UUID tenantId, String limitCode) {
        EffectiveEntitlementSnapshotResponse snapshot = getCurrentSnapshot(tenantId);
        return snapshot.limits().stream()
                .filter(limit -> limit.code().equalsIgnoreCase(limitCode))
                .findFirst()
                .map(limit -> new EffectiveLimitResponse(limit.code(), limit.name(), limit.configuredValue(), limit.unlimited(), limit.unit(), limit.period(), limit.enforcementType(), snapshot.snapshotId(), snapshot.generatedAt(), limit.source().name()))
                .orElseThrow(() -> notFound("Limit", limitCode));
    }

    @Transactional
    public EffectiveEntitlementSnapshotResponse regenerateCurrentSnapshot(UUID tenantId, GenerationReason reason) {
        return persistSnapshot(calculateSnapshot(tenantId, reason));
    }

    @Transactional
    public EffectiveEntitlementSnapshotResponse backfillTenant(UUID tenantId) {
        return regenerateCurrentSnapshot(tenantId, GenerationReason.BACKFILL);
    }

    @Transactional
    public EffectiveEntitlementSnapshotResponse handleSubscriptionLifecycleEvent(UUID tenantId, GenerationReason reason) {
        return regenerateCurrentSnapshot(tenantId, reason);
    }

    @Transactional
    public EffectiveEntitlementSnapshotResponse handleOverrideLifecycleEvent(UUID tenantId, GenerationReason reason) {
        return regenerateCurrentSnapshot(tenantId, reason);
    }

    @Transactional
    public OverrideResponse createOverride(UUID tenantId, CreateOverrideRequest request) {
        return saveOverride(tenantId, null, request.targetType(), request.targetCode(), request.operation(), request.value(), request.addOnState(), request.effectiveFrom(), request.effectiveUntil(), request.reason(), request.internalNotes(), request.subscriptionId(), OverrideStatus.DRAFT, false);
    }

    @Transactional
    public OverrideResponse updateOverride(UUID tenantId, UUID overrideId, UpdateOverrideRequest request) {
        CommercialTenantEntitlementOverrideEntity entity = overrideRepository.findById(overrideId).orElseThrow(() -> notFound("Override", overrideId));
        if (!tenantId.equals(entity.getTenantId())) {
            throw notFound("Override", overrideId);
        }
        return saveOverride(tenantId, entity, request.targetType(), request.targetCode(), request.operation(), request.value(), request.addOnState(), request.effectiveFrom(), request.effectiveUntil(), request.reason(), request.internalNotes(), request.subscriptionId(), entity.getStatus(), false);
    }

    @Transactional(readOnly = true)
    public List<OverrideHistoryResponse> getOverrideHistory(UUID tenantId, UUID overrideId) {
        loadOverride(tenantId, overrideId);
        return eventRepository.findByTenantIdOrderByOccurredAtDesc(tenantId).stream()
                .filter(event -> overrideId.equals(event.getOverrideId()))
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional
    public OverrideResponse submitOverride(UUID tenantId, UUID overrideId) {
        CommercialTenantEntitlementOverrideEntity entity = loadOverride(tenantId, overrideId);
        entity.submit(now(), currentActor());
        overrideRepository.save(entity);
        recordOverrideEvent(tenantId, entity.getSubscriptionId(), entity.getId(), "COMMERCIAL_OVERRIDE_SUBMITTED", "SUBMITTED", "Submitted override for approval");
        return toOverrideResponse(entity);
    }

    @Transactional
    public OverrideResponse withdrawOverride(UUID tenantId, UUID overrideId) {
        CommercialTenantEntitlementOverrideEntity entity = loadOverride(tenantId, overrideId);
        entity.withdraw(now(), currentActor());
        overrideRepository.save(entity);
        recordOverrideEvent(tenantId, entity.getSubscriptionId(), entity.getId(), "COMMERCIAL_OVERRIDE_WITHDRAWN", "DRAFT", "Withdrew override from approval");
        return toOverrideResponse(entity);
    }

    @Transactional
    public OverrideResponse requestChanges(UUID tenantId, UUID overrideId, String remarks) {
        CommercialTenantEntitlementOverrideEntity entity = loadOverride(tenantId, overrideId);
        entity.requestChanges(now(), currentActor(), blankToNull(remarks));
        overrideRepository.save(entity);
        recordOverrideEvent(tenantId, entity.getSubscriptionId(), entity.getId(), "COMMERCIAL_OVERRIDE_CHANGES_REQUESTED", "CHANGES_REQUESTED", remarks);
        return toOverrideResponse(entity);
    }

    @Transactional
    public OverrideResponse approveOverride(UUID tenantId, UUID overrideId, String remarks) {
        CommercialTenantEntitlementOverrideEntity entity = loadOverride(tenantId, overrideId);
        UUID actor = currentActor();
        if (actor != null && (actor.equals(entity.getCreatedBy()) || actor.equals(entity.getSubmittedBy()))) {
            throw conflict("Maker cannot approve own override");
        }
        entity.approve(now(), actor, blankToNull(remarks));
        overrideRepository.save(entity);
        recordOverrideEvent(tenantId, entity.getSubscriptionId(), entity.getId(), "COMMERCIAL_OVERRIDE_APPROVED", "APPROVED", remarks);
        return toOverrideResponse(entity);
    }

    @Transactional
    public OverrideResponse activateOverride(UUID tenantId, UUID overrideId) {
        CommercialTenantEntitlementOverrideEntity entity = loadOverride(tenantId, overrideId);
        entity.transition(entity.getEffectiveFrom().isAfter(LocalDate.now(ZoneOffset.UTC)) ? OverrideStatus.SCHEDULED : OverrideStatus.ACTIVE, now(), currentActor());
        overrideRepository.save(entity);
        recordOverrideEvent(tenantId, entity.getSubscriptionId(), entity.getId(), "COMMERCIAL_OVERRIDE_ACTIVATED", entity.getStatus().name(), "Activated override");
        regenerateCurrentSnapshot(tenantId, GenerationReason.OVERRIDE_UPDATED);
        return toOverrideResponse(entity);
    }

    @Transactional
    public OverrideResponse cancelOverride(UUID tenantId, UUID overrideId) {
        CommercialTenantEntitlementOverrideEntity entity = loadOverride(tenantId, overrideId);
        entity.transition(OverrideStatus.CANCELLED, now(), currentActor());
        overrideRepository.save(entity);
        recordOverrideEvent(tenantId, entity.getSubscriptionId(), entity.getId(), "COMMERCIAL_OVERRIDE_CANCELLED", "CANCELLED", "Cancelled override");
        regenerateCurrentSnapshot(tenantId, GenerationReason.OVERRIDE_RETIRED);
        return toOverrideResponse(entity);
    }

    @Transactional
    public OverrideResponse rollbackOverride(UUID tenantId, UUID overrideId, String reason) {
        CommercialTenantEntitlementOverrideEntity entity = loadOverride(tenantId, overrideId);
        entity.supersede(now(), currentActor());
        overrideRepository.save(entity);
        recordOverrideEvent(tenantId, entity.getSubscriptionId(), entity.getId(), "COMMERCIAL_OVERRIDE_SUPERSEDED", "SUPERSEDED", blankToNull(reason));
        regenerateCurrentSnapshot(tenantId, GenerationReason.OVERRIDE_RETIRED);
        return toOverrideResponse(entity);
    }

    @Transactional(readOnly = true)
    public OverrideImpactPreviewResponse previewOverride(UUID tenantId, CreateOverrideRequest request) {
        CommercialTenantSubscriptionEntity subscription = latestSubscription(tenantId);
        EffectiveEntitlementSnapshotResponse before = getCurrentSnapshot(tenantId);
        ValidationFinding[] findings = new ValidationFinding[0];
        String beforeValue = resolvePreviewValue(before, request.targetType(), request.targetCode());
        String afterValue = previewAfterValue(before, request);
        List<String> dependentEffects = previewDependentEffects(request);
        String runtimeImpact = isCommercialRuntimeEnabledForTenant(tenantId) ? "Commercial runtime may use this effective change" : "No runtime change while commercial runtime is disabled";
        return new OverrideImpactPreviewResponse(
                tenantId,
                subscription == null ? null : subscription.getId(),
                request.targetType() == null ? null : request.targetType().name(),
                normalizeCode(request.targetCode()),
                request.operation() == null ? null : request.operation().name(),
                beforeValue,
                afterValue,
                request.targetType() == null ? null : request.targetType().name(),
                runtimeImpact,
                dependentEffects,
                List.of(findings)
        );
    }

    @Transactional(readOnly = true)
    public RuntimeDiffSummaryResponse getRuntimeDiffSummary() {
        long allowlistedTenants = runtimeProperties.getTenantAllowlist() == null ? 0 : runtimeProperties.getTenantAllowlist().size();
        return new RuntimeDiffSummaryResponse(
                subscriptionRepository.findAll().stream().filter(this::isSubscriptionActive).count(),
                snapshotRepository.countBySnapshotStatus(SnapshotStatus.CURRENT),
                snapshotRepository.findAll().stream().filter(snapshot -> "MISSING".equalsIgnoreCase(snapshot.getValidationState())).count(),
                countGenerationFailures(),
                countRuntimeMismatches(),
                countRuntimeMismatches(),
                countRuntimeMismatches(),
                0L,
                countActiveOverrides(),
                countGenerationFailures(),
                runtimeProperties.isEnabled(),
                runtimeProperties.isShadowCompareEnabled(),
                allowlistedTenants
        );
    }

    @Transactional(readOnly = true)
    public List<RuntimeDiffTenantResponse> getRuntimeDiffTenants(List<RuntimeDiffTenantResponse> rows) {
        return rows == null ? List.of() : rows;
    }

    @Transactional(readOnly = true)
    public OverrideResponse approveAndActivate(UUID tenantId, UUID overrideId, String remarks) {
        approveOverride(tenantId, overrideId, remarks);
        return activateOverride(tenantId, overrideId);
    }

    @Transactional(readOnly = true)
    public LegacyComparisonResponse compareWithLegacy(UUID tenantId, Map<String, Boolean> legacyModules) {
        EffectiveEntitlementSnapshotResponse snapshot = getCurrentSnapshot(tenantId);
        Map<String, Boolean> commercialModules = snapshot.modules().stream().collect(Collectors.toMap(ModuleResponse::code, ModuleResponse::enabled, (left, right) -> left, LinkedHashMap::new));
        List<ComparisonItemResponse> moduleItems = compareSimple(legacyModules, commercialModules, "module");
        List<ComparisonItemResponse> featureItems = snapshot.features().stream()
                .map(feature -> new ComparisonItemResponse(feature.code(), feature.name(), feature.enabled() ? ComparisonCategory.COMMERCIAL_ONLY : ComparisonCategory.LEGACY_ONLY, null, String.valueOf(feature.enabled()), feature.reason()))
                .toList();
        List<ComparisonItemResponse> limitItems = snapshot.limits().stream()
                .map(limit -> new ComparisonItemResponse(limit.code(), limit.name(), ComparisonCategory.MATCH, null, limit.configuredValue(), limit.overrideSource()))
                .toList();
        return new LegacyComparisonResponse(tenantId, moduleItems, featureItems, limitItems);
    }

    @Transactional(readOnly = true)
    public boolean isCommercialRuntimeEnabledForTenant(UUID tenantId) {
        if (!runtimeProperties.isEnabled()) {
            return false;
        }
        Set<String> allowlist = runtimeProperties.getTenantAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            return true;
        }
        return allowlist.contains(String.valueOf(tenantId));
    }

    @Transactional(readOnly = true)
    public boolean isModuleEnabled(UUID tenantId, String moduleCode) {
        EffectiveEntitlementSnapshotResponse snapshot = getCurrentSnapshot(tenantId);
        if (!SnapshotStatus.CURRENT.name().equals(snapshot.snapshotStatus())) {
            return false;
        }
        return snapshot.modules().stream().anyMatch(module -> module.enabled() && module.code().equalsIgnoreCase(moduleCode));
    }

    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(UUID tenantId, String featureCode) {
        EffectiveEntitlementSnapshotResponse snapshot = getCurrentSnapshot(tenantId);
        if (!SnapshotStatus.CURRENT.name().equals(snapshot.snapshotStatus())) {
            return false;
        }
        return snapshot.features().stream().anyMatch(feature -> feature.enabled() && feature.code().equalsIgnoreCase(featureCode));
    }

    @Transactional(readOnly = true)
    public long countCurrentSnapshots() {
        return snapshotRepository.countBySnapshotStatus(SnapshotStatus.CURRENT);
    }

    @Transactional(readOnly = true)
    public long countGenerationFailures() {
        return snapshotRepository.countBySnapshotStatus(SnapshotStatus.INVALID) + snapshotRepository.countBySnapshotStatus(SnapshotStatus.PENDING_REGENERATION);
    }

    @Transactional(readOnly = true)
    public long countActiveOverrides() {
        return overrideRepository.findAll().stream().filter(override -> override.getStatus() == OverrideStatus.ACTIVE).count();
    }

    @Transactional(readOnly = true)
    public long countRuntimeMismatches() {
        return eventRepository.findAll().stream().filter(event -> "RUNTIME_MISMATCH".equalsIgnoreCase(eventType(event))).count();
    }

    private EffectiveEntitlementSnapshotResponse calculateSnapshot(UUID tenantId, GenerationReason reason) {
        CommercialTenantSubscriptionEntity subscription = latestSubscription(tenantId);
        PlanConfigurationSnapshot baseSnapshot = resolveBaseSnapshot(subscription);
        List<CommercialTenantEntitlementOverrideEntity> overrides = loadActiveOverrides(tenantId);
        SnapshotContext context = buildContext(tenantId, subscription, baseSnapshot, overrides, reason);
        return context.toResponse();
    }

    private EffectiveEntitlementSnapshotResponse calculateForTenant(UUID tenantId, GenerationReason reason, boolean persist) {
        EffectiveEntitlementSnapshotResponse response = calculateSnapshot(tenantId, reason);
        return persist ? persistSnapshot(response) : response;
    }

    private EffectiveEntitlementSnapshotResponse persistSnapshot(EffectiveEntitlementSnapshotResponse response) {
        CommercialEffectiveEntitlementSnapshotEntity current = snapshotRepository.findTopByTenantIdAndSnapshotStatusOrderByGeneratedAtDesc(response.tenantId(), SnapshotStatus.CURRENT).orElse(null);
        if (current != null && Objects.equals(current.getContentHash(), response.contentHash()) && Objects.equals(current.getSourceHash(), response.sourceHash())) {
            return response;
        }
        OffsetDateTime now = now();
        if (current != null) {
            current.supersede(now);
            snapshotRepository.save(current);
        }
        CommercialEffectiveEntitlementSnapshotEntity entity = CommercialEffectiveEntitlementSnapshotEntity.create(
                response.snapshotId(),
                response.tenantId(),
                response.subscriptionId(),
                response.planTemplateId(),
                response.publishedVersionId(),
                response.publishedVersionNumber(),
                response.subscriptionStatus(),
                response.effectiveFrom(),
                response.effectiveUntil(),
                SnapshotStatus.valueOf(response.snapshotStatus()),
                serialize(response),
                response.sourceHash(),
                response.contentHash(),
                GenerationReason.valueOf(response.generationReason()),
                response.validationState(),
                serialize(response.validationFindings()),
                response.generatedAt(),
                response.generatedBy()
        );
        snapshotRepository.save(entity);
        recordEvent(entity.getTenantId(), entity.getSubscriptionId(), entity.getId(), null, "COMMERCIAL_EFFECTIVE_ENTITLEMENT_GENERATED", entity.getGenerationReason(), entity.getValidationState(), serialize(Map.of("contentHash", entity.getContentHash(), "status", entity.getSnapshotStatus().name())));
        audit(entity.getTenantId(), "Generated effective commercial entitlement snapshot", Map.of("contentHash", entity.getContentHash(), "generationReason", entity.getGenerationReason().name()));
        return response;
    }

    private SnapshotContext buildContext(UUID tenantId, CommercialTenantSubscriptionEntity subscription, PlanConfigurationSnapshot baseSnapshot, List<CommercialTenantEntitlementOverrideEntity> overrides, GenerationReason reason) {
        OffsetDateTime now = now();
        Map<String, CapabilityResponse> capabilities = new LinkedHashMap<>();
        Map<String, ModuleResponse> modules = new LinkedHashMap<>();
        Map<String, FeatureResponse> features = new LinkedHashMap<>();
        Map<String, LimitResponse> limits = new LinkedHashMap<>();
        Map<String, AddOnContributionResponse> addOns = new LinkedHashMap<>();
        List<ProvenanceResponse> provenance = new ArrayList<>();
        List<ValidationFinding> findings = new ArrayList<>();

        applyPlanSnapshot(baseSnapshot, capabilities, modules, features, limits, addOns, provenance);
        applyOverrideStates(overrides, addOns, provenance);
        applyAddOnContributions(addOns, capabilities, modules, features, limits, provenance, findings);
        applyOverrides(overrides, capabilities, modules, features, limits, addOns, provenance, findings);
        reconcileDependencies(modules, features, provenance, findings);
        sortCanonical(capabilities, modules, features, limits, addOns, provenance);
        String sourceHash = hashSource(subscription, overrides);
        String contentHash = hashContent(tenantId, subscription, capabilities, modules, features, limits, addOns, provenance, reason);
        boolean active = subscription != null && isSubscriptionActive(subscription);
        SnapshotStatus status = active && findings.stream().noneMatch(ValidationFinding::blocking) ? SnapshotStatus.CURRENT : SnapshotStatus.INVALID;
        String validationState = findings.stream().noneMatch(ValidationFinding::blocking) ? "VALID" : "INVALID";
        OffsetDateTime effectiveFrom = subscription == null ? now : toOffset(subscription.getStartDate());
        OffsetDateTime effectiveUntil = subscription == null ? null : (subscription.getEndDate() == null ? null : subscription.getEndDate().atStartOfDay().atOffset(ZoneOffset.UTC));
        return new SnapshotContext(
                UUID.randomUUID(),
                tenantId,
                subscription == null ? null : subscription.getId(),
                subscription == null ? null : subscription.getPlanTemplate().getId(),
                subscription == null ? null : subscription.getPublishedVersion().getId(),
                subscription == null ? null : subscription.getPublishedVersion().getVersionNumber(),
                subscription == null ? null : subscription.getSubscriptionStatus().name(),
                effectiveFrom,
                effectiveUntil,
                capabilities.values().stream().toList(),
                modules.values().stream().toList(),
                features.values().stream().toList(),
                limits.values().stream().toList(),
                addOns.values().stream().toList(),
                overrides.stream().map(this::toOverrideResponse).toList(),
                provenance,
                sourceHash,
                contentHash,
                now,
                currentActorLabel(),
                reason.name(),
                status.name(),
                validationState,
                findings
        );
    }

    private void applyPlanSnapshot(
            PlanConfigurationSnapshot baseSnapshot,
            Map<String, CapabilityResponse> capabilities,
            Map<String, ModuleResponse> modules,
            Map<String, FeatureResponse> features,
            Map<String, LimitResponse> limits,
            Map<String, AddOnContributionResponse> addOns,
            List<ProvenanceResponse> provenance
    ) {
        if (baseSnapshot == null) {
            return;
        }
        for (SelectedCapability capability : baseSnapshot.capabilities() == null ? List.<SelectedCapability>of() : baseSnapshot.capabilities()) {
            capabilities.put(normalize(capability.capabilityCode()), new CapabilityResponse(capability.capabilityCode(), capability.capabilityName(), true, SourceType.PLAN, "Published plan"));
        }
        for (SelectedModule module : baseSnapshot.modules() == null ? List.<SelectedModule>of() : baseSnapshot.modules()) {
            modules.put(normalize(module.moduleCode()), new ModuleResponse(module.moduleCode(), module.moduleName(), module.runtimeModuleCode(), true, SourceType.PLAN, "Published plan", capabilityForModule(baseSnapshot, module.moduleId())));
        }
        for (SelectedFeature feature : baseSnapshot.features() == null ? List.<SelectedFeature>of() : baseSnapshot.features()) {
            features.put(normalize(feature.featureCode()), new FeatureResponse(feature.featureCode(), feature.featureName(), runtimeFeatureKey(feature.featureCode()), feature.moduleCode(), true, SourceType.PLAN, "Published plan"));
        }
        for (SelectedLimit limit : baseSnapshot.limits() == null ? List.<SelectedLimit>of() : baseSnapshot.limits()) {
            limits.put(normalize(limit.limitCode()), new LimitResponse(limit.limitCode(), limit.limitName(), limit.valueType().name(), limit.configuredValue(), false, limit.unit(), limit.aggregationPeriod().name(), limit.enforcementMode().name(), SourceType.PLAN, null));
        }
        for (SelectedAddon addon : baseSnapshot.addons() == null ? List.<SelectedAddon>of() : baseSnapshot.addons()) {
            addOns.put(normalize(addon.addonCode()), new AddOnContributionResponse(addon.addonCode(), addon.addonName(), toAddonState(addon.selectionState()), SourceType.PLAN, List.of()));
        }
        provenance.add(new ProvenanceResponse("PLAN", baseSnapshot.templateCode(), "PLAN", "Published plan version", "Base snapshot"));
    }

    private void applyOverrideStates(List<CommercialTenantEntitlementOverrideEntity> overrides, Map<String, AddOnContributionResponse> addOns, List<ProvenanceResponse> provenance) {
        for (CommercialTenantEntitlementOverrideEntity override : overrides) {
            if (override.getTargetType() != OverrideTargetType.ADD_ON || override.getOperation() != OverrideOperation.SET_ADDON_STATE) {
                continue;
            }
            AddOnEffectiveState state = parseAddonState(override.getAddOnState());
            addOns.put(normalize(override.getTargetCode()), new AddOnContributionResponse(override.getTargetCode(), catalogAddonName(override.getTargetCode()), state, SourceType.OVERRIDE, List.of("Override add-on state")));
            provenance.add(new ProvenanceResponse("ADD_ON", override.getTargetCode(), "OVERRIDE", "Override add-on state", override.getReason()));
        }
    }

    private void applyAddOnContributions(Map<String, AddOnContributionResponse> addOns, Map<String, CapabilityResponse> capabilities, Map<String, ModuleResponse> modules, Map<String, FeatureResponse> features, Map<String, LimitResponse> limits, List<ProvenanceResponse> provenance, List<ValidationFinding> findings) {
        List<CommercialAddonOfferEntity> includedAddOns = addonRepository.findAllById(addOns.values().stream().filter(item -> item.state() == AddOnEffectiveState.INCLUDED).map(item -> addonIdByCode(item.code())).filter(Objects::nonNull).toList());
        for (CommercialAddonOfferEntity addon : includedAddOns) {
            if (addon.getStatus() != Status.ACTIVE) {
                continue;
            }
            for (var relation : addon.getCapabilities()) {
                CommercialCapabilityEntity capability = relation.getCapability();
                String code = normalize(capability.getCode());
                capabilities.putIfAbsent(code, new CapabilityResponse(capability.getCode(), capability.getName(), true, SourceType.ADD_ON, "Add-on " + addon.getName()));
                provenance.add(new ProvenanceResponse("CAPABILITY", capability.getCode(), "ADD_ON", addon.getName(), "Included via add-on"));
            }
            for (var relation : addon.getModules()) {
                CommercialModuleEntity module = relation.getModule();
                String code = normalize(module.getCode());
                modules.putIfAbsent(code, new ModuleResponse(module.getCode(), module.getName(), module.getRuntimeModuleCode(), true, SourceType.ADD_ON, "Add-on " + addon.getName(), null));
                provenance.add(new ProvenanceResponse("MODULE", module.getCode(), "ADD_ON", addon.getName(), "Included via add-on"));
            }
            for (CommercialAddonFeatureEntity relation : addon.getFeatures()) {
                CommercialFeatureEntity feature = relation.getFeature();
                String code = normalize(feature.getCode());
                features.putIfAbsent(code, new FeatureResponse(feature.getCode(), feature.getName(), feature.getRuntimeFeatureKey(), feature.getModule().getCode(), true, SourceType.ADD_ON, "Add-on " + addon.getName()));
                provenance.add(new ProvenanceResponse("FEATURE", feature.getCode(), "ADD_ON", addon.getName(), "Included via add-on"));
            }
            for (CommercialAddonLimitIncrementEntity relation : addon.getLimitIncrements()) {
                CommercialLimitDefinitionEntity limit = relation.getLimitDefinition();
                String code = normalize(limit.getCode());
                LimitResponse existing = limits.get(code);
                String nextValue = existing == null ? relation.getIncrementValue().stripTrailingZeros().toPlainString() : addNumeric(existing.configuredValue(), relation.getIncrementValue());
                limits.put(code, new LimitResponse(limit.getCode(), limit.getName(), limit.getValueType().name(), nextValue, false, limit.getUnit(), limit.getAggregationPeriod().name(), limit.getEnforcementMode().name(), SourceType.ADD_ON, addon.getCode()));
                provenance.add(new ProvenanceResponse("LIMIT", limit.getCode(), "ADD_ON", addon.getName(), "Increment via add-on"));
            }
        }
    }

    private void applyOverrides(List<CommercialTenantEntitlementOverrideEntity> overrides, Map<String, CapabilityResponse> capabilities, Map<String, ModuleResponse> modules, Map<String, FeatureResponse> features, Map<String, LimitResponse> limits, Map<String, AddOnContributionResponse> addOns, List<ProvenanceResponse> provenance, List<ValidationFinding> findings) {
        Map<String, List<CommercialTenantEntitlementOverrideEntity>> grouped = overrides.stream().collect(Collectors.groupingBy(override -> override.getTargetType().name() + ":" + normalize(override.getTargetCode())));
        grouped.values().forEach(group -> {
            if (group.size() > 1) {
                findings.add(new ValidationFinding("CONFLICTING_OVERRIDES", "Conflicting overrides", "Multiple active overrides exist for the same target and code.", true, "Cancel or retire the conflicting overrides.", group.get(0).getTargetCode(), group.get(0).getTargetType().name()));
            }
        });
        for (CommercialTenantEntitlementOverrideEntity override : overrides) {
            String code = normalize(override.getTargetCode());
            switch (override.getTargetType()) {
                case CAPABILITY -> applyCapabilityOverride(override, capabilities, provenance, findings);
                case MODULE -> applyModuleOverride(override, modules, features, provenance, findings);
                case FEATURE -> applyFeatureOverride(override, modules, features, provenance, findings);
                case LIMIT -> applyLimitOverride(override, limits, provenance, findings);
                case ADD_ON -> {
                    if (override.getOperation() == OverrideOperation.SET_ADDON_STATE) {
                        addOns.put(code, new AddOnContributionResponse(override.getTargetCode(), catalogAddonName(override.getTargetCode()), parseAddonState(override.getAddOnState()), SourceType.OVERRIDE, List.of(override.getReason() == null ? "Override" : override.getReason())));
                    }
                }
            }
        }
    }

    private void applyCapabilityOverride(CommercialTenantEntitlementOverrideEntity override, Map<String, CapabilityResponse> capabilities, List<ProvenanceResponse> provenance, List<ValidationFinding> findings) {
        String code = normalize(override.getTargetCode());
        if (override.getOperation() == OverrideOperation.DISABLE) {
            capabilities.remove(code);
            provenance.add(new ProvenanceResponse("CAPABILITY", override.getTargetCode(), "OVERRIDE", "Disabled capability", override.getReason()));
            return;
        }
        if (override.getOperation() == OverrideOperation.ENABLE) {
            capabilityRepository.findByCodeIgnoreCase(override.getTargetCode()).ifPresentOrElse(entity -> {
                if (entity.getStatus() == Status.RETIRED) {
                    findings.add(new ValidationFinding("TARGET_RETIRED", "Capability is retired", "Cannot enable a retired capability.", true, "Choose an active catalog capability.", override.getTargetCode(), "CAPABILITY"));
                } else {
                    capabilities.put(code, new CapabilityResponse(entity.getCode(), entity.getName(), true, SourceType.OVERRIDE, override.getReason() == null ? "Override enabled" : override.getReason()));
                    provenance.add(new ProvenanceResponse("CAPABILITY", entity.getCode(), "OVERRIDE", "Enabled capability", override.getReason()));
                }
            }, () -> findings.add(new ValidationFinding("TARGET_NOT_FOUND", "Capability not found", "The capability does not exist in the catalog.", true, "Select an active capability.", override.getTargetCode(), "CAPABILITY")));
        }
    }

    private void applyModuleOverride(CommercialTenantEntitlementOverrideEntity override, Map<String, ModuleResponse> modules, Map<String, FeatureResponse> features, List<ProvenanceResponse> provenance, List<ValidationFinding> findings) {
        String code = normalize(override.getTargetCode());
        if (override.getOperation() == OverrideOperation.DISABLE) {
            modules.remove(code);
            features.entrySet().removeIf(entry -> code.equalsIgnoreCase(entry.getValue().parentModuleCode()));
            provenance.add(new ProvenanceResponse("MODULE", override.getTargetCode(), "OVERRIDE", "Disabled module", override.getReason()));
            return;
        }
        if (override.getOperation() == OverrideOperation.ENABLE) {
            moduleRepository.findByCodeIgnoreCase(override.getTargetCode()).ifPresentOrElse(entity -> {
                if (entity.getStatus() == Status.RETIRED) {
                    findings.add(new ValidationFinding("TARGET_RETIRED", "Module is retired", "Cannot enable a retired module.", true, "Choose an active catalog module.", override.getTargetCode(), "MODULE"));
                } else {
                    modules.put(code, new ModuleResponse(entity.getCode(), entity.getName(), entity.getRuntimeModuleCode(), true, SourceType.OVERRIDE, override.getReason() == null ? "Override enabled" : override.getReason(), null));
                    provenance.add(new ProvenanceResponse("MODULE", entity.getCode(), "OVERRIDE", "Enabled module", override.getReason()));
                }
            }, () -> findings.add(new ValidationFinding("TARGET_NOT_FOUND", "Module not found", "The module does not exist in the catalog.", true, "Select an active module.", override.getTargetCode(), "MODULE")));
        }
    }

    private void applyFeatureOverride(CommercialTenantEntitlementOverrideEntity override, Map<String, ModuleResponse> modules, Map<String, FeatureResponse> features, List<ProvenanceResponse> provenance, List<ValidationFinding> findings) {
        String code = normalize(override.getTargetCode());
        if (override.getOperation() == OverrideOperation.DISABLE) {
            features.remove(code);
            provenance.add(new ProvenanceResponse("FEATURE", override.getTargetCode(), "OVERRIDE", "Disabled feature", override.getReason()));
            return;
        }
        if (override.getOperation() == OverrideOperation.ENABLE) {
            featureRepository.findByCodeIgnoreCase(override.getTargetCode()).ifPresentOrElse(entity -> {
                String parentCode = entity.getModule().getCode();
                if (!modules.containsKey(normalize(parentCode))) {
                    findings.add(new ValidationFinding("PARENT_MODULE_REQUIRED", "Parent module required", "Cannot enable a feature without its parent module.", true, "Enable the parent module first.", override.getTargetCode(), "FEATURE"));
                } else if (entity.getStatus() == Status.RETIRED) {
                    findings.add(new ValidationFinding("TARGET_RETIRED", "Feature is retired", "Cannot enable a retired feature.", true, "Choose an active catalog feature.", override.getTargetCode(), "FEATURE"));
                } else {
                    features.put(code, new FeatureResponse(entity.getCode(), entity.getName(), entity.getRuntimeFeatureKey(), parentCode, true, SourceType.OVERRIDE, override.getReason() == null ? "Override enabled" : override.getReason()));
                    provenance.add(new ProvenanceResponse("FEATURE", entity.getCode(), "OVERRIDE", "Enabled feature", override.getReason()));
                }
            }, () -> findings.add(new ValidationFinding("TARGET_NOT_FOUND", "Feature not found", "The feature does not exist in the catalog.", true, "Select an active feature.", override.getTargetCode(), "FEATURE")));
        }
    }

    private void applyLimitOverride(CommercialTenantEntitlementOverrideEntity override, Map<String, LimitResponse> limits, List<ProvenanceResponse> provenance, List<ValidationFinding> findings) {
        String code = normalize(override.getTargetCode());
        if (override.getOperation() == OverrideOperation.SET_VALUE) {
            limitRepository.findByCodeIgnoreCase(override.getTargetCode()).ifPresentOrElse(entity -> {
                if (!isCompatibleLimitValue(entity.getValueType(), override.getValue())) {
                    findings.add(new ValidationFinding("LIMIT_VALUE_TYPE", "Limit value type mismatch", "The configured limit value does not match the target type.", true, "Provide a value that matches the limit type.", override.getTargetCode(), "LIMIT"));
                } else {
                    limits.put(code, new LimitResponse(entity.getCode(), entity.getName(), entity.getValueType().name(), override.getValue(), false, entity.getUnit(), entity.getAggregationPeriod().name(), entity.getEnforcementMode().name(), SourceType.OVERRIDE, "Override value"));
                    provenance.add(new ProvenanceResponse("LIMIT", entity.getCode(), "OVERRIDE", "Override value", override.getReason()));
                }
            }, () -> findings.add(new ValidationFinding("TARGET_NOT_FOUND", "Limit not found", "The limit does not exist in the catalog.", true, "Select an active limit.", override.getTargetCode(), "LIMIT")));
        } else if (override.getOperation() == OverrideOperation.SET_UNLIMITED) {
            limitRepository.findByCodeIgnoreCase(override.getTargetCode()).ifPresentOrElse(entity -> {
                if (entity.getValueType() == LimitValueType.BOOLEAN) {
                    findings.add(new ValidationFinding("LIMIT_UNLIMITED_UNSUPPORTED", "Unlimited is unsupported", "The target limit does not support unlimited values.", true, "Use a different limit or a numeric type.", override.getTargetCode(), "LIMIT"));
                } else {
                    limits.put(code, new LimitResponse(entity.getCode(), entity.getName(), entity.getValueType().name(), null, true, entity.getUnit(), entity.getAggregationPeriod().name(), entity.getEnforcementMode().name(), SourceType.OVERRIDE, "Unlimited override"));
                    provenance.add(new ProvenanceResponse("LIMIT", entity.getCode(), "OVERRIDE", "Unlimited override", override.getReason()));
                }
            }, () -> findings.add(new ValidationFinding("TARGET_NOT_FOUND", "Limit not found", "The limit does not exist in the catalog.", true, "Select an active limit.", override.getTargetCode(), "LIMIT")));
        }
    }

    private void reconcileDependencies(Map<String, ModuleResponse> modules, Map<String, FeatureResponse> features, List<ProvenanceResponse> provenance, List<ValidationFinding> findings) {
        Set<String> missingModules = new LinkedHashSet<>();
        for (FeatureResponse feature : new ArrayList<>(features.values())) {
            if (!modules.containsKey(normalize(feature.parentModuleCode()))) {
                missingModules.add(feature.parentModuleCode());
                features.remove(normalize(feature.code()));
            }
        }
        for (String missing : missingModules) {
            findings.add(new ValidationFinding("ORPHAN_FEATURE", "Orphan feature removed", "A feature could not remain enabled without its parent module.", true, "Enable the parent module or remove the feature.", missing, "FEATURE"));
            provenance.add(new ProvenanceResponse("FEATURE", missing, "DEPENDENCY", "Disabled because parent module was removed", null));
        }
    }

    private void sortCanonical(Map<String, CapabilityResponse> capabilities, Map<String, ModuleResponse> modules, Map<String, FeatureResponse> features, Map<String, LimitResponse> limits, Map<String, AddOnContributionResponse> addOns, List<ProvenanceResponse> provenance) {
        sortMap(capabilities);
        sortMap(modules);
        sortMap(features);
        sortMap(limits);
        sortMap(addOns);
        provenance.sort(Comparator.comparing(ProvenanceResponse::itemType).thenComparing(ProvenanceResponse::code));
    }

    private <T> void sortMap(Map<String, T> map) {
        List<Map.Entry<String, T>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        map.clear();
        for (Map.Entry<String, T> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
    }

    private List<CommercialTenantEntitlementOverrideEntity> loadActiveOverrides(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return overrideRepository.findByTenantIdOrderByEffectiveFromAscUpdatedAtAsc(tenantId).stream()
                .filter(override -> override.getStatus() == OverrideStatus.ACTIVE || override.getStatus() == OverrideStatus.SCHEDULED)
                .filter(override -> !override.getEffectiveFrom().isAfter(today))
                .filter(override -> override.getEffectiveUntil() == null || !override.getEffectiveUntil().isBefore(today))
                .sorted(Comparator.comparing(CommercialTenantEntitlementOverrideEntity::getEffectiveFrom).thenComparing(CommercialTenantEntitlementOverrideEntity::getUpdatedAt))
                .toList();
    }

    private CommercialTenantSubscriptionEntity latestSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().findFirst().orElse(null);
    }

    private PlanConfigurationSnapshot resolveBaseSnapshot(CommercialTenantSubscriptionEntity subscription) {
        if (subscription == null || subscription.getPublishedVersion() == null) {
            return new PlanConfigurationSnapshot(null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        CommercialPlanVersionEntity version = versionRepository.findById(subscription.getPublishedVersion().getId()).orElse(subscription.getPublishedVersion());
        if (version == null) {
            return new PlanConfigurationSnapshot(null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        try {
            return objectMapper.readValue(version.getSnapshotJson(), PlanConfigurationSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read published commercial plan snapshot", ex);
        }
    }

    private OverrideResponse saveOverride(
            UUID tenantId,
            CommercialTenantEntitlementOverrideEntity existing,
            OverrideTargetType targetType,
            String targetCode,
            OverrideOperation operation,
            String value,
            String addOnState,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason,
            String internalNotes,
            UUID subscriptionId,
            OverrideStatus status,
            boolean regenerateSnapshot
    ) {
        validateOverride(targetType, targetCode, operation, value, addOnState, effectiveFrom, effectiveUntil);
        OffsetDateTime now = now();
        CommercialTenantEntitlementOverrideEntity entity = existing == null
                ? CommercialTenantEntitlementOverrideEntity.create(UUID.randomUUID(), tenantId, subscriptionId, targetType, normalizeCode(targetCode), operation, blankToNull(value), blankToNull(addOnState), effectiveFrom, effectiveUntil, status, blankToNull(reason), blankToNull(internalNotes), now, currentActor())
                : existing;
        if (existing != null) {
            entity.update(subscriptionId, targetType, normalizeCode(targetCode), operation, blankToNull(value), blankToNull(addOnState), effectiveFrom, effectiveUntil, status, blankToNull(reason), blankToNull(internalNotes), now, currentActor());
        }
        overrideRepository.save(entity);
        recordOverrideEvent(tenantId, subscriptionId, entity.getId(), existing == null ? "COMMERCIAL_OVERRIDE_CREATED" : "COMMERCIAL_OVERRIDE_UPDATED", status.name(), "Saved entitlement override");
        if (regenerateSnapshot) {
            regenerateCurrentSnapshot(tenantId, existing == null ? GenerationReason.OVERRIDE_CREATED : GenerationReason.OVERRIDE_UPDATED);
        }
        return toOverrideResponse(entity);
    }

    private void validateOverride(OverrideTargetType targetType, String targetCode, OverrideOperation operation, String value, String addOnState, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        if (targetType == null || !StringUtils.hasText(targetCode) || operation == null || effectiveFrom == null) {
            throw conflict("Override target, code, operation, and effectiveFrom are required");
        }
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw conflict("effectiveUntil must be after effectiveFrom");
        }
        switch (targetType) {
            case CAPABILITY -> {
                capabilityRepository.findByCodeIgnoreCase(targetCode).orElseThrow(() -> conflict("Capability does not exist: " + targetCode));
                if (operation != OverrideOperation.ENABLE && operation != OverrideOperation.DISABLE) {
                    throw conflict("Capability overrides only support enable/disable");
                }
            }
            case MODULE -> {
                moduleRepository.findByCodeIgnoreCase(targetCode).orElseThrow(() -> conflict("Module does not exist: " + targetCode));
                if (operation != OverrideOperation.ENABLE && operation != OverrideOperation.DISABLE) {
                    throw conflict("Module overrides only support enable/disable");
                }
            }
            case FEATURE -> {
                featureRepository.findByCodeIgnoreCase(targetCode).orElseThrow(() -> conflict("Feature does not exist: " + targetCode));
                if (operation != OverrideOperation.ENABLE && operation != OverrideOperation.DISABLE) {
                    throw conflict("Feature overrides only support enable/disable");
                }
            }
            case LIMIT -> {
                CommercialLimitDefinitionEntity entity = limitRepository.findByCodeIgnoreCase(targetCode).orElseThrow(() -> conflict("Limit does not exist: " + targetCode));
                if (operation == OverrideOperation.SET_VALUE && !isCompatibleLimitValue(entity.getValueType(), value)) {
                    throw conflict("Limit value is incompatible with its configured type");
                }
                if (operation == OverrideOperation.SET_UNLIMITED && entity.getValueType() == LimitValueType.BOOLEAN) {
                    throw conflict("Unlimited is not supported for this limit");
                }
                if (operation != OverrideOperation.SET_VALUE && operation != OverrideOperation.SET_UNLIMITED) {
                    throw conflict("Limit overrides only support set value or set unlimited");
                }
            }
            case ADD_ON -> {
                addonRepository.findByCodeIgnoreCase(targetCode).orElseThrow(() -> conflict("Add-on does not exist: " + targetCode));
                if (operation != OverrideOperation.SET_ADDON_STATE) {
                    throw conflict("Add-on overrides only support set add-on state");
                }
                parseAddonState(addOnState);
            }
        }
    }

    private boolean isSubscriptionActive(CommercialTenantSubscriptionEntity subscription) {
        if (subscription == null) {
            return false;
        }
        return subscription.getSubscriptionStatus() == SubscriptionStatus.ACTIVE
                || subscription.getSubscriptionStatus() == SubscriptionStatus.SCHEDULED
                || subscription.getSubscriptionStatus() == SubscriptionStatus.PAUSED;
    }

    private boolean isCompatibleLimitValue(LimitValueType valueType, String configuredValue) {
        if (!StringUtils.hasText(configuredValue)) {
            return false;
        }
        return switch (valueType) {
            case INTEGER -> configuredValue.matches("^\\d+$");
            case DECIMAL -> configuredValue.matches("^\\d+(\\.\\d+)?$");
            case BOOLEAN -> "true".equalsIgnoreCase(configuredValue) || "false".equalsIgnoreCase(configuredValue);
        };
    }

    private String addNumeric(String existing, BigDecimal increment) {
        try {
            BigDecimal base = new BigDecimal(existing);
            return base.add(increment).stripTrailingZeros().toPlainString();
        } catch (Exception ex) {
            return increment.stripTrailingZeros().toPlainString();
        }
    }

    private AddOnEffectiveState toAddonState(com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.SelectionState selectionState) {
        if (selectionState == null) {
            return AddOnEffectiveState.AVAILABLE_FOR_PURCHASE;
        }
        return switch (selectionState) {
            case INCLUDED -> AddOnEffectiveState.INCLUDED;
            case AVAILABLE -> AddOnEffectiveState.AVAILABLE_FOR_PURCHASE;
            case UNAVAILABLE -> AddOnEffectiveState.UNAVAILABLE;
        };
    }

    private AddOnEffectiveState parseAddonState(String state) {
        if (!StringUtils.hasText(state)) {
            return AddOnEffectiveState.AVAILABLE_FOR_PURCHASE;
        }
        return AddOnEffectiveState.valueOf(state.trim().toUpperCase(Locale.ROOT));
    }

    private String currentActorLabel() {
        return RequestContextHolder.get() == null || RequestContextHolder.get().appUserId() == null ? null : RequestContextHolder.get().appUserId().toString();
    }

    private UUID currentActor() {
        return RequestContextHolder.get() == null ? null : RequestContextHolder.get().appUserId();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime toOffset(LocalDate value) {
        return value == null ? null : value.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String input) {
        return StringUtils.hasText(input) ? input.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "") : input;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize effective entitlement snapshot", ex);
        }
    }

    private String hashContent(UUID tenantId, CommercialTenantSubscriptionEntity subscription, Map<String, CapabilityResponse> capabilityMap, Map<String, ModuleResponse> moduleMap, Map<String, FeatureResponse> featureMap, Map<String, LimitResponse> limitMap, Map<String, AddOnContributionResponse> addOnMap, List<ProvenanceResponse> provenance, GenerationReason reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subscriptionStatus", subscription == null ? null : subscription.getSubscriptionStatus().name());
        payload.put("capabilities", capabilityMap.values().stream().toList());
        payload.put("modules", moduleMap.values().stream().toList());
        payload.put("features", featureMap.values().stream().toList());
        payload.put("limits", limitMap.values().stream().toList());
        payload.put("addOns", addOnMap.values().stream().toList());
        payload.put("provenance", provenance);
        payload.put("generationReason", reason.name());
        return hash(serialize(payload));
    }

    private String hashSource(CommercialTenantSubscriptionEntity subscription, List<CommercialTenantEntitlementOverrideEntity> overrides) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subscriptionId", subscription == null ? null : subscription.getId());
        payload.put("publishedVersionId", subscription == null ? null : subscription.getPublishedVersion().getId());
        payload.put("publishedVersionHash", subscription == null ? null : subscription.getPublishedVersion().getContentHash());
        payload.put("overrides", overrides.stream().map(override -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", override.getId());
            item.put("targetType", override.getTargetType().name());
            item.put("targetCode", override.getTargetCode());
            item.put("operation", override.getOperation().name());
            item.put("value", override.getValue());
            item.put("addOnState", override.getAddOnState());
            item.put("status", override.getStatus().name());
            item.put("effectiveFrom", override.getEffectiveFrom());
            item.put("effectiveUntil", override.getEffectiveUntil());
            item.put("version", override.getVersion());
            return item;
        }).toList());
        return hash(serialize(payload));
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
            throw new IllegalStateException("Unable to hash effective entitlement snapshot", ex);
        }
    }

    private String runtimeFeatureKey(String featureCode) {
        return featureRepository.findByCodeIgnoreCase(featureCode).map(CommercialFeatureEntity::getRuntimeFeatureKey).orElse(null);
    }

    private String capabilityForModule(PlanConfigurationSnapshot snapshot, UUID moduleId) {
        if (moduleId == null || snapshot == null || snapshot.capabilities() == null || snapshot.modules() == null) {
            return null;
        }
        return snapshot.capabilities().stream().findFirst().map(SelectedCapability::capabilityCode).orElse(null);
    }

    private String catalogAddonName(String addonCode) {
        return addonRepository.findByCodeIgnoreCase(addonCode).map(CommercialAddonOfferEntity::getName).orElse(addonCode);
    }

    private UUID addonIdByCode(String addonCode) {
        return addonRepository.findByCodeIgnoreCase(addonCode).map(CommercialAddonOfferEntity::getId).orElse(null);
    }

    private String resolvePreviewValue(EffectiveEntitlementSnapshotResponse snapshot, OverrideTargetType targetType, String targetCode) {
        if (snapshot == null || targetType == null || !StringUtils.hasText(targetCode)) {
            return null;
        }
        String code = normalize(targetCode);
        return switch (targetType) {
            case CAPABILITY -> snapshot.capabilities().stream().filter(item -> code.equals(normalize(item.code()))).findFirst().map(item -> item.enabled() ? "Enabled" : "Disabled").orElse("Not included");
            case MODULE -> snapshot.modules().stream().filter(item -> code.equals(normalize(item.code()))).findFirst().map(item -> item.enabled() ? "Enabled" : "Disabled").orElse("Not included");
            case FEATURE -> snapshot.features().stream().filter(item -> code.equals(normalize(item.code()))).findFirst().map(item -> item.enabled() ? "Enabled" : "Disabled").orElse("Not included");
            case LIMIT -> snapshot.limits().stream().filter(item -> code.equals(normalize(item.code()))).findFirst().map(item -> item.unlimited() ? "Unlimited" : item.configuredValue()).orElse("Not defined");
            case ADD_ON -> snapshot.addOns().stream().filter(item -> code.equals(normalize(item.code()))).findFirst().map(item -> item.state().name()).orElse("Not included");
        };
    }

    private String previewAfterValue(EffectiveEntitlementSnapshotResponse snapshot, CreateOverrideRequest request) {
        if (request == null || request.targetType() == null || !StringUtils.hasText(request.targetCode()) || request.operation() == null) {
            return "—";
        }
        return switch (request.targetType()) {
            case CAPABILITY, MODULE, FEATURE -> request.operation() == OverrideOperation.ENABLE ? "Enabled" : "Disabled";
            case LIMIT -> request.operation() == OverrideOperation.SET_UNLIMITED ? "Unlimited" : blankToNull(request.value());
            case ADD_ON -> blankToNull(request.addOnState());
        };
    }

    private List<String> previewDependentEffects(CreateOverrideRequest request) {
        if (request == null || request.targetType() == null || request.operation() == null) {
            return List.of("Select a target to preview dependency impact.");
        }
        return switch (request.targetType()) {
            case MODULE -> request.operation() == OverrideOperation.DISABLE
                    ? List.of("Dependent features will be disabled or removed from the effective snapshot.")
                    : List.of("Dependent features may become eligible if the module is effective.");
            case FEATURE -> request.operation() == OverrideOperation.ENABLE
                    ? List.of("Parent module must remain effective.")
                    : List.of("The feature will be removed from the effective snapshot.");
            case LIMIT -> List.of("Limit overrides replace the base published plan value.");
            case ADD_ON -> List.of("Add-on contributions will be recalculated from the chosen state.");
            case CAPABILITY -> request.operation() == OverrideOperation.DISABLE
                    ? List.of("Related modules and features may be removed from the effective snapshot.")
                    : List.of("Related modules and features may become eligible.");
        };
    }

    private OverrideResponse toOverrideResponse(CommercialTenantEntitlementOverrideEntity entity) {
        return new OverrideResponse(
                entity.getId(),
                entity.getTargetType().name(),
                entity.getTargetCode(),
                entity.getOperation().name(),
                entity.getValue(),
                entity.getAddOnState(),
                entity.getEffectiveFrom(),
                entity.getEffectiveUntil(),
                entity.getStatus().name(),
                entity.getReason(),
                entity.getSubmittedAt(),
                entity.getSubmittedBy(),
                entity.getReviewedAt(),
                entity.getReviewedBy(),
                entity.getReviewRemarks(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy(),
                entity.getVersion()
        );
    }

    private OverrideHistoryResponse toHistoryResponse(CommercialEffectiveEntitlementEventEntity event) {
        Map<String, Object> payload = parsePayload(event.getPayloadJson());
        return new OverrideHistoryResponse(
                event.getOverrideId(),
                String.valueOf(event.getVersion()),
                stringValue(payload.get("previousStatus")),
                stringValue(payload.get("newStatus")),
                stringValue(payload.get("action")),
                event.getOccurredAt(),
                event.getActor(),
                stringValue(payload.get("remarks")),
                stringValue(payload.get("snapshotHash"))
        );
    }

    private void recordOverrideEvent(UUID tenantId, UUID subscriptionId, UUID overrideId, String eventType, String status, String remarks) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", eventType);
        payload.put("previousStatus", null);
        payload.put("newStatus", status);
        payload.put("remarks", remarks);
        recordEvent(tenantId, subscriptionId, null, overrideId, eventType, GenerationReason.OVERRIDE_UPDATED, status, serialize(payload));
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, LinkedHashMap.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private EffectiveEntitlementSnapshotResponse toResponse(CommercialEffectiveEntitlementSnapshotEntity entity) {
        try {
            return objectMapper.readValue(entity.getCanonicalSnapshotJson(), EffectiveEntitlementSnapshotResponse.class);
        } catch (Exception ex) {
            return new EffectiveEntitlementSnapshotResponse(
                    entity.getId(),
                    entity.getTenantId(),
                    entity.getSubscriptionId(),
                    entity.getPlanTemplateId(),
                    entity.getPublishedVersionId(),
                    entity.getPublishedVersionNumber(),
                    entity.getSubscriptionStatus(),
                    entity.getEffectiveFrom(),
                    entity.getEffectiveUntil(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    entity.getSourceHash(),
                    entity.getContentHash(),
                    entity.getGeneratedAt(),
                    entity.getGeneratedBy(),
                    entity.getGenerationReason().name(),
                    entity.getSnapshotStatus().name(),
                    entity.getValidationState(),
                    List.of()
            );
        }
    }

    private ComparisonItemResponse comparison(String code, String label, ComparisonCategory category, String legacyValue, String commercialValue, String detail) {
        return new ComparisonItemResponse(code, label, category, legacyValue, commercialValue, detail);
    }

    private List<ComparisonItemResponse> compareSimple(Map<String, Boolean> legacyModules, Map<String, Boolean> commercialModules, String kind) {
        Map<String, Boolean> left = legacyModules == null ? Map.of() : legacyModules.entrySet().stream().collect(Collectors.toMap(entry -> normalize(entry.getKey()), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        Map<String, Boolean> right = commercialModules == null ? Map.of() : commercialModules;
        List<ComparisonItemResponse> items = new ArrayList<>();
        for (String code : right.keySet()) {
            boolean leftValue = left.getOrDefault(code, false);
            boolean rightValue = Boolean.TRUE.equals(right.get(code));
            ComparisonCategory category = left.containsKey(code) ? (leftValue == rightValue ? ComparisonCategory.MATCH : ComparisonCategory.DIFFERENT) : ComparisonCategory.COMMERCIAL_ONLY;
            items.add(comparison(code, code, category, String.valueOf(leftValue), String.valueOf(rightValue), kind));
        }
        for (String code : left.keySet()) {
            if (!right.containsKey(code)) {
                items.add(comparison(code, code, ComparisonCategory.LEGACY_ONLY, String.valueOf(left.get(code)), null, kind));
            }
        }
        items.sort(Comparator.comparing(ComparisonItemResponse::code));
        return items;
    }

    private CommercialTenantEntitlementOverrideEntity loadOverride(UUID tenantId, UUID overrideId) {
        CommercialTenantEntitlementOverrideEntity entity = overrideRepository.findById(overrideId).orElseThrow(() -> notFound("Override", overrideId));
        if (!tenantId.equals(entity.getTenantId())) {
            throw notFound("Override", overrideId);
        }
        return entity;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String type, Object id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found: " + id);
    }

    private void audit(UUID entityId, String summary, Map<String, Object> details) {
        auditEventPublisher.record(new AuditEventCommand(platformAuditTenantId(), AuditEntityType.COMMERCIAL_TENANT_SUBSCRIPTION, entityId, AuditEventAction.COMMERCIAL_PLAN_VERSION_PUBLISHED, currentActor(), now(), summary, serialize(details)));
    }

    private void recordAndAudit(UUID tenantId, UUID subscriptionId, UUID snapshotId, UUID overrideId, String eventType, GenerationReason reason, String message) {
        recordEvent(tenantId, subscriptionId, snapshotId, overrideId, eventType, reason, null, serialize(Map.of("message", message)));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason.name());
        details.put("subscriptionId", subscriptionId);
        details.put("snapshotId", snapshotId);
        details.put("overrideId", overrideId);
        audit(tenantId, message, details);
    }

    private void recordEvent(UUID tenantId, UUID subscriptionId, UUID snapshotId, UUID overrideId, String eventType, GenerationReason reason, String validationState, String payloadJson) {
        CommercialEffectiveEntitlementEventEntity event = CommercialEffectiveEntitlementEventEntity.create(UUID.randomUUID(), tenantId, subscriptionId, snapshotId, overrideId, eventType, reason, validationState, payloadJson, now(), currentActorLabel());
        eventRepository.save(event);
    }

    private String eventType(CommercialEffectiveEntitlementEventEntity event) {
        return event.getEventType();
    }

    private UUID platformAuditTenantId() {
        var ctx = RequestContextHolder.get();
        if (ctx != null && ctx.tenantId() != null) {
            return ctx.tenantId().value();
        }
        return PLATFORM_AUDIT_TENANT_ID;
    }

    private record SnapshotContext(
            UUID snapshotId,
            UUID tenantId,
            UUID subscriptionId,
            UUID planTemplateId,
            UUID publishedVersionId,
            Integer publishedVersionNumber,
            String subscriptionStatus,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveUntil,
            List<CapabilityResponse> capabilities,
            List<ModuleResponse> modules,
            List<FeatureResponse> features,
            List<LimitResponse> limits,
            List<AddOnContributionResponse> addOns,
            List<OverrideResponse> overrides,
            List<ProvenanceResponse> provenance,
            String sourceHash,
            String contentHash,
            OffsetDateTime generatedAt,
            String generatedBy,
            String generationReason,
            String snapshotStatus,
            String validationState,
            List<ValidationFinding> validationFindings
    ) {
        EffectiveEntitlementSnapshotResponse toResponse() {
            return new EffectiveEntitlementSnapshotResponse(snapshotId, tenantId, subscriptionId, planTemplateId, publishedVersionId, publishedVersionNumber, subscriptionStatus, effectiveFrom, effectiveUntil, capabilities, modules, features, limits, addOns, overrides, provenance, sourceHash, contentHash, generatedAt, generatedBy, generationReason, snapshotStatus, validationState, validationFindings);
        }
    }
}
