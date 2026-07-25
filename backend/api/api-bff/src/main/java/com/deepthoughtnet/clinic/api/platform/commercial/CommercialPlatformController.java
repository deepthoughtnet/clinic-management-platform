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
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/commercial")
@PreAuthorize("@permissionChecker.hasPermission('commercial.view')")
public class CommercialPlatformController {
    private final CommercialPlatformApiService service;

    public CommercialPlatformController(CommercialPlatformApiService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        return service.getOverview();
    }

    @GetMapping("/plan-templates")
    public PageResponse<TemplateSummaryResponse> listTemplates(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TemplateStatus status,
            @RequestParam(required = false) TargetSegment targetSegment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listTemplates(search, status, targetSegment, page, size);
    }

    @GetMapping("/plan-templates/{templateId}")
    public TemplateDetailResponse getTemplate(@PathVariable UUID templateId) {
        return service.getTemplate(templateId);
    }

    @PostMapping("/plan-templates")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.plans.manage')")
    public TemplateDetailResponse createTemplate(@RequestBody CreatePlanTemplateRequest request) {
        return service.createTemplate(request);
    }

    @PostMapping("/plan-templates/{sourceTemplateId}/clone")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.plans.manage')")
    public TemplateDetailResponse cloneTemplate(@PathVariable UUID sourceTemplateId, @RequestBody ClonePlanTemplateRequest request) {
        return service.cloneTemplate(sourceTemplateId, request);
    }

    @PutMapping("/plan-templates/{templateId}")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.plans.manage')")
    public TemplateDetailResponse updateTemplate(@PathVariable UUID templateId, @RequestBody UpdatePlanTemplateRequest request) {
        return service.updateTemplate(templateId, request);
    }

    @PostMapping("/plan-templates/{templateId}/retire")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.plans.manage')")
    public TemplateDetailResponse retireTemplate(@PathVariable UUID templateId) {
        return service.retireTemplate(templateId);
    }

    @GetMapping("/plan-templates/{templateId}/draft")
    public PlanDraftResponse getDraft(@PathVariable UUID templateId) {
        return service.getDraft(templateId);
    }

    @PutMapping("/plan-templates/{templateId}/draft")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.plans.manage')")
    public PlanDraftResponse saveDraft(@PathVariable UUID templateId, @RequestBody SavePlanDraftRequest request) {
        return service.saveDraft(templateId, request);
    }

    @PostMapping("/plan-templates/{templateId}/draft/validate")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.plans.manage')")
    public ValidatePlanDraftResponse validateDraft(@PathVariable UUID templateId) {
        return service.validateDraft(templateId);
    }

    @PostMapping("/plan-templates/{templateId}/versions")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.plans.publish')")
    public PlanVersionDetailResponse publishVersion(@PathVariable UUID templateId, @RequestBody(required = false) PublishPlanVersionRequest request) {
        return service.publishVersion(templateId, request);
    }

    @GetMapping("/plan-templates/{templateId}/versions")
    public PageResponse<PlanVersionSummaryResponse> listVersions(@PathVariable UUID templateId) {
        return service.listVersions(templateId);
    }

    @GetMapping("/plan-templates/{templateId}/versions/{versionId}")
    public PlanVersionDetailResponse getVersion(@PathVariable UUID templateId, @PathVariable UUID versionId) {
        return service.getVersion(templateId, versionId);
    }

    @GetMapping("/plan-templates/{templateId}/compare")
    public CompareVersionsResponse compareVersions(
            @PathVariable UUID templateId,
            @RequestParam(required = false) UUID leftVersionId,
            @RequestParam(required = false) UUID rightVersionId
    ) {
        return service.compareVersions(templateId, leftVersionId, rightVersionId);
    }
}
