package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PatientPortalConversationStateServiceTest {
    private final ConversationEntityMergeService mergeService = new ConversationEntityMergeService();
    private final WorkflowInterruptManager workflowInterruptManager = new WorkflowInterruptManager();
    private final AivaConversationStateProperties properties = new AivaConversationStateProperties();
    private final PatientPortalConversationStateService service = new PatientPortalConversationStateService(
            mergeService,
            workflowInterruptManager,
            properties,
            new ObjectMapper()
    );

    @Test
    void recordTurnPreservesExistingDateWhenOnlyTimeChanges() {
        String conversationId = "conv-1";
        Map<String, Object> currentEntities = new LinkedHashMap<>();
        currentEntities.put("preferredDate", "2026-06-30");
        currentEntities.put("doctorName", "Dr Ashish Shri");

        service.recordTurn(
                conversationId,
                service.turn(
                        "30 June 2026",
                        "hi-IN",
                        "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT",
                        currentEntities,
                        Map.of("confirmationPending", false),
                        List.of("date"),
                        false,
                        "Pick a time",
                        "slot_list",
                        false
                )
        );

        PatientPortalConversationStateService.ConversationStateSnapshot updated = service.recordTurn(
                conversationId,
                service.turn(
                        "Evening",
                        "hi-IN",
                        "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT",
                        Map.of("preferredTimeWindow", "evening"),
                        Map.of("confirmationPending", false),
                        List.of("time"),
                        false,
                        "Pick a time",
                        "slot_list",
                        false
                )
        );

        assertThat(updated.currentEntities()).containsEntry("preferredDate", "2026-06-30");
        assertThat(updated.currentEntities()).containsEntry("preferredTimeWindow", "evening");
    }

    @Test
    void resetRequestedClearsStoredState() {
        String conversationId = "conv-reset";
        service.recordTurn(
                conversationId,
                service.turn(
                        "Book appointment",
                        "en",
                        "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT",
                        Map.of("doctorName", "Dr Ashish Shri"),
                        Map.of(),
                        List.of(),
                        true,
                        "Confirm?",
                        "confirmation_prompt",
                        false
                )
        );

        PatientPortalConversationStateService.ConversationStateSnapshot cleared = service.recordTurn(
                conversationId,
                service.turn(
                        "Start again",
                        "en",
                        "BOOK_APPOINTMENT",
                        "RESET_CONVERSATION",
                        Map.of(),
                        Map.of(),
                        List.of(),
                        false,
                        null,
                        "general",
                        true
                )
        );

        assertThat(cleared.currentWorkflow()).isNull();
        assertThat(cleared.currentEntities()).isEmpty();
        assertThat(service.get(conversationId)).isNull();
    }

    @Test
    void expiredConversationIsClearedOnRead() throws Exception {
        String conversationId = "conv-expired";
        Field snapshotsField = PatientPortalConversationStateService.class.getDeclaredField("snapshots");
        snapshotsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, PatientPortalConversationStateService.ConversationStateSnapshot> snapshots =
                (Map<String, PatientPortalConversationStateService.ConversationStateSnapshot>) snapshotsField.get(service);
        snapshots.put(conversationId, new PatientPortalConversationStateService.ConversationStateSnapshot(
                conversationId,
                "en",
                "BOOK_APPOINTMENT",
                "BOOK_APPOINTMENT",
                Map.of("doctorName", "Dr Ashish Shri"),
                Map.of(),
                List.of(),
                false,
                null,
                "slot_list",
                Instant.now().minusSeconds(3600)
        ));

        assertThat(service.get(conversationId)).isNull();
    }
}
