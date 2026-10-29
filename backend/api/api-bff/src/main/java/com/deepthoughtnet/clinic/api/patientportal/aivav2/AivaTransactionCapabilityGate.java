package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaTransactionAuthorization.Capability;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaTransactionAuthorization.IdentityState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Single fail-closed policy mapping from AIVA operations to transaction capabilities. */
@Component
public class AivaTransactionCapabilityGate {
    /** Guest grants are restricted to draft/preparation operations; confirmation is never included. */
    public Decision authorizeGuestBookingPreparation(Operation operation, VoiceGuestRegistrationState guest,
            AivaExecutionContext context) {
        if (context == null || guest == null || guest.status() != VoiceGuestRegistrationStatus.CAPABILITY_GRANTED
                || !guest.guestBookingCapabilityGranted() || guest.patientId() == null) {
            return new Decision(Capability.GUEST_BOOK_APPOINTMENT, Outcome.DENIED, "GUEST_CAPABILITY_MISSING");
        }
        if (!"VOICE".equalsIgnoreCase(context.channel())) {
            return new Decision(Capability.GUEST_BOOK_APPOINTMENT, Outcome.DENIED, "CHANNEL_BINDING_MISMATCH");
        }
        if (context.tenantId() == null || !context.tenantId().equals(guest.tenantId())) {
            return new Decision(Capability.GUEST_BOOK_APPOINTMENT, Outcome.DENIED, "TENANT_BINDING_MISMATCH");
        }
        if (!java.util.Objects.equals(context.effectiveSessionBinding(), guest.providerCallId())
                || !java.util.Objects.equals(context.providerCallId(), guest.providerCallId())) {
            return new Decision(Capability.GUEST_BOOK_APPOINTMENT, Outcome.DENIED, "SESSION_BINDING_MISMATCH");
        }
        if (!java.util.Objects.equals(context.interactionId(), guest.interactionId())) {
            return new Decision(Capability.GUEST_BOOK_APPOINTMENT, Outcome.DENIED, "INTERACTION_BINDING_MISMATCH");
        }
        if (guest.capabilityExpiresAt() == null || !guest.capabilityExpiresAt().isAfter(Instant.now())) {
            return new Decision(Capability.GUEST_BOOK_APPOINTMENT, Outcome.DENIED, "CAPABILITY_EXPIRED");
        }
        boolean preparationOnly = operation == Operation.START_BOOKING || operation == Operation.UPDATE_BOOKING
                || operation == Operation.RESOLVE_PROVIDER || operation == Operation.GET_AVAILABILITY
                || operation == Operation.SELECT_SLOT || operation == Operation.SHOW_MORE_SLOTS
                || operation == Operation.PREPARE_BOOKING;
        return preparationOnly
                ? new Decision(Capability.GUEST_BOOK_APPOINTMENT, Outcome.ALLOWED, "GUEST_PREPARATION_ONLY")
                : new Decision(Capability.GUEST_BOOK_APPOINTMENT, Outcome.DENIED, "GUEST_CAPABILITY_SCOPE_DENIED");
    }

    public Decision authorize(Operation operation, AivaExecutionContext context) {
        return decide(capability(operation), context);
    }

    public Decision authorizeVoiceIntent(String intent, AivaExecutionContext context) {
        return decide(capability(intent), context);
    }

    private Decision decide(Capability required, AivaExecutionContext context) {
        if (required == null) return new Decision(null, Outcome.ALLOWED, "NOT_TRANSACTIONAL");
        if (context == null) return new Decision(required, Outcome.DENIED, "EXECUTION_CONTEXT_MISSING");
        if ("VOICE".equalsIgnoreCase(context.channel()) && required != Capability.BOOK_APPOINTMENT) {
            return new Decision(required, Outcome.DENIED, "VOICE_OPERATION_NOT_ENABLED");
        }
        AivaTransactionAuthorization grant = context.transactionAuthorization();
        if (grant == null || grant.identityState() != IdentityState.AUTHENTICATED || !grant.authenticated()) {
            return new Decision(required, Outcome.DENIED, "PATIENT_NOT_AUTHENTICATED");
        }
        if (grant.actorType() == AivaTransactionAuthorization.ActorType.PATIENT_PORTAL_USER
                && !"PATIENT_PORTAL".equalsIgnoreCase(context.channel())) {
            return new Decision(required, Outcome.DENIED, "CHANNEL_BINDING_MISMATCH");
        }
        if (grant.tenantId() == null || !grant.tenantId().equals(context.tenantId())) {
            return new Decision(required, Outcome.DENIED, "TENANT_BINDING_MISMATCH");
        }
        if (!grant.authenticatedPatientId().equals(context.identifiedPatientId())) {
            return new Decision(required, Outcome.DENIED, "PATIENT_BINDING_MISMATCH");
        }
        if (grant.actorId() == null || grant.actorId().isBlank()) {
            return new Decision(required, Outcome.DENIED, "AUDIT_ACTOR_MISSING");
        }
        if (grant.sessionBinding() == null
                || !grant.sessionBinding().equals(context.effectiveSessionBinding())) {
            return new Decision(required, Outcome.DENIED, "SESSION_BINDING_MISMATCH");
        }
        if (grant.expiresAt() != null && !grant.expiresAt().isAfter(Instant.now())) {
            return new Decision(required, Outcome.DENIED, "AUTHORIZATION_EXPIRED");
        }
        if (!grant.grantedCapabilities().contains(required)) {
            return new Decision(required, Outcome.DENIED, "CAPABILITY_NOT_GRANTED");
        }
        return new Decision(required, Outcome.ALLOWED, "CAPABILITY_GRANTED");
    }

    private Capability capability(Operation operation) {
        if (operation == null) return null;
        return switch (operation) {
            case START_BOOKING, UPDATE_BOOKING, RESOLVE_PROVIDER, GET_AVAILABILITY, SELECT_SLOT,
                    SHOW_MORE_SLOTS, PREPARE_BOOKING, CONFIRM_BOOKING, FIND_NEXT_AVAILABLE_SLOT,
                    SUSPEND_BOOKING, RESUME_BOOKING, ABANDON_BOOKING -> Capability.BOOK_APPOINTMENT;
            case LOOKUP_APPOINTMENTS, NEXT_UPCOMING_APPOINTMENT -> Capability.VIEW_APPOINTMENTS;
            case CANCEL_APPOINTMENT, PREPARE_CANCELLATION, CONFIRM_CANCELLATION -> Capability.CANCEL_APPOINTMENT;
            case RESCHEDULE_APPOINTMENT, UPDATE_RESCHEDULE, GET_RESCHEDULE_AVAILABILITY,
                    SELECT_RESCHEDULE_SLOT, PREPARE_RESCHEDULE, CONFIRM_RESCHEDULE -> Capability.RESCHEDULE_APPOINTMENT;
            default -> null;
        };
    }

    private Capability capability(String intent) {
        if (intent == null) return null;
        return switch (intent.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "BOOK_APPOINTMENT" -> Capability.BOOK_APPOINTMENT;
            case "FIND_APPOINTMENTS", "CHECK_APPOINTMENT" -> Capability.VIEW_APPOINTMENTS;
            case "CANCEL_APPOINTMENT" -> Capability.CANCEL_APPOINTMENT;
            case "RESCHEDULE_APPOINTMENT" -> Capability.RESCHEDULE_APPOINTMENT;
            default -> null;
        };
    }

    public record Decision(Capability requestedCapability, Outcome outcome, String denialReason) {
        public boolean allowed() { return outcome == Outcome.ALLOWED; }

        public Map<String, Object> diagnostics(AivaTransactionAuthorization authorization) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("identityState", authorization == null ? "UNIDENTIFIED" : authorization.identityState().name());
            result.put("authenticated", authorization != null && authorization.authenticated());
            result.put("authorizationSource", authorization == null ? "NONE" : authorization.authorizationSource());
            result.put("grantedCapabilities", authorization == null ? java.util.List.of()
                    : authorization.grantedCapabilities().stream().map(Enum::name).sorted().toList());
            result.put("requestedCapability", requestedCapability == null ? null : requestedCapability.name());
            result.put("decision", outcome.name());
            result.put("denialReason", allowed() ? null : denialReason);
            result.entrySet().removeIf(entry -> entry.getValue() == null);
            return Map.copyOf(result);
        }
    }

    public enum Outcome { ALLOWED, DENIED }
}
