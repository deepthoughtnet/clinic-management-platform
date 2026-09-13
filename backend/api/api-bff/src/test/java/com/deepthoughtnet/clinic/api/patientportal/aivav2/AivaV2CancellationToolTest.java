package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AivaV2CancellationToolTest {
    @Test
    void preparationCreatesServerHeldReferenceAndStableCommandKey() {
        AivaV2CancellationTool tool = new AivaV2CancellationTool(mock(PatientPortalService.class));
        AppointmentSummary appointment = new AppointmentSummary(
                UUID.randomUUID().toString(), "Doc Akshu Kumar", "NewAuto Lab",
                LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), "BOOKED", null);

        var result = tool.prepare(appointment, Instant.parse("2026-09-12T10:00:00Z"));

        assertThat(result.category()).isEqualTo("SUCCESS");
        assertThat(result.value().appointmentReference()).isEqualTo(appointment.appointmentReference());
        assertThat(result.value().confirmationRef()).startsWith("aiva-v2-cancel-");
        assertThat(result.value().idempotencyKey()).startsWith("aiva-v2-cancel-");
        assertThat(result.value().expiresAt()).isEqualTo(Instant.parse("2026-09-12T10:05:00Z"));
    }
}
