package com.deepthoughtnet.clinic.ai.careai.persistence;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiPendingConfirmationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiPendingConfirmationRepository;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEventEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEventRepository;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowRepository;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CareAiWorkflowLifecycleService {
    private static final Set<CareAiWorkflowState> TERMINAL_STATES = EnumSet.of(
            CareAiWorkflowState.COMPLETED,
            CareAiWorkflowState.CANCELLED,
            CareAiWorkflowState.ESCALATED,
            CareAiWorkflowState.EXPIRED
    );

    private final CareAiWorkflowRepository workflowRepository;
    private final CareAiWorkflowEventRepository workflowEventRepository;
    private final CareAiPendingConfirmationRepository pendingConfirmationRepository;

    public CareAiWorkflowLifecycleService(
            CareAiWorkflowRepository workflowRepository,
            CareAiWorkflowEventRepository workflowEventRepository,
            CareAiPendingConfirmationRepository pendingConfirmationRepository
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowEventRepository = workflowEventRepository;
        this.pendingConfirmationRepository = pendingConfirmationRepository;
    }

    public CareAiWorkflowEntity applySnapshot(
            UUID tenantId,
            CareAiConversationEntity conversation,
            CareAiWorkflowSnapshot snapshot
    ) {
        if (snapshot == null || snapshot.workflowType() == null || snapshot.state() == null) {
            return null;
        }

        CareAiWorkflowEntity workflow = resolveWorkflow(tenantId, conversation, snapshot);
        CareAiWorkflowState previousState = parseState(workflow.getState());
        String previousContext = workflow.getContextJson();

        if (workflow.getId() == null) {
            workflow = workflowRepository.save(CareAiWorkflowEntity.create(
                    tenantId,
                    conversation.getId(),
                    snapshot.workflowType().name(),
                    snapshot.state().name(),
                    snapshot.contextJson(),
                    snapshot.lastQuestionKey(),
                    snapshot.repeatedQuestionCount()
            ));
            appendEvent(tenantId, workflow.getId(), defaultEventType(snapshot, true), snapshot.eventPayloadJson());
        } else {
            workflow.applySnapshot(
                    snapshot.state().name(),
                    snapshot.contextJson(),
                    snapshot.lastQuestionKey(),
                    snapshot.repeatedQuestionCount()
            );
            workflow = workflowRepository.save(workflow);
            if (previousState != snapshot.state()
                    || !safe(previousContext).equals(safe(snapshot.contextJson()))
                    || StringUtils.hasText(snapshot.eventType())) {
                appendEvent(tenantId, workflow.getId(), defaultEventType(snapshot, false), snapshot.eventPayloadJson());
            }
        }

        syncPendingConfirmation(tenantId, workflow, snapshot, previousState);
        return workflow;
    }

    public CareAiWorkflowEntity findLatestWorkflow(UUID tenantId, UUID conversationId) {
        if (tenantId == null || conversationId == null) {
            return null;
        }
        return workflowRepository.findTopByTenantIdAndConversationIdOrderByUpdatedAtDesc(tenantId, conversationId)
                .orElse(null);
    }

    public CareAiPendingConfirmationEntity findActivePendingConfirmation(UUID tenantId, UUID workflowId) {
        if (tenantId == null || workflowId == null) {
            return null;
        }
        return pendingConfirmationRepository.findByTenantIdAndWorkflowIdAndResolvedAtIsNull(tenantId, workflowId)
                .stream()
                .filter(row -> row.getExpiresAt() == null || row.getExpiresAt().isAfter(OffsetDateTime.now()))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public void appendWorkflowEvent(UUID tenantId, UUID workflowId, String eventType, String payloadJson) {
        if (tenantId == null || workflowId == null || !StringUtils.hasText(eventType)) {
            return;
        }
        appendEvent(tenantId, workflowId, eventType, payloadJson);
    }

    private CareAiWorkflowEntity resolveWorkflow(
            UUID tenantId,
            CareAiConversationEntity conversation,
            CareAiWorkflowSnapshot snapshot
    ) {
        Optional<CareAiWorkflowEntity> latest = workflowRepository
                .findTopByTenantIdAndConversationIdOrderByUpdatedAtDesc(tenantId, conversation.getId());
        if (latest.isEmpty()) {
            return new CareAiWorkflowEntity();
        }
        CareAiWorkflowEntity existing = latest.get();
        if (!snapshot.workflowType().name().equals(existing.getWorkflowType()) || TERMINAL_STATES.contains(parseState(existing.getState()))) {
            return new CareAiWorkflowEntity();
        }
        return existing;
    }

    private void appendEvent(UUID tenantId, UUID workflowId, String eventType, String payloadJson) {
        workflowEventRepository.save(CareAiWorkflowEventEntity.create(
                tenantId,
                workflowId,
                eventType,
                payloadJson
        ));
    }

    private void syncPendingConfirmation(
            UUID tenantId,
            CareAiWorkflowEntity workflow,
            CareAiWorkflowSnapshot snapshot,
            CareAiWorkflowState previousState
    ) {
        if (StringUtils.hasText(snapshot.pendingConfirmationType()) && StringUtils.hasText(snapshot.pendingConfirmationScopeKey())) {
            List<CareAiPendingConfirmationEntity> activeRows = pendingConfirmationRepository
                    .findByTenantIdAndWorkflowIdAndResolvedAtIsNull(tenantId, workflow.getId());
            activeRows.stream()
                    .filter(row -> !snapshot.pendingConfirmationScopeKey().equals(row.getScopeKey()))
                    .forEach(row -> row.resolve("RESET"));
            if (activeRows.stream().anyMatch(row -> !snapshot.pendingConfirmationScopeKey().equals(row.getScopeKey()))) {
                appendEvent(tenantId, workflow.getId(), "CONFIRMATION_RESET", snapshot.pendingConfirmationPayloadJson());
            }
            pendingConfirmationRepository
                    .findTopByTenantIdAndWorkflowIdAndScopeKeyAndResolvedAtIsNullOrderByCreatedAtDesc(
                            tenantId,
                            workflow.getId(),
                            snapshot.pendingConfirmationScopeKey()
                    )
                    .orElseGet(() -> {
                        int nextVersion = pendingConfirmationRepository
                                .findTopByTenantIdAndWorkflowIdOrderByCreatedAtDesc(tenantId, workflow.getId())
                                .map(row -> row.getVersion() + 1)
                                .orElse(1);
                        CareAiPendingConfirmationEntity created = pendingConfirmationRepository.save(CareAiPendingConfirmationEntity.create(
                                tenantId,
                                workflow.getId(),
                                snapshot.pendingConfirmationType(),
                                snapshot.pendingConfirmationScopeKey(),
                                snapshot.pendingConfirmationPrompt(),
                                snapshot.pendingConfirmationPayloadJson(),
                                snapshot.pendingConfirmationExpiresAt(),
                                nextVersion
                        ));
                        appendEvent(tenantId, workflow.getId(), "CONFIRMATION_CREATED", snapshot.pendingConfirmationPayloadJson());
                        return created;
                    });
            return;
        }

        if (workflow.getState() == null || !CareAiWorkflowState.WAITING_CONFIRMATION.name().equals(workflow.getState())) {
            pendingConfirmationRepository.findByTenantIdAndWorkflowIdAndResolvedAtIsNull(tenantId, workflow.getId())
                    .forEach(row -> row.resolve(resolveConfirmationResolution(previousState, snapshot)));
            if (previousState == CareAiWorkflowState.WAITING_CONFIRMATION) {
                appendEvent(tenantId, workflow.getId(), confirmationResolutionEvent(snapshot), snapshot.eventPayloadJson());
            }
        }
    }

    private String resolveConfirmationResolution(CareAiWorkflowState previousState, CareAiWorkflowSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.eventType())) {
            if ("CONFIRMATION_RESET".equals(snapshot.eventType())) {
                return "RESET";
            }
            if ("APPOINTMENT_BOOKED".equals(snapshot.eventType()) || "WORKFLOW_COMPLETED".equals(snapshot.eventType())) {
                return "RESOLVED";
            }
            if ("CONFIRMATION_EXPIRED".equals(snapshot.eventType())) {
                return "EXPIRED";
            }
        }
        if (previousState == CareAiWorkflowState.WAITING_CONFIRMATION
                && snapshot.state() == CareAiWorkflowState.COMPLETED) {
            return "RESOLVED";
        }
        return "CLEARED";
    }

    private String confirmationResolutionEvent(CareAiWorkflowSnapshot snapshot) {
        if ("CONFIRMATION_RESET".equals(snapshot.eventType())) {
            return "CONFIRMATION_RESET";
        }
        if ("CONFIRMATION_EXPIRED".equals(snapshot.eventType())) {
            return "CONFIRMATION_EXPIRED";
        }
        return "CONFIRMATION_RESOLVED";
    }

    private String defaultEventType(CareAiWorkflowSnapshot snapshot, boolean created) {
        if (StringUtils.hasText(snapshot.eventType())) {
            return snapshot.eventType();
        }
        if (created) {
            return "WORKFLOW_STARTED";
        }
        return "WORKFLOW_" + snapshot.state().name();
    }

    private CareAiWorkflowState parseState(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return CareAiWorkflowState.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
