package com.deepthoughtnet.clinic.api.platform.commercial;

import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.CompareVersionsResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.ClonePlanTemplateRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.CreatePlanTemplateRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PageResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PlanDraftResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PlanVersionDetailResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PlanVersionSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PublishPlanVersionRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.SavePlanDraftRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.TemplateDetailResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.TemplateSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.UpdatePlanTemplateRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.ValidatePlanDraftResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.OverviewResponse;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TargetSegment;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TemplateStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CommercialPlatformApiService {
    private final CommercialPlatformService delegate;
    private final ObjectMapper objectMapper;

    public CommercialPlatformApiService(CommercialPlatformService delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    public OverviewResponse getOverview() {
        return map(delegate.getOverview(), OverviewResponse.class);
    }

    public PageResponse<TemplateSummaryResponse> listTemplates(String search, TemplateStatus status, TargetSegment targetSegment, int page, int size) {
        return mapPage(delegate.listTemplates(search, status, targetSegment, page, size), TemplateSummaryResponse.class);
    }

    public TemplateDetailResponse getTemplate(UUID templateId) {
        return map(delegate.getTemplate(templateId), TemplateDetailResponse.class);
    }

    public TemplateDetailResponse createTemplate(CreatePlanTemplateRequest request) {
        return map(delegate.createTemplate(map(request, com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.CreatePlanTemplateRequest.class)), TemplateDetailResponse.class);
    }

    public TemplateDetailResponse cloneTemplate(UUID sourceTemplateId, ClonePlanTemplateRequest request) {
        return map(delegate.cloneTemplate(sourceTemplateId, map(request, com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.ClonePlanTemplateRequest.class)), TemplateDetailResponse.class);
    }

    public TemplateDetailResponse updateTemplate(UUID templateId, UpdatePlanTemplateRequest request) {
        return map(delegate.updateTemplate(templateId, map(request, com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.UpdatePlanTemplateRequest.class)), TemplateDetailResponse.class);
    }

    public TemplateDetailResponse retireTemplate(UUID templateId) {
        return map(delegate.retireTemplate(templateId), TemplateDetailResponse.class);
    }

    public PlanDraftResponse getDraft(UUID templateId) {
        return map(delegate.getDraft(templateId), PlanDraftResponse.class);
    }

    public PlanDraftResponse saveDraft(UUID templateId, SavePlanDraftRequest request) {
        return map(delegate.saveDraft(templateId, map(request, com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.SavePlanDraftRequest.class)), PlanDraftResponse.class);
    }

    public ValidatePlanDraftResponse validateDraft(UUID templateId) {
        return map(delegate.validateDraft(templateId), ValidatePlanDraftResponse.class);
    }

    public PlanVersionDetailResponse publishVersion(UUID templateId, PublishPlanVersionRequest request) {
        return map(delegate.publishVersion(templateId, map(request, com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PublishPlanVersionRequest.class)), PlanVersionDetailResponse.class);
    }

    public PageResponse<PlanVersionSummaryResponse> listVersions(UUID templateId) {
        return mapPage(delegate.listVersions(templateId), PlanVersionSummaryResponse.class);
    }

    public PlanVersionDetailResponse getVersion(UUID templateId, UUID versionId) {
        return map(delegate.getVersion(templateId, versionId), PlanVersionDetailResponse.class);
    }

    public CompareVersionsResponse compareVersions(UUID templateId, UUID leftVersionId, UUID rightVersionId) {
        return map(delegate.compareVersions(templateId, leftVersionId, rightVersionId), CompareVersionsResponse.class);
    }

    private <S, T> T map(S source, Class<T> targetClass) {
        return objectMapper.convertValue(source, targetClass);
    }

    private <S, T> PageResponse<T> mapPage(com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformModels.PageResponse<S> source, Class<T> targetClass) {
        List<T> items = source.items().stream().map(item -> objectMapper.convertValue(item, targetClass)).toList();
        return new PageResponse<>(items, source.page(), source.size(), source.totalElements(), source.totalPages());
    }
}
