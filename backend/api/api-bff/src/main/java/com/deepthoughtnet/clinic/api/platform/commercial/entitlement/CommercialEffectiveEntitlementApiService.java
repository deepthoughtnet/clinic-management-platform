package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.ComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.CreateOverrideRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.EffectiveEntitlementSnapshotResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.EffectiveLimitResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.LegacyComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideHistoryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideImpactPreviewResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.PageResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffDashboardResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffTenantResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.UpdateOverrideRequest;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.GenerationReason;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CommercialEffectiveEntitlementApiService {
    private final CommercialEffectiveEntitlementService delegate;
    private final ObjectMapper objectMapper;

    public CommercialEffectiveEntitlementApiService(CommercialEffectiveEntitlementService delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    public EffectiveEntitlementSnapshotResponse getCurrentSnapshot(UUID tenantId) {
        return map(delegate.getCurrentSnapshot(tenantId), EffectiveEntitlementSnapshotResponse.class);
    }

    public EffectiveEntitlementSnapshotResponse regenerate(UUID tenantId, GenerationReason reason) {
        return map(delegate.regenerateCurrentSnapshot(tenantId, reason), EffectiveEntitlementSnapshotResponse.class);
    }

    public PageResponse<EffectiveEntitlementSnapshotResponse> history(UUID tenantId, int page, int size) {
        var result = delegate.getSnapshotHistory(tenantId, page, size);
        return new PageResponse<>(result.items().stream().map(item -> map(item, EffectiveEntitlementSnapshotResponse.class)).toList(), result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    public LegacyComparisonResponse compareWithLegacy(UUID tenantId, Map<String, Boolean> legacyModules) {
        return map(delegate.compareWithLegacy(tenantId, legacyModules), LegacyComparisonResponse.class);
    }

    public List<OverrideResponse> listOverrides(UUID tenantId) {
        return delegate.listOverrides(tenantId).stream().map(item -> map(item, OverrideResponse.class)).toList();
    }

    public OverrideResponse createOverride(UUID tenantId, CreateOverrideRequest request) {
        return map(delegate.createOverride(tenantId, map(request, CommercialEffectiveEntitlementModels.CreateOverrideRequest.class)), OverrideResponse.class);
    }

    public OverrideResponse updateOverride(UUID tenantId, UUID overrideId, UpdateOverrideRequest request) {
        return map(delegate.updateOverride(tenantId, overrideId, map(request, CommercialEffectiveEntitlementModels.UpdateOverrideRequest.class)), OverrideResponse.class);
    }

    public OverrideResponse activateOverride(UUID tenantId, UUID overrideId) {
        return map(delegate.activateOverride(tenantId, overrideId), OverrideResponse.class);
    }

    public OverrideResponse cancelOverride(UUID tenantId, UUID overrideId) {
        return map(delegate.cancelOverride(tenantId, overrideId), OverrideResponse.class);
    }

    public OverrideResponse submitOverride(UUID tenantId, UUID overrideId) {
        return map(delegate.submitOverride(tenantId, overrideId), OverrideResponse.class);
    }

    public OverrideResponse withdrawOverride(UUID tenantId, UUID overrideId) {
        return map(delegate.withdrawOverride(tenantId, overrideId), OverrideResponse.class);
    }

    public OverrideResponse approveOverride(UUID tenantId, UUID overrideId, String remarks) {
        return map(delegate.approveOverride(tenantId, overrideId, remarks), OverrideResponse.class);
    }

    public OverrideResponse requestChanges(UUID tenantId, UUID overrideId, String remarks) {
        return map(delegate.requestChanges(tenantId, overrideId, remarks), OverrideResponse.class);
    }

    public OverrideResponse rollbackOverride(UUID tenantId, UUID overrideId, String reason) {
        return map(delegate.rollbackOverride(tenantId, overrideId, reason), OverrideResponse.class);
    }

    public List<OverrideHistoryResponse> getOverrideHistory(UUID tenantId, UUID overrideId) {
        return delegate.getOverrideHistory(tenantId, overrideId).stream().map(item -> map(item, OverrideHistoryResponse.class)).toList();
    }

    public OverrideImpactPreviewResponse previewOverride(UUID tenantId, CreateOverrideRequest request) {
        return map(delegate.previewOverride(tenantId, map(request, CommercialEffectiveEntitlementModels.CreateOverrideRequest.class)), OverrideImpactPreviewResponse.class);
    }

    public EffectiveLimitResponse getLimit(UUID tenantId, String limitCode) {
        return map(delegate.getLimit(tenantId, limitCode), EffectiveLimitResponse.class);
    }

    public RuntimeDiffSummaryResponse getRuntimeDiffSummary() {
        return map(delegate.getRuntimeDiffSummary(), RuntimeDiffSummaryResponse.class);
    }

    private <S, T> T map(S source, Class<T> targetClass) {
        return objectMapper.convertValue(source, targetClass);
    }
}
