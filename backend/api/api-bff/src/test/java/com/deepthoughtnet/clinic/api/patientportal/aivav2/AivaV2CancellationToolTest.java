package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentSummary;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.CancellationConfirmation;
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

    @Test
    void expiredCapabilityIsDistinguishedAndDoesNotReachDomain() {
        PatientPortalService portal = mock(PatientPortalService.class);
        AivaV2CancellationTool tool = new AivaV2CancellationTool(portal);
        var attempt = tool.cancel(confirmation(Instant.now().minusSeconds(1)), "conversation-a", 8L);

        assertThat(attempt.result().category()).isEqualTo("STALE");
        assertThat(attempt.staleReason()).isEqualTo(AivaV2CancellationTool.StaleReason.CAPABILITY_EXPIRED);
        assertThat(attempt.domainInvocationReached()).isFalse();
        verifyNoInteractions(portal);
    }

    @Test
    void domainRejectionIsDistinguishedAfterInvocation() {
        PatientPortalService portal = mock(PatientPortalService.class);
        when(portal.cancelAppointment(any(), any(), any())).thenThrow(new IllegalStateException("private details"));
        AivaV2CancellationTool tool = new AivaV2CancellationTool(portal);

        var attempt = tool.cancel(confirmation(Instant.now().plusSeconds(60)), "conversation-a", 8L);

        assertThat(attempt.result().category()).isEqualTo("STALE");
        assertThat(attempt.staleReason()).isEqualTo(AivaV2CancellationTool.StaleReason.DOMAIN_CANCEL_REJECTED);
        assertThat(attempt.domainInvocationReached()).isTrue();
        assertThat(attempt.exceptionType()).isEqualTo("IllegalStateException");
        verify(portal, times(1)).cancelAppointment(any(), eq("Cancelled by patient"), any());
    }

    @Test
    void ownerTenantCapabilityUsesOwnerAwareCancellationBoundary() {
        PatientPortalService portal = mock(PatientPortalService.class);
        UUID ownerTenant = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        when(portal.cancelAppointmentInOwningTenant(eq(appointmentId), eq(ownerTenant), anyString(), anyString()))
                .thenReturn(new com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse(
                        LocalDate.of(2026, 9, 24), LocalTime.of(20, 30), "Doctor", "Clinic", "ref", "CANCELLED", null, "ok"));
        AivaV2CancellationTool tool = new AivaV2CancellationTool(portal);
        AppointmentSummary appointment = new AppointmentSummary(appointmentId.toString(), "Doctor", "Clinic",
                LocalDate.of(2026, 9, 24), LocalTime.of(20, 30), "BOOKED", null, null, null,
                ownerTenant.toString(), null);

        CancellationConfirmation confirmation = tool.prepare(appointment, Instant.now()).value();
        var attempt = tool.cancel(confirmation, "conversation-a", 3L);

        assertThat(attempt.result().category()).isEqualTo("SUCCESS");
        verify(portal).cancelAppointmentInOwningTenant(eq(appointmentId), eq(ownerTenant),
                eq("Cancelled by patient"), anyString());
        verify(portal, never()).cancelAppointment(any(), any(), any());
    }

    private CancellationConfirmation confirmation(Instant expiresAt) {
        return new CancellationConfirmation("opaque-confirmation", UUID.randomUUID(), "opaque-appointment",
                "Doctor", "Clinic", LocalDate.of(2026, 9, 24), LocalTime.of(20, 30), expiresAt,
                "opaque-idempotency");
    }
}
