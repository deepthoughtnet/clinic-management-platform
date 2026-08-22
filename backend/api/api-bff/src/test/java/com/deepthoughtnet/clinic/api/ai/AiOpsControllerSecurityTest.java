package com.deepthoughtnet.clinic.api.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AiOpsControllerSecurityTest {

    @Test
    void allAdminEndpointsRequirePlatformAdminRole() throws Exception {
        List<Method> methods = List.of(
                AiOpsController.class.getMethod("prompts"),
                AiOpsController.class.getMethod("prompt", java.util.UUID.class),
                AiOpsController.class.getMethod("createPrompt", com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.CreatePromptRequest.class),
                AiOpsController.class.getMethod("createVersion", java.util.UUID.class, com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.CreatePromptVersionRequest.class),
                AiOpsController.class.getMethod("activateVersion", java.util.UUID.class, java.util.UUID.class),
                AiOpsController.class.getMethod("archiveVersion", java.util.UUID.class, java.util.UUID.class),
                AiOpsController.class.getMethod("invocations"),
                AiOpsController.class.getMethod("usage", String.class, String.class, String.class, String.class),
                AiOpsController.class.getMethod("tools"),
                AiOpsController.class.getMethod("guardrails"),
                AiOpsController.class.getMethod("workflowRuns"),
                AiOpsController.class.getMethod("workflowSteps", java.util.UUID.class)
        );

        for (Method method : methods) {
            assertThat(method.getAnnotation(PreAuthorize.class).value())
                    .contains("PLATFORM_ADMIN")
                    .doesNotContain("CLINIC_ADMIN")
                    .doesNotContain("TENANT_ADMIN")
                    .doesNotContain("DOCTOR");
        }
    }
}
