package com.deepthoughtnet.clinic.carepilot.campaign.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignApprovalHistoryRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignCreateCommand;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.carepilot.shared.exception.CampaignConflictException;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignRecord;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CampaignServiceAuthorizationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    private CampaignRepository repository;
    private CampaignTemplateRepository templateRepository;
    private CampaignApprovalHistoryRepository historyRepository;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        repository = mock(CampaignRepository.class);
        templateRepository = mock(CampaignTemplateRepository.class);
        historyRepository = mock(CampaignApprovalHistoryRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        service = new CampaignService(repository, templateRepository, historyRepository, mock(AppUserRepository.class), jdbcTemplate);
        when(repository.save(any())).thenAnswer(invocation -> {
            CampaignEntity entity = invocation.getArgument(0);
            bumpVersion(entity);
            return entity;
        });
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            CampaignEntity entity = invocation.getArgument(0);
            bumpVersion(entity);
            return entity;
        });
    }

    @Test
    void createIsManagerOnly() {
        assertThatThrownBy(() -> service.create(tenantId, new CampaignCreateCommand(
                "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, null, null
        ), actorId, "CLINIC_ADMIN"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void managerCanEditDraftCampaigns() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "CAM-2026-000001", "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, "Original description", actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        CampaignRecord updated = service.update(tenantId, campaign.getId(), new CampaignCreateCommand(
                "Recall updated", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, "Updated description"
        ), actorId, "ENGAGE_MANAGER");

        assertThat(updated.name()).isEqualTo("Recall updated");
        assertThat(updated.notes()).isEqualTo("Updated description");
        assertThat(updated.version()).isGreaterThan(0);
    }

    @Test
    void submitApproveAndActivateAreRoleGuarded() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "CAM-2026-000001", "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, null, actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_EXECUTIVE", null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.approve(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", null, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.requestChanges(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", "review", null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.activate(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void pendingApprovalContentEditsAreRejected() {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "CAM-2026-000001", "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, null, actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", null);

        assertThatThrownBy(() -> service.update(tenantId, campaign.getId(), new CampaignCreateCommand(
                "Changed", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, "Updated description"
        ), actorId, "ENGAGE_MANAGER"))
                .isInstanceOf(CampaignConflictException.class)
                .hasMessageContaining("Withdraw the pending submission before editing this campaign");
    }

    @Test
    void managerCanSubmitAndClinicAdminCanApproveActivatePauseAndResume() throws Exception {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "CAM-2026-000001", "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, null, actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", null);
        service.approve(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN", "looks good", campaign.getVersion());
        service.activate(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN");
        service.pause(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN");
        service.resume(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN");

        assertThatThrownBy(() -> service.update(tenantId, campaign.getId(), new CampaignCreateCommand(
                "Changed", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, null
        ), actorId, "ENGAGE_MANAGER"))
                .isInstanceOf(CampaignConflictException.class);
    }

    @Test
    void unchangedChangesRequestedResubmissionIsRejectedWhileChangedResubmissionSucceeds() throws Exception {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "CAM-2026-000001", "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, null, actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", null, null);
        service.requestChanges(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN", "Please add description", campaign.getVersion());

        assertThatThrownBy(() -> service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", campaign.getVersion(), null))
                .isInstanceOf(CampaignConflictException.class)
                .hasMessageContaining("Save a campaign change or provide a resolution note before resubmitting");

        String originalHash = campaign.getApprovedConfigurationHash();
        service.update(tenantId, campaign.getId(), new CampaignCreateCommand(
                "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, "Updated description"
        ), actorId, "ENGAGE_MANAGER");
        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", campaign.getVersion(), null);

        assertThat(campaign.getApprovedConfigurationHash()).isNotEqualTo(originalHash);
        assertThat(campaign.getStatus()).isEqualTo(com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus.PENDING_APPROVAL);
    }

    @Test
    void resolutionNoteAllowsUnchangedResubmission() throws Exception {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, null, actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", null, null);
        service.requestChanges(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN", "Please add description", campaign.getVersion());

        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", campaign.getVersion(), "Kept as-is per reviewer note");

        assertThat(campaign.getStatus()).isEqualTo(com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus.PENDING_APPROVAL);
    }

    @Test
    void editAndResubmitUsesTheReturnedVersionAndDoesNotConflict() throws Exception {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "CAM-2026-000001", "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, "Original description", actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", null);
        service.requestChanges(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN", "Please add description", campaign.getVersion());

        var updated = service.editAndResubmit(
                tenantId,
                campaign.getId(),
                new CampaignCreateCommand(
                        "Recall",
                        CampaignType.CUSTOM,
                        TriggerType.MANUAL,
                        AudienceType.ALL_PATIENTS,
                        templateId,
                        "Updated description"
                ),
                actorId,
                "ENGAGE_MANAGER",
                campaign.getVersion(),
                null
        );

        assertThat(updated.status()).isEqualTo(com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus.PENDING_APPROVAL);
        assertThat(updated.version()).isEqualTo(campaign.getVersion());
        assertThat(campaign.getApprovedVersion()).isEqualTo(campaign.getVersion() - 1);
        assertThat(campaign.getApprovedConfigurationHash()).isNotNull();
        assertThat(campaign.getVersion()).isEqualTo(3);
    }

    @Test
    void failedAtomicResubmitDoesNotPersistPartialChanges() throws Exception {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "CAM-2026-000001", "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, "Original description", actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", null);
        service.requestChanges(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN", "Please add description", campaign.getVersion());
        clearInvocations(repository, historyRepository);

        assertThatThrownBy(() -> service.editAndResubmit(
                tenantId,
                campaign.getId(),
                new CampaignCreateCommand(
                        "",
                        CampaignType.CUSTOM,
                        TriggerType.MANUAL,
                        AudienceType.ALL_PATIENTS,
                        templateId,
                        "Updated description"
                ),
                actorId,
                "ENGAGE_MANAGER",
                campaign.getVersion(),
                null
        )).isInstanceOf(com.deepthoughtnet.clinic.platform.core.errors.BadRequestException.class);

        verify(repository, never()).save(campaign);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void resumeFailsWhenApprovedSnapshotNoLongerMatches() throws Exception {
        CampaignEntity campaign = CampaignEntity.create(
                tenantId, "CAM-2026-000001", "Recall", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, templateId, null, actorId
        );
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId, "Recall Template", ChannelType.EMAIL, "Subject", "Body", true
        );
        when(repository.findByTenantIdAndId(tenantId, campaign.getId())).thenReturn(Optional.of(campaign));
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        service.submitForApproval(tenantId, campaign.getId(), actorId, "ENGAGE_MANAGER", null);
        service.approve(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN", "looks good", campaign.getVersion());
        service.activate(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN");
        service.pause(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN");

        Field field = CampaignEntity.class.getDeclaredField("approvedConfigurationHash");
        field.setAccessible(true);
        field.set(campaign, "tampered-hash");

        assertThatThrownBy(() -> service.resume(tenantId, campaign.getId(), UUID.randomUUID(), "CLINIC_ADMIN"))
                .isInstanceOf(CampaignConflictException.class);
    }

    private void bumpVersion(CampaignEntity entity) {
        try {
            Field version = CampaignEntity.class.getDeclaredField("version");
            version.setAccessible(true);
            version.setInt(entity, entity.getVersion() + 1);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
