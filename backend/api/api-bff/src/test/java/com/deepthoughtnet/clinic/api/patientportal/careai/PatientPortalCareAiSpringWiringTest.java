package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class PatientPortalCareAiSpringWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CareAiInterpreterConfiguration.class);

    @Test
    void registersExtractorAndTurnInterpreter() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PatientPortalCareAiEntityExtractor.class);
            assertThat(context).hasSingleBean(PatientPortalCareAiTurnInterpreter.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({PatientPortalCareAiEntityExtractor.class, PatientPortalCareAiTurnInterpreter.class})
    static class CareAiInterpreterConfiguration {
    }
}
