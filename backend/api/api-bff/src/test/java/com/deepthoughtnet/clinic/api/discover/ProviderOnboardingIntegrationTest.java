package com.deepthoughtnet.clinic.api.discover;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json",
        "spring.flyway.enabled=true",
        "clinic.ai.enabled=false",
        "clinic.ocr.enabled=false",
        "clinic.notifications.scheduler.enabled=false",
        "clinic.notifications.dispatcher.enabled=false",
        "clinic.carepilot.scheduler.enabled=false",
        "voice.stt.provider-order=mock",
        "voice.tts.provider-order=mock",
        "clinic.storage.minio.endpoint=http://localhost:9005",
        "clinic.storage.minio.access-key=minio",
        "clinic.storage.minio.secret-key=minio123",
        "clinic.storage.minio.bucket=clinic-documents"
})
class ProviderOnboardingIntegrationTest extends PostgresTestContainerSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProviderOnboardingService providerOnboardingService;

    @Autowired
    private ProviderApplicationRepository providerApplicationRepository;

    @MockBean
    private ObjectStorageService objectStorageService;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Test
    void providerRegistrationEndpointWiresControllerServiceRepositoryAndDatabase() throws Exception {
        long before = providerApplicationRepository.count();

        Map<String, Object> request = Map.of(
                "providerType", "INDIVIDUAL_DOCTOR",
                "email", "doctor.%s@example.com".formatted(UUID.randomUUID()),
                "phone", "+919999999999",
                "password", "StrongPass123",
                "termsAccepted", true,
                "privacyAccepted", true
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/provider-registration/providers",
                new HttpEntity<>(request, headers),
                Map.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("providerType", "INDIVIDUAL_DOCTOR");
        assertThat(response.getBody()).containsEntry("status", "DRAFT");
        assertThat(response.getBody()).containsKeys("id", "referenceNumber", "onboardingToken");
        assertThat(response.getBody().get("onboardingToken")).isInstanceOf(String.class);
        assertThat((String) response.getBody().get("onboardingToken")).isNotBlank();
        assertThat(providerOnboardingService).isNotNull();
        assertThat(providerApplicationRepository.count()).isEqualTo(before + 1);
        assertThat(providerApplicationRepository.findByTokenHash("").isEmpty()).isTrue();
    }
}
