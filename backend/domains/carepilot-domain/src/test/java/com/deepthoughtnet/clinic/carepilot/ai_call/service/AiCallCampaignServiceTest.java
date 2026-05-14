package com.deepthoughtnet.clinic.carepilot.ai_call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallCampaignEntity;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallCampaignRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallType;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallCampaignUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiCallCampaignServiceTest {
    @Mock
    private AiCallCampaignRepository repository;

    @InjectMocks
    private AiCallCampaignService service;

    @Test
    void createCampaignPersistsRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AiCallCampaignUpsertCommand command = new AiCallCampaignUpsertCommand(
                "Refill Reminder", "desc", AiCallType.REFILL_REMINDER, null, null, ChannelType.SMS, true, 3, true
        );
        when(repository.save(any(AiCallCampaignEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(tenantId, command, actorId);
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Refill Reminder");
        assertThat(created.callType()).isEqualTo(AiCallType.REFILL_REMINDER);
    }

    @Test
    void createRejectsInvalidAttempts() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AiCallCampaignUpsertCommand bad = new AiCallCampaignUpsertCommand(
                "X", null, AiCallType.MANUAL_OUTREACH, null, null, ChannelType.SMS, true, 0, false
        );

        assertThatThrownBy(() -> service.create(tenantId, bad, actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }
}
