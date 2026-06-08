package com.deepthoughtnet.clinic.ai.careai.task;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiChannel;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationSessionSnapshot;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiSpeaker;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiWorkflowLifecycleService;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiJsonSupport;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEntity;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEventEntity;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEventRepository;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CareAiReceptionistTaskService {
    private static final Set<CareAiReceptionistTaskStatus> ACTIVE_STATUSES = Set.of(
            CareAiReceptionistTaskStatus.OPEN,
            CareAiReceptionistTaskStatus.ASSIGNED,
            CareAiReceptionistTaskStatus.IN_PROGRESS
    );

    private final CareAiReceptionistTaskRepository taskRepository;
    private final CareAiReceptionistTaskEventRepository eventRepository;
    private final CareAiWorkflowLifecycleService workflowLifecycleService;
    private final CareAiConversationPersistenceService conversationPersistenceService;

    public CareAiReceptionistTaskService(
            CareAiReceptionistTaskRepository taskRepository,
            CareAiReceptionistTaskEventRepository eventRepository,
            CareAiWorkflowLifecycleService workflowLifecycleService,
            CareAiConversationPersistenceService conversationPersistenceService
    ) {
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.workflowLifecycleService = workflowLifecycleService;
        this.conversationPersistenceService = conversationPersistenceService;
    }

    @Transactional
    public CareAiReceptionistTaskEntity createHandoffTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskPriority priority
    ) {
        return upsertHandoffTask(command, priority).task();
    }

    @Transactional
    public CareAiReceptionistTaskUpsertResult upsertHandoffTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskPriority priority
    ) {
        return createTask(command, CareAiReceptionistTaskType.HUMAN_HANDOFF, priority);
    }

    @Transactional
    public CareAiReceptionistTaskEntity createCallbackTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskPriority priority
    ) {
        return upsertCallbackTask(command, priority).task();
    }

    @Transactional
    public CareAiReceptionistTaskUpsertResult upsertCallbackTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskPriority priority
    ) {
        return createTask(command, CareAiReceptionistTaskType.CALLBACK_REQUEST, priority);
    }

    @Transactional
    public CareAiReceptionistTaskEntity createAppointmentHandoffTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskPriority priority
    ) {
        return upsertAppointmentHandoffTask(command, priority).task();
    }

    @Transactional
    public CareAiReceptionistTaskUpsertResult upsertAppointmentHandoffTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskPriority priority
    ) {
        return createTask(command, CareAiReceptionistTaskType.APPOINTMENT_HANDOFF, priority);
    }

    @Transactional
    public CareAiReceptionistTaskEntity createEscalationTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskPriority priority
    ) {
        return upsertEscalationTask(command, priority).task();
    }

    @Transactional
    public CareAiReceptionistTaskUpsertResult upsertEscalationTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskPriority priority
    ) {
        return createTask(command, CareAiReceptionistTaskType.ESCALATION, priority);
    }

    @Transactional(readOnly = true)
    public List<CareAiReceptionistTaskEntity> listTasks(UUID tenantId, CareAiReceptionistTaskFilter filter) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dueSoonThreshold = now.plusMinutes(5);
        return taskRepository.findTop200ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(task -> filter == null || filter.status() == null || filter.status().name().equals(task.getStatus()))
                .filter(task -> filter == null || filter.type() == null || filter.type().name().equals(task.getTaskType()))
                .filter(task -> filter == null || filter.priority() == null || filter.priority().name().equals(task.getPriority()))
                .filter(task -> filter == null || filter.assignedUserId() == null || filter.assignedUserId().equals(task.getAssignedUserId()))
                .filter(task -> filter == null || filter.patientId() == null || filter.patientId().equals(task.getPatientId()))
                .filter(task -> filter == null || !Boolean.TRUE.equals(filter.overdueOnly()) || isOverdue(task, now))
                .filter(task -> filter == null || !Boolean.TRUE.equals(filter.dueSoonOnly()) || isDueSoon(task, now, dueSoonThreshold))
                .sorted(Comparator
                        .comparing((CareAiReceptionistTaskEntity task) -> overdueWeight(task, now))
                        .thenComparing(CareAiReceptionistTaskEntity::getDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CareAiReceptionistTaskEntity::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CareAiReceptionistTaskEntity getTask(UUID tenantId, UUID taskId) {
        return taskRepository.findByTenantIdAndId(tenantId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("CareAI receptionist task not found"));
    }

    @Transactional(readOnly = true)
    public List<CareAiReceptionistTaskEventEntity> listEvents(UUID tenantId, UUID taskId) {
        getTask(tenantId, taskId);
        return eventRepository.findByTenantIdAndTaskIdOrderByCreatedAtAsc(tenantId, taskId);
    }

    @Transactional(readOnly = true)
    public CareAiReceptionistTaskResumeContext getResumeContext(UUID tenantId, UUID taskId) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        CareAiConversationSessionSnapshot snapshot = task.getConversationId() == null
                ? null
                : conversationPersistenceService.getConversationSessionSnapshot(tenantId, task.getConversationId(), 50);
        Map<String, Object> workflowContext = snapshot == null || snapshot.workflow() == null
                ? Map.of()
                : CareAiJsonSupport.parseObject(snapshot.workflow().getContextJson());
        String recommendedNextPrompt = recommendedNextPrompt(task, workflowContext);
        return new CareAiReceptionistTaskResumeContext(
                task,
                snapshot == null ? null : snapshot.conversation(),
                snapshot == null ? null : snapshot.workflow(),
                snapshot == null ? List.of() : snapshot.recentMessages(),
                workflowContext,
                recommendedNextPrompt
        );
    }

    @Transactional
    public CareAiReceptionistTaskEntity assignTask(UUID tenantId, UUID taskId, UUID actorUserId) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        assertMutable(task);
        task.assignTo(actorUserId);
        task = taskRepository.save(task);
        appendTaskEvent(tenantId, task.getId(), "TASK_ASSIGNED", actorUserId, json(payload("assignedUserId", actorUserId)));
        appendEscalationWorkflowEvent(task, "ESCALATION_ASSIGNED", actorUserId, payload("assignedUserId", actorUserId));
        return task;
    }

    @Transactional
    public CareAiReceptionistTaskEntity markInProgress(UUID tenantId, UUID taskId, UUID actorUserId) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        assertMutable(task);
        task.markInProgress(actorUserId);
        task = taskRepository.save(task);
        appendTaskEvent(tenantId, task.getId(), "TASK_IN_PROGRESS", actorUserId, json(payload("assignedUserId", task.getAssignedUserId())));
        return task;
    }

    @Transactional
    public CareAiReceptionistTaskEntity resumeTask(UUID tenantId, UUID taskId, UUID actorUserId) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        assertMutable(task);
        task.resumeWithStaff(actorUserId);
        task = taskRepository.save(task);
        appendTaskEvent(tenantId, task.getId(), "STAFF_RESUMED_WORKFLOW", actorUserId, json(taskContextPayload(task)));
        appendWorkflowEvent(task, "STAFF_JOINED_CONVERSATION", payload("actorUserId", actorUserId));
        appendWorkflowEvent(task, "STAFF_RESUMED_WORKFLOW", taskContextPayload(task));
        return task;
    }

    @Transactional
    public CareAiReceptionistTaskEntity addStaffNote(UUID tenantId, UUID taskId, UUID actorUserId, String note) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        assertMutable(task);
        String trimmedNote = blankToNull(note);
        if (!StringUtils.hasText(trimmedNote)) {
            throw new IllegalArgumentException("Staff note is required");
        }
        task.recordStaffNote();
        task = taskRepository.save(task);
        appendTaskEvent(tenantId, task.getId(), "STAFF_NOTE_ADDED", actorUserId, json(payload("note", trimmedNote)));
        if (task.getConversationId() != null) {
            conversationPersistenceService.appendConversationMessage(
                    tenantId,
                    task.getConversationId(),
                    CareAiSpeaker.SYSTEM,
                    parseChannel(task.getChannel()),
                    "Staff note: " + trimmedNote,
                    json(Map.of("actorUserId", actorUserId, "taskId", task.getId(), "messageType", "STAFF_NOTE"))
            );
        }
        return task;
    }

    @Transactional
    public CareAiReceptionistTaskEntity returnToAi(UUID tenantId, UUID taskId, UUID actorUserId) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        assertMutable(task);
        task.returnToAi();
        task = taskRepository.save(task);
        appendTaskEvent(tenantId, task.getId(), "STAFF_RETURNED_TO_AI", actorUserId, json(taskContextPayload(task)));
        appendWorkflowEvent(task, "PATIENT_RETURNED_TO_AI", payload("actorUserId", actorUserId));
        return task;
    }

    @Transactional
    public CareAiReceptionistTaskEntity scheduleCallback(
            UUID tenantId,
            UUID taskId,
            UUID actorUserId,
            String callbackTimePreference,
            OffsetDateTime callbackDueAt
    ) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        assertMutable(task);
        OffsetDateTime dueAt = computeDueAt(CareAiReceptionistTaskType.CALLBACK_REQUEST, parsePriority(task.getPriority()), callbackDueAt);
        task.scheduleCallback(blankToNull(callbackTimePreference), callbackDueAt, dueAt);
        task = taskRepository.save(task);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callbackTimePreference", callbackTimePreference);
        payload.put("callbackDueAt", callbackDueAt);
        appendTaskEvent(tenantId, task.getId(), "CALLBACK_SCHEDULED", actorUserId, json(payload));
        return task;
    }

    @Transactional
    public CareAiReceptionistTaskEntity resolveTask(UUID tenantId, UUID taskId, UUID actorUserId, String resolutionNotes) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        assertMutable(task);
        task.resolve(actorUserId, resolutionNotes);
        task = taskRepository.save(task);
        appendTaskEvent(tenantId, task.getId(), "TASK_RESOLVED", actorUserId, json(payload("resolutionNotes", blankToNull(resolutionNotes))));
        appendEscalationWorkflowEvent(task, "ESCALATION_RESOLVED", actorUserId, payload("resolutionNotes", blankToNull(resolutionNotes)));
        return task;
    }

    @Transactional
    public CareAiReceptionistTaskEntity cancelTask(UUID tenantId, UUID taskId, UUID actorUserId, String resolutionNotes) {
        CareAiReceptionistTaskEntity task = getTask(tenantId, taskId);
        assertMutable(task);
        task.cancel(actorUserId, resolutionNotes);
        task = taskRepository.save(task);
        appendTaskEvent(tenantId, task.getId(), "TASK_CANCELLED", actorUserId, json(payload("resolutionNotes", blankToNull(resolutionNotes))));
        return task;
    }

    @Transactional
    public void appendTaskEvent(UUID tenantId, UUID taskId, String eventType, UUID actorUserId, String payloadJson) {
        eventRepository.save(CareAiReceptionistTaskEventEntity.create(tenantId, taskId, eventType, actorUserId, payloadJson));
    }

    @Transactional
    public List<CareAiReceptionistTaskEntity> evaluateSla(UUID tenantId, Duration dueSoonWindow) {
        List<CareAiReceptionistTaskEntity> tasks = taskRepository.findByTenantIdAndStatusInOrderByCreatedAtAsc(
                tenantId,
                ACTIVE_STATUSES.stream().map(Enum::name).toList()
        );
        OffsetDateTime now = OffsetDateTime.now();
        Duration window = dueSoonWindow == null || dueSoonWindow.isNegative() || dueSoonWindow.isZero()
                ? Duration.ofMinutes(5)
                : dueSoonWindow;
        return tasks.stream()
                .map(task -> updateSla(task, now, window))
                .filter(task -> task != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countActiveTasks(UUID tenantId) {
        return taskRepository.countByTenantIdAndStatusIn(tenantId, ACTIVE_STATUSES.stream().map(Enum::name).toList());
    }

    private CareAiReceptionistTaskUpsertResult createTask(
            CareAiReceptionistTaskCreateCommand command,
            CareAiReceptionistTaskType taskType,
            CareAiReceptionistTaskPriority priority
    ) {
        if (command == null || command.tenantId() == null) {
            throw new IllegalArgumentException("Tenant is required");
        }
        CareAiReceptionistTaskEntity existing = findDuplicate(command, taskType);
        if (existing != null) {
            return new CareAiReceptionistTaskUpsertResult(existing, false);
        }
        CareAiReceptionistTaskEntity created = taskRepository.save(CareAiReceptionistTaskEntity.create(
                command.tenantId(),
                command.conversationId(),
                command.workflowId(),
                command.patientId(),
                command.leadId(),
                command.appointmentId(),
                taskType,
                priority,
                command.channel(),
                blankToNull(command.reason()),
                blankToNull(command.latestUserMessage()),
                blankToNull(command.callbackTimePreference()),
                command.callbackDueAt(),
                computeDueAt(taskType, priority, command.callbackDueAt()),
                command.metadataJson()
        ));
        appendTaskEvent(command.tenantId(), created.getId(), "TASK_CREATED", null, json(taskCreatedPayload(created)));
        appendEscalationWorkflowEvent(created, "ESCALATION_CREATED", null, taskCreatedPayload(created));
        return new CareAiReceptionistTaskUpsertResult(created, true);
    }

    private CareAiReceptionistTaskEntity findDuplicate(CareAiReceptionistTaskCreateCommand command, CareAiReceptionistTaskType taskType) {
        Collection<String> statuses = ACTIVE_STATUSES.stream().map(Enum::name).toList();
        if (command.conversationId() != null) {
            return taskRepository.findTopByTenantIdAndConversationIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(
                    command.tenantId(),
                    command.conversationId(),
                    taskType.name(),
                    statuses
            ).orElse(null);
        }
        if (command.workflowId() != null) {
            return taskRepository.findTopByTenantIdAndWorkflowIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(
                    command.tenantId(),
                    command.workflowId(),
                    taskType.name(),
                    statuses
            ).orElse(null);
        }
        return null;
    }

    private void assertMutable(CareAiReceptionistTaskEntity task) {
        if (CareAiReceptionistTaskStatus.RESOLVED.name().equals(task.getStatus())
                || CareAiReceptionistTaskStatus.CANCELLED.name().equals(task.getStatus())) {
            throw new IllegalStateException("CareAI receptionist task is already closed");
        }
    }

    private Map<String, Object> taskCreatedPayload(CareAiReceptionistTaskEntity task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskType", task.getTaskType());
        payload.put("priority", task.getPriority());
        payload.put("reason", task.getReason());
        payload.put("channel", task.getChannel());
        payload.put("callbackTimePref", task.getCallbackTimePref());
        payload.put("callbackDueAt", task.getCallbackDueAt());
        payload.put("dueAt", task.getDueAt());
        payload.put("slaStatus", task.getSlaStatus());
        payload.put("handlingMode", task.getHandlingMode());
        return payload;
    }

    private Map<String, Object> taskContextPayload(CareAiReceptionistTaskEntity task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("conversationId", task.getConversationId());
        payload.put("workflowId", task.getWorkflowId());
        payload.put("taskStatus", task.getStatus());
        payload.put("handlingMode", task.getHandlingMode());
        return payload;
    }

    private String json(Map<String, Object> payload) {
        return CareAiJsonSupport.writeObject(payload);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, Object> payload(String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(key, value);
        return payload;
    }

    private void appendEscalationWorkflowEvent(
            CareAiReceptionistTaskEntity task,
            String eventType,
            UUID actorUserId,
            Map<String, Object> payload
    ) {
        if (task.getWorkflowId() == null || !isEscalationTrackedTask(task)) {
            return;
        }
        Map<String, Object> workflowPayload = new LinkedHashMap<>(payload);
        workflowPayload.put("taskId", task.getId());
        workflowPayload.put("taskType", task.getTaskType());
        workflowPayload.put("taskStatus", task.getStatus());
        workflowPayload.put("actorUserId", actorUserId);
        workflowLifecycleService.appendWorkflowEvent(
                task.getTenantId(),
                task.getWorkflowId(),
                eventType,
                json(workflowPayload)
        );
    }

    private void appendWorkflowEvent(CareAiReceptionistTaskEntity task, String eventType, Map<String, Object> payload) {
        if (task.getWorkflowId() == null) {
            return;
        }
        Map<String, Object> workflowPayload = new LinkedHashMap<>(payload);
        workflowPayload.put("taskId", task.getId());
        workflowPayload.put("taskType", task.getTaskType());
        workflowPayload.put("handlingMode", task.getHandlingMode());
        workflowLifecycleService.appendWorkflowEvent(
                task.getTenantId(),
                task.getWorkflowId(),
                eventType,
                json(workflowPayload)
        );
    }

    private boolean isEscalationTrackedTask(CareAiReceptionistTaskEntity task) {
        return CareAiReceptionistTaskType.ESCALATION.name().equals(task.getTaskType())
                || CareAiReceptionistTaskType.HUMAN_HANDOFF.name().equals(task.getTaskType())
                || CareAiReceptionistTaskType.APPOINTMENT_HANDOFF.name().equals(task.getTaskType());
    }

    private CareAiReceptionistTaskEntity updateSla(CareAiReceptionistTaskEntity task, OffsetDateTime now, Duration dueSoonWindow) {
        if (task.getDueAt() == null) {
            return null;
        }
        CareAiReceptionistTaskSlaStatus nextStatus = computeSlaStatus(task, now, dueSoonWindow);
        OffsetDateTime breachedAt = nextStatus == CareAiReceptionistTaskSlaStatus.BREACHED
                ? (task.getBreachedAt() == null ? now : task.getBreachedAt())
                : null;
        OffsetDateTime lastNotificationAt = task.getLastNotificationAt();
        boolean shouldMarkNotification = false;
        if (nextStatus == CareAiReceptionistTaskSlaStatus.DUE_SOON
                && !CareAiReceptionistTaskSlaStatus.DUE_SOON.name().equals(task.getSlaStatus())) {
            appendTaskEvent(task.getTenantId(), task.getId(), "SLA_DUE_SOON", null, json(taskContextPayload(task)));
            shouldMarkNotification = true;
        }
        if (nextStatus == CareAiReceptionistTaskSlaStatus.BREACHED
                && !CareAiReceptionistTaskSlaStatus.BREACHED.name().equals(task.getSlaStatus())) {
            appendTaskEvent(task.getTenantId(), task.getId(), "SLA_BREACHED", null, json(taskContextPayload(task)));
            shouldMarkNotification = true;
        }
        if (shouldMarkNotification) {
            lastNotificationAt = now;
        }
        boolean changed = task.updateSlaStatus(nextStatus, breachedAt, lastNotificationAt);
        if (!changed) {
            return null;
        }
        return taskRepository.save(task);
    }

    private CareAiReceptionistTaskSlaStatus computeSlaStatus(
            CareAiReceptionistTaskEntity task,
            OffsetDateTime now,
            Duration dueSoonWindow
    ) {
        OffsetDateTime dueAt = task.getDueAt();
        if (dueAt == null) {
            return CareAiReceptionistTaskSlaStatus.ON_TIME;
        }
        if (!now.isBefore(dueAt.plus(dueSoonWindow))) {
            return CareAiReceptionistTaskSlaStatus.BREACHED;
        }
        if (!now.isBefore(dueAt)) {
            return CareAiReceptionistTaskSlaStatus.OVERDUE;
        }
        if (!now.isBefore(dueAt.minus(dueSoonWindow))) {
            return CareAiReceptionistTaskSlaStatus.DUE_SOON;
        }
        return CareAiReceptionistTaskSlaStatus.ON_TIME;
    }

    private OffsetDateTime computeDueAt(
            CareAiReceptionistTaskType taskType,
            CareAiReceptionistTaskPriority priority,
            OffsetDateTime callbackDueAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        if (callbackDueAt != null) {
            return callbackDueAt;
        }
        if (priority == CareAiReceptionistTaskPriority.URGENT) {
            return now.plusMinutes(5);
        }
        if (taskType == CareAiReceptionistTaskType.ESCALATION) {
            return now.plusMinutes(10);
        }
        if (taskType == CareAiReceptionistTaskType.HUMAN_HANDOFF) {
            return now.plusMinutes(15);
        }
        if (taskType == CareAiReceptionistTaskType.APPOINTMENT_HANDOFF) {
            return now.plusMinutes(15);
        }
        return now.plusHours(4);
    }

    private CareAiReceptionistTaskPriority parsePriority(String value) {
        try {
            return CareAiReceptionistTaskPriority.valueOf(value);
        } catch (Exception ex) {
            return CareAiReceptionistTaskPriority.MEDIUM;
        }
    }

    private CareAiChannel parseChannel(String value) {
        try {
            return CareAiChannel.valueOf(value);
        } catch (Exception ex) {
            return CareAiChannel.UNKNOWN;
        }
    }

    private String recommendedNextPrompt(CareAiReceptionistTaskEntity task, Map<String, Object> workflowContext) {
        String doctor = textValue(workflowContext.get("doctorName"));
        String date = textValue(workflowContext.get("preferredDate"));
        String slot = textValue(workflowContext.get("selectedSlot"));
        String timePreference = textValue(workflowContext.get("preferredTimeWindow"));
        if (StringUtils.hasText(slot)) {
            return "Please confirm whether you want to continue with the selected slot " + slot + ".";
        }
        if (StringUtils.hasText(doctor) && StringUtils.hasText(date) && StringUtils.hasText(timePreference)) {
            return "Continue booking for " + doctor + " on " + date + " and offer " + timePreference + " slots.";
        }
        if (StringUtils.hasText(doctor) && StringUtils.hasText(date)) {
            return "Continue booking for " + doctor + " on " + date + " and ask for the preferred time window.";
        }
        if (StringUtils.hasText(doctor)) {
            return "Continue the booking flow and ask for the appointment date for " + doctor + ".";
        }
        if (task.getTaskType().equals(CareAiReceptionistTaskType.APPOINTMENT_HANDOFF.name())) {
            return "Resume the appointment booking, confirm the remaining booking details, and complete the handoff safely.";
        }
        if (task.getTaskType().equals(CareAiReceptionistTaskType.CALLBACK_REQUEST.name())) {
            return "Confirm the callback window and capture any missing contact details before returning the conversation to CareAI.";
        }
        return "Review the latest patient message, answer the open question, and decide whether to return the conversation to CareAI.";
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private boolean isOverdue(CareAiReceptionistTaskEntity task, OffsetDateTime now) {
        return task.getDueAt() != null && !now.isBefore(task.getDueAt());
    }

    private boolean isDueSoon(CareAiReceptionistTaskEntity task, OffsetDateTime now, OffsetDateTime dueSoonThreshold) {
        return task.getDueAt() != null && task.getDueAt().isAfter(now) && !task.getDueAt().isAfter(dueSoonThreshold);
    }

    private int overdueWeight(CareAiReceptionistTaskEntity task, OffsetDateTime now) {
        if (CareAiReceptionistTaskSlaStatus.BREACHED.name().equals(task.getSlaStatus())) {
            return 0;
        }
        if (isOverdue(task, now)) {
            return 1;
        }
        if (isDueSoon(task, now, now.plusMinutes(5))) {
            return 2;
        }
        return 3;
    }
}
