package com.deepthoughtnet.clinic.api.patientportal.booking;

import java.time.ZoneId;
import java.util.UUID;

/** Explicit, trusted actor and scope for appointment booking application work. */
public final class BookingExecutionContext {
    private final UUID tenantId;
    private final UUID patientId;
    private final ActorType actorType;
    private final UUID actorId;
    private final String channel;
    private final AuthenticationState authenticationState;
    private final String authorizationSource;
    private final String sessionId;
    private final String providerCallId;
    private final UUID interactionId;
    private final String correlationId;
    private final ZoneId timezone;
    private final Operation allowedOperation;
    private final String guestRegistrationSource;
    private final boolean authorizationValidated;

    BookingExecutionContext(UUID tenantId, UUID patientId, ActorType actorType, UUID actorId, String channel,
                            AuthenticationState authenticationState, String authorizationSource, String sessionId,
                            String providerCallId, UUID interactionId, String correlationId, ZoneId timezone,
                            Operation allowedOperation, String guestRegistrationSource,
                            boolean authorizationValidated) {
        if (tenantId == null || patientId == null || actorType == null || channel == null || channel.isBlank()
                || authenticationState == null || allowedOperation == null) {
            throw new IllegalArgumentException("Booking execution scope is incomplete");
        }
        this.tenantId = tenantId;
        this.patientId = patientId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.channel = channel;
        this.authenticationState = authenticationState;
        this.authorizationSource = authorizationSource;
        this.sessionId = sessionId;
        this.providerCallId = providerCallId;
        this.interactionId = interactionId;
        this.correlationId = correlationId;
        this.timezone = timezone == null ? ZoneId.of("Asia/Kolkata") : timezone;
        this.allowedOperation = allowedOperation;
        this.guestRegistrationSource = guestRegistrationSource;
        this.authorizationValidated = authorizationValidated;
    }

    public UUID tenantId() { return tenantId; }
    public UUID patientId() { return patientId; }
    public ActorType actorType() { return actorType; }
    public UUID actorId() { return actorId; }
    public String channel() { return channel; }
    public AuthenticationState authenticationState() { return authenticationState; }
    public String authorizationSource() { return authorizationSource; }
    public String sessionId() { return sessionId; }
    public String providerCallId() { return providerCallId; }
    public UUID interactionId() { return interactionId; }
    public String correlationId() { return correlationId; }
    public ZoneId timezone() { return timezone; }
    public Operation allowedOperation() { return allowedOperation; }
    public String guestRegistrationSource() { return guestRegistrationSource; }
    public boolean authorizationValidated() { return authorizationValidated; }

    public enum ActorType { PATIENT_PORTAL_USER, VOICE_AUTHENTICATED_PATIENT, VOICE_REGISTERED_UNVERIFIED,
        VOICE_GUEST_PATIENT, SYSTEM }
    public enum AuthenticationState { AUTHENTICATED, REGISTERED_PHONE_VERIFIED, REGISTERED_UNVERIFIED, GUEST_REGISTERED }
    public enum Operation { BOOK_APPOINTMENT }
}
