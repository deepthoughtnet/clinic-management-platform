package com.deepthoughtnet.clinic.carepilot.execution.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignExecutionEntityTest {
    @Test
    void markProcessingSetsAcquiredAtOnceAndKeepsItStable() {
        CampaignExecutionEntity entity = CampaignExecutionEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                ChannelType.EMAIL,
                null,
                OffsetDateTime.parse("2026-07-19T06:30:00Z"),
                null,
                null,
                null,
                null
        );

        entity.markProcessing();
        OffsetDateTime acquiredAt = entity.getAcquiredAt();

        assertThat(acquiredAt).isNotNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(acquiredAt);
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.PROCESSING);

        setField(entity, "acquiredAt", OffsetDateTime.parse("2026-07-19T06:31:00Z"));
        entity.markProcessing();

        assertThat(entity.getAcquiredAt()).isEqualTo(OffsetDateTime.parse("2026-07-19T06:31:00Z"));
        assertThat(entity.getUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-07-19T06:31:00Z"));
    }

    @Test
    void markFailedPreservesAcquiredAtForDurationReporting() {
        CampaignExecutionEntity entity = CampaignExecutionEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                ChannelType.EMAIL,
                null,
                OffsetDateTime.parse("2026-07-19T06:30:00Z"),
                null,
                null,
                null,
                null
        );
        OffsetDateTime acquiredAt = OffsetDateTime.parse("2026-07-19T06:34:00Z");
        setField(entity, "acquiredAt", acquiredAt);

        entity.markFailed("err", "FAILED", MessageDeliveryStatus.FAILED, null, 3);

        assertThat(entity.getAcquiredAt()).isEqualTo(acquiredAt);
        assertThat(entity.getLastAttemptAt()).isNotNull();
        assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
