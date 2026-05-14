package com.deepthoughtnet.clinic.api.admin;

import com.deepthoughtnet.clinic.api.admin.dto.AdminTemplateDtos.PreviewRequest;
import com.deepthoughtnet.clinic.api.admin.dto.AdminTemplateDtos.PreviewResponse;
import com.deepthoughtnet.clinic.api.admin.dto.AdminTemplateDtos.TemplateResponse;
import com.deepthoughtnet.clinic.api.admin.dto.AdminTemplateDtos.UpsertTemplateRequest;
import com.deepthoughtnet.clinic.carepilot.template.service.CareTemplateService;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CareTemplateRecord;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CareTemplateSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CareTemplateUpsertCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration APIs for centralized reusable template management.
 */
@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplateController {
    private final CareTemplateService templateService;

    public AdminTemplateController(CareTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<TemplateResponse> list(
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return templateService.list(tenantId, new CareTemplateSearchCriteria(
                parseType(templateType),
                parseChannel(channel),
                parseCategory(category),
                active,
                search
        )).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public TemplateResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(templateService.get(tenantId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public TemplateResponse create(@RequestBody UpsertTemplateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(templateService.create(tenantId, toCommand(request), actorAppUserId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public TemplateResponse update(@PathVariable UUID id, @RequestBody UpsertTemplateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(templateService.update(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public TemplateResponse activate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(templateService.activate(tenantId, id, actorAppUserId));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public TemplateResponse deactivate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(templateService.deactivate(tenantId, id, actorAppUserId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public void delete(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        templateService.delete(tenantId, id);
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public PreviewResponse preview(@PathVariable UUID id, @RequestBody(required = false) PreviewRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var rendered = templateService.preview(tenantId, id, request == null ? null : request.variables());
        return new PreviewResponse(rendered.renderedSubject(), rendered.renderedBody());
    }

    private CareTemplateUpsertCommand toCommand(UpsertTemplateRequest request) {
        return new CareTemplateUpsertCommand(
                request.name(),
                request.description(),
                request.templateType(),
                request.channel(),
                request.category(),
                request.subject(),
                request.body(),
                request.variablesJson(),
                request.active()
        );
    }

    private TemplateResponse toResponse(CareTemplateRecord record) {
        return new TemplateResponse(
                record.id(),
                record.tenantId(),
                record.name(),
                record.description(),
                record.templateType(),
                record.channel(),
                record.category(),
                record.subject(),
                record.body(),
                record.variablesJson(),
                record.active(),
                record.systemTemplate(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private com.deepthoughtnet.clinic.carepilot.template.model.TemplateType parseType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return com.deepthoughtnet.clinic.carepilot.template.model.TemplateType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid templateType");
        }
    }

    private com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel parseChannel(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid channel");
        }
    }

    private com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory parseCategory(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid category");
        }
    }
}
