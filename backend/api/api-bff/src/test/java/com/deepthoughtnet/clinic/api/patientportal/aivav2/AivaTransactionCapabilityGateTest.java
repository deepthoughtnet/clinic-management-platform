package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaTransactionAuthorization.Capability;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AivaTransactionCapabilityGateTest {
    private final AivaTransactionCapabilityGate gate = new AivaTransactionCapabilityGate();

    @Test
    void identifiedVoiceCallerHasNoTransactionalCapabilities() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var authorization = AivaTransactionAuthorization.unauthenticated(tenant, "call-1", patient);
        var context = context(tenant, patient, "call-1", authorization);

        assertThat(authorization.identityState()).isEqualTo(AivaTransactionAuthorization.IdentityState.IDENTIFIED);
        assertThat(authorization.authenticated()).isFalse();
        assertThat(authorization.grantedCapabilities()).isEmpty();
        for (String intent : Set.of("BOOK_APPOINTMENT", "FIND_APPOINTMENTS", "CANCEL_APPOINTMENT",
                "RESCHEDULE_APPOINTMENT")) {
            var decision = gate.authorizeVoiceIntent(intent, context);
            assertThat(decision.allowed()).as(intent).isFalse();
            assertThat(decision.denialReason()).isEqualTo("PATIENT_NOT_AUTHENTICATED");
        }
    }

    @Test
    void voiceReadOnlyOperationsRemainOutsideTransactionCapabilityGate() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var voiceContext = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("Asia/Kolkata"), "read-only-call", null, null, null,
                Set.of("FIND_DOCTOR", "FIND_DOCTOR_SCHEDULE", "FIND_SLOTS"), "read-only-call", null,
                AivaTransactionAuthorization.unauthenticated(tenant, "read-only-call", patient));

        assertThat(gate.authorizeVoiceIntent("FIND_DOCTOR", voiceContext).allowed()).isTrue();
        assertThat(gate.authorizeVoiceIntent("FIND_DOCTOR_SCHEDULE", voiceContext).allowed()).isTrue();
        assertThat(gate.authorizeVoiceIntent("FIND_SLOTS", voiceContext).allowed()).isTrue();
    }

    @Test
    void authenticatedPortalContextRetainsExistingKernelCapabilities() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        String conversation = "portal-conversation";
        var authorization = AivaTransactionAuthorization.patientPortal(
                tenant, patient, UUID.randomUUID().toString(), conversation);
        var context = context(tenant, patient, conversation, authorization);

        assertThat(gate.authorize(Operation.START_BOOKING, context).allowed()).isTrue();
        assertThat(gate.authorize(Operation.LOOKUP_APPOINTMENTS, context).allowed()).isTrue();
        assertThat(gate.authorize(Operation.CANCEL_APPOINTMENT, context).allowed()).isTrue();
        assertThat(gate.authorize(Operation.RESCHEDULE_APPOINTMENT, context).allowed()).isTrue();
    }

    @Test
    void webAdapterBuildsAuthenticatedPatientContextFromExistingRequestPrincipal() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID appUser = UUID.randomUUID();
        RequestContext request = new RequestContext(TenantId.of(tenant), appUser, "principal-sub",
                Set.of("PATIENT"), "PATIENT", "request-correlation");

        AivaExecutionContext context = AivaV2ConversationService.authenticatedWebContext(
                request, patient, "en", ZoneId.of("Asia/Kolkata"), "portal-session");

        assertThat(context.tenantId()).isEqualTo(tenant);
        assertThat(context.identifiedPatientId()).isEqualTo(patient);
        assertThat(context.transactionAuthorization().authenticatedPatientId()).isEqualTo(patient);
        assertThat(context.transactionAuthorization().actorId()).isEqualTo(appUser.toString());
        assertThat(context.correlationId()).isEqualTo("request-correlation");
        assertThat(gate.authorize(Operation.CONFIRM_BOOKING, context).allowed()).isTrue();
    }

    @Test
    void voiceTenantAndCallBindingCannotBeOverriddenByConversationText() {
        UUID routedTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID identifiedPatient = UUID.randomUUID();
        var authorization = AivaTransactionAuthorization.patientPortal(routedTenant, identifiedPatient,
                UUID.randomUUID().toString(), "provider-call-7");
        var routedContext = context(routedTenant, identifiedPatient, "provider-call-7", authorization);
        var alteredTenantContext = context(otherTenant, identifiedPatient, "provider-call-7", authorization);

        assertThat(routedContext.tenantId()).isEqualTo(routedTenant);
        var decision = gate.authorizeVoiceIntent("BOOK_APPOINTMENT", alteredTenantContext);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialReason()).isEqualTo("TENANT_BINDING_MISMATCH");
        assertThat(gate.authorizeVoiceIntent("BOOK_APPOINTMENT", routedContext).allowed()).isTrue();
    }

    @Test
    void gateDiagnosticsDoNotIncludePatientIdentifiersOrSecrets() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var authorization = AivaTransactionAuthorization.unauthenticated(tenant, "call-safe", patient);
        var decision = gate.authorizeVoiceIntent("CANCEL_APPOINTMENT",
                context(tenant, patient, "call-safe", authorization));

        assertThat(decision.diagnostics(authorization)).containsEntry("identityState", "IDENTIFIED")
                .containsEntry("authenticated", false)
                .containsEntry("grantedCapabilities", java.util.List.of())
                .doesNotContainValue(patient.toString())
                .doesNotContainKey("token")
                .doesNotContainKey("otp");
    }

    @Test
    void mismatchedPortalSessionBindingIsDenied() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var grant = AivaTransactionAuthorization.patientPortal(tenant, patient,
                UUID.randomUUID().toString(), "conversation-a");
        var context = context(tenant, patient, "conversation-b", grant);

        var result = gate.authorize(Operation.CONFIRM_BOOKING, context);
        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).isEqualTo("SESSION_BINDING_MISMATCH");
    }

    @Test
    void portalGrantCannotBeReplayedOnVoiceChannel() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var grant = AivaTransactionAuthorization.patientPortal(tenant, patient,
                UUID.randomUUID().toString(), "call-portal-replay");
        AivaExecutionContext voice = new AivaExecutionContext(tenant, patient, "VOICE", "en-IN",
                ZoneId.of("Asia/Kolkata"), "call-portal-replay", null, null, null,
                Set.of("FIND_SLOTS"), "call-portal-replay", "corr", grant);

        var result = gate.authorizeVoiceIntent("BOOK_APPOINTMENT", voice);
        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).isEqualTo("CHANNEL_BINDING_MISMATCH");
    }

    @Test
    void authenticatedVoiceGrantCannotUnlockLookupCancellationOrReschedule() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var grant = new AivaTransactionAuthorization(AivaTransactionAuthorization.IdentityState.AUTHENTICATED,
                patient, AivaTransactionAuthorization.ActorType.VOICE_AUTHENTICATED_PATIENT,
                UUID.randomUUID().toString(), Set.of(Capability.BOOK_APPOINTMENT, Capability.VIEW_APPOINTMENTS,
                Capability.CANCEL_APPOINTMENT, Capability.RESCHEDULE_APPOINTMENT), "VOICE_OTP", "OTP",
                java.time.Instant.now().plusSeconds(600), tenant, "call-book-only");
        var execution = new AivaExecutionContext(tenant, patient, "VOICE", "en", ZoneId.of("UTC"),
                "call-book-only", null, null, null, Set.of(), "call-book-only", "corr", grant);

        assertThat(gate.authorizeVoiceIntent("BOOK_APPOINTMENT", execution).allowed()).isTrue();
        for (String intent : Set.of("FIND_APPOINTMENTS", "CANCEL_APPOINTMENT", "RESCHEDULE_APPOINTMENT")) {
            var decision = gate.authorizeVoiceIntent(intent, execution);
            assertThat(decision.allowed()).as(intent).isFalse();
            assertThat(decision.denialReason()).isEqualTo("VOICE_OPERATION_NOT_ENABLED");
        }
    }

    @Test
    void guestGrantAllowsOnlyCallBoundBookingPreparationNotMutationOrPatientData() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        var execution = new AivaExecutionContext(tenant, null, "VOICE", "hi-IN", ZoneId.of("Asia/Kolkata"),
                "guest-call", interaction, null, null, Set.of("FIND_SLOTS"), "guest-call", "guest-correlation",
                AivaTransactionAuthorization.unauthenticated(tenant, "guest-call", null));
        var guest = new AivaV2Models.VoiceGuestRegistrationState(
                AivaV2Models.VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, patient,
                "guest-call", interaction, java.time.Instant.now().plusSeconds(600), true);

        assertThat(gate.authorizeGuestBookingPreparation(Operation.PREPARE_BOOKING, guest, execution).allowed()).isTrue();
        assertThat(gate.authorizeGuestBookingPreparation(Operation.GET_AVAILABILITY, guest, execution).allowed()).isTrue();
        for (Operation forbidden : Set.of(Operation.CONFIRM_BOOKING, Operation.LOOKUP_APPOINTMENTS,
                Operation.CANCEL_APPOINTMENT, Operation.RESCHEDULE_APPOINTMENT)) {
            var denied = gate.authorizeGuestBookingPreparation(forbidden, guest, execution);
            assertThat(denied.allowed()).as(forbidden.name()).isFalse();
            assertThat(denied.requestedCapability()).isEqualTo(Capability.GUEST_BOOK_APPOINTMENT);
        }
    }

    @Test
    void guestGrantCannotCrossCallTenantInteractionOrExpiry() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        var guest = new AivaV2Models.VoiceGuestRegistrationState(
                AivaV2Models.VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, patient,
                "guest-call-a", interaction, java.time.Instant.now().plusSeconds(600), true);
        var valid = new AivaExecutionContext(tenant, null, "VOICE", "en", ZoneId.of("Asia/Kolkata"),
                "guest-call-a", interaction, null, null, Set.of(), "guest-call-a", "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, "guest-call-a", null));
        var otherCall = new AivaExecutionContext(tenant, null, "VOICE", "en", ZoneId.of("Asia/Kolkata"),
                "guest-call-b", interaction, null, null, Set.of(), "guest-call-b", "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, "guest-call-b", null));
        var otherChannel = new AivaExecutionContext(tenant, null, "PATIENT_PORTAL", "en",
                ZoneId.of("Asia/Kolkata"), "guest-call-a", interaction, null, null, Set.of(),
                "guest-call-a", "corr", AivaTransactionAuthorization.unauthenticated(tenant, "guest-call-a", null));
        var otherTenant = new AivaExecutionContext(UUID.randomUUID(), null, "VOICE", "en", ZoneId.of("Asia/Kolkata"),
                "guest-call-a", interaction, null, null, Set.of(), "guest-call-a", "corr",
                AivaTransactionAuthorization.unauthenticated(UUID.randomUUID(), "guest-call-a", null));

        assertThat(gate.authorizeGuestBookingPreparation(Operation.PREPARE_BOOKING, guest, valid).allowed()).isTrue();
        assertThat(gate.authorizeGuestBookingPreparation(Operation.PREPARE_BOOKING, guest, otherCall).denialReason())
                .isEqualTo("SESSION_BINDING_MISMATCH");
        assertThat(gate.authorizeGuestBookingPreparation(Operation.PREPARE_BOOKING, guest, otherChannel).denialReason())
                .isEqualTo("CHANNEL_BINDING_MISMATCH");
        assertThat(gate.authorizeGuestBookingPreparation(Operation.PREPARE_BOOKING, guest, otherTenant).denialReason())
                .isEqualTo("TENANT_BINDING_MISMATCH");
        var wrongInteraction = new AivaExecutionContext(tenant, null, "VOICE", "en", ZoneId.of("Asia/Kolkata"),
                "guest-call-a", UUID.randomUUID(), null, null, Set.of(), "guest-call-a", "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, "guest-call-a", null));
        assertThat(gate.authorizeGuestBookingPreparation(Operation.PREPARE_BOOKING, guest, wrongInteraction).denialReason())
                .isEqualTo("INTERACTION_BINDING_MISMATCH");
        var expired = new AivaV2Models.VoiceGuestRegistrationState(guest.status(), tenant, patient,
                "guest-call-a", interaction, java.time.Instant.now().minusSeconds(1), true);
        assertThat(gate.authorizeGuestBookingPreparation(Operation.PREPARE_BOOKING, expired, valid).denialReason())
                .isEqualTo("CAPABILITY_EXPIRED");
    }

    private AivaExecutionContext context(UUID tenant, UUID patient, String session,
                                         AivaTransactionAuthorization authorization) {
        return new AivaExecutionContext(tenant, patient, "PATIENT_PORTAL", "en-IN", ZoneId.of("Asia/Kolkata"),
                session, null, null, null, Set.of("FIND_DOCTOR", "FIND_SLOTS"), session,
                "correlation-safe", authorization);
    }
}
