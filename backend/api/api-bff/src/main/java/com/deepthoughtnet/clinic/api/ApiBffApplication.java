package com.deepthoughtnet.clinic.api;

import com.deepthoughtnet.clinic.notification.service.NotificationProperties;
import com.deepthoughtnet.clinic.api.notifications.NotificationsSchedulerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.deepthoughtnet.clinic")
@EnableScheduling
@EnableConfigurationProperties({
        NotificationProperties.class,
        NotificationsSchedulerProperties.class
})
public class ApiBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiBffApplication.class, args);
    }
}
