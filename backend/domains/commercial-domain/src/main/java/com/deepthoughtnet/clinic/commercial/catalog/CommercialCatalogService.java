package com.deepthoughtnet.clinic.commercial.catalog;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.*;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AddonType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import com.deepthoughtnet.clinic.commercial.catalog.db.*;
import com.deepthoughtnet.clinic.platform.audit.AuditEventAction;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.platform.core.module.SaasModuleCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@Service
public class CommercialCatalogService {
    private static final Set<String> ACTIVE_RUNTIME_MODULE_CODES = Set.of(
            SaasModuleCode.APPOINTMENTS.name(),
            "PATIENTS",
            SaasModuleCode.CONSULTATION.name(),
            SaasModuleCode.PRESCRIPTION.name(),
            SaasModuleCode.BILLING.name(),
            SaasModuleCode.VACCINATION.name(),
            SaasModuleCode.INVENTORY.name(),
            SaasModuleCode.PHARMACY_POS.name(),
            SaasModuleCode.LABORATORY.name(),
            SaasModuleCode.REPORTS.name(),
            SaasModuleCode.AI_COPILOT.name(),
            SaasModuleCode.CAREPILOT.name(),
            "NOTIFICATIONS"
    );

    private final CommercialCapabilityRepository capabilityRepository;
    private final CommercialModuleRepository moduleRepository;
    private final CommercialCapabilityModuleRepository capabilityModuleRepository;
    private final CommercialFeatureRepository featureRepository;
    private final CommercialLimitDefinitionRepository limitRepository;
    private final CommercialAddonOfferRepository addonRepository;
    private final CommercialAddonCapabilityRepository addonCapabilityRepository;
    private final CommercialAddonModuleRepository addonModuleRepository;
    private final CommercialAddonFeatureRepository addonFeatureRepository;
    private final CommercialAddonLimitIncrementRepository addonLimitIncrementRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public CommercialCatalogService(
            CommercialCapabilityRepository capabilityRepository,
            CommercialModuleRepository moduleRepository,
            CommercialCapabilityModuleRepository capabilityModuleRepository,
            CommercialFeatureRepository featureRepository,
            CommercialLimitDefinitionRepository limitRepository,
            CommercialAddonOfferRepository addonRepository,
            CommercialAddonCapabilityRepository addonCapabilityRepository,
            CommercialAddonModuleRepository addonModuleRepository,
            CommercialAddonFeatureRepository addonFeatureRepository,
            CommercialAddonLimitIncrementRepository addonLimitIncrementRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.capabilityRepository = capabilityRepository;
        this.moduleRepository = moduleRepository;
        this.capabilityModuleRepository = capabilityModuleRepository;
        this.featureRepository = featureRepository;
        this.limitRepository = limitRepository;
        this.addonRepository = addonRepository;
        this.addonCapabilityRepository = addonCapabilityRepository;
        this.addonModuleRepository = addonModuleRepository;
        this.addonFeatureRepository = addonFeatureRepository;
        this.addonLimitIncrementRepository = addonLimitIncrementRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<CapabilitySummaryResponse> listCapabilities(String search, Status status, int page, int size) {
        Page<CommercialCapabilityEntity> result = capabilityRepository.findAll(capabilitySpec(search, status),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("code"))));
        return new PageResponse<>(result.map(this::toCapabilitySummary).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CapabilityDetailResponse getCapability(UUID id) {
        CommercialCapabilityEntity entity = capabilityRepository.findById(id).orElseThrow(() -> notFound("Capability", id));
        return toCapabilityDetail(entity);
    }

    @Transactional
    public CapabilityDetailResponse createCapability(CreateCapabilityRequest request) {
        String code = normalizeCode(requireText(request == null ? null : request.code(), "code is required"));
        if (capabilityRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("Capability code already exists: " + code);
        }
        CommercialCapabilityEntity entity = newCapability(request, code);
        capabilityRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_CAPABILITY, AuditEventAction.COMMERCIAL_CAPABILITY_CREATED, "Created capability", Map.of("code", code, "status", entity.getStatus().name()));
        return toCapabilityDetail(entity);
    }

    @Transactional
    public CapabilityDetailResponse updateCapability(UUID id, UpdateCapabilityRequest request) {
        CommercialCapabilityEntity entity = capabilityRepository.findById(id).orElseThrow(() -> notFound("Capability", id));
        applyCapabilityUpdate(entity, request);
        capabilityRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_CAPABILITY, AuditEventAction.COMMERCIAL_CAPABILITY_UPDATED, "Updated capability", Map.of("code", entity.getCode(), "status", entity.getStatus().name()));
        return toCapabilityDetail(entity);
    }

    @Transactional
    public CapabilityDetailResponse retireCapability(UUID id) {
        CommercialCapabilityEntity entity = capabilityRepository.findById(id).orElseThrow(() -> notFound("Capability", id));
        entityStatus(entity, Status.RETIRED);
        capabilityRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_CAPABILITY, AuditEventAction.COMMERCIAL_CAPABILITY_RETIRED, "Retired capability", Map.of("code", entity.getCode()));
        return toCapabilityDetail(entity);
    }

    @Transactional
    public CapabilityDetailResponse updateCapabilityModules(UUID capabilityId, UpdateCapabilityModulesRequest request) {
        CommercialCapabilityEntity capability = capabilityRepository.findById(capabilityId).orElseThrow(() -> notFound("Capability", capabilityId));
        if (capability.getStatus() == Status.RETIRED) {
            throw new IllegalArgumentException("Retired capability cannot be reassigned modules");
        }
        capabilityModuleRepository.deleteByCapability_Id(capabilityId);
        List<CapabilityModuleAssignmentRequest> assignments = request == null || request.modules() == null ? List.of() : request.modules();
        List<CommercialCapabilityModuleEntity> saved = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        UUID actor = currentActor();
        for (CapabilityModuleAssignmentRequest assignment : assignments) {
            CommercialModuleEntity module = activeModule(requireUuid(assignment.moduleId(), "moduleId is required"));
            CommercialCapabilityModuleEntity relation = new CommercialCapabilityModuleEntity();
            setField(relation, "id", new CommercialCapabilityModuleId(capabilityId, module.getId()));
            setField(relation, "capability", capability);
            setField(relation, "module", module);
            setField(relation, "includedByDefault", Boolean.TRUE.equals(assignment.includedByDefault()));
            setField(relation, "displayOrder", assignment.displayOrder() == null ? 0 : assignment.displayOrder());
            setField(relation, "createdAt", now);
            setField(relation, "createdBy", actor);
            saved.add(relation);
        }
        capabilityModuleRepository.saveAll(saved);
        audit(capabilityId, AuditEntityType.COMMERCIAL_CAPABILITY, AuditEventAction.COMMERCIAL_RELATIONSHIP_UPDATED, "Updated capability-module links", Map.of("moduleCount", saved.size()));
        return toCapabilityDetail(capability);
    }

    @Transactional(readOnly = true)
    public PageResponse<ModuleSummaryResponse> listModules(String search, Status status, int page, int size) {
        Page<CommercialModuleEntity> result = moduleRepository.findAll(moduleSpec(search, status),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("code"))));
        return new PageResponse<>(result.map(this::toModuleSummary).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ModuleDetailResponse getModule(UUID id) {
        CommercialModuleEntity entity = moduleRepository.findById(id).orElseThrow(() -> notFound("Module", id));
        return toModuleDetail(entity);
    }

    @Transactional
    public ModuleDetailResponse createModule(CreateModuleRequest request) {
        String code = normalizeCode(requireText(request == null ? null : request.code(), "code is required"));
        if (moduleRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("Module code already exists: " + code);
        }
        String runtime = normalizeRuntimeModuleCode(request == null ? null : request.runtimeModuleCode());
        CommercialModuleEntity entity = newModule(request, code, runtime);
        moduleRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_MODULE, AuditEventAction.COMMERCIAL_MODULE_CREATED, "Created module", Map.of("code", code, "runtimeModuleCode", runtime));
        return toModuleDetail(entity);
    }

    @Transactional
    public ModuleDetailResponse updateModule(UUID id, UpdateModuleRequest request) {
        CommercialModuleEntity entity = moduleRepository.findById(id).orElseThrow(() -> notFound("Module", id));
        if (request != null) {
            entitySet(entity, "name", requireText(request.name(), "name is required"));
            entitySet(entity, "description", blankToNull(request.description()));
            entitySet(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
            entitySet(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
            entitySet(entity, "runtimeModuleCode", normalizeRuntimeModuleCode(request.runtimeModuleCode()));
            entitySet(entity, "updatedAt", OffsetDateTime.now());
            entitySet(entity, "updatedBy", currentActor());
        }
        moduleRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_MODULE, AuditEventAction.COMMERCIAL_MODULE_UPDATED, "Updated module", Map.of("code", entity.getCode(), "status", entity.getStatus().name()));
        return toModuleDetail(entity);
    }

    @Transactional
    public ModuleDetailResponse retireModule(UUID id) {
        CommercialModuleEntity entity = moduleRepository.findById(id).orElseThrow(() -> notFound("Module", id));
        entitySet(entity, "status", Status.RETIRED);
        entitySet(entity, "updatedAt", OffsetDateTime.now());
        entitySet(entity, "updatedBy", currentActor());
        moduleRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_MODULE, AuditEventAction.COMMERCIAL_MODULE_RETIRED, "Retired module", Map.of("code", entity.getCode()));
        return toModuleDetail(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<FeatureSummaryResponse> listFeatures(String search, Status status, int page, int size) {
        Page<CommercialFeatureEntity> result = featureRepository.findAll(featureSpec(search, status),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("code"))));
        return new PageResponse<>(result.map(this::toFeatureSummary).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public FeatureDetailResponse getFeature(UUID id) {
        CommercialFeatureEntity entity = featureRepository.findById(id).orElseThrow(() -> notFound("Feature", id));
        return toFeatureDetail(entity);
    }

    @Transactional
    public FeatureDetailResponse createFeature(CreateFeatureRequest request) {
        String code = normalizeCode(requireText(request == null ? null : request.code(), "code is required"));
        if (featureRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("Feature code already exists: " + code);
        }
        CommercialModuleEntity module = activeModule(requireUuid(request.moduleId(), "moduleId is required"));
        CommercialFeatureEntity entity = newFeature(request, code, module);
        featureRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_FEATURE, AuditEventAction.COMMERCIAL_FEATURE_CREATED, "Created feature", Map.of("code", code, "moduleCode", module.getCode()));
        return toFeatureDetail(entity);
    }

    @Transactional
    public FeatureDetailResponse updateFeature(UUID id, UpdateFeatureRequest request) {
        CommercialFeatureEntity entity = featureRepository.findById(id).orElseThrow(() -> notFound("Feature", id));
        CommercialModuleEntity module = activeModule(requireUuid(request.moduleId(), "moduleId is required"));
        entitySet(entity, "name", requireText(request.name(), "name is required"));
        entitySet(entity, "description", blankToNull(request.description()));
        entitySet(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
        entitySet(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
        entitySet(entity, "module", module);
        entitySet(entity, "runtimeFeatureKey", blankToNull(request.runtimeFeatureKey()));
        entitySet(entity, "updatedAt", OffsetDateTime.now());
        entitySet(entity, "updatedBy", currentActor());
        featureRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_FEATURE, AuditEventAction.COMMERCIAL_FEATURE_UPDATED, "Updated feature", Map.of("code", entity.getCode(), "moduleCode", module.getCode()));
        return toFeatureDetail(entity);
    }

    @Transactional
    public FeatureDetailResponse retireFeature(UUID id) {
        CommercialFeatureEntity entity = featureRepository.findById(id).orElseThrow(() -> notFound("Feature", id));
        entitySet(entity, "status", Status.RETIRED);
        entitySet(entity, "updatedAt", OffsetDateTime.now());
        entitySet(entity, "updatedBy", currentActor());
        featureRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_FEATURE, AuditEventAction.COMMERCIAL_FEATURE_RETIRED, "Retired feature", Map.of("code", entity.getCode()));
        return toFeatureDetail(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<LimitDefinitionSummaryResponse> listLimits(String search, Status status, int page, int size) {
        Page<CommercialLimitDefinitionEntity> result = limitRepository.findAll(limitSpec(search, status),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("code"))));
        return new PageResponse<>(result.map(this::toLimitSummary).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LimitDefinitionDetailResponse getLimit(UUID id) {
        CommercialLimitDefinitionEntity entity = limitRepository.findById(id).orElseThrow(() -> notFound("Limit definition", id));
        return toLimitDetail(entity);
    }

    @Transactional
    public LimitDefinitionDetailResponse createLimit(CreateLimitDefinitionRequest request) {
        String code = normalizeCode(requireText(request == null ? null : request.code(), "code is required"));
        if (limitRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("Limit code already exists: " + code);
        }
        CommercialLimitDefinitionEntity entity = newLimit(request, code);
        limitRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_LIMIT_DEFINITION, AuditEventAction.COMMERCIAL_LIMIT_CREATED, "Created limit definition", Map.of("code", code, "unit", entity.getUnit()));
        return toLimitDetail(entity);
    }

    @Transactional
    public LimitDefinitionDetailResponse updateLimit(UUID id, UpdateLimitDefinitionRequest request) {
        CommercialLimitDefinitionEntity entity = limitRepository.findById(id).orElseThrow(() -> notFound("Limit definition", id));
        entitySet(entity, "name", requireText(request.name(), "name is required"));
        entitySet(entity, "description", blankToNull(request.description()));
        entitySet(entity, "unit", requireText(request.unit(), "unit is required"));
        entitySet(entity, "valueType", request.valueType());
        entitySet(entity, "aggregationPeriod", request.aggregationPeriod());
        entitySet(entity, "enforcementMode", request.enforcementMode());
        entitySet(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
        entitySet(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
        entitySet(entity, "updatedAt", OffsetDateTime.now());
        entitySet(entity, "updatedBy", currentActor());
        limitRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_LIMIT_DEFINITION, AuditEventAction.COMMERCIAL_LIMIT_UPDATED, "Updated limit definition", Map.of("code", entity.getCode(), "unit", entity.getUnit()));
        return toLimitDetail(entity);
    }

    @Transactional
    public LimitDefinitionDetailResponse retireLimit(UUID id) {
        CommercialLimitDefinitionEntity entity = limitRepository.findById(id).orElseThrow(() -> notFound("Limit definition", id));
        entitySet(entity, "status", Status.RETIRED);
        entitySet(entity, "updatedAt", OffsetDateTime.now());
        entitySet(entity, "updatedBy", currentActor());
        limitRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_LIMIT_DEFINITION, AuditEventAction.COMMERCIAL_LIMIT_RETIRED, "Retired limit definition", Map.of("code", entity.getCode()));
        return toLimitDetail(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AddonSummaryResponse> listAddons(String search, Status status, int page, int size) {
        Page<CommercialAddonOfferEntity> result = addonRepository.findAll(addonSpec(search, status),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("code"))));
        return new PageResponse<>(result.map(this::toAddonSummary).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AddonDetailResponse getAddon(UUID id) {
        CommercialAddonOfferEntity entity = addonRepository.findById(id).orElseThrow(() -> notFound("Addon offer", id));
        return toAddonDetail(entity);
    }

    @Transactional
    public AddonDetailResponse createAddon(CreateAddonOfferRequest request) {
        String code = normalizeCode(requireText(request == null ? null : request.code(), "code is required"));
        if (addonRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("Addon code already exists: " + code);
        }
        CommercialAddonOfferEntity entity = newAddon(request, code);
        addonRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_ADDON_OFFER, AuditEventAction.COMMERCIAL_ADDON_CREATED, "Created addon", Map.of("code", code, "type", entity.getAddonType().name()));
        return toAddonDetail(entity);
    }

    @Transactional
    public AddonDetailResponse updateAddon(UUID id, UpdateAddonOfferRequest request) {
        CommercialAddonOfferEntity entity = addonRepository.findById(id).orElseThrow(() -> notFound("Addon offer", id));
        entitySet(entity, "name", requireText(request.name(), "name is required"));
        entitySet(entity, "description", blankToNull(request.description()));
        entitySet(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
        entitySet(entity, "addonType", request.addonType() == null ? AddonType.CAPABILITY : request.addonType());
        entitySet(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
        entitySet(entity, "repeatable", Boolean.TRUE.equals(request.repeatable()));
        entitySet(entity, "updatedAt", OffsetDateTime.now());
        entitySet(entity, "updatedBy", currentActor());
        addonRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_ADDON_OFFER, AuditEventAction.COMMERCIAL_ADDON_UPDATED, "Updated addon", Map.of("code", entity.getCode(), "type", entity.getAddonType().name()));
        return toAddonDetail(entity);
    }

    @Transactional
    public AddonDetailResponse retireAddon(UUID id) {
        CommercialAddonOfferEntity entity = addonRepository.findById(id).orElseThrow(() -> notFound("Addon offer", id));
        entitySet(entity, "status", Status.RETIRED);
        entitySet(entity, "updatedAt", OffsetDateTime.now());
        entitySet(entity, "updatedBy", currentActor());
        addonRepository.save(entity);
        audit(entity.getId(), AuditEntityType.COMMERCIAL_ADDON_OFFER, AuditEventAction.COMMERCIAL_ADDON_RETIRED, "Retired addon", Map.of("code", entity.getCode()));
        return toAddonDetail(entity);
    }

    @Transactional
    public AddonDetailResponse updateAddonCapabilities(UUID addonId, UpdateAddonCapabilitiesRequest request) {
        CommercialAddonOfferEntity addon = activeAddon(addonId);
        addonCapabilityRepository.deleteByAddon_Id(addonId);
        List<CommercialAddonCapabilityEntity> relations = new ArrayList<>();
        for (UUID capabilityId : safeIds(request == null ? null : request.capabilityIds())) {
            CommercialCapabilityEntity capability = activeCapability(capabilityId);
            CommercialAddonCapabilityEntity relation = new CommercialAddonCapabilityEntity();
            setField(relation, "id", new CommercialAddonCapabilityId(addonId, capabilityId));
            setField(relation, "addon", addon);
            setField(relation, "capability", capability);
            relations.add(relation);
        }
        addonCapabilityRepository.saveAll(relations);
        audit(addonId, AuditEntityType.COMMERCIAL_ADDON_OFFER, AuditEventAction.COMMERCIAL_RELATIONSHIP_UPDATED, "Updated addon capabilities", Map.of("count", relations.size()));
        return toAddonDetail(addon);
    }

    @Transactional
    public AddonDetailResponse updateAddonModules(UUID addonId, UpdateAddonModulesRequest request) {
        CommercialAddonOfferEntity addon = activeAddon(addonId);
        addonModuleRepository.deleteByAddon_Id(addonId);
        List<CommercialAddonModuleEntity> relations = new ArrayList<>();
        for (UUID moduleId : safeIds(request == null ? null : request.moduleIds())) {
            CommercialModuleEntity module = activeModule(moduleId);
            CommercialAddonModuleEntity relation = new CommercialAddonModuleEntity();
            setField(relation, "id", new CommercialAddonModuleId(addonId, moduleId));
            setField(relation, "addon", addon);
            setField(relation, "module", module);
            relations.add(relation);
        }
        addonModuleRepository.saveAll(relations);
        audit(addonId, AuditEntityType.COMMERCIAL_ADDON_OFFER, AuditEventAction.COMMERCIAL_RELATIONSHIP_UPDATED, "Updated addon modules", Map.of("count", relations.size()));
        return toAddonDetail(addon);
    }

    @Transactional
    public AddonDetailResponse updateAddonFeatures(UUID addonId, UpdateAddonFeaturesRequest request) {
        CommercialAddonOfferEntity addon = activeAddon(addonId);
        addonFeatureRepository.deleteByAddon_Id(addonId);
        List<CommercialAddonFeatureEntity> relations = new ArrayList<>();
        for (UUID featureId : safeIds(request == null ? null : request.featureIds())) {
            CommercialFeatureEntity feature = activeFeature(featureId);
            CommercialAddonFeatureEntity relation = new CommercialAddonFeatureEntity();
            setField(relation, "id", new CommercialAddonFeatureId(addonId, featureId));
            setField(relation, "addon", addon);
            setField(relation, "feature", feature);
            relations.add(relation);
        }
        addonFeatureRepository.saveAll(relations);
        audit(addonId, AuditEntityType.COMMERCIAL_ADDON_OFFER, AuditEventAction.COMMERCIAL_RELATIONSHIP_UPDATED, "Updated addon features", Map.of("count", relations.size()));
        return toAddonDetail(addon);
    }

    @Transactional
    public AddonDetailResponse updateAddonLimitIncrements(UUID addonId, UpdateAddonLimitIncrementsRequest request) {
        CommercialAddonOfferEntity addon = activeAddon(addonId);
        addonLimitIncrementRepository.deleteByAddon_Id(addonId);
        List<CommercialAddonLimitIncrementEntity> relations = new ArrayList<>();
        for (AddonLimitIncrementAssignmentRequest assignment : request == null || request.limitIncrements() == null ? List.<AddonLimitIncrementAssignmentRequest>of() : request.limitIncrements()) {
            if (assignment.incrementValue() == null || assignment.incrementValue().signum() <= 0) {
                throw new IllegalArgumentException("incrementValue must be positive");
            }
            CommercialLimitDefinitionEntity limit = activeLimit(requireUuid(assignment.limitDefinitionId(), "limitDefinitionId is required"));
            CommercialAddonLimitIncrementEntity relation = new CommercialAddonLimitIncrementEntity();
            setField(relation, "id", new CommercialAddonLimitIncrementId(addonId, limit.getId()));
            setField(relation, "addon", addon);
            setField(relation, "limitDefinition", limit);
            setField(relation, "incrementValue", assignment.incrementValue());
            relations.add(relation);
        }
        addonLimitIncrementRepository.saveAll(relations);
        audit(addonId, AuditEntityType.COMMERCIAL_ADDON_OFFER, AuditEventAction.COMMERCIAL_RELATIONSHIP_UPDATED, "Updated addon limits", Map.of("count", relations.size()));
        return toAddonDetail(addon);
    }

    private PageResponse<CapabilitySummaryResponse> page(Page<CommercialCapabilityEntity> result) {
        return new PageResponse<>(result.map(this::toCapabilitySummary).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private CapabilitySummaryResponse toCapabilitySummary(CommercialCapabilityEntity entity) {
        return new CapabilitySummaryResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getDisplayOrder(), entity.isStandaloneAllowed(), entity.isAddonAllowed(), entity.getModules().size());
    }

    private CapabilityDetailResponse toCapabilityDetail(CommercialCapabilityEntity entity) {
        List<CapabilityModuleResponse> modules = capabilityModuleRepository.findByCapability_IdOrderByDisplayOrderAsc(entity.getId()).stream()
                .map(rel -> new CapabilityModuleResponse(rel.getModule().getId(), rel.getModule().getCode(), rel.getModule().getName(), rel.isIncludedByDefault(), rel.getDisplayOrder()))
                .toList();
        return new CapabilityDetailResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getDisplayOrder(), entity.isStandaloneAllowed(), entity.isAddonAllowed(), modules);
    }

    private ModuleSummaryResponse toModuleSummary(CommercialModuleEntity entity) {
        return new ModuleSummaryResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getDisplayOrder(), entity.getRuntimeModuleCode(), entity.getCapabilities().size());
    }

    private ModuleDetailResponse toModuleDetail(CommercialModuleEntity entity) {
        List<ModuleCapabilityResponse> capabilities = entity.getCapabilities().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(rel -> new ModuleCapabilityResponse(rel.getCapability().getId(), rel.getCapability().getCode(), rel.getCapability().getName(), rel.isIncludedByDefault(), rel.getDisplayOrder()))
                .toList();
        return new ModuleDetailResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getDisplayOrder(), entity.getRuntimeModuleCode(), capabilities);
    }

    private FeatureSummaryResponse toFeatureSummary(CommercialFeatureEntity entity) {
        CommercialModuleEntity module = entity.getModule();
        return new FeatureSummaryResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getDisplayOrder(), module.getId(), module.getCode(), module.getName(), entity.getRuntimeFeatureKey());
    }

    private FeatureDetailResponse toFeatureDetail(CommercialFeatureEntity entity) {
        CommercialModuleEntity module = entity.getModule();
        return new FeatureDetailResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getDisplayOrder(), module.getId(), module.getCode(), module.getName(), entity.getRuntimeFeatureKey());
    }

    private LimitDefinitionSummaryResponse toLimitSummary(CommercialLimitDefinitionEntity entity) {
        return new LimitDefinitionSummaryResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getUnit(), entity.getValueType(), entity.getAggregationPeriod(), entity.getEnforcementMode(), entity.getStatus(), entity.getDisplayOrder());
    }

    private LimitDefinitionDetailResponse toLimitDetail(CommercialLimitDefinitionEntity entity) {
        return new LimitDefinitionDetailResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getUnit(), entity.getValueType(), entity.getAggregationPeriod(), entity.getEnforcementMode(), entity.getStatus(), entity.getDisplayOrder());
    }

    private AddonSummaryResponse toAddonSummary(CommercialAddonOfferEntity entity) {
        return new AddonSummaryResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getAddonType(), entity.getDisplayOrder(), entity.isRepeatable(), entity.getCapabilities().size(), entity.getModules().size(), entity.getFeatures().size(), entity.getLimitIncrements().size());
    }

    private AddonDetailResponse toAddonDetail(CommercialAddonOfferEntity entity) {
        List<AddonCapabilityResponse> capabilities = addonCapabilityRepository.findByAddon_Id(entity.getId()).stream()
                .map(rel -> new AddonCapabilityResponse(rel.getCapability().getId(), rel.getCapability().getCode(), rel.getCapability().getName()))
                .toList();
        List<AddonModuleResponse> modules = addonModuleRepository.findByAddon_Id(entity.getId()).stream()
                .map(rel -> new AddonModuleResponse(rel.getModule().getId(), rel.getModule().getCode(), rel.getModule().getName()))
                .toList();
        List<AddonFeatureResponse> features = addonFeatureRepository.findByAddon_Id(entity.getId()).stream()
                .map(rel -> new AddonFeatureResponse(rel.getFeature().getId(), rel.getFeature().getCode(), rel.getFeature().getName()))
                .toList();
        List<AddonLimitIncrementResponse> limitIncrements = addonLimitIncrementRepository.findByAddon_Id(entity.getId()).stream()
                .map(rel -> new AddonLimitIncrementResponse(rel.getLimitDefinition().getId(), rel.getLimitDefinition().getCode(), rel.getLimitDefinition().getName(), rel.getIncrementValue()))
                .toList();
        return new AddonDetailResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getAddonType(), entity.getDisplayOrder(), entity.isRepeatable(), capabilities, modules, features, limitIncrements);
    }

    private Specification<CommercialCapabilityEntity> capabilitySpec(String search, Status status) {
        return (root, query, cb) -> andFilters(root, cb, search, status);
    }

    private Specification<CommercialModuleEntity> moduleSpec(String search, Status status) {
        return (root, query, cb) -> andFilters(root, cb, search, status);
    }

    private Specification<CommercialFeatureEntity> featureSpec(String search, Status status) {
        return (root, query, cb) -> andFilters(root, cb, search, status);
    }

    private Specification<CommercialLimitDefinitionEntity> limitSpec(String search, Status status) {
        return (root, query, cb) -> andFilters(root, cb, search, status);
    }

    private Specification<CommercialAddonOfferEntity> addonSpec(String search, Status status) {
        return (root, query, cb) -> andFilters(root, cb, search, status);
    }

    private jakarta.persistence.criteria.Predicate andFilters(jakarta.persistence.criteria.Root<?> root, jakarta.persistence.criteria.CriteriaBuilder cb, String search, Status status) {
        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("name")), like)));
        }
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    }

    private CommercialCapabilityEntity newCapability(CreateCapabilityRequest request, String code) {
        CommercialCapabilityEntity entity = new CommercialCapabilityEntity();
        setField(entity, "id", UUID.randomUUID());
        setField(entity, "code", code);
        setField(entity, "name", requireText(request.name(), "name is required"));
        setField(entity, "description", blankToNull(request.description()));
        setField(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
        setField(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
        setField(entity, "standaloneAllowed", request.standaloneAllowed() == null || request.standaloneAllowed());
        setField(entity, "addonAllowed", request.addonAllowed() == null || request.addonAllowed());
        setField(entity, "createdAt", OffsetDateTime.now());
        setField(entity, "updatedAt", OffsetDateTime.now());
        setField(entity, "createdBy", currentActor());
        setField(entity, "updatedBy", currentActor());
        return entity;
    }

    private CommercialModuleEntity newModule(CreateModuleRequest request, String code, String runtime) {
        CommercialModuleEntity entity = new CommercialModuleEntity();
        setField(entity, "id", UUID.randomUUID());
        setField(entity, "code", code);
        setField(entity, "name", requireText(request.name(), "name is required"));
        setField(entity, "description", blankToNull(request.description()));
        setField(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
        setField(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
        setField(entity, "runtimeModuleCode", runtime);
        setField(entity, "createdAt", OffsetDateTime.now());
        setField(entity, "updatedAt", OffsetDateTime.now());
        setField(entity, "createdBy", currentActor());
        setField(entity, "updatedBy", currentActor());
        return entity;
    }

    private CommercialFeatureEntity newFeature(CreateFeatureRequest request, String code, CommercialModuleEntity module) {
        CommercialFeatureEntity entity = new CommercialFeatureEntity();
        setField(entity, "id", UUID.randomUUID());
        setField(entity, "code", code);
        setField(entity, "name", requireText(request.name(), "name is required"));
        setField(entity, "description", blankToNull(request.description()));
        setField(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
        setField(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
        setField(entity, "module", module);
        setField(entity, "runtimeFeatureKey", blankToNull(request.runtimeFeatureKey()));
        setField(entity, "createdAt", OffsetDateTime.now());
        setField(entity, "updatedAt", OffsetDateTime.now());
        setField(entity, "createdBy", currentActor());
        setField(entity, "updatedBy", currentActor());
        return entity;
    }

    private CommercialLimitDefinitionEntity newLimit(CreateLimitDefinitionRequest request, String code) {
        CommercialLimitDefinitionEntity entity = new CommercialLimitDefinitionEntity();
        setField(entity, "id", UUID.randomUUID());
        setField(entity, "code", code);
        setField(entity, "name", requireText(request.name(), "name is required"));
        setField(entity, "description", blankToNull(request.description()));
        setField(entity, "unit", requireText(request.unit(), "unit is required"));
        setField(entity, "valueType", request.valueType());
        setField(entity, "aggregationPeriod", request.aggregationPeriod());
        setField(entity, "enforcementMode", request.enforcementMode());
        setField(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
        setField(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
        setField(entity, "createdAt", OffsetDateTime.now());
        setField(entity, "updatedAt", OffsetDateTime.now());
        setField(entity, "createdBy", currentActor());
        setField(entity, "updatedBy", currentActor());
        return entity;
    }

    private CommercialAddonOfferEntity newAddon(CreateAddonOfferRequest request, String code) {
        CommercialAddonOfferEntity entity = new CommercialAddonOfferEntity();
        setField(entity, "id", UUID.randomUUID());
        setField(entity, "code", code);
        setField(entity, "name", requireText(request.name(), "name is required"));
        setField(entity, "description", blankToNull(request.description()));
        setField(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
        setField(entity, "addonType", request.addonType() == null ? AddonType.CAPABILITY : request.addonType());
        setField(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
        setField(entity, "repeatable", Boolean.TRUE.equals(request.repeatable()));
        setField(entity, "createdAt", OffsetDateTime.now());
        setField(entity, "updatedAt", OffsetDateTime.now());
        setField(entity, "createdBy", currentActor());
        setField(entity, "updatedBy", currentActor());
        return entity;
    }

    private void applyCapabilityUpdate(CommercialCapabilityEntity entity, UpdateCapabilityRequest request) {
        if (request != null) {
            setField(entity, "name", requireText(request.name(), "name is required"));
            setField(entity, "description", blankToNull(request.description()));
            setField(entity, "status", request.status() == null ? Status.ACTIVE : request.status());
            setField(entity, "displayOrder", request.displayOrder() == null ? 0 : request.displayOrder());
            setField(entity, "standaloneAllowed", request.standaloneAllowed() == null || request.standaloneAllowed());
            setField(entity, "addonAllowed", request.addonAllowed() == null || request.addonAllowed());
            setField(entity, "updatedAt", OffsetDateTime.now());
            setField(entity, "updatedBy", currentActor());
        }
    }

    private void entityStatus(Object entity, Status status) {
        setField(entity, "status", status);
        setField(entity, "updatedAt", OffsetDateTime.now());
        setField(entity, "updatedBy", currentActor());
    }

    private CommercialCapabilityEntity activeCapability(UUID id) {
        CommercialCapabilityEntity capability = capabilityRepository.findById(id).orElseThrow(() -> notFound("Capability", id));
        if (capability.getStatus() == Status.RETIRED) {
            throw new IllegalArgumentException("Retired capability cannot be referenced");
        }
        return capability;
    }

    private CommercialModuleEntity activeModule(UUID id) {
        CommercialModuleEntity module = moduleRepository.findById(id).orElseThrow(() -> notFound("Module", id));
        if (module.getStatus() == Status.RETIRED) {
            throw new IllegalArgumentException("Retired module cannot be referenced");
        }
        return module;
    }

    private CommercialFeatureEntity activeFeature(UUID id) {
        CommercialFeatureEntity feature = featureRepository.findById(id).orElseThrow(() -> notFound("Feature", id));
        if (feature.getStatus() == Status.RETIRED) {
            throw new IllegalArgumentException("Retired feature cannot be referenced");
        }
        return feature;
    }

    private CommercialLimitDefinitionEntity activeLimit(UUID id) {
        CommercialLimitDefinitionEntity limit = limitRepository.findById(id).orElseThrow(() -> notFound("Limit definition", id));
        if (limit.getStatus() == Status.RETIRED) {
            throw new IllegalArgumentException("Retired limit definition cannot be referenced");
        }
        return limit;
    }

    private CommercialAddonOfferEntity activeAddon(UUID id) {
        CommercialAddonOfferEntity addon = addonRepository.findById(id).orElseThrow(() -> notFound("Addon offer", id));
        if (addon.getStatus() == Status.RETIRED) {
            throw new IllegalArgumentException("Retired addon cannot be referenced");
        }
        return addon;
    }

    private Status requireStatus(Status status) {
        return status == null ? Status.ACTIVE : status;
    }

    private String normalizeCode(String value) {
        String trimmed = requireText(value, "code is required").trim();
        if (!trimmed.equals(trimmed.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("code must be uppercase");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeRuntimeModuleCode(String runtimeModuleCode) {
        if (!StringUtils.hasText(runtimeModuleCode)) {
            return null;
        }
        String normalized = normalizeCode(runtimeModuleCode);
        if (!ACTIVE_RUNTIME_MODULE_CODES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown runtime module code: " + runtimeModuleCode);
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private UUID requireUuid(UUID id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
        return id;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String type, UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found");
    }

    private UUID currentActor() {
        var ctx = RequestContextHolder.get();
        return ctx == null ? null : ctx.appUserId();
    }

    private void audit(UUID entityId, String entityType, String action, String summary, Map<String, Object> details) {
        try {
            String json = objectMapper.writeValueAsString(details == null ? Map.of() : details);
            auditEventPublisher.record(new AuditEventCommand(
                    RequestContextHolder.requireTenantId(),
                    entityType,
                    entityId,
                    action,
                    currentActor(),
                    OffsetDateTime.now(),
                    summary,
                    json
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize catalog audit payload", ex);
        }
    }

    private <T> void setField(Object target, String fieldName, T value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set field " + fieldName + " on " + target.getClass().getSimpleName(), ex);
        }
    }

    private void entitySet(Object target, String fieldName, Object value) {
        setField(target, fieldName, value);
    }

    private Set<UUID> safeIds(List<UUID> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }
}
