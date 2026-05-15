package com.deepthoughtnet.clinic.realtime.voice.receptionist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEntity;
import com.deepthoughtnet.clinic.realtime.voice.session.VoiceSessionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AiReceptionistWorkflowServiceTest {
    private ClinicProfileService clinicProfileService;
    private AiReceptionistWorkflowService service;

    @BeforeEach
    void setUp() {
        clinicProfileService = Mockito.mock(ClinicProfileService.class);
        service = new AiReceptionistWorkflowService(
                new ObjectMapper(),
                Mockito.mock(LeadService.class),
                Mockito.mock(LeadActivityService.class),
                clinicProfileService
        );
    }

    @Test
    void greetingTransitionsToIdentifyIntent() {
        VoiceSessionEntity session = newSession("{}");

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "hello", "corr-1");

        assertThat(result.promptKey()).isEqualTo("AI_RECEPTIONIST_INTENT_DETECTION");
        assertThat(session.getMetadataJson()).contains("IDENTIFY_INTENT");
    }

    @Test
    void faqIntentUsesDeterministicClinicProfileWhenAvailable() {
        UUID tenantId = UUID.randomUUID();
        VoiceSessionEntity session = newSession("{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\"}}", tenantId);
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.of(new ClinicProfileRecord(
                UUID.randomUUID(), tenantId, "Clinic One", "Clinic One", "+1-222-333-4444", "contact@clinic.test",
                "12 Main St", null, "Metropolis", "CA", "US", "90001", null, null, null, true, null, null
        )));

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "what is your address", "corr-2");

        assertThat(result.promptKey()).isEqualTo("AI_RECEPTIONIST_FAQ");
        assertThat(result.deterministicReply()).contains("12 Main St");
    }

    @Test
    void emergencyIntentAlwaysEscalates() {
        VoiceSessionEntity session = newSession("{}");

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "I have chest pain emergency", "corr-3");

        assertThat(result.escalate()).isTrue();
        assertThat(result.promptKey()).isEqualTo("AI_RECEPTIONIST_SAFE_ESCALATION");
        assertThat(result.deterministicReply()).contains("can't provide medical advice");
        assertThat(session.getMetadataJson()).contains("HUMAN_ESCALATION");
    }

    @Test
    void repeatedUnknownIntentEscalatesOnThirdAttempt() {
        VoiceSessionEntity session = newSession("{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\",\"unknownCount\":2}}", UUID.randomUUID());

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "blabla random", "corr-4");

        assertThat(result.escalate()).isTrue();
        assertThat(result.escalationReason()).contains("Low confidence");
        assertThat(session.getMetadataJson()).contains("IDENTIFY_INTENT");
    }

    @Test
    void bookingIntentTracksMissingFieldsInMemory() {
        VoiceSessionEntity session = newSession("{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\"}}", UUID.randomUUID());

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "I want to book appointment", "corr-5");

        assertThat(result.intent()).isEqualTo(ReceptionistIntent.BOOK_APPOINTMENT);
        assertThat(session.getMetadataJson()).contains("missingFields");
        assertThat(session.getMetadataJson()).contains("appointmentRequestCreated");
    }

    @Test
    void billingIntentRoutesToBillingDeskEscalation() {
        VoiceSessionEntity session = newSession("{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\"}}", UUID.randomUUID());

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "I need billing invoice help", "corr-6");

        assertThat(result.escalate()).isTrue();
        assertThat(result.escalationCategory()).isEqualTo("BILLING_DESK");
        assertThat(result.deterministicReply()).contains("billing desk");
    }

    private VoiceSessionEntity newSession(String metadata) {
        return newSession(metadata, UUID.randomUUID());
    }

    private VoiceSessionEntity newSession(String metadata, UUID tenantId) {
        return VoiceSessionEntity.create(
                tenantId,
                VoiceSessionType.AI_RECEPTIONIST,
                null,
                null,
                "ai-orchestration",
                "mock-stt",
                "mock-tts",
                metadata
        );
    }
}
