package com.deepthoughtnet.clinic.api.carepilot;

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
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.engagement.service.PatientEngagementService;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CarePilotCampaignTriggerServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private CampaignRepository campaignRepository;
    private CampaignTemplateRepository templateRepository;
    private CampaignExecutionRepository executionRepository;
    private CampaignExecutionService executionService;
    private PatientRepository patientRepository;
    private PatientEngagementService engagementService;
    private CarePilotCampaignTriggerService service;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        templateRepository = mock(CampaignTemplateRepository.class);
        executionRepository = mock(CampaignExecutionRepository.class);
        executionService = mock(CampaignExecutionService.class);
        patientRepository = mock(PatientRepository.class);
        engagementService = mock(PatientEngagementService.class);
        service = new CarePilotCampaignTriggerService(
                campaignRepository,
                templateRepository,
                executionRepository,
                executionService,
                patientRepository,
                engagementService
        );
    }

    @Test
    void triggerQueuesExecutionsForActiveAllPatientsCampaign() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
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
        when(executionRepository.existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
                eq(tenantId), eq(campaign.getId()), eq(patient.getId()), eq(ChannelType.EMAIL), any(), any()
        )).thenReturn(false);

        var result = service.trigger(tenantId, campaign.getId());

        assertThat(result.queued()).isTrue();
        assertThat(result.eligibleRecipients()).isEqualTo(1);
        assertThat(result.queuedExecutions()).isEqualTo(1);
        assertThat(result.skippedRecipients()).isZero();
        verify(executionService).create(eq(tenantId), any());
    }

    @Test
    void triggerReturnsClearMessageWhenNoEligibleRecipientsExist() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
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

        var result = service.trigger(tenantId, campaign.getId());

        assertThat(result.queued()).isFalse();
        assertThat(result.queuedExecutions()).isZero();
        assertThat(result.message()).contains("No eligible recipients");
        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void triggerRejectsInactiveCampaign() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "Wellness",
                CampaignType.WELLNESS_MESSAGE,
                TriggerType.MANUAL,
                AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                null,
                UUID.randomUUID()
        );
        when(campaignRepository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.trigger(tenantId, campaign.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only active campaigns");
    }

    @Test
    void triggerRejectsUnsupportedManualAudience() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
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
}
