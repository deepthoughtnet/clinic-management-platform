package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

class VoiceCapabilityConfigurationDefaultsTest {
    @Test
    void applicationDefaultsFailClosedForSharedAndGuestVoiceBooking() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        var application = sources.getFirst();

        assertThat(application.getProperty("aiva.v2.enabled")).isEqualTo("${AIVA_V2_ENABLED:false}");
        assertThat(application.getProperty("voice.aiva-v2-enabled")).isEqualTo("${VOICE_AIVA_V2_ENABLED:false}");
        assertThat(application.getProperty("voice.booking-enabled")).isEqualTo("${AIVA_VOICE_BOOKING_ENABLED:false}");
        assertThat(application.getProperty("voice.guest-booking-enabled"))
                .isEqualTo("${AIVA_VOICE_GUEST_BOOKING_ENABLED:false}");
        assertThat(application.getProperty("clinic.aiva.voice.transaction-auth.default-policy.booking-enabled"))
                .isEqualTo("${AIVA_VOICE_BOOKING_ENABLED:false}");
        assertThat(application.getProperty("clinic.aiva.voice.transaction-auth.default-policy.guest-booking-enabled"))
                .isEqualTo("${AIVA_VOICE_GUEST_BOOKING_ENABLED:false}");
        assertThat(new VoiceTestProperties().isAivaV2Enabled()).isFalse();
    }

    @Test
    void startupCapabilityReporterWiresAndReportsWithoutTenantOrPatientIdentifiers() {
        new ApplicationContextRunner()
                .withPropertyValues("aiva.v2.enabled=false", "voice.aiva-v2-enabled=false")
                .withBean(VoiceTestProperties.class, VoiceTestProperties::new)
                .withBean(VoiceOperatorProperties.class, VoiceOperatorProperties::new)
                .withUserConfiguration(ReporterConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VoiceCapabilityStartupReporter reporter = context.getBean(VoiceCapabilityStartupReporter.class);
                    org.assertj.core.api.Assertions.assertThatCode(reporter::reportCapabilities).doesNotThrowAnyException();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(VoiceCapabilityStartupReporter.class)
    static class ReporterConfiguration {}
}
