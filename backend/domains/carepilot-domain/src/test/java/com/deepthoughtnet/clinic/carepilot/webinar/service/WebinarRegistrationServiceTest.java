package com.deepthoughtnet.clinic.carepilot.webinar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarAttendanceCommand;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarRegistrationCommand;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarRegistrationService;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebinarRegistrationServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private WebinarRepository webinarRepository;
    private WebinarRegistrationRepository registrationRepository;
    private PatientRepository patientRepository;
    private LeadRepository leadRepository;
    private WebinarRegistrationService service;
    private WebinarEntity webinar;

    @BeforeEach
    void setUp() {
        webinarRepository = mock(WebinarRepository.class);
        registrationRepository = mock(WebinarRegistrationRepository.class);
        patientRepository = mock(PatientRepository.class);
        leadRepository = mock(LeadRepository.class);
        service = new WebinarRegistrationService(webinarRepository, registrationRepository, patientRepository, leadRepository);

        webinar = WebinarEntity.create(tenantId, UUID.randomUUID());
        webinar.setTitle("Webinar");
        webinar.setScheduledStartAt(OffsetDateTime.now().plusDays(1));
        webinar.setScheduledEndAt(OffsetDateTime.now().plusDays(1).plusHours(1));
        webinar.setStatus(WebinarStatus.SCHEDULED);
        when(webinarRepository.findByTenantIdAndId(tenantId, webinar.getId())).thenReturn(Optional.of(webinar));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registerAttendeeAndMarkAttendance() {
        PatientEntity patient = PatientEntity.create(tenantId, "P1");
        patient.update("A", "B", null, null, null, "9999999999", "a@b.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findByTenantIdAndId(tenantId, patient.getId())).thenReturn(Optional.of(patient));
        when(registrationRepository.countByTenantIdAndWebinarId(tenantId, webinar.getId())).thenReturn(0L);
        when(registrationRepository.countByTenantIdAndWebinarIdAndRegistrationStatus(tenantId, webinar.getId(), WebinarRegistrationStatus.CANCELLED)).thenReturn(0L);

        var created = service.register(tenantId, webinar.getId(), new WebinarRegistrationCommand(
                patient.getId(), null, "Attendee", "attendee@example.com", "9999999999", null, null
        ));

        assertThat(created.registrationStatus()).isEqualTo(WebinarRegistrationStatus.REGISTERED);

        var row = com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationEntity.create(tenantId, webinar.getId());
        row.setAttendeeName("Attendee");
        when(registrationRepository.findById(created.id())).thenReturn(Optional.of(row));

        var attended = service.markAttendance(tenantId, webinar.getId(), created.id(), new WebinarAttendanceCommand(WebinarRegistrationStatus.ATTENDED, null));
        assertThat(attended.attended()).isTrue();
        assertThat(attended.registrationStatus()).isEqualTo(WebinarRegistrationStatus.ATTENDED);
    }
}
