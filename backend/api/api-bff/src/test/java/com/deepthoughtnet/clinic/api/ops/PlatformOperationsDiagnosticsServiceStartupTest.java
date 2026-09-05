package com.deepthoughtnet.clinic.api.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.carepilot.CarePilotAiCallSchedulerMonitor;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotRuntimeSchedulerMonitor;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentTextExtractionService;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.DeterministicLabFactParser;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityService;
import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.storage.minio.MinioStorageProperties;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import io.minio.MinioClient;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlatformOperationsDiagnosticsServiceStartupTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void platformOperationsDiagnosticsServiceCanBeCreatedWithoutAClincialDocumentAdapterBean() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(PlatformOperationsDiagnosticsService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        PlatformOperationsDiagnosticStateStore stateStore() {
            return new PlatformOperationsDiagnosticStateStore();
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);
            return jdbcTemplate;
        }

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        MinioClient minioClient() {
            return mock(MinioClient.class);
        }

        @Bean
        MinioStorageProperties minioStorageProperties() {
            MinioStorageProperties properties = new MinioStorageProperties();
            properties.setBucket("clinic-documents");
            properties.setAutoCreateBucket(true);
            return properties;
        }

        @Bean
        DatabaseSchemaIntegrityService databaseSchemaIntegrityService() {
            return mock(DatabaseSchemaIntegrityService.class);
        }

        @Bean
        CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor() {
            return new CarePilotRuntimeSchedulerMonitor(true);
        }

        @Bean
        CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor() {
            return new CarePilotAiCallSchedulerMonitor(true, java.time.Duration.ofMinutes(5));
        }

        @Bean
        SchedulerLockMonitor schedulerLockMonitor() {
            return new SchedulerLockMonitor();
        }

        @Bean(name = "geminiLlmClient")
        ObjectProvider<LlmClient> geminiLlmClient() {
            return provider(null);
        }

        @Bean(name = "groqLlmClient")
        ObjectProvider<LlmClient> groqLlmClient() {
            return provider(null);
        }

        @Bean
        ClinicalDocumentTextExtractionService textExtractionService() {
            return new ClinicalDocumentTextExtractionService(ocrProvider());
        }

        @Bean
        DeterministicLabFactParser deterministicLabFactParser() {
            return new DeterministicLabFactParser();
        }

        @Bean
        AuditEventPublisher auditEventPublisher() {
            return mock(AuditEventPublisher.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PlatformOperationsDiagnosticsService platformOperationsDiagnosticsService(
                PlatformOperationsDiagnosticStateStore stateStore,
                JdbcTemplate jdbcTemplate,
                DataSource dataSource,
                ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
                MinioClient minioClient,
                MinioStorageProperties minioStorageProperties,
                DatabaseSchemaIntegrityService databaseSchemaIntegrityService,
                CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor,
                CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor,
                SchedulerLockMonitor schedulerLockMonitor,
                ObjectProvider<LlmClient> geminiLlmClient,
                ObjectProvider<LlmClient> groqLlmClient,
                ClinicalDocumentTextExtractionService textExtractionService,
                DeterministicLabFactParser deterministicLabFactParser,
                AuditEventPublisher auditEventPublisher,
                com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                Environment environment
        ) {
            return new PlatformOperationsDiagnosticsService(
                    stateStore,
                    jdbcTemplate,
                    dataSource,
                    redisConnectionFactoryProvider,
                    minioClient,
                    minioStorageProperties,
                    databaseSchemaIntegrityService,
                    reminderSchedulerMonitor,
                    aiCallSchedulerMonitor,
                    schedulerLockMonitor,
                    geminiLlmClient,
                    groqLlmClient,
                    textExtractionService,
                    deterministicLabFactParser,
                    auditEventPublisher,
                    objectMapper,
                    environment
            );
        }

        @SuppressWarnings("unchecked")
        private static ObjectProvider<LlmClient> provider(LlmClient client) {
            ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(Optional.ofNullable(client).orElse(null));
            return provider;
        }

        @SuppressWarnings("unchecked")
        private static ObjectProvider<OcrProvider> ocrProvider() {
            ObjectProvider<OcrProvider> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(null);
            return provider;
        }
    }
}
