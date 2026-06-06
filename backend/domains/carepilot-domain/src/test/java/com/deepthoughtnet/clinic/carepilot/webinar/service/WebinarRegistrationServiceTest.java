package com.deepthoughtnet.clinic.carepilot.webinar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        campaign = CampaignEntity.create(tenantId, "Campaign A", null, null, null, null, null, actorId);
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
        LeadEntity savedLead = leadEntity(createdLead);
        when(leadRepository.findByTenantIdAndId(tenantId, createdLead.id())).thenReturn(Optional.of(savedLead));

        var attended = service.markAttendance(tenantId, webinar.getId(), created.id(), new WebinarAttendanceCommand(WebinarRegistrationStatus.ATTENDED, null));
        assertThat(attended.attended()).isTrue();
        assertThat(attended.registrationStatus()).isEqualTo(WebinarRegistrationStatus.ATTENDED);
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
}
