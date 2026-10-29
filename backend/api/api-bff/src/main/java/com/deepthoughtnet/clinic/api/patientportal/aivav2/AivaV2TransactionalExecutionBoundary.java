package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.patientportal.booking.BookingExecutionContext;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Context- and capability-aware entry boundary for the existing V2 transactional kernel. */
@Component
class AivaV2TransactionalExecutionBoundary {
    private final AivaV2TransactionalKernel kernel;
    private final AivaTransactionCapabilityGate authorizationGate;

    AivaV2TransactionalExecutionBoundary(AivaV2TransactionalKernel kernel,
                                         AivaTransactionCapabilityGate authorizationGate) {
        this.kernel = kernel;
        this.authorizationGate = authorizationGate;
    }

    ProviderCandidate resolveVoiceBookingProvider(java.util.UUID tenantId, ProviderCandidate discovered) {
        return kernel.resolveVoiceBookingProvider(tenantId, discovered);
    }

    AivaV2Models.ProviderSearchResult resolveVoiceProviderEvidence(java.util.UUID tenantId, String evidence) {
        return kernel.resolveVoiceProviderEvidence(tenantId, evidence);
    }

    Result execute(AivaExecutionContext executionContext, SessionProjection session,
                   ConversationDecision decision, AivaV2TemporalResolution temporal,
                   ZoneId timezone, String turnId, boolean ambiguousTime) {
        AivaTransactionCapabilityGate.Decision authorization = authorizationGate.authorize(
                decision.operation(), executionContext);
        if (!authorization.allowed()) return new Result(null, authorization);
        return new Result(kernel.handle(session, decision, temporal, timezone, turnId, ambiguousTime), authorization);
    }

    Result prepareGuestBookingOnly(AivaExecutionContext executionContext, SessionProjection session,
                                   VoiceGuestRegistrationState guest, ConversationDecision decision,
                                   AivaV2TemporalResolution temporal, ZoneId timezone, String turnId,
                                   boolean ambiguousTime) {
        if (decision.operation() == AivaV2Models.Operation.END_CONVERSATION && session != null
                && "VOICE".equalsIgnoreCase(executionContext.channel())
                && executionContext.tenantId().toString().equals(session.tenantId())) {
            return new Result(kernel.handle(session, decision, temporal, timezone, turnId, ambiguousTime),
                    new AivaTransactionCapabilityGate.Decision(null,
                            AivaTransactionCapabilityGate.Outcome.ALLOWED, "SESSION_END"));
        }
        AivaTransactionCapabilityGate.Decision authorization = authorizationGate
                .authorizeGuestBookingPreparation(decision.operation(), guest, executionContext);
        if (!authorization.allowed()) {
            if (authorization.denialReason() != null
                    && "GUEST_CAPABILITY_SCOPE_DENIED".equals(authorization.denialReason())
                    && (decision.operation() == AivaV2Models.Operation.CONFIRM_BOOKING
                    || decision.confirmation() == AivaV2Models.ConfirmationPolarity.POSITIVE)) {
                return new Result(kernel.prepareOnly(session, decision, temporal, timezone, turnId, ambiguousTime),
                        new AivaTransactionCapabilityGate.Decision(
                                AivaTransactionAuthorization.Capability.GUEST_BOOK_APPOINTMENT,
                                AivaTransactionCapabilityGate.Outcome.DENIED, "TRANSACTION_EXECUTION_DISABLED"));
            }
            return new Result(null, authorization);
        }
        if (session == null || guest.patientId() == null || !guest.tenantId().toString().equals(session.tenantId())) {
            return new Result(null, new AivaTransactionCapabilityGate.Decision(
                    AivaTransactionAuthorization.Capability.GUEST_BOOK_APPOINTMENT,
                    AivaTransactionCapabilityGate.Outcome.DENIED, "SESSION_TENANT_MISMATCH"));
        }
        if (decision.operation() == AivaV2Models.Operation.CONFIRM_BOOKING
                || decision.confirmation() == AivaV2Models.ConfirmationPolarity.POSITIVE) {
            return new Result(kernel.prepareOnly(session, decision, temporal, timezone, turnId, ambiguousTime),
                    new AivaTransactionCapabilityGate.Decision(
                            AivaTransactionAuthorization.Capability.GUEST_BOOK_APPOINTMENT,
                            AivaTransactionCapabilityGate.Outcome.DENIED, "TRANSACTION_EXECUTION_DISABLED"));
        }
        SessionProjection bookingSession = session.patientId() == null
                ? session.withPatientId(guest.patientId()) : session;
        if (!guest.patientId().equals(bookingSession.patientId())) {
            return new Result(null, new AivaTransactionCapabilityGate.Decision(
                    AivaTransactionAuthorization.Capability.GUEST_BOOK_APPOINTMENT,
                    AivaTransactionCapabilityGate.Outcome.DENIED, "BOOKING_PATIENT_BINDING_MISMATCH"));
        }
        return new Result(kernel.prepareOnly(bookingSession, decision, temporal, timezone, turnId, ambiguousTime),
                authorization);
    }

    Result prepareAuthorizedVoiceBookingOnly(AivaExecutionContext executionContext, SessionProjection session,
            ConversationDecision decision, AivaV2TemporalResolution temporal, ZoneId timezone,
            String turnId, boolean ambiguousTime, VoiceTransactionAuthPolicy policy,
            BookingExecutionContext bookingContext) {
        if (executionContext == null || session == null || decision == null || policy == null
                || bookingContext == null || !policy.bookingExecutionAllowed()
                || !"VOICE".equalsIgnoreCase(executionContext.channel())
                || !executionContext.tenantId().equals(bookingContext.tenantId())
                || !java.util.Objects.equals(executionContext.providerCallId(), bookingContext.providerCallId())
                || !java.util.Objects.equals(executionContext.sessionId(), bookingContext.sessionId())
                || !java.util.Objects.equals(executionContext.interactionId(), bookingContext.interactionId())
                || !session.tenantId().equals(bookingContext.tenantId().toString())
                || !java.util.Objects.equals(session.patientId(), bookingContext.patientId())
                || bookingContext.allowedOperation() != BookingExecutionContext.Operation.BOOK_APPOINTMENT) {
            return new Result(null, new AivaTransactionCapabilityGate.Decision(
                    AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT,
                    AivaTransactionCapabilityGate.Outcome.DENIED, "BOOKING_POLICY_OR_CONTEXT_DENIED"));
        }
        return new Result(kernel.prepareOnly(session, decision, temporal, timezone, turnId, ambiguousTime),
                new AivaTransactionCapabilityGate.Decision(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT,
                        AivaTransactionCapabilityGate.Outcome.ALLOWED, "BOOKING_PREPARATION_ONLY"));
    }

    record Result(AivaV2TransactionalKernel.KernelResult kernelResult,
                  AivaTransactionCapabilityGate.Decision authorization) { }
}
