package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignApprovalHistoryResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.EditAndResubmitCampaignRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignLookupResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignTriggerPreviewResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignReviewRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignRuntimeResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignTriggerResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CreateCampaignRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.UpdateCampaignRequest;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignApprovalHistoryEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.CampaignService;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignCreateCommand;
import com.deepthoughtnet.clinic.api.reliability.service.IdempotencyService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carepilot/campaigns")
public class CarePilotCampaignController {
    private final CampaignService campaignService;
    private final CarePilotCampaignRuntimeService runtimeService;
    private final CarePilotCampaignTriggerService triggerService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public CarePilotCampaignController(
            CampaignService campaignService,
            CarePilotCampaignRuntimeService runtimeService,
            CarePilotCampaignTriggerService triggerService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper
    ) {
        this.campaignService = campaignService;
        this.runtimeService = runtimeService;
        this.triggerService = triggerService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.campaign.view','engage.audit.view')")
    public List<CampaignResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return campaignService.list(tenantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/approval-needed")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.campaign.review','engage.campaign.approve','engage.audit.view')")
    public List<CampaignResponse> listApprovalNeeded() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return campaignService.listPendingApproval(tenantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/lookup")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.lookup')")
    public List<CampaignLookupResponse> lookup(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return campaignService.lookup(tenantId, q, limit).stream()
                .map(record -> new CampaignLookupResponse(record.id(), record.campaignReference(), record.name(), record.campaignType(), record.status(), record.templateId()))
                .toList();
    }

    @GetMapping("/{campaignId}/trigger-preview")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.activate')")
    public CampaignTriggerPreviewResponse triggerPreview(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var preview = triggerService.preview(tenantId, campaignId);
        return new CampaignTriggerPreviewResponse(
                preview.campaignReference(),
                preview.campaignName(),
                preview.status(),
                preview.triggerType(),
                preview.channelType(),
                preview.templateName(),
                preview.templateActive(),
                preview.providerName(),
                preview.providerMode(),
                preview.providerReady(),
                preview.manualDispatcherEnabled(),
                preview.eligibleRecipients(),
                preview.excludedRecipients(),
                preview.missingEmailOrPhoneCount(),
                preview.invalidDestinationCount(),
                preview.consentOrOptOutCount(),
                preview.duplicateRecipientCount(),
                preview.inactivePatientCount(),
                preview.missingRequiredTemplateDataCount(),
                preview.estimatedMessages(),
                preview.estimatedBillableCost(),
                preview.environmentWarning(),
                preview.approvedConfigurationValid(),
                preview.canTrigger(),
                preview.blockingReasons()
        );
    }

    @GetMapping("/{campaignId}")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.campaign.view','engage.audit.view')")
    public CampaignResponse get(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignRecord record = campaignService.find(tenantId, campaignId).orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        return toResponse(record);
    }

    @GetMapping("/{campaignId}/runtime")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.campaign.view','engage.audit.view')")
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
                new com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignRuntimeSummaryResponse(
                        runtime.summary().totalExecutions(),
                        runtime.summary().scheduled(),
                        runtime.summary().sent(),
                        runtime.summary().failed(),
                        runtime.summary().retrying(),
                        runtime.summary().skipped(),
                        runtime.summary().lastSentAt(),
                        runtime.summary().lastFailedAt()
                ),
                runtime.recentExecutions().stream().map(row -> new com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignRuntimeExecutionResponse(
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
                        row.deliveryAttemptCount(),
                        row.retryCount()
                )).toList()
        );
    }

    @GetMapping("/{campaignId}/approval-history")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.campaign.view','engage.campaign.review','engage.campaign.approve','engage.audit.view')")
    public List<CampaignApprovalHistoryResponse> approvalHistory(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return campaignService.history(tenantId, campaignId).stream().map(this::toHistoryResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.manage')")
    public CampaignResponse create(@RequestBody CreateCampaignRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.create(tenantId, new CampaignCreateCommand(
                request.name(), request.campaignType(), request.triggerType(), request.audienceType(), request.templateId(), request.notes()
        ), actorId, actorRole));
    }

    @PatchMapping("/{campaignId}")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.manage')")
    public CampaignResponse update(@PathVariable UUID campaignId, @RequestBody UpdateCampaignRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.update(tenantId, campaignId, new CampaignCreateCommand(
                request.name(), request.campaignType(), request.triggerType(), request.audienceType(), request.templateId(), request.notes()
        ), actorId, actorRole));
    }

    @PostMapping("/{campaignId}/edit-and-resubmit")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.submit')")
    public CampaignResponse editAndResubmit(@PathVariable UUID campaignId, @RequestBody EditAndResubmitCampaignRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.editAndResubmit(tenantId, campaignId, new CampaignCreateCommand(
                request.name(), request.campaignType(), request.triggerType(), request.audienceType(), request.templateId(), request.notes()
        ), actorId, actorRole, request.expectedVersion(), request.resolutionNote()));
    }

    @PostMapping("/{campaignId}/submit")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.submit')")
    public CampaignResponse submit(@PathVariable UUID campaignId, @RequestBody(required = false) CampaignReviewRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        Integer expectedVersion = request == null ? null : request.expectedVersion();
        String resolutionNote = request == null ? null : request.comment();
        return toResponse(campaignService.submitForApproval(tenantId, campaignId, actorId, actorRole, expectedVersion, resolutionNote));
    }

    @PostMapping("/{campaignId}/withdraw")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.submit')")
    public CampaignResponse withdraw(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.withdrawSubmission(tenantId, campaignId, actorId, actorRole));
    }

    @PostMapping("/{campaignId}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.approve')")
    public CampaignResponse approve(@PathVariable UUID campaignId, @RequestBody(required = false) CampaignReviewRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.approve(tenantId, campaignId, actorId, actorRole, request == null ? null : request.comment(), request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/{campaignId}/request-changes")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.review')")
    public CampaignResponse requestChanges(@PathVariable UUID campaignId, @RequestBody CampaignReviewRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.requestChanges(tenantId, campaignId, actorId, actorRole, request.comment(), request.expectedVersion()));
    }

    @PatchMapping("/{campaignId}/activate")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.activate')")
    public CampaignResponse activate(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.activate(tenantId, campaignId, actorId, actorRole));
    }

    @PatchMapping("/{campaignId}/pause")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.activate')")
    public CampaignResponse pause(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.pause(tenantId, campaignId, actorId, actorRole));
    }

    @PatchMapping("/{campaignId}/deactivate")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.activate')")
    public CampaignResponse deactivate(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.deactivate(tenantId, campaignId, actorId, actorRole));
    }

    @PatchMapping("/{campaignId}/resume")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.activate')")
    public CampaignResponse resume(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.resume(tenantId, campaignId, actorId, actorRole));
    }

    @PatchMapping("/{campaignId}/cancel")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.activate')")
    public CampaignResponse cancel(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.cancel(tenantId, campaignId, actorId, actorRole));
    }

    @PatchMapping("/{campaignId}/complete")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.activate')")
    public CampaignResponse complete(@PathVariable UUID campaignId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String actorRole = RequestContextHolder.require().tenantRole();
        return toResponse(campaignService.complete(tenantId, campaignId, actorId, actorRole));
    }

    @PostMapping("/{campaignId}/trigger")
    @PreAuthorize("@permissionChecker.hasPermission('engage.campaign.activate')")
    public CampaignTriggerResponse trigger(
            @PathVariable UUID campaignId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body == null ? Map.of() : body);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize trigger request", ex);
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyService.findCachedResponse(tenantId, idempotencyKey, requestBody);
            if (cached.isPresent()) {
                try {
                    return objectMapper.readValue(cached.get(), CampaignTriggerResponse.class);
                } catch (Exception ex) {
                    throw new IllegalStateException("Unable to deserialize cached trigger response", ex);
                }
            }
        }

        var result = triggerService.trigger(tenantId, campaignId);
        CampaignTriggerResponse response = new CampaignTriggerResponse(
                result.campaignReference(),
                result.executionReference(),
                result.campaignName(),
                result.audienceType(),
                result.channelType().name(),
                result.status(),
                result.queued(),
                result.eligibleRecipients(),
                result.queuedExecutions(),
                result.skippedRecipients(),
                result.message(),
                result.queuedAt()
        );

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyService.storeResponse(tenantId, idempotencyKey, requestBody, objectMapper.writeValueAsString(response));
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to store idempotent trigger response", ex);
            }
        }

        return response;
    }

    private CampaignResponse toResponse(com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignRecord record) {
        return new CampaignResponse(
                record.id(),
                record.campaignReference(),
                record.tenantId(),
                record.name(),
                record.campaignType(),
                record.status(),
                record.triggerType(),
                record.audienceType(),
                record.templateId(),
                record.active(),
                record.notes(),
                record.createdBy(),
                record.submittedBy(),
                record.submittedByDisplayName(),
                record.submittedByRoleLabel(),
                record.submittedByEmployeeCode(),
                record.submittedByUsername(),
                record.submittedAt(),
                record.reviewedBy(),
                record.reviewedByDisplayName(),
                record.reviewedByRoleLabel(),
                record.reviewedByEmployeeCode(),
                record.reviewedByUsername(),
                record.reviewedAt(),
                record.reviewComment(),
                record.approvedBy(),
                record.approvedByDisplayName(),
                record.approvedByRoleLabel(),
                record.approvedByEmployeeCode(),
                record.approvedByUsername(),
                record.approvedAt(),
                record.activationBy(),
                record.activationByDisplayName(),
                record.activationByRoleLabel(),
                record.activationByEmployeeCode(),
                record.activationByUsername(),
                record.activationAt(),
                record.approvalInvalidatedReason(),
                record.approvedVersion(),
                record.approvedConfigurationHash(),
                record.version(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private CampaignApprovalHistoryResponse toHistoryResponse(CampaignApprovalHistoryEntity record) {
        return new CampaignApprovalHistoryResponse(
                record.getId(),
                record.getCampaignId(),
                record.getEventType(),
                record.getFromStatus(),
                record.getToStatus(),
                record.getActorId(),
                record.getActorRole(),
                record.getActorDisplayName(),
                record.getActorRoleLabel(),
                record.getActorEmployeeCode(),
                record.getActorUsername(),
                record.getComment(),
                record.getInvalidationReason(),
                record.getResolutionNote(),
                record.getPreviousCampaignVersion(),
                record.getCampaignVersion(),
                record.getNewCampaignVersion(),
                record.getPreviousConfigurationHash(),
                record.getConfigurationHash(),
                record.getNewConfigurationHash(),
                record.getCreatedAt()
        );
    }
}
