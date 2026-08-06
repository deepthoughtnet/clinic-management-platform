package com.deepthoughtnet.clinic.discover.publicprofile;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderLocationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderLocationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderServiceEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderServiceRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderSubmissionEntity;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProfileMediaContent;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderSearchCriteria;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicSpecialitySummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderPublicationRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicationReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileSlugEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileSlugRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionRepository;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderPublicProfileService {
    private static final Duration MEDIA_URL_TTL = Duration.ofDays(7);
    private static final String SOURCE_SYSTEM_ONBOARDING = "DISCOVER_ONBOARDING_APPLICATION";
    private static final String SOURCE_SYSTEM_MODERATED_PROFILE = "PROVIDER_PUBLIC_PROFILE_DRAFT";
    private static final String BOOKING_MODE_ONLINE = "ONLINE_BOOKING";

    private final DiscoverPublicProviderProfileRepository profiles;
    private final DiscoverPublicProviderProfileVersionRepository versions;
    private final DiscoverPublicProviderProfileSlugRepository slugs;
    private final ProviderLocationRepository locations;
    private final ProviderServiceRepository services;
    private final ProviderDocumentRepository documents;
    private final ObjectStorageService storageService;
    private final ObjectMapper objectMapper;

    public ProviderPublicProfileService(
            DiscoverPublicProviderProfileRepository profiles,
            DiscoverPublicProviderProfileVersionRepository versions,
            DiscoverPublicProviderProfileSlugRepository slugs,
            ProviderLocationRepository locations,
            ProviderServiceRepository services,
            ProviderDocumentRepository documents,
            ObjectStorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.profiles = profiles;
        this.versions = versions;
        this.slugs = slugs;
        this.locations = locations;
        this.services = services;
        this.documents = documents;
        this.storageService = storageService;
        this.objectMapper = objectMapper.copy().findAndRegisterModules().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Transactional
    public PublicProviderPublicationRecord publishApprovedApplication(ProviderApplicationEntity application, ProviderSubmissionEntity submission, String publicationReason) {
        require(application != null, "provider application is required");
        require(submission != null, "submitted profile snapshot is required");
        require(application.getStatus() == ProviderLifecycleStatus.APPROVED, "provider application must be approved before publishing");
        require(application.getId().equals(submission.getProviderId()), "provider submission does not belong to application");

        OffsetDateTime publishedAt = OffsetDateTime.now();
        String requestedSlug = slugFor(application);
        String canonicalSlug = ensureUniqueSlug(requestedSlug, application.getId());

        int nextVersion = versions.findFirstByProviderIdOrderByVersionNumberDesc(application.getId())
                .map(DiscoverPublicProviderProfileVersionEntity::getVersionNumber)
                .orElse(0) + 1;

        PublicProviderProfileSnapshot snapshot = buildSnapshot(application, canonicalSlug, publishedAt, nextVersion);
        return upsertPublicProfile(snapshot, submission.getVersionNumber(), submission.getStatusBefore(), submission.getStatusAfter(), publicationReason, publishedAt);
    }

    @Transactional
    public PublicProviderPublicationRecord upsertPublicProfile(
            PublicProviderProfileSnapshot snapshot,
            Integer sourceSubmissionVersionNumber,
            String statusBefore,
            String statusAfter,
            String publicationReason,
            OffsetDateTime publishedAt
    ) {
        return upsertLifecycleProfile(
                snapshot,
                sourceSubmissionVersionNumber,
                statusBefore,
                statusAfter,
                publicationReason,
                publishedAt,
                "PUBLISHED",
                snapshot.sourceSystem(),
                snapshot.providerId().toString(),
                sourceSubmissionVersionNumber == null ? 0L : sourceSubmissionVersionNumber.longValue(),
                publishedAt == null ? OffsetDateTime.now() : publishedAt,
                0L
        );
    }

    @Transactional
    public PublicProviderPublicationRecord upsertLifecycleProfile(
            PublicProviderProfileSnapshot snapshot,
            Integer sourceSubmissionVersionNumber,
            String statusBefore,
            String statusAfter,
            String publicationReason,
            OffsetDateTime projectedAt,
            String publicationStatus,
            String sourceSystem,
            String sourceEntityReference,
            long sourceRevision,
            OffsetDateTime sourceUpdatedAt,
            long connectionRevision
    ) {
        require(snapshot != null, "public profile snapshot is required");
        require(snapshot.providerId() != null, "public profile source reference is required");
        require(snapshot.providerType() != null, "public profile type is required");
        require(StringUtils.hasText(snapshot.sourceSystem()), "public profile source system is required");
        UUID effectiveProviderId = resolveEffectiveProviderId(snapshot);
        OffsetDateTime resolvedProjectedAt = projectedAt == null ? OffsetDateTime.now() : projectedAt;
        String lifecycleStatus = StringUtils.hasText(publicationStatus) ? publicationStatus : "PUBLISHED";
        DiscoverPublicProviderProfileEntity existingProfile = profiles.findByProviderId(effectiveProviderId).orElse(null);
        String resolvedSourceSystem = StringUtils.hasText(sourceSystem) ? sourceSystem : snapshot.sourceSystem();
        String resolvedSourceEntityReference = StringUtils.hasText(sourceEntityReference) ? sourceEntityReference : snapshot.providerId().toString();
        if (existingProfile != null && shouldPreserveModeratedProjection(existingProfile, resolvedSourceSystem)) {
            return currentPublicationRecord(existingProfile);
        }
        if (existingProfile != null && !effectiveProviderId.equals(snapshot.providerId())) {
            resolvedSourceSystem = existingProfile.getSourceSystem();
            resolvedSourceEntityReference = existingProfile.getSourceEntityReference();
        }
        long resolvedSourceRevision = sourceRevision;
        OffsetDateTime resolvedSourceUpdatedAt = sourceUpdatedAt == null ? resolvedProjectedAt : sourceUpdatedAt;

        String snapshotJson = snapshotJson(snapshot);
        String snapshotHash = digest(snapshotJson);
        int targetVersionNumber = sourceSubmissionVersionNumber == null
                ? versions.findFirstByProviderIdOrderByVersionNumberDesc(effectiveProviderId)
                .map(DiscoverPublicProviderProfileVersionEntity::getVersionNumber)
                .orElse(0) + 1
                : sourceSubmissionVersionNumber;

        Optional<DiscoverPublicProviderProfileVersionEntity> duplicate = versions.findByProviderIdAndVersionNumber(effectiveProviderId, targetVersionNumber);
        if (duplicate.isPresent()) {
            DiscoverPublicProviderProfileVersionEntity version = duplicate.get();
            if (hasSameProjectedContent(version, snapshot)) {
                return projectExistingVersion(snapshot, effectiveProviderId, version, resolvedProjectedAt, lifecycleStatus,
                        resolvedSourceSystem, resolvedSourceEntityReference, resolvedSourceRevision,
                        resolvedSourceUpdatedAt, connectionRevision);
            }
            Optional<DiscoverPublicProviderProfileVersionEntity> existingApprovedProjection = sourceSubmissionVersionNumber == null
                    ? Optional.empty()
                    : versions.findFirstByProviderIdAndSourceSubmissionVersionNumberOrderByVersionNumberDesc(
                    effectiveProviderId,
                    sourceSubmissionVersionNumber
            ).filter(existing -> hasSameProjectedContent(existing, snapshot));
            if (existingApprovedProjection.isPresent()) {
                return projectExistingVersion(snapshot, effectiveProviderId, existingApprovedProjection.get(), resolvedProjectedAt,
                        lifecycleStatus, resolvedSourceSystem, resolvedSourceEntityReference, resolvedSourceRevision,
                        resolvedSourceUpdatedAt, connectionRevision);
            }
            targetVersionNumber = nextProjectionVersionNumber(effectiveProviderId, targetVersionNumber);
        }

        DiscoverPublicProviderProfileVersionEntity version = versions.save(DiscoverPublicProviderProfileVersionEntity.create(
                effectiveProviderId,
                targetVersionNumber,
                sourceSubmissionVersionNumber == null ? targetVersionNumber : sourceSubmissionVersionNumber,
                statusBefore,
                statusAfter == null ? lifecycleStatus : statusAfter,
                snapshot.sourceSystem(),
                StringUtils.hasText(publicationReason) ? publicationReason : "Published public profile",
                snapshotHash,
                snapshotJson,
                snapshot.canonicalSlug(),
                resolvedProjectedAt
        ));

        upsertSlugAlias(effectiveProviderId, version.getId(), targetVersionNumber, snapshot.canonicalSlug(), resolvedProjectedAt);
        upsertAggregate(snapshot, effectiveProviderId, version.getId(), targetVersionNumber, resolvedProjectedAt, lifecycleStatus, resolvedSourceSystem, resolvedSourceEntityReference, resolvedSourceRevision, resolvedSourceUpdatedAt, connectionRevision);

        return new PublicProviderPublicationRecord(
                effectiveProviderId,
                snapshot.providerType(),
                snapshot.canonicalSlug(),
                targetVersionNumber,
                resolvedProjectedAt,
                publicPath(snapshot.providerType(), snapshot.canonicalSlug())
        );
    }

    private PublicProviderPublicationRecord projectExistingVersion(
            PublicProviderProfileSnapshot snapshot,
            UUID providerId,
            DiscoverPublicProviderProfileVersionEntity version,
            OffsetDateTime projectedAt,
            String publicationStatus,
            String sourceSystem,
            String sourceEntityReference,
            long sourceRevision,
            OffsetDateTime sourceUpdatedAt,
            long connectionRevision
    ) {
        upsertSlugAlias(providerId, version.getId(), version.getVersionNumber(), snapshot.canonicalSlug(), projectedAt);
        upsertAggregate(snapshot, providerId, version.getId(), version.getVersionNumber(), projectedAt, publicationStatus,
                sourceSystem, sourceEntityReference, sourceRevision, sourceUpdatedAt, connectionRevision);
        return new PublicProviderPublicationRecord(
                providerId,
                snapshot.providerType(),
                snapshot.canonicalSlug(),
                version.getVersionNumber(),
                projectedAt,
                publicPath(snapshot.providerType(), snapshot.canonicalSlug())
        );
    }

    private int nextProjectionVersionNumber(UUID providerId, int occupiedVersionNumber) {
        int latestVersionNumber = versions.findFirstByProviderIdOrderByVersionNumberDesc(providerId)
                .map(DiscoverPublicProviderProfileVersionEntity::getVersionNumber)
                .orElse(0);
        return Math.max(latestVersionNumber, occupiedVersionNumber) + 1;
    }

    private boolean shouldPreserveModeratedProjection(
            DiscoverPublicProviderProfileEntity existingProfile,
            String incomingSourceSystem
    ) {
        if (SOURCE_SYSTEM_MODERATED_PROFILE.equalsIgnoreCase(incomingSourceSystem)) {
            return false;
        }
        return versions.findById(existingProfile.getLatestPublishedVersionId())
                .map(DiscoverPublicProviderProfileVersionEntity::getPublishedBy)
                .filter(StringUtils::hasText)
                .filter(SOURCE_SYSTEM_MODERATED_PROFILE::equalsIgnoreCase)
                .isPresent();
    }

    private PublicProviderPublicationRecord currentPublicationRecord(DiscoverPublicProviderProfileEntity profile) {
        return new PublicProviderPublicationRecord(
                profile.getProviderId(),
                profile.getProviderType(),
                profile.getCanonicalSlug(),
                profile.getLatestPublishedVersionNumber(),
                profile.getProjectedAt(),
                publicPath(profile.getProviderType(), profile.getCanonicalSlug())
        );
    }

    private boolean hasSameProjectedContent(
            DiscoverPublicProviderProfileVersionEntity existingVersion,
            PublicProviderProfileSnapshot requestedSnapshot
    ) {
        try {
            JsonNode existingContent = objectMapper.readTree(existingVersion.getSnapshotJson());
            JsonNode requestedContent = objectMapper.valueToTree(requestedSnapshot);
            removeProjectionMetadata(existingContent);
            removeProjectionMetadata(requestedContent);
            return Objects.equals(existingContent, requestedContent);
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    private void removeProjectionMetadata(JsonNode snapshot) {
        if (snapshot instanceof ObjectNode objectNode) {
            objectNode.remove("publishedAt");
        }
    }

    @Transactional
    public void unpublishPublicProfile(UUID providerId, String sourceSystem, String reason) {
        require(providerId != null, "public profile source reference is required");
        require(StringUtils.hasText(sourceSystem), "public profile source system is required");
        profiles.findByProviderId(providerId)
                .filter(profile -> sourceSystem.equalsIgnoreCase(profile.getSourceSystem()))
                .ifPresent(profile -> {
                    profile.markUnpublished(OffsetDateTime.now());
                    profiles.save(profile);
                });
    }

    @Transactional
    public UUID upsertPublishedMedia(UUID providerId, ProviderDocumentType documentType, String originalFilename, String contentType, byte[] bytes) {
        require(providerId != null, "providerId is required");
        require(documentType != null, "documentType is required");
        require(bytes != null && bytes.length > 0, "media bytes are required");
        String fileName = StringUtils.hasText(originalFilename) ? originalFilename.trim() : documentType.name().toLowerCase(Locale.ROOT);
        String mediaType = StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream";
        ProviderDocumentEntity document = documents.findFirstByProviderIdAndDocumentTypeOrderByUploadedAtDesc(providerId, documentType)
                .orElseGet(() -> new ProviderDocumentEntity(providerId, documentType, fileName, mediaType, bytes.length, storageService.buildDocumentStorageKey(providerId, fileName)));
        String storageKey = StringUtils.hasText(document.getStorageKey())
                ? document.getStorageKey()
                : storageService.buildDocumentStorageKey(providerId, fileName);
        storageService.putObject(storageKey, mediaType, bytes);
        document.update(fileName, mediaType, bytes.length, storageKey);
        ProviderDocumentEntity saved = documents.save(document);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public boolean isSlugReserved(String slug, UUID currentProviderId) {
        String cleanSlug = cleanSlug(slug);
        if (!StringUtils.hasText(cleanSlug)) {
            return false;
        }
        boolean profileConflict = profiles.findByCanonicalSlug(cleanSlug)
                .filter(profile -> currentProviderId == null || !currentProviderId.equals(profile.getProviderId()))
                .isPresent();
        boolean aliasConflict = slugs.findFirstBySlug(cleanSlug)
                .filter(alias -> currentProviderId == null || !currentProviderId.equals(alias.getProviderId()))
                .isPresent();
        return profileConflict || aliasConflict;
    }

    @Transactional(readOnly = true)
    public Page<PublicProviderProfileSummaryRecord> listProfiles(PublicProviderSearchCriteria criteria, int page, int size) {
        if (hasDistanceFilter(criteria)) {
            List<PublicProviderProfileSummaryRecord> filtered = listAllProfiles(criteria);
            int safePage = Math.max(page, 0);
            int safeSize = Math.min(Math.max(size, 1), 48);
            int fromIndex = Math.min(safePage * safeSize, filtered.size());
            int toIndex = Math.min(fromIndex + safeSize, filtered.size());
            return new org.springframework.data.domain.PageImpl<>(
                    filtered.subList(fromIndex, toIndex),
                    PageRequest.of(safePage, safeSize),
                    filtered.size()
            );
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 48), Sort.by(Sort.Direction.ASC, "displayName"));
        Page<DiscoverPublicProviderProfileEntity> result = profiles.findAll(profileSpecification(criteria), pageable);
        Map<UUID, ProviderDocumentEntity> documentMap = loadDocuments(result.getContent());
        return result.map(entity -> toSummary(entity, documentMap, null));
    }

    @Transactional(readOnly = true)
    public List<PublicProviderProfileSummaryRecord> listAllProfiles(PublicProviderSearchCriteria criteria) {
        Pageable pageable = PageRequest.of(0, 512, Sort.by(Sort.Direction.ASC, "displayName"));
        List<DiscoverPublicProviderProfileEntity> content = profiles.findAll(profileSpecification(criteria), pageable).getContent();
        Map<UUID, ProviderDocumentEntity> documentMap = loadDocuments(content);
        if (hasDistanceFilter(criteria)) {
            return content.stream()
                    .map(entity -> new java.util.AbstractMap.SimpleEntry<>(entity, distanceKm(entity, criteria.latitude(), criteria.longitude())))
                    .filter(entry -> entry.getValue() != null)
                    .filter(entry -> criteria.radiusKm() == null || entry.getValue().compareTo(BigDecimal.valueOf(criteria.radiusKm())) <= 0)
                    .sorted(Comparator
                            .comparing((java.util.AbstractMap.SimpleEntry<DiscoverPublicProviderProfileEntity, BigDecimal> entry) -> entry.getValue())
                            .thenComparing(entry -> normalize(entry.getKey().getDisplayName())))
                    .map(entry -> toSummary(entry.getKey(), documentMap, entry.getValue()))
                    .toList();
        }
        return content.stream()
                .map(entity -> toSummary(entity, documentMap, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PublicProviderProfileDetailRecord> findBySlug(String slug) {
        String cleanSlug = cleanSlug(slug);
        if (!StringUtils.hasText(cleanSlug)) {
            return Optional.empty();
        }
        Optional<DiscoverPublicProviderProfileSlugEntity> alias = slugs.findFirstBySlug(cleanSlug);
        if (alias.isPresent()) {
            return detailForSlugAlias(alias.get());
        }
        return profiles.findByCanonicalSlug(cleanSlug)
                .filter(profile -> "PUBLISHED".equalsIgnoreCase(profile.getPublicationStatus()))
                .flatMap(profile -> versions.findFirstByProviderIdOrderByVersionNumberDesc(profile.getProviderId())
                        .map(version -> toDetail(profile, version, cleanSlug, readSnapshot(version.getSnapshotJson()))));
    }

    @Transactional(readOnly = true)
    public Optional<PublicProviderProfileDetailRecord> findByProviderId(UUID providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return profiles.findByProviderId(providerId)
                .filter(profile -> "PUBLISHED".equalsIgnoreCase(profile.getPublicationStatus()))
                .flatMap(profile -> versions.findFirstByProviderIdOrderByVersionNumberDesc(providerId)
                        .map(version -> toDetail(profile, version, profile.getCanonicalSlug(), readSnapshot(version.getSnapshotJson()))));
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileLifecycleRecord> findLifecycleByProviderId(UUID providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return profiles.findByProviderId(providerId)
                .flatMap(profile -> versions.findFirstByProviderIdOrderByVersionNumberDesc(providerId)
                        .map(version -> toLifecycleRecord(profile, version)));
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileLifecycleRecord> findLifecycleBySourceReference(String sourceSystem, String sourceEntityReference) {
        if (!StringUtils.hasText(sourceSystem) || !StringUtils.hasText(sourceEntityReference)) {
            return Optional.empty();
        }
        return profiles.findFirstBySourceSystemIgnoreCaseAndSourceEntityReference(sourceSystem.trim(), sourceEntityReference.trim())
                .flatMap(profile -> versions.findFirstByProviderIdOrderByVersionNumberDesc(profile.getProviderId())
                        .map(version -> toLifecycleRecord(profile, version)));
    }

    @Transactional(readOnly = true)
    public List<PublicProfileLifecycleRecord> listLifecycleProfiles(ProviderType providerType, String q, String city) {
        String normalizedQuery = normalize(q);
        String normalizedCity = normalize(city);
        return profiles.findAll().stream()
                .filter(profile -> providerType == null || profile.getProviderType() == providerType)
                .filter(profile -> StringUtils.hasText(normalizedCity) ? containsIgnoreCase(profile.getCity(), normalizedCity) : true)
                .filter(profile -> StringUtils.hasText(normalizedQuery)
                        ? containsIgnoreCase(profile.getDisplayName(), normalizedQuery)
                        || containsIgnoreCase(profile.getSummary(), normalizedQuery)
                        || containsIgnoreCase(profile.getPrimarySpeciality(), normalizedQuery)
                        || containsIgnoreCase(profile.getCity(), normalizedQuery)
                        || containsIgnoreCase(profile.getArea(), normalizedQuery)
                        || containsIgnoreCase(profile.getSourceEntityReference(), normalizedQuery)
                        : true)
                .sorted(Comparator.comparing(DiscoverPublicProviderProfileEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(profile -> versions.findFirstByProviderIdOrderByVersionNumberDesc(profile.getProviderId())
                        .map(version -> toLifecycleRecord(profile, version))
                        .orElseGet(() -> new PublicProfileLifecycleRecord(
                                profile.getProviderId(),
                                profile.getProviderType(),
                                profile.getSourceSystem(),
                                profile.getSourceEntityReference(),
                                profile.getSourceRevision(),
                                profile.getSourceUpdatedAt(),
                                profile.getCanonicalSlug(),
                                profile.getDisplayName(),
                                profile.getCity(),
                                profile.getArea(),
                                profile.getBookingMode(),
                                profile.getPublicationStatus(),
                                profile.getProjectedAt(),
                                profile.getPublishedAt(),
                                profile.getConnectionRevision(),
                                publicPath(profile.getProviderType(), profile.getCanonicalSlug())
                        )))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicationReadinessRecord publicationReadiness(UUID providerId) {
        if (providerId == null) {
            return new PublicationReadinessRecord(false, 0, List.of("MISSING_PROVIDER"), List.of(), List.of(), "UNKNOWN", 0L, null);
        }
        DiscoverPublicProviderProfileEntity profile = profiles.findByProviderId(providerId).orElse(null);
        if (profile == null) {
            return new PublicationReadinessRecord(false, 0, List.of("PROFILE_NOT_FOUND"), List.of(), List.of(), "UNKNOWN", 0L, null);
        }
        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!StringUtils.hasText(profile.getDisplayName())) {
            missing.add("DISPLAY_NAME");
        }
        if (!StringUtils.hasText(profile.getCity()) || !StringUtils.hasText(profile.getArea()) && profile.getProviderType() != ProviderType.INDIVIDUAL_DOCTOR) {
            missing.add("PUBLIC_LOCATION");
        }
        if (!StringUtils.hasText(profile.getContactPhone())) {
            missing.add("PUBLIC_CONTACT");
        }
        if (!StringUtils.hasText(profile.getSummary())) {
            missing.add("ABOUT_SECTION");
        }
        if (profile.getCoverImageDocumentId() == null) {
            missing.add("COVER_PHOTO");
        }
        if (profile.getLogoDocumentId() == null && profile.getDoctorPhotoDocumentId() == null) {
            missing.add("LOGO");
        }
        if (!StringUtils.hasText(profile.getServices())) {
            missing.add("AT_LEAST_ONE_SERVICE");
        }
        if (!StringUtils.hasText(profile.getSpecialities())) {
            missing.add("AT_LEAST_ONE_SPECIALITY");
        }
        if (profile.getGalleryCount() <= 0) {
            warnings.add("GALLERY_RECOMMENDED");
        }
        if (profile.getBookingMode() == null || !StringUtils.hasText(profile.getBookingMode())) {
            missing.add("OPENING_HOURS");
        }
        if (profile.getDoctorPhotoDocumentId() == null && profile.getCoverImageDocumentId() == null && profile.getLogoDocumentId() == null) {
            warnings.add("BRANDING_RECOMMENDED");
        }
        if (!StringUtils.hasText(profile.getSourceSystem())) {
            invalid.add("SOURCE_SYSTEM");
        }
        if (!StringUtils.hasText(profile.getCanonicalSlug())) {
            warnings.add("SLUG_PENDING");
        }
        if (!"PUBLISHED".equalsIgnoreCase(profile.getPublicationStatus())) {
            warnings.add("NOT_YET_PUBLISHED");
        }
        boolean ready = missing.isEmpty() && invalid.isEmpty();
        int completenessPercentage = ready ? 100 : Math.max(0, 100 - (missing.size() * 12));
        return new PublicationReadinessRecord(ready, completenessPercentage, missing, invalid, warnings, profile.getPublicationStatus(), profile.getSourceRevision(), profile.getSourceUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<PublicSpecialitySummaryRecord> listSpecialities(String q, String city) {
        String normalizedQuery = normalize(q);
        String normalizedCity = normalize(city);
        Map<String, SpecialityBucket> buckets = new LinkedHashMap<>();

        for (DiscoverPublicProviderProfileEntity profile : profiles.findAll()) {
            if (!"PUBLISHED".equalsIgnoreCase(profile.getPublicationStatus())) {
                continue;
            }
            if (StringUtils.hasText(normalizedCity) && !containsIgnoreCase(profile.getCity(), normalizedCity)) {
                continue;
            }
            for (String speciality : split(profile.getSpecialities())) {
                if (StringUtils.hasText(normalizedQuery) && !containsIgnoreCase(speciality, normalizedQuery)) {
                    continue;
                }
                buckets.computeIfAbsent(normalize(speciality), key -> new SpecialityBucket(speciality))
                        .add(profile.getProviderType(), profile.getProviderId());
            }
        }

        return buckets.values().stream()
                .sorted(Comparator.comparing(SpecialityBucket::label, String.CASE_INSENSITIVE_ORDER))
                .map(bucket -> new PublicSpecialitySummaryRecord(
                        bucket.label(),
                        slugify(bucket.label()),
                        bucket.doctorCount(),
                        bucket.clinicCount(),
                        bucket.hospitalCount()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicProviderProfileSummaryRecord> summariesByType(ProviderType providerType, String q, String city, String area, String speciality, String service) {
        return listProfiles(new PublicProviderSearchCriteria(providerType, q, city, area, speciality, service, null, null, null), 0, 512).getContent();
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveDocumentUrl(UUID documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        return documents.findById(documentId)
                .filter(document -> StringUtils.hasText(document.getStorageKey()))
                .map(document -> storageService.generatePresignedDownloadUrl(document.getStorageKey(), MEDIA_URL_TTL));
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileMediaContent> loadPublishedDoctorMedia(String slug, DoctorPublicMediaAsset asset, Integer galleryIndex) {
        return resolvePublishedProfile(slug)
                .filter(profile -> profile.entity().getProviderType() == ProviderType.INDIVIDUAL_DOCTOR)
                .flatMap(profile -> resolveDoctorDocumentId(profile, asset, galleryIndex))
                .flatMap(this::loadMediaContent);
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileMediaContent> loadPublishedProviderLogo(String slug, ProviderType providerType) {
        return loadPublishedProviderMedia(slug, providerType, ProviderPublicMediaAsset.LOGO, null);
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileMediaContent> loadPublishedProviderMedia(String slug, ProviderType providerType, ProviderPublicMediaAsset asset, Integer galleryIndex) {
        return resolvePublishedProfile(slug)
                .filter(profile -> profile.entity().getProviderType() == providerType)
                .flatMap(profile -> resolveProviderDocumentId(profile, asset, galleryIndex))
                .flatMap(this::loadMediaContent);
    }

    public enum DoctorPublicMediaAsset {
        PHOTO,
        COVER,
        GALLERY
    }

    public enum ProviderPublicMediaAsset {
        LOGO,
        COVER,
        GALLERY
    }

    private Optional<PublicProviderProfileDetailRecord> detailForSlugAlias(DiscoverPublicProviderProfileSlugEntity alias) {
        DiscoverPublicProviderProfileEntity profile = profiles.findByProviderId(alias.getProviderId()).orElse(null);
        DiscoverPublicProviderProfileVersionEntity version = versions.findByProviderIdAndVersionNumber(alias.getProviderId(), alias.getVersionNumber()).orElse(null);
        if (profile == null || version == null || !"PUBLISHED".equalsIgnoreCase(profile.getPublicationStatus())) {
            return Optional.empty();
        }
        PublicProviderProfileSnapshot snapshot = readSnapshot(version.getSnapshotJson());
        return Optional.of(toDetail(profile, version, alias.getSlug(), snapshot));
    }

    private Optional<ResolvedPublishedProfile> resolvePublishedProfile(String slug) {
        String cleanSlug = cleanSlug(slug);
        if (!StringUtils.hasText(cleanSlug)) {
            return Optional.empty();
        }
        Optional<DiscoverPublicProviderProfileSlugEntity> alias = slugs.findFirstBySlug(cleanSlug);
        if (alias.isPresent()) {
            DiscoverPublicProviderProfileEntity profile = profiles.findByProviderId(alias.get().getProviderId()).orElse(null);
            DiscoverPublicProviderProfileVersionEntity version = versions.findByProviderIdAndVersionNumber(alias.get().getProviderId(), alias.get().getVersionNumber()).orElse(null);
            if (profile == null || version == null || !"PUBLISHED".equalsIgnoreCase(profile.getPublicationStatus())) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedPublishedProfile(profile, readSnapshot(version.getSnapshotJson())));
        }
        DiscoverPublicProviderProfileEntity profile = profiles.findByCanonicalSlug(cleanSlug).orElse(null);
        if (profile == null) {
            return Optional.empty();
        }
        DiscoverPublicProviderProfileVersionEntity version = versions.findFirstByProviderIdOrderByVersionNumberDesc(profile.getProviderId()).orElse(null);
        if (version == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedPublishedProfile(profile, readSnapshot(version.getSnapshotJson())));
    }

    private Optional<UUID> resolveDoctorDocumentId(ResolvedPublishedProfile profile, DoctorPublicMediaAsset asset, Integer galleryIndex) {
        return switch (asset) {
            case PHOTO -> Optional.ofNullable(profile.entity().getDoctorPhotoDocumentId());
            case COVER -> Optional.ofNullable(profile.entity().getCoverImageDocumentId());
            case GALLERY -> resolveGalleryDocumentId(profile.snapshot(), galleryIndex);
        };
    }

    private Optional<UUID> resolveProviderDocumentId(ResolvedPublishedProfile profile, ProviderPublicMediaAsset asset, Integer galleryIndex) {
        return switch (asset) {
            case LOGO -> Optional.ofNullable(profile.entity().getLogoDocumentId());
            case COVER -> Optional.ofNullable(profile.entity().getCoverImageDocumentId());
            case GALLERY -> resolveGalleryDocumentId(profile.snapshot(), galleryIndex);
        };
    }

    private Optional<UUID> resolveGalleryDocumentId(PublicProviderProfileSnapshot snapshot, Integer galleryIndex) {
        if (galleryIndex == null || galleryIndex < 0 || galleryIndex >= snapshot.gallery().size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.gallery().get(galleryIndex).documentId());
    }

    private Optional<PublicProfileMediaContent> loadMediaContent(UUID documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        return documents.findById(documentId)
                .filter(document -> StringUtils.hasText(document.getStorageKey()))
                .map(document -> new PublicProfileMediaContent(
                        document.getContentType(),
                        document.getOriginalFilename(),
                        storageService.getObjectBytes(document.getStorageKey())
                ));
    }

    private PublicProviderProfileSummaryRecord toSummary(
            DiscoverPublicProviderProfileEntity entity,
            Map<UUID, ProviderDocumentEntity> documentMap,
            BigDecimal distanceKm
    ) {
        String imageUrl = resolveMediaUrl(entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR ? entity.getDoctorPhotoDocumentId() : entity.getLogoDocumentId(), documentMap);
        String coverUrl = resolveMediaUrl(entity.getCoverImageDocumentId(), documentMap);
        return new PublicProviderProfileSummaryRecord(
                entity.getProviderId(),
                entity.getProviderType(),
                entity.getCanonicalSlug(),
                publicPath(entity.getProviderType(), entity.getCanonicalSlug()),
                entity.getDisplayName(),
                firstNonBlank(entity.getPrimarySpeciality(), entity.getTagline(), entity.getSummary()),
                entity.getSummary(),
                entity.getPrimarySpeciality(),
                entity.getCity(),
                entity.getArea(),
                imageUrl,
                coverUrl,
                entity.getDoctorCount(),
                entity.getServiceCount(),
                entity.getDepartmentCount(),
                entity.getGalleryCount(),
                entity.getContactPhone(),
                entity.getBookingMode(),
                entity.isEmergencyAvailable(),
                tags(entity),
                distanceKm
        );
    }

    private PublicProviderProfileDetailRecord toDetail(
            DiscoverPublicProviderProfileEntity entity,
            DiscoverPublicProviderProfileVersionEntity version,
            String requestedSlug,
            PublicProviderProfileSnapshot snapshot
    ) {
        Map<UUID, ProviderDocumentEntity> documentMap = loadDocuments(snapshot);
        String imageUrl = resolveMediaUrl(entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR ? entity.getDoctorPhotoDocumentId() : entity.getLogoDocumentId(), documentMap);
        String coverUrl = resolveMediaUrl(entity.getCoverImageDocumentId(), documentMap);
        String logoUrl = resolveMediaUrl(entity.getLogoDocumentId(), documentMap);
        List<String> galleryImageUrls = snapshot.gallery().stream()
                .map(image -> resolveMediaUrl(image.documentId(), documentMap))
                .filter(url -> url != null && !url.isBlank())
                .toList();
        return new PublicProviderProfileDetailRecord(
                entity.getProviderId(),
                entity.getProviderType(),
                snapshot.referenceNumber(),
                entity.getCanonicalSlug(),
                publicPath(entity.getProviderType(), entity.getCanonicalSlug()),
                entity.getDisplayName(),
                entity.getLegalName(),
                firstNonBlank(entity.getPrimarySpeciality(), entity.getTagline(), entity.getSummary()),
                entity.getSummary(),
                snapshot.biography(),
                snapshot.qualification(),
                snapshot.medicalCouncil(),
                snapshot.yearsOfExperience(),
                snapshot.consultationFee(),
                snapshot.appointmentDurationMinutes(),
                snapshot.onlineConsultation(),
                snapshot.languages(),
                snapshot.specialities(),
                snapshot.subSpecialities(),
                snapshot.services(),
                snapshot.departments(),
                snapshot.facilities(),
                snapshot.consultationModes(),
                snapshot.locations(),
                snapshot.gallery(),
                galleryImageUrls,
                imageUrl,
                coverUrl,
                logoUrl,
                entity.getContactPhone(),
                entity.getContactEmail(),
                entity.getWebsite(),
                entity.getCity(),
                entity.getArea(),
                entity.getState(),
                entity.getCountry(),
                entity.getPrimarySpeciality(),
                entity.getOwnership(),
                entity.getHospitalType(),
                entity.getMedicalDirector(),
                entity.getBeds(),
                entity.isEmergencyAvailable(),
                entity.getBookingMode(),
                true,
                version.getPublishedAt(),
                version.getVersionNumber(),
                requestedSlug,
                entity.getCanonicalSlug().equalsIgnoreCase(requestedSlug) ? null : requestedSlug,
                entity.getCanonicalSlug().equalsIgnoreCase(requestedSlug)
        );
    }

    private PublicProviderProfileSnapshot buildSnapshot(ProviderApplicationEntity application, String canonicalSlug, OffsetDateTime publishedAt, int versionNumber) {
        List<ProviderLocationEntity> locationRecords = locations.findByProviderIdOrderByLabelAsc(application.getId());
        List<ProviderServiceEntity> serviceRecords = services.findByProviderIdOrderByLabelAsc(application.getId());
        List<ProviderDocumentEntity> documentRecords = documents.findByProviderIdOrderByUploadedAtDesc(application.getId());
        List<PublicProviderLocationSnapshot> publicLocations = locationRecords.stream()
                .map(item -> new PublicProviderLocationSnapshot(
                        item.getLabel(),
                        item.getAddress(),
                        item.getCity(),
                        item.getState(),
                        item.getCountry(),
                        item.getPinCode(),
                        item.getWorkingHours(),
                        item.isParkingAvailable(),
                        item.isAccessibilityAvailable(),
                        item.getLatitude(),
                        item.getLongitude()))
                .toList();
        List<PublicProviderGalleryImageSnapshot> gallery = documentRecords.stream()
                .filter(record -> record.getDocumentType() == ProviderDocumentType.GALLERY_IMAGE)
                .map(record -> new PublicProviderGalleryImageSnapshot(record.getId(), record.getOriginalFilename()))
                .toList();
        Map<UUID, ProviderDocumentEntity> documentMap = documentRecords.stream()
                .collect(Collectors.toMap(ProviderDocumentEntity::getId, Function.identity(), (left, right) -> left));
        List<String> galleryImageUrls = gallery.stream()
                .map(image -> resolveMediaUrl(image.documentId(), documentMap))
                .filter(url -> url != null && !url.isBlank())
                .toList();

        List<String> specialities = split(application.getSpecialities());
        List<String> subSpecialities = split(application.getSubSpecialities());
        List<String> languages = split(application.getLanguages());
        List<String> departments = split(application.getDepartments());
        List<String> facilities = split(application.getFacilities());
        List<String> enabledServices = serviceRecords.stream().filter(ProviderServiceEntity::isEnabled).map(ProviderServiceEntity::getLabel).toList();
        List<String> consultationModes = consultationModes(application);
        ProviderLocationEntity firstLocation = locationRecords.isEmpty() ? null : locationRecords.get(0);
        String displayName = firstNonBlank(application.getDisplayName(), application.getLegalName(), application.getEmail());
        String legalName = firstNonBlank(application.getLegalName(), displayName);
        String summary = firstNonBlank(application.getBiography(), application.getTagline());
        return new PublicProviderProfileSnapshot(
                application.getId(),
                application.getProviderType(),
                SOURCE_SYSTEM_ONBOARDING,
                application.getReferenceNumber(),
                displayName,
                legalName,
                canonicalSlug,
                summary,
                application.getBiography(),
                application.getQualification(),
                application.getMedicalCouncil(),
                application.getYearsOfExperience(),
                application.getConsultationFee(),
                application.getAppointmentDurationMinutes(),
                application.isOnlineConsultation(),
                languages,
                specialities,
                subSpecialities,
                enabledServices,
                departments,
                facilities,
                consultationModes,
                publicLocations,
                gallery,
                galleryImageUrls,
                application.getLogoDocumentId(),
                application.getCoverImageDocumentId(),
                application.getDoctorPhotoDocumentId(),
                application.getPhone(),
                application.getEmail(),
                application.getWebsite(),
                firstLocation == null ? null : firstLocation.getCity(),
                firstLocation == null ? null : firstLocation.getLabel(),
                firstLocation == null ? null : firstLocation.getState(),
                firstLocation == null ? null : firstLocation.getCountry(),
                first(specialities),
                application.getTagline(),
                application.getOwnership(),
                application.getHospitalType(),
                application.getMedicalDirector(),
                application.getBeds(),
                application.isEmergencyAvailable(),
                application.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR ? 1 : 0,
                enabledServices.size(),
                departments.size(),
                gallery.size(),
                BOOKING_MODE_ONLINE,
                true,
                publishedAt,
                versionNumber,
                publicPath(application.getProviderType(), canonicalSlug)
        );
    }

    private void upsertAggregate(
            PublicProviderProfileSnapshot snapshot,
            UUID providerId,
            UUID latestVersionId,
            int latestVersionNumber,
            OffsetDateTime projectedAt,
            String publicationStatus,
            String sourceSystem,
            String sourceEntityReference,
            long sourceRevision,
            OffsetDateTime sourceUpdatedAt,
            long connectionRevision
    ) {
        DiscoverPublicProviderProfileEntity entity = profiles.findByProviderId(providerId).orElseGet(() -> DiscoverPublicProviderProfileEntity.create(
                providerId,
                snapshot.providerType(),
                sourceSystem,
                firstNonBlank(sourceEntityReference, snapshot.providerId().toString()),
                sourceRevision,
                sourceUpdatedAt == null ? projectedAt : sourceUpdatedAt,
                snapshot.canonicalSlug(),
                latestVersionId,
                latestVersionNumber,
                snapshot.displayName(),
                snapshot.legalName(),
                snapshot.summary(),
                snapshot.primarySpeciality(),
                join(snapshot.specialities()),
                join(snapshot.subSpecialities()),
                join(snapshot.services()),
                join(snapshot.departments()),
                join(snapshot.facilities()),
                join(snapshot.languages()),
                join(snapshot.consultationModes()),
                snapshot.logoDocumentId(),
                snapshot.coverImageDocumentId(),
                snapshot.doctorPhotoDocumentId(),
                snapshot.contactPhone(),
                snapshot.contactEmail(),
                snapshot.website(),
                snapshot.city(),
                snapshot.area(),
                snapshot.state(),
                snapshot.country(),
                snapshot.tagline(),
                snapshot.ownership(),
                snapshot.hospitalType(),
                snapshot.medicalDirector(),
                snapshot.beds(),
                snapshot.emergencyAvailable(),
                snapshot.doctorCount(),
                snapshot.serviceCount(),
                snapshot.departmentCount(),
                snapshot.galleryCount(),
                snapshot.bookingMode(),
                projectedAt
        ));
        entity.update(
                snapshot.canonicalSlug(),
                latestVersionId,
                latestVersionNumber,
                snapshot.displayName(),
                snapshot.legalName(),
                snapshot.summary(),
                snapshot.primarySpeciality(),
                join(snapshot.specialities()),
                join(snapshot.subSpecialities()),
                join(snapshot.services()),
                join(snapshot.departments()),
                join(snapshot.facilities()),
                join(snapshot.languages()),
                join(snapshot.consultationModes()),
                snapshot.logoDocumentId(),
                snapshot.coverImageDocumentId(),
                snapshot.doctorPhotoDocumentId(),
                snapshot.contactPhone(),
                snapshot.contactEmail(),
                snapshot.website(),
                snapshot.city(),
                snapshot.area(),
                snapshot.state(),
                snapshot.country(),
                snapshot.tagline(),
                snapshot.ownership(),
                snapshot.hospitalType(),
                snapshot.medicalDirector(),
                snapshot.beds(),
                snapshot.emergencyAvailable(),
                snapshot.doctorCount(),
                snapshot.serviceCount(),
                snapshot.departmentCount(),
                snapshot.galleryCount(),
                snapshot.bookingMode(),
                projectedAt
        );
        entity.applyLifecycleMetadata(
                sourceSystem,
                firstNonBlank(sourceEntityReference, snapshot.providerId().toString()),
                sourceRevision,
                sourceUpdatedAt == null ? projectedAt : sourceUpdatedAt,
                projectedAt,
                connectionRevision,
                publicationStatus == null ? "PUBLISHED" : publicationStatus
        );
        profiles.save(entity);
    }

    private UUID resolveEffectiveProviderId(PublicProviderProfileSnapshot snapshot) {
        UUID requestedProviderId = snapshot.providerId();
        if (profiles.findByProviderId(requestedProviderId).isPresent()) {
            return requestedProviderId;
        }
        return profiles.findByCanonicalSlug(snapshot.canonicalSlug())
                .filter(existing -> canAdoptExistingProfile(existing, snapshot))
                .map(DiscoverPublicProviderProfileEntity::getProviderId)
                .orElse(requestedProviderId);
    }

    private boolean canAdoptExistingProfile(DiscoverPublicProviderProfileEntity existing, PublicProviderProfileSnapshot snapshot) {
        if (existing == null || snapshot == null) {
            return false;
        }
        if (!StringUtils.hasText(existing.getCanonicalSlug()) || !StringUtils.hasText(snapshot.canonicalSlug())) {
            return false;
        }
        if (!existing.getCanonicalSlug().equalsIgnoreCase(snapshot.canonicalSlug())) {
            return false;
        }
        return existing.getProviderType() == snapshot.providerType();
    }

    private void upsertSlugAlias(UUID providerId, UUID versionId, int versionNumber, String slug, OffsetDateTime publishedAt) {
        DiscoverPublicProviderProfileSlugEntity alias = slugs.findFirstBySlug(slug).orElse(null);
        if (alias == null) {
            slugs.findFirstByProviderIdAndActiveTrueOrderByUpdatedAtDesc(providerId)
                    .filter(current -> !current.getSlug().equalsIgnoreCase(slug))
                    .ifPresent(current -> {
                        current.deactivate(publishedAt);
                        slugs.save(current);
                    });
            slugs.save(DiscoverPublicProviderProfileSlugEntity.create(providerId, versionId, slug, versionNumber, true, publishedAt));
            return;
        }
        if (!alias.getProviderId().equals(providerId)) {
            throw new ProviderOwnershipConflictException(
                    "public_profile_slug_conflict",
                    "The requested public profile URL is already used by another provider."
            );
        }
        alias.activate(versionId, versionNumber, publishedAt);
        slugs.save(alias);
        slugs.findFirstByProviderIdAndActiveTrueOrderByUpdatedAtDesc(providerId)
                .filter(current -> !current.getSlug().equalsIgnoreCase(slug))
                .ifPresent(current -> {
                    current.deactivate(publishedAt);
                    slugs.save(current);
                });
    }

    private Map<UUID, ProviderDocumentEntity> loadDocuments(List<DiscoverPublicProviderProfileEntity> entities) {
        List<UUID> ids = new ArrayList<>();
        for (DiscoverPublicProviderProfileEntity entity : entities) {
            if (entity.getLogoDocumentId() != null) {
                ids.add(entity.getLogoDocumentId());
            }
            if (entity.getCoverImageDocumentId() != null) {
                ids.add(entity.getCoverImageDocumentId());
            }
            if (entity.getDoctorPhotoDocumentId() != null) {
                ids.add(entity.getDoctorPhotoDocumentId());
            }
        }
        return documents.findAllById(ids).stream().collect(Collectors.toMap(ProviderDocumentEntity::getId, Function.identity(), (left, right) -> left));
    }

    private Map<UUID, ProviderDocumentEntity> loadDocuments(PublicProviderProfileSnapshot snapshot) {
        List<UUID> ids = new ArrayList<>();
        if (snapshot.logoDocumentId() != null) ids.add(snapshot.logoDocumentId());
        if (snapshot.coverImageDocumentId() != null) ids.add(snapshot.coverImageDocumentId());
        if (snapshot.doctorPhotoDocumentId() != null) ids.add(snapshot.doctorPhotoDocumentId());
        for (PublicProviderGalleryImageSnapshot image : snapshot.gallery()) {
            if (image.documentId() != null) {
                ids.add(image.documentId());
            }
        }
        return documents.findAllById(ids).stream().collect(Collectors.toMap(ProviderDocumentEntity::getId, Function.identity(), (left, right) -> left));
    }

    private String resolveMediaUrl(UUID documentId, Map<UUID, ProviderDocumentEntity> documentMap) {
        if (documentId == null) {
            return null;
        }
        ProviderDocumentEntity document = documentMap.get(documentId);
        if (document == null || !StringUtils.hasText(document.getStorageKey())) {
            return null;
        }
        return storageService.generatePresignedDownloadUrl(document.getStorageKey(), MEDIA_URL_TTL);
    }

    private Specification<DiscoverPublicProviderProfileEntity> profileSpecification(PublicProviderSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                predicates.add(cb.equal(root.get("publicationStatus"), "PUBLISHED"));
                if (criteria.providerType() != null) {
                    predicates.add(cb.equal(root.get("providerType"), criteria.providerType()));
                }
                if (StringUtils.hasText(criteria.city())) {
                    predicates.add(cb.like(cb.lower(root.get("city")), like(criteria.city())));
                }
                if (StringUtils.hasText(criteria.area())) {
                    predicates.add(cb.like(cb.lower(root.get("area")), like(criteria.area())));
                }
                if (StringUtils.hasText(criteria.speciality())) {
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("primarySpeciality")), like(criteria.speciality())),
                            cb.like(cb.lower(root.get("specialities")), like(criteria.speciality()))
                    ));
                }
                if (StringUtils.hasText(criteria.service())) {
                    predicates.add(cb.like(cb.lower(root.get("services")), like(criteria.service())));
                }
                if (StringUtils.hasText(criteria.query())) {
                    String like = like(criteria.query());
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("displayName")), like),
                            cb.like(cb.lower(root.get("summary")), like),
                            cb.like(cb.lower(root.get("primarySpeciality")), like),
                            cb.like(cb.lower(root.get("specialities")), like),
                            cb.like(cb.lower(root.get("services")), like),
                            cb.like(cb.lower(root.get("city")), like),
                            cb.like(cb.lower(root.get("area")), like),
                            cb.like(cb.lower(root.get("state")), like),
                            cb.like(cb.lower(root.get("hospitalType")), like),
                            cb.like(cb.lower(root.get("medicalDirector")), like)
                    ));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private boolean hasDistanceFilter(PublicProviderSearchCriteria criteria) {
        return criteria != null
                && criteria.latitude() != null
                && criteria.longitude() != null
                && criteria.radiusKm() != null
                && criteria.radiusKm() > 0;
    }

    private BigDecimal distanceKm(DiscoverPublicProviderProfileEntity entity, BigDecimal latitude, BigDecimal longitude) {
        Double sourceLatitude = entityLatitude(entity);
        Double sourceLongitude = entityLongitude(entity);
        if (sourceLatitude == null || sourceLongitude == null || latitude == null || longitude == null) {
            return null;
        }
        double earthRadiusKm = 6371.0088d;
        double dLat = Math.toRadians(latitude.doubleValue() - sourceLatitude);
        double dLon = Math.toRadians(longitude.doubleValue() - sourceLongitude);
        double lat1 = Math.toRadians(sourceLatitude);
        double lat2 = Math.toRadians(latitude.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(earthRadiusKm * c).setScale(1, RoundingMode.HALF_UP);
    }

    private Double entityLatitude(DiscoverPublicProviderProfileEntity entity) {
        return firstLocationCoordinate(entity.getProviderId(), true);
    }

    private Double entityLongitude(DiscoverPublicProviderProfileEntity entity) {
        return firstLocationCoordinate(entity.getProviderId(), false);
    }

    private Double firstLocationCoordinate(UUID providerId, boolean latitude) {
        if (providerId == null) {
            return null;
        }
        return locations.findByProviderIdOrderByLabelAsc(providerId).stream()
                .map(location -> latitude ? coordinate(location.getLatitude()) : coordinate(location.getLongitude()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Double coordinate(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String like(String value) {
        return "%" + normalize(value) + "%";
    }

    private PublicProviderProfileSnapshot readSnapshot(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, PublicProviderProfileSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read public profile snapshot", ex);
        }
    }

    private String snapshotJson(PublicProviderProfileSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize public profile snapshot", ex);
        }
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash public profile snapshot", ex);
        }
    }

    private String publicPath(ProviderType providerType, String slug) {
        return "/discover/" + switch (providerType) {
            case INDIVIDUAL_DOCTOR -> "doctors";
            case CLINIC -> "clinics";
            case HOSPITAL -> "hospitals";
        } + "/" + slug;
    }

    private PublicProfileLifecycleRecord toLifecycleRecord(
            DiscoverPublicProviderProfileEntity profile,
            DiscoverPublicProviderProfileVersionEntity version
    ) {
        return new PublicProfileLifecycleRecord(
                profile.getProviderId(),
                profile.getProviderType(),
                profile.getSourceSystem(),
                profile.getSourceEntityReference(),
                profile.getSourceRevision(),
                profile.getSourceUpdatedAt(),
                profile.getCanonicalSlug(),
                profile.getDisplayName(),
                profile.getCity(),
                profile.getArea(),
                profile.getBookingMode(),
                profile.getPublicationStatus(),
                profile.getProjectedAt(),
                version == null ? profile.getPublishedAt() : version.getPublishedAt(),
                profile.getConnectionRevision(),
                publicPath(profile.getProviderType(), profile.getCanonicalSlug())
        );
    }

    private String slugFor(ProviderApplicationEntity application) {
        String base = firstNonBlank(application.getDisplayName(), application.getLegalName(), application.getReferenceNumber());
        if (application.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR && !normalize(base).startsWith("dr")) {
            base = "Dr " + base;
        }
        return slugify(base);
    }

    private String ensureUniqueSlug(String requestedSlug, UUID providerId) {
        String base = StringUtils.hasText(requestedSlug) ? requestedSlug : "provider";
        String candidate = base;
        int suffix = 2;
        while (slugConflicts(candidate, providerId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean slugConflicts(String slug, UUID providerId) {
        if (profiles.findByCanonicalSlug(slug).filter(profile -> !profile.getProviderId().equals(providerId)).isPresent()) {
            return true;
        }
        return slugs.findFirstBySlug(slug).filter(alias -> !alias.getProviderId().equals(providerId)).isPresent();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanSlug(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String slugify(String value) {
        return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private boolean containsIgnoreCase(String text, String query) {
        return StringUtils.hasText(text) && normalize(text).contains(normalize(query));
    }

    private List<String> split(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : raw.split("[,;]")) {
            String trimmed = token == null ? "" : token.trim();
            if (StringUtils.hasText(trimmed)) {
                result.add(trimmed);
            }
        }
        return result.stream().distinct().toList();
    }

    private List<String> consultationModes(ProviderApplicationEntity application) {
        List<String> modes = new ArrayList<>();
        if (application.isOnlineConsultation()) {
            modes.add("Online consultation");
        }
        modes.add("In-person consultation");
        if (application.getAppointmentDurationMinutes() != null) {
            modes.add("Appointment duration " + application.getAppointmentDurationMinutes() + " mins");
        }
        return modes.stream().distinct().toList();
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.joining(", "));
    }

    private List<String> tags(DiscoverPublicProviderProfileEntity entity) {
        List<String> tags = new ArrayList<>();
        if (StringUtils.hasText(entity.getPrimarySpeciality())) {
            tags.add(entity.getPrimarySpeciality());
        }
        if (StringUtils.hasText(entity.getCity())) {
            tags.add(entity.getCity());
        }
        if (entity.isEmergencyAvailable()) {
            tags.add("Emergency");
        }
        if (entity.getDoctorCount() > 0) {
            tags.add(entity.getDoctorCount() + " doctors");
        }
        if (entity.getServiceCount() > 0) {
            tags.add(entity.getServiceCount() + " services");
        }
        return tags;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record ResolvedPublishedProfile(
            DiscoverPublicProviderProfileEntity entity,
            PublicProviderProfileSnapshot snapshot
    ) {
    }

    private static final class SpecialityBucket {
        private final String label;
        private final LinkedHashSet<UUID> doctorProfiles = new LinkedHashSet<>();
        private final LinkedHashSet<UUID> clinicProfiles = new LinkedHashSet<>();
        private final LinkedHashSet<UUID> hospitalProfiles = new LinkedHashSet<>();

        private SpecialityBucket(String label) {
            this.label = label;
        }

        private void add(ProviderType type, UUID providerId) {
            if (type == ProviderType.INDIVIDUAL_DOCTOR) {
                doctorProfiles.add(providerId);
            } else if (type == ProviderType.CLINIC) {
                clinicProfiles.add(providerId);
            } else {
                hospitalProfiles.add(providerId);
            }
        }

        private String label() {
            return label;
        }

        private int doctorCount() {
            return doctorProfiles.size();
        }

        private int clinicCount() {
            return clinicProfiles.size();
        }

        private int hospitalCount() {
            return hospitalProfiles.size();
        }
    }
}
