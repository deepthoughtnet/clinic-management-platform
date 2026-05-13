package com.deepthoughtnet.clinic.carepilot.lead.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadConversionService;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
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
}
