package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        classes = DiscoverOwnershipPersistenceScanTest.TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:discover_ownership_scan;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "spring.sql.init.mode=never"
        }
)
class DiscoverOwnershipPersistenceScanTest {

    @Autowired
    private PublicProfileClaimIntentRepository publicProfileClaimIntentRepository;

    @Test
    void springCreatesClaimIntentRepositoryBean() {
        assertThat(publicProfileClaimIntentRepository).isNotNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import(PersistenceScanConfig.class)
    static class TestApplication {
    }
}
