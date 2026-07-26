package com.deepthoughtnet.clinic.api.platform.commercial.dto;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.DraftStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.PublicationStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.SelectionSource;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.SelectionState;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TargetSegment;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TemplateStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationState;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationSeverity;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.AddonPurchaseType;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.BillingCycle;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.PricingStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.TaxModel;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AddonType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AggregationPeriod;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.EnforcementMode;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.LimitValueType;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CommercialPlatformDtos {
    private CommercialPlatformDtos() {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    }

    public record KpiCardResponse(String key, String label, long value, String helperText) {
    }

    public record LifecycleStageResponse(String key, String label, boolean available, boolean comingSoon) {
    }

    public record QuickActionResponse(String key, String label, String path, boolean available, boolean primary) {
    }

    public record OverviewResponse(List<KpiCardResponse> kpis, List<LifecycleStageResponse> lifecycle, List<QuickActionResponse> actions) {
    }

    public record ValidationMessageResponse(
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
    }

    public record PlanValidationResultResponse(
            ValidationState validationState,
            boolean readyToPublish,
            int blockingFindingCount,
            int warningFindingCount,
            List<ValidationMessageResponse> findings,
            int validatedDraftRevision,
            OffsetDateTime validatedAt
    ) {
    }

    public record DraftCapabilityResponse(UUID capabilityId, String capabilityCode, String capabilityName, String description, int displayOrder, boolean selected, boolean retired) {
    }

    public record DraftModuleResponse(UUID moduleId, String moduleCode, String moduleName, String description, String runtimeModuleCode, int displayOrder, boolean selected, boolean inherited, SelectionSource selectionSource, boolean retired) {
    }

    public record DraftFeatureResponse(UUID featureId, String featureCode, String featureName, String description, UUID moduleId, String moduleCode, String moduleName, int displayOrder, boolean selected, boolean retired) {
    }

    public record DraftLimitResponse(UUID limitDefinitionId, String limitCode, String limitName, String description, String unit, LimitValueType valueType, AggregationPeriod aggregationPeriod, EnforcementMode enforcementMode, String configuredValue, int displayOrder, boolean selected, boolean retired) {
    }

    public record DraftAddonResponse(UUID addonId, String addonCode, String addonName, String description, AddonType addonType, int displayOrder, SelectionState selectionState, boolean retired) {
    }

    public record PlanPricingMeteredRateResponse(
            UUID id,
            UUID limitDefinitionId,
            String limitCode,
            String limitName,
            String includedQuantity,
            boolean overageEnabled,
            String unitPrice,
            String unitName,
            String billingRounding,
            PricingStatus status
    ) {
    }

    public record PlanPricingAddonResponse(
            UUID id,
            UUID addonOfferId,
            String addonCode,
            String addonName,
            AddonPurchaseType purchaseType,
            String monthlyPrice,
            String annualPrice,
            String oneTimePrice,
            Integer maxQuantity,
            PricingStatus status
    ) {
    }

    public record PlanPricingSnapshot(
            String currency,
            BillingCycle billingCycle,
            String monthlyPrice,
            String annualPrice,
            String setupFee,
            Integer trialDays,
            TaxModel taxModel,
            String taxPercentage,
            boolean discountAllowed,
            List<PlanPricingMeteredRateResponse> meteredRates,
            List<PlanPricingAddonResponse> addonPricing
    ) {
    }

    public record PlanPricingResponse(
            UUID id,
            UUID publishedVersionId,
            String currency,
            BillingCycle billingCycle,
            String monthlyPrice,
            String annualPrice,
            String setupFee,
            Integer trialDays,
            TaxModel taxModel,
            String taxPercentage,
            boolean discountAllowed,
            PricingStatus status,
            OffsetDateTime createdAt,
            UUID createdBy,
            List<PlanPricingMeteredRateResponse> meteredRates,
            List<PlanPricingAddonResponse> addonPricing
    ) {
    }

    public record PricingValidationResultResponse(
            ValidationState validationState,
            boolean readyToPublish,
            int blockingFindingCount,
            int warningFindingCount,
            List<ValidationMessageResponse> findings,
            int validatedDraftRevision,
            OffsetDateTime validatedAt
    ) {
    }

    public record PricingComparisonEntry(String code, String name, String detail) {
    }

    public record PricingComparisonResponse(
            UUID templateId,
            String templateCode,
            String templateName,
            String leftLabel,
            String rightLabel,
            List<PricingComparisonEntry> subscriptionPricing,
            List<PricingComparisonEntry> meteredRates,
            List<PricingComparisonEntry> addonPricing
    ) {
    }

    public record DraftConfigurationResponse(
            List<DraftCapabilityResponse> capabilities,
            List<DraftModuleResponse> modules,
            List<DraftFeatureResponse> features,
            List<DraftLimitResponse> limits,
            List<DraftAddonResponse> addons,
            PlanPricingSnapshot pricing
    ) {
        public DraftConfigurationResponse(List<DraftCapabilityResponse> capabilities, List<DraftModuleResponse> modules, List<DraftFeatureResponse> features, List<DraftLimitResponse> limits, List<DraftAddonResponse> addons) {
            this(capabilities, modules, features, limits, addons, new PlanPricingSnapshot(null, null, null, null, null, null, null, null, false, List.of(), List.of()));
        }
    }

    public record PlanDraftResponse(
            UUID id,
            UUID templateId,
            int revision,
            DraftStatus status,
            String draftNotes,
            String validationStatus,
            boolean publicationReady,
            PlanValidationResultResponse validation,
            OffsetDateTime updatedAt,
            UUID updatedBy,
            DraftConfigurationResponse configuration,
            List<ValidationMessageResponse> validationMessages
    ) {
    }

    public record CreatePlanTemplateRequest(String code, String name, String description, TargetSegment targetSegment, TemplateStatus status, Integer displayOrder) {
    }

    public record UpdatePlanTemplateRequest(String name, String description, TargetSegment targetSegment, TemplateStatus status, Integer displayOrder) {
    }

    public record SavePlanDraftRequest(
            String draftNotes,
            List<SelectedCapabilityRequest> capabilities,
            List<SelectedModuleRequest> modules,
            List<SelectedFeatureRequest> features,
            List<ConfiguredLimitRequest> limits,
            List<SelectedAddonRequest> addons,
            PlanPricingSnapshot pricing
    ) {
        public SavePlanDraftRequest(String draftNotes, List<SelectedCapabilityRequest> capabilities, List<SelectedModuleRequest> modules, List<SelectedFeatureRequest> features, List<ConfiguredLimitRequest> limits, List<SelectedAddonRequest> addons) {
            this(draftNotes, capabilities, modules, features, limits, addons, new PlanPricingSnapshot(null, null, null, null, null, null, null, null, false, List.of(), List.of()));
        }
    }

    public record SavePlanPricingRequest(PlanPricingSnapshot pricing) {
    }

    public record ValidatePlanDraftResponse(PlanDraftResponse draft, PlanValidationResultResponse validation, List<ValidationMessageResponse> messages, boolean publicationReady) {
    }

    public record PublishPlanVersionRequest(String publicationNotes) {
    }

    public record ClonePlanTemplateRequest(
            UUID sourceTemplateId,
            UUID sourceVersionId,
            String code,
            String name,
            String description,
            TargetSegment targetSegment,
            TemplateStatus status,
            Integer displayOrder
    ) {
    }

    public record TemplateSummaryResponse(
            UUID id,
            String code,
            String name,
            String description,
            TargetSegment targetSegment,
            TemplateStatus status,
            int displayOrder,
            int draftRevision,
            Integer latestPublishedVersionNumber,
            DraftStatus draftStatus,
            boolean publicationReady,
            PlanValidationResultResponse validation,
            int capabilityCount,
            int moduleCount,
            int featureCount,
            int limitCount,
            int addonCount,
            OffsetDateTime updatedAt,
            String changeSummary
    ) {
    }

    public record TemplateDetailResponse(
            UUID id,
            String code,
            String name,
            String description,
            TargetSegment targetSegment,
            TemplateStatus status,
            int displayOrder,
            int draftRevision,
            Integer latestPublishedVersionNumber,
            DraftStatus draftStatus,
            boolean publicationReady,
            PlanValidationResultResponse validation,
            OffsetDateTime updatedAt,
            PlanDraftResponse draft,
            PlanVersionSummaryResponse latestPublishedVersion,
            PlanPricingResponse pricing,
            PricingValidationResultResponse pricingValidation
    ) {
        public TemplateDetailResponse(UUID id, String code, String name, String description, TargetSegment targetSegment, TemplateStatus status, int displayOrder, int draftRevision, Integer latestPublishedVersionNumber, DraftStatus draftStatus, boolean publicationReady, PlanValidationResultResponse validation, OffsetDateTime updatedAt, PlanDraftResponse draft, PlanVersionSummaryResponse latestPublishedVersion) {
            this(id, code, name, description, targetSegment, status, displayOrder, draftRevision, latestPublishedVersionNumber, draftStatus, publicationReady, validation, updatedAt, draft, latestPublishedVersion, new PlanPricingResponse(null, null, null, null, null, null, null, null, null, null, false, PricingStatus.DRAFT, null, null, List.of(), List.of()), null);
        }
    }

    public record PlanVersionSummaryResponse(
            UUID id,
            UUID templateId,
            int versionNumber,
            String versionLabel,
            PublicationStatus status,
            OffsetDateTime publishedAt,
            UUID publishedBy,
            String publicationNotes,
            int sourceDraftRevision,
            String contentHash,
            int capabilityCount,
            int moduleCount,
            int featureCount,
            int limitCount,
            int addonCount,
            String changeSummary
    ) {
    }

    public record PlanVersionDetailResponse(
            UUID id,
            UUID templateId,
            int versionNumber,
            String versionLabel,
            PublicationStatus status,
            OffsetDateTime publishedAt,
            UUID publishedBy,
            String publicationNotes,
            int sourceDraftRevision,
            String contentHash,
            int capabilityCount,
            int moduleCount,
            int featureCount,
            int limitCount,
            int addonCount,
            String snapshotJson,
            PlanPricingResponse pricing
    ) {
        public PlanVersionDetailResponse(UUID id, UUID templateId, int versionNumber, String versionLabel, PublicationStatus status, OffsetDateTime publishedAt, UUID publishedBy, String publicationNotes, int sourceDraftRevision, String contentHash, int capabilityCount, int moduleCount, int featureCount, int limitCount, int addonCount, String snapshotJson) {
            this(id, templateId, versionNumber, versionLabel, status, publishedAt, publishedBy, publicationNotes, sourceDraftRevision, contentHash, capabilityCount, moduleCount, featureCount, limitCount, addonCount, snapshotJson, new PlanPricingResponse(null, null, null, null, null, null, null, null, null, null, false, PricingStatus.DRAFT, null, null, List.of(), List.of()));
        }
    }

    public record ComparisonEntryResponse(String code, String name, String detail) {
    }

    public record ComparisonSectionResponse(List<ComparisonEntryResponse> added, List<ComparisonEntryResponse> removed, List<ComparisonEntryResponse> changed) {
    }

    public record TemplateMetadataComparisonResponse(List<ComparisonEntryResponse> changed) {
    }

    public record CompareVersionsResponse(
            UUID templateId,
            String templateCode,
            String templateName,
            String leftLabel,
            String rightLabel,
            TemplateMetadataComparisonResponse metadata,
            ComparisonSectionResponse capabilities,
            ComparisonSectionResponse modules,
            ComparisonSectionResponse features,
            ComparisonSectionResponse limits,
            ComparisonSectionResponse addons,
            PricingComparisonResponse pricing
    ) {
        public CompareVersionsResponse(UUID templateId, String templateCode, String templateName, String leftLabel, String rightLabel, TemplateMetadataComparisonResponse metadata, ComparisonSectionResponse capabilities, ComparisonSectionResponse modules, ComparisonSectionResponse features, ComparisonSectionResponse limits, ComparisonSectionResponse addons) {
            this(templateId, templateCode, templateName, leftLabel, rightLabel, metadata, capabilities, modules, features, limits, addons, new PricingComparisonResponse(templateId, templateCode, templateName, leftLabel, rightLabel, List.of(), List.of(), List.of()));
        }
    }

    public record SelectedCapabilityRequest(UUID capabilityId) {
    }

    public record SelectedModuleRequest(UUID moduleId, SelectionSource selectionSource, Boolean inherited, Integer displayOrder) {
    }

    public record SelectedFeatureRequest(UUID featureId) {
    }

    public record ConfiguredLimitRequest(UUID limitDefinitionId, String configuredValue) {
    }

    public record SelectedAddonRequest(UUID addonId, SelectionState selectionState) {
    }

    public record CommercialPlatformTemplateStatusResponse(Status status) {
    }
}
