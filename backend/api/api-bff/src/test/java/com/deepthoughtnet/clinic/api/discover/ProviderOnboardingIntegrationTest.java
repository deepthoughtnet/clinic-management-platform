package com.deepthoughtnet.clinic.api.discover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.DocumentRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.UploadedDocumentCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import org.springframework.web.client.HttpClientErrorException;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
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

    @Test
    void staleVersionUpdateReturnsHttp409Conflict() throws Exception {
        Map<String, Object> request = Map.of(
                "providerType", "CLINIC",
                "email", "clinic.%s@example.com".formatted(UUID.randomUUID()),
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
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        String id = (String) body.get("id");
        String token = (String) body.get("onboardingToken");
        long initialVersion = ((Number) body.get("version")).longValue();

        Map<String, Object> update = new java.util.LinkedHashMap<>();
        update.put("version", body.get("version"));
        update.put("displayName", "Sunrise Family Clinic");
        update.put("legalName", "Sunrise Family Clinic");
        update.put("organisationType", "Private clinic");
        update.put("registrationNumber", "CLIN-100");
        update.put("website", "https://example.com");
        update.put("languages", List.of("English"));
        update.put("biography", "Primary care clinic.");
        update.put("specialities", List.of("General Medicine"));
        update.put("ownership", "Private");
        update.put("appointmentDurationMinutes", 15);
        update.put("facilities", List.of("Parking"));
        Map<String, Object> requestLocation = new java.util.LinkedHashMap<>();
        requestLocation.put("label", "Primary");
        requestLocation.put("address", "Baner Road");
        requestLocation.put("city", "Pune");
        requestLocation.put("state", "Maharashtra");
        requestLocation.put("country", "India");
        requestLocation.put("pinCode", "411045");
        requestLocation.put("workingHours", "Mon-Sat 9 AM-5 PM");
        requestLocation.put("parkingAvailable", true);
        requestLocation.put("accessibilityAvailable", true);
        requestLocation.put("latitude", 18.5204);
        requestLocation.put("longitude", 73.8567);
        update.put("locations", List.of(requestLocation));

        HttpHeaders updateHeaders = new HttpHeaders();
        updateHeaders.setContentType(MediaType.APPLICATION_JSON);
        updateHeaders.set("X-Provider-Onboarding-Token", token);

        ResponseEntity<Map> firstUpdate = restTemplate.exchange(
                "/api/provider-registration/providers/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(update, updateHeaders),
                Map.class
        );
        assertThat(firstUpdate.getStatusCode().value()).isEqualTo(200);
        assertThat(firstUpdate.getBody()).isNotNull();
        long updatedVersion = ((Number) firstUpdate.getBody().get("version")).longValue();
        assertThat(updatedVersion).isGreaterThan(initialVersion);
        List<Map<String, Object>> locations = (List<Map<String, Object>>) firstUpdate.getBody().get("locations");
        assertThat(locations).singleElement().satisfies(persistedLocation -> {
            assertThat(((Number) persistedLocation.get("latitude")).doubleValue()).isEqualTo(18.5204);
            assertThat(((Number) persistedLocation.get("longitude")).doubleValue()).isEqualTo(73.8567);
        });

        ResponseEntity<Map> secondUpdate = restTemplate.exchange(
                "/api/provider-registration/providers/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(update, updateHeaders),
                Map.class
        );
        assertThat(secondUpdate.getStatusCode().value()).isEqualTo(409);
        assertThat(secondUpdate.getBody()).isNotNull();
        assertThat((String) secondUpdate.getBody().get("message")).contains("changed in another session");
    }

    @Test
    void onboardingApplicationResponsePreservesNullLocationCoordinates() {
        Map<String, Object> request = Map.of(
                "providerType", "CLINIC",
                "email", "null-location.%s@example.com".formatted(UUID.randomUUID()),
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
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        String id = (String) body.get("id");
        String token = (String) body.get("onboardingToken");

        Map<String, Object> update = new java.util.LinkedHashMap<>();
        update.put("version", body.get("version"));
        update.put("displayName", "Jeevanam Family Clinic");
        update.put("legalName", "Jeevanam Family Clinic");
        update.put("organisationType", "Private clinic");
        update.put("registrationNumber", "CLIN-101");
        update.put("website", "https://example.com");
        update.put("languages", List.of("English"));
        update.put("biography", "Primary care clinic.");
        update.put("specialities", List.of("General Medicine"));
        update.put("ownership", "Private");
        update.put("appointmentDurationMinutes", 15);
        update.put("facilities", List.of("Parking"));
        Map<String, Object> requestLocation = new java.util.LinkedHashMap<>();
        requestLocation.put("label", "Primary");
        requestLocation.put("address", "Jeevanam Family Clinic");
        requestLocation.put("city", "Kharadi");
        requestLocation.put("state", "Maharashtra");
        requestLocation.put("country", "India");
        requestLocation.put("pinCode", "411014");
        requestLocation.put("workingHours", "Mon-Sat 9 AM-5 PM");
        requestLocation.put("parkingAvailable", true);
        requestLocation.put("accessibilityAvailable", true);
        requestLocation.put("latitude", null);
        requestLocation.put("longitude", null);
        update.put("locations", List.of(requestLocation));

        HttpHeaders updateHeaders = new HttpHeaders();
        updateHeaders.setContentType(MediaType.APPLICATION_JSON);
        updateHeaders.set("X-Provider-Onboarding-Token", token);

        ResponseEntity<Map> updated = restTemplate.exchange(
                "/api/provider-registration/providers/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(update, updateHeaders),
                Map.class
        );
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> locations = (List<Map<String, Object>>) updated.getBody().get("locations");
        assertThat(locations).singleElement().satisfies(location -> {
            assertThat(location).containsEntry("latitude", null);
            assertThat(location).containsEntry("longitude", null);
        });

        ResponseEntity<Map> reloaded = restTemplate.exchange(
                "/api/provider-registration/providers/" + id,
                HttpMethod.GET,
                new HttpEntity<>(updateHeaders),
                Map.class
        );
        assertThat(reloaded.getStatusCode().value()).isEqualTo(200);
        List<Map<String, Object>> reloadedLocations = (List<Map<String, Object>>) reloaded.getBody().get("locations");
        assertThat(reloadedLocations).singleElement().satisfies(location -> {
            assertThat(location).containsEntry("latitude", null);
            assertThat(location).containsEntry("longitude", null);
        });
    }

    @Test
    void previewProjectsGalleryAndStreamsOnlyPublicBrandingDocuments() throws Exception {
        Map<String, Object> request = Map.of(
                "providerType", "CLINIC",
                "email", "clinic.%s@example.com".formatted(UUID.randomUUID()),
                "phone", "+919999999999",
                "password", "StrongPass123",
                "termsAccepted", true,
                "privacyAccepted", true
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> createdResponse = restTemplate.postForEntity(
                "/api/provider-registration/providers",
                new HttpEntity<>(request, headers),
                Map.class
        );
        assertThat(createdResponse.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> created = createdResponse.getBody();
        assertThat(created).isNotNull();

        String providerId = (String) created.get("id");
        String token = (String) created.get("onboardingToken");

        when(objectStorageService.buildDocumentStorageKey(any(UUID.class), anyString()))
                .thenAnswer(invocation -> "tenants/discover/" + invocation.getArgument(1, String.class));

        DocumentRecord logo = providerOnboardingService.uploadDocument(
                UUID.fromString(providerId),
                token,
                new UploadedDocumentCommand(ProviderDocumentType.LOGO, "logo.png", "image/png", 4, new byte[] {1, 2, 3, 4})
        );
        DocumentRecord cover = providerOnboardingService.uploadDocument(
                UUID.fromString(providerId),
                token,
                new UploadedDocumentCommand(ProviderDocumentType.COVER_IMAGE, "cover.png", "image/png", 4, new byte[] {1, 2, 3, 4})
        );
        DocumentRecord gallery = providerOnboardingService.uploadDocument(
                UUID.fromString(providerId),
                token,
                new UploadedDocumentCommand(ProviderDocumentType.GALLERY_IMAGE, "gallery.png", "image/png", 4, new byte[] {1, 2, 3, 4})
        );
        DocumentRecord registration = providerOnboardingService.uploadDocument(
                UUID.fromString(providerId),
                token,
                new UploadedDocumentCommand(ProviderDocumentType.REGISTRATION_CERTIFICATE, "registration.pdf", "application/pdf", 4, new byte[] {1, 2, 3, 4})
        );

        when(objectStorageService.getObjectBytes(anyString())).thenReturn(new byte[] {1, 2, 3, 4});

        HttpHeaders previewHeaders = new HttpHeaders();
        previewHeaders.set("X-Provider-Onboarding-Token", token);

        ResponseEntity<Map> previewResponse = restTemplate.exchange(
                "/api/provider-registration/providers/" + providerId + "/preview",
                HttpMethod.GET,
                new HttpEntity<>(previewHeaders),
                Map.class
        );
        assertThat(previewResponse.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> preview = previewResponse.getBody();
        assertThat(preview).isNotNull();
        Map<String, Object> branding = (Map<String, Object>) preview.get("branding");
        assertThat(branding.get("logoDocumentId")).isEqualTo(logo.id().toString());
        assertThat(branding.get("coverImageDocumentId")).isEqualTo(cover.id().toString());
        assertThat(((List<String>) branding.get("galleryDocumentIds"))).containsExactly(gallery.id().toString());
        assertThat(branding.toString()).doesNotContain(registration.id().toString());

        ResponseEntity<byte[]> logoContent = restTemplate.exchange(
                "/api/provider-registration/providers/" + providerId + "/documents/" + logo.id() + "/content",
                HttpMethod.GET,
                new HttpEntity<>(previewHeaders),
                byte[].class
        );
        assertThat(logoContent.getStatusCode().value()).isEqualTo(200);
        assertThat(logoContent.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/png"));
        assertThat(logoContent.getBody()).isNotEmpty();

        ResponseEntity<byte[]> coverContent = restTemplate.exchange(
                "/api/provider-registration/providers/" + providerId + "/documents/" + cover.id() + "/content",
                HttpMethod.GET,
                new HttpEntity<>(previewHeaders),
                byte[].class
        );
        assertThat(coverContent.getStatusCode().value()).isEqualTo(200);
        assertThat(coverContent.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/png"));

        ResponseEntity<byte[]> galleryContent = restTemplate.exchange(
                "/api/provider-registration/providers/" + providerId + "/documents/" + gallery.id() + "/content",
                HttpMethod.GET,
                new HttpEntity<>(previewHeaders),
                byte[].class
        );
        assertThat(galleryContent.getStatusCode().value()).isEqualTo(200);
        assertThat(galleryContent.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/png"));

        assertThatThrownBy(() -> restTemplate.exchange(
                "/api/provider-registration/providers/" + providerId + "/documents/" + registration.id() + "/content",
                HttpMethod.GET,
                new HttpEntity<>(previewHeaders),
                String.class
        )).isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
