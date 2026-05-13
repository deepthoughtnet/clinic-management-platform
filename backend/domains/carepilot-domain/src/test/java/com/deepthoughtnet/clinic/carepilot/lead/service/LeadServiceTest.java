package com.deepthoughtnet.clinic.carepilot.lead.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatusUpdateCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
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
    private LeadService service;

    @BeforeEach
    void setUp() {
        repository = mock(LeadRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        activityService = mock(LeadActivityService.class);
        service = new LeadService(repository, campaignRepository, activityService);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityService.latestByLeadIds(any(), any())).thenReturn(java.util.Map.of());
    }

    @Test
    void createLead() {
        var record = service.create(tenantId, new LeadUpsertCommand(
                "Ava", "Smith", "+15550100", "ava@example.com", PatientGender.FEMALE, null,
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
    void convertedLeadIsImmutableExceptNotesAndTags() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("Old");
        entity.setPhone("+15550000");
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.CONVERTED);
        entity.setPriority(LeadPriority.MEDIUM);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

        var updated = service.update(tenantId, entity.getId(), new LeadUpsertCommand(
                "New", "Name", "+16660000", "new@example.com", PatientGender.OTHER, null,
                LeadSource.GOOGLE_ADS, "x", null, null, LeadStatus.LOST, LeadPriority.LOW,
                "changed-note", "changed-tags", null, null
        ), actorId);

        assertThat(updated.firstName()).isEqualTo("Old");
        assertThat(updated.phone()).isEqualTo("+15550000");
        assertThat(updated.notes()).isEqualTo("changed-note");
        assertThat(updated.tags()).isEqualTo("changed-tags");
    }

    @Test
    void statusUpdateRejectsTransitionOutOfConverted() {
        LeadEntity entity = LeadEntity.create(tenantId, actorId);
        entity.setFirstName("A");
        entity.setPhone("+15550000");
        entity.setSource(LeadSource.MANUAL);
        entity.setStatus(LeadStatus.CONVERTED);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(tenantId, entity.getId(), new LeadStatusUpdateCommand(
                LeadStatus.LOST, null, null, null, null, "oops"
        ), actorId)).isInstanceOf(IllegalArgumentException.class);
    }
}
