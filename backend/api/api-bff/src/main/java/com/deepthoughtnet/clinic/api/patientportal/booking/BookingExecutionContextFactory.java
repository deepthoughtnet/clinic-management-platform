package com.deepthoughtnet.clinic.api.patientportal.booking;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaTransactionAuthorization;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceIdentityTrust;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BookingExecutionContextFactory {
    public BookingExecutionContext patientPortal(UUID tenantId, UUID patientId, UUID appUserId,
                                                  String sessionId, String correlationId,
                                                  java.time.ZoneId timezone) {
        if (tenantId == null || patientId == null || appUserId == null) {
            throw new SecurityException("Authenticated Patient Portal actor is required");
        }
        return new BookingExecutionContext(tenantId, patientId,
                BookingExecutionContext.ActorType.PATIENT_PORTAL_USER, appUserId, "WEB",
                BookingExecutionContext.AuthenticationState.AUTHENTICATED,
                "AUTHENTICATED_PATIENT_PORTAL_PRINCIPAL", sessionId, null, null, correlationId,
                timezone, BookingExecutionContext.Operation.BOOK_APPOINTMENT, null, true);
    }

    public BookingExecutionContext authenticatedVoice(AivaExecutionContext context) {
        AivaTransactionAuthorization grant = context == null ? null : context.transactionAuthorization();
        if (context == null || !"VOICE".equalsIgnoreCase(context.channel()) || grant == null
                || !grant.authenticated() || grant.authenticatedPatientId() == null
                || !context.tenantId().equals(grant.tenantId())
                || !java.util.Objects.equals(context.identifiedPatientId(), grant.authenticatedPatientId())
                || !context.effectiveSessionBinding().equals(grant.sessionBinding())
                || !"VOICE_OTP".equals(grant.authorizationSource())
                || !grant.grantedCapabilities().contains(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT)
                || grant.expiresAt() == null || !grant.expiresAt().isAfter(java.time.Instant.now())
                || grant.actorId() == null || grant.actorId().isBlank()) {
            throw new SecurityException("Valid call-bound Voice booking authorization is required");
        }
        return new BookingExecutionContext(context.tenantId(), grant.authenticatedPatientId(),
                BookingExecutionContext.ActorType.VOICE_AUTHENTICATED_PATIENT, parseActorId(grant.actorId()),
                "VOICE", BookingExecutionContext.AuthenticationState.AUTHENTICATED,
                grant.authorizationSource(), context.sessionId(), context.providerCallId(), context.interactionId(),
                context.correlationId(), context.timezone(), BookingExecutionContext.Operation.BOOK_APPOINTMENT,
                null, true);
    }

    public BookingExecutionContext preverifiedVoice(AivaExecutionContext context, VoiceIdentityTrust trust,
            VoiceTransactionAuthPolicy policy, String currentInboundMobile, UUID realAppUserIdIfAvailable) {
        if (context == null || trust == null || policy == null || !"VOICE".equalsIgnoreCase(context.channel())
                || trust.identityState() != VoiceIdentityTrust.IdentityState.REGISTERED_PHONE_VERIFIED
                || trust.trustSource() != VoiceIdentityTrust.TrustSource.PREVERIFIED_PHONE
                || !trust.matchesCall(context.tenantId(), context.identifiedPatientId(), currentInboundMobile,
                        context.effectiveSessionBinding()) || context.identifiedPatientId() == null
                || !policy.preverifiedPhoneAllowed() || !policy.bookingAuthRequired()
                || !policy.bookingExecutionAllowed() || context.tenantId() == null || context.providerCallId() == null
                || !context.effectiveSessionBinding().equals(context.providerCallId())) {
            throw new SecurityException("Valid policy-approved, call-bound preverified phone trust is required");
        }
        return new BookingExecutionContext(context.tenantId(), context.identifiedPatientId(),
                BookingExecutionContext.ActorType.VOICE_AUTHENTICATED_PATIENT, realAppUserIdIfAvailable, "VOICE",
                BookingExecutionContext.AuthenticationState.REGISTERED_PHONE_VERIFIED, "PREVERIFIED_PHONE",
                context.sessionId(), context.providerCallId(), context.interactionId(), context.correlationId(),
                context.timezone(), BookingExecutionContext.Operation.BOOK_APPOINTMENT, null, true);
    }

    public BookingExecutionContext guestVoice(AivaExecutionContext context, VoiceGuestRegistrationState guest) {
        if (context == null || guest == null || !"VOICE".equalsIgnoreCase(context.channel())
                || !guest.guestBookingCapabilityGranted() || guest.patientId() == null
                || !context.tenantId().equals(guest.tenantId())
                || !context.effectiveSessionBinding().equals(guest.providerCallId())
                || guest.capabilityExpiresAt() == null
                || !guest.capabilityExpiresAt().isAfter(java.time.Instant.now())) {
            throw new SecurityException("Valid call-bound guest booking capability is required");
        }
        return new BookingExecutionContext(context.tenantId(), guest.patientId(),
                BookingExecutionContext.ActorType.VOICE_GUEST_PATIENT, null, "VOICE",
                BookingExecutionContext.AuthenticationState.GUEST_REGISTERED,
                "VOICE_GUEST_REGISTRATION", context.sessionId(), context.providerCallId(), guest.interactionId(),
                context.correlationId(), context.timezone(), BookingExecutionContext.Operation.BOOK_APPOINTMENT,
                "VOICE_GUEST_BOOKING", true);
    }

    /** Restricted new-appointment actor: existing unverified patient identity, no personal-data capability. */
    public BookingExecutionContext restrictedUnverifiedVoice(AivaExecutionContext context,
            VoiceIdentityTrust trust, VoiceTransactionAuthPolicy policy, String identificationStatus) {
        if (context == null || trust == null || policy == null || !"VOICE".equalsIgnoreCase(context.channel())
                || trust.identityState() != VoiceIdentityTrust.IdentityState.REGISTERED_UNVERIFIED
                || context.identifiedPatientId() == null || !"SINGLE_MATCH".equals(identificationStatus)
                || !policy.bookingExecutionAllowed() || context.providerCallId() == null
                || !context.effectiveSessionBinding().equals(context.providerCallId())) {
            throw new SecurityException("Restricted new-booking policy/context is not valid");
        }
        return new BookingExecutionContext(context.tenantId(), context.identifiedPatientId(),
                BookingExecutionContext.ActorType.VOICE_REGISTERED_UNVERIFIED, null, "VOICE",
                BookingExecutionContext.AuthenticationState.REGISTERED_UNVERIFIED,
                "VOICE_RESTRICTED_NEW_BOOKING", context.sessionId(), context.providerCallId(),
                context.interactionId(), context.correlationId(), context.timezone(),
                BookingExecutionContext.Operation.BOOK_APPOINTMENT, "VOICE_RESTRICTED_NEW_BOOKING", true);
    }

    private UUID parseActorId(String actorId) {
        if (actorId == null || actorId.isBlank()) return null;
        try { return UUID.fromString(actorId); }
        catch (IllegalArgumentException ex) { throw new SecurityException("Invalid authenticated Voice actor identity"); }
    }
}
