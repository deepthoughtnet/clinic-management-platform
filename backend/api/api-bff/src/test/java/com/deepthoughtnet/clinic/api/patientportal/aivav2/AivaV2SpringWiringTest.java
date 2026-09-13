package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AivaV2SpringWiringTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AiOrchestrationService.class, () -> mock(AiOrchestrationService.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(PatientPortalService.class, () -> mock(PatientPortalService.class))
            .withBean(PublicCatalogFacade.class, () -> mock(PublicCatalogFacade.class))
            .withBean(ClinicTimeZoneResolver.class, () -> mock(ClinicTimeZoneResolver.class))
            .withUserConfiguration(AiAivaV2ConversationDecisionGateway.class, AivaV2SessionStore.class,
                    AivaV2BookingTools.class, AivaV2TransactionalKernel.class,
                    AivaV2AppointmentLookupTool.class, AivaV2CancellationTool.class,
                    AivaV2RescheduleTools.class,
                    AivaV2ConversationService.class, AivaV2Controller.class);

    @Test
    void endpointBeanIsAbsentByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AivaV2Controller.class);
            assertThat(context).hasSingleBean(AivaV2ConversationService.class);
        });
    }

    @Test
    void endpointBeanWiresWhenExplicitlyEnabled() {
        runner.withPropertyValues("aiva.v2.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AivaV2Controller.class);
            assertThat(context).hasSingleBean(AivaV2ConversationService.class);
        });
    }
}
