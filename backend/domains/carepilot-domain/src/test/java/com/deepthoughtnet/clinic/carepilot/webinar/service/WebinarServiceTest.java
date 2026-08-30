package com.deepthoughtnet.clinic.carepilot.webinar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarType;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarUpsertCommand;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebinarServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private WebinarRepository repository;
    private CampaignRepository campaignRepository;
    private TenantNotificationSettingsService notificationSettingsService;
    private WebinarService service;

    @BeforeEach
    void setUp() {
        repository = mock(WebinarRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        notificationSettingsService = mock(TenantNotificationSettingsService.class);
        service = new WebinarService(repository, campaignRepository, notificationSettingsService);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createWebinar() {
        when(notificationSettingsService.findByTenantId(tenantId)).thenReturn(java.util.Optional.empty());
        var row = service.create(tenantId, new WebinarUpsertCommand(
                "Diabetes Awareness", "Session", WebinarType.HEALTH_AWARENESS, WebinarStatus.SCHEDULED,
                null,
                "https://example.com/w/1", "Admin", "admin@example.com",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1), null, 100,
                true, true, true, "care"
        ), actorId);

        assertThat(row.title()).isEqualTo("Diabetes Awareness");
        assertThat(row.status()).isEqualTo(WebinarStatus.SCHEDULED);
        assertThat(row.timezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void draftPublishTransitionsToScheduled() {
        WebinarEntity entity = WebinarEntity.create(tenantId, actorId);
        entity.setTitle("Future Draft");
        entity.setStatus(WebinarStatus.DRAFT);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(java.util.Optional.of(entity));

        var updated = service.updateStatus(tenantId, entity.getId(), WebinarStatus.SCHEDULED, actorId);

        assertThat(updated.status()).isEqualTo(WebinarStatus.SCHEDULED);
    }

    @Test
    void draftCannotJumpDirectlyToLive() {
        WebinarEntity entity = WebinarEntity.create(tenantId, actorId);
        entity.setTitle("Future Draft");
        entity.setStatus(WebinarStatus.DRAFT);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(tenantId, entity.getId(), WebinarStatus.LIVE, actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid webinar status transition");
    }

    @Test
    void completedWebinarCannotBeEdited() {
        WebinarEntity entity = WebinarEntity.create(tenantId, actorId);
        entity.setTitle("Completed");
        entity.setStatus(WebinarStatus.COMPLETED);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.update(tenantId, entity.getId(), new WebinarUpsertCommand(
                "Updated", null, WebinarType.EDUCATIONAL, WebinarStatus.COMPLETED,
                null, null, null, null,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1),
                null, null, null, null, null, null
        ), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Completed or cancelled webinars cannot be edited");
    }

    @Test
    void liveWebinarCannotBeEdited() {
        WebinarEntity entity = WebinarEntity.create(tenantId, actorId);
        entity.setTitle("Live");
        entity.setStatus(WebinarStatus.LIVE);
        when(repository.findByTenantIdAndId(tenantId, entity.getId())).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.update(tenantId, entity.getId(), new WebinarUpsertCommand(
                "Updated", null, WebinarType.EDUCATIONAL, WebinarStatus.LIVE,
                null, null, null, null,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1),
                null, null, null, null, null, null
        ), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Live webinars cannot be edited");
    }

    @Test
    void activeCampaignIsAcceptedButCompletedCampaignIsRejectedForNewWebinar() {
        CampaignEntity activeCampaign = CampaignEntity.create(tenantId, "CAM-2026-000001", "Active", null, null, null, null, null, actorId);
        activeCampaign.activate();
        when(campaignRepository.findByTenantIdAndId(tenantId, activeCampaign.getId())).thenReturn(java.util.Optional.of(activeCampaign));

        var created = service.create(tenantId, new WebinarUpsertCommand(
                "Campaign Linked", null, WebinarType.EDUCATIONAL, WebinarStatus.SCHEDULED,
                activeCampaign.getId(), null, null, null,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1),
                "Asia/Kolkata", null, null, null, null, null
        ), actorId);
        assertThat(created.campaignId()).isEqualTo(activeCampaign.getId());

        CampaignEntity completedCampaign = CampaignEntity.create(tenantId, "CAM-2026-000002", "Completed", null, null, null, null, null, actorId);
        completedCampaign.complete();
        when(campaignRepository.findByTenantIdAndId(tenantId, completedCampaign.getId())).thenReturn(java.util.Optional.of(completedCampaign));

        assertThatThrownBy(() -> service.create(tenantId, new WebinarUpsertCommand(
                "Rejected Campaign", null, WebinarType.EDUCATIONAL, WebinarStatus.SCHEDULED,
                completedCampaign.getId(), null, null, null,
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1),
                "Asia/Kolkata", null, null, null, null, null
        ), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selected campaign must be active");
    }
}
