package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaTransactionAuthorization;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceConversationAdapter;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalOtpService;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VoiceGuestRegistrationTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID INTERACTION = UUID.randomUUID();

    @Test
    void englishAndHindiAffirmativeConsentAdvanceToNameWithoutCreatingOrBooking() {
        for (String affirmative : List.of("Yes, please.", "हाँ, रजिस्ट्रेशन कर दीजिए।")) {
            HealthcareAiReadOnlyToolGateway gateway = mock(HealthcareAiReadOnlyToolGateway.class);
            AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
            PatientService patients = mock(PatientService.class);
            VoiceReadOnlyAivaService service = service(gateway, adapter, patients, guestEnabledProperties());
            VoiceReadOnlyAivaService.Context context = noMatchContext("guest-consent-" + affirmative.hashCode());
            AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
            when(adapter.voiceAuthenticationState(any())).thenReturn(null);
            when(adapter.currentVoiceAuthorization(any())).thenReturn(
                    AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
            when(adapter.hasVoiceBookingWorkflow(any())).thenReturn(true);
            when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                    .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                            "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

            service.continueTurn(context, "book appointment");
            assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CONSENT_REQUESTED);
            var consent = service.continueTurn(context, affirmative);
            assertThat(consent.response()).contains("पहला नाम");
            assertThat(consent.response()).doesNotContain("क्या मैं आपका basic registration बना दूँ");
            assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.COLLECTING_DETAILS);
            assertThat(state.get().consentGiven()).isTrue();
            assertThat(state.get().consentTimestamp()).isNotNull();
            assertThat(state.get().consentSource()).isEqualTo("VOICE");
            assertThat(state.get().patientId()).isNull();
            assertThat(state.get().guestBookingCapabilityGranted()).isFalse();
            assertThat(consent.mutationBlocked()).isFalse();
            assertThat(consent.runtimeDiagnostics().toString()).contains(
                    "guestRegistrationStateBefore=CONSENT_REQUESTED",
                    "guestRegistrationStateAfter=AWAIT_QUICK_REGISTRATION_NAME",
                    "consentDecision=ACCEPTED", "consentPersisted=true", "namePromptIssued=true",
                    "bookingContextPreserved=true", "consentSource=VOICE", "mutationAllowed=false",
                    "state=AWAIT_QUICK_REGISTRATION_NAME", "intent=GUEST_REGISTRATION_CONSENTED");

            var repeated = service.continueTurn(context, "Yes, please.");
            assertThat(repeated.response()).contains("पहला नाम")
                    .doesNotContain("क्या मैं आपका basic registration बना दूँ");
            assertThat(repeated.intent()).isEqualTo("GUEST_COLLECTING_DETAILS");
            assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.COLLECTING_DETAILS);
            assertThat(state.get().capturedFirstName()).isNull();
            verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
            verify(gateway, never()).execute(any(), any(), any());
        }
    }

    @Test
    void noMatchBookingRequiresConsentNameAndAgeBeforeCanonicalPatientCreateBeforeGrant() {
        HealthcareAiReadOnlyToolGateway gateway = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceTransactionAuthProperties properties = guestEnabledProperties();
        VoiceReadOnlyAivaService service = service(gateway, adapter, patients, properties);
        VoiceReadOnlyAivaService.Context context = noMatchContext("guest-call-a");
        AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
        when(adapter.voiceAuthenticationState(any())).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any())).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
        when(adapter.continueTurn(any(AivaExecutionContext.class), eq("book appointment"), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
        UUID createdId = UUID.randomUUID();
        PatientRecord created = patientRecord(createdId, "Asha", "Rao");
        when(patients.findActiveByTenantAndMobile(TENANT, "9876543210")).thenReturn(List.of());
        when(patients.createVoiceQuickRegistration(eq(TENANT), any(PatientUpsertCommand.class), eq(INTERACTION), any()))
                .thenReturn(created);

        var offer = service.continueTurn(context, "book appointment");
        assertThat(offer.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(offer.response()).contains("क्या मैं आपका basic registration बना दूँ");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CONSENT_REQUESTED);
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());

        var consent = service.continueTurn(context, "हाँ");
        assertThat(consent.response()).contains("पहला नाम");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.COLLECTING_DETAILS);

        var name = service.continueTurn(context, "Asha");
        assertThat(name.response()).contains("उम्र");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.AWAITING_AGE);
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());

        var registration = service.continueTurn(context, "42");
        assertThat(registration.intent()).isEqualTo("GUEST_REGISTRATION_CREATED");
        assertThat(registration.mutationBlocked()).isTrue();
        assertThat(registration.response()).contains("आपका नाम रिकॉर्ड हो गया है");
        assertThat(registration.runtimeDiagnostics().toString()).contains("BOOK_APPOINTMENT", "authenticated=false")
                .contains("REGISTERED_UNVERIFIED", "phoneBindingVerified=false", "personalDataAllowed=false")
                .contains("restrictedBookingAllowed=true", "otpVerified=false", "careSession=false")
                .doesNotContain("9876543210", createdId.toString(), "authenticated=true", "OTP_VERIFIED");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CAPABILITY_GRANTED);
        assertThat(state.get().patientId()).isEqualTo(createdId);
        assertThat(state.get().guestBookingCapabilityGranted()).isTrue();
        assertThat(state.get().capturedFirstName()).isEqualTo("Asha");
        assertThat(state.get().capturedAgeYears()).isEqualTo(42);
        assertThat(state.get().consentTimestamp()).isNotNull();

        ArgumentCaptor<PatientUpsertCommand> command = ArgumentCaptor.forClass(PatientUpsertCommand.class);
        verify(patients).createVoiceQuickRegistration(eq(TENANT), command.capture(), eq(INTERACTION), any());
        assertThat(command.getValue().firstName()).isEqualTo("Asha");
        assertThat(command.getValue().lastName()).isNull();
        assertThat(command.getValue().gender()).isEqualTo(PatientGender.UNKNOWN);
        assertThat(command.getValue().mobile()).isEqualTo("9876543210");
        assertThat(command.getValue().dateOfBirth()).isNull();
        assertThat(command.getValue().ageYears()).isEqualTo(42);
        verify(gateway, never()).execute(any(), any(), any());
    }

    @Test
    void noConsentAndDuplicateDetectionNeverCreateAnotherPatient() {
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                patients, guestEnabledProperties());
        VoiceReadOnlyAivaService.Context context = noMatchContext("guest-call-b");
        AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
        when(adapter.voiceAuthenticationState(any())).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any())).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
        when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

        service.continueTurn(context, "book appointment");
        var declined = service.continueTurn(context, "नहीं");
        assertThat(declined.intent()).isEqualTo("GUEST_REGISTRATION_DECLINED");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.DECLINED);
        verify(patients, never()).findActiveByTenantAndMobile(any(), any());
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());

        var bookingAfterDecline = service.continueTurn(context, "book appointment");
        assertThat(bookingAfterDecline.response()).doesNotContain("basic registration बना दूँ");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.DECLINED);
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());

        state.set(null);
        service.continueTurn(context, "book appointment");
        var englishDeclined = service.continueTurn(context, "No, thank you.");
        assertThat(englishDeclined.intent()).isEqualTo("GUEST_REGISTRATION_DECLINED");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.DECLINED);
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());

        state.set(null);
        service.continueTurn(context, "book appointment");
        service.continueTurn(context, "हाँ");
        service.continueTurn(context, "New Patient");
        when(patients.findActiveByTenantAndMobile(TENANT, "9876543210"))
                .thenReturn(List.of(patientRecord(UUID.randomUUID(), "Existing", "Patient")));
        var duplicate = service.continueTurn(context, "40");
        assertThat(duplicate.intent()).isEqualTo("EXISTING_PATIENT_REUSED");
        assertThat(duplicate.response()).doesNotContain("OTP", "verify", "verification");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CAPABILITY_GRANTED);
        assertThat(state.get().patientId()).isNotNull();
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
    }

    @Test
    void dedicatedQuickRegistrationPolicyEnablesEntry() {
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceTransactionAuthProperties legacy = new VoiceTransactionAuthProperties();
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(true);
        operator.setOtpRequired(false);
        legacy.getDefaultPolicy().setVoiceQuickRegistrationEnabled(true);
        VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                patients, legacy, operator);
        VoiceReadOnlyAivaService.Context context = noMatchContext("guest-simple-policy");
        AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
        when(adapter.voiceAuthenticationState(any())).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any())).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
        when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
        when(patients.findActiveByTenantAndMobile(TENANT, "9876543210")).thenReturn(List.of());
        PatientRecord created = patientRecord(UUID.randomUUID(), "Jane", "Doe");
        when(patients.createVoiceQuickRegistration(eq(TENANT), any(), eq(INTERACTION), any())).thenReturn(created);

        service.continueTurn(context, "book appointment");
        service.continueTurn(context, "yes");
        service.continueTurn(context, "Jane");
        var registered = service.continueTurn(context, "35");

        assertThat(registered.intent()).isEqualTo("GUEST_REGISTRATION_CREATED");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CAPABILITY_GRANTED);
        verify(patients).createVoiceQuickRegistration(eq(TENANT), any(), eq(INTERACTION), any());
    }

    @Test
    void guestPolicyDefaultsDisabledForNoMatchBooking() {
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                patients, new VoiceTransactionAuthProperties());
        VoiceReadOnlyAivaService.Context context = noMatchContext("guest-call-disabled");
        when(adapter.voiceAuthenticationState(any())).thenReturn(null);
        when(adapter.voiceGuestRegistrationState(any())).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any())).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
        when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

        var turn = service.continueTurn(context, "book appointment");

        assertThat(turn.response()).doesNotContain("create a basic patient record");
        verify(adapter, never()).updateVoiceGuestRegistrationState(any(), any());
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
    }

    @Test
    void persistedHindiModeWinsOverEnglishTurnLanguageDuringRegistration() {
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                patients, guestEnabledProperties());
        VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context("guest-hindi-sticky",
                INTERACTION, TENANT, UUID.randomUUID(), null, null, "en-IN", "NO_MATCH", "GUEST_CALLER",
                null, "+919876543210");
        AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
        when(adapter.presentationMode(any())).thenReturn(
                com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.HINDI);
        when(adapter.voiceAuthenticationState(any())).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any())).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
        when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
        when(patients.findActiveByTenantAndMobile(TENANT, "9876543210")).thenReturn(List.of());
        PatientRecord created = patientRecord(UUID.randomUUID(), "Ashish", null);
        when(patients.createVoiceQuickRegistration(eq(TENANT), any(), eq(INTERACTION), any())).thenReturn(created);

        var prompt = service.continueTurn(context, "book appointment");

        assertThat(prompt.response()).contains("इस नंबर से कोई patient record नहीं मिला")
                .doesNotContain("I couldn't find");
        assertThat(prompt.runtimeDiagnostics().toString()).contains("HINDI", "hi-IN");
        var consented = service.continueTurn(context, "yes");
        assertThat(consented.response()).contains("पहला नाम").doesNotContain("Please tell me");
        assertThat(consented.runtimeDiagnostics().toString()).contains("HINDI", "hi-IN");
        var age = service.continueTurn(context, "Ashish");
        assertThat(age.response()).contains("उम्र").doesNotContain("How old");
        assertThat(age.runtimeDiagnostics().toString()).contains("HINDI", "hi-IN");
        var completed = service.continueTurn(context, "42");
        assertThat(completed.response()).contains("आपका नाम रिकॉर्ड हो गया है")
                .doesNotContain("Your name has been recorded");
        assertThat(completed.runtimeDiagnostics().toString()).contains("HINDI", "hi-IN");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CAPABILITY_GRANTED);
        assertThat(state.get().capturedFirstName()).isEqualTo("Ashish");
        assertThat(state.get().capturedAgeYears()).isEqualTo(42);
        verify(patients).createVoiceQuickRegistration(eq(TENANT), any(), eq(INTERACTION), any());
    }

    @Test
    void guestDoctorAndSlotDiscoveryNeverStartsRegistration() {
        for (String intent : List.of("FIND_DOCTOR", "FIND_SLOTS")) {
            AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
            PatientService patients = mock(PatientService.class);
            VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                    patients, guestEnabledProperties());
            VoiceReadOnlyAivaService.Context context = noMatchContext("guest-discovery-" + intent);
            when(adapter.voiceAuthenticationState(any())).thenReturn(null);
            when(adapter.voiceGuestRegistrationState(any())).thenReturn(null);
            when(adapter.currentVoiceAuthorization(any())).thenReturn(
                    AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
            when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                    .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("discovery", intent, intent,
                            true, false, false, null, 0, "READ_ONLY_ALLOWED", false, null));

            service.continueTurn(context, "कल कौन से डॉक्टर available हैं?");

            verify(adapter, never()).updateVoiceGuestRegistrationState(any(), any());
            verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
        }
    }

    @Test
    void uncertainConsentAndInvalidAgeNeverCreatePatient() {
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                patients, guestEnabledProperties());
        VoiceReadOnlyAivaService.Context context = noMatchContext("guest-uncertain-age");
        AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
        when(adapter.voiceAuthenticationState(any())).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any())).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
        when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

        service.continueTurn(context, "book appointment");
        var uncertain = service.continueTurn(context, "maybe");
        assertThat(uncertain.intent()).isEqualTo("GUEST_REGISTRATION_CONSENT");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CONSENT_REQUESTED);
        assertThat(uncertain.response()).contains("मुझे स्पष्ट नहीं हुआ")
                .doesNotContain("इस नंबर से कोई patient record नहीं मिला");
        assertThat(uncertain.runtimeDiagnostics().toString()).contains("consentDecision=UNCLEAR",
                "consentPending=true", "consentPersisted=false", "namePromptIssued=false");
        service.continueTurn(context, "yes");
        service.continueTurn(context, "Ashish");
        var invalidAge = service.continueTurn(context, "121");

        assertThat(invalidAge.intent()).isEqualTo("GUEST_AGE_CLARIFICATION");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.AWAITING_AGE);
        assertThat(state.get().capturedFirstName()).isEqualTo("Ashish");
        assertThat(state.get().consentTimestamp()).isNotNull();
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
    }

    @Test
    void ageParserAcceptsWholeYearPhrasesAndRejectsInvalidOrFractionalAge() {
        for (String validAge : List.of("15.", "20.", "20", "20 years", "I am 20", "I'm 20",
                "twenty", "twenty years old", "20 साल", "मेरी उम्र 20 है", "बीस", "बीस साल")) {
            AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
            PatientService patients = mock(PatientService.class);
            VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                    patients, guestEnabledProperties());
            VoiceReadOnlyAivaService.Context context = noMatchContext("guest-valid-age-" + validAge.hashCode());
            AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
            when(adapter.voiceAuthenticationState(any())).thenReturn(null);
            when(adapter.currentVoiceAuthorization(any())).thenReturn(
                    AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
            when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                    .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                            "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
            when(patients.findActiveByTenantAndMobile(TENANT, "9876543210")).thenReturn(List.of());
            UUID id = UUID.randomUUID();
            when(patients.createVoiceQuickRegistration(eq(TENANT), any(PatientUpsertCommand.class), eq(INTERACTION), any()))
                    .thenReturn(patientRecord(id, "Ashish", null));

            service.continueTurn(context, "book appointment");
            service.continueTurn(context, "yes please create");
            service.continueTurn(context, "Ashish");
            assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.AWAITING_AGE);
            var result = service.continueTurn(context, validAge);
            int expectedAge = validAge.startsWith("15") ? 15 : 20;
            assertThat(result.intent()).isEqualTo("GUEST_REGISTRATION_CREATED");
            assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CAPABILITY_GRANTED);
            assertThat(state.get().capturedAgeYears()).isEqualTo(expectedAge);
            assertThat(result.runtimeDiagnostics().toString()).contains("ageParsed=" + expectedAge, "ageValid=true",
                    "guestRegistrationStateBefore=AWAIT_QUICK_REGISTRATION_AGE",
                    "guestRegistrationStateAfter=REGISTERED_UNVERIFIED", "duplicateRecheckResult=ZERO_MATCHES_CREATED");
            verify(patients).findActiveByTenantAndMobile(TENANT, "9876543210");
            verify(patients).createVoiceQuickRegistration(eq(TENANT), any(PatientUpsertCommand.class), eq(INTERACTION), any());
        }
    }

    @Test
    void invalidAgeFormsNeverCreateOrGrantAndNaturalCreateConsentIsSafe() {
        for (String invalidAge : List.of("1515.", "121", "-1", "20.5")) {
            AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
            PatientService patients = mock(PatientService.class);
            VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                    patients, guestEnabledProperties());
            VoiceReadOnlyAivaService.Context context = noMatchContext("guest-invalid-age-" + invalidAge);
            AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
            when(adapter.voiceAuthenticationState(any())).thenReturn(null);
            when(adapter.currentVoiceAuthorization(any())).thenReturn(
                    AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
            when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                    .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                            "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
            service.continueTurn(context, "book appointment");
            service.continueTurn(context, "yes");
            service.continueTurn(context, "Ashish");
            var result = service.continueTurn(context, invalidAge);
            assertThat(result.intent()).isEqualTo("GUEST_AGE_CLARIFICATION");
            assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.AWAITING_AGE);
            assertThat(state.get().guestBookingCapabilityGranted()).isFalse();
            assertThat(result.runtimeDiagnostics().toString()).contains("ageValid=false", "capturedAgePresent=false");
            verify(patients, never()).findActiveByTenantAndMobile(any(), any());
            verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
        }

        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                patients, guestEnabledProperties());
        VoiceReadOnlyAivaService.Context context = noMatchContext("guest-negative-natural-consent");
        AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
        when(adapter.voiceAuthenticationState(any())).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any())).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
        when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
        service.continueTurn(context, "book appointment");
        var declined = service.continueTurn(context, "don't create");
        assertThat(declined.intent()).isEqualTo("GUEST_REGISTRATION_DECLINED");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.DECLINED);
        verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
    }

    @Test
    void invalidNameAndAbandonmentBeforeNameOrAgeNeverCreatePatient() {
        for (boolean captureName : List.of(false, true)) {
            AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
            PatientService patients = mock(PatientService.class);
            VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                    patients, guestEnabledProperties());
            VoiceReadOnlyAivaService.Context context = noMatchContext("guest-abandoned-" + captureName);
            AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
            when(adapter.voiceAuthenticationState(any())).thenReturn(null);
            when(adapter.currentVoiceAuthorization(any())).thenReturn(
                    AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
            when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                    .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                            "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

            service.continueTurn(context, "book appointment");
            service.continueTurn(context, "yes");
            if (captureName) {
                service.continueTurn(context, "Ashish");
                assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.AWAITING_AGE);
                service.continueTurn(context, ""); // Caller abandons before supplying a valid age.
                assertThat(state.get().capturedFirstName()).isEqualTo("Ashish");
            } else {
                assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.COLLECTING_DETAILS);
                service.continueTurn(context, "   "); // Invalid/empty name, then caller abandons.
                assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.COLLECTING_DETAILS);
            }
            assertThat(state.get().patientId()).isNull();
            assertThat(state.get().guestBookingCapabilityGranted()).isFalse();
            verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
        }
    }

    @Test
    void createFailureDoesNotRetainPatientOrGrantBookingCapability() {
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                patients, guestEnabledProperties());
        VoiceReadOnlyAivaService.Context context = noMatchContext("guest-create-failure");
        AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
        when(adapter.voiceAuthenticationState(any())).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any())).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
        when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
        when(patients.findActiveByTenantAndMobile(TENANT, "9876543210")).thenReturn(List.of());
        when(patients.createVoiceQuickRegistration(eq(TENANT), any(), eq(INTERACTION), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        service.continueTurn(context, "book appointment");
        service.continueTurn(context, "yes");
        service.continueTurn(context, "Ashish");
        var failed = service.continueTurn(context, "42");

        assertThat(failed.intent()).isEqualTo("GUEST_REGISTRATION_FAILED");
        assertThat(failed.response()).doesNotContain("आपका नाम रिकॉर्ड हो गया है");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.FAILED);
        assertThat(state.get().patientId()).isNull();
        assertThat(state.get().guestBookingCapabilityGranted()).isFalse();
        verify(patients).createVoiceQuickRegistration(eq(TENANT), any(), eq(INTERACTION), any());
    }

    @Test
    void createConflictRecheckReusesOnlyExactlyOneMatch() {
        for (List<PatientRecord> matchesAfterConflict : List.<List<PatientRecord>>of(
                List.of(patientRecord(UUID.randomUUID(), "Asha", "One")), List.<PatientRecord>of(),
                List.of(patientRecord(UUID.randomUUID(), "Asha", "One"),
                        patientRecord(UUID.randomUUID(), "Asha", "Two")))) {
            AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
            PatientService patients = mock(PatientService.class);
            VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                    patients, guestEnabledProperties());
            VoiceReadOnlyAivaService.Context context = noMatchContext("guest-conflict-recheck-" + matchesAfterConflict.size());
            AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
            when(adapter.voiceAuthenticationState(any())).thenReturn(null);
            when(adapter.currentVoiceAuthorization(any())).thenReturn(
                    AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
            when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                    .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                            "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
            when(patients.findActiveByTenantAndMobile(TENANT, "9876543210"))
                    .thenReturn(List.of()).thenReturn(matchesAfterConflict);
            when(patients.createVoiceQuickRegistration(eq(TENANT), any(), eq(INTERACTION), any()))
                    .thenThrow(new com.deepthoughtnet.clinic.patient.service.model.PatientConflictException("raced"));

            service.continueTurn(context, "book appointment");
            service.continueTurn(context, "yes");
            service.continueTurn(context, "Ashish");
            var result = service.continueTurn(context, "42");

            if (matchesAfterConflict.size() == 1) {
                assertThat(result.intent()).isEqualTo("EXISTING_PATIENT_REUSED");
                assertThat(state.get().patientId()).isEqualTo(matchesAfterConflict.getFirst().id());
                assertThat(state.get().guestBookingCapabilityGranted()).isTrue();
            } else {
                assertThat(result.intent()).isEqualTo("GUEST_DUPLICATE_CHECK_FAILED");
                assertThat(state.get().patientId()).isNull();
                assertThat(state.get().guestBookingCapabilityGranted()).isFalse();
            }
            verify(patients).createVoiceQuickRegistration(eq(TENANT), any(), eq(INTERACTION), any());
        }
    }

    @Test
    void guestRegistrationIsRestrictedToNoMatchBookingIntent() {
        for (String identification : List.of("SINGLE_MATCH", "MULTIPLE_MATCHES", "LOOKUP_FAILED")) {
            for (String intent : List.of("BOOK_APPOINTMENT", "FIND_APPOINTMENTS", "CANCEL_APPOINTMENT",
                    "RESCHEDULE_APPOINTMENT")) {
                AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
                PatientService patients = mock(PatientService.class);
                VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                        patients, guestEnabledProperties());
                UUID identified = "SINGLE_MATCH".equals(identification) ? UUID.randomUUID() : null;
                VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context(
                        "guest-ineligible-" + identification + "-" + intent, INTERACTION, TENANT, UUID.randomUUID(),
                        null, null, "hi-IN", identification, "CALLER", identified, "+919876543210");
                when(adapter.voiceAuthenticationState(any())).thenReturn(null);
                when(adapter.voiceGuestRegistrationState(any())).thenReturn(null);
                when(adapter.currentVoiceAuthorization(any())).thenReturn(
                        AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), identified));
                when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                        .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", intent, intent,
                                true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

                var result = service.continueTurn(context, "transaction request");

                assertThat(result.response()).as(identification + "/" + intent)
                        .doesNotContain("create a basic patient record");
                verify(adapter, never()).updateVoiceGuestRegistrationState(any(), any());
                verify(patients, never()).findActiveByTenantAndMobile(any(), any());
                verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
            }
        }
    }

    @Test
    void ambiguousDuplicateOrDuplicateLookupFailureNeverCreatesPatientOrGrantsCapability() {
        for (boolean lookupFails : List.of(false, true)) {
            AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
            PatientService patients = mock(PatientService.class);
            VoiceReadOnlyAivaService service = service(mock(HealthcareAiReadOnlyToolGateway.class), adapter,
                    patients, guestEnabledProperties());
            VoiceReadOnlyAivaService.Context context = noMatchContext("guest-duplicate-ambiguous-" + lookupFails);
            AtomicReference<VoiceGuestRegistrationState> state = installGuestState(adapter);
            when(adapter.voiceAuthenticationState(any())).thenReturn(null);
            when(adapter.currentVoiceAuthorization(any())).thenReturn(
                    AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), null));
            when(adapter.continueTurn(any(AivaExecutionContext.class), any(), any()))
                    .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                            "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
            if (lookupFails) {
                when(patients.findActiveByTenantAndMobile(TENANT, "9876543210"))
                        .thenThrow(new IllegalStateException("lookup unavailable"));
            } else {
                when(patients.findActiveByTenantAndMobile(TENANT, "9876543210"))
                        .thenReturn(List.of(patientRecord(UUID.randomUUID(), "A", "One"),
                                patientRecord(UUID.randomUUID(), "B", "Two")));
            }

            service.continueTurn(context, "book appointment");
            service.continueTurn(context, "yes");
            service.continueTurn(context, "New Patient");
            var result = service.continueTurn(context, "42");

            assertThat(result.intent()).isEqualTo(lookupFails
                    ? "GUEST_DUPLICATE_CHECK_FAILED" : "GUEST_DUPLICATE_AMBIGUOUS");
            assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.FAILED);
            assertThat(state.get().guestBookingCapabilityGranted()).isFalse();
            verify(patients, never()).createVoiceQuickRegistration(any(), any(), any(), any());
        }
    }

    private VoiceReadOnlyAivaService service(HealthcareAiReadOnlyToolGateway gateway,
            AivaV2VoiceConversationAdapter adapter, PatientService patients, VoiceTransactionAuthProperties properties) {
        return service(gateway, adapter, patients, properties, new VoiceOperatorProperties());
    }

    private VoiceReadOnlyAivaService service(HealthcareAiReadOnlyToolGateway gateway,
            AivaV2VoiceConversationAdapter adapter, PatientService patients, VoiceTransactionAuthProperties properties,
            VoiceOperatorProperties operator) {
        when(adapter.presentationMode(any())).thenReturn(
                com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.HINDI);
        return new VoiceReadOnlyAivaService(gateway, adapter, new VoiceTestProperties(), null,
                mock(PatientPortalOtpService.class), new VoiceTransactionPolicyResolver(properties, operator), patients);
    }

    private VoiceTransactionAuthProperties guestEnabledProperties() {
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setBookingEnabled(true);
        properties.getDefaultPolicy().setVoiceQuickRegistrationEnabled(true);
        properties.getDefaultPolicy().setMinimalRegistrationEnabled(true);
        return properties;
    }

    private VoiceReadOnlyAivaService.Context noMatchContext(String callId) {
        return new VoiceReadOnlyAivaService.Context(callId, INTERACTION, TENANT, UUID.randomUUID(), null, null,
                "hi-IN", "NO_MATCH", "GUEST_CALLER", null, "+919876543210");
    }

    private AtomicReference<VoiceGuestRegistrationState> installGuestState(AivaV2VoiceConversationAdapter adapter) {
        AtomicReference<VoiceGuestRegistrationState> reference = new AtomicReference<>();
        when(adapter.voiceGuestRegistrationState(any())).thenAnswer(ignored -> reference.get());
        doAnswer(invocation -> {
            reference.set(invocation.getArgument(1));
            return null;
        }).when(adapter).updateVoiceGuestRegistrationState(any(), any());
        return reference;
    }

    private PatientRecord patientRecord(UUID patientId, String firstName, String lastName) {
        OffsetDateTime now = OffsetDateTime.now();
        return new PatientRecord(patientId, TENANT, "PAT-TEST", firstName, lastName, PatientGender.UNKNOWN,
                null, null, "9876543210", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, true, now, now);
    }
}
