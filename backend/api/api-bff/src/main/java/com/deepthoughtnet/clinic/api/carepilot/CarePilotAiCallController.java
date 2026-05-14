package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallAnalyticsResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallActionRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallCampaignResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallCampaignStatusRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallCampaignUpsertRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallEventResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallExecutionListResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallExecutionResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallManualCallRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallRescheduleRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallSchedulerHealthResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallTranscriptResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallTriggerRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.AiCallDtos.AiCallWebhookRequest;
import com.deepthoughtnet.clinic.carepilot.ai_call.analytics.AiCallAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallCampaignRecord;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionRecord;
import com.deepthoughtnet.clinic.carepilot.ai_call.orchestration.AiCallOrchestrationService;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.AiCallCampaignService;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallCampaignUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallExecutionSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallManualCallCommand;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallTriggerCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CarePilot AI calls campaign/orchestration APIs. */
@RestController
@RequestMapping("/api/carepilot/ai-calls")
public class CarePilotAiCallController {
    private final AiCallCampaignService campaignService;
    private final AiCallOrchestrationService orchestrationService;
    private final AiCallAnalyticsService analyticsService;
    private final CarePilotAiCallSchedulerMonitor schedulerMonitor;

    public CarePilotAiCallController(
            AiCallCampaignService campaignService,
            AiCallOrchestrationService orchestrationService,
            AiCallAnalyticsService analyticsService,
            CarePilotAiCallSchedulerMonitor schedulerMonitor
    ) {
        this.campaignService = campaignService;
        this.orchestrationService = orchestrationService;
        this.analyticsService = analyticsService;
        this.schedulerMonitor = schedulerMonitor;
    }

    @GetMapping("/campaigns")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public java.util.List<AiCallCampaignResponse> listCampaigns() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return campaignService.list(tenantId).stream().map(this::toCampaignResponse).toList();
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallCampaignResponse getCampaign(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toCampaignResponse(campaignService.find(tenantId, id).orElseThrow(() -> new IllegalArgumentException("AI call campaign not found")));
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallCampaignResponse createCampaign(@RequestBody AiCallCampaignUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toCampaignResponse(campaignService.create(tenantId, toUpsertCommand(request), actor));
    }

    @PutMapping("/campaigns/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallCampaignResponse updateCampaign(@PathVariable UUID id, @RequestBody AiCallCampaignUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toCampaignResponse(campaignService.update(tenantId, id, toUpsertCommand(request), actor));
    }

    @PostMapping("/campaigns/{id}/status")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallCampaignResponse updateCampaignStatus(@PathVariable UUID id, @RequestBody AiCallCampaignStatusRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toCampaignResponse(campaignService.updateStatus(tenantId, id, request.status(), actor));
    }

    @PostMapping("/campaigns/{id}/trigger")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public java.util.List<AiCallExecutionResponse> triggerCampaign(@PathVariable UUID id, @RequestBody(required = false) AiCallTriggerRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var targets = request == null || request.targets() == null ? java.util.List.<AiCallTriggerCommand>of() : request.targets().stream()
                .map(r -> new AiCallTriggerCommand(r.patientId(), r.leadId(), r.phoneNumber(), r.script(), r.scheduledAt()))
                .toList();
        return orchestrationService.triggerCampaign(tenantId, id, targets).stream().map(row -> toExecutionResponse(row, null)).toList();
    }

    @PostMapping("/manual-call")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallExecutionResponse manualCall(@RequestBody AiCallManualCallRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        AiCallExecutionRecord row = orchestrationService.triggerManual(tenantId, new AiCallManualCallCommand(
                request.patientId(), request.leadId(), request.phoneNumber(), request.templateId(), request.callType(), request.script(), request.scheduledAt()
        ));
        return toExecutionResponse(row, null);
    }

    @GetMapping("/executions")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallExecutionListResponse executions(
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus status,
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallType callType,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(required = false) Boolean escalationRequired,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        Page<AiCallExecutionRecord> rows = orchestrationService.search(
                tenantId,
                new AiCallExecutionSearchCriteria(status, callType, patientId, leadId, startDate, endDate, escalationRequired, provider, campaignId),
                page,
                size
        );
        return new AiCallExecutionListResponse(
                rows.getNumber(),
                rows.getSize(),
                rows.getTotalElements(),
                rows.getContent().stream().map(row -> toExecutionResponse(row, orchestrationService.transcript(tenantId, row.id()))).toList()
        );
    }

    @GetMapping("/executions/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallExecutionResponse execution(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = orchestrationService.get(tenantId, id);
        return toExecutionResponse(row, orchestrationService.transcript(tenantId, row.id()));
    }

    @GetMapping("/executions/{id}/transcript")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallTranscriptResponse transcript(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = orchestrationService.transcript(tenantId, id);
        if (row == null) {
            throw new IllegalArgumentException("Transcript not found");
        }
        return new AiCallTranscriptResponse(
                row.id(), row.executionId(), row.transcriptText(), row.summary(), row.sentiment(), row.outcome(),
                row.intent(), row.requiresFollowUp(), row.escalationReason(), row.extractedEntitiesJson(), row.createdAt()
        );
    }

    @GetMapping("/executions/{id}/events")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public java.util.List<AiCallEventResponse> events(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return orchestrationService.events(tenantId, id).stream()
                .map(e -> new AiCallEventResponse(
                        e.id(), e.executionId(), e.providerName(), e.providerCallId(), e.eventType(), e.externalStatus(), e.internalStatus(),
                        e.eventTimestamp(), e.rawPayloadRedacted(), e.createdAt()
                ))
                .toList();
    }

    @PostMapping("/executions/{id}/retry")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallExecutionResponse retry(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = orchestrationService.retry(tenantId, id);
        return toExecutionResponse(row, null);
    }

    @PostMapping("/executions/{id}/cancel")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallExecutionResponse cancel(@PathVariable UUID id, @RequestBody(required = false) AiCallActionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = orchestrationService.cancel(tenantId, id, request == null ? null : request.reason());
        return toExecutionResponse(row, null);
    }

    @PostMapping("/executions/{id}/suppress")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallExecutionResponse suppress(@PathVariable UUID id, @RequestBody(required = false) AiCallActionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = orchestrationService.suppress(tenantId, id, request == null ? null : request.reason());
        return toExecutionResponse(row, null);
    }

    @PostMapping("/executions/{id}/reschedule")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallExecutionResponse reschedule(@PathVariable UUID id, @RequestBody AiCallRescheduleRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = orchestrationService.reschedule(tenantId, id, request.scheduledAt(), request.reason());
        return toExecutionResponse(row, null);
    }

    @PostMapping("/executions/dispatch-due")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public java.util.Map<String, Integer> dispatchDue() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var r = orchestrationService.dispatchDueExecutions(tenantId);
        return java.util.Map.of("processed", r.processed(), "dispatched", r.dispatched(), "failed", r.failed(), "skipped", r.skipped());
    }

    @PostMapping("/webhooks/{provider}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public java.util.Map<String, String> webhook(@PathVariable String provider, @RequestBody(required = false) AiCallWebhookRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        orchestrationService.ingestWebhook(tenantId, provider, request == null ? java.util.Map.of() : request.payload());
        return java.util.Map.of("status", "accepted");
    }

    @GetMapping("/scheduler-health")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallSchedulerHealthResponse schedulerHealth() {
        return new AiCallSchedulerHealthResponse(
                schedulerMonitor.enabled(),
                schedulerMonitor.lastRunAt(),
                schedulerMonitor.nextEstimatedRunAt(),
                schedulerMonitor.lastProcessedCount(),
                schedulerMonitor.lastDispatchedCount(),
                schedulerMonitor.lastFailedCount(),
                schedulerMonitor.lastSkippedCount(),
                schedulerMonitor.lastDurationMs()
        );
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public AiCallAnalyticsResponse analyticsSummary() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return AiCallAnalyticsResponse.from(analyticsService.summary(tenantId));
    }

    private AiCallCampaignUpsertCommand toUpsertCommand(AiCallCampaignUpsertRequest request) {
        return new AiCallCampaignUpsertCommand(
                request.name(), request.description(), request.callType(), request.status(), request.templateId(), request.channel(),
                request.retryEnabled(), request.maxAttempts(), request.escalationEnabled()
        );
    }

    private AiCallCampaignResponse toCampaignResponse(AiCallCampaignRecord row) {
        return new AiCallCampaignResponse(
                row.id(), row.tenantId(), row.name(), row.description(), row.callType(), row.status(), row.templateId(), row.channel(),
                row.retryEnabled(), row.maxAttempts(), row.escalationEnabled(), row.createdBy(), row.updatedBy(), row.createdAt(), row.updatedAt()
        );
    }

    private AiCallExecutionResponse toExecutionResponse(
            AiCallExecutionRecord row,
            com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallTranscriptRecord transcript
    ) {
        long durationSeconds = row.startedAt() != null && row.endedAt() != null
                ? java.time.Duration.between(row.startedAt(), row.endedAt()).getSeconds()
                : 0;
        return new AiCallExecutionResponse(
                row.id(), row.tenantId(), row.campaignId(), row.patientId(), row.leadId(), row.phoneNumber(), row.executionStatus(), row.providerName(),
                row.providerCallId(), row.scheduledAt(), row.startedAt(), row.endedAt(), row.retryCount(), row.nextRetryAt(), row.lastAttemptAt(),
                row.failureReason(), row.suppressionReason(), row.escalationRequired(), row.escalationReason(), row.failoverAttempted(), row.failoverReason(),
                row.transcriptId(), row.createdAt(), row.updatedAt(), durationSeconds, transcript == null ? null : transcript.summary()
        );
    }
}
