package com.deepthoughtnet.clinic.api.careai;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiMessageEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskFilter;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskPriority;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskResumeContext;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskService;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskStatus;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskType;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEntity;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEventEntity;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/careai/receptionist-tasks")
public class CareAiReceptionistTaskController {
    private final CareAiReceptionistTaskService taskService;

    public CareAiReceptionistTaskController(CareAiReceptionistTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.reception.operate','engage.view')")
    public List<CareAiReceptionistTaskResponse> list(
            @RequestParam(required = false) CareAiReceptionistTaskStatus status,
            @RequestParam(required = false) CareAiReceptionistTaskType type,
            @RequestParam(required = false) CareAiReceptionistTaskPriority priority,
            @RequestParam(required = false, defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false, defaultValue = "false") boolean dueSoonOnly,
            @RequestParam(required = false) UUID patientId
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorUserId = assignedToMe ? RequestContextHolder.require().appUserId() : null;
        return taskService.listTasks(tenantId, new CareAiReceptionistTaskFilter(status, type, priority, actorUserId, patientId, overdueOnly, dueSoonOnly)).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/callbacks")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.reception.operate','engage.view')")
    public List<CareAiReceptionistTaskResponse> callbacks(
            @RequestParam(required = false) CareAiReceptionistTaskStatus status,
            @RequestParam(required = false) CareAiReceptionistTaskPriority priority,
            @RequestParam(required = false, defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false, defaultValue = "false") boolean dueSoonOnly,
            @RequestParam(required = false) UUID patientId
    ) {
        return list(status, CareAiReceptionistTaskType.CALLBACK_REQUEST, priority, assignedToMe, overdueOnly, dueSoonOnly, patientId);
    }

    @GetMapping("/escalations")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.reception.operate','engage.view')")
    public List<CareAiReceptionistTaskResponse> escalations(
            @RequestParam(required = false) CareAiReceptionistTaskStatus status,
            @RequestParam(required = false) CareAiReceptionistTaskPriority priority,
            @RequestParam(required = false, defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false, defaultValue = "false") boolean dueSoonOnly,
            @RequestParam(required = false) UUID patientId
    ) {
        return list(status, CareAiReceptionistTaskType.ESCALATION, priority, assignedToMe, overdueOnly, dueSoonOnly, patientId);
    }

    @GetMapping("/handoffs")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.reception.operate','engage.view')")
    public List<CareAiReceptionistTaskResponse> handoffs(
            @RequestParam(required = false) CareAiReceptionistTaskStatus status,
            @RequestParam(required = false) CareAiReceptionistTaskPriority priority,
            @RequestParam(required = false, defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false, defaultValue = "false") boolean dueSoonOnly,
            @RequestParam(required = false) UUID patientId
    ) {
        return list(status, CareAiReceptionistTaskType.HUMAN_HANDOFF, priority, assignedToMe, overdueOnly, dueSoonOnly, patientId);
    }

    @GetMapping("/appointment-handoffs")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.reception.operate','engage.view')")
    public List<CareAiReceptionistTaskResponse> appointmentHandoffs(
            @RequestParam(required = false) CareAiReceptionistTaskStatus status,
            @RequestParam(required = false) CareAiReceptionistTaskPriority priority,
            @RequestParam(required = false, defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false, defaultValue = "false") boolean dueSoonOnly,
            @RequestParam(required = false) UUID patientId
    ) {
        return list(status, CareAiReceptionistTaskType.APPOINTMENT_HANDOFF, priority, assignedToMe, overdueOnly, dueSoonOnly, patientId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public CareAiReceptionistTaskDetailResponse get(@PathVariable UUID id) {
        return toDetailResponse(taskService.getResumeContext(RequestContextHolder.requireTenantId(), id));
    }

    @GetMapping("/{id}/conversation")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public CareAiReceptionistTaskConversationResponse conversation(@PathVariable UUID id) {
        return toConversationResponse(taskService.getResumeContext(RequestContextHolder.requireTenantId(), id));
    }

    @GetMapping("/{id}/resume-context")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public CareAiReceptionistTaskResumeContextResponse resumeContext(@PathVariable UUID id) {
        return toResumeContextResponse(taskService.getResumeContext(RequestContextHolder.requireTenantId(), id));
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public List<CareAiReceptionistTaskEventResponse> events(@PathVariable UUID id) {
        return taskService.listEvents(RequestContextHolder.requireTenantId(), id).stream()
                .map(this::toEventResponse)
                .toList();
    }

    @PostMapping("/{id}/assign-me")
    @PreAuthorize("@permissionChecker.hasPermission('engage.reception.operate')")
    public CareAiReceptionistTaskResponse assignMe(@PathVariable UUID id) {
        return toResponse(taskService.assignTask(
                RequestContextHolder.requireTenantId(),
                id,
                RequestContextHolder.require().appUserId()
        ));
    }

    @PostMapping("/{id}/assignments/claim")
    @PreAuthorize("@permissionChecker.hasPermission('engage.reception.operate')")
    public CareAiReceptionistTaskResponse claimAssignment(@PathVariable UUID id) {
        return assignMe(id);
    }

    @PostMapping("/{id}/in-progress")
    @PreAuthorize("@permissionChecker.hasPermission('engage.reception.operate')")
    public CareAiReceptionistTaskResponse markInProgress(@PathVariable UUID id) {
        return toResponse(taskService.markInProgress(
                RequestContextHolder.requireTenantId(),
                id,
                RequestContextHolder.require().appUserId()
        ));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("@permissionChecker.hasPermission('engage.reception.operate')")
    public CareAiReceptionistTaskResponse resume(@PathVariable UUID id) {
        return toResponse(taskService.resumeTask(
                RequestContextHolder.requireTenantId(),
                id,
                RequestContextHolder.require().appUserId()
        ));
    }

    @PostMapping("/{id}/staff-note")
    @PreAuthorize("@permissionChecker.hasPermission('engage.reception.operate')")
    public CareAiReceptionistTaskResponse addStaffNote(@PathVariable UUID id, @RequestBody StaffNoteRequest request) {
        return toResponse(taskService.addStaffNote(
                RequestContextHolder.requireTenantId(),
                id,
                RequestContextHolder.require().appUserId(),
                request == null ? null : request.note()
        ));
    }

    @PostMapping("/{id}/return-to-ai")
    @PreAuthorize("@permissionChecker.hasPermission('engage.reception.operate')")
    public CareAiReceptionistTaskResponse returnToAi(@PathVariable UUID id) {
        return toResponse(taskService.returnToAi(
                RequestContextHolder.requireTenantId(),
                id,
                RequestContextHolder.require().appUserId()
        ));
    }

    @PostMapping("/{id}/schedule-callback")
    @PreAuthorize("@permissionChecker.hasPermission('engage.reception.operate')")
    public CareAiReceptionistTaskResponse scheduleCallback(@PathVariable UUID id, @RequestBody(required = false) ScheduleCallbackRequest request) {
        return toResponse(taskService.scheduleCallback(
                RequestContextHolder.requireTenantId(),
                id,
                RequestContextHolder.require().appUserId(),
                request == null ? null : blankToNull(request.callbackTimePreference()),
                request == null ? null : request.callbackDueAt()
        ));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public CareAiReceptionistTaskResponse resolve(@PathVariable UUID id, @RequestBody(required = false) TaskMutationRequest request) {
        return toResponse(taskService.resolveTask(
                RequestContextHolder.requireTenantId(),
                id,
                RequestContextHolder.require().appUserId(),
                request == null ? null : blankToNull(request.resolutionNotes())
        ));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public CareAiReceptionistTaskResponse cancel(@PathVariable UUID id, @RequestBody(required = false) TaskMutationRequest request) {
        return toResponse(taskService.cancelTask(
                RequestContextHolder.requireTenantId(),
                id,
                RequestContextHolder.require().appUserId(),
                request == null ? null : blankToNull(request.resolutionNotes())
        ));
    }

    private CareAiReceptionistTaskResponse toResponse(CareAiReceptionistTaskEntity entity) {
        return new CareAiReceptionistTaskResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getConversationId(),
                entity.getWorkflowId(),
                entity.getPatientId(),
                entity.getLeadId(),
                entity.getAppointmentId(),
                entity.getTaskType(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getChannel(),
                entity.getReason(),
                entity.getLatestUserMessage(),
                entity.getCallbackTimePref(),
                entity.getCallbackDueAt(),
                entity.getDueAt(),
                entity.getSlaStatus(),
                entity.getHandlingMode(),
                entity.getAssignedUserId(),
                entity.getAssignedAt(),
                entity.getFirstResponseAt(),
                entity.getBreachedAt(),
                entity.getLastNotificationAt(),
                entity.getLastStaffMessageAt(),
                entity.getResolvedAt(),
                entity.getResolvedByUserId(),
                entity.getResolutionNotes(),
                entity.getMetadataJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CareAiReceptionistTaskDetailResponse toDetailResponse(CareAiReceptionistTaskResumeContext context) {
        return new CareAiReceptionistTaskDetailResponse(
                toResponse(context.task()),
                context.messages().stream().map(this::toMessageResponse).toList(),
                toResumeContextResponse(context)
        );
    }

    private CareAiReceptionistTaskConversationResponse toConversationResponse(CareAiReceptionistTaskResumeContext context) {
        return new CareAiReceptionistTaskConversationResponse(
                toResponse(context.task()),
                context.conversation() == null ? null : toConversationSummary(context.conversation()),
                context.workflow() == null ? null : toWorkflowSummary(context.workflow(), context.workflowContext()),
                context.messages().stream().map(this::toMessageResponse).toList()
        );
    }

    private CareAiReceptionistTaskResumeContextResponse toResumeContextResponse(CareAiReceptionistTaskResumeContext context) {
        return new CareAiReceptionistTaskResumeContextResponse(
                toResponse(context.task()),
                context.conversation() == null ? null : toConversationSummary(context.conversation()),
                context.workflow() == null ? null : toWorkflowSummary(context.workflow(), context.workflowContext()),
                context.recommendedNextPrompt()
        );
    }

    private CareAiReceptionistTaskEventResponse toEventResponse(CareAiReceptionistTaskEventEntity entity) {
        return new CareAiReceptionistTaskEventResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getEventType(),
                entity.getActorUserId(),
                entity.getPayloadJson(),
                entity.getCreatedAt()
        );
    }

    private TaskMessageResponse toMessageResponse(CareAiMessageEntity entity) {
        return new TaskMessageResponse(
                entity.getId(),
                entity.getSpeaker(),
                entity.getChannel(),
                entity.getContent(),
                entity.getCreatedAt()
        );
    }

    private ConversationSummaryResponse toConversationSummary(CareAiConversationEntity entity) {
        return new ConversationSummaryResponse(
                entity.getId(),
                entity.getChannel(),
                entity.getStatus(),
                entity.getPatientId(),
                entity.getLeadId(),
                entity.getAppointmentId(),
                entity.getSummary(),
                entity.getExternalSessionId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private WorkflowSummaryResponse toWorkflowSummary(CareAiWorkflowEntity entity, Map<String, Object> workflowContext) {
        return new WorkflowSummaryResponse(
                entity.getId(),
                entity.getWorkflowType(),
                entity.getState(),
                entity.getLastQuestionKey(),
                entity.getRepeatedQuestionCount(),
                workflowContext
        );
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record TaskMutationRequest(String resolutionNotes) {
    }

    public record StaffNoteRequest(String note) {
    }

    public record ScheduleCallbackRequest(String callbackTimePreference, OffsetDateTime callbackDueAt) {
    }

    public record CareAiReceptionistTaskResponse(
            UUID id,
            UUID tenantId,
            UUID conversationId,
            UUID workflowId,
            UUID patientId,
            UUID leadId,
            UUID appointmentId,
            String taskType,
            String status,
            String priority,
            String channel,
            String reason,
            String latestUserMessage,
            String callbackTimePref,
            OffsetDateTime callbackDueAt,
            OffsetDateTime dueAt,
            String slaStatus,
            String handlingMode,
            UUID assignedUserId,
            OffsetDateTime assignedAt,
            OffsetDateTime firstResponseAt,
            OffsetDateTime breachedAt,
            OffsetDateTime lastNotificationAt,
            OffsetDateTime lastStaffMessageAt,
            OffsetDateTime resolvedAt,
            UUID resolvedByUserId,
            String resolutionNotes,
            String metadataJson,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record CareAiReceptionistTaskDetailResponse(
            CareAiReceptionistTaskResponse task,
            List<TaskMessageResponse> messages,
            CareAiReceptionistTaskResumeContextResponse resumeContext
    ) {
    }

    public record CareAiReceptionistTaskConversationResponse(
            CareAiReceptionistTaskResponse task,
            ConversationSummaryResponse conversation,
            WorkflowSummaryResponse workflow,
            List<TaskMessageResponse> messages
    ) {
    }

    public record CareAiReceptionistTaskResumeContextResponse(
            CareAiReceptionistTaskResponse task,
            ConversationSummaryResponse conversation,
            WorkflowSummaryResponse workflow,
            String recommendedNextPrompt
    ) {
    }

    public record ConversationSummaryResponse(
            UUID id,
            String channel,
            String status,
            UUID patientId,
            UUID leadId,
            UUID appointmentId,
            String summary,
            String externalSessionId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record WorkflowSummaryResponse(
            UUID id,
            String workflowType,
            String state,
            String lastQuestionKey,
            int repeatedQuestionCount,
            Map<String, Object> context
    ) {
    }

    public record CareAiReceptionistTaskEventResponse(
            UUID id,
            UUID taskId,
            String eventType,
            UUID actorUserId,
            String payloadJson,
            OffsetDateTime createdAt
    ) {
    }

    public record TaskMessageResponse(
            UUID id,
            String speaker,
            String channel,
            String content,
            OffsetDateTime createdAt
    ) {
    }
}
