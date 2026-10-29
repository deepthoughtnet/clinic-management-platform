package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Explicit, non-secret authorization grant for AIVA transactional operations. */
public record AivaTransactionAuthorization(
        IdentityState identityState,
        UUID authenticatedPatientId,
        ActorType actorType,
        String actorId,
        Set<Capability> grantedCapabilities,
        String authorizationSource,
        String authenticationMethod,
        Instant expiresAt,
        UUID tenantId,
        String sessionBinding
) {
    public AivaTransactionAuthorization {
        identityState = identityState == null ? IdentityState.UNIDENTIFIED : identityState;
        actorType = actorType == null ? ActorType.SYSTEM : actorType;
        grantedCapabilities = grantedCapabilities == null ? Set.of() : Set.copyOf(grantedCapabilities);
        if (identityState != IdentityState.AUTHENTICATED
                && (authenticatedPatientId != null || !grantedCapabilities.isEmpty())) {
            throw new IllegalArgumentException("Unauthenticated identity cannot carry patient authorization");
        }
        if (identityState == IdentityState.AUTHENTICATED && authenticatedPatientId == null) {
            throw new IllegalArgumentException("Authenticated identity requires an authenticated patient");
        }
    }

    public boolean authenticated() {
        return identityState == IdentityState.AUTHENTICATED && authenticatedPatientId != null;
    }

    public static AivaTransactionAuthorization unauthenticated(UUID tenantId, String sessionBinding,
                                                                UUID identifiedPatientId) {
        return new AivaTransactionAuthorization(identifiedPatientId == null
                ? IdentityState.UNIDENTIFIED : IdentityState.IDENTIFIED,
                null, ActorType.VOICE_PATIENT, null, Set.of(), "VOICE_UNAUTHENTICATED",
                null, null, tenantId, sessionBinding);
    }

    static AivaTransactionAuthorization patientPortal(UUID tenantId, UUID patientId,
                                                       String actorId, String sessionBinding) {
        return new AivaTransactionAuthorization(IdentityState.AUTHENTICATED, patientId,
                ActorType.PATIENT_PORTAL_USER, actorId,
                Set.of(Capability.BOOK_APPOINTMENT, Capability.VIEW_APPOINTMENTS,
                        Capability.CANCEL_APPOINTMENT, Capability.RESCHEDULE_APPOINTMENT),
                "AUTHENTICATED_PATIENT_PORTAL_PRINCIPAL", "PATIENT_PORTAL_SESSION",
                null, tenantId, sessionBinding);
    }

    public enum IdentityState { UNIDENTIFIED, IDENTIFIED, AUTHENTICATED }
    public enum ActorType { PATIENT_PORTAL_USER, VOICE_PATIENT, VOICE_AUTHENTICATED_PATIENT, VOICE_GUEST_PATIENT, SYSTEM }
    public enum Capability { BOOK_APPOINTMENT, GUEST_BOOK_APPOINTMENT, VIEW_APPOINTMENTS, CANCEL_APPOINTMENT, RESCHEDULE_APPOINTMENT }
}
