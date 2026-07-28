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
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderSearchCriteria;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicSpecialitySummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderPublicationRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileSlugEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileSlugRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionRepository;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
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
        String snapshotJson = snapshotJson(snapshot);
        String snapshotHash = digest(snapshotJson);

        Optional<DiscoverPublicProviderProfileVersionEntity> duplicate = versions.findFirstByProviderIdAndSnapshotHashOrderByVersionNumberDesc(application.getId(), snapshotHash);
        if (duplicate.isPresent()) {
            DiscoverPublicProviderProfileVersionEntity version = duplicate.get();
            upsertSlugAlias(application.getId(), version.getId(), version.getVersionNumber(), canonicalSlug, publishedAt);
            upsertAggregate(snapshot, version.getId(), version.getVersionNumber(), publishedAt);
            return new PublicProviderPublicationRecord(
                    application.getId(),
                    application.getProviderType(),
                    canonicalSlug,
                    version.getVersionNumber(),
                    version.getPublishedAt(),
                    publicPath(application.getProviderType(), canonicalSlug)
            );
        }

        DiscoverPublicProviderProfileVersionEntity version = versions.save(DiscoverPublicProviderProfileVersionEntity.create(
                application.getId(),
                nextVersion,
                submission.getVersionNumber(),
                submission.getStatusBefore(),
                submission.getStatusAfter(),
                "VERIFICATION",
                StringUtils.hasText(publicationReason) ? publicationReason : "Published after approval",
                snapshotHash,
                snapshotJson,
                canonicalSlug,
                publishedAt
        ));

        upsertSlugAlias(application.getId(), version.getId(), nextVersion, canonicalSlug, publishedAt);
        upsertAggregate(snapshot, version.getId(), nextVersion, publishedAt);

        return new PublicProviderPublicationRecord(
                application.getId(),
                application.getProviderType(),
                canonicalSlug,
                nextVersion,
                publishedAt,
                publicPath(application.getProviderType(), canonicalSlug)
        );
    }

    @Transactional(readOnly = true)
    public Page<PublicProviderProfileSummaryRecord> listProfiles(PublicProviderSearchCriteria criteria, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 48), Sort.by(Sort.Direction.ASC, "displayName"));
        Page<DiscoverPublicProviderProfileEntity> result = profiles.findAll(profileSpecification(criteria), pageable);
        Map<UUID, ProviderDocumentEntity> documentMap = loadDocuments(result.getContent());
        return result.map(entity -> toSummary(entity, documentMap));
    }

    @Transactional(readOnly = true)
    public List<PublicProviderProfileSummaryRecord> listAllProfiles(PublicProviderSearchCriteria criteria) {
        Pageable pageable = PageRequest.of(0, 512, Sort.by(Sort.Direction.ASC, "displayName"));
        List<DiscoverPublicProviderProfileEntity> content = profiles.findAll(profileSpecification(criteria), pageable).getContent();
        Map<UUID, ProviderDocumentEntity> documentMap = loadDocuments(content);
        return content.stream()
                .map(entity -> toSummary(entity, documentMap))
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
        return profiles.findByCanonicalSlug(cleanSlug).flatMap(profile -> versions.findFirstByProviderIdOrderByVersionNumberDesc(profile.getProviderId())
                .map(version -> toDetail(profile, version, cleanSlug, readSnapshot(version.getSnapshotJson()))));
    }

    @Transactional(readOnly = true)
    public List<PublicSpecialitySummaryRecord> listSpecialities(String q, String city) {
        String normalizedQuery = normalize(q);
        String normalizedCity = normalize(city);
        Map<String, SpecialityBucket> buckets = new LinkedHashMap<>();

        for (DiscoverPublicProviderProfileEntity profile : profiles.findAll()) {
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
        return listProfiles(new PublicProviderSearchCriteria(providerType, q, city, area, speciality, service), 0, 512).getContent();
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

    private Optional<PublicProviderProfileDetailRecord> detailForSlugAlias(DiscoverPublicProviderProfileSlugEntity alias) {
        DiscoverPublicProviderProfileEntity profile = profiles.findByProviderId(alias.getProviderId()).orElse(null);
        DiscoverPublicProviderProfileVersionEntity version = versions.findByProviderIdAndVersionNumber(alias.getProviderId(), alias.getVersionNumber()).orElse(null);
        if (profile == null || version == null) {
            return Optional.empty();
        }
        PublicProviderProfileSnapshot snapshot = readSnapshot(version.getSnapshotJson());
        return Optional.of(toDetail(profile, version, alias.getSlug(), snapshot));
    }

    private PublicProviderProfileSummaryRecord toSummary(DiscoverPublicProviderProfileEntity entity, Map<UUID, ProviderDocumentEntity> documentMap) {
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
                entity.isEmergencyAvailable(),
                tags(entity)
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
                        item.isAccessibilityAvailable()))
                .toList();
        List<PublicProviderGalleryImageSnapshot> gallery = documentRecords.stream()
                .filter(record -> record.getDocumentType() == ProviderDocumentType.GALLERY_IMAGE)
                .map(record -> new PublicProviderGalleryImageSnapshot(record.getId(), record.getOriginalFilename()))
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
                true,
                publishedAt,
                versionNumber,
                publicPath(application.getProviderType(), canonicalSlug)
        );
    }

    private void upsertAggregate(PublicProviderProfileSnapshot snapshot, UUID latestVersionId, int latestVersionNumber, OffsetDateTime publishedAt) {
        DiscoverPublicProviderProfileEntity entity = profiles.findByProviderId(snapshot.providerId()).orElseGet(() -> DiscoverPublicProviderProfileEntity.create(
                snapshot.providerId(),
                snapshot.providerType(),
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
                publishedAt
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
                publishedAt
        );
        profiles.save(entity);
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
            throw new IllegalStateException("public profile slug is already in use");
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
