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
    void noMatchBookingRequiresConsentMobileConfirmationAndCanonicalPatientCreateBeforeGrant() {
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
        when(patients.createVoiceGuest(eq(TENANT), any(PatientUpsertCommand.class), eq(INTERACTION))).thenReturn(created);

        var offer = service.continueTurn(context, "book appointment");
        assertThat(offer.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(offer.response()).contains("क्या मैं आगे बढ़ूँ");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CONSENT_REQUESTED);
        verify(patients, never()).createVoiceGuest(any(), any(), any());

        var consent = service.continueTurn(context, "हाँ");
        assertThat(consent.response()).contains("mobile number");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.MOBILE_CONFIRMATION_REQUESTED);

        var mobile = service.continueTurn(context, "हाँ");
        assertThat(mobile.response()).contains("पूरा नाम");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.COLLECTING_DETAILS);
        verify(patients, never()).createVoiceGuest(any(), any(), any());

        var registration = service.continueTurn(context, "Asha Rao");
        assertThat(registration.intent()).isEqualTo("GUEST_REGISTRATION_CREATED");
        assertThat(registration.mutationBlocked()).isTrue();
        assertThat(registration.response()).contains("अभी appointment book नहीं हुई");
        assertThat(registration.runtimeDiagnostics().toString()).contains("GUEST_BOOK_APPOINTMENT", "authenticated=false")
                .doesNotContain("9876543210", createdId.toString());
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CAPABILITY_GRANTED);
        assertThat(state.get().patientId()).isEqualTo(createdId);
        assertThat(state.get().guestBookingCapabilityGranted()).isTrue();

        ArgumentCaptor<PatientUpsertCommand> command = ArgumentCaptor.forClass(PatientUpsertCommand.class);
        verify(patients).createVoiceGuest(eq(TENANT), command.capture(), eq(INTERACTION));
        assertThat(command.getValue().firstName()).isEqualTo("Asha");
        assertThat(command.getValue().lastName()).isEqualTo("Rao");
        assertThat(command.getValue().gender()).isEqualTo(PatientGender.UNKNOWN);
        assertThat(command.getValue().mobile()).isEqualTo("9876543210");
        assertThat(command.getValue().dateOfBirth()).isNull();
        assertThat(command.getValue().ageYears()).isNull();
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
        verify(patients, never()).findActiveByTenantAndMobile(any(), any());
        verify(patients, never()).createVoiceGuest(any(), any(), any());

        state.set(null);
        service.continueTurn(context, "book appointment");
        service.continueTurn(context, "हाँ");
        service.continueTurn(context, "हाँ");
        when(patients.findActiveByTenantAndMobile(TENANT, "9876543210"))
                .thenReturn(List.of(patientRecord(UUID.randomUUID(), "Existing", "Patient")));
        var duplicate = service.continueTurn(context, "New Person");
        assertThat(duplicate.intent()).isEqualTo("EXISTING_PATIENT_FOUND");
        assertThat(duplicate.response()).doesNotContain("OTP", "verify", "verification");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.FAILED);
        verify(patients, never()).createVoiceGuest(any(), any(), any());
    }

    @Test
    void threeSimpleOperatorFlagsAreSufficientForGuestRegistrationEntry() {
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientService patients = mock(PatientService.class);
        VoiceTransactionAuthProperties legacy = new VoiceTransactionAuthProperties();
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(true);
        operator.setOtpRequired(false);
        operator.setGuestBookingEnabled(true);
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
        when(patients.createVoiceGuest(eq(TENANT), any(), eq(INTERACTION))).thenReturn(created);

        service.continueTurn(context, "book appointment");
        service.continueTurn(context, "yes");
        service.continueTurn(context, "yes");
        var registered = service.continueTurn(context, "Jane Doe");

        assertThat(registered.intent()).isEqualTo("GUEST_REGISTRATION_CREATED");
        assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.CAPABILITY_GRANTED);
        verify(patients).createVoiceGuest(eq(TENANT), any(), eq(INTERACTION));
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
        verify(patients, never()).createVoiceGuest(any(), any(), any());
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
                verify(patients, never()).createVoiceGuest(any(), any(), any());
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
            service.continueTurn(context, "yes");
            var result = service.continueTurn(context, "New Patient");

            assertThat(result.intent()).isEqualTo(lookupFails
                    ? "GUEST_DUPLICATE_CHECK_FAILED" : "GUEST_DUPLICATE_AMBIGUOUS");
            assertThat(state.get().status()).isEqualTo(VoiceGuestRegistrationStatus.FAILED);
            assertThat(state.get().guestBookingCapabilityGranted()).isFalse();
            verify(patients, never()).createVoiceGuest(any(), any(), any());
        }
    }

    private VoiceReadOnlyAivaService service(HealthcareAiReadOnlyToolGateway gateway,
            AivaV2VoiceConversationAdapter adapter, PatientService patients, VoiceTransactionAuthProperties properties) {
        return service(gateway, adapter, patients, properties, new VoiceOperatorProperties());
    }

    private VoiceReadOnlyAivaService service(HealthcareAiReadOnlyToolGateway gateway,
            AivaV2VoiceConversationAdapter adapter, PatientService patients, VoiceTransactionAuthProperties properties,
            VoiceOperatorProperties operator) {
        return new VoiceReadOnlyAivaService(gateway, adapter, new VoiceTestProperties(), null,
                mock(PatientPortalOtpService.class), new VoiceTransactionPolicyResolver(properties, operator), patients);
    }

    private VoiceTransactionAuthProperties guestEnabledProperties() {
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setBookingEnabled(true);
        properties.getDefaultPolicy().setGuestBookingEnabled(true);
        properties.getDefaultPolicy().setMinimalRegistrationEnabled(true);
        properties.getDefaultPolicy().setGuestBookingRequiresMobileConfirmation(true);
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
