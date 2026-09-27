package com.deepthoughtnet.clinic.messaging.email;

import java.util.Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties(Msg91EmailMessagingProperties.class)
public class Msg91EmailMessageProviderConfig {
    @Bean
    public Msg91EmailMessageProvider msg91EmailMessageProvider(Msg91EmailMessagingProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.smtp.auth", String.valueOf(properties.isAuth()));
        mail.put("mail.smtp.starttls.enable", String.valueOf(properties.isStarttls()));
        mail.put("mail.smtp.starttls.required", String.valueOf(properties.isStarttls()));
        return new Msg91EmailMessageProvider(properties, sender);
    }
}
