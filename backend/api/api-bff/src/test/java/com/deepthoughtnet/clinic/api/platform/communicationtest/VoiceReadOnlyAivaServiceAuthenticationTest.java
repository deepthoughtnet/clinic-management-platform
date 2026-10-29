package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaTransactionAuthorization;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceConversationAdapter;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceAuthenticationState;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalOtpService;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class VoiceReadOnlyAivaServiceAuthenticationTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID INTERACTION = UUID.randomUUID();

    @Test
    void currentReleaseDoesNotConsultHistoricalVoiceOtpEvidence() {
        PatientPortalOtpService otp = mock(PatientPortalOtpService.class);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setTrustMode(VoiceTransactionAuthPolicy.TrustMode.PREVERIFIED_PHONE_ALLOWED);
        VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(
                mock(HealthcareAiReadOnlyToolGateway.class), mock(AivaV2VoiceConversationAdapter.class),
                new VoiceTestProperties(), null, otp, new VoiceTransactionPolicyResolver(properties));
        VoiceIdentityTrust verified = service.assessIdentityTrust(TENANT, null, PATIENT, "+919876543210", "call-a");
        VoiceIdentityTrust changedPhone = service.assessIdentityTrust(TENANT, null, PATIENT, "+919000000000", "call-a");
        VoiceIdentityTrust guest = service.assessIdentityTrust(TENANT, null, null, "+919876543210", "call-a");

        assertThat(verified.identityState()).isEqualTo(VoiceIdentityTrust.IdentityState.REGISTERED_UNVERIFIED);
        assertThat(verified.trustSource()).isEqualTo(VoiceIdentityTrust.TrustSource.NONE);
        assertThat(verified.phoneBindingVerified()).isFalse();
        assertThat(changedPhone.identityState()).isEqualTo(VoiceIdentityTrust.IdentityState.REGISTERED_UNVERIFIED);
        assertThat(changedPhone.trustSource()).isEqualTo(VoiceIdentityTrust.TrustSource.NONE);
        assertThat(guest.identityState()).isEqualTo(VoiceIdentityTrust.IdentityState.GUEST);
        verifyNoInteractions(otp);
    }

    @Test
    void registeredPatientWithoutBindingRemainsRestrictedAndDisabledPolicyDoesNotQueryEvidence() {
        PatientPortalOtpService otp = mock(PatientPortalOtpService.class);
        VoiceReadOnlyAivaService disabled = new VoiceReadOnlyAivaService(
                mock(HealthcareAiReadOnlyToolGateway.class), mock(AivaV2VoiceConversationAdapter.class),
                new VoiceTestProperties(), null, otp, new VoiceTransactionPolicyResolver(new VoiceTransactionAuthProperties()));
        assertThat(disabled.assessIdentityTrust(TENANT, null, PATIENT, "+919876543210", "call-a").identityState())
                .isEqualTo(VoiceIdentityTrust.IdentityState.REGISTERED_UNVERIFIED);
        verifyNoInteractions(otp);

        VoiceIdentityTrust unverified = VoiceIdentityTrust.registeredUnverified();
        assertThat(unverified.phoneBindingVerified()).isFalse();
        assertThat(unverified.trustSource()).isEqualTo(VoiceIdentityTrust.TrustSource.NONE);
    }

    @Test
    void unsupportedProtectedVoiceActionStopsBeforeGatewayOrPatientDataTools() {
        HealthcareAiReadOnlyToolGateway tools = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientPortalOtpService otp = mock(PatientPortalOtpService.class);
        VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(tools, adapter,
                new VoiceTestProperties(), null, otp, new VoiceTransactionPolicyResolver(new VoiceTransactionAuthProperties()));
        VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context("call-protected", INTERACTION,
                TENANT, UUID.randomUUID(), null, null, "hi-IN", "SINGLE_MATCH", "IDENTIFIED_PATIENT", PATIENT,
                "+919876543210");
        when(adapter.unsupportedVoiceScopeRequest(any(AivaExecutionContext.class), eq("meri reports batao")))
                .thenReturn(new AivaV2VoiceConversationAdapter.VoiceScopeBoundaryResult(
                        "PATIENT_DATA_NOT_SUPPORTED", "Reports dekhne ke liye please Care use kijiye."));

        var result = service.continueTurn(context, "meri reports batao");

        assertThat(result.intent()).isEqualTo("PATIENT_DATA_NOT_SUPPORTED");
        assertThat(result.response()).contains("Care");
        verify(adapter, never()).continueTurn(any(AivaExecutionContext.class), any(), any());
        verify(tools, never()).execute(any(), any(), any());
        verifyNoInteractions(otp);
    }

    @Test
    void preverifiedPhonePolicyDoesNotStartOtpOrEnableVoiceBookingMutation() {
        HealthcareAiReadOnlyToolGateway tools = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientPortalOtpService otp = mock(PatientPortalOtpService.class);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setTrustMode(VoiceTransactionAuthPolicy.TrustMode.PREVERIFIED_PHONE_ALLOWED);
        VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(tools, adapter,
                new VoiceTestProperties(), null, otp, new VoiceTransactionPolicyResolver(properties));
        VoiceIdentityTrust preverifiedTrust = VoiceIdentityTrust.registeredPhoneVerified(TENANT, PATIENT,
                "+919876543210", "preverified-call");
        VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context("preverified-call", INTERACTION,
                TENANT, UUID.randomUUID(), null, null, "hi-IN", "SINGLE_MATCH", "IDENTIFIED_PATIENT", PATIENT,
                "+919876543210", preverifiedTrust);
        when(adapter.voiceAuthenticationState(any(AivaExecutionContext.class))).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any(AivaExecutionContext.class))).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), PATIENT));
        when(adapter.continueTurn(any(AivaExecutionContext.class), eq("book appointment"), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

        var turn = service.continueTurn(context, "book appointment");

        assertThat(turn.mutationBlocked()).isTrue();
        assertThat(turn.runtimeDiagnostics().get("voiceIdentity").toString())
                .contains("REGISTERED_PHONE_VERIFIED", "PREVERIFIED_PHONE");
        assertThat(turn.runtimeDiagnostics().get("voiceTransactionPolicy").toString())
                .contains("PREVERIFIED_PHONE_ALLOWED", "transactionFeatureEnabled=false");
        verify(otp, never()).startVoiceChallenge(any(), any(), any(), any(), any());
        verify(tools, never()).execute(any(), any(), any());
    }

    @Test
    void otpRequiredCompatibilityFlagNeverStartsOrVerifiesVoiceOtp() {
        HealthcareAiReadOnlyToolGateway tools = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientPortalOtpService otp = mock(PatientPortalOtpService.class);
        VoiceTransactionAuthProperties policyProperties = new VoiceTransactionAuthProperties();
        policyProperties.getDefaultPolicy().setEnabled(true);
        policyProperties.getDefaultPolicy().setBookingEnabled(true);
        policyProperties.getDefaultPolicy().setAuthenticationMode(VoiceTransactionAuthPolicy.AuthenticationMode.OTP_REQUIRED);
        VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(tools, adapter,
                new VoiceTestProperties(), null, otp, new VoiceTransactionPolicyResolver(policyProperties));
        VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context("call-voice-a", INTERACTION,
                TENANT, UUID.randomUUID(), null, null, "hi-IN", "SINGLE_MATCH", "IDENTIFIED_PATIENT", PATIENT,
                "+919876543210");
        when(adapter.voiceAuthenticationState(any(AivaExecutionContext.class))).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any(AivaExecutionContext.class))).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), PATIENT));
        when(adapter.continueTurn(any(AivaExecutionContext.class), eq("book appointment"), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT", "BOOK_APPOINTMENT", true,
                        true, false, null, 0, "BLOCKED_READ_ONLY", false, null));
        VoiceReadOnlyAivaService.TurnResult result = service.continueTurn(context, "book appointment");

        assertThat(result.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.response()).doesNotContain("OTP");
        verify(otp, never()).startVoiceChallenge(any(), any(), any(), any(), any());
        verify(otp, never()).verifyVoiceChallenge(any(), any(), any(), any(), any(), any(), any());
        verify(tools, never()).execute(any(), any(), any());
    }

    @Test
    void defaultDisabledPolicyNeverOffersOrStartsAChallenge() {
        HealthcareAiReadOnlyToolGateway tools = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientPortalOtpService otp = mock(PatientPortalOtpService.class);
        VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(tools, adapter,
                new VoiceTestProperties(), null, otp,
                new VoiceTransactionPolicyResolver(new VoiceTransactionAuthProperties()));
        VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context("call-disabled", INTERACTION,
                TENANT, UUID.randomUUID(), null, null, "hi-IN", "SINGLE_MATCH", "IDENTIFIED_PATIENT", PATIENT,
                "+919876543210");
        when(adapter.voiceAuthenticationState(any(AivaExecutionContext.class))).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any(AivaExecutionContext.class))).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), PATIENT));
        when(adapter.continueTurn(any(AivaExecutionContext.class), eq("book appointment"), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

        VoiceReadOnlyAivaService.TurnResult turn = service.continueTurn(context, "book appointment");

        assertThat(turn.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(turn.policyDecision()).isEqualTo("DENIED");
        assertThat(turn.response()).doesNotContain("OTP");
        assertThat(turn.runtimeDiagnostics().get("voiceTransactionPolicy").toString())
                .contains("AUTHENTICATION_DISABLED", "DISABLED", "transactionFeatureEnabled=false");
        verify(otp, never()).startVoiceChallenge(any(), any(), any(), any(), any());
        verify(adapter, never()).updateVoiceAuthenticationState(any(), any());
        verify(tools, never()).execute(any(), any(), any());
    }

    @Test
    void otpRequiredCompatibilityFlagDoesNotBlockRestrictedExistingPatientBooking() {
        HealthcareAiReadOnlyToolGateway tools = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        PatientPortalOtpService otp = mock(PatientPortalOtpService.class);
        com.deepthoughtnet.clinic.patient.service.PatientService patientService =
                mock(com.deepthoughtnet.clinic.patient.service.PatientService.class);
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(true);
        operator.setOtpRequired(true);
        operator.setGuestBookingEnabled(true);
        VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(tools, adapter,
                new VoiceTestProperties(), null, otp,
                new VoiceTransactionPolicyResolver(new VoiceTransactionAuthProperties(), operator), patientService);
        VoiceIdentityTrust unverified = VoiceIdentityTrust.registeredUnverified();
        VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context("call-unverified-booking",
                INTERACTION, TENANT, UUID.randomUUID(), null, null, "hi-IN", "SINGLE_MATCH", "IDENTIFIED_PATIENT",
                PATIENT, "+919876543210", unverified);
        when(adapter.voiceAuthenticationState(any(AivaExecutionContext.class))).thenReturn(null);
        when(adapter.voiceGuestRegistrationState(any(AivaExecutionContext.class))).thenReturn(null);
        when(adapter.currentVoiceAuthorization(any(AivaExecutionContext.class))).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), PATIENT));
        when(adapter.continueTurn(any(AivaExecutionContext.class), eq("book appointment"), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("booking remains protected",
                        "BOOK_APPOINTMENT", "BOOK_APPOINTMENT", true, true, false, null, 0,
                        "BLOCKED_READ_ONLY", false, null));

        VoiceReadOnlyAivaService.TurnResult result = service.continueTurn(context, "book appointment");

        assertThat(result.runtimeDiagnostics().get("voiceTransactionPolicy").toString())
                .contains("configuredOtpRequired=true", "otpRequired=false",
                        "effectiveOtpChallengeRequired=false", "bookingEnabled=true", "guestBookingEnabled=true");
        assertThat(result.runtimeDiagnostics().get("voiceIdentity").toString())
                .contains("REGISTERED_UNVERIFIED", "restrictedBookingAllowed=true", "personalDataAllowed=false");
        assertThat(result.runtimeDiagnostics().get("voiceIdentity").toString())
                .contains("identityState=IDENTIFIED_PATIENT", "assuranceState=REGISTERED_UNVERIFIED",
                        "conversationParty=IDENTIFIED_PATIENT", "patientIdPresent=true",
                        "callerPatientBinding=TENANT_SCOPED_CALLER_MATCH", "phoneBindingVerified=false");
        verify(otp, never()).startVoiceChallenge(any(), any(), any(), any(), any());
        verify(adapter, never()).updateVoiceAuthenticationState(any(), any());
        verifyNoInteractions(patientService);
        verify(tools, never()).execute(any(), any(), any());
    }

    @Test
    void courtesyCloseShortCircuitsBookingContextBeforeAnyVoiceToolOrExecution() {
        HealthcareAiReadOnlyToolGateway tools = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setBookingEnabled(true);
        VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(tools, adapter,
                new VoiceTestProperties(), null, mock(PatientPortalOtpService.class),
                new VoiceTransactionPolicyResolver(properties));
        VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context("call-thanks", INTERACTION,
                TENANT, UUID.randomUUID(), null, null, "en-IN", "SINGLE_MATCH", "IDENTIFIED_PATIENT", PATIENT,
                "+919876543210");
        when(adapter.isCourtesyClose(any(AivaExecutionContext.class), eq("Okay. Thank you."))).thenReturn(true);
        when(adapter.continueTurn(any(AivaExecutionContext.class), eq("Okay. Thank you."), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("Thank you. Take care.",
                        "END_CONVERSATION", null, false, false, false, null, 0,
                        "COURTESY_CLOSE", true, null, null, java.util.Map.of()));

        var result = service.continueTurn(context, "Okay. Thank you.");

        assertThat(result.intent()).isEqualTo("END_CONVERSATION");
        assertThat(result.closing()).isTrue();
        assertThat(result.toolExecuted()).isFalse();
        verify(tools, never()).execute(any(), any(), any());
        verify(adapter, never()).shouldRouteVoiceBookingTurn(any(AivaExecutionContext.class), any());
    }

    @Test
    void disabledPolicyRevokesExistingVoiceGrantBeforeTransactionRouting() {
        HealthcareAiReadOnlyToolGateway tools = mock(HealthcareAiReadOnlyToolGateway.class);
        AivaV2VoiceConversationAdapter adapter = mock(AivaV2VoiceConversationAdapter.class);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(tools, adapter,
                new VoiceTestProperties(), null, mock(PatientPortalOtpService.class),
                new VoiceTransactionPolicyResolver(properties));
        VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context("call-revoked", INTERACTION,
                TENANT, UUID.randomUUID(), null, null, "hi-IN", "SINGLE_MATCH", "IDENTIFIED_PATIENT", PATIENT,
                "+919876543210");
        var grant = new AivaTransactionAuthorization(AivaTransactionAuthorization.IdentityState.AUTHENTICATED,
                PATIENT, AivaTransactionAuthorization.ActorType.VOICE_PATIENT, "real-app-user-id",
                Set.of(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT), "VOICE_OTP", "OTP",
                Instant.now().plusSeconds(600), TENANT, context.providerCallId());
        VoiceAuthenticationState verified = new VoiceAuthenticationState("VERIFIED", UUID.randomUUID(), TENANT,
                PATIENT, context.providerCallId(), INTERACTION, Instant.now().plusSeconds(300), 0, grant,
                "BOOK_APPOINTMENT");
        when(adapter.voiceAuthenticationState(any(AivaExecutionContext.class))).thenReturn(verified);
        when(adapter.currentVoiceAuthorization(any(AivaExecutionContext.class))).thenReturn(
                AivaTransactionAuthorization.unauthenticated(TENANT, context.providerCallId(), PATIENT));
        when(adapter.continueTurn(any(AivaExecutionContext.class), eq("book appointment"), any()))
                .thenReturn(new AivaV2VoiceConversationAdapter.TurnResult("blocked", "BOOK_APPOINTMENT",
                        "BOOK_APPOINTMENT", true, true, false, null, 0, "BLOCKED_READ_ONLY", false, null));

        service.continueTurn(context, "book appointment");

        verify(adapter).updateVoiceAuthenticationState(any(), eq(null));
        ArgumentCaptor<AivaExecutionContext> executionCaptor = ArgumentCaptor.forClass(AivaExecutionContext.class);
        verify(adapter).continueTurn(executionCaptor.capture(), eq("book appointment"), any());
        assertThat(executionCaptor.getValue().transactionAuthorization().authenticated()).isFalse();
        verify(tools, never()).execute(any(), any(), any());
    }
}
