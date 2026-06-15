package com.deepthoughtnet.clinic.api.careai;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiJsonSupport;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskSlaStatus;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEntity;
import com.deepthoughtnet.clinic.notification.model.NotificationEventPayload;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventCommand;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CareAiTaskNotificationService {
    private static final List<String> DEFAULT_RECIPIENT_ROLES = List.of("CLINIC_ADMIN", "RECEPTIONIST");

    private final OutboxEventPublisher outboxEventPublisher;
    private final ObjectMapper objectMapper;

    public CareAiTaskNotificationService(
            OutboxEventPublisher outboxEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.outboxEventPublisher = outboxEventPublisher;
        this.objectMapper = objectMapper;
    }

    public void notifyTaskCreated(CareAiReceptionistTaskEntity task) {
        if (task == null) {
            return;
        }
        publish(task, "CAREAI_TASK_CREATED", "New AIVA " + task.getTaskType() + " task",
                buildBody(task, "An AIVA task needs staff follow-up."),
                "careai-task-created:" + task.getId());
    }

    public void notifySlaBreached(CareAiReceptionistTaskEntity task) {
        if (task == null) {
            return;
        }
        publish(task, "CAREAI_TASK_SLA_BREACHED", "AIVA task SLA breached",
                buildBody(task, "An AIVA task has breached its SLA and needs attention."),
                "careai-task-sla-breached:" + task.getId() + ":" + task.getSlaStatus());
    }

    private void publish(
            CareAiReceptionistTaskEntity task,
            String eventType,
            String subject,
            String body,
            String deduplicationKey
    ) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("taskId", task.getId());
            details.put("taskType", task.getTaskType());
            details.put("priority", task.getPriority());
            details.put("patientId", task.getPatientId());
            details.put("reason", task.getReason());
            details.put("dueAt", task.getDueAt());
            details.put("slaStatus", task.getSlaStatus());
            NotificationEventPayload payload = new NotificationEventPayload(
                    null,
                    eventType,
                    DEFAULT_RECIPIENT_ROLES,
                    null,
                    "email",
                    subject,
                    body,
                    eventType,
                    CareAiJsonSupport.writeObject(details),
                    task.getPatientId(),
                    "CAREAI_TASK",
                    task.getId()
            );
            outboxEventPublisher.publish(new OutboxEventCommand(
                    task.getTenantId(),
                    eventType,
                    "CAREAI_TASK",
                    task.getId(),
                    deduplicationKey,
                    objectMapper.writeValueAsString(payload),
                    OffsetDateTime.now()
            ));
        } catch (Exception ignored) {
            // Task notifications are additive and should not break operational flow.
        }
    }

    private String buildBody(CareAiReceptionistTaskEntity task, String leadLine) {
        StringBuilder body = new StringBuilder(leadLine);
        if (task.getReason() != null) {
            body.append("\nReason: ").append(task.getReason());
        }
        if (task.getLatestUserMessage() != null) {
            body.append("\nLatest message: ").append(task.getLatestUserMessage());
        }
        if (task.getDueAt() != null) {
            body.append("\nDue at: ").append(task.getDueAt());
        }
        if (CareAiReceptionistTaskSlaStatus.BREACHED.name().equals(task.getSlaStatus())) {
            body.append("\nSLA status: BREACHED");
        }
        return body.toString();
    }
}
