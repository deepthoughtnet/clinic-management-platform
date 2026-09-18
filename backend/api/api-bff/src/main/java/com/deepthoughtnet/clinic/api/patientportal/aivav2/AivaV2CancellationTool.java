package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentSummary;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.CancellationConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AivaV2CancellationTool {
    private static final Logger log = LoggerFactory.getLogger(AivaV2CancellationTool.class);
    private final PatientPortalService patientPortalService;

    enum StaleReason { NONE, CAPABILITY_EXPIRED, DOMAIN_CANCEL_REJECTED }

    record CancellationAttempt(ToolResult<String> result, StaleReason staleReason,
                               boolean domainInvocationReached, Instant currentInstant,
                               String exceptionType) { }

    AivaV2CancellationTool(PatientPortalService patientPortalService) {
        this.patientPortalService = patientPortalService;
    }

    ToolResult<CancellationConfirmation> prepare(AppointmentSummary appointment, Instant now) {
        if (appointment == null || appointment.appointmentReference() == null) {
            return ToolResult.failure("NOT_FOUND", "No cancellable appointment was found.");
        }
        try {
            UUID appointmentId = UUID.fromString(appointment.appointmentReference());
            return ToolResult.success(new CancellationConfirmation(
                    "aiva-v2-cancel-" + UUID.randomUUID(), appointmentId, appointment.appointmentReference(),
                    appointment.doctorDisplayName(), appointment.clinicDisplayName(), appointment.date(),
                    appointment.time(), now.plusSeconds(5 * 60), "aiva-v2-cancel-" + UUID.randomUUID(),
                    parseUuid(appointment.tenantId())));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failure("FAILED", "The appointment reference is invalid.");
        }
    }

    CancellationAttempt cancel(CancellationConfirmation confirmation, String conversationId, long sessionVersion) {
        Instant now = Instant.now();
        if (confirmation == null || confirmation.expiresAt() == null
                || confirmation.expiresAt().isBefore(now)) {
            log.info("AIVA_V2_CANCELLATION_CONFIRMATION conversationId={} activeWorkflow=CANCELLATION "
                            + "sessionVersion={} pendingCancellationPresent={} confirmationRefPresent={} "
                            + "appointmentRefPresent={} expiresAt={} currentInstant={} domainInvocationReached=false "
                            + "staleReason=CAPABILITY_EXPIRED",
                    conversationId, sessionVersion, confirmation != null,
                    confirmation != null && confirmation.confirmationRef() != null,
                    confirmation != null && confirmation.appointmentReference() != null,
                    confirmation == null ? null : confirmation.expiresAt(), now);
            return new CancellationAttempt(ToolResult.failure("STALE", "The cancellation confirmation has expired."),
                    StaleReason.CAPABILITY_EXPIRED, false, now, null);
        }
        try {
            var response = confirmation.owningTenantId() == null
                    ? patientPortalService.cancelAppointment(confirmation.appointmentId(), "Cancelled by patient", confirmation.idempotencyKey())
                    : patientPortalService.cancelAppointmentInOwningTenant(confirmation.appointmentId(),
                    confirmation.owningTenantId(), "Cancelled by patient", confirmation.idempotencyKey());
            log.info("AIVA_V2_CANCELLATION_CONFIRMATION conversationId={} activeWorkflow=CANCELLATION "
                            + "sessionVersion={} pendingCancellationPresent=true confirmationRefPresent={} "
                            + "appointmentRefPresent={} expiresAt={} currentInstant={} domainInvocationReached=true "
                            + "staleReason=NONE domainOutcome=RETURNED_SUCCESS idempotencyOutcome=REQUEST_RETURNED",
                    conversationId, sessionVersion, confirmation.confirmationRef() != null,
                    confirmation.appointmentReference() != null, confirmation.expiresAt(), now);
            return new CancellationAttempt(ToolResult.success(response.message()), StaleReason.NONE, true, now, null);
        } catch (RuntimeException ex) {
            log.info("AIVA_V2_CANCELLATION_CONFIRMATION conversationId={} activeWorkflow=CANCELLATION "
                            + "sessionVersion={} pendingCancellationPresent=true confirmationRefPresent={} "
                            + "appointmentRefPresent={} expiresAt={} currentInstant={} domainInvocationReached=true "
                            + "staleReason=DOMAIN_CANCEL_REJECTED exceptionType={} domainOutcome=THREW "
                            + "appointmentStateCategory=UNKNOWN idempotencyOutcome=UNKNOWN commitStatus=UNKNOWN",
                    conversationId, sessionVersion, confirmation.confirmationRef() != null,
                    confirmation.appointmentReference() != null, confirmation.expiresAt(), now,
                    ex.getClass().getSimpleName());
            return new CancellationAttempt(ToolResult.failure("STALE", "The appointment is no longer cancellable."),
                    StaleReason.DOMAIN_CANCEL_REJECTED, true, now, ex.getClass().getSimpleName());
        }
    }

    private UUID parseUuid(String value) {
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
