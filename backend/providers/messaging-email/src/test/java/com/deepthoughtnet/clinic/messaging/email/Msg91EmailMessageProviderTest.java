package com.deepthoughtnet.clinic.messaging.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

class Msg91EmailMessageProviderTest {
    @Test
    void disabledProviderIsNotConfigured() {
        var properties = properties();
        var provider = new Msg91EmailMessageProvider(properties, mock(JavaMailSender.class));

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    void missingCredentialsAreNotConfigured() {
        var properties = properties();
        properties.setEnabled(true);
        properties.setUsername("user");
        var provider = new Msg91EmailMessageProvider(properties, mock(JavaMailSender.class));

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    void completeConfigurationIsReadyWithoutExposingSecrets() {
        var properties = properties();
        properties.setEnabled(true);
        properties.setUsername("user");
        properties.setPassword("secret");
        properties.setFrom("no-reply@example.com");
        var provider = new Msg91EmailMessageProvider(properties, mock(JavaMailSender.class));

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.providerName()).isEqualTo("msg91-email-smtp");
        assertThat(provider.passwordConfigured()).isTrue();
    }

    private Msg91EmailMessagingProperties properties() {
        var properties = new Msg91EmailMessagingProperties();
        properties.setHost("smtp.mailer91.com");
        properties.setPort(587);
        return properties;
    }
}
