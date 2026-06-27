package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalConversationStateService {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalConversationStateService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ConversationEntityMergeService mergeService;
    private final WorkflowInterruptManager workflowInterruptManager;
    private final AivaConversationStateProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, ConversationStateSnapshot> snapshots = new ConcurrentHashMap<>();

    public PatientPortalConversationStateService(
            ConversationEntityMergeService mergeService,
            WorkflowInterruptManager workflowInterruptManager,
            AivaConversationStateProperties properties,
            ObjectMapper objectMapper
    ) {
        this.mergeService = mergeService;
        this.workflowInterruptManager = workflowInterruptManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ConversationStateSnapshot get(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        ConversationStateSnapshot snapshot = snapshots.get(conversationId);
        if (snapshot == null) {
            return null;
        }
        if (isExpired(snapshot)) {
            clear(conversationId);
            return null;
        }
        return snapshot;
    }

    public boolean isExpired(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return false;
        }
        ConversationStateSnapshot snapshot = snapshots.get(conversationId);
        return snapshot != null && isExpired(snapshot);
    }

    public ConversationStateSnapshot recordTurn(String conversationId, ConversationTurn turn) {
        if (!StringUtils.hasText(conversationId) || turn == null) {
            return null;
        }
        ConversationStateSnapshot previous = get(conversationId);
        ConversationEntityMergeService mergeService = this.mergeService;
        Map<String, Object> mergedEntities = previous == null
                ? mergeService.copy(turn.currentEntities())
                : mergeService.merge(previous.currentEntities(), turn.currentEntities());
        Map<String, Object> mergedPending = previous == null
                ? mergeService.copy(turn.pendingEntities())
                : mergeService.appendPending(previous.pendingEntities(), turn.pendingEntities());
        List<String> mergedSteps = previous == null
                ? List.copyOf(turn.completedSteps() == null ? List.of() : turn.completedSteps())
                : mergeService.mergeCompletedSteps(previous.completedSteps(), turn.completedSteps());
        WorkflowInterruptManager.WorkflowInterruptDecision interruptDecision = workflowInterruptManager.evaluate(
                previous == null ? null : PatientPortalCareAiIntent.parse(previous.currentIntent()),
                PatientPortalCareAiIntent.parse(turn.currentIntent()),
                turn.userText()
        );
        if (turn.resetRequested() || interruptDecision.resetRequested()) {
            clear(conversationId);
            ConversationStateSnapshot cleared = new ConversationStateSnapshot(
                    conversationId,
                    turn.language(),
                    null,
                    null,
                    Map.of(),
                    Map.of(),
                    List.of(),
                    false,
                    null,
                    turn.lastResponseType(),
                    Instant.now()
            );
            trace(conversationId, previous, cleared, interruptDecision, true, false);
            return cleared;
        }
        ConversationStateSnapshot updated = new ConversationStateSnapshot(
                conversationId,
                StringUtils.hasText(turn.language()) ? turn.language() : previous == null ? null : previous.language(),
                interruptDecision.targetIntent() == null ? turn.currentWorkflow() : interruptDecision.targetIntent().name(),
                turn.currentIntent(),
                mergedEntities,
                mergedPending,
                mergedSteps,
                turn.confirmationPending(),
                turn.lastAssistantQuestion(),
                turn.lastResponseType(),
                Instant.now()
        );
        snapshots.put(conversationId, updated);
        trace(conversationId, previous, updated, interruptDecision, interruptDecision.interruptOccurred(), false);
        return updated;
    }

    public void clear(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        snapshots.remove(conversationId);
    }

    public boolean isResetRequest(String userText) {
        return workflowInterruptManager.isResetRequest(userText);
    }

    public ConversationTurn turn(
            String userText,
            String language,
            String currentWorkflow,
            String currentIntent,
            Map<String, Object> currentEntities,
            Map<String, Object> pendingEntities,
            List<String> completedSteps,
            boolean confirmationPending,
            String lastAssistantQuestion,
            String lastResponseType,
            boolean resetRequested
    ) {
        return new ConversationTurn(userText, language, currentWorkflow, currentIntent, currentEntities, pendingEntities, completedSteps, confirmationPending, lastAssistantQuestion, lastResponseType, resetRequested);
    }

    private boolean isExpired(ConversationStateSnapshot snapshot) {
        return snapshot.lastUpdated() != null
                && Duration.between(snapshot.lastUpdated(), Instant.now()).toMinutes() >= Math.max(1, properties.getTimeoutMinutes());
    }

    private void trace(String conversationId,
                       ConversationStateSnapshot before,
                       ConversationStateSnapshot after,
                       WorkflowInterruptManager.WorkflowInterruptDecision interruptDecision,
                       boolean reset,
                       boolean timeout) {
        if (!log.isInfoEnabled()) {
            return;
        }
        Map<String, Object> entityChanges = new LinkedHashMap<>();
        if (before != null && after != null) {
            after.currentEntities().forEach((key, value) -> {
                Object previous = before.currentEntities().get(key);
                if (!java.util.Objects.equals(previous, value)) {
                    Map<String, Object> diff = new LinkedHashMap<>();
                    diff.put("before", previous);
                    diff.put("after", value);
                    entityChanges.put(key, diff);
                }
            });
        } else if (after != null) {
            entityChanges.putAll(after.currentEntities());
        }
        log.info(
                "AIVA_CONVERSATION_TRACE conversationId={} workflowBefore={} workflowAfter={} intentBefore={} intentAfter={} entityChanges={} interruptOccurred={} confirmationResolved={} stateReset={} timeout={} responseType={} language={}",
                conversationId,
                before == null ? null : before.currentWorkflow(),
                after == null ? null : after.currentWorkflow(),
                before == null ? null : before.currentIntent(),
                after == null ? null : after.currentIntent(),
                safeJson(entityChanges),
                interruptDecision != null && interruptDecision.interruptOccurred(),
                before != null && before.confirmationPending() && after != null && !after.confirmationPending(),
                reset,
                timeout,
                after == null ? null : after.lastResponseType(),
                after == null ? null : after.language()
        );
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public record ConversationTurn(
            String userText,
            String language,
            String currentWorkflow,
            String currentIntent,
            Map<String, Object> currentEntities,
            Map<String, Object> pendingEntities,
            List<String> completedSteps,
            boolean confirmationPending,
            String lastAssistantQuestion,
            String lastResponseType,
            boolean resetRequested
    ) {
    }

    public record ConversationStateSnapshot(
            String conversationId,
            String language,
            String currentWorkflow,
            String currentIntent,
            Map<String, Object> currentEntities,
            Map<String, Object> pendingEntities,
            List<String> completedSteps,
            boolean confirmationPending,
            String lastAssistantQuestion,
            String lastResponseType,
            Instant lastUpdated
    ) {
        public ConversationStateSnapshot {
            currentEntities = currentEntities == null ? Map.of() : Map.copyOf(currentEntities);
            pendingEntities = pendingEntities == null ? Map.of() : Map.copyOf(pendingEntities);
            completedSteps = completedSteps == null ? List.of() : List.copyOf(completedSteps);
        }
    }
}
