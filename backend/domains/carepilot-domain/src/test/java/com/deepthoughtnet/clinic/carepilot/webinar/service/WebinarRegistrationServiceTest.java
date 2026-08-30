package com.deepthoughtnet.clinic.carepilot.webinar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.intake.LeadIntakeService;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarAttendanceCommand;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarRegistrationCommand;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarRegistrationService;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.time.OffsetDateTime;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class WebinarRegistrationServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private WebinarRepository webinarRepository;
    private WebinarRegistrationRepository registrationRepository;
    private PatientRepository patientRepository;
    private LeadRepository leadRepository;
    private LeadIntakeService leadIntakeService;
    private LeadActivityService leadActivityService;
    private CampaignRepository campaignRepository;
    private WebinarRegistrationService service;
    private WebinarEntity webinar;
    private CampaignEntity campaign;

    @BeforeEach
    void setUp() {
        webinarRepository = mock(WebinarRepository.class);
        registrationRepository = mock(WebinarRegistrationRepository.class);
        patientRepository = mock(PatientRepository.class);
        leadRepository = mock(LeadRepository.class);
        leadIntakeService = mock(LeadIntakeService.class);
        leadActivityService = mock(LeadActivityService.class);
        campaignRepository = mock(CampaignRepository.class);
        service = new WebinarRegistrationService(
                webinarRepository,
                registrationRepository,
                patientRepository,
                leadRepository,
                leadIntakeService,
                leadActivityService,
                campaignRepository
        );

        webinar = WebinarEntity.create(tenantId, UUID.randomUUID());
        webinar.setTitle("Webinar");
        webinar.setScheduledStartAt(OffsetDateTime.now().plusDays(1));
        webinar.setScheduledEndAt(OffsetDateTime.now().plusDays(1).plusHours(1));
        webinar.setStatus(WebinarStatus.SCHEDULED);

        campaign = CampaignEntity.create(tenantId, "CAM-2026-000001", "Campaign A", null, null, null, null, null, actorId);
        webinar.setCampaignId(campaign.getId());

        when(webinarRepository.findByTenantIdAndId(tenantId, webinar.getId())).thenReturn(Optional.of(webinar));
        when(campaignRepository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationRepository.findByTenantIdAndWebinarIdAndAttendeeEmail(any(), any(), any())).thenReturn(Optional.empty());
        when(registrationRepository.findByTenantIdAndWebinarIdAndAttendeePhone(any(), any(), any())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByTenantIdAndEmailIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByTenantIdAndPhoneIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(leadRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void registerAttendeeAndMarkAttendance() {
        PatientEntity patient = PatientEntity.create(tenantId, "P1");
        patient.update("A", "B", PatientGender.UNKNOWN, null, null, "9999999999", "a@b.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findByTenantIdAndId(tenantId, patient.getId())).thenReturn(Optional.of(patient));
        when(registrationRepository.countByTenantIdAndWebinarId(tenantId, webinar.getId())).thenReturn(0L);
        when(registrationRepository.countByTenantIdAndWebinarIdAndRegistrationStatus(tenantId, webinar.getId(), WebinarRegistrationStatus.CANCELLED)).thenReturn(0L);
        LeadRecord createdLead = leadRecord(UUID.randomUUID(), "Attendee", "One", "9999999999", "attendee@example.com", webinar.getCampaignId(), LeadSource.WEBINAR);
        when(leadIntakeService.intake(eq(tenantId), any(), eq(actorId))).thenReturn(createdLead);

        var created = service.register(tenantId, webinar.getId(), new WebinarRegistrationCommand(
                patient.getId(), null, "Attendee One", "attendee@example.com", "9999999999", null, null
        ), actorId);

        assertThat(created.registrationStatus()).isEqualTo(WebinarRegistrationStatus.REGISTERED);
        assertThat(created.leadId()).isEqualTo(createdLead.id());
        assertThat(created.leadName()).isEqualTo(createdLead.fullName());
        assertThat(created.campaignId()).isEqualTo(webinar.getCampaignId());

        WebinarRegistrationEntity row = WebinarRegistrationEntity.create(tenantId, webinar.getId());
        row.setAttendeeName("Attendee");
        row.setLeadId(createdLead.id());
        when(registrationRepository.findById(created.id())).thenReturn(Optional.of(row));
        webinar.setStatus(WebinarStatus.LIVE);
        LeadEntity savedLead = leadEntity(createdLead);
        when(leadRepository.findByTenantIdAndId(tenantId, createdLead.id())).thenReturn(Optional.of(savedLead));

        var attended = service.markAttendance(tenantId, webinar.getId(), created.id(), new WebinarAttendanceCommand(WebinarRegistrationStatus.ATTENDED, null), actorId);
        assertThat(attended.attended()).isTrue();
        assertThat(attended.registrationStatus()).isEqualTo(WebinarRegistrationStatus.ATTENDED);
        verify(leadActivityService).record(
                eq(tenantId),
                eq(createdLead.id()),
                eq(com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType.WEBINAR_ATTENDED),
                eq("Webinar Attended"),
                eq("Attended webinar: Webinar"),
                isNull(),
                isNull(),
                eq("WEBINAR"),
                eq(webinar.getId()),
                eq(actorId)
        );
    }

    @Test
    void registerCreatesLeadWhenNoMatchingLeadExists() {
        when(registrationRepository.countByTenantIdAndWebinarId(tenantId, webinar.getId())).thenReturn(0L);
        when(registrationRepository.countByTenantIdAndWebinarIdAndRegistrationStatus(tenantId, webinar.getId(), WebinarRegistrationStatus.CANCELLED)).thenReturn(0L);
        LeadRecord createdLead = leadRecord(UUID.randomUUID(), "Manual", "Attendee", "9999999999", "manual@example.com", webinar.getCampaignId(), LeadSource.WEBINAR);
        when(leadIntakeService.intake(eq(tenantId), any(), eq(actorId))).thenReturn(createdLead);

        var created = service.register(tenantId, webinar.getId(), new WebinarRegistrationCommand(
                null, null, "Manual Attendee", "manual@example.com", "9999999999", null, null
        ), actorId);

        assertThat(created.leadId()).isEqualTo(createdLead.id());
        assertThat(created.campaignId()).isEqualTo(webinar.getCampaignId());
        verify(leadIntakeService).intake(eq(tenantId), any(), eq(actorId));
    }

    @Test
    void registerLinksExistingLeadByEmailAndPreservesCampaign() {
        when(registrationRepository.countByTenantIdAndWebinarId(tenantId, webinar.getId())).thenReturn(0L);
        when(registrationRepository.countByTenantIdAndWebinarIdAndRegistrationStatus(tenantId, webinar.getId(), WebinarRegistrationStatus.CANCELLED)).thenReturn(0L);
        UUID existingCampaignId = UUID.randomUUID();
        LeadEntity existingLead = leadEntity(leadRecord(UUID.randomUUID(), "Existing", "Lead", "9999999999", "existing@example.com", existingCampaignId, LeadSource.MANUAL));
        when(leadRepository.findFirstByTenantIdAndEmailIgnoreCase(tenantId, "existing@example.com")).thenReturn(Optional.of(existingLead));

        var created = service.register(tenantId, webinar.getId(), new WebinarRegistrationCommand(
                null, null, "Existing Lead", "existing@example.com", "9999999999", null, null
        ), actorId);

        assertThat(created.leadId()).isEqualTo(existingLead.getId());
        assertThat(created.campaignId()).isEqualTo(existingCampaignId);
        verify(leadRepository, never()).save(existingLead);
        verify(leadIntakeService, never()).intake(eq(tenantId), any(), eq(actorId));
    }

    @Test
    void registerLinksExistingLeadByPhoneAndBackfillsCampaignWhenMissing() {
        when(registrationRepository.countByTenantIdAndWebinarId(tenantId, webinar.getId())).thenReturn(0L);
        when(registrationRepository.countByTenantIdAndWebinarIdAndRegistrationStatus(tenantId, webinar.getId(), WebinarRegistrationStatus.CANCELLED)).thenReturn(0L);
        LeadEntity existingLead = leadEntity(leadRecord(UUID.randomUUID(), "Existing", "Lead", "9999999999", null, null, LeadSource.MANUAL));
        when(leadRepository.findFirstByTenantIdAndPhoneIgnoreCase(tenantId, "9999999999")).thenReturn(Optional.of(existingLead));
        when(leadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.register(tenantId, webinar.getId(), new WebinarRegistrationCommand(
                null, null, "Existing Lead", null, "9999999999", null, null
        ), actorId);

        assertThat(created.leadId()).isEqualTo(existingLead.getId());
        assertThat(created.campaignId()).isEqualTo(webinar.getCampaignId());
        verify(leadRepository).save(existingLead);
    }

    @Test
    void registerReturnsExistingRowForDuplicatePhone() {
        WebinarRegistrationEntity existing = WebinarRegistrationEntity.create(tenantId, webinar.getId());
        existing.setAttendeeName("Existing");
        existing.setAttendeePhone("9999999999");
        when(registrationRepository.findByTenantIdAndWebinarIdAndAttendeePhone(tenantId, webinar.getId(), "9999999999"))
                .thenReturn(Optional.of(existing));

        var created = service.register(tenantId, webinar.getId(), new WebinarRegistrationCommand(
                null, null, "Manual Attendee", null, "9999999999", null, null
        ), actorId);

        assertThat(created.id()).isEqualTo(existing.getId());
        assertThat(created.attendeeName()).isEqualTo("Existing");
    }

    @Test
    void registerRequiresContactDetailsWhenNoPatientOrLeadProvided() {
        assertThatThrownBy(() -> service.register(tenantId, webinar.getId(), new WebinarRegistrationCommand(
                null, null, "Manual Attendee", null, null, null, null
        ), actorId)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("patientId, leadId, attendeeEmail, or attendeePhone is required");
    }

    @Test
    void terminalWebinarRejectsRegistrationAndAttendance() {
        webinar.setStatus(WebinarStatus.COMPLETED);

        assertThatThrownBy(() -> service.register(tenantId, webinar.getId(), new WebinarRegistrationCommand(
                null, null, "Manual Attendee", "manual@example.com", "9999999999", null, null
        ), actorId)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed or cancelled webinar does not accept registrations");

        WebinarRegistrationEntity row = WebinarRegistrationEntity.create(tenantId, webinar.getId());
        row.setAttendeeName("Attendee");
        row.setRegistrationStatus(WebinarRegistrationStatus.REGISTERED);
        when(registrationRepository.findById(row.getId())).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.markAttendance(tenantId, webinar.getId(), row.getId(), new WebinarAttendanceCommand(WebinarRegistrationStatus.ATTENDED, null), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed or cancelled webinar registrations are read-only");
    }

    @Test
    void attendanceRequiresLiveWebinarAndRegisteredRow() {
        webinar.setStatus(WebinarStatus.SCHEDULED);
        WebinarRegistrationEntity row = WebinarRegistrationEntity.create(tenantId, webinar.getId());
        row.setAttendeeName("Attendee");
        row.setRegistrationStatus(WebinarRegistrationStatus.REGISTERED);
        when(registrationRepository.findById(row.getId())).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.markAttendance(tenantId, webinar.getId(), row.getId(), new WebinarAttendanceCommand(WebinarRegistrationStatus.ATTENDED, null), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attendance can only be recorded while the webinar is live");
    }

    @Test
    void attendanceIsIdempotentForRepeatedTerminalTransition() {
        webinar.setStatus(WebinarStatus.LIVE);
        WebinarRegistrationEntity row = WebinarRegistrationEntity.create(tenantId, webinar.getId());
        row.setAttendeeName("Attendee");
        row.setLeadId(UUID.randomUUID());
        row.setRegistrationStatus(WebinarRegistrationStatus.ATTENDED);
        row.setAttended(true);
        when(registrationRepository.findById(row.getId())).thenReturn(Optional.of(row));
        when(leadRepository.findByTenantIdAndId(tenantId, row.getLeadId())).thenReturn(Optional.of(leadEntity(leadRecord(row.getLeadId(), "Att", "End", "9999999999", "att@example.com", webinar.getCampaignId(), LeadSource.WEBINAR))));

        var result = service.markAttendance(tenantId, webinar.getId(), row.getId(), new WebinarAttendanceCommand(WebinarRegistrationStatus.ATTENDED, "ignored"), actorId);

        assertThat(result.registrationStatus()).isEqualTo(WebinarRegistrationStatus.ATTENDED);
        verify(leadActivityService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelledAttendanceCreatesLeadTimelineEvent() {
        webinar.setStatus(WebinarStatus.LIVE);
        WebinarRegistrationEntity row = WebinarRegistrationEntity.create(tenantId, webinar.getId());
        row.setAttendeeName("Attendee");
        row.setLeadId(UUID.randomUUID());
        row.setRegistrationStatus(WebinarRegistrationStatus.REGISTERED);
        when(registrationRepository.findById(row.getId())).thenReturn(Optional.of(row));
        LeadRecord lead = leadRecord(row.getLeadId(), "Att", "End", "9999999999", "att@example.com", webinar.getCampaignId(), LeadSource.WEBINAR);
        when(leadRepository.findByTenantIdAndId(tenantId, row.getLeadId())).thenReturn(Optional.of(leadEntity(lead)));

        var result = service.markAttendance(tenantId, webinar.getId(), row.getId(), new WebinarAttendanceCommand(WebinarRegistrationStatus.CANCELLED, "cancelled by attendee"), actorId);

        assertThat(result.registrationStatus()).isEqualTo(WebinarRegistrationStatus.CANCELLED);
        verify(leadActivityService).record(
                eq(tenantId),
                eq(row.getLeadId()),
                eq(com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType.WEBINAR_REGISTRATION_CANCELLED),
                eq("Webinar Registration Cancelled"),
                eq("Registration cancelled for webinar: Webinar"),
                isNull(),
                isNull(),
                eq("WEBINAR"),
                eq(webinar.getId()),
                eq(actorId)
        );
    }

    @Test
    void listHidesStalePatientLinksWithoutInventingAReplacement() {
        WebinarRegistrationEntity row = WebinarRegistrationEntity.create(tenantId, webinar.getId());
        row.setPatientId(UUID.randomUUID());
        row.setAttendeeName("Archived");
        when(registrationRepository.findByTenantIdAndWebinarIdOrderByCreatedAtDesc(eq(tenantId), eq(webinar.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 25), 1));
        when(patientRepository.findByTenantIdAndId(eq(tenantId), eq(row.getPatientId()))).thenReturn(Optional.empty());

        var page = service.list(tenantId, webinar.getId(), 0, 25);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).patientId()).isNull();
    }

    private LeadRecord leadRecord(UUID id, String firstName, String lastName, String phone, String email, UUID campaignId, LeadSource source) {
        return new LeadRecord(
                id,
                tenantId,
                firstName,
                lastName,
                (firstName + " " + lastName).trim(),
                phone,
                email,
                null,
                null,
                source,
                null,
                campaignId,
                null,
                LeadStatus.NEW,
                com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority.MEDIUM,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                actorId,
                actorId,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private LeadEntity leadEntity(LeadRecord record) {
        LeadEntity entity = LeadEntity.create(record.tenantId(), actorId);
        setField(entity, "id", record.id());
        entity.setFirstName(record.firstName());
        entity.setLastName(record.lastName());
        entity.setFullName(record.fullName());
        entity.setPhone(record.phone());
        entity.setEmail(record.email());
        entity.setSource(record.source());
        entity.setCampaignId(record.campaignId());
        entity.setStatus(record.status());
        entity.setPriority(record.priority());
        return entity;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
