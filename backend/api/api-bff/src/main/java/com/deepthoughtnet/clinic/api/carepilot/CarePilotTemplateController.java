package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.TemplateDtos.CreateTemplateRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.TemplateDtos.PatchTemplateRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.TemplateDtos.TemplateResponse;
import com.deepthoughtnet.clinic.carepilot.template.service.CampaignTemplateService;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplateCreateCommand;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplatePatchCommand;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplateRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CarePilot template management APIs. */
@RestController
@RequestMapping("/api/carepilot/templates")
public class CarePilotTemplateController {
    private final CampaignTemplateService templateService;

    public CarePilotTemplateController(CampaignTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<TemplateResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return templateService.list(tenantId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public TemplateResponse create(@RequestBody CreateTemplateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(templateService.create(tenantId, new CampaignTemplateCreateCommand(
                request.name(), request.channelType(), request.subjectLine(), request.bodyTemplate(), request.active() != null && request.active()
        )));
    }

    @PatchMapping("/{templateId}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public TemplateResponse patch(@PathVariable UUID templateId, @RequestBody PatchTemplateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(templateService.patch(tenantId, templateId, new CampaignTemplatePatchCommand(
                request.name(), request.subjectLine(), request.bodyTemplate(), request.active()
        )));
    }

    private TemplateResponse toResponse(CampaignTemplateRecord record) {
        return new TemplateResponse(
                record.id(), record.tenantId(), record.name(), record.channelType(), record.subjectLine(),
                record.bodyTemplate(), record.active(), record.createdAt(), record.updatedAt()
        );
    }
}
