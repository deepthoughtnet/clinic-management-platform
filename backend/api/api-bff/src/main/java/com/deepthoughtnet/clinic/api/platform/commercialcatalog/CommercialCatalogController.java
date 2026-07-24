package com.deepthoughtnet.clinic.api.platform.commercialcatalog;

import com.deepthoughtnet.clinic.api.platform.commercialcatalog.CommercialCatalogDtos.*;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/commercial-catalog")
@PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
public class CommercialCatalogController {
    private final CommercialCatalogApiService service;

    public CommercialCatalogController(CommercialCatalogApiService service) {
        this.service = service;
    }

    @GetMapping("/capabilities")
    public PageResponse<CapabilitySummaryResponse> listCapabilities(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listCapabilities(search, status, page, size);
    }

    @GetMapping("/capabilities/{id}")
    public CapabilityDetailResponse getCapability(@PathVariable UUID id) {
        return service.getCapability(id);
    }

    @PostMapping("/capabilities")
    public CapabilityDetailResponse createCapability(@RequestBody CreateCapabilityRequest request) {
        return service.createCapability(request);
    }

    @PutMapping("/capabilities/{id}")
    public CapabilityDetailResponse updateCapability(@PathVariable UUID id, @RequestBody UpdateCapabilityRequest request) {
        return service.updateCapability(id, request);
    }

    @PostMapping("/capabilities/{id}/retire")
    public CapabilityDetailResponse retireCapability(@PathVariable UUID id) {
        return service.retireCapability(id);
    }

    @PutMapping("/capabilities/{capabilityId}/modules")
    public CapabilityDetailResponse updateCapabilityModules(@PathVariable UUID capabilityId, @RequestBody UpdateCapabilityModulesRequest request) {
        return service.updateCapabilityModules(capabilityId, request);
    }

    @GetMapping("/modules")
    public PageResponse<ModuleSummaryResponse> listModules(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listModules(search, status, page, size);
    }

    @GetMapping("/modules/{id}")
    public ModuleDetailResponse getModule(@PathVariable UUID id) {
        return service.getModule(id);
    }

    @PostMapping("/modules")
    public ModuleDetailResponse createModule(@RequestBody CreateModuleRequest request) {
        return service.createModule(request);
    }

    @PutMapping("/modules/{id}")
    public ModuleDetailResponse updateModule(@PathVariable UUID id, @RequestBody UpdateModuleRequest request) {
        return service.updateModule(id, request);
    }

    @PostMapping("/modules/{id}/retire")
    public ModuleDetailResponse retireModule(@PathVariable UUID id) {
        return service.retireModule(id);
    }

    @GetMapping("/features")
    public PageResponse<FeatureSummaryResponse> listFeatures(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listFeatures(search, status, page, size);
    }

    @GetMapping("/features/{id}")
    public FeatureDetailResponse getFeature(@PathVariable UUID id) {
        return service.getFeature(id);
    }

    @PostMapping("/features")
    public FeatureDetailResponse createFeature(@RequestBody CreateFeatureRequest request) {
        return service.createFeature(request);
    }

    @PutMapping("/features/{id}")
    public FeatureDetailResponse updateFeature(@PathVariable UUID id, @RequestBody UpdateFeatureRequest request) {
        return service.updateFeature(id, request);
    }

    @PostMapping("/features/{id}/retire")
    public FeatureDetailResponse retireFeature(@PathVariable UUID id) {
        return service.retireFeature(id);
    }

    @GetMapping("/limits")
    public PageResponse<LimitDefinitionSummaryResponse> listLimits(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listLimits(search, status, page, size);
    }

    @GetMapping("/limits/{id}")
    public LimitDefinitionDetailResponse getLimit(@PathVariable UUID id) {
        return service.getLimit(id);
    }

    @PostMapping("/limits")
    public LimitDefinitionDetailResponse createLimit(@RequestBody CreateLimitDefinitionRequest request) {
        return service.createLimit(request);
    }

    @PutMapping("/limits/{id}")
    public LimitDefinitionDetailResponse updateLimit(@PathVariable UUID id, @RequestBody UpdateLimitDefinitionRequest request) {
        return service.updateLimit(id, request);
    }

    @PostMapping("/limits/{id}/retire")
    public LimitDefinitionDetailResponse retireLimit(@PathVariable UUID id) {
        return service.retireLimit(id);
    }

    @GetMapping("/addons")
    public PageResponse<AddonSummaryResponse> listAddons(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listAddons(search, status, page, size);
    }

    @GetMapping("/addons/{id}")
    public AddonDetailResponse getAddon(@PathVariable UUID id) {
        return service.getAddon(id);
    }

    @PostMapping("/addons")
    public AddonDetailResponse createAddon(@RequestBody CreateAddonOfferRequest request) {
        return service.createAddon(request);
    }

    @PutMapping("/addons/{id}")
    public AddonDetailResponse updateAddon(@PathVariable UUID id, @RequestBody UpdateAddonOfferRequest request) {
        return service.updateAddon(id, request);
    }

    @PostMapping("/addons/{id}/retire")
    public AddonDetailResponse retireAddon(@PathVariable UUID id) {
        return service.retireAddon(id);
    }

    @PutMapping("/addons/{id}/capabilities")
    public AddonDetailResponse updateAddonCapabilities(@PathVariable UUID id, @RequestBody UpdateAddonCapabilitiesRequest request) {
        return service.updateAddonCapabilities(id, request);
    }

    @PutMapping("/addons/{id}/modules")
    public AddonDetailResponse updateAddonModules(@PathVariable UUID id, @RequestBody UpdateAddonModulesRequest request) {
        return service.updateAddonModules(id, request);
    }

    @PutMapping("/addons/{id}/features")
    public AddonDetailResponse updateAddonFeatures(@PathVariable UUID id, @RequestBody UpdateAddonFeaturesRequest request) {
        return service.updateAddonFeatures(id, request);
    }

    @PutMapping("/addons/{id}/limit-increments")
    public AddonDetailResponse updateAddonLimitIncrements(@PathVariable UUID id, @RequestBody UpdateAddonLimitIncrementsRequest request) {
        return service.updateAddonLimitIncrements(id, request);
    }
}
