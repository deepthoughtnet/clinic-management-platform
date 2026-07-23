package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.engagement.service.PatientEngagementService;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class CarePilotCampaignTriggerServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private CampaignRepository campaignRepository;
    private CampaignTemplateRepository templateRepository;
    private CampaignExecutionRepository executionRepository;
    private CampaignExecutionService executionService;
    private PatientRepository patientRepository;
    private PatientEngagementService engagementService;
    private TenantNotificationSettingsService notificationSettingsService;
    private CarePilotMessagingStatusService messagingStatusService;
    private Environment environment;
    private CarePilotCampaignTriggerService service;
    private boolean manualExecutionDispatcherEnabled;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        templateRepository = mock(CampaignTemplateRepository.class);
        executionRepository = mock(CampaignExecutionRepository.class);
        executionService = mock(CampaignExecutionService.class);
        patientRepository = mock(PatientRepository.class);
        engagementService = mock(PatientEngagementService.class);
        notificationSettingsService = mock(TenantNotificationSettingsService.class);
        messagingStatusService = mock(CarePilotMessagingStatusService.class);
        environment = mock(Environment.class);

        when(environment.getActiveProfiles()).thenReturn(new String[] {"uat"});
        when(notificationSettingsService.findByTenantId(tenantId)).thenReturn(Optional.of(notificationSettings()));
        when(messagingStatusService.providerStatuses()).thenReturn(List.of(
                new ProviderStatusResponse(MessageChannel.EMAIL, "mock-email", true, true, true, ProviderReadinessStatus.READY, List.of(), "Mock provider ready for local UAT.", true, OffsetDateTime.parse("2026-07-18T00:00:00Z"), true, true, false, false),
                new ProviderStatusResponse(MessageChannel.SMS, "mock-sms", false, false, false, ProviderReadinessStatus.DISABLED, List.of(), "SMS channel is disabled.", false, OffsetDateTime.parse("2026-07-18T00:00:00Z"), false, false, false, false),
                new ProviderStatusResponse(MessageChannel.WHATSAPP, "mock-whatsapp", false, false, false, ProviderReadinessStatus.DISABLED, List.of(), "WhatsApp channel is disabled.", false, OffsetDateTime.parse("2026-07-18T00:00:00Z"), false, false, false, false)
        ));
        manualExecutionDispatcherEnabled = true;

        service = newService(manualExecutionDispatcherEnabled);
    }

    private CarePilotCampaignTriggerService newService(boolean dispatcherEnabled) {
        return new CarePilotCampaignTriggerService(
                campaignRepository,
                templateRepository,
                executionRepository,
                executionService,
                patientRepository,
                engagementService,
                notificationSettingsService,
                messagingStatusService,
                environment,
                dispatcherEnabled
        );
    }

    @Test
    void previewBlocksNonManualCampaigns() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000001",
                "Wellness",
                CampaignType.WELLNESS_MESSAGE,
                TriggerType.SCHEDULED,
                AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                null,
                UUID.randomUUID()
        );
        when(campaignRepository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));

        var preview = service.preview(tenantId, campaign.getId());

        assertThat(preview.canTrigger()).isFalse();
        assertThat(preview.blockingReasons()).isNotEmpty();
    }

    @Test
    void triggerQueuesExecutionsForActiveManualCampaign() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000001",
                "Wellness",
                CampaignType.WELLNESS_MESSAGE,
                TriggerType.MANUAL,
                AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                null,
                UUID.randomUUID()
        );
        campaign.activate();
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId,
                "Wellness Template",
                ChannelType.EMAIL,
                "Subject",
                "Body",
                true
        );
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        patient.update("John", "Doe", null, null, null, "+15550100", "john@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);

        when(campaignRepository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, campaign.getTemplateId())).thenReturn(Optional.of(template));
        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));
        when(patientRepository.findByTenantIdAndIdIn(tenantId, List.of(patient.getId()))).thenReturn(List.of(patient));
        when(executionRepository.existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
                eq(tenantId), eq(campaign.getId()), eq(patient.getId()), eq(ChannelType.EMAIL), any(), any()
        )).thenReturn(false);
        campaign.activate();
        campaign.updateDraft(campaign.getName(), campaign.getCampaignType(), campaign.getTriggerType(), campaign.getAudienceType(), campaign.getTemplateId(), campaign.getNotes());
        setApprovedConfigurationHash(campaign, configurationHash(campaign, template));

        var result = service.trigger(tenantId, campaign.getId());

        assertThat(result.queued()).isTrue();
        assertThat(result.eligibleRecipients()).isEqualTo(1);
        assertThat(result.queuedExecutions()).isEqualTo(1);
        assertThat(result.executionReference()).startsWith("EXE-CAM-2026-000001-");
        verify(executionService).create(eq(tenantId), any());
    }

    @Test
    void triggerRejectsZeroEligibleRecipients() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000001",
                "Wellness",
                CampaignType.WELLNESS_MESSAGE,
                TriggerType.MANUAL,
                AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                null,
                UUID.randomUUID()
        );
        campaign.activate();
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId,
                "Wellness Template",
                ChannelType.EMAIL,
                "Subject",
                "Body",
                true
        );
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-2");
        patient.update("Jane", "Doe", null, null, null, "+15550101", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);

        when(campaignRepository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, campaign.getTemplateId())).thenReturn(Optional.of(template));
        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));
        when(patientRepository.findByTenantIdAndIdIn(tenantId, List.of(patient.getId()))).thenReturn(List.of(patient));
        campaign.activate();
        setApprovedConfigurationHash(campaign, configurationHash(campaign, template));

        assertThatThrownBy(() -> service.trigger(tenantId, campaign.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No eligible recipients");
        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void triggerRejectsUnsupportedManualAudience() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000001",
                "Rule Campaign",
                CampaignType.CUSTOM,
                TriggerType.MANUAL,
                AudienceType.RULE_BASED,
                UUID.randomUUID(),
                null,
                UUID.randomUUID()
        );
        campaign.activate();
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId,
                "Rule Template",
                ChannelType.EMAIL,
                "Subject",
                "Body",
                true
        );
        when(campaignRepository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, campaign.getTemplateId())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.trigger(tenantId, campaign.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Manual trigger is not supported");
    }

    @Test
    void previewBlocksWhenManualDispatcherDisabledEvenWithReadyProvider() {
        service = newService(false);

        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000001",
                "Wellness",
                CampaignType.WELLNESS_MESSAGE,
                TriggerType.MANUAL,
                AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                null,
                UUID.randomUUID()
        );
        campaign.activate();
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId,
                "Wellness Template",
                ChannelType.EMAIL,
                "Subject",
                "Body",
                true
        );
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-3");
        patient.update("John", "Doe", null, null, null, "+15550102", "john@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);

        when(campaignRepository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, campaign.getTemplateId())).thenReturn(Optional.of(template));
        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));
        when(patientRepository.findByTenantIdAndIdIn(tenantId, List.of(patient.getId()))).thenReturn(List.of(patient));
        setApprovedConfigurationHash(campaign, configurationHash(campaign, template));

        var preview = service.preview(tenantId, campaign.getId());

        assertThat(preview.manualDispatcherEnabled()).isFalse();
        assertThat(preview.canTrigger()).isFalse();
        assertThat(preview.blockingReasons()).contains("Manual execution dispatcher is disabled.");
    }

    @Test
    void triggerRejectsWhenManualDispatcherDisabled() {
        service = newService(false);

        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000001",
                "Wellness",
                CampaignType.WELLNESS_MESSAGE,
                TriggerType.MANUAL,
                AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                null,
                UUID.randomUUID()
        );
        campaign.activate();
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId,
                "Wellness Template",
                ChannelType.EMAIL,
                "Subject",
                "Body",
                true
        );
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-4");
        patient.update("John", "Doe", null, null, null, "+15550103", "john@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);

        when(campaignRepository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, campaign.getTemplateId())).thenReturn(Optional.of(template));
        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));
        when(patientRepository.findByTenantIdAndIdIn(tenantId, List.of(patient.getId()))).thenReturn(List.of(patient));
        setApprovedConfigurationHash(campaign, configurationHash(campaign, template));

        assertThatThrownBy(() -> service.trigger(tenantId, campaign.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Manual execution dispatcher is disabled.");
        verify(executionService, never()).create(eq(tenantId), any());
    }

    private NotificationSettingsRecord notificationSettings() {
        UUID id = UUID.randomUUID();
        return new NotificationSettingsRecord(
                id,
                tenantId,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                LocalTime.of(0, 0),
                LocalTime.of(0, 0),
                "Asia/Kolkata",
                com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference.EMAIL,
                com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference.SMS,
                false,
                false,
                true,
                5,
                "{}",
                OffsetDateTime.parse("2026-07-18T00:00:00Z"),
                OffsetDateTime.parse("2026-07-18T00:00:00Z"),
                null,
                null
        );
    }

    private void setApprovedConfigurationHash(CampaignEntity campaign, String hash) {
        try {
            var field = CampaignEntity.class.getDeclaredField("approvedConfigurationHash");
            field.setAccessible(true);
            field.set(campaign, hash);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set approved configuration hash for test", ex);
        }
    }

    private String configurationHash(CampaignEntity campaign, CampaignTemplateEntity template) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.join("|",
                    value(campaign.getName()),
                    value(campaign.getCampaignType() == null ? null : campaign.getCampaignType().name()),
                    value(campaign.getTriggerType() == null ? null : campaign.getTriggerType().name()),
                    value(campaign.getAudienceType() == null ? null : campaign.getAudienceType().name()),
                    value(campaign.getTemplateId() == null ? null : campaign.getTemplateId().toString()),
                    value(template == null ? null : template.getName()),
                    value(template == null || template.getChannelType() == null ? null : template.getChannelType().name()),
                    value(template == null ? null : template.getSubjectLine()),
                    value(template == null ? null : template.getBodyTemplate()),
                    value(template == null ? null : Boolean.toString(template.isActive())),
                    value(campaign.getNotes())
            );
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hashed) {
                out.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
