package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

    @Test
    void quickRegistrationConsentNameAndAgeSurviveSessionJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var registration = new AivaV2Models.VoiceGuestRegistrationState(
                AivaV2Models.VoiceGuestRegistrationStatus.AWAITING_AGE, UUID.randomUUID(), null,
                "voice-call", UUID.randomUUID(), null, false, "Asha", 42,
                Instant.parse("2026-10-30T02:00:00Z"));
        SessionProjection original = new SessionProjection("voice-call", null,
                registration.tenantId().toString(), null, null, null, null, null,
                Instant.now().plusSeconds(600)).withVoiceGuestRegistrationState(registration);

        SessionProjection restored = mapper.readValue(mapper.writeValueAsBytes(original), SessionProjection.class);

        assertThat(restored.voiceGuestRegistrationState()).isEqualTo(registration);
        assertThat(restored.voiceGuestRegistrationState().consentGiven()).isTrue();
        assertThat(restored.voiceGuestRegistrationState().consentSource()).isEqualTo("VOICE");

        var patientId = UUID.randomUUID();
        var completedRegistration = new AivaV2Models.VoiceGuestRegistrationState(
                AivaV2Models.VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, registration.tenantId(), patientId,
                registration.providerCallId(), registration.interactionId(), Instant.now().plusSeconds(600), true,
                registration.capturedFirstName(), registration.capturedAgeYears(), registration.consentTimestamp());
        var provider = new AivaV2Models.ProviderCandidate("provider-candidate", "provider-handle", "doctor-1",
                "clinic-1", registration.tenantId().toString(), "clinic", "booking-ref", "Dr. Ravi Mehta",
                "General Medicine", "Clinic", AivaV2Models.ProviderSource.CARE_PRIVATE,
                new AivaV2Models.ProviderCapabilities(true, true, false, false, false, true, false));
        var date = LocalDate.of(2026, 10, 31);
        var draft = new AivaV2Models.BookingDraft(UUID.randomUUID(), patientId,
                registration.tenantId().toString(), provider, "General Medicine", date, "EVENING", null,
                null, null, AivaV2Models.DraftStatus.COLLECTING, 2, Instant.now().plusSeconds(600), null);
        var availability = new AivaV2Models.AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(),
                "criteria", provider.providerHandle(), provider.doctorId(), provider.clinicId(), date, "EVENING",
                "Asia/Kolkata", List.of(new AivaV2Models.AvailabilitySlot("displayed-only", LocalTime.of(18, 0),
                        LocalTime.of(18, 30), "6 PM")), null, false, Instant.now(), Instant.now().plusSeconds(300));
        var providerResult = new AivaV2Models.ProviderSearchResult(UUID.randomUUID(),
                AivaV2Models.ResolutionStatus.RESOLVED, "provider", List.of(provider), provider, Instant.now(),
                Instant.now().plusSeconds(300));
        SessionProjection registered = original.withPatientId(patientId).withBookingDraft(draft)
                .withVoiceGuestRegistrationState(completedRegistration).withActiveVoiceDoctor(provider)
                .withVoiceProviderResult(providerResult).withVoiceAvailabilityResult(availability)
                .withPresentationMode(AivaV2Models.VoicePresentationMode.HINDI);
        SessionProjection registeredRestored = mapper.readValue(mapper.writeValueAsBytes(registered),
                SessionProjection.class);
        assertThat(registeredRestored.voiceGuestRegistrationState()).isEqualTo(completedRegistration);
        assertThat(registeredRestored.patientId()).isEqualTo(patientId);
        assertThat(registeredRestored.activeDraft()).isEqualTo(draft);
        assertThat(registeredRestored.activeDraft().selectedProvider()).isEqualTo(provider);
        assertThat(registeredRestored.activeDraft().specialtyFilter()).isEqualTo("General Medicine");
        assertThat(registeredRestored.activeDraft().preferredDate()).isEqualTo(date);
        assertThat(registeredRestored.activeDraft().preferredTimeWindow()).isEqualTo("EVENING");
        assertThat(registeredRestored.activeDraft().selectedSlotReference()).isNull();
        assertThat(registeredRestored.activeVoiceDoctor()).isEqualTo(provider);
        assertThat(registeredRestored.latestProviderResult()).isEqualTo(providerResult);
        assertThat(registeredRestored.latestAvailabilityResult()).isEqualTo(availability);
        assertThat(registeredRestored.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.HINDI);
        assertThat(registeredRestored.tenantId()).isEqualTo(registration.tenantId().toString());
        assertThat(registeredRestored.conversationId()).isEqualTo(registration.providerCallId());

        var consented = new AivaV2Models.VoiceGuestRegistrationState(
                AivaV2Models.VoiceGuestRegistrationStatus.COLLECTING_DETAILS, registration.tenantId(), null,
                registration.providerCallId(), registration.interactionId(), null, false, null, null,
                registration.consentTimestamp(), true, "VOICE");
        SessionProjection afterConsent = registeredRestored.withVoiceGuestRegistrationState(consented);
        assertThat(afterConsent.voiceGuestRegistrationState().consentGiven()).isTrue();
        assertThat(afterConsent.voiceGuestRegistrationState().consentSource()).isEqualTo("VOICE");
        assertThat(afterConsent.activeDraft()).isEqualTo(registeredRestored.activeDraft());
        assertThat(afterConsent.latestAvailabilityResult()).isEqualTo(registeredRestored.latestAvailabilityResult());
        assertThat(afterConsent.activeVoiceDoctor()).isEqualTo(registeredRestored.activeVoiceDoctor());
        assertThat(afterConsent.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.HINDI);
        SessionProjection nameAndAge = afterConsent.withVoiceGuestRegistrationState(
                new AivaV2Models.VoiceGuestRegistrationState(
                        AivaV2Models.VoiceGuestRegistrationStatus.DUPLICATE_CHECK, registration.tenantId(), null,
                        registration.providerCallId(), registration.interactionId(), null, false, "Ashish", 42,
                        registration.consentTimestamp(), true, "VOICE"));
        assertThat(nameAndAge.voiceGuestRegistrationState().capturedFirstName()).isEqualTo("Ashish");
        assertThat(nameAndAge.voiceGuestRegistrationState().capturedAgeYears()).isEqualTo(42);
        assertThat(nameAndAge.activeDraft().selectedProvider()).isEqualTo(provider);
        assertThat(nameAndAge.activeDraft().preferredDate()).isEqualTo(date);
        assertThat(nameAndAge.activeDraft().preferredTimeWindow()).isEqualTo("EVENING");
        assertThat(nameAndAge.latestAvailabilityResult()).isEqualTo(availability);
        assertThat(nameAndAge.latestAvailabilityResult().slots().getFirst().startsAt()).isEqualTo(LocalTime.of(18, 0));
    }
}
