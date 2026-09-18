package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AivaV2RescheduleToolsTest {
    private final AivaV2RescheduleTools tools = new AivaV2RescheduleTools(null, null,
            Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void ordinalSelectionUsesTheVisiblePageOffset() {
        AvailabilityResult availability = availability(3);

        assertThat(tools.select(availability, new Selection(null, 1, null, null)).value().slotReference())
                .isEqualTo("slot-4");
        assertThat(tools.select(availability, new Selection(null, 3, null, null)).value().slotReference())
                .isEqualTo("slot-6");
    }

    @Test
    void ordinalOutsideVisiblePageIsRejected() {
        var result = tools.select(availability(3), new Selection(null, 4, null, null));

        assertThat(result.category()).isEqualTo("NEEDS_INPUT");
    }

    @Test
    void classifiesSlotUnavailable() {
        assertThat(confirmWith(new IllegalArgumentException("Selected slot is no longer available")))
                .extracting(result -> result.category(), result -> result.safeReason())
                .containsExactly("SLOT_NO_LONGER_AVAILABLE", "That slot is no longer available. Please choose another time.");
    }

    @Test
    void classifiesSourceAppointmentMissing() {
        assertThat(confirmWith(new IllegalArgumentException("Upcoming appointment not found")))
                .extracting(result -> result.category(), result -> result.safeReason())
                .containsExactly("SOURCE_APPOINTMENT_MISSING", "That appointment is no longer available to reschedule.");
    }

    @Test
    void classifiesChangedSourceAppointment() {
        assertThat(confirmWith(new IllegalArgumentException("Only booked appointments can be rescheduled")))
                .extracting(result -> result.category(), result -> result.safeReason())
                .containsExactly("SOURCE_APPOINTMENT_CHANGED", "That appointment is no longer available to reschedule.");
    }

    @Test
    void classifiesDuplicateAppointmentConflict() {
        assertThat(confirmWith(new IllegalArgumentException("Appointment already exists for this patient")))
                .extracting(result -> result.category(), result -> result.safeReason())
                .containsExactly("DUPLICATE_APPOINTMENT_CONFLICT", "That slot is no longer available. Please choose another time.");
    }

    @Test
    void classifiesAuthorizationFailure() {
        assertThat(confirmWith(new ResponseStatusException(HttpStatus.FORBIDDEN)))
                .extracting(result -> result.category(), result -> result.safeReason())
                .containsExactly("AUTHORIZATION_FAILURE", "The reschedule could not be completed right now.");
    }

    @Test
    void classifiesUnknownRuntimeFailureWithoutCallingItStale() {
        assertThat(confirmWith(new IllegalStateException("unexpected failure")))
                .extracting(result -> result.category(), result -> result.safeReason())
                .containsExactly("OTHER_DOMAIN_FAILURE", "The reschedule could not be completed right now.");
    }

    private ToolResult<String> confirmWith(RuntimeException failure) {
        PatientPortalService patientPortalService = mock(PatientPortalService.class);
        when(patientPortalService.rescheduleAppointmentInOwningTenant(any(UUID.class), any(UUID.class),
                any(LocalDate.class), any(LocalTime.class), anyString(), anyString())).thenThrow(failure);
        AivaV2RescheduleTools subject = new AivaV2RescheduleTools(patientPortalService, null,
                Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC));
        return subject.confirm(confirmation(), "conversation-1", "turn-1");
    }

    private RescheduleConfirmation confirmation() {
        return new RescheduleConfirmation("confirmation-1", UUID.randomUUID().toString(), "provider",
                "doctor", "clinic", "00000000-0000-0000-0000-000000000099", UUID.randomUUID(), "slot-1", LocalDate.of(2026, 9, 23),
                LocalTime.of(20, 0), 3, Instant.parse("2026-09-13T00:00:00Z"),
                Instant.parse("2026-09-13T00:05:00Z"), "command-1");
    }

    private AvailabilityResult availability(int offset) {
        return new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1, "criteria", "provider",
                "doctor", "clinic", LocalDate.of(2026, 9, 18), "evening", null, "Asia/Kolkata", List.of(
                slot("slot-1", "17:30"), slot("slot-2", "18:00"), slot("slot-3", "18:30"),
                slot("slot-4", "19:00"), slot("slot-5", "19:30"), slot("slot-6", "20:00")), null,
                true, Instant.now(), Instant.now().plusSeconds(600), offset, 3);
    }

    private AvailabilitySlot slot(String reference, String time) {
        LocalTime start = LocalTime.parse(time);
        return new AvailabilitySlot(reference, start, start.plusMinutes(30), time);
    }
}
