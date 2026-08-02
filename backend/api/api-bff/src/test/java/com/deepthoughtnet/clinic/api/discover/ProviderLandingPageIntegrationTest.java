package com.deepthoughtnet.clinic.api.discover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentRepository;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.landingpage.db.LandingPageRepository;
import com.deepthoughtnet.clinic.discover.landingpage.db.LandingPageVersionRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionRepository;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
class ProviderLandingPageIntegrationTest extends PostgresTestContainerSupport {
    private static final UUID TEST_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProviderOnboardingService providerOnboardingService;

    @Autowired
    private ProviderApplicationRepository providerApplicationRepository;

    @Autowired
    private ProviderDocumentRepository providerDocumentRepository;

    @Autowired
    private LandingPageRepository landingPageRepository;

    @Autowired
    private LandingPageVersionRepository landingPageVersionRepository;

    @Autowired
    private DiscoverPublicProviderProfileRepository publicProfileRepository;

    @Autowired
    private DiscoverPublicProviderProfileVersionRepository publicProfileVersionRepository;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockBean
    private ObjectStorageService objectStorageService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Test
    void providerCanEditPublishAndServeStructuredLandingPages() throws Exception {
        String slug = "sunrise-family-clinic";

        Map<String, Object> created = createPublishedProvider(slug);
        String token = (String) created.get("onboardingToken");
        assertThat(token).isNotBlank();

        ResponseEntity<Map> draftResponse = restTemplate.exchange(
                "/api/provider/landing-page",
                HttpMethod.GET,
                authorized(token, null),
                Map.class
        );
        assertThat(draftResponse.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> draft = draftResponse.getBody();
        assertThat(draft).isNotNull();
        assertThat(draft).containsEntry("editable", true);
        assertThat(draft).containsEntry("canonicalSlug", slug);
        assertThat(((List<?>) draft.get("templates"))).isNotEmpty();

        Map<String, Object> updatedSection = Map.of(
                "key", "HERO",
                "enabled", true,
                "displayOrder", 0,
                "title", "Sunrise Family Clinic",
                "description", "Patient-first care for families.",
                "visibilityRule", "PUBLIC",
                "content", Map.of()
        );

        ResponseEntity<Map> saveResponse = restTemplate.exchange(
                "/api/provider/landing-page",
                HttpMethod.PUT,
                authorized(token, Map.of(
                        "version", draft.get("draftVersionNumber"),
                        "templateKey", ((Map<String, Object>) ((List<?>) draft.get("templates")).get(0)).get("templateKey"),
                        "theme", draftSnapshotTheme(draft),
                        "sections", List.of(updatedSection)
                )),
                Map.class
        );
        assertThat(saveResponse.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> saved = saveResponse.getBody();
        assertThat(saved).isNotNull();
        assertThat((List<?>) ((Map<String, Object>) saved.get("draft")).get("sections")).isNotEmpty();

        ResponseEntity<Map> publishResponse = restTemplate.exchange(
                "/api/provider/landing-page/publish",
                HttpMethod.POST,
                authorized(token, null),
                Map.class
        );
        assertThat(publishResponse.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> published = publishResponse.getBody();
        assertThat(published).isNotNull();
        assertThat(published.get("published")).isEqualTo(true);
        assertThat(published.get("publishedVersionNumber")).isEqualTo(1);

        ResponseEntity<Map> secondPublishResponse = restTemplate.exchange(
                "/api/provider/landing-page",
                HttpMethod.PUT,
                authorized(token, Map.of(
                        "version", published.get("draftVersionNumber"),
                        "templateKey", ((Map<String, Object>) ((List<?>) published.get("templates")).get(0)).get("templateKey"),
                        "theme", draftSnapshotTheme(published),
                        "sections", List.of(Map.of(
                                "key", "HERO",
                                "enabled", true,
                                "displayOrder", 0,
                                "title", "Sunrise Family Clinic Care",
                                "description", "Updated landing page copy.",
                                "visibilityRule", "PUBLIC",
                                "content", Map.of()
                        ))
                )),
                Map.class
        );
        assertThat(secondPublishResponse.getStatusCode().value()).isEqualTo(200);
        restTemplate.exchange("/api/provider/landing-page/publish", HttpMethod.POST, authorized(token, null), Map.class);

        ResponseEntity<List> versionsResponse = restTemplate.exchange(
                "/api/provider/landing-page/versions",
                HttpMethod.GET,
                authorized(token, null),
                List.class
        );
        assertThat(versionsResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(versionsResponse.getBody()).isNotNull();
        assertThat(versionsResponse.getBody()).hasSize(2);

        ResponseEntity<Map> publicResponse = restTemplate.getForEntity("/api/public/landing/%s".formatted(slug), Map.class);
        assertThat(publicResponse.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> publicLanding = publicResponse.getBody();
        assertThat(publicLanding).isNotNull();
        assertThat(publicLanding.get("published")).isEqualTo(true);
        assertThat(publicLanding.get("canonicalSlug")).isEqualTo(slug);
        assertThat(publicLanding).doesNotContainKey("draft");
        assertThat(((Map<?, ?>) publicLanding.get("publishedSnapshot")).get("templateKey")).isEqualTo(((Map<?, ?>) published.get("draft")).get("templateKey"));
        Map<String, Object> profile = (Map<String, Object>) publicLanding.get("profile");
        assertThat(profile.get("logoUrl")).isEqualTo("https://example.test/media/logo-storage");
        assertThat(profile.get("coverUrl")).isEqualTo("https://example.test/media/cover-storage");
        assertThat((List<String>) profile.get("galleryImageUrls")).containsExactly("https://example.test/media/gallery-storage");
        assertThat(profile.toString()).doesNotContain("registration-storage");
    }

    private Map<String, Object> draftSnapshotTheme(Map<String, Object> response) {
        Map<String, Object> draft = (Map<String, Object>) response.get("draft");
        return (Map<String, Object>) draft.get("theme");
    }

    private HttpEntity<Object> authorized(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Provider-Onboarding-Token", token);
        headers.set("X-Tenant-Id", TEST_TENANT_ID.toString());
        headers.setBearerAuth("provider-access-token");
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> createPublishedProvider(String slug) throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("provider-access-token")
                .header("alg", "none")
                .claim("iss", "http://localhost:8182/realms/clinic-management")
                .claim("sub", "provider-landing-page-test")
                .claim("email", "provider-landing-page-test@example.com")
                .claim("preferred_username", "provider-landing-page-test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                .build());
        when(objectStorageService.generatePresignedDownloadUrl(any(), any(Duration.class))).thenAnswer(invocation -> "https://example.test/media/" + invocation.getArgument(0));
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
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/provider-registration/providers", new HttpEntity<>(request, headers), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        UUID providerId = UUID.fromString((String) body.get("id"));

        ProviderDocumentEntity logo = providerDocumentRepository.save(new ProviderDocumentEntity(providerId, ProviderDocumentType.LOGO, "logo.png", "image/png", 1024, "logo-storage"));
        ProviderDocumentEntity cover = providerDocumentRepository.save(new ProviderDocumentEntity(providerId, ProviderDocumentType.COVER_IMAGE, "cover.png", "image/png", 2048, "cover-storage"));
        ProviderDocumentEntity gallery = providerDocumentRepository.save(new ProviderDocumentEntity(providerId, ProviderDocumentType.GALLERY_IMAGE, "gallery.png", "image/png", 4096, "gallery-storage"));
        providerDocumentRepository.save(new ProviderDocumentEntity(providerId, ProviderDocumentType.REGISTRATION_CERTIFICATE, "registration.pdf", "application/pdf", 8192, "registration-storage"));

        ProviderApplicationEntity application = providerApplicationRepository.findById(UUID.fromString((String) body.get("id"))).orElseThrow();
        application.setStatus(com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus.PUBLISHED);
        application.setDisplayName("Sunrise Family Clinic");
        application.setLegalName("Sunrise Family Clinic Private Limited");
        application.setBiography("Care for the whole family.");
        application.setQualification("MBBS");
        application.setMedicalCouncil("MCI");
        application.setWebsite("https://sunrise.example.com");
        application.setSpecialities("Family Medicine,Internal Medicine");
        application.setDepartments("General Medicine,Pediatrics");
        application.setFacilities("Pharmacy,Lab,Waiting Lounge");
        application.setOwnership("Private");
        application.setHospitalType("Clinic");
        application.setEmergencyAvailable(true);
        application.setContactVerified(true);
        application.setLogoDocumentId(logo.getId());
        application.setCoverImageDocumentId(cover.getId());
        providerApplicationRepository.save(application);

        OffsetDateTime publishedAt = OffsetDateTime.now();
        UUID versionId = UUID.randomUUID();
        PublicProviderProfileSnapshot snapshot = new PublicProviderProfileSnapshot(
                providerId,
                ProviderType.CLINIC,
                "DISCOVER_ONBOARDING_APPLICATION",
                "REF-001",
                "Sunrise Family Clinic",
                "Sunrise Family Clinic Private Limited",
                slug,
                "Family-first care for the neighbourhood.",
                "Family-first care for the neighbourhood.",
                "MBBS",
                "MCI",
                12,
                new BigDecimal("500"),
                15,
                true,
                List.of("English", "Hindi"),
                List.of("Family Medicine", "Internal Medicine"),
                List.<String>of(),
                List.of("Consultations", "Preventive care"),
                List.of("General Medicine", "Pediatrics"),
                List.of("Pharmacy", "Lab", "Waiting Lounge"),
                List.of("In-person", "Online"),
                List.of(new PublicProviderLocationSnapshot("Main Branch", "12 Health Street", "Pune", "Maharashtra", "India", "411001", "Mon-Sat 9 AM - 8 PM", true, true, null, null)),
                List.of(new PublicProviderGalleryImageSnapshot(gallery.getId(), "Reception")),
                List.of("https://example.test/media/gallery-storage"),
                logo.getId(),
                cover.getId(),
                null,
                "+911234567890",
                "hello@sunrise.example.com",
                "https://sunrise.example.com",
                "Pune",
                "Baner",
                "Maharashtra",
                "India",
                "Family Medicine",
                "Family-first care for the neighbourhood.",
                "Private",
                "Clinic",
                "Dr. Sunita Rao",
                null,
                true,
                1,
                2,
                2,
                1,
                "ONLINE_BOOKING",
                false,
                publishedAt,
                1,
                "/discover/clinics/%s".formatted(slug)
        );

        DiscoverPublicProviderProfileEntity profile = DiscoverPublicProviderProfileEntity.create(
                providerId,
                ProviderType.CLINIC,
                "DISCOVER_ONBOARDING_APPLICATION",
                providerId.toString(),
                1L,
                publishedAt,
                slug,
                versionId,
                1,
                "Sunrise Family Clinic",
                "Sunrise Family Clinic Private Limited",
                "Family-first care for the neighbourhood.",
                "Family Medicine",
                "Family Medicine,Internal Medicine",
                "",
                "Consultations,Preventive care",
                "General Medicine,Pediatrics",
                "Pharmacy,Lab,Waiting Lounge",
                "English,Hindi",
                "In-person,Online",
                logo.getId(),
                cover.getId(),
                null,
                "+911234567890",
                "hello@sunrise.example.com",
                "https://sunrise.example.com",
                "Pune",
                "Baner",
                "Maharashtra",
                "India",
                "Family Medicine",
                "Private",
                "Clinic",
                "Dr. Sunita Rao",
                null,
                true,
                1,
                2,
                2,
                1,
                "ONLINE_BOOKING",
                publishedAt
        );
        publicProfileRepository.save(profile);
        publicProfileVersionRepository.save(DiscoverPublicProviderProfileVersionEntity.create(
                providerId,
                1,
                1,
                "SUBMITTED",
                "PUBLISHED",
                "PROVIDER",
                "Published for public landing page",
                "hash-1",
                objectMapper.writeValueAsString(snapshot),
                slug,
                publishedAt
        ));

        return body;
    }
}
