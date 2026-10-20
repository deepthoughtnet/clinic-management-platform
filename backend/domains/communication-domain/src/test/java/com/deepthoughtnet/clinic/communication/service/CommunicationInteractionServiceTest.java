package com.deepthoughtnet.clinic.communication.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.communication.db.CommunicationInteractionEntity;
import com.deepthoughtnet.clinic.communication.db.CommunicationInteractionRepository;
import com.deepthoughtnet.clinic.communication.model.ConversationPartyType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommunicationInteractionServiceTest {
    @Test
    void providerCallIdIsReusedForDuplicateStartEvents() {
        CommunicationInteractionRepository repository = mock(CommunicationInteractionRepository.class);
        CommunicationInteractionService service = new CommunicationInteractionService(repository);
        CommunicationInteractionEntity row = CommunicationInteractionEntity.create("call-1", UUID.randomUUID(), UUID.randomUUID(),
                null, null, "VOICE", "DOTVOICE", "INBOUND", "+919999999999", "******1234",
                "NO_MATCH", null, ConversationPartyType.GUEST_CALLER, 0, "RINGING", OffsetDateTime.now());
        when(repository.findByProviderCallId("call-1")).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID first = service.startInbound("call-1", row.getTenantId(), row.getCommunicationChannelId(), null, null,
                "DOTVOICE", "+919999999999", "******1234", "NO_MATCH", null,
                ConversationPartyType.GUEST_CALLER, 0, "RINGING", row.getStartedAt());
        UUID second = service.startInbound("call-1", row.getTenantId(), row.getCommunicationChannelId(), null, null,
                "DOTVOICE", "+919999999999", "******1234", "NO_MATCH", null,
                ConversationPartyType.GUEST_CALLER, 0, "RINGING", row.getStartedAt());

        assertNotNull(first);
        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        verify(repository, times(2)).save(row);
    }

    @Test
    void persistenceFailureIsBestEffort() {
        CommunicationInteractionRepository repository = mock(CommunicationInteractionRepository.class);
        when(repository.findByProviderCallId("failed-call")).thenThrow(new IllegalStateException("database unavailable"));

        CommunicationInteractionService service = new CommunicationInteractionService(repository);

        assertDoesNotThrow(() -> service.startInbound("failed-call", null, null, null, null, "DOTVOICE",
                null, "******1234", "LOOKUP_FAILED", null, ConversationPartyType.GUEST_CALLER, 0,
                "ROUTING_FAILED", OffsetDateTime.now()));
    }

    @Test
    void lifecycleUpdatesPersistAnswerTranscriptAndEnd() {
        CommunicationInteractionRepository repository = mock(CommunicationInteractionRepository.class);
        CommunicationInteractionService service = new CommunicationInteractionService(repository);
        CommunicationInteractionEntity row = CommunicationInteractionEntity.create("call-2", null, null, null, null,
                "VOICE", "DOTVOICE", "INBOUND", null, "******1234", "NO_MATCH", null,
                ConversationPartyType.GUEST_CALLER, 0, "RINGING", OffsetDateTime.now().minusSeconds(3));
        when(repository.findByProviderCallId("call-2")).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateInbound("call-2", current -> current.markAnswered(OffsetDateTime.now()));
        service.updateInbound("call-2", CommunicationInteractionEntity::markTranscriptPresent);
        service.updateInbound("call-2", current -> current.markEnded("ENDED", "COMPLETED", "NORMAL", OffsetDateTime.now()));

        org.junit.jupiter.api.Assertions.assertNotNull(row.getAnsweredAt());
        org.junit.jupiter.api.Assertions.assertTrue(row.isTranscriptPresent());
        org.junit.jupiter.api.Assertions.assertNotNull(row.getEndedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(row.getDurationSeconds());
        verify(repository, times(3)).save(row);
    }
}
