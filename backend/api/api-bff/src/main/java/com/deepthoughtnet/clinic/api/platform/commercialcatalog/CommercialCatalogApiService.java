package com.deepthoughtnet.clinic.api.platform.commercialcatalog;

import com.deepthoughtnet.clinic.api.platform.commercialcatalog.CommercialCatalogDtos.*;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CommercialCatalogApiService {
    private final CommercialCatalogService delegate;
    private final ObjectMapper objectMapper;

    public CommercialCatalogApiService(CommercialCatalogService delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    public PageResponse<CapabilitySummaryResponse> listCapabilities(String search, Status status, int page, int size) {
        return mapPage(delegate.listCapabilities(search, mapStatus(status), page, size), CapabilitySummaryResponse.class);
    }

    public CapabilityDetailResponse getCapability(java.util.UUID id) {
        return map(delegate.getCapability(id), CapabilityDetailResponse.class);
    }

    public CapabilityDetailResponse createCapability(CreateCapabilityRequest request) {
        return map(delegate.createCapability(map(request, CreateCapabilityRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.CreateCapabilityRequest.class)), CapabilityDetailResponse.class);
    }

    public CapabilityDetailResponse updateCapability(java.util.UUID id, UpdateCapabilityRequest request) {
        return map(delegate.updateCapability(id, map(request, UpdateCapabilityRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateCapabilityRequest.class)), CapabilityDetailResponse.class);
    }

    public CapabilityDetailResponse retireCapability(java.util.UUID id) {
        return map(delegate.retireCapability(id), CapabilityDetailResponse.class);
    }

    public CapabilityDetailResponse updateCapabilityModules(java.util.UUID id, UpdateCapabilityModulesRequest request) {
        return map(delegate.updateCapabilityModules(id, map(request, UpdateCapabilityModulesRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateCapabilityModulesRequest.class)), CapabilityDetailResponse.class);
    }

    public PageResponse<ModuleSummaryResponse> listModules(String search, Status status, int page, int size) {
        return mapPage(delegate.listModules(search, mapStatus(status), page, size), ModuleSummaryResponse.class);
    }

    public ModuleDetailResponse getModule(java.util.UUID id) {
        return map(delegate.getModule(id), ModuleDetailResponse.class);
    }

    public ModuleDetailResponse createModule(CreateModuleRequest request) {
        return map(delegate.createModule(map(request, CreateModuleRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.CreateModuleRequest.class)), ModuleDetailResponse.class);
    }

    public ModuleDetailResponse updateModule(java.util.UUID id, UpdateModuleRequest request) {
        return map(delegate.updateModule(id, map(request, UpdateModuleRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateModuleRequest.class)), ModuleDetailResponse.class);
    }

    public ModuleDetailResponse retireModule(java.util.UUID id) {
        return map(delegate.retireModule(id), ModuleDetailResponse.class);
    }

    public PageResponse<FeatureSummaryResponse> listFeatures(String search, Status status, int page, int size) {
        return mapPage(delegate.listFeatures(search, mapStatus(status), page, size), FeatureSummaryResponse.class);
    }

    public FeatureDetailResponse getFeature(java.util.UUID id) {
        return map(delegate.getFeature(id), FeatureDetailResponse.class);
    }

    public FeatureDetailResponse createFeature(CreateFeatureRequest request) {
        return map(delegate.createFeature(map(request, CreateFeatureRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.CreateFeatureRequest.class)), FeatureDetailResponse.class);
    }

    public FeatureDetailResponse updateFeature(java.util.UUID id, UpdateFeatureRequest request) {
        return map(delegate.updateFeature(id, map(request, UpdateFeatureRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateFeatureRequest.class)), FeatureDetailResponse.class);
    }

    public FeatureDetailResponse retireFeature(java.util.UUID id) {
        return map(delegate.retireFeature(id), FeatureDetailResponse.class);
    }

    public PageResponse<LimitDefinitionSummaryResponse> listLimits(String search, Status status, int page, int size) {
        return mapPage(delegate.listLimits(search, mapStatus(status), page, size), LimitDefinitionSummaryResponse.class);
    }

    public LimitDefinitionDetailResponse getLimit(java.util.UUID id) {
        return map(delegate.getLimit(id), LimitDefinitionDetailResponse.class);
    }

    public LimitDefinitionDetailResponse createLimit(CreateLimitDefinitionRequest request) {
        return map(delegate.createLimit(map(request, CreateLimitDefinitionRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.CreateLimitDefinitionRequest.class)), LimitDefinitionDetailResponse.class);
    }

    public LimitDefinitionDetailResponse updateLimit(java.util.UUID id, UpdateLimitDefinitionRequest request) {
        return map(delegate.updateLimit(id, map(request, UpdateLimitDefinitionRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateLimitDefinitionRequest.class)), LimitDefinitionDetailResponse.class);
    }

    public LimitDefinitionDetailResponse retireLimit(java.util.UUID id) {
        return map(delegate.retireLimit(id), LimitDefinitionDetailResponse.class);
    }

    public PageResponse<AddonSummaryResponse> listAddons(String search, Status status, int page, int size) {
        return mapPage(delegate.listAddons(search, mapStatus(status), page, size), AddonSummaryResponse.class);
    }

    public AddonDetailResponse getAddon(java.util.UUID id) {
        return map(delegate.getAddon(id), AddonDetailResponse.class);
    }

    public AddonDetailResponse createAddon(CreateAddonOfferRequest request) {
        return map(delegate.createAddon(map(request, CreateAddonOfferRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.CreateAddonOfferRequest.class)), AddonDetailResponse.class);
    }

    public AddonDetailResponse updateAddon(java.util.UUID id, UpdateAddonOfferRequest request) {
        return map(delegate.updateAddon(id, map(request, UpdateAddonOfferRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateAddonOfferRequest.class)), AddonDetailResponse.class);
    }

    public AddonDetailResponse retireAddon(java.util.UUID id) {
        return map(delegate.retireAddon(id), AddonDetailResponse.class);
    }

    public AddonDetailResponse updateAddonCapabilities(java.util.UUID id, UpdateAddonCapabilitiesRequest request) {
        return map(delegate.updateAddonCapabilities(id, map(request, UpdateAddonCapabilitiesRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateAddonCapabilitiesRequest.class)), AddonDetailResponse.class);
    }

    public AddonDetailResponse updateAddonModules(java.util.UUID id, UpdateAddonModulesRequest request) {
        return map(delegate.updateAddonModules(id, map(request, UpdateAddonModulesRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateAddonModulesRequest.class)), AddonDetailResponse.class);
    }

    public AddonDetailResponse updateAddonFeatures(java.util.UUID id, UpdateAddonFeaturesRequest request) {
        return map(delegate.updateAddonFeatures(id, map(request, UpdateAddonFeaturesRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateAddonFeaturesRequest.class)), AddonDetailResponse.class);
    }

    public AddonDetailResponse updateAddonLimitIncrements(java.util.UUID id, UpdateAddonLimitIncrementsRequest request) {
        return map(delegate.updateAddonLimitIncrements(id, map(request, UpdateAddonLimitIncrementsRequest.class, com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.UpdateAddonLimitIncrementsRequest.class)), AddonDetailResponse.class);
    }

    private <S, T> T map(S source, Class<S> sourceClass, Class<T> targetClass) {
        return objectMapper.convertValue(source, targetClass);
    }

    private <S, T> T map(S source, Class<T> targetClass) {
        return objectMapper.convertValue(source, targetClass);
    }

    private <S, T> PageResponse<T> mapPage(com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogModels.PageResponse<S> source, Class<T> targetClass) {
        List<T> items = source.items().stream().map(item -> objectMapper.convertValue(item, targetClass)).toList();
        return new PageResponse<>(items, source.page(), source.size(), source.totalElements(), source.totalPages());
    }

    private Status mapStatus(Status status) {
        return status;
    }
}
