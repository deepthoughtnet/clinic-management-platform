package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AivaV2SessionProjectionSerializationTest {
    @Test
    void presentationModeSurvivesTheSessionJsonRoundTripAndWorkflowClearing() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        SessionProjection original = new SessionProjection("call", null, "tenant", null, null,
                null, null, null, Instant.now().plusSeconds(600))
                .withPresentationMode(AivaV2Models.VoicePresentationMode.HINGLISH);

        SessionProjection restored = mapper.readValue(mapper.writeValueAsBytes(original), SessionProjection.class);

        assertThat(restored.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.HINGLISH);
        assertThat(restored.clearTransientWorkflow().presentationMode())
                .isEqualTo(AivaV2Models.VoicePresentationMode.HINGLISH);
    }

    @Test
    void tenantCanonicalProviderClarificationCandidatesSurviveSessionJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        UUID tenant = UUID.randomUUID();
        var clarification = new AivaV2Models.VoiceClarificationState(
                AivaV2Models.VoiceClarificationKind.PROVIDER_IDENTITY, 1,
                AivaV2Models.VoiceUnresolvedReason.AMBIGUOUS_DOCTOR,
                List.of("Ravi Mehta", "Ravi Sharma"), false, "PROVIDER_IDENTITY", "BOOKING",
                List.of(new AivaV2Models.VoiceProviderIdentityCandidate("ravi-mehta", tenant, "Ravi Mehta"),
                        new AivaV2Models.VoiceProviderIdentityCandidate("ravi-sharma", tenant, "Ravi Sharma")), false);
        SessionProjection original = new SessionProjection("call", null, tenant.toString(), null, null,
                null, null, null, Instant.now().plusSeconds(600)).withVoiceClarificationState(clarification);

        SessionProjection restored = mapper.readValue(mapper.writeValueAsBytes(original), SessionProjection.class);

        assertThat(restored.voiceClarificationState().pendingAction()).isEqualTo("PROVIDER_IDENTITY");
        assertThat(restored.voiceClarificationState().canonicalProviderCandidates())
                .containsExactlyElementsOf(clarification.canonicalProviderCandidates());
    }

    @Test
    void completedBookingOutcomeSurvivesSerializationAndTransientWorkflowClearing() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var completed = new AivaV2Models.CompletedBookingOutcome(UUID.randomUUID(), "APT-123",
                "Dr Amit Verma", java.time.LocalDate.of(2026, 10, 27), java.time.LocalTime.of(17, 30),
                Instant.now(), tenant, patient, "call-1", "execution-ref", "VOICE", "CONFIRMED");
        SessionProjection original = new SessionProjection("call-1", patient, tenant.toString(), null, null,
                null, null, null, Instant.now().plusSeconds(600)).withCompletedBookingOutcome(completed);

        SessionProjection restored = mapper.readValue(mapper.writeValueAsBytes(original), SessionProjection.class);

        assertThat(restored.completedBookingOutcome()).isEqualTo(completed);
        assertThat(restored.clearTransientWorkflow().completedBookingOutcome()).isEqualTo(completed);
    }
}
