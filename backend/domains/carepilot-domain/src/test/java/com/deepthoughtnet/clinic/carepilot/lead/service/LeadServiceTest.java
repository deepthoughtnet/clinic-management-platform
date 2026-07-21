package com.deepthoughtnet.clinic.carepilot.lead.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadConvertedMetadataCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatusUpdateCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeadServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private LeadRepository repository;
    private CampaignRepository campaignRepository;
    private LeadActivityService activityService;
    private PatientRepository patientRepository;
    private AppUserRepository appUserRepository;
    private LeadService service;

    @BeforeEach
    void setUp() {
        repository = mock(LeadRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        activityService = mock(LeadActivityService.class);
        patientRepository = mock(PatientRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        service = new LeadService(repository, campaignRepository, activityService, patientRepository, appUserRepository);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityService.latestByLeadIds(any(), any())).thenReturn(java.util.Map.of());
        when(patientRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void createLead() {
        var record = service.create(tenantId, new LeadUpsertCommand(
                "Ava", "Smith", "9876543210", "ava@example.com", PatientGender.FEMALE, null,
                LeadSource.WEBSITE, "landing page", null, null, LeadStatus.NEW, LeadPriority.HIGH,
                "note", "vip", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1)
        ), actorId);

        assertThat(record.firstName()).isEqualTo("Ava");
        assertThat(record.source()).isEqualTo(LeadSource.WEBSITE);
        assertThat(record.status()).isEqualTo(LeadStatus.NEW);
        assertThat(record.priority()).isEqualTo(LeadPriority.HIGH);
        verify(activityService, times(2))
                .record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createLeadAllowsOptionalFollowUp() {
        var record = service.create(tenantId, new LeadUpsertCommand(
                "Asha", "Mehta", "9876543210", "asha@example.com", PatientGender.FEMALE, null,
                LeadSource.WALK_IN, "desk", null, null, LeadStatus.NEW, LeadPriority.MEDIUM,
                "note", "uat", null, null
        ), actorId);

        assertThat(record.nextFollowUpAt()).isNull();
    }

    @Test
    void createLeadPreservesFollowUpInstant() {
        OffsetDateTime followUpAt = OffsetDateTime.parse("2026-08-02T11:00:00+05:30");
        var record = service.create(tenantId, new LeadUpsertCommand(
                "Asha", "Mehta", "9876543210", "asha@example.com", PatientGender.FEMALE, null,
                LeadSource.WALK_IN, "desk", null, null, LeadStatus.NEW, LeadPriority.MEDIUM,
                "note", "uat", null, followUpAt
        ), actorId);

        assertThat(record.nextFollowUpAt()).isEqualTo(followUpAt);
    }

    @Test
    void convertedLeadProjectionHidesStaleNextFollowUp() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("Rahul");
        entity.setPhone("9876543210");
        entity.setSource(LeadSource.WEBSITE);
        entity.setStatus(LeadStatus.CONVERTED);
        entity.setConverted(UUID.randomUUID(), actorId);
        entity.setNextFollowUpAt(OffsetDateTime.parse("2026-07-21T05:30:00Z"));
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

        var record = service.find(tenantId, entity.getId()).orElseThrow();

        assertThat(record.nextFollowUpAt()).isNull();
        assertThat(record.convertedPatientId()).isNull();
    }

    @Test
    void convertedLeadAllowsMarketingMetadataUpdatesOnly() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("Old");
        entity.setLastName("Name");
        entity.setPhone("9876543210");
        entity.setEmail("old@example.com");
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.CONVERTED);
        entity.setPriority(LeadPriority.MEDIUM);
        entity.setSourceDetails("old source");
        UUID campaignId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        entity.setCampaignId(campaignId);
        entity.setAssignedToAppUserId(assigneeId);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));
        when(campaignRepository.findByTenantIdAndId(tenantId, campaignId)).thenReturn(Optional.of(mock(com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity.class)));

        var updated = service.update(tenantId, entity.getId(), new LeadUpsertCommand(
                "Old", "Name", "9876543210", "old@example.com", null, null,
                LeadSource.MANUAL, "new source", campaignId, assigneeId, LeadStatus.CONVERTED, LeadPriority.MEDIUM,
                "changed-note", "changed-tags", null, null
        ), actorId);

        assertThat(updated.firstName()).isEqualTo("Old");
        assertThat(updated.phone()).isEqualTo("9876543210");
        assertThat(updated.campaignId()).isEqualTo(campaignId);
        assertThat(updated.assignedToAppUserId()).isEqualTo(assigneeId);
        assertThat(updated.sourceDetails()).isEqualTo("new source");
        assertThat(updated.notes()).isEqualTo("changed-note");
        assertThat(updated.tags()).isEqualTo("changed-tags");
    }

    @Test
    void convertedMetadataUpdateAllowsPartialMarketingPayloadWithoutImmutableFields() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("Old");
        entity.setLastName("Name");
        entity.setPhone("9876543210");
        entity.setEmail("old@example.com");
        entity.setGender(PatientGender.FEMALE);
        entity.setDateOfBirth(java.time.LocalDate.of(1992, 1, 1));
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.CONVERTED);
        entity.setPriority(LeadPriority.MEDIUM);
        entity.setLastContactedAt(OffsetDateTime.parse("2026-07-20T09:00:00Z"));
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));
        UUID campaignId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        when(campaignRepository.findByTenantIdAndId(tenantId, campaignId)).thenReturn(Optional.of(mock(com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity.class)));
        AppUserEntity assignee = mock(AppUserEntity.class);
        when(assignee.getStatus()).thenReturn("ACTIVE");
        when(appUserRepository.findByTenantIdAndId(tenantId, assigneeId)).thenReturn(Optional.of(assignee));

        var updated = service.updateConvertedMetadata(tenantId, entity.getId(), new LeadConvertedMetadataCommand(
                "marketing note",
                "Test, UAT",
                "webinar follow-up",
                campaignId,
                assigneeId
        ), actorId);

        assertThat(updated.notes()).isEqualTo("marketing note");
        assertThat(updated.tags()).isEqualTo("Test, UAT");
        assertThat(updated.sourceDetails()).isEqualTo("webinar follow-up");
        assertThat(updated.campaignId()).isEqualTo(campaignId);
        assertThat(updated.assignedToAppUserId()).isEqualTo(assigneeId);
        assertThat(updated.status()).isEqualTo(LeadStatus.CONVERTED);
    }

    @Test
    void convertedMetadataUpdateRejectsCrossTenantAssignee() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("Old");
        entity.setPhone("9876543210");
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.CONVERTED);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

        UUID assigneeId = UUID.randomUUID();
        when(appUserRepository.findByTenantIdAndId(tenantId, assigneeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConvertedMetadata(tenantId, entity.getId(), new LeadConvertedMetadataCommand(
                null, null, null, null, assigneeId
        ), actorId)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("assignedToAppUserId");
    }

    @Test
    void convertedLeadRejectsImmutableFieldChanges() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("Old");
        entity.setLastName("Name");
        entity.setPhone("9876543210");
        entity.setEmail("old@example.com");
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.CONVERTED);
        entity.setPriority(LeadPriority.MEDIUM);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.update(tenantId, entity.getId(), new LeadUpsertCommand(
                "Changed", "Name", "1234567890", "new@example.com", PatientGender.OTHER, null,
                LeadSource.GOOGLE_ADS, "x", null, null, LeadStatus.LOST, LeadPriority.LOW,
                "note", "tags", null, null
        ), actorId)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Converted leads cannot change immutable fields");
    }

    @Test
    void statusUpdateRejectsTransitionOutOfConverted() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("A");
        entity.setPhone("9876543210");
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.CONVERTED);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(tenantId, entity.getId(), new LeadStatusUpdateCommand(
                LeadStatus.LOST, null, null, null, null, "oops"
        ), actorId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusUpdateCompletesPendingFollowUpOnce() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("A");
        entity.setPhone("9876543210");
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.FOLLOW_UP_REQUIRED);
        entity.setNextFollowUpAt(OffsetDateTime.parse("2026-07-20T09:00:00+05:30"));
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

        var updated = service.updateStatus(tenantId, entity.getId(), new LeadStatusUpdateCommand(
                LeadStatus.CONTACTED, null, null, null, null, "Follow-up completed after call"
        ), actorId);

        assertThat(updated.status()).isEqualTo(LeadStatus.CONTACTED);
        assertThat(updated.nextFollowUpAt()).isNull();
        assertThat(updated.notes()).contains("Follow-up completed after call");
        verify(activityService, times(1)).record(eq(tenantId), eq(entity.getId()), eq(LeadActivityType.FOLLOW_UP_COMPLETED), any(), any(), any(), any(), any(), any(), eq(actorId));
    }

    @Test
    void statusUpdateWithoutPendingFollowUpDoesNotCreateCompletionEvent() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("A");
        entity.setPhone("9876543210");
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.NEW);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

        service.updateStatus(tenantId, entity.getId(), new LeadStatusUpdateCommand(
                LeadStatus.CONTACTED, null, null, null, null, "Checked in"
        ), actorId);

        verify(activityService, never()).record(eq(tenantId), eq(entity.getId()), eq(LeadActivityType.FOLLOW_UP_COMPLETED), any(), any(), any(), any(), any(), any(), any());
    }
}
