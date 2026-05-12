package com.deepthoughtnet.clinic.carepilot.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignCreateCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CampaignServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private CampaignRepository repository;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        repository = mock(CampaignRepository.class);
        service = new CampaignService(repository);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAndActivateAndDeactivate() {
        var created = service.create(tenantId, new CampaignCreateCommand(
                "Follow-up", CampaignType.FOLLOW_UP_REMINDER, TriggerType.SCHEDULED, AudienceType.ALL_PATIENTS, null, "n"
        ), actorId);
        when(repository.findByTenantIdAndId(tenantId, created.id())).thenReturn(Optional.of(com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity.create(
                tenantId, "Follow-up", CampaignType.FOLLOW_UP_REMINDER, TriggerType.SCHEDULED, AudienceType.ALL_PATIENTS, null, null, actorId
        )));

        var activated = service.activate(tenantId, created.id());
        var deactivated = service.deactivate(tenantId, created.id());

        assertThat(created.status()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(activated.status()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(deactivated.status()).isEqualTo(CampaignStatus.INACTIVE);
    }

    @Test
    void listReturnsTenantCampaigns() {
        var c = com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity.create(
                tenantId, "x", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, null, null, actorId
        );
        when(repository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(c));
        assertThat(service.list(tenantId)).hasSize(1);
    }
}
