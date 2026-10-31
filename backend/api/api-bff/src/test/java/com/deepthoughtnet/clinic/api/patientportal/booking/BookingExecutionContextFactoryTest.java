package com.deepthoughtnet.clinic.api.patientportal.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaTransactionAuthorization;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationStatus;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceIdentityTrust;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceOperatorProperties;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthProperties;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionPolicyResolver;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingExecutionContextFactoryTest {
    private final BookingExecutionContextFactory factory = new BookingExecutionContextFactory();
    private final UUID tenant = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @Test
    void registeredVoiceContextUsesVerifiedPatientAndRealActor() {
        AivaTransactionAuthorization grant = new AivaTransactionAuthorization(
                AivaTransactionAuthorization.IdentityState.AUTHENTICATED, patient,
                AivaTransactionAuthorization.ActorType.VOICE_AUTHENTICATED_PATIENT, user.toString(),
                Set.of(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT), "VOICE_OTP", "OTP",
                Instant.now().plusSeconds(300), tenant, "call-1");
        AivaExecutionContext execution = new AivaExecutionContext(tenant, patient, "VOICE", "en",
                ZoneId.of("Asia/Kolkata"), "session-1", UUID.randomUUID(), null, null, Set.of(),
                "call-1", "corr-1", grant);

        BookingExecutionContext result = factory.authenticatedVoice(execution);

        assertThat(result.actorType()).isEqualTo(BookingExecutionContext.ActorType.VOICE_AUTHENTICATED_PATIENT);
        assertThat(result.patientId()).isEqualTo(patient);
        assertThat(result.actorId()).isEqualTo(user);
        assertThat(result.channel()).isEqualTo("VOICE");
    }

    @Test
    void registeredVoicePatientMismatchAndWrongCallAreRejected() {
        AivaTransactionAuthorization grant = new AivaTransactionAuthorization(
                AivaTransactionAuthorization.IdentityState.AUTHENTICATED, patient,
                AivaTransactionAuthorization.ActorType.VOICE_AUTHENTICATED_PATIENT, user.toString(),
                Set.of(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT), "VOICE_OTP", "OTP",
                Instant.now().plusSeconds(300), tenant, "other-call");
        AivaExecutionContext execution = new AivaExecutionContext(tenant, patient, "VOICE", "en",
                ZoneId.of("UTC"), "session-1", null, null, null, Set.of(), "call-1", null, grant);
        assertThatThrownBy(() -> factory.authenticatedVoice(execution)).isInstanceOf(SecurityException.class);
    }

    @Test
    void registeredVoiceTenantMismatchIsRejected() {
        AivaTransactionAuthorization grant = new AivaTransactionAuthorization(
                AivaTransactionAuthorization.IdentityState.AUTHENTICATED, patient,
                AivaTransactionAuthorization.ActorType.VOICE_AUTHENTICATED_PATIENT, user.toString(),
                Set.of(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT), "VOICE_OTP", "OTP",
                Instant.now().plusSeconds(300), UUID.randomUUID(), "call-1");
        AivaExecutionContext execution = new AivaExecutionContext(tenant, patient, "VOICE", "en",
                ZoneId.of("UTC"), "session-1", null, null, null, Set.of(), "call-1", null, grant);
        assertThatThrownBy(() -> factory.authenticatedVoice(execution)).isInstanceOf(SecurityException.class);
    }

    @Test
    void guestContextHasNoPortalActorAndIsCallBound() {
        AivaExecutionContext execution = new AivaExecutionContext(tenant, null, "VOICE", "hi-IN",
                ZoneId.of("Asia/Kolkata"), "call-1", UUID.randomUUID(), null, null, Set.of(),
                "call-1", "corr-1", AivaTransactionAuthorization.unauthenticated(tenant, "call-1", null));
        VoiceGuestRegistrationState guest = new VoiceGuestRegistrationState(
                VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, patient, "call-1",
                execution.interactionId(), Instant.now().plusSeconds(300), true);

        BookingExecutionContext result = factory.guestVoice(execution, guest);

        assertThat(result.actorType()).isEqualTo(BookingExecutionContext.ActorType.VOICE_GUEST_PATIENT);
        assertThat(result.patientId()).isEqualTo(patient);
        assertThat(result.actorId()).isNull();
        assertThat(result.authenticationState()).isEqualTo(BookingExecutionContext.AuthenticationState.GUEST_REGISTERED);
        assertThatThrownBy(() -> factory.guestVoice(new AivaExecutionContext(tenant, null, "VOICE", "hi-IN",
                ZoneId.of("Asia/Kolkata"), "call-2", null, null, null, Set.of(), "call-2", null,
                AivaTransactionAuthorization.unauthenticated(tenant, "call-2", null)), guest))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void preverifiedPhoneBuildsRegisteredVoiceContextWithoutInventingAppUser() {
        AivaExecutionContext execution = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("Asia/Kolkata"), "call-verified", UUID.randomUUID(), null, null, Set.of(),
                "call-verified", "corr", AivaTransactionAuthorization.unauthenticated(tenant, "call-verified", patient));
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setTrustMode(VoiceTransactionAuthPolicy.TrustMode.PREVERIFIED_PHONE_ALLOWED);
        properties.getDefaultPolicy().setBookingEnabled(true);
        var policy = new VoiceTransactionPolicyResolver(properties).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        String mobile = "+919876543210";
        var trust = VoiceIdentityTrust.registeredPhoneVerified(tenant, patient, mobile, "call-verified");

        BookingExecutionContext context = factory.preverifiedVoice(execution, trust, policy, mobile, null);

        assertThat(context.actorType()).isEqualTo(BookingExecutionContext.ActorType.VOICE_AUTHENTICATED_PATIENT);
        assertThat(context.authenticationState()).isEqualTo(BookingExecutionContext.AuthenticationState.REGISTERED_PHONE_VERIFIED);
        assertThat(context.authorizationSource()).isEqualTo("PREVERIFIED_PHONE");
        assertThat(context.patientId()).isEqualTo(patient);
        assertThat(context.actorId()).isNull();
    }

    @Test
    void preverifiedPhoneCannotOverrideDisabledPolicyOrUnverifiedIdentity() {
        AivaExecutionContext execution = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("UTC"), "call-verified", null, null, null, Set.of(), "call-verified", null,
                AivaTransactionAuthorization.unauthenticated(tenant, "call-verified", patient));
        var trust = VoiceIdentityTrust.registeredPhoneVerified(tenant, patient, "+919876543210", "call-verified");
        var disabledPolicy = new VoiceTransactionPolicyResolver(new VoiceTransactionAuthProperties())
                .resolve(tenant, null, VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        assertThatThrownBy(() -> factory.preverifiedVoice(execution, trust, disabledPolicy, "+919876543210", null))
                .isInstanceOf(SecurityException.class);
        var enabledPolicy = new VoiceTransactionPolicyResolver(preverifiedPolicy()).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        assertThatThrownBy(() -> factory.preverifiedVoice(execution,
                VoiceIdentityTrust.registeredUnverified(), enabledPolicy, "+919876543210", null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void preverifiedPhoneEvidenceCannotCrossTenantPatientPhoneOrCall() {
        var policy = new VoiceTransactionPolicyResolver(preverifiedPolicy()).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        var trust = VoiceIdentityTrust.registeredPhoneVerified(tenant, patient, "+919876543210", "call-a");
        AivaExecutionContext valid = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("UTC"), "call-a", UUID.randomUUID(), null, null, Set.of(), "call-a", "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, "call-a", patient));
        assertThatThrownBy(() -> factory.preverifiedVoice(valid, trust, policy, "+919000000000", null))
                .isInstanceOf(SecurityException.class);
        AivaExecutionContext otherCall = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("UTC"), "call-b", UUID.randomUUID(), null, null, Set.of(), "call-b", "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, "call-b", patient));
        assertThatThrownBy(() -> factory.preverifiedVoice(otherCall, trust, policy, "+919876543210", null))
                .isInstanceOf(SecurityException.class);
        AivaExecutionContext otherTenant = new AivaExecutionContext(UUID.randomUUID(), patient, "VOICE", "hi-IN",
                ZoneId.of("UTC"), "call-a", UUID.randomUUID(), null, null, Set.of(), "call-a", "corr",
                AivaTransactionAuthorization.unauthenticated(UUID.randomUUID(), "call-a", patient));
        assertThatThrownBy(() -> factory.preverifiedVoice(otherTenant, trust, policy, "+919876543210", null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void registeredUnverifiedGetsOnlyPolicyBoundNewBookingActorWithoutPortalIdentity() {
        AivaExecutionContext execution = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("Asia/Kolkata"), "call-unverified", UUID.randomUUID(), null, null, Set.of(),
                "call-unverified", "corr", AivaTransactionAuthorization.unauthenticated(tenant,
                        "call-unverified", patient));
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(true);
        operator.setOtpRequired(false);
        operator.setGuestBookingEnabled(false);
        var policy = new VoiceTransactionPolicyResolver(properties, operator).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);

        BookingExecutionContext context = factory.restrictedUnverifiedVoice(execution,
                VoiceIdentityTrust.registeredUnverified(), policy, "SINGLE_MATCH");

        assertThat(context.actorType()).isEqualTo(BookingExecutionContext.ActorType.VOICE_REGISTERED_UNVERIFIED);
        assertThat(context.authenticationState()).isEqualTo(BookingExecutionContext.AuthenticationState.REGISTERED_UNVERIFIED);
        assertThat(context.actorId()).isNull();
        assertThat(context.allowedOperation()).isEqualTo(BookingExecutionContext.Operation.BOOK_APPOINTMENT);
        assertThatThrownBy(() -> factory.restrictedUnverifiedVoice(execution,
                VoiceIdentityTrust.registeredUnverified(), policy, "NO_MATCH"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void quickRegisteredCallerHasUnverifiedCallBoundIdentityAndOnlyNewBookingContext() {
        UUID interaction = UUID.randomUUID();
        Instant consentAt = Instant.now();
        AivaExecutionContext execution = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("Asia/Kolkata"), "quick-call", interaction, null, null, Set.of(), "quick-call", "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, "quick-call", patient));
        VoiceGuestRegistrationState registration = new VoiceGuestRegistrationState(
                VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, patient, "quick-call", interaction,
                Instant.now().plusSeconds(300), true, "Ashish", 42, consentAt);

        BookingExecutionContext result = factory.quickRegisteredVoice(execution, registration);
        VoiceIdentityTrust trust = VoiceIdentityTrust.registeredUnverified();

        assertThat(result.actorType()).isEqualTo(BookingExecutionContext.ActorType.VOICE_REGISTERED_UNVERIFIED);
        assertThat(result.authenticationState()).isEqualTo(BookingExecutionContext.AuthenticationState.REGISTERED_UNVERIFIED);
        assertThat(result.allowedOperation()).isEqualTo(BookingExecutionContext.Operation.BOOK_APPOINTMENT);
        assertThat(result.authorizationSource()).isEqualTo("VOICE_RESTRICTED_NEW_BOOKING");
        assertThat(result.actorId()).isNull();
        assertThat(execution.transactionAuthorization().authenticated()).isFalse();
        assertThat(execution.transactionAuthorization().authorizationSource()).isNotEqualTo("OTP");
        assertThat(trust.identityState()).isEqualTo(VoiceIdentityTrust.IdentityState.REGISTERED_UNVERIFIED);
        assertThat(trust.trustSource()).isEqualTo(VoiceIdentityTrust.TrustSource.NONE);
        assertThat(trust.phoneBindingVerified()).isFalse();
    }

    @Test
    void registeredUnverifiedCanUseRestrictedBookingWithoutGuestRegistrationOrOtpEvidence() {
        AivaExecutionContext execution = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("UTC"), "call-unverified", null, null, null, Set.of(), "call-unverified", null,
                AivaTransactionAuthorization.unauthenticated(tenant, "call-unverified", patient));
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(true);
        operator.setOtpRequired(false);
        operator.setGuestBookingEnabled(false);
        var policy = new VoiceTransactionPolicyResolver(properties, operator).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        BookingExecutionContext context = factory.restrictedUnverifiedVoice(execution,
                VoiceIdentityTrust.registeredUnverified(), policy, "SINGLE_MATCH");
        assertThat(context.actorType()).isEqualTo(BookingExecutionContext.ActorType.VOICE_REGISTERED_UNVERIFIED);
        assertThat(context.authenticationState()).isEqualTo(BookingExecutionContext.AuthenticationState.REGISTERED_UNVERIFIED);
        assertThat(context.actorId()).isNull();
        assertThat(context.allowedOperation()).isEqualTo(BookingExecutionContext.Operation.BOOK_APPOINTMENT);

        operator.setOtpRequired(true);
        var otpPolicy = new VoiceTransactionPolicyResolver(properties, operator).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        BookingExecutionContext otpCompatibilityFlagDoesNotBlockNewBooking = factory.restrictedUnverifiedVoice(
                execution, VoiceIdentityTrust.registeredUnverified(), otpPolicy, "SINGLE_MATCH");
        assertThat(otpCompatibilityFlagDoesNotBlockNewBooking.allowedOperation())
                .isEqualTo(BookingExecutionContext.Operation.BOOK_APPOINTMENT);
    }

    private VoiceTransactionAuthProperties preverifiedPolicy() {
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setTrustMode(VoiceTransactionAuthPolicy.TrustMode.PREVERIFIED_PHONE_ALLOWED);
        properties.getDefaultPolicy().setBookingEnabled(true);
        return properties;
    }
}
