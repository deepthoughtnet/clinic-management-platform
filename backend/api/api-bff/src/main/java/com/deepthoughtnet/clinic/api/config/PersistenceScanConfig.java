package com.deepthoughtnet.clinic.api.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {
        "com.deepthoughtnet.clinic.platform.audit.db",
        "com.deepthoughtnet.clinic.notification.db",
        "com.deepthoughtnet.clinic.identity.db",
        "com.deepthoughtnet.clinic.ai.db"
})
@EnableJpaRepositories(basePackages = {
        "com.deepthoughtnet.clinic.platform.audit.db",
        "com.deepthoughtnet.clinic.notification.db",
        "com.deepthoughtnet.clinic.identity.db",
        "com.deepthoughtnet.clinic.ai.db"
})
public class PersistenceScanConfig {
}
