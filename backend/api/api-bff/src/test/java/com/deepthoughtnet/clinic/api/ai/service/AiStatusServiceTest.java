package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.core.env.Environment;

class AiStatusServiceTest {
    @Test
    void returnsProviderNotConfiguredWhenTenantEnabledButGeminiMissingKey() {
        TenantModuleEntitlementService moduleService = mock(TenantModuleEntitlementService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        UUID tenantId = UUID.randomUUID();

        when(moduleService.isModuleEnabled(tenantId, ModuleKeys.AI_COPILOT)).thenReturn(true);
        when(permissionChecker.hasAnyPermission("ai_copilot.run", "ai_copilot.clinic.run")).thenReturn(true);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});

        AiStatusService service = new AiStatusService(
                moduleService,
                permissionChecker,
                List.of(new StubProvider("GEMINI", AiProviderStatus.UNAVAILABLE)),
                true,
                "GEMINI",
                "GEMINI,GROQ,MOCK",
                true,
                "",
                true,
                "TESSERACT",
                environment
        );

        var status = service.status(tenantId);
        assertThat(status.tenantModuleEnabled()).isTrue();
        assertThat(status.runtimeEnabled()).isTrue();
        assertThat(status.provider()).isEqualTo("GEMINI");
        assertThat(status.providerConfigured()).isFalse();
        assertThat(status.effectiveStatus()).isEqualTo("PROVIDER_NOT_CONFIGURED");
        assertThat(status.message()).isEqualTo("AI module is enabled for this clinic, but AI provider is not configured.");
    }

    @Test
    void returnsReadyForMockProvider() {
        TenantModuleEntitlementService moduleService = mock(TenantModuleEntitlementService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        UUID tenantId = UUID.randomUUID();

        when(moduleService.isModuleEnabled(tenantId, ModuleKeys.AI_COPILOT)).thenReturn(true);
        when(permissionChecker.hasAnyPermission("ai_copilot.run", "ai_copilot.clinic.run")).thenReturn(true);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});

        AiStatusService service = new AiStatusService(
                moduleService,
                permissionChecker,
                List.of(new StubProvider("MOCK", AiProviderStatus.AVAILABLE)),
                true,
                "MOCK",
                "GEMINI,GROQ,MOCK",
                false,
                "",
                true,
                "TESSERACT",
                environment
        );

        var status = service.status(tenantId);
        assertThat(status.effectiveStatus()).isEqualTo("READY");
        assertThat(status.providerConfigured()).isTrue();
        assertThat(status.provider()).isEqualTo("MOCK");
    }

    @Test
    void returnsReadyForGroqWhenGeminiUnavailable() {
        TenantModuleEntitlementService moduleService = mock(TenantModuleEntitlementService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        UUID tenantId = UUID.randomUUID();

        when(moduleService.isModuleEnabled(tenantId, ModuleKeys.AI_COPILOT)).thenReturn(true);
        when(permissionChecker.hasAnyPermission("ai_copilot.run", "ai_copilot.clinic.run")).thenReturn(true);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});

        AiStatusService service = new AiStatusService(
                moduleService,
                permissionChecker,
                List.of(
                        new StubProvider("GEMINI", AiProviderStatus.UNAVAILABLE),
                        new StubProvider("GROQ", AiProviderStatus.AVAILABLE)
                ),
                true,
                "GEMINI",
                "GEMINI,GROQ,MOCK",
                true,
                "",
                true,
                "TESSERACT",
                environment
        );

        var status = service.status(tenantId);
        assertThat(status.effectiveStatus()).isEqualTo("READY");
        assertThat(status.providerConfigured()).isTrue();
        assertThat(status.provider()).isEqualTo("GROQ");
    }

    @Test
    void prefersRealProvidersOverMockEvenWhenChainStartsWithMock() {
        TenantModuleEntitlementService moduleService = mock(TenantModuleEntitlementService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        UUID tenantId = UUID.randomUUID();

        when(moduleService.isModuleEnabled(tenantId, ModuleKeys.AI_COPILOT)).thenReturn(true);
        when(permissionChecker.hasAnyPermission("ai_copilot.run", "ai_copilot.clinic.run")).thenReturn(true);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});

        AiStatusService service = new AiStatusService(
                moduleService,
                permissionChecker,
                List.of(
                        new StubProvider("MOCK", AiProviderStatus.AVAILABLE),
                        new StubProvider("GEMINI", AiProviderStatus.AVAILABLE)
                ),
                true,
                "MOCK",
                "MOCK,GEMINI,GROQ",
                true,
                "test-key",
                true,
                "TESSERACT",
                environment
        );

        var status = service.status(tenantId);
        assertThat(status.effectiveStatus()).isEqualTo("READY");
        assertThat(status.provider()).isEqualTo("GEMINI");
    }

    @Test
    void requireProviderReadyThrowsFriendlyMessageWhenProviderMissing() {
        TenantModuleEntitlementService moduleService = mock(TenantModuleEntitlementService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        UUID tenantId = UUID.randomUUID();

        when(moduleService.isModuleEnabled(tenantId, ModuleKeys.AI_COPILOT)).thenReturn(true);
        when(permissionChecker.hasAnyPermission("ai_copilot.run", "ai_copilot.clinic.run")).thenReturn(true);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});

        AiStatusService service = new AiStatusService(
                moduleService,
                permissionChecker,
                List.of(),
                true,
                "GEMINI",
                "GEMINI,GROQ,MOCK",
                true,
                "",
                true,
                "TESSERACT",
                environment
        );

        assertThatThrownBy(() -> service.requireProviderReady(tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI module is enabled for this clinic, but AI provider is not configured.");
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
}
