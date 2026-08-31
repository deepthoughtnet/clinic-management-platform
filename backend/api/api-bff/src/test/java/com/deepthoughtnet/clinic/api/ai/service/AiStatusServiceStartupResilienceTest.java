package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.identity.service.TenantModuleEntitlementService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

class AiStatusServiceStartupResilienceTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void applicationContextStartsEvenWhenProviderStatusLookupFails() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(AiStatusService.class);

            AiStatusService service = context.getBean(AiStatusService.class);
            UUID tenantId = UUID.randomUUID();

            TenantModuleEntitlementService moduleService = context.getBean(TenantModuleEntitlementService.class);
            PermissionChecker permissionChecker = context.getBean(PermissionChecker.class);
            when(moduleService.isModuleEnabled(tenantId, ModuleKeys.AI_COPILOT)).thenReturn(true);
            when(permissionChecker.hasAnyPermission("ai_copilot.run", "ai_copilot.clinic.run")).thenReturn(true);

            assertThat(service.status(tenantId).effectiveStatus()).isEqualTo("READY");
            assertThat(service.status(tenantId).provider()).isEqualTo("GEMINI");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        TenantModuleEntitlementService tenantModuleEntitlementService() {
            return mock(TenantModuleEntitlementService.class);
        }

        @Bean
        PermissionChecker permissionChecker() {
            return mock(PermissionChecker.class);
        }

        @Bean
        AiProvider groqProvider() {
            return new ThrowingProvider("GROQ");
        }

        @Bean
        AiProvider geminiProvider() {
            return new StubProvider("GEMINI", AiProviderStatus.AVAILABLE);
        }

        @Bean
        AiStatusService aiStatusService(
                TenantModuleEntitlementService tenantModuleEntitlementService,
                PermissionChecker permissionChecker,
                List<AiProvider> providers,
                Environment environment
        ) {
            return new AiStatusService(
                    tenantModuleEntitlementService,
                    permissionChecker,
                    providers,
                    true,
                    "GEMINI",
                    "GEMINI,GROQ,MOCK",
                    true,
                    "test-gemini-key",
                    true,
                    "TESSERACT",
                    environment
            );
        }
    }

    private static final class StubProvider implements AiProvider {
        private final String providerName;
        private final AiProviderStatus status;

        private StubProvider(String providerName, AiProviderStatus status) {
            this.providerName = providerName;
            this.status = status;
        }

        @Override
        public String providerName() {
            return providerName;
        }

        @Override
        public boolean supports(AiTaskType taskType) {
            return true;
        }

        @Override
        public AiProviderResponse complete(AiProviderRequest request) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public AiProviderStatus status() {
            return status;
        }
    }

    private static final class ThrowingProvider implements AiProvider {
        private final String providerName;

        private ThrowingProvider(String providerName) {
            this.providerName = providerName;
        }

        @Override
        public String providerName() {
            return providerName;
        }

        @Override
        public boolean supports(AiTaskType taskType) {
            return true;
        }

        @Override
        public AiProviderResponse complete(AiProviderRequest request) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public AiProviderStatus status() {
            throw new IllegalStateException("No default constructor found");
        }
    }
}
