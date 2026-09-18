package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentSummary;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleResolution;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side reschedule orchestration; it never trusts model-supplied IDs. */
@Service
class AivaV2RescheduleTools {
    private static final long TTL_SECONDS = 5 * 60;
    private static final Logger log = LoggerFactory.getLogger(AivaV2RescheduleTools.class);
    private final PatientPortalService patientPortalService;
    private final AivaV2BookingTools bookingTools;
    private final Clock clock;

    @Autowired
    AivaV2RescheduleTools(PatientPortalService patientPortalService, AivaV2BookingTools bookingTools) {
        this(patientPortalService, bookingTools, Clock.systemUTC());
    }

    AivaV2RescheduleTools(PatientPortalService patientPortalService, AivaV2BookingTools bookingTools, Clock clock) {
        this.patientPortalService = patientPortalService;
        this.bookingTools = bookingTools;
        this.clock = clock;
    }

    ProviderCandidate providerFor(AppointmentSummary appointment) {
        if (appointment == null || !StringUtils.hasText(appointment.doctorId())
                || !StringUtils.hasText(appointment.tenantId())) return null;
        return new ProviderCandidate(
                "reschedule-" + appointment.doctorId(), appointment.doctorId(), appointment.doctorId(),
                appointment.clinicId(), appointment.tenantId(), appointment.clinicSlug(), null,
                appointment.doctorDisplayName(), null, appointment.clinicDisplayName(), ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, false, true, false));
    }

    ToolResult<AvailabilityResult> availability(RescheduleResolution state) {
        if (state == null || state.targetDate() == null) {
            return ToolResult.failure("NEEDS_INPUT", "A target date is required.");
        }
        return bookingTools.getAvailability(providerFor(new AppointmentSummary(
                state.sourceAppointmentReference(), state.originalDoctorDisplayName(), null,
                state.originalDate(), state.originalStartsAt(), "BOOKED", null, state.doctorId(),
                state.clinicId(), state.tenantId(), state.clinicSlug())), state.targetDate(), null,
                state.availabilityConstraint(), UUID.nameUUIDFromBytes(
                        ("reschedule:" + state.sourceAppointmentReference()).getBytes(StandardCharsets.UTF_8)),
                state.revision(), null);
    }

    ToolResult<AvailabilitySlot> select(AvailabilityResult availability, Selection selection) {
        if (availability == null || selection == null) {
            return ToolResult.failure("STALE", "Those reschedule slots are no longer current.");
        }
        AvailabilitySlot selected = null;
        if (StringUtils.hasText(selection.slotRef())) {
            selected = availability.slots().stream().filter(s -> selection.slotRef().equals(s.slotReference()))
                    .findFirst().orElse(null);
        } else if (StringUtils.hasText(selection.exactTime())) {
            selected = availability.slots().stream().filter(s -> selection.exactTime().equals(s.startsAt().toString()))
                    .findFirst().orElse(null);
        } else if (selection.ordinal() != null) {
            int index = selection.ordinal() == -1
                    ? Math.min(availability.slots().size(), availability.displayOffset() + availability.pageSize()) - 1
                    : availability.displayOffset() + selection.ordinal() - 1;
            int pageEnd = Math.min(availability.slots().size(), availability.displayOffset() + availability.pageSize());
            selected = index >= availability.displayOffset() && index < pageEnd
                    ? availability.slots().get(index) : null;
        }
        return selected == null ? ToolResult.failure("NEEDS_INPUT", "Select one of the current reschedule slots.")
                : ToolResult.success(selected);
    }

    ToolResult<RescheduleConfirmation> prepare(RescheduleResolution state, AvailabilityResult availability,
                                                AvailabilitySlot slot) {
        if (state == null || availability == null || slot == null || state.targetDate() == null
                || !availability.date().equals(state.targetDate())
                || !slot.slotReference().equals(state.selectedSlotReference())) {
            return ToolResult.failure("STALE", "The selected reschedule slot is no longer current.");
        }
        Instant now = Instant.now(clock);
        String ref = "v2-reschedule-confirm-" + UUID.randomUUID();
        return ToolResult.success(new RescheduleConfirmation(ref, state.sourceAppointmentReference(), state.providerHandle(),
                state.doctorId(), state.clinicId(), state.tenantId(), availability.requestId(), slot.slotReference(),
                state.targetDate(), slot.startsAt(), state.revision(), now, now.plusSeconds(TTL_SECONDS),
                "aiva-v2-reschedule-" + UUID.nameUUIDFromBytes(ref.getBytes(StandardCharsets.UTF_8))));
    }

    ToolResult<String> confirm(RescheduleConfirmation confirmation) {
        return confirm(confirmation, null, null);
    }

    ToolResult<String> confirm(RescheduleConfirmation confirmation, String conversationId, String turnId) {
        if (confirmation == null || confirmation.expiresAt() == null
                || !confirmation.expiresAt().isAfter(Instant.now(clock))) {
            traceFailure(conversationId, turnId, confirmation, "CONFIRMATION_EXPIRED", IllegalStateException.class);
            return ToolResult.failure("STALE", "The reschedule confirmation has expired.");
        }
        try {
            patientPortalService.rescheduleAppointmentInOwningTenant(
                    UUID.fromString(confirmation.sourceAppointmentReference()),
                    UUID.fromString(confirmation.tenantId()), confirmation.targetDate(), confirmation.targetStartsAt(),
                    "Rescheduled by patient", confirmation.commandId());
            return ToolResult.success("RESCHEDULED");
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            String category = classify(ex);
            traceFailure(conversationId, turnId, confirmation, category, ex.getClass());
            return ToolResult.failure(category, userMessage(category));
        } catch (RuntimeException ex) {
            String category = classify(ex);
            traceFailure(conversationId, turnId, confirmation, category, ex.getClass());
            return ToolResult.failure(category, userMessage(category));
        }
    }

    private String classify(RuntimeException exception) {
        if (exception instanceof org.springframework.web.server.ResponseStatusException statusException) {
            HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) return "AUTHORIZATION_FAILURE";
            if (status == HttpStatus.NOT_FOUND) return "SOURCE_APPOINTMENT_MISSING";
            if (status == HttpStatus.CONFLICT) {
                String reason = safeExceptionText(statusException);
                return reason.contains("duplicate") || reason.contains("already exists")
                        ? "DUPLICATE_APPOINTMENT_CONFLICT" : "SLOT_NO_LONGER_AVAILABLE";
            }
            if (status == HttpStatus.BAD_REQUEST) return "VALIDATION_FAILURE";
        }
        String message = safeExceptionText(exception);
        if (message.contains("appointment not found") || message.contains("upcoming appointment not found")) {
            return "SOURCE_APPOINTMENT_MISSING";
        }
        if (message.contains("only booked appointments") || message.contains("not actionable")
                || message.contains("already changed") || message.contains("status")) {
            return "SOURCE_APPOINTMENT_CHANGED";
        }
        if (message.contains("selected slot is no longer available") || message.contains("slot is no longer available")) {
            return "SLOT_NO_LONGER_AVAILABLE";
        }
        if (message.contains("already exists") || message.contains("duplicate")) {
            return "DUPLICATE_APPOINTMENT_CONFLICT";
        }
        if (message.contains("tenant")) return "TENANT_SCOPE_MISMATCH";
        if (message.contains("clinic")) return "CLINIC_SCOPE_MISMATCH";
        if (message.contains("access") || message.contains("authorized") || message.contains("permission")) {
            return "AUTHORIZATION_FAILURE";
        }
        if (message.contains("required") || message.contains("invalid") || message.contains("past")) {
            return "VALIDATION_FAILURE";
        }
        return "OTHER_DOMAIN_FAILURE";
    }

    private String safeExceptionText(RuntimeException exception) {
        return exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
    }

    private String userMessage(String category) {
        return switch (category) {
            case "SLOT_NO_LONGER_AVAILABLE", "DUPLICATE_APPOINTMENT_CONFLICT" ->
                    "That slot is no longer available. Please choose another time.";
            case "SOURCE_APPOINTMENT_MISSING", "SOURCE_APPOINTMENT_CHANGED", "SOURCE_NOT_ACTIONABLE" ->
                    "That appointment is no longer available to reschedule.";
            default -> "The reschedule could not be completed right now.";
        };
    }

    private void traceFailure(String conversationId, String turnId, RescheduleConfirmation confirmation,
                              String category, Class<?> exceptionClass) {
        log.warn("AIVA_V2_RESCHEDULE_DOMAIN_FAILURE conversationId={} turnId={} failureCategory={} "
                        + "exceptionClass={} domainOperation=RESCHEDULE sourceBound={} targetDate={} targetTime={} "
                        + "owningClinicPresent={} owningTenantPresent={}",
                conversationId, turnId, category, exceptionClass == null ? null : exceptionClass.getSimpleName(),
                confirmation != null && StringUtils.hasText(confirmation.sourceAppointmentReference()),
                confirmation == null ? null : confirmation.targetDate(),
                confirmation == null ? null : confirmation.targetStartsAt(),
                confirmation != null && StringUtils.hasText(confirmation.clinicId()),
                confirmation != null && StringUtils.hasText(confirmation.tenantId()));
    }
}
