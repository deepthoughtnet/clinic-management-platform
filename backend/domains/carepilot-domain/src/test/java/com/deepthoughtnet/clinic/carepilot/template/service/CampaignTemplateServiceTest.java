package com.deepthoughtnet.clinic.carepilot.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplateCreateCommand;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplatePatchCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CampaignTemplateServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private CampaignTemplateRepository repository;
    private CampaignTemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(CampaignTemplateRepository.class);
        service = new CampaignTemplateService(repository);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAndPatchTemplate() {
        var created = service.create(tenantId, new CampaignTemplateCreateCommand("R", ChannelType.EMAIL, "sub", "body", true));
        CampaignTemplateEntity entity = CampaignTemplateEntity.create(tenantId, "R", ChannelType.EMAIL, "sub", "body", true);
        when(repository.findByTenantIdAndId(tenantId, created.id())).thenReturn(Optional.of(entity));

        var patched = service.patch(tenantId, created.id(), new CampaignTemplatePatchCommand("Updated", null, null, false));

        assertThat(created.name()).isEqualTo("R");
        assertThat(patched.name()).isEqualTo("Updated");
        assertThat(patched.active()).isFalse();
    }
}
