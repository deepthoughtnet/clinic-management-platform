package com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CommercialEntitlementDtos {
    private CommercialEntitlementDtos() {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    }

    public record ValidationFinding(String code, String title, String message, boolean blocking, String remediation, String affectedCode, String source) {
    }

    public record CapabilityResponse(String code, String name, boolean enabled, String source, String reason) {
    }

    public record ModuleResponse(String code, String name, String runtimeModuleCode, boolean enabled, String source, String reason, String relatedCapabilityCode) {
    }

    public record FeatureResponse(String code, String name, String runtimeFeatureKey, String parentModuleCode, boolean enabled, String source, String reason) {
    }

    public record LimitResponse(String code, String name, String valueType, String configuredValue, boolean unlimited, String unit, String period, String enforcementType, String source, String overrideSource) {
    }

    public record AddOnContributionResponse(String code, String name, String state, String source, List<String> appliedContributions) {
    }

    public record OverrideResponse(
            UUID id,
            String targetType,
            String targetCode,
            String operation,
            String value,
            String addOnState,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String status,
            String reason,
            OffsetDateTime submittedAt,
            UUID submittedBy,
            OffsetDateTime reviewedAt,
            UUID reviewedBy,
            String reviewRemarks,
            OffsetDateTime createdAt,
            UUID createdBy,
            OffsetDateTime updatedAt,
            UUID updatedBy,
            long version
    ) {
    }

    public record OverrideHistoryResponse(
            UUID overrideId,
            String revision,
            String previousStatus,
            String newStatus,
            String action,
            OffsetDateTime changedAt,
            String changedBy,
            String remarks,
            String snapshotHash
    ) {
    }

    public record ProvenanceResponse(String itemType, String code, String source, String reason, String details) {
    }

    public record EffectiveEntitlementSnapshotResponse(
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
    }

    public record ComparisonResponse(String code, String label, String category, String legacyValue, String commercialValue, String detail) {
    }

    public record LegacyComparisonResponse(UUID tenantId, List<ComparisonResponse> modules, List<ComparisonResponse> features, List<ComparisonResponse> limits) {
    }

    public record CreateOverrideRequest(
            String targetType,
            String targetCode,
            String operation,
            String value,
            String addOnState,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason,
            String internalNotes,
            UUID subscriptionId
    ) {
    }

    public record UpdateOverrideRequest(
            String targetType,
            String targetCode,
            String operation,
            String value,
            String addOnState,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String reason,
            String internalNotes,
            UUID subscriptionId
    ) {
    }

    public record EffectiveLimitResponse(String code, String name, String configuredValue, boolean unlimited, String unit, String period, String enforcementType, UUID snapshotId, OffsetDateTime generatedAt, String source) {
    }

    public record OverrideImpactPreviewResponse(
            UUID tenantId,
            UUID subscriptionId,
            String targetType,
            String targetCode,
            String operation,
            String beforeValue,
            String afterValue,
            String source,
            String runtimeImpact,
            List<String> dependentEffects,
            List<ValidationFinding> findings
    ) {
    }

    public record RuntimeDiffSummaryResponse(
            long tenantsWithActiveCommercialSubscriptions,
            long tenantsWithCurrentValidSnapshots,
            long missingSnapshots,
            long invalidSnapshots,
            long exactMatches,
            long tenantsWithDifferences,
            long legacyOnlyEntitlements,
            long commercialOnlyEntitlements,
            long activeOverrides,
            long snapshotGenerationFailures,
            boolean commercialRuntimeEnabled,
            boolean shadowComparisonEnabled,
            long allowlistedTenants
    ) {
    }

    public record RuntimeDiffTenantResponse(
            UUID tenantId,
            String tenantName,
            String tenantCode,
            String currentSubscription,
            String publishedVersion,
            String snapshotStatus,
            OffsetDateTime generatedAt,
            String comparisonStatus,
            long moduleDifferences,
            long featureDifferences,
            long limitDifferences,
            long activeOverrides,
            String runtimeSource,
            String rolloutReadiness,
            String recommendation,
            List<String> differences
    ) {
    }

    public record RuntimeDiffDashboardResponse(RuntimeDiffSummaryResponse summary, List<RuntimeDiffTenantResponse> tenants) {
    }
}
