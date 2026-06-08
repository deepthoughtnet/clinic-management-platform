package com.deepthoughtnet.clinic.ai.careai.task.db;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiJsonSupport;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskHandlingMode;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskPriority;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskSlaStatus;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskStatus;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "careai_receptionist_tasks", indexes = {
        @Index(name = "ix_careai_receptionist_tasks_tenant_status_priority_created", columnList = "tenant_id,status,priority,created_at"),
        @Index(name = "ix_careai_receptionist_tasks_tenant_type_status", columnList = "tenant_id,task_type,status"),
        @Index(name = "ix_careai_receptionist_tasks_tenant_assigned_status", columnList = "tenant_id,assigned_user_id,status"),
        @Index(name = "ix_careai_receptionist_tasks_tenant_patient", columnList = "tenant_id,patient_id"),
        @Index(name = "ix_careai_receptionist_tasks_tenant_conversation", columnList = "tenant_id,conversation_id")
})
public class CareAiReceptionistTaskEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "task_type", nullable = false, length = 64)
    private String taskType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 32)
    private String priority;

    @Column(length = 64)
    private String channel;

    @Column(length = 255)
    private String reason;

    @Column(name = "latest_user_message")
    private String latestUserMessage;

    @Column(name = "callback_time_pref", length = 128)
    private String callbackTimePref;

    @Column(name = "callback_due_at")
    private OffsetDateTime callbackDueAt;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "sla_status", nullable = false, length = 32)
    private String slaStatus;

    @Column(name = "first_response_at")
    private OffsetDateTime firstResponseAt;

    @Column(name = "breached_at")
    private OffsetDateTime breachedAt;

    @Column(name = "last_notification_at")
    private OffsetDateTime lastNotificationAt;

    @Column(name = "last_staff_message_at")
    private OffsetDateTime lastStaffMessageAt;

    @Column(name = "handling_mode", nullable = false, length = 32)
    private String handlingMode;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public CareAiReceptionistTaskEntity() {
    }

    public static CareAiReceptionistTaskEntity create(
            UUID tenantId,
            UUID conversationId,
            UUID workflowId,
            UUID patientId,
            UUID leadId,
            UUID appointmentId,
            CareAiReceptionistTaskType taskType,
            CareAiReceptionistTaskPriority priority,
            String channel,
            String reason,
            String latestUserMessage,
            String callbackTimePref,
            OffsetDateTime callbackDueAt,
            OffsetDateTime dueAt,
            String metadataJson
    ) {
        CareAiReceptionistTaskEntity entity = new CareAiReceptionistTaskEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.conversationId = conversationId;
        entity.workflowId = workflowId;
        entity.patientId = patientId;
        entity.leadId = leadId;
        entity.appointmentId = appointmentId;
        entity.taskType = taskType.name();
        entity.status = CareAiReceptionistTaskStatus.OPEN.name();
        entity.priority = priority.name();
        entity.channel = channel;
        entity.reason = reason;
        entity.latestUserMessage = latestUserMessage;
        entity.callbackTimePref = callbackTimePref;
        entity.callbackDueAt = callbackDueAt;
        entity.dueAt = dueAt;
        entity.slaStatus = CareAiReceptionistTaskSlaStatus.ON_TIME.name();
        entity.handlingMode = CareAiReceptionistTaskHandlingMode.AI_HANDLING.name();
        entity.metadataJson = CareAiJsonSupport.parseObject(metadataJson);
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void assignTo(UUID assignedUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        this.assignedUserId = assignedUserId;
        this.assignedAt = now;
        this.status = CareAiReceptionistTaskStatus.ASSIGNED.name();
        if (this.firstResponseAt == null) {
            this.firstResponseAt = now;
        }
        this.handlingMode = CareAiReceptionistTaskHandlingMode.STAFF_HANDLING.name();
        this.updatedAt = now;
    }

    public void markInProgress(UUID actorUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.assignedUserId == null) {
            this.assignedUserId = actorUserId;
            this.assignedAt = now;
        }
        if (this.firstResponseAt == null) {
            this.firstResponseAt = now;
        }
        this.status = CareAiReceptionistTaskStatus.IN_PROGRESS.name();
        this.handlingMode = CareAiReceptionistTaskHandlingMode.STAFF_HANDLING.name();
        this.updatedAt = now;
    }

    public void resolve(UUID actorUserId, String resolutionNotes) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = CareAiReceptionistTaskStatus.RESOLVED.name();
        this.resolvedAt = now;
        this.resolvedByUserId = actorUserId;
        this.resolutionNotes = resolutionNotes;
        this.handlingMode = CareAiReceptionistTaskHandlingMode.STAFF_HANDLING.name();
        this.updatedAt = now;
    }

    public void cancel(UUID actorUserId, String resolutionNotes) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = CareAiReceptionistTaskStatus.CANCELLED.name();
        this.resolvedAt = now;
        this.resolvedByUserId = actorUserId;
        this.resolutionNotes = resolutionNotes;
        this.handlingMode = CareAiReceptionistTaskHandlingMode.STAFF_HANDLING.name();
        this.updatedAt = now;
    }

    public void resumeWithStaff(UUID actorUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.assignedUserId == null && actorUserId != null) {
            this.assignedUserId = actorUserId;
            this.assignedAt = now;
        }
        if (this.firstResponseAt == null) {
            this.firstResponseAt = now;
        }
        this.status = CareAiReceptionistTaskStatus.IN_PROGRESS.name();
        this.handlingMode = CareAiReceptionistTaskHandlingMode.STAFF_HANDLING.name();
        this.updatedAt = now;
    }

    public void returnToAi() {
        OffsetDateTime now = OffsetDateTime.now();
        this.handlingMode = CareAiReceptionistTaskHandlingMode.RETURNED_TO_AI.name();
        this.updatedAt = now;
    }

    public void scheduleCallback(String callbackTimePref, OffsetDateTime callbackDueAt, OffsetDateTime dueAt) {
        this.callbackTimePref = callbackTimePref;
        this.callbackDueAt = callbackDueAt;
        this.dueAt = dueAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public void recordStaffNote() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.firstResponseAt == null) {
            this.firstResponseAt = now;
        }
        this.lastStaffMessageAt = now;
        this.handlingMode = CareAiReceptionistTaskHandlingMode.STAFF_HANDLING.name();
        this.updatedAt = now;
    }

    public boolean updateSlaStatus(CareAiReceptionistTaskSlaStatus nextStatus, OffsetDateTime breachedAt, OffsetDateTime lastNotificationAt) {
        boolean changed = !nextStatus.name().equals(this.slaStatus)
                || (!equalsInstant(this.breachedAt, breachedAt))
                || (!equalsInstant(this.lastNotificationAt, lastNotificationAt));
        this.slaStatus = nextStatus.name();
        this.breachedAt = breachedAt;
        this.lastNotificationAt = lastNotificationAt;
        if (changed) {
            this.updatedAt = OffsetDateTime.now();
        }
        return changed;
    }

    private boolean equalsInstant(OffsetDateTime left, OffsetDateTime right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.toInstant().equals(right.toInstant());
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getConversationId() { return conversationId; }
    public UUID getWorkflowId() { return workflowId; }
    public UUID getPatientId() { return patientId; }
    public UUID getLeadId() { return leadId; }
    public UUID getAppointmentId() { return appointmentId; }
    public String getTaskType() { return taskType; }
    public String getStatus() { return status; }
    public String getPriority() { return priority; }
    public String getChannel() { return channel; }
    public String getReason() { return reason; }
    public String getLatestUserMessage() { return latestUserMessage; }
    public String getCallbackTimePref() { return callbackTimePref; }
    public OffsetDateTime getCallbackDueAt() { return callbackDueAt; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public String getSlaStatus() { return slaStatus; }
    public OffsetDateTime getFirstResponseAt() { return firstResponseAt; }
    public OffsetDateTime getBreachedAt() { return breachedAt; }
    public OffsetDateTime getLastNotificationAt() { return lastNotificationAt; }
    public OffsetDateTime getLastStaffMessageAt() { return lastStaffMessageAt; }
    public String getHandlingMode() { return handlingMode; }
    public UUID getAssignedUserId() { return assignedUserId; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public UUID getResolvedByUserId() { return resolvedByUserId; }
    public String getResolutionNotes() { return resolutionNotes; }
    public String getMetadataJson() { return CareAiJsonSupport.writeObject(metadataJson); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
