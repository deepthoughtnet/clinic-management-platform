package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignRuntimeExecutionResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignRuntimeResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignRuntimeSummaryResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CreateCampaignRequest;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.CampaignService;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignCreateCommand;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CarePilot campaign APIs for tenant admins. */
@RestController
@RequestMapping("/api/carepilot/campaigns")
public class CarePilotCampaignController {
    private final CampaignService campaignService;
    private final CarePilotCampaignRuntimeService runtimeService;

    public CarePilotCampaignController(CampaignService campaignService, CarePilotCampaignRuntimeService runtimeService) {
        this.campaignService = campaignService;
        this.runtimeService = runtimeService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<CampaignResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return campaignService.list(tenantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{campaignId}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public CampaignResponse get(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        CampaignRecord record = campaignService.find(tenantId, campaignId).orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        return toResponse(record);
    }

    @GetMapping("/{campaignId}/runtime")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public CampaignRuntimeResponse runtime(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var runtime = runtimeService.runtime(tenantId, campaignId);
        return new CampaignRuntimeResponse(
                runtime.campaignId(),
                runtime.campaignName(),
                runtime.active(),
                TriggerType.valueOf(runtime.triggerType()),
                CampaignType.valueOf(runtime.campaignType()),
                runtime.nextExpectedExecutionAt(),
                runtime.schedulerStatus(),
                runtime.lastSchedulerScanAt(),
                new CampaignRuntimeSummaryResponse(
                        runtime.summary().totalExecutions(),
                        runtime.summary().scheduled(),
                        runtime.summary().sent(),
                        runtime.summary().failed(),
                        runtime.summary().retrying(),
                        runtime.summary().skipped(),
                        runtime.summary().lastSentAt(),
                        runtime.summary().lastFailedAt()
                ),
                runtime.recentExecutions().stream().map(row -> new CampaignRuntimeExecutionResponse(
                        row.executionId(),
                        row.recipientPatientId(),
                        row.recipientPatientName(),
                        row.recipientEmail(),
                        row.recipientPhone(),
                        row.relatedEntityType(),
                        row.relatedEntityId(),
                        row.relatedEntityLabel(),
                        row.doctorName(),
                        row.reminderWindow(),
                        row.createdAt(),
                        row.scheduledAt(),
                        row.attemptedAt(),
                        row.sentAt(),
                        row.failedAt(),
                        row.nextRetryAt(),
                        row.channel(),
                        row.providerName(),
                        row.providerMessageId(),
                        row.status(),
                        row.failureReason(),
                        row.retryCount()
                )).toList()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public CampaignResponse create(@RequestBody CreateCampaignRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        return toResponse(campaignService.create(tenantId, new CampaignCreateCommand(
                request.name(), request.campaignType(), request.triggerType(), request.audienceType(), request.templateId(), request.notes()
        ), actorId));
    }

    @PatchMapping("/{campaignId}/activate")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public CampaignResponse activate(@PathVariable UUID campaignId) {
        return toResponse(campaignService.activate(RequestContextHolder.requireTenantId(), campaignId));
    }

    @PatchMapping("/{campaignId}/deactivate")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public CampaignResponse deactivate(@PathVariable UUID campaignId) {
        return toResponse(campaignService.deactivate(RequestContextHolder.requireTenantId(), campaignId));
    }

    private CampaignResponse toResponse(CampaignRecord record) {
        return new CampaignResponse(
                record.id(), record.tenantId(), record.name(), record.campaignType(), record.status(), record.triggerType(),
                record.audienceType(), record.templateId(), record.active(), record.notes(), record.createdBy(), record.createdAt(), record.updatedAt()
        );
    }
}
