package com.deepthoughtnet.clinic.api.publicsite;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.PublicDoctorPracticeAssociationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = PublicCatalogFacadeSpringContextTest.TestApplication.class)
class PublicCatalogFacadeSpringContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private ProviderPublicProfileService publicProfileService;

    @MockBean
    private ProviderLinkingService providerLinkingService;

    @MockBean
    private ProviderPublicProfileModerationService moderationService;

    @MockBean
    private PublicDoctorPracticeAssociationService publicDoctorPracticeAssociationService;

    @Test
    void springCreatesPublicCatalogControllerAndFacade() {
        assertThat(applicationContext.getBean(PublicCatalogController.class)).isNotNull();
        assertThat(applicationContext.getBean(PublicCatalogFacade.class)).isNotNull();
        assertThat(applicationContext.getBean(ProviderLinkingService.class)).isNotNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({
            PublicCatalogController.class,
            PublicCatalogFacade.class
    })
    static class TestApplication {
        @Bean
        Clock providerIntegrationClock() {
            return Clock.systemUTC();
        }
    }
}
