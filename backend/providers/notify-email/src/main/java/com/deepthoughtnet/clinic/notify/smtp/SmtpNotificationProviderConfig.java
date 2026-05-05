package com.deepthoughtnet.clinic.notify.smtp;

import com.deepthoughtnet.clinic.notify.NotificationProvider;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties(MailNotificationProperties.class)
public class SmtpNotificationProviderConfig {

    @Bean
    @ConditionalOnExpression("'${clinic.mail.enabled:false}' == 'true' && '${clinic.mail.provider:logging}' == 'smtp'")
    public JavaMailSender clinicJavaMailSender(MailNotificationProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.auth", String.valueOf(properties.isAuth()));
        javaMailProperties.put("mail.smtp.starttls.enable", String.valueOf(properties.isStarttls()));
        javaMailProperties.put("mail.smtp.starttls.required", String.valueOf(properties.isStarttls()));
        return sender;
    }

    @Bean
    @ConditionalOnExpression("'${clinic.mail.enabled:false}' == 'true' && '${clinic.mail.provider:logging}' == 'smtp'")
    public NotificationProvider smtpNotificationProvider(JavaMailSender mailSender, MailNotificationProperties properties) {
        return new SmtpNotificationProvider(mailSender, properties);
    }
}
