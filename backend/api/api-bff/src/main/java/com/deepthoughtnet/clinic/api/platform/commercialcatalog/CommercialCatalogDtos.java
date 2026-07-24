package com.deepthoughtnet.clinic.api.platform.commercialcatalog;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AddonType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AggregationPeriod;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.EnforcementMode;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.LimitValueType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import java.util.List;
import java.util.UUID;

public final class CommercialCatalogDtos {
    private CommercialCatalogDtos() {
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record CapabilitySummaryResponse(
            UUID id,
            String code,
            String name,
            String description,
            Status status,
            int displayOrder,
            boolean standaloneAllowed,
            boolean addonAllowed,
            int moduleCount
    ) {
    }

    public record CapabilityDetailResponse(
            UUID id,
            String code,
            String name,
            String description,
            Status status,
            int displayOrder,
            boolean standaloneAllowed,
            boolean addonAllowed,
            List<CapabilityModuleResponse> modules
    ) {
    }

    public record CapabilityModuleResponse(
            UUID moduleId,
            String moduleCode,
            String moduleName,
            boolean includedByDefault,
            int displayOrder
    ) {
    }

    public record CreateCapabilityRequest(
            String code,
            String name,
            String description,
            Status status,
            Integer displayOrder,
            Boolean standaloneAllowed,
            Boolean addonAllowed
    ) {
    }

    public record UpdateCapabilityRequest(
            String name,
            String description,
            Status status,
            Integer displayOrder,
            Boolean standaloneAllowed,
            Boolean addonAllowed
    ) {
    }

    public record UpdateCapabilityModulesRequest(
            List<CapabilityModuleAssignmentRequest> modules
    ) {
    }

    public record CapabilityModuleAssignmentRequest(
            UUID moduleId,
            Boolean includedByDefault,
            Integer displayOrder
    ) {
    }

    public record ModuleSummaryResponse(
            UUID id,
            String code,
            String name,
            String description,
            Status status,
            int displayOrder,
            String runtimeModuleCode,
            int capabilityCount
    ) {
    }

    public record ModuleDetailResponse(
            UUID id,
            String code,
            String name,
            String description,
            Status status,
            int displayOrder,
            String runtimeModuleCode,
            List<ModuleCapabilityResponse> capabilities
    ) {
    }

    public record ModuleCapabilityResponse(
            UUID capabilityId,
            String capabilityCode,
            String capabilityName,
            boolean includedByDefault,
            int displayOrder
    ) {
    }

    public record CreateModuleRequest(
            String code,
            String name,
            String description,
            Status status,
            Integer displayOrder,
            String runtimeModuleCode
    ) {
    }

    public record UpdateModuleRequest(
            String name,
            String description,
            Status status,
            Integer displayOrder,
            String runtimeModuleCode
    ) {
    }

    public record FeatureSummaryResponse(
            UUID id,
            String code,
            String name,
            String description,
            Status status,
            int displayOrder,
            UUID moduleId,
            String moduleCode,
            String moduleName,
            String runtimeFeatureKey
    ) {
    }

    public record FeatureDetailResponse(
            UUID id,
            String code,
            String name,
            String description,
            Status status,
            int displayOrder,
            UUID moduleId,
            String moduleCode,
            String moduleName,
            String runtimeFeatureKey
    ) {
    }

    public record CreateFeatureRequest(
            String code,
            String name,
            String description,
            Status status,
            Integer displayOrder,
            UUID moduleId,
            String runtimeFeatureKey
    ) {
    }

    public record UpdateFeatureRequest(
            String name,
            String description,
            Status status,
            Integer displayOrder,
            UUID moduleId,
            String runtimeFeatureKey
    ) {
    }

    public record LimitDefinitionSummaryResponse(
            UUID id,
            String code,
            String name,
            String description,
            String unit,
            LimitValueType valueType,
            AggregationPeriod aggregationPeriod,
            EnforcementMode enforcementMode,
            Status status,
            int displayOrder
    ) {
    }

    public record LimitDefinitionDetailResponse(
            UUID id,
            String code,
            String name,
            String description,
            String unit,
            LimitValueType valueType,
            AggregationPeriod aggregationPeriod,
            EnforcementMode enforcementMode,
            Status status,
            int displayOrder
    ) {
    }

    public record CreateLimitDefinitionRequest(
            String code,
            String name,
            String description,
            String unit,
            LimitValueType valueType,
            AggregationPeriod aggregationPeriod,
            EnforcementMode enforcementMode,
            Status status,
            Integer displayOrder
    ) {
    }

    public record UpdateLimitDefinitionRequest(
            String name,
            String description,
            String unit,
            LimitValueType valueType,
            AggregationPeriod aggregationPeriod,
            EnforcementMode enforcementMode,
            Status status,
            Integer displayOrder
    ) {
    }

    public record AddonSummaryResponse(
            UUID id,
            String code,
            String name,
            String description,
            Status status,
            AddonType addonType,
            int displayOrder,
            boolean repeatable,
            int capabilityCount,
            int moduleCount,
            int featureCount,
            int limitIncrementCount
    ) {
    }

    public record AddonDetailResponse(
            UUID id,
            String code,
            String name,
            String description,
            Status status,
            AddonType addonType,
            int displayOrder,
            boolean repeatable,
            List<AddonCapabilityResponse> capabilities,
            List<AddonModuleResponse> modules,
            List<AddonFeatureResponse> features,
            List<AddonLimitIncrementResponse> limitIncrements
    ) {
    }

    public record AddonCapabilityResponse(
            UUID capabilityId,
            String capabilityCode,
            String capabilityName
    ) {
    }

    public record AddonModuleResponse(
            UUID moduleId,
            String moduleCode,
            String moduleName
    ) {
    }

    public record AddonFeatureResponse(
            UUID featureId,
            String featureCode,
            String featureName
    ) {
    }

    public record AddonLimitIncrementResponse(
            UUID limitDefinitionId,
            String limitDefinitionCode,
            String limitDefinitionName,
            java.math.BigDecimal incrementValue
    ) {
    }

    public record CreateAddonOfferRequest(
            String code,
            String name,
            String description,
            Status status,
            AddonType addonType,
            Integer displayOrder,
            Boolean repeatable
    ) {
    }

    public record UpdateAddonOfferRequest(
            String name,
            String description,
            Status status,
            AddonType addonType,
            Integer displayOrder,
            Boolean repeatable
    ) {
    }

    public record UpdateAddonCapabilitiesRequest(List<UUID> capabilityIds) {
    }

    public record UpdateAddonModulesRequest(List<UUID> moduleIds) {
    }

    public record UpdateAddonFeaturesRequest(List<UUID> featureIds) {
    }

    public record UpdateAddonLimitIncrementsRequest(List<AddonLimitIncrementAssignmentRequest> limitIncrements) {
    }

    public record AddonLimitIncrementAssignmentRequest(UUID limitDefinitionId, java.math.BigDecimal incrementValue) {
    }
}
