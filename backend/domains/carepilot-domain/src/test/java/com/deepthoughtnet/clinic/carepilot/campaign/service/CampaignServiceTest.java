package com.deepthoughtnet.clinic.carepilot.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignApprovalHistoryRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignCreateCommand;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CampaignServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private CampaignRepository repository;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        repository = mock(CampaignRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doReturn(1).when(jdbcTemplate).queryForObject(any(String.class), any(Class.class), any(Object[].class));
        service = new CampaignService(repository, mock(CampaignTemplateRepository.class), mock(CampaignApprovalHistoryRepository.class), mock(AppUserRepository.class), jdbcTemplate);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAndListCampaigns() {
        var created = service.create(tenantId, new CampaignCreateCommand(
                "Follow-up", CampaignType.FOLLOW_UP_REMINDER, TriggerType.SCHEDULED, AudienceType.ALL_PATIENTS, null, "n"
        ), actorId, "ENGAGE_MANAGER");

        assertThat(created.status()).isEqualTo(CampaignStatus.DRAFT);
    }

    @Test
    void listReturnsTenantCampaigns() {
        var c = com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity.create(
                tenantId, "CAM-2026-000001", "x", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, null, null, actorId
        );
        when(repository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(c));
        assertThat(service.list(tenantId)).hasSize(1);
    }
}
