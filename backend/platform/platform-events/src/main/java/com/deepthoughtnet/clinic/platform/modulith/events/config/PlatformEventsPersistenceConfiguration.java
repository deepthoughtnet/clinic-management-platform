package com.deepthoughtnet.clinic.platform.modulith.events.config;

import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventEntity;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Public persistence bootstrap for the platform module-event store.
 * <p>
 * The executable application imports this configuration instead of referencing the
 * internal repository/entity package names directly. That keeps the event store
 * ownership inside the platform-events module while leaving the application layer
 * with a stable, intentional contract.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = ModuleBusinessEventEntity.class)
@EnableJpaRepositories(basePackageClasses = ModuleBusinessEventRepository.class)
public class PlatformEventsPersistenceConfiguration {
}
