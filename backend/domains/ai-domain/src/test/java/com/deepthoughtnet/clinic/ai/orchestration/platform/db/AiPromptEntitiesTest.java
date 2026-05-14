package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiPromptVersionStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiPromptEntitiesTest {

    @Test
    void clearActiveVersionNullsVersionAndUpdatesAuditFields() {
        UUID actor = UUID.randomUUID();
        AiPromptDefinitionEntity entity = AiPromptDefinitionEntity.create(
                UUID.randomUUID(),
                "test.prompt",
                "Test Prompt",
                "desc",
                "CLINIC",
                "SUMMARY",
                false,
                actor
        );

        entity.activateVersion(3, actor);
        assertEquals(3, entity.getActiveVersion());

        entity.clearActiveVersion(actor);
        assertNull(entity.getActiveVersion());
        assertEquals(actor, entity.getUpdatedBy());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void versionDefaultsToDraftThenCanActivateAndArchive() {
        AiPromptVersionEntity version = AiPromptVersionEntity.create(
                UUID.randomUUID(),
                1,
                "gpt-5",
                null,
                1024,
                "system",
                "user",
                null,
                null
        );

        assertEquals(AiPromptVersionStatus.DRAFT, version.getStatus());
        version.activate();
        assertEquals(AiPromptVersionStatus.ACTIVE, version.getStatus());
        assertNotNull(version.getActivatedAt());
        version.archive();
        assertEquals(AiPromptVersionStatus.ARCHIVED, version.getStatus());
    }
}
