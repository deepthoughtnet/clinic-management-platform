package com.deepthoughtnet.clinic.ai.careai.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiWorkflowLifecycleService;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEntity;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEventRepository;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CareAiReceptionistTaskServiceTest {

    @Test
    void duplicateSameConversationDoesNotCreateSecondOpenTask() {
        UUID tenantId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiReceptionistTaskEntity existing = CareAiReceptionistTaskEntity.create(
                tenantId, conversationId, null, null, null, null,
                CareAiReceptionistTaskType.HUMAN_HANDOFF,
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_CHAT",
                "requested-receptionist",
                "talk to receptionist",
                null,
                null,
                OffsetDateTime.now().plusMinutes(15),
                "{}"
        );
        when(repository.findTopByTenantIdAndConversationIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));
        CareAiReceptionistTaskEntity created = service.createHandoffTask(
                new CareAiReceptionistTaskCreateCommand(tenantId, conversationId, null, null, null, null, "PATIENT_PORTAL_CHAT", "requested-receptionist", "talk to receptionist", null, null, "{}"),
                CareAiReceptionistTaskPriority.MEDIUM
        );

        assertThat(created.getId()).isEqualTo(existing.getId());
        verify(repository, never()).save(any(CareAiReceptionistTaskEntity.class));
    }

    @Test
    void assignTaskUpdatesStatusAndAssignedUser() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiReceptionistTaskEntity task = CareAiReceptionistTaskEntity.create(
                tenantId, null, null, null, null, null,
                CareAiReceptionistTaskType.HUMAN_HANDOFF,
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_CHAT",
                "requested-receptionist",
                "talk to receptionist",
                null,
                null,
                OffsetDateTime.now().plusMinutes(15),
                "{}"
        );
        when(repository.findByTenantIdAndId(tenantId, taskId)).thenReturn(Optional.of(task));
        when(repository.save(any(CareAiReceptionistTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setEntityId(task, taskId);
        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));
        CareAiReceptionistTaskEntity updated = service.assignTask(tenantId, taskId, actorUserId);

        assertThat(updated.getStatus()).isEqualTo(CareAiReceptionistTaskStatus.ASSIGNED.name());
        assertThat(updated.getAssignedUserId()).isEqualTo(actorUserId);
        verify(eventRepository).save(any());
    }

    @Test
    void resolveTaskUpdatesStatusAndResolution() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiReceptionistTaskEntity task = CareAiReceptionistTaskEntity.create(
                tenantId, null, null, null, null, null,
                CareAiReceptionistTaskType.CALLBACK_REQUEST,
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_VOICE",
                "callback-request",
                "call me back tomorrow",
                "tomorrow evening",
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1),
                "{}"
        );
        when(repository.findByTenantIdAndId(tenantId, taskId)).thenReturn(Optional.of(task));
        when(repository.save(any(CareAiReceptionistTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setEntityId(task, taskId);
        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));
        CareAiReceptionistTaskEntity updated = service.resolveTask(tenantId, taskId, actorUserId, "Called patient");

        assertThat(updated.getStatus()).isEqualTo(CareAiReceptionistTaskStatus.RESOLVED.name());
        assertThat(updated.getResolvedByUserId()).isEqualTo(actorUserId);
        assertThat(updated.getResolutionNotes()).isEqualTo("Called patient");
    }

    @Test
    void cancelTaskUpdatesStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiReceptionistTaskEntity task = CareAiReceptionistTaskEntity.create(
                tenantId, null, null, null, null, null,
                CareAiReceptionistTaskType.HUMAN_HANDOFF,
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_CHAT",
                "requested-receptionist",
                "talk to receptionist",
                null,
                null,
                OffsetDateTime.now().plusMinutes(15),
                "{}"
        );
        when(repository.findByTenantIdAndId(tenantId, taskId)).thenReturn(Optional.of(task));
        when(repository.save(any(CareAiReceptionistTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setEntityId(task, taskId);
        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));
        CareAiReceptionistTaskEntity updated = service.cancelTask(tenantId, taskId, actorUserId, "Duplicate");

        assertThat(updated.getStatus()).isEqualTo(CareAiReceptionistTaskStatus.CANCELLED.name());
        assertThat(updated.getResolutionNotes()).isEqualTo("Duplicate");
    }

    @Test
    void tenantIsolationBlocksCrossTenantLookup() {
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        when(repository.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());

        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));

        assertThatThrownBy(() -> service.getTask(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void listTasksFiltersByAssignedUser() {
        UUID tenantId = UUID.randomUUID();
        UUID assignedUserId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiReceptionistTaskEntity assigned = CareAiReceptionistTaskEntity.create(
                tenantId, null, null, null, null, null,
                CareAiReceptionistTaskType.CALLBACK_REQUEST,
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_CHAT",
                "callback-request",
                "call me tomorrow",
                "tomorrow",
                null,
                OffsetDateTime.now().plusHours(4),
                "{}"
        );
        CareAiReceptionistTaskEntity other = CareAiReceptionistTaskEntity.create(
                tenantId, null, null, null, null, null,
                CareAiReceptionistTaskType.HUMAN_HANDOFF,
                CareAiReceptionistTaskPriority.HIGH,
                "PATIENT_PORTAL_CHAT",
                "requested-receptionist",
                "talk to staff",
                null,
                null,
                OffsetDateTime.now().plusMinutes(15),
                "{}"
        );
        assigned.assignTo(assignedUserId);
        when(repository.findTop200ByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(other, assigned));

        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));
        List<CareAiReceptionistTaskEntity> rows = service.listTasks(
                tenantId,
                new CareAiReceptionistTaskFilter(null, null, null, assignedUserId, null, null, null)
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getAssignedUserId()).isEqualTo(assignedUserId);
    }

    @Test
    void duplicateEscalationDoesNotCreateSecondOpenTask() {
        UUID tenantId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiReceptionistTaskEntity existing = CareAiReceptionistTaskEntity.create(
                tenantId, conversationId, null, null, null, null,
                CareAiReceptionistTaskType.ESCALATION,
                CareAiReceptionistTaskPriority.HIGH,
                "PATIENT_PORTAL_VOICE",
                "booking-failed",
                "this is not working",
                null,
                null,
                OffsetDateTime.now().plusMinutes(10),
                "{}"
        );
        when(repository.findTopByTenantIdAndConversationIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));
        CareAiReceptionistTaskEntity created = service.createEscalationTask(
                new CareAiReceptionistTaskCreateCommand(tenantId, conversationId, null, null, null, null, "PATIENT_PORTAL_VOICE", "booking-failed", "this is not working", null, null, "{}"),
                CareAiReceptionistTaskPriority.HIGH
        );

        assertThat(created.getId()).isEqualTo(existing.getId());
        verify(repository, never()).save(any(CareAiReceptionistTaskEntity.class));
    }

    @Test
    void resumeTaskMarksStaffHandlingAndFirstResponse() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiWorkflowLifecycleService workflowLifecycleService = mock(CareAiWorkflowLifecycleService.class);
        CareAiConversationPersistenceService conversationPersistenceService = mock(CareAiConversationPersistenceService.class);
        CareAiReceptionistTaskEntity task = CareAiReceptionistTaskEntity.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), null, null, null,
                CareAiReceptionistTaskType.HUMAN_HANDOFF,
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_CHAT",
                "requested-receptionist",
                "talk to receptionist",
                null,
                null,
                OffsetDateTime.now().plusMinutes(15),
                "{}"
        );
        setEntityId(task, taskId);
        when(repository.findByTenantIdAndId(tenantId, taskId)).thenReturn(Optional.of(task));
        when(repository.save(any(CareAiReceptionistTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, workflowLifecycleService, conversationPersistenceService);
        CareAiReceptionistTaskEntity updated = service.resumeTask(tenantId, taskId, actorUserId);

        assertThat(updated.getStatus()).isEqualTo(CareAiReceptionistTaskStatus.IN_PROGRESS.name());
        assertThat(updated.getHandlingMode()).isEqualTo(CareAiReceptionistTaskHandlingMode.STAFF_HANDLING.name());
        assertThat(updated.getFirstResponseAt()).isNotNull();
        verify(eventRepository).save(any());
    }

    @Test
    void evaluateSlaMarksBreachedTask() {
        UUID tenantId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiReceptionistTaskEntity task = CareAiReceptionistTaskEntity.create(
                tenantId, null, null, null, null, null,
                CareAiReceptionistTaskType.ESCALATION,
                CareAiReceptionistTaskPriority.HIGH,
                "PATIENT_PORTAL_VOICE",
                "booking-failed",
                "this is not working",
                null,
                null,
                OffsetDateTime.now().minusMinutes(20),
                "{}"
        );
        when(repository.findByTenantIdAndStatusInOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(task));
        when(repository.save(any(CareAiReceptionistTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));
        List<CareAiReceptionistTaskEntity> updated = service.evaluateSla(tenantId, Duration.ofMinutes(5));

        assertThat(updated).hasSize(1);
        assertThat(updated.getFirst().getSlaStatus()).isEqualTo(CareAiReceptionistTaskSlaStatus.BREACHED.name());
        assertThat(updated.getFirst().getBreachedAt()).isNotNull();
    }

    @Test
    void staffNoteIsStoredOnLinkedConversation() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiConversationPersistenceService conversationPersistenceService = mock(CareAiConversationPersistenceService.class);
        CareAiReceptionistTaskEntity task = CareAiReceptionistTaskEntity.create(
                tenantId, conversationId, UUID.randomUUID(), null, null, null,
                CareAiReceptionistTaskType.HUMAN_HANDOFF,
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_CHAT",
                "requested-receptionist",
                "talk to receptionist",
                null,
                null,
                OffsetDateTime.now().plusMinutes(15),
                "{}"
        );
        setEntityId(task, taskId);
        when(repository.findByTenantIdAndId(tenantId, taskId)).thenReturn(Optional.of(task));
        when(repository.save(any(CareAiReceptionistTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), conversationPersistenceService);
        service.addStaffNote(tenantId, taskId, actorUserId, "Patient asked for evening slots");

        verify(conversationPersistenceService).appendConversationMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void returnToAiUpdatesHandlingMode() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        CareAiReceptionistTaskRepository repository = mock(CareAiReceptionistTaskRepository.class);
        CareAiReceptionistTaskEventRepository eventRepository = mock(CareAiReceptionistTaskEventRepository.class);
        CareAiReceptionistTaskEntity task = CareAiReceptionistTaskEntity.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), null, null, null,
                CareAiReceptionistTaskType.CALLBACK_REQUEST,
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_CHAT",
                "callback-request",
                "please call me back",
                "tomorrow evening",
                null,
                OffsetDateTime.now().plusHours(4),
                "{}"
        );
        setEntityId(task, taskId);
        when(repository.findByTenantIdAndId(tenantId, taskId)).thenReturn(Optional.of(task));
        when(repository.save(any(CareAiReceptionistTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareAiReceptionistTaskService service = new CareAiReceptionistTaskService(repository, eventRepository, mock(CareAiWorkflowLifecycleService.class), mock(CareAiConversationPersistenceService.class));
        CareAiReceptionistTaskEntity updated = service.returnToAi(tenantId, taskId, UUID.randomUUID());

        assertThat(updated.getHandlingMode()).isEqualTo(CareAiReceptionistTaskHandlingMode.RETURNED_TO_AI.name());
    }

    private void setEntityId(CareAiReceptionistTaskEntity entity, UUID taskId) {
        try {
            var field = CareAiReceptionistTaskEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, taskId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
