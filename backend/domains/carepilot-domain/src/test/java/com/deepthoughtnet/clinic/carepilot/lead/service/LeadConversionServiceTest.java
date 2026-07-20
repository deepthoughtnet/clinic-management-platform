package com.deepthoughtnet.clinic.carepilot.lead.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadConversionService;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadAppointmentBookingCommand;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeadConversionServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private LeadRepository leadRepository;
    private PatientRepository patientRepository;
    private PatientService patientService;
    private AppointmentService appointmentService;
    private LeadActivityService activityService;
    private LeadService leadService;
    private LeadConversionService service;

    @BeforeEach
    void setUp() {
        leadRepository = mock(LeadRepository.class);
        patientRepository = mock(PatientRepository.class);
        patientService = mock(PatientService.class);
        appointmentService = mock(AppointmentService.class);
        activityService = mock(LeadActivityService.class);
        leadService = mock(LeadService.class);
        service = new LeadConversionService(leadRepository, patientRepository, patientService, appointmentService, activityService, leadService);
        when(leadRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void conversionLinksExistingPatientWhenMobileMatches() {
        LeadEntity lead = LeadEntity.create(tenantId, actorId);
        lead.setFirstName("Amy");
        lead.setPhone("+15550123");
        lead.setSource(LeadSource.MANUAL);
        when(leadRepository.findByTenantIdAndId(tenantId, lead.getId())).thenReturn(Optional.of(lead));

        PatientEntity existing = PatientEntity.create(tenantId, "PAT-ABC");
        existing.update("Amy", "", PatientGender.FEMALE, null, null, "+15550123", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, "+15550123")).thenReturn(Optional.of(existing));

        var result = service.convert(tenantId, lead.getId(), actorId, null, false);
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.patientId()).isEqualTo(existing.getId());
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
    }

    @Test
    void conversionLinksExistingPatientWhenEmailMatches() {
        LeadEntity lead = LeadEntity.create(tenantId, actorId);
        lead.setFirstName("Amy");
        lead.setPhone("+15550999");
        lead.setEmail("amy@example.com");
        lead.setSource(LeadSource.MANUAL);
        when(leadRepository.findByTenantIdAndId(tenantId, lead.getId())).thenReturn(Optional.of(lead));
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, "+15550999")).thenReturn(Optional.empty());

        PatientEntity existing = PatientEntity.create(tenantId, "PAT-EMAIL");
        existing.update("Amy", "", PatientGender.FEMALE, null, null, "+15550000", "amy@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findFirstByTenantIdAndEmailIgnoreCaseAndActiveTrue(tenantId, "amy@example.com")).thenReturn(Optional.of(existing));

        var result = service.convert(tenantId, lead.getId(), actorId, null, false);

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.patientId()).isEqualTo(existing.getId());
        verify(patientService, never()).create(any(), any(), any());
    }

    @Test
    void conversionCreatesPatientWhenNoDuplicateFound() {
        LeadEntity lead = LeadEntity.create(tenantId, actorId);
        lead.setFirstName("Bob");
        lead.setPhone("+15559999");
        lead.setSource(LeadSource.WEBSITE);
        when(leadRepository.findByTenantIdAndId(tenantId, lead.getId())).thenReturn(Optional.of(lead));
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, "+15559999")).thenReturn(Optional.empty());

        UUID patientId = UUID.randomUUID();
        when(patientService.create(any(), any(), any())).thenReturn(new PatientRecord(
                patientId, tenantId, "PAT-X", "Bob", "", PatientGender.UNKNOWN, null, null, "+15559999", null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, true,
                OffsetDateTime.now(), OffsetDateTime.now()
        ));

        var result = service.convert(tenantId, lead.getId(), actorId, null, false);
        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.patientId()).isEqualTo(patientId);
    }

    @Test
    void conversionWithUnavailableAppointmentSlotFailsBeforeLeadMutation() {
        LeadEntity lead = LeadEntity.create(tenantId, actorId);
        lead.setFirstName("Cara");
        lead.setPhone("+15558888");
        lead.setSource(LeadSource.WEBSITE);
        when(leadRepository.findByTenantIdAndId(tenantId, lead.getId())).thenReturn(Optional.of(lead));

        LeadAppointmentBookingCommand booking = new LeadAppointmentBookingCommand(
                UUID.randomUUID(),
                LocalDate.now().plusDays(1),
                LocalTime.of(10, 0),
                "Initial consult",
                null,
                AppointmentPriority.NORMAL
        );
        doThrow(new IllegalArgumentException("Selected time is already fully booked"))
                .when(appointmentService)
                .validateScheduledBookingRequest(any(), any(), any(), any(), any(), any(), anyBoolean());

        assertThatThrownBy(() -> service.convert(tenantId, lead.getId(), actorId, booking, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fully booked");

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.getConvertedPatientId()).isNull();
        verify(patientService, never()).create(any(), any(), any());
        verify(leadRepository, never()).save(any());
        verify(leadService, never()).linkAppointment(any(), any(), any(), any());
    }

    @Test
    void conversionBooksAppointmentWhenSlotIsAvailable() {
        LeadEntity lead = LeadEntity.create(tenantId, actorId);
        lead.setFirstName("Drew");
        lead.setPhone("+15557777");
        lead.setSource(LeadSource.MANUAL);
        when(leadRepository.findByTenantIdAndId(tenantId, lead.getId())).thenReturn(Optional.of(lead));
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, "+15557777")).thenReturn(Optional.empty());

        UUID patientId = UUID.randomUUID();
        when(patientService.create(any(), any(), any())).thenReturn(new PatientRecord(
                patientId, tenantId, "PAT-Y", "Drew", "", PatientGender.UNKNOWN, null, null, "+15557777", null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, true,
                OffsetDateTime.now(), OffsetDateTime.now()
        ));

        UUID appointmentId = UUID.randomUUID();
        when(appointmentService.createScheduled(any(), any(), any(), anyBoolean())).thenReturn(new AppointmentRecord(
                appointmentId,
                tenantId,
                patientId,
                "PAT-Y",
                "Drew",
                "+15557777",
                UUID.randomUUID(),
                "Doctor",
                null,
                LocalDate.now().plusDays(1),
                LocalTime.of(11, 0),
                null,
                "Initial consult",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        LeadAppointmentBookingCommand booking = new LeadAppointmentBookingCommand(
                UUID.randomUUID(),
                LocalDate.now().plusDays(1),
                LocalTime.of(11, 0),
                "Initial consult",
                null,
                AppointmentPriority.NORMAL
        );

        var result = service.convert(tenantId, lead.getId(), actorId, booking, false);

        assertThat(result.patientId()).isEqualTo(patientId);
        assertThat(result.appointmentId()).isEqualTo(appointmentId);
        assertThat(result.appointmentError()).isNull();
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        verify(appointmentService).validateScheduledBookingRequest(eq(tenantId), any(), any(), any(), any(), any(), eq(false));
        verify(leadService).linkAppointment(tenantId, lead.getId(), appointmentId, actorId);
    }

    @Test
    void conversionClearsPendingFollowUpAndIsIdempotent() {
        LeadEntity lead = LeadEntity.create(tenantId, actorId);
        lead.setFirstName("Eve");
        lead.setPhone("+15556666");
        lead.setSource(LeadSource.WEBSITE);
        lead.setNextFollowUpAt(OffsetDateTime.parse("2026-07-20T10:30:00+05:30"));
        when(leadRepository.findByTenantIdAndId(tenantId, lead.getId())).thenReturn(Optional.of(lead));
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, "+15556666")).thenReturn(Optional.empty());

        UUID patientId = UUID.randomUUID();
        when(patientService.create(any(), any(), any())).thenReturn(new PatientRecord(
                patientId, tenantId, "PAT-Z", "Eve", "", PatientGender.UNKNOWN, null, null, "+15556666", null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, true,
                OffsetDateTime.now(), OffsetDateTime.now()
        ));

        var first = service.convert(tenantId, lead.getId(), actorId, null, false);
        var second = service.convert(tenantId, lead.getId(), actorId, null, false);

        assertThat(first.newlyCreated()).isTrue();
        assertThat(first.patientId()).isEqualTo(patientId);
        assertThat(second.newlyCreated()).isFalse();
        assertThat(second.patientId()).isEqualTo(patientId);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        assertThat(lead.getNextFollowUpAt()).isNull();
        verify(activityService, times(2)).record(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(actorId));
        verify(leadRepository, times(1)).save(lead);
    }
}
