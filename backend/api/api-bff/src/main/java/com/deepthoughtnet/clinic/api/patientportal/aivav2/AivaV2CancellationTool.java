package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentSummary;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.CancellationConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AivaV2CancellationTool {
    private final PatientPortalService patientPortalService;

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
                    appointment.time(), now.plusSeconds(5 * 60), "aiva-v2-cancel-" + UUID.randomUUID()));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failure("FAILED", "The appointment reference is invalid.");
        }
    }

    ToolResult<String> cancel(CancellationConfirmation confirmation) {
        if (confirmation == null || confirmation.expiresAt() == null
                || confirmation.expiresAt().isBefore(Instant.now())) {
            return ToolResult.failure("STALE", "The cancellation confirmation has expired.");
        }
        try {
            var response = patientPortalService.cancelAppointment(
                    confirmation.appointmentId(), "Cancelled by patient", confirmation.idempotencyKey());
            return ToolResult.success(response.message());
        } catch (RuntimeException ex) {
            return ToolResult.failure("STALE", "The appointment is no longer cancellable.");
        }
    }
}
