package com.deepthoughtnet.clinic.discover.publicprofiledraft;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionRepository;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftFieldSourceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftMediaContentRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftMediaUploadRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionUpdateRequest;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftVersionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftEntity;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftRepository;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftVersionEntity;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftVersionRepository;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderPublicProfileDraftService {
    private static final String SOURCE_SYSTEM = "HEALTHCARE_CLINIC_PROFILE";
    private static final String PUBLIC_PROFILE_STATUS = "UNPUBLISHED";
    private static final long LOGO_MAX_BYTES = 5L * 1024L * 1024L;
    private static final long MEDIA_MAX_BYTES = 10L * 1024L * 1024L;
    private static final List<String> SUPPORTED_MEDIA_CONTENT_TYPES = List.of("image/png", "image/jpeg", "image/webp");
    private static final List<String> MANDATORY_FIELDS = List.of(
            "displayName",
            "description",
            "addressLine1",
            "city",
            "state",
            "country",
            "publicContact",
            "specialities",
            "services",
            "timings",
            "logo",
            "cover"
    );
    private static final List<String> RECOMMENDED_FIELDS = List.of(
            "gallery",
            "establishedYear",
            "facilities",
            "languages",
            "fees",
            "website",
            "whatsappNumber",
            "metaTitle",
            "metaDescription"
    );

    private final DiscoverPublicProfileDraftRepository drafts;
    private final DiscoverPublicProfileDraftVersionRepository versions;
    private final ProviderOwnershipService ownershipService;
    private final ClinicProfileConsentLookup clinicProfileConsentLookup;
    private final ProviderPublicProfileService publicProfileService;
    private final DiscoverPublicProfileSubmissionRepository submissions;
    private final ObjectStorageService storageService;
    private final ObjectMapper objectMapper;

    public ProviderPublicProfileDraftService(
            DiscoverPublicProfileDraftRepository drafts,
            DiscoverPublicProfileDraftVersionRepository versions,
            ProviderOwnershipService ownershipService,
            ClinicProfileConsentLookup clinicProfileConsentLookup,
            ProviderPublicProfileService publicProfileService,
            DiscoverPublicProfileSubmissionRepository submissions,
            ObjectStorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.drafts = drafts;
        this.versions = versions;
        this.ownershipService = ownershipService;
        this.clinicProfileConsentLookup = clinicProfileConsentLookup;
        this.publicProfileService = publicProfileService;
        this.submissions = submissions;
        this.storageService = storageService;
        this.objectMapper = objectMapper.copy().findAndRegisterModules().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Transactional
    public PublicProfileDraftWorkspaceRecord createOrLoadDraft(UUID providerAccountId, String publicProfileReference) {
        return loadOrCreate(providerAccountId, publicProfileReference, true);
    }

    @Transactional(readOnly = true)
    public PublicProfileDraftWorkspaceRecord getDraft(UUID providerAccountId, String publicProfileReference) {
        return loadOrCreate(providerAccountId, publicProfileReference, false);
    }

    @Transactional
    public PublicProfileDraftWorkspaceRecord recalculateReadiness(UUID providerAccountId, String publicProfileReference) {
        requireEditableAccess(providerAccountId, publicProfileReference);
        DiscoverPublicProfileDraftEntity entity = loadDraftEntity(publicProfileReference)
                .orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_draft_not_found", "Public profile draft not found."));
        canonicalDraftState(entity, true);
        return toWorkspace(entity);
    }

    @Transactional
    public PublicProfileDraftWorkspaceRecord saveSection(UUID providerAccountId, String publicProfileReference, PublicProfileDraftSectionUpdateRequest request) {
        requireEditableAccess(providerAccountId, publicProfileReference);
        if (request == null || !StringUtils.hasText(request.sectionKey())) {
            throw new ProviderOwnershipConflictException("invalid_public_profile_section", "Section key is required.");
        }
        ensureNotSubmitted(publicProfileReference);
        DiscoverPublicProfileDraftEntity entity = loadDraftEntity(publicProfileReference)
                .orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_draft_not_found", "Public profile draft not found."));
        if (request.expectedVersion() != null && request.expectedVersion() != entity.getCurrentVersion()) {
            throw new ProviderOwnershipConflictException("stale_public_profile_draft", "This profile was updated elsewhere. Reload before saving.");
        }
        Map<String, Object> content = contentMap(entity);
        Map<String, Object> nextSection = normalizeContent(request.sectionKey(), request.content(), entity.getPublicProfileType());
        Map<String, Object> currentSection = asSection(content.get(request.sectionKey()));
        if (Objects.equals(currentSection, nextSection)) {
            return toWorkspace(entity);
        }
        content.put(request.sectionKey(), nextSection);
        Map<String, PublicProfileDraftFieldSourceRecord> sources = sourceMap(entity);
        OffsetDateTime now = OffsetDateTime.now();
        nextSection.keySet().forEach(field -> sources.put(fieldPath(request.sectionKey(), field), new PublicProfileDraftFieldSourceRecord(
                "PROVIDER_ENTERED",
                "PROVIDER_WORKSPACE",
                entity.getSourceRevision(),
                now,
                providerAccountId,
                now,
                true
        )));
        return persistVersion(entity, providerAccountId, request.changeSummary(), content, sources, false);
    }

    @Transactional
    public PublicProfileDraftMediaUploadRecord uploadMedia(
            UUID providerAccountId,
            String publicProfileReference,
            ProviderDocumentType mediaType,
            String originalFilename,
            String contentType,
            long sizeBytes,
            byte[] bytes,
            String altText
    ) {
        requireEditableAccess(providerAccountId, publicProfileReference);
        if (mediaType == null) {
            throw new ProviderOwnershipConflictException("public_profile_media_invalid", "Media type is required.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new ProviderOwnershipConflictException("public_profile_media_invalid", "File is required.");
        }
        if (!StringUtils.hasText(originalFilename)) {
            throw new ProviderOwnershipConflictException("public_profile_media_invalid", "Filename is required.");
        }
        String normalizedContentType = normalizeMediaContentType(contentType, originalFilename, bytes);
        if (!SUPPORTED_MEDIA_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new ProviderOwnershipConflictException("public_profile_media_type_not_supported", "Supported formats are PNG, JPG, JPEG, and WEBP.");
        }
        long maxBytes = mediaType == ProviderDocumentType.LOGO ? LOGO_MAX_BYTES : MEDIA_MAX_BYTES;
        if (sizeBytes <= 0L || sizeBytes > maxBytes) {
            throw new ProviderOwnershipConflictException("public_profile_media_too_large", mediaType == ProviderDocumentType.LOGO
                    ? "Logo must be 5 MB or smaller."
                    : "Cover and gallery images must be 10 MB or smaller.");
        }

        DiscoverPublicProfileDraftEntity entity = loadDraftEntity(normalizeReference(publicProfileReference))
                .orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_draft_not_found", "Public profile draft not found."));
        Map<String, Object> content = contentMap(entity);
        Map<String, Object> media = asSection(content.get("media"));
        Map<String, Object> metadataByReference = asSection(media.get("mediaMetadataByDocumentId"));
        Map<String, Object> altTextByReference = asSection(media.get("galleryAltTextByDocumentId"));
        String mediaReference = mediaReference(entity.getPublicProfileReference(), mediaType, bytes, originalFilename, normalizedContentType);
        String storageKey = storageKey(entity.getPublicProfileReference(), mediaReference);

        Object existingMetadata = metadataByReference.get(mediaReference);
        String normalizedFilename = sanitizeFilename(originalFilename);
        String normalizedAltText = StringUtils.hasText(altText) ? altText.trim() : null;
        if (existingMetadata instanceof Map<?, ?> existingMetadataMap) {
            Map<String, Object> existing = asSection(existingMetadataMap);
            boolean metadataMatches = Objects.equals(stringValue(existing, "mediaType"), mediaType.name())
                    && Objects.equals(stringValue(existing, "originalFilename"), normalizedFilename)
                    && Objects.equals(stringValue(existing, "contentType"), normalizedContentType)
                    && Objects.equals(numberValue(existing.get("sizeBytes")), sizeBytes);
            boolean alreadyAttached = switch (mediaType) {
                case LOGO -> Objects.equals(stringValue(media, "logoDocumentId"), mediaReference);
                case COVER_IMAGE -> Objects.equals(stringValue(media, "coverDocumentId"), mediaReference);
                case GALLERY_IMAGE -> stringList(media, "gallery").contains(mediaReference);
                default -> false;
            };
            boolean altTextMatches = !StringUtils.hasText(normalizedAltText)
                    ? !StringUtils.hasText(stringValue(altTextByReference, mediaReference))
                    : Objects.equals(stringValue(altTextByReference, mediaReference), normalizedAltText);
            if (metadataMatches && alreadyAttached && altTextMatches) {
                return new PublicProfileDraftMediaUploadRecord(mediaReference, toWorkspace(entity));
            }
        }

        storageService.putObject(storageKey, normalizedContentType, bytes);

        Map<String, Object> mediaMetadata = sectionMap(
                "mediaType", mediaType.name(),
                "originalFilename", normalizedFilename,
                "contentType", normalizedContentType,
                "sizeBytes", sizeBytes,
                "uploadedAt", OffsetDateTime.now(),
                "storageKey", storageKey
        );
        metadataByReference.put(mediaReference, mediaMetadata);

        switch (mediaType) {
            case LOGO -> media.put("logoDocumentId", mediaReference);
            case COVER_IMAGE -> media.put("coverDocumentId", mediaReference);
            case GALLERY_IMAGE -> {
                List<String> gallery = new ArrayList<>(stringList(media, "gallery"));
                if (!gallery.contains(mediaReference)) {
                    gallery.add(mediaReference);
                }
                media.put("gallery", gallery);
                if (!StringUtils.hasText(stringValue(media, "primaryGalleryDocumentId"))) {
                    media.put("primaryGalleryDocumentId", mediaReference);
                }
                if (StringUtils.hasText(altText)) {
                    altTextByReference.put(mediaReference, altText.trim());
                }
            }
            default -> {
                throw new ProviderOwnershipConflictException("public_profile_media_invalid", "Unsupported media type.");
            }
        }
        media.put("mediaMetadataByDocumentId", metadataByReference);
        media.put("galleryAltTextByDocumentId", altTextByReference);
        content.put("media", media);

        Map<String, PublicProfileDraftFieldSourceRecord> sources = sourceMap(entity);
        OffsetDateTime now = OffsetDateTime.now();
        sources.put(fieldPath("media", mediaType == ProviderDocumentType.LOGO ? "logoDocumentId" : mediaType == ProviderDocumentType.COVER_IMAGE ? "coverDocumentId" : "gallery"),
                new PublicProfileDraftFieldSourceRecord(
                        "PROVIDER_ENTERED",
                        "PROVIDER_MEDIA_UPLOAD",
                        entity.getSourceRevision(),
                        now,
                        providerAccountId,
                        now,
                        true
                ));

        return new PublicProfileDraftMediaUploadRecord(
                mediaReference,
                persistVersion(entity, providerAccountId, "Uploaded " + mediaType.name().toLowerCase(Locale.ROOT).replace('_', ' '), content, sources, false)
        );
    }

    @Transactional(readOnly = true)
    public PublicProfileDraftMediaContentRecord downloadMedia(UUID providerAccountId, String publicProfileReference, String mediaReference) {
        requireEditableAccess(providerAccountId, publicProfileReference);
        DiscoverPublicProfileDraftEntity entity = loadDraftEntity(normalizeReference(publicProfileReference))
                .orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_draft_not_found", "Public profile draft not found."));
        Map<String, Object> media = asSection(contentMap(entity).get("media"));
        Map<String, Object> metadataByReference = asSection(media.get("mediaMetadataByDocumentId"));
        Object metadata = metadataByReference.get(mediaReference);
        if (!(metadata instanceof Map<?, ?> metadataMap)) {
            throw new ProviderOwnershipConflictException("public_profile_media_not_found", "Media reference not found.");
        }
        Map<String, Object> normalizedMetadata = asSection(metadataMap);
        String storageKey = stringValue(normalizedMetadata, "storageKey");
        String contentType = stringValue(normalizedMetadata, "contentType");
        String originalFilename = stringValue(normalizedMetadata, "originalFilename");
        if (!StringUtils.hasText(storageKey)) {
            throw new ProviderOwnershipConflictException("public_profile_media_not_found", "Media reference not found.");
        }
        byte[] bytes = storageService.getObjectBytes(storageKey);
        if (bytes == null || bytes.length == 0) {
            throw new ProviderOwnershipConflictException("public_profile_media_not_found", "Media reference not found.");
        }
        return new PublicProfileDraftMediaContentRecord(
                mediaReference,
                StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                StringUtils.hasText(originalFilename) ? originalFilename : mediaReference,
                bytes
        );
    }

    @Transactional(readOnly = true)
    public PublicProfileDraftWorkspaceRecord preview(UUID providerAccountId, String publicProfileReference) {
        return getDraft(providerAccountId, publicProfileReference);
    }

    @Transactional(readOnly = true)
    public List<PublicProfileDraftWorkspaceRecord> listDraftLifecycle() {
        return drafts.findAll().stream()
                .sorted(Comparator.comparing(DiscoverPublicProfileDraftEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toWorkspace)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileDraftWorkspaceRecord> findDraft(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return drafts.findByPublicProfileReference(publicProfileReference.trim()).map(this::toWorkspace);
    }

    private PublicProfileDraftWorkspaceRecord loadOrCreate(UUID providerAccountId, String publicProfileReference, boolean createIfMissing) {
        requireEditableAccess(providerAccountId, publicProfileReference);
        String reference = normalizeReference(publicProfileReference);
        return loadDraftEntity(reference)
                .map(entity -> {
                    if (createIfMissing) {
                        hydrateHistoricalDraft(entity, providerAccountId);
                    }
                    return toWorkspace(entity);
                })
                .orElseGet(() -> {
                    if (!createIfMissing) {
                        throw new ProviderOwnershipConflictException("public_profile_draft_not_found", "Public profile draft not found.");
                    }
                    return createDraft(providerAccountId, reference);
                });
    }

    private PublicProfileDraftWorkspaceRecord createDraft(UUID providerAccountId, String publicProfileReference) {
        UUID providerId = parseUuid(publicProfileReference);
        PublicProfileLifecycleRecord lifecycle = publicProfileService.findLifecycleByProviderId(providerId).orElse(null);
        PublicProviderProfileSnapshot snapshot = publicProfileService.findSnapshotByProviderId(providerId).orElse(null);
        ProviderType publicProfileType = snapshot != null && snapshot.providerType() != null
                ? snapshot.providerType()
                : lifecycle == null ? ProviderType.CLINIC : lifecycle.providerType();
        String tenantConsentStatus = resolveTenantConsentStatus(publicProfileReference, "DISABLED");
        if (publicProfileType != ProviderType.CLINIC && !StringUtils.hasText(tenantConsentStatus)) {
            tenantConsentStatus = "ENABLED";
        }
        if (publicProfileType != ProviderType.CLINIC && "DISABLED".equalsIgnoreCase(tenantConsentStatus)) {
            tenantConsentStatus = "ENABLED";
        }
        String publicProfileStatus = lifecycle == null ? PUBLIC_PROFILE_STATUS : firstNonBlank(lifecycle.publicationStatus(), PUBLIC_PROFILE_STATUS);
        Map<String, Object> content = initialContent(lifecycle, tenantConsentStatus, publicProfileType, providerId, snapshot);
        content = hydrateHistoricalContent(content, publicProfileReference, lifecycle, snapshot);
        Map<String, PublicProfileDraftFieldSourceRecord> sources = initialSources(lifecycle, providerId, snapshot);
        PublicProfileDraftReadinessRecord readiness = evaluateReadiness(content, providerAccountId, true, 1);
        String canonicalSlug = resolveSlug(lifecycle, providerId, snapshot);
        String publicPath = publicPath(publicProfileType, canonicalSlug);
        String draftReference = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        String contentJson = toJson(content);
        String sourcesJson = toJson(sources);
        String readinessJson = toJson(readiness);
        SummaryValues summary = summarize(content, lifecycle, canonicalSlug);
        try {
            DiscoverPublicProfileDraftEntity entity = drafts.save(DiscoverPublicProfileDraftEntity.create(
                    UUID.randomUUID(),
                    draftReference,
                    publicProfileReference,
                    publicProfileType,
                    providerAccountId,
                    "VERIFIED",
                    tenantConsentStatus,
                    publicProfileStatus,
                    readiness.ready() ? "READY_FOR_REVIEW" : "DRAFT_INCOMPLETE",
                    readiness.readinessStatus(),
                    readiness.completenessPercentage(),
                    1,
                    SOURCE_SYSTEM,
                    SOURCE_SYSTEM,
                    0L,
                    lifecycle == null ? now : lifecycle.projectedAt(),
                    summary.displayName(),
                    summary.canonicalSlug(),
                    summary.city(),
                    summary.area(),
                    summary.state(),
                    summary.country(),
                    summary.publicPhone(),
                    summary.publicEmail(),
                    summary.website(),
                    summary.whatsappNumber(),
                    summary.registrationNumber(),
                    summary.establishedYear(),
                    now,
                    providerAccountId,
                    providerAccountId,
                    now,
                    now,
                    publicPath,
                    contentJson,
                    sourcesJson,
                    readinessJson
            ));
            versions.save(DiscoverPublicProfileDraftVersionEntity.create(
                    draftReference,
                    publicProfileReference,
                    1,
                    "Initial draft",
                    contentJson,
                    readinessJson,
                    sourcesJson,
                    providerAccountId,
                    now
            ));
            return toWorkspace(entity);
        } catch (DataIntegrityViolationException ex) {
            return drafts.findByPublicProfileReference(publicProfileReference)
                    .map(entity -> {
                        hydrateHistoricalDraft(entity, providerAccountId);
                        return toWorkspace(entity);
                    })
                    .orElseThrow(() -> ex);
        }
    }

    private PublicProfileDraftWorkspaceRecord persistVersion(
            DiscoverPublicProfileDraftEntity entity,
            UUID providerAccountId,
            String changeSummary,
            Map<String, Object> content,
            Map<String, PublicProfileDraftFieldSourceRecord> sources,
            boolean force
    ) {
        String contentJson = toJson(content);
        if (!force && Objects.equals(contentJson, entity.getContentJson())) {
            return toWorkspace(entity);
        }
        int nextVersion = entity.getCurrentVersion() + 1;
        PublicProfileDraftReadinessRecord readiness = evaluateReadiness(content, providerAccountId, false, nextVersion);
        String tenantConsentStatus = resolveTenantConsentStatus(entity.getPublicProfileReference(), entity.getTenantConsentStatus());
        OffsetDateTime now = OffsetDateTime.now();
        String readinessJson = toJson(readiness);
        String sourcesJson = toJson(sources);
        SummaryValues summary = summarize(content, null, entity.getCanonicalSlug());
        entity.update(
                readiness.ready() ? "READY_FOR_REVIEW" : "DRAFT_INCOMPLETE",
                readiness.readinessStatus(),
                readiness.completenessPercentage(),
                tenantConsentStatus,
                entity.getPublicProfileStatus(),
                nextVersion,
                summary.displayName(),
                summary.canonicalSlug(),
                summary.city(),
                summary.area(),
                summary.state(),
                summary.country(),
                summary.publicPhone(),
                summary.publicEmail(),
                summary.website(),
                summary.whatsappNumber(),
                summary.registrationNumber(),
                summary.establishedYear(),
                now,
                providerAccountId,
                publicPath(entity.getPublicProfileType(), summary.canonicalSlug()),
                contentJson,
                sourcesJson,
                readinessJson
        );
        drafts.save(entity);
        versions.save(DiscoverPublicProfileDraftVersionEntity.create(
                entity.getDraftReference(),
                entity.getPublicProfileReference(),
                nextVersion,
                StringUtils.hasText(changeSummary) ? changeSummary.trim() : "Updated draft section",
                contentJson,
                readinessJson,
                sourcesJson,
                providerAccountId,
                now
        ));
        return toWorkspace(entity);
    }

    private void requireEditableAccess(UUID providerAccountId, String publicProfileReference) {
        if (providerAccountId == null) {
            throw new ProviderOwnershipConflictException("public_profile_edit_not_allowed", "Provider account is required.");
        }
        String normalizedReference = normalizeReference(publicProfileReference);
        ensureNotSubmitted(normalizedReference);
        var ownership = ownershipService.findOwnership(providerAccountId, normalizedReference)
                .orElseThrow(() -> new ProviderOwnershipConflictException("ownership_not_verified", "Verified ownership is required before editing a public profile."));
        if (ownership.status() != com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus.VERIFIED) {
            throw new ProviderOwnershipConflictException("ownership_not_verified", "Verified ownership is required before editing a public profile.");
        }
        boolean activeOwnerMembership = ownershipService.listMemberships(normalizedReference).stream()
                .anyMatch(item -> providerAccountId.equals(item.providerAccountId())
                        && item.role() == com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole.OWNER
                        && "ACTIVE".equalsIgnoreCase(item.status()));
        if (!activeOwnerMembership) {
            throw new ProviderOwnershipConflictException("public_profile_edit_not_allowed", "An active owner membership is required.");
        }
    }

    private void ensureNotSubmitted(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return;
        }
        submissions.findFirstByPublicProfileReferenceAndCurrentTrueOrderBySubmittedAtDesc(publicProfileReference.trim())
                .filter(entity -> "SUBMITTED".equals(entity.getModerationStatus()) || "UNDER_REVIEW".equals(entity.getModerationStatus()))
                .ifPresent(entity -> {
                    throw new ProviderOwnershipConflictException("public_profile_edit_not_allowed", "This public profile is locked while a submission is under review.");
                });
    }

    private Optional<DiscoverPublicProfileDraftEntity> loadDraftEntity(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return drafts.findByPublicProfileReference(publicProfileReference.trim());
    }

    private PublicProfileDraftWorkspaceRecord toWorkspace(DiscoverPublicProfileDraftEntity entity) {
        Map<String, Object> content = contentMap(entity);
        Map<String, Object> about = asSection(content.get("about"));
        Map<String, PublicProfileDraftFieldSourceRecord> sources = sourceMap(entity);
        CanonicalDraftState canonical = canonicalDraftState(entity, false);
        List<PublicProfileDraftVersionRecord> versionRecords = versions.findByDraftReferenceOrderByVersionNumberDesc(entity.getDraftReference()).stream()
                .map(version -> new PublicProfileDraftVersionRecord(
                        version.getId(),
                        version.getVersionNumber(),
                        version.getChangeSummary(),
                        version.getCreatedAt(),
                        version.getCreatedByProviderAccountId()
                ))
                .toList();
        List<PublicProfileDraftSectionRecord> sections = sections(content, sources, canonical);
        return new PublicProfileDraftWorkspaceRecord(
                entity.getId(),
                entity.getDraftReference(),
                entity.getPublicProfileReference(),
                entity.getPublicProfileType(),
                entity.getProviderAccountId(),
                entity.getOwnershipStatus(),
                canonical.tenantConsentStatus(),
                entity.getPublicProfileStatus(),
                canonical.contentStatus(),
                canonical.readinessStatus(),
                canonical.completenessPercentage(),
                entity.getCurrentVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastSavedAt(),
                entity.getSourceUpdatedAt(),
                entity.getDisplayName(),
                entity.getCanonicalSlug(),
                entity.getCity(),
                entity.getArea(),
                entity.getState(),
                entity.getCountry(),
                entity.getPublicPhone(),
                entity.getPublicEmail(),
                entity.getWebsite(),
                entity.getWhatsappNumber(),
                entity.getRegistrationNumber(),
                resolveEstablishedYear(about),
                entity.getSourceSystem(),
                entity.getSourceReference(),
                entity.getSourceRevision(),
                entity.getSourceUpdatedAt(),
                entity.getPublicPath(),
                canonical.allowedActions(),
                sections,
                canonical.readiness(),
                versionRecords,
                sources
        );
    }

    private List<PublicProfileDraftSectionRecord> sections(
            Map<String, Object> content,
            Map<String, PublicProfileDraftFieldSourceRecord> sources,
            CanonicalDraftState canonical
    ) {
        return List.of(
                section("overview", "Overview", content, sources, canonical),
                section("about", "About", content, sources, canonical),
                section("contact", "Contact", content, sources, canonical),
                section("services", "Services", content, sources, canonical),
                section("specialities", "Specialities", content, sources, canonical),
                section("facilities", "Facilities", content, sources, canonical),
                section("timings", "Timings", content, sources, canonical),
                section("fees", "Fees", content, sources, canonical),
                section("languages", "Languages", content, sources, canonical),
                section("media", "Media", content, sources, canonical),
                section("seo", "SEO", content, sources, canonical)
        );
    }

    private PublicProfileDraftSectionRecord section(
            String key,
            String title,
            Map<String, Object> content,
            Map<String, PublicProfileDraftFieldSourceRecord> sources,
            CanonicalDraftState canonical
    ) {
        Map<String, Object> section = asSection(content.getOrDefault(key, Map.of()));
        if ("overview".equals(key) && canonical != null) {
            Map<String, Object> aligned = new LinkedHashMap<>(section);
            aligned.put("contentStatus", canonical.contentStatus());
            aligned.put("completenessPercentage", canonical.completenessPercentage());
            aligned.put("summaryStatus", canonical.readiness().ready() ? "READY" : "DRAFT");
            aligned.put("tenantConsentStatus", canonical.tenantConsentStatus());
            section = aligned;
        }
        Map<String, PublicProfileDraftFieldSourceRecord> sectionSources = sources.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(key + "."))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        return new PublicProfileDraftSectionRecord(key, title, section, sectionSources);
    }

    private List<String> allowedActions(DiscoverPublicProfileDraftEntity entity) {
        if (entity == null) {
            return List.of();
        }
        return canonicalDraftState(entity, false).allowedActions();
    }

    private PublicProfileDraftReadinessRecord readiness(DiscoverPublicProfileDraftEntity entity) {
        return canonicalDraftState(entity, false).readiness();
    }

    private String resolveTenantConsentStatus(String publicProfileReference, String fallbackStatus) {
        UUID tenantId = parseUuid(publicProfileReference);
        if (tenantId == null) {
            return hasText(fallbackStatus) ? fallbackStatus : "DISABLED";
        }
        return clinicProfileConsentLookup.findDiscoverPublicListingEnabled(tenantId)
                .map(enabled -> enabled ? "ENABLED" : "DISABLED")
                .orElseGet(() -> hasText(fallbackStatus) ? fallbackStatus : "DISABLED");
    }

    private CanonicalDraftState canonicalDraftState(DiscoverPublicProfileDraftEntity entity) {
        return canonicalDraftState(entity, false);
    }

    private CanonicalDraftState canonicalDraftState(DiscoverPublicProfileDraftEntity entity, boolean persistIfChanged) {
        Map<String, Object> content = contentMap(entity);
        PublicProfileDraftReadinessRecord readiness = evaluateReadiness(content, entity.getProviderAccountId(), false, entity.getCurrentVersion());
        String tenantConsentStatus = resolveTenantConsentStatus(entity.getPublicProfileReference(), entity.getTenantConsentStatus());
        String contentStatus = readiness.ready() ? "READY_FOR_REVIEW" : "DRAFT_INCOMPLETE";
        String readinessStatus = readiness.readinessStatus();
        int completenessPercentage = readiness.completenessPercentage();
        if (persistIfChanged && needsCanonicalUpdate(entity, contentStatus, readinessStatus, completenessPercentage, tenantConsentStatus, readiness)) {
            OffsetDateTime now = OffsetDateTime.now();
            entity.update(
                    contentStatus,
                    readinessStatus,
                    completenessPercentage,
                    tenantConsentStatus,
                    entity.getPublicProfileStatus(),
                    entity.getCurrentVersion(),
                    entity.getDisplayName(),
                    entity.getCanonicalSlug(),
                    entity.getCity(),
                    entity.getArea(),
                    entity.getState(),
                    entity.getCountry(),
                    entity.getPublicPhone(),
                    entity.getPublicEmail(),
                    entity.getWebsite(),
                    entity.getWhatsappNumber(),
                    entity.getRegistrationNumber(),
                    entity.getEstablishedYear(),
                    now,
                    entity.getUpdatedByProviderAccountId(),
                    entity.getPublicPath(),
                    entity.getContentJson(),
                    entity.getSourceAttributionJson(),
                    toJson(readiness)
            );
            drafts.save(entity);
        }
        return new CanonicalDraftState(
                contentStatus,
                readinessStatus,
                completenessPercentage,
                tenantConsentStatus,
                readiness,
                allowedActions(contentStatus, readinessStatus, tenantConsentStatus, entity.getPublicProfileStatus())
        );
    }

    private List<String> allowedActions(String contentStatus, String readinessStatus, String tenantConsentStatus, String publicProfileStatus) {
        if ("PUBLISHED".equals(publicProfileStatus)) {
            return List.of("VIEW_DETAILS", "OPEN_PUBLIC_PROFILE", "VIEW_PREVIEW", "VIEW_READINESS", "SAVE_DRAFT");
        }
        List<String> actions = new ArrayList<>(List.of("VIEW_DETAILS", "OPEN_PUBLIC_PROFILE", "VIEW_PREVIEW", "VIEW_READINESS", "SAVE_DRAFT"));
        if ("READY_FOR_REVIEW".equals(contentStatus) && "READY".equals(readinessStatus) && "ENABLED".equalsIgnoreCase(tenantConsentStatus)) {
            actions.add("SUBMIT_FOR_REVIEW");
        }
        return List.copyOf(actions);
    }

    private boolean needsCanonicalUpdate(
            DiscoverPublicProfileDraftEntity entity,
            String nextContentStatus,
            String nextReadinessStatus,
            int nextCompletenessPercentage,
            String nextTenantConsentStatus,
            PublicProfileDraftReadinessRecord nextReadiness
    ) {
        PublicProfileDraftReadinessRecord currentReadiness = readReadinessRecord(entity.getReadinessJson()).orElse(null);
        return !Objects.equals(entity.getContentStatus(), nextContentStatus)
                || !Objects.equals(entity.getReadinessStatus(), nextReadinessStatus)
                || entity.getCompletenessPercentage() != nextCompletenessPercentage
                || !Objects.equals(entity.getTenantConsentStatus(), nextTenantConsentStatus)
                || !readinessSemanticallyEquals(currentReadiness, nextReadiness);
    }

    private boolean readinessSemanticallyEquals(PublicProfileDraftReadinessRecord left, PublicProfileDraftReadinessRecord right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.readinessStatus(), right.readinessStatus())
                && left.ready() == right.ready()
                && left.completenessPercentage() == right.completenessPercentage()
                && Objects.equals(left.missingMandatoryFields(), right.missingMandatoryFields())
                && Objects.equals(left.recommendedFields(), right.recommendedFields())
                && Objects.equals(left.invalidFields(), right.invalidFields())
                && Objects.equals(left.warnings(), right.warnings())
                && Objects.equals(left.blockingReasons(), right.blockingReasons())
                && Objects.equals(left.evaluatedDraftVersion(), right.evaluatedDraftVersion());
    }

    private PublicProfileDraftReadinessRecord evaluateReadiness(Map<String, Object> content, UUID providerAccountId, boolean newlyCreated, Integer evaluatedDraftVersion) {
        Map<String, Object> about = asSection(content.get("about"));
        Map<String, Object> contact = asSection(content.get("contact"));
        Map<String, Object> services = asSection(content.get("services"));
        Map<String, Object> specialities = asSection(content.get("specialities"));
        Map<String, Object> timings = asSection(content.get("timings"));
        Map<String, Object> media = asSection(content.get("media"));
        Map<String, Object> seo = asSection(content.get("seo"));

        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockingReasons = new ArrayList<>();

        if (!hasText(stringValue(about, "displayName"))) missing.add("displayName");
        if (stringValue(about, "description") == null || stringValue(about, "description").trim().length() < 80) missing.add("description");
        if (!hasText(stringValue(contact, "addressLine1"))) missing.add("addressLine1");
        if (!hasText(stringValue(contact, "city"))) missing.add("city");
        if (!hasText(stringValue(contact, "state"))) missing.add("state");
        if (!hasText(stringValue(contact, "country"))) missing.add("country");
        if (!hasText(firstNonBlank(stringValue(contact, "publicPhone"), stringValue(contact, "publicEmail"), stringValue(contact, "whatsappNumber")))) missing.add("publicContact");
        if (stringList(specialities, "items").isEmpty()) missing.add("specialities");
        if (stringList(services, "items").isEmpty()) missing.add("services");
        if (timingDays(timings).isEmpty()) missing.add("timings");
        if (!hasText(stringValue(media, "logoDocumentId"))) missing.add("logo");
        if (!hasText(stringValue(media, "coverDocumentId"))) missing.add("cover");

        List<String> serviceItems = stringList(services, "items");
        List<String> specialityItems = stringList(specialities, "items");
        List<String> languageItems = stringList(asSection(content.get("languages")), "items");

        if (new LinkedHashSet<>(serviceItems).size() != serviceItems.size()) invalid.add("duplicate_services");
        if (new LinkedHashSet<>(specialityItems).size() != specialityItems.size()) invalid.add("duplicate_specialities");
        if (new LinkedHashSet<>(languageItems).size() != languageItems.size()) invalid.add("duplicate_languages");
        if (overlappingTimings(timings)) invalid.add("overlapping_timings");
        if (stringValue(contact, "publicPhone") != null && !stringValue(contact, "publicPhone").trim().matches("[0-9+()\\-\\s]{7,20}")) invalid.add("invalid_phone");
        if (stringValue(contact, "publicEmail") != null && !stringValue(contact, "publicEmail").trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) invalid.add("invalid_email");
        if (stringValue(seo, "canonicalPublicPath") != null && !stringValue(seo, "canonicalPublicPath").startsWith("/discover/")) warnings.add("canonical_path_preview_only");
        Integer normalizedEstablishedYear = resolveEstablishedYear(about);
        if (hasText(stringValue(about, "establishedYear")) && normalizedEstablishedYear == null) invalid.add("invalid_established_year");

        if (ownershipService.findLatestVerifiedOwnership(providerAccountId).isEmpty()) {
            blockingReasons.add("OWNERSHIP_NOT_VERIFIED");
        }

        boolean ready = missing.isEmpty() && invalid.isEmpty();
        int mandatoryScore = score(MANDATORY_FIELDS, missing);
        int recommendedScore = recommendedScore(content);
        int completeness = ready ? 100 : (int) Math.round((mandatoryScore * 0.8d) + (recommendedScore * 0.2d));
        String readinessStatus = ready ? "READY" : "INCOMPLETE";
        return new PublicProfileDraftReadinessRecord(
                readinessStatus,
                ready,
                Math.min(100, Math.max(0, completeness)),
                missing,
                RECOMMENDED_FIELDS,
                invalid,
                warnings,
                blockingReasons,
                OffsetDateTime.now(),
                evaluatedDraftVersion
        );
    }

    private int score(Collection<String> expected, Collection<String> missing) {
        if (expected.isEmpty()) {
            return 100;
        }
        long present = expected.stream().filter(item -> !missing.contains(item)).count();
        return (int) Math.round((present * 100d) / expected.size());
    }

    private boolean overlappingTimings(Map<String, Object> timings) {
        Map<String, List<int[]>> slotsByDay = new LinkedHashMap<>();
        for (Map<String, Object> day : timingDays(timings)) {
            String inheritedDay = firstNonBlank(stringValue(day, "dayOfWeek"), stringValue(day, "day"));
            List<Map<String, Object>> intervals = timingIntervals(day);
            if (intervals.isEmpty()) {
                int start = minutes(firstNonBlank(stringValue(day, "startTime"), stringValue(day, "start"), stringValue(day, "open")));
                int end = minutes(firstNonBlank(stringValue(day, "endTime"), stringValue(day, "end"), stringValue(day, "close")));
                if (start < 0 || end <= start) {
                    return true;
                }
                String dayKey = normalizeDay(inheritedDay);
                if (dayKey != null) {
                    slotsByDay.computeIfAbsent(dayKey, ignored -> new ArrayList<>()).add(new int[]{start, end});
                }
                continue;
            }
            for (Map<String, Object> interval : intervals) {
                String dayKey = normalizeDay(firstNonBlank(stringValue(interval, "dayOfWeek"), stringValue(interval, "day"), inheritedDay));
                int start = minutes(firstNonBlank(stringValue(interval, "startTime"), stringValue(interval, "start"), stringValue(interval, "open")));
                int end = minutes(firstNonBlank(stringValue(interval, "endTime"), stringValue(interval, "end"), stringValue(interval, "close")));
                if (start < 0 || end <= start) {
                    return true;
                }
                if (dayKey != null) {
                    slotsByDay.computeIfAbsent(dayKey, ignored -> new ArrayList<>()).add(new int[]{start, end});
                }
            }
        }
        for (List<int[]> slots : slotsByDay.values()) {
            if (slots.size() < 2) {
                continue;
            }
            slots.sort(Comparator.comparingInt(slot -> slot[0]));
            for (int i = 1; i < slots.size(); i++) {
                if (slots.get(i)[0] < slots.get(i - 1)[1]) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Map<String, Object>> timingDays(Map<String, Object> timings) {
        if (timings == null) {
            return List.of();
        }
        List<Map<String, Object>> weekly = asList(timings.get("weekly"));
        if (!weekly.isEmpty()) {
            return weekly;
        }
        List<Map<String, Object>> intervals = asList(timings.get("intervals"));
        if (!intervals.isEmpty()) {
            return List.of(timings);
        }
        return List.of();
    }

    private List<Map<String, Object>> timingIntervals(Map<String, Object> timingDay) {
        List<Map<String, Object>> intervals = asList(timingDay.get("intervals"));
        if (!intervals.isEmpty()) {
            return intervals;
        }
        return List.of(timingDay);
    }

    private String normalizeDay(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday" -> normalized;
            default -> null;
        };
    }

    private int minutes(String value) {
        if (!hasText(value) || !value.contains(":")) {
            return -1;
        }
        String[] parts = value.split(":");
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private boolean slugConflict(String slug) {
        if (!hasText(slug)) {
            return false;
        }
        return publicProfileService.isSlugReserved(slug, null);
    }

    private Map<String, Object> initialContent(
            PublicProfileLifecycleRecord lifecycle,
            String tenantConsentStatus,
            ProviderType publicProfileType,
            UUID providerId,
            PublicProviderProfileSnapshot snapshot
    ) {
        Map<String, Object> content = new LinkedHashMap<>();
        String displayName = lifecycle == null ? "Provider profile" : firstNonBlank(lifecycle.displayName(), "Provider profile");
        String city = lifecycle == null ? null : lifecycle.city();
        String area = lifecycle == null ? null : lifecycle.area();
        String slug = resolveSlug(lifecycle, providerId, snapshot);
        content.put("overview", sectionMap(
                "profileType", publicProfileType == null ? "CLINIC" : publicProfileType.name(),
                "displayName", displayName,
                "shortTagline", null,
                "establishedYear", null,
                "summaryStatus", "DRAFT",
                "ownershipStatus", "VERIFIED",
                "tenantConsentStatus", tenantConsentStatus,
                "contentStatus", "DRAFT_INCOMPLETE",
                "completenessPercentage", 0,
                "lastSavedAt", null
        ));
        content.put("about", sectionMap(
                "displayName", displayName,
                "shortTagline", null,
                "description", null,
                "philosophy", null,
                "establishedYear", null,
                "registrationNumber", null,
                "emergencyAvailability", null
        ));
        content.put("contact", sectionMap(
                "publicPhone", null,
                "publicEmail", null,
                "website", null,
                "whatsappNumber", null,
                "addressLine1", null,
                "addressLine2", null,
                "area", area,
                "city", city,
                "state", null,
                "country", null,
                "postalCode", null,
                "phoneVisible", true,
                "emailVisible", true,
                "whatsappVisible", false
        ));
        content.put("services", sectionMap("items", List.of()));
        content.put("specialities", sectionMap("items", List.of(), "primary", null));
        content.put("facilities", sectionMap("items", List.of()));
        content.put("timings", sectionMap("timezone", "Asia/Kolkata", "weekly", List.of()));
        content.put("fees", sectionMap("currency", "INR", "visible", false));
        content.put("languages", sectionMap("items", List.of()));
        content.put("media", sectionMap(
                "logoDocumentId", null,
                "coverDocumentId", null,
                "gallery", List.of(),
                "primaryGalleryDocumentId", null,
                "galleryAltTextByDocumentId", Map.of(),
                "mediaMetadataByDocumentId", Map.of()
        ));
        content.put("seo", sectionMap(
                "slug", slug,
                "metaTitle", displayName,
                "metaDescription", null,
                "canonicalPublicPath", publicProfileType == null ? publicPath(ProviderType.CLINIC, slug) : publicPath(publicProfileType, slug)
        ));
        return content;
    }

    private Map<String, PublicProfileDraftFieldSourceRecord> initialSources(
            PublicProfileLifecycleRecord lifecycle,
            UUID providerId,
            PublicProviderProfileSnapshot snapshot
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, PublicProfileDraftFieldSourceRecord> sources = new LinkedHashMap<>();
        String displayName = lifecycle == null ? "Provider profile" : firstNonBlank(lifecycle.displayName(), "Provider profile");
        putSource(sources, "overview.displayName", now, displayName);
        putSource(sources, "about.displayName", now, displayName);
        putSource(sources, "contact.city", now, lifecycle == null ? null : lifecycle.city());
        putSource(sources, "contact.area", now, lifecycle == null ? null : lifecycle.area());
        putSource(sources, "seo.slug", now, resolveSlug(lifecycle, providerId, snapshot));
        putSource(sources, "seo.metaTitle", now, displayName);
        return sources;
    }

    private void putSource(Map<String, PublicProfileDraftFieldSourceRecord> sources, String fieldPath, OffsetDateTime now, String sourceReference) {
        sources.put(fieldPath, new PublicProfileDraftFieldSourceRecord(
                SOURCE_SYSTEM,
                StringUtils.hasText(sourceReference) ? sourceReference : SOURCE_SYSTEM,
                0L,
                now,
                null,
                null,
                false
        ));
    }

    private String resolveSlug(PublicProfileLifecycleRecord lifecycle, UUID providerId, PublicProviderProfileSnapshot snapshot) {
        if (snapshot != null && StringUtils.hasText(snapshot.canonicalSlug())) {
            return snapshot.canonicalSlug().trim();
        }
        if (lifecycle != null && StringUtils.hasText(lifecycle.canonicalSlug())) {
            return lifecycle.canonicalSlug().trim();
        }
        String requested = slugify(lifecycle == null ? null : lifecycle.displayName());
        if (!StringUtils.hasText(requested)) {
            requested = "provider";
        }
        String candidate = requested;
        int suffix = 2;
        while (publicProfileService.isSlugReserved(candidate, providerId)) {
            candidate = requested + "-" + suffix++;
        }
        return candidate;
    }

    private Map<String, Object> hydrateHistoricalContent(
            Map<String, Object> content,
            String publicProfileReference,
            PublicProfileLifecycleRecord lifecycle,
            PublicProviderProfileSnapshot snapshot
    ) {
        if (snapshot == null) {
            return content;
        }
        Map<String, Object> overview = asSection(content.get("overview"));
        Map<String, Object> about = asSection(content.get("about"));
        Map<String, Object> contact = asSection(content.get("contact"));
        Map<String, Object> services = asSection(content.get("services"));
        Map<String, Object> specialities = asSection(content.get("specialities"));
        Map<String, Object> facilities = asSection(content.get("facilities"));
        Map<String, Object> timings = asSection(content.get("timings"));
        Map<String, Object> media = asSection(content.get("media"));
        Map<String, Object> seo = asSection(content.get("seo"));

        applyIfBlank(overview, "displayName", snapshot.displayName());
        applyIfBlank(overview, "shortTagline", firstNonBlank(snapshot.tagline(), snapshot.summary()));

        applyIfBlank(about, "displayName", snapshot.displayName());
        applyIfBlank(about, "shortTagline", firstNonBlank(snapshot.tagline(), snapshot.summary()));
        applyIfBlank(about, "description", firstNonBlank(snapshot.biography(), snapshot.summary()));
        applyIfBlank(about, "philosophy", firstNonBlank(snapshot.summary(), snapshot.biography()));
        applyIfBlank(about, "emergencyAvailability", snapshot.emergencyAvailable() ? "Emergency care available" : null);
        repairHistoricalRegistrationNumber(about, publicProfileReference, lifecycle);

        PublicProviderLocationSnapshot primaryLocation = snapshot.locations() == null || snapshot.locations().isEmpty() ? null : snapshot.locations().getFirst();
        if (primaryLocation != null) {
            applyIfBlank(contact, "addressLine1", firstNonBlank(primaryLocation.address(), primaryLocation.label()));
            applyIfBlank(contact, "area", snapshot.area());
            applyIfBlank(contact, "city", primaryLocation.city());
            applyIfBlank(contact, "state", primaryLocation.state());
            applyIfBlank(contact, "country", primaryLocation.country());
            applyIfBlank(contact, "postalCode", primaryLocation.pinCode());
        }
        applyIfBlank(contact, "publicPhone", snapshot.contactPhone());
        applyIfBlank(contact, "publicEmail", snapshot.contactEmail());
        applyIfBlank(contact, "website", snapshot.website());
        applyIfBlank(contact, "whatsappNumber", snapshot.contactPhone());

        applyIfBlankList(services, "items", snapshot.services());
        applyIfBlankList(specialities, "items", snapshot.specialities());
        if (stringList(specialities, "items").isEmpty() && StringUtils.hasText(snapshot.primarySpeciality())) {
            specialities.put("items", List.of(snapshot.primarySpeciality().trim()));
        }
        applyIfBlankList(facilities, "items", snapshot.facilities());
        PublicProviderLocationSnapshot workingHoursLocation = primaryLocation(snapshot);
        if (requiresHistoricalTwentyFourSevenRepair(timings, workingHoursLocation)) {
            timings.put("timezone", firstNonBlank(snapshot.timingTimezone(), "Asia/Kolkata"));
            timings.put("intervals", canonicalTwentyFourSevenIntervals());
            timings.remove("weekly");
        } else if (stringList(timings, "intervals").isEmpty() && timingDays(timings).isEmpty() && snapshot.weeklyTimings() != null && !snapshot.weeklyTimings().isEmpty()) {
            timings.put("timezone", firstNonBlank(snapshot.timingTimezone(), "Asia/Kolkata"));
            timings.put("intervals", snapshot.weeklyTimings().stream()
                    .map(interval -> sectionMap(
                            "dayOfWeek", interval.day(),
                            "startTime", interval.open(),
                            "endTime", interval.close()
                    ))
                    .toList());
        } else {
            applyIfBlank(timings, "timezone", snapshot.timingTimezone());
        }

        applyIfBlank(media, "logoDocumentId", snapshot.logoDocumentId() == null ? null : snapshot.logoDocumentId().toString());
        applyIfBlank(media, "coverDocumentId", snapshot.coverImageDocumentId() == null ? null : snapshot.coverImageDocumentId().toString());
        if (stringList(media, "gallery").isEmpty() && snapshot.gallery() != null && !snapshot.gallery().isEmpty()) {
            List<String> gallery = snapshot.gallery().stream()
                    .map(item -> item.documentId() == null ? null : item.documentId().toString())
                    .filter(StringUtils::hasText)
                    .toList();
            media.put("gallery", gallery);
            if (!hasText(stringValue(media, "primaryGalleryDocumentId")) && !gallery.isEmpty()) {
                media.put("primaryGalleryDocumentId", gallery.getFirst());
            }
            Map<String, Object> metadataByDocumentId = asSection(media.get("mediaMetadataByDocumentId"));
            Map<String, Object> altTextByDocumentId = asSection(media.get("galleryAltTextByDocumentId"));
            for (int index = 0; index < snapshot.gallery().size(); index++) {
                PublicProviderGalleryImageSnapshot image = snapshot.gallery().get(index);
                if (image.documentId() == null) {
                    continue;
                }
                String documentId = image.documentId().toString();
                if (!metadataByDocumentId.containsKey(documentId)) {
                    metadataByDocumentId.put(documentId, sectionMap(
                            "mediaType", "GALLERY_IMAGE",
                            "originalFilename", firstNonBlank(image.caption(), "Gallery image " + (index + 1)),
                            "contentType", "image/jpeg",
                            "sizeBytes", null,
                            "uploadedAt", snapshot.publishedAt(),
                            "storageKey", null
                    ));
                }
                if (!altTextByDocumentId.containsKey(documentId) && StringUtils.hasText(image.caption())) {
                    altTextByDocumentId.put(documentId, image.caption().trim());
                }
            }
            media.put("mediaMetadataByDocumentId", metadataByDocumentId);
            media.put("galleryAltTextByDocumentId", altTextByDocumentId);
        }

        String canonicalSlug = resolveSlug(lifecycle, parseUuid(publicProfileReference), snapshot);
        if (!hasText(stringValue(seo, "slug")) || shouldRestoreCanonicalSlug(content, publicProfileReference, snapshot, canonicalSlug)) {
            seo.put("slug", canonicalSlug);
        }
        if (!hasText(stringValue(seo, "metaTitle")) && StringUtils.hasText(snapshot.displayName())) {
            seo.put("metaTitle", snapshot.displayName().trim());
        }
        if (!hasText(stringValue(seo, "metaDescription"))) {
            seo.put("metaDescription", firstNonBlank(snapshot.summary(), snapshot.biography()));
        }
        seo.put("canonicalPublicPath", snapshot.publicPath());

        content.put("overview", overview);
        content.put("about", about);
        content.put("contact", contact);
        content.put("services", services);
        content.put("specialities", specialities);
        content.put("facilities", facilities);
        content.put("timings", timings);
        content.put("media", media);
        content.put("seo", seo);
        return content;
    }

    private PublicProviderLocationSnapshot primaryLocation(PublicProviderProfileSnapshot snapshot) {
        return snapshot == null || snapshot.locations() == null || snapshot.locations().isEmpty() ? null : snapshot.locations().getFirst();
    }

    private boolean requiresHistoricalTwentyFourSevenRepair(Map<String, Object> timings, PublicProviderLocationSnapshot primaryLocation) {
        if (primaryLocation == null || !isTwentyFourSevenWorkingHours(primaryLocation.workingHours())) {
            return false;
        }
        List<Map<String, Object>> currentDays = timingDays(timings);
        if (currentDays.isEmpty()) {
            return true;
        }
        if (currentDays.size() != 7) {
            return true;
        }
        Map<String, String> canonical = canonicalTwentyFourSevenIntervals().stream()
                .collect(Collectors.toMap(
                        interval -> stringValue(interval, "dayOfWeek"),
                        interval -> stringValue(interval, "startTime") + "-" + stringValue(interval, "endTime"),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, String> current = new LinkedHashMap<>();
        for (Map<String, Object> day : currentDays) {
            String dayKey = normalizeDay(firstNonBlank(stringValue(day, "dayOfWeek"), stringValue(day, "day")));
            List<Map<String, Object>> intervals = timingIntervals(day);
            if (dayKey == null || intervals.size() != 1) {
                return true;
            }
            Map<String, Object> interval = intervals.getFirst();
            String start = firstNonBlank(stringValue(interval, "startTime"), stringValue(interval, "start"), stringValue(interval, "open"));
            String end = firstNonBlank(stringValue(interval, "endTime"), stringValue(interval, "end"), stringValue(interval, "close"));
            if (!"00:00".equals(start) || !"23:59".equals(end)) {
                return true;
            }
            current.put(dayKey, start + "-" + end);
        }
        return !current.equals(canonical);
    }

    private List<Map<String, Object>> canonicalTwentyFourSevenIntervals() {
        return List.of(
                sectionMap("dayOfWeek", "MONDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "TUESDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "WEDNESDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "THURSDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "FRIDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "SATURDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "SUNDAY", "startTime", "00:00", "endTime", "23:59")
        );
    }

    private boolean isTwentyFourSevenWorkingHours(String workingHours) {
        if (!hasText(workingHours)) {
            return false;
        }
        String normalized = workingHours.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]", "");
        return normalized.contains("24x7") || normalized.contains("24/7") || normalized.contains("247");
    }

    private void applyIfBlank(Map<String, Object> section, String key, String value) {
        if (!hasText(stringValue(section, key)) && hasText(value)) {
            section.put(key, value.trim());
        }
    }

    private void applyIfBlankList(Map<String, Object> section, String key, List<String> values) {
        if (stringList(section, key).isEmpty() && values != null && !values.isEmpty()) {
            section.put(key, values.stream().filter(StringUtils::hasText).map(String::trim).toList());
        }
    }

    private void repairHistoricalRegistrationNumber(Map<String, Object> about, String publicProfileReference, PublicProfileLifecycleRecord lifecycle) {
        String registration = stringValue(about, "registrationNumber");
        if (!hasText(registration)) {
            return;
        }
        String normalized = registration.trim();
        if (Objects.equals(normalized, publicProfileReference)
                || (lifecycle != null && Objects.equals(normalized, lifecycle.sourceEntityReference()))) {
            about.put("registrationNumber", null);
        }
    }

    private boolean shouldRestoreCanonicalSlug(
            Map<String, Object> content,
            String publicProfileReference,
            PublicProviderProfileSnapshot snapshot,
            String canonicalSlug
    ) {
        String currentSlug = stringValue(asSection(content.get("seo")), "slug");
        if (!hasText(currentSlug) || !StringUtils.hasText(canonicalSlug)) {
            return false;
        }
        String currentPath = stringValue(asSection(content.get("seo")), "canonicalPublicPath");
        if (StringUtils.hasText(currentPath) && currentPath.endsWith("-2")) {
            return true;
        }
        if (snapshot != null && Objects.equals(currentSlug.trim(), canonicalSlug.trim())) {
            return false;
        }
        return Objects.equals(currentSlug.trim(), publicProfileReference.trim());
    }

    private void hydrateHistoricalDraft(DiscoverPublicProfileDraftEntity entity, UUID providerAccountId) {
        UUID providerId = parseUuid(entity.getPublicProfileReference());
        if (providerId == null) {
            return;
        }
        PublicProfileLifecycleRecord lifecycle = publicProfileService.findLifecycleByProviderId(providerId).orElse(null);
        PublicProviderProfileSnapshot snapshot = publicProfileService.findSnapshotByProviderId(providerId).orElse(null);
        if (snapshot == null && lifecycle == null) {
            return;
        }
        Map<String, Object> currentContent = contentMap(entity);
        Map<String, Object> hydratedContent = hydrateHistoricalContent(readMap(toJson(currentContent)).orElseGet(LinkedHashMap::new), entity.getPublicProfileReference(), lifecycle, snapshot);
        String hydratedContentJson = toJson(hydratedContent);
        String canonicalSlug = resolveSlug(lifecycle, providerId, snapshot);
        String nextPublicPath = publicPath(entity.getPublicProfileType(), canonicalSlug);
        String nextRegistrationNumber = repairedRegistrationNumber(entity, entity.getPublicProfileReference(), lifecycle);
        SummaryValues summary = summarize(hydratedContent, lifecycle, canonicalSlug);
        PublicProfileDraftReadinessRecord readiness = evaluateReadiness(hydratedContent, providerAccountId, false, entity.getCurrentVersion());
        String nextContentStatus = readiness.ready() ? "READY_FOR_REVIEW" : "DRAFT_INCOMPLETE";
        String nextReadinessStatus = readiness.ready() ? "READY" : "INCOMPLETE";
        boolean changed = !Objects.equals(currentContent, hydratedContent)
                || !Objects.equals(entity.getCanonicalSlug(), canonicalSlug)
                || !Objects.equals(entity.getPublicPath(), nextPublicPath)
                || !Objects.equals(entity.getRegistrationNumber(), nextRegistrationNumber)
                || !Objects.equals(entity.getDisplayName(), summary.displayName())
                || !Objects.equals(entity.getCity(), summary.city())
                || !Objects.equals(entity.getArea(), summary.area())
                || !Objects.equals(entity.getState(), summary.state())
                || !Objects.equals(entity.getCountry(), summary.country())
                || !Objects.equals(entity.getPublicPhone(), summary.publicPhone())
                || !Objects.equals(entity.getPublicEmail(), summary.publicEmail())
                || !Objects.equals(entity.getWebsite(), summary.website())
                || !Objects.equals(entity.getWhatsappNumber(), summary.whatsappNumber())
                || !Objects.equals(entity.getEstablishedYear(), summary.establishedYear())
                || !Objects.equals(entity.getContentStatus(), nextContentStatus)
                || !Objects.equals(entity.getReadinessStatus(), nextReadinessStatus)
                || entity.getCompletenessPercentage() != readiness.completenessPercentage();
        if (!changed) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        entity.update(
                nextContentStatus,
                nextReadinessStatus,
                readiness.completenessPercentage(),
                entity.getTenantConsentStatus(),
                entity.getPublicProfileStatus(),
                entity.getCurrentVersion(),
                summary.displayName(),
                canonicalSlug,
                summary.city(),
                summary.area(),
                summary.state(),
                summary.country(),
                summary.publicPhone(),
                summary.publicEmail(),
                summary.website(),
                summary.whatsappNumber(),
                nextRegistrationNumber,
                summary.establishedYear(),
                now,
                providerAccountId,
                nextPublicPath,
                hydratedContentJson,
                entity.getSourceAttributionJson(),
                toJson(readiness)
        );
        drafts.save(entity);
    }

    private String repairedRegistrationNumber(DiscoverPublicProfileDraftEntity entity, String publicProfileReference, PublicProfileLifecycleRecord lifecycle) {
        String current = entity.getRegistrationNumber();
        if (!hasText(current)) {
            return null;
        }
        String normalized = current.trim();
        if (Objects.equals(normalized, publicProfileReference)
                || (lifecycle != null && Objects.equals(normalized, lifecycle.sourceEntityReference()))) {
            return null;
        }
        return normalized;
    }

    private SummaryValues summarize(Map<String, Object> content, PublicProfileLifecycleRecord lifecycle, String fallbackSlug) {
        Map<String, Object> about = asSection(content.get("about"));
        Map<String, Object> contact = asSection(content.get("contact"));
        Map<String, Object> seo = asSection(content.get("seo"));
        return new SummaryValues(
                firstNonBlank(stringValue(about, "displayName"), lifecycle == null ? null : lifecycle.displayName()),
                firstNonBlank(stringValue(seo, "slug"), fallbackSlug),
                firstNonBlank(stringValue(contact, "city"), lifecycle == null ? null : lifecycle.city()),
                stringValue(contact, "area"),
                firstNonBlank(stringValue(contact, "state"), null),
                firstNonBlank(stringValue(contact, "country"), null),
                firstNonBlank(stringValue(contact, "publicPhone"), null),
                firstNonBlank(stringValue(contact, "publicEmail"), null),
                stringValue(contact, "website"),
                stringValue(contact, "whatsappNumber"),
                firstNonBlank(stringValue(about, "registrationNumber"), null),
                resolveEstablishedYear(about)
        );
    }

    private int recommendedScore(Map<String, Object> content) {
        int present = 0;
        if (stringList(asSection(content.get("media")), "gallery").size() > 0) {
            present++;
        }
        if (hasText(stringValue(asSection(content.get("about")), "establishedYear"))) {
            present++;
        }
        if (stringList(asSection(content.get("facilities")), "items").size() > 0) {
            present++;
        }
        if (stringList(asSection(content.get("languages")), "items").size() > 0) {
            present++;
        }
        Map<String, Object> fees = asSection(content.get("fees"));
        if (hasText(stringValue(fees, "currency")) && (hasText(stringValue(fees, "inClinic")) || hasText(stringValue(fees, "video")) || hasText(stringValue(fees, "homeVisit")) || hasText(stringValue(fees, "emergency")))) {
            present++;
        }
        if (hasText(stringValue(asSection(content.get("contact")), "website"))) {
            present++;
        }
        if (hasText(stringValue(asSection(content.get("contact")), "whatsappNumber"))) {
            present++;
        }
        if (hasText(stringValue(asSection(content.get("seo")), "metaTitle"))) {
            present++;
        }
        if (hasText(stringValue(asSection(content.get("seo")), "metaDescription"))) {
            present++;
        }
        return (int) Math.round((present * 100d) / Math.max(1, RECOMMENDED_FIELDS.size()));
    }

    private PublicProfileDraftWorkspaceRecord reconcileReadiness(DiscoverPublicProfileDraftEntity entity, UUID providerAccountId, boolean persistIfChanged) {
        Map<String, Object> content = contentMap(entity);
        PublicProfileDraftReadinessRecord readiness = evaluateReadiness(content, providerAccountId, false, entity.getCurrentVersion());
        String nextContentStatus = readiness.ready() ? "READY_FOR_REVIEW" : "DRAFT_INCOMPLETE";
        String nextReadinessStatus = readiness.ready() ? "READY" : "INCOMPLETE";
        boolean changed = !Objects.equals(entity.getContentStatus(), nextContentStatus)
                || !Objects.equals(entity.getReadinessStatus(), nextReadinessStatus)
                || entity.getCompletenessPercentage() != readiness.completenessPercentage()
                || !Objects.equals(entity.getReadinessJson(), toJson(readiness));
        if (persistIfChanged && changed) {
            OffsetDateTime now = OffsetDateTime.now();
            entity.update(
                    nextContentStatus,
                    nextReadinessStatus,
                    readiness.completenessPercentage(),
                    entity.getTenantConsentStatus(),
                    entity.getPublicProfileStatus(),
                    entity.getCurrentVersion(),
                    entity.getDisplayName(),
                    entity.getCanonicalSlug(),
                    entity.getCity(),
                    entity.getArea(),
                    entity.getState(),
                    entity.getCountry(),
                    entity.getPublicPhone(),
                    entity.getPublicEmail(),
                    entity.getWebsite(),
                    entity.getWhatsappNumber(),
                    entity.getRegistrationNumber(),
                    entity.getEstablishedYear(),
                    now,
                    providerAccountId,
                    entity.getPublicPath(),
                    entity.getContentJson(),
                    entity.getSourceAttributionJson(),
                    toJson(readiness)
            );
            drafts.save(entity);
        }
        return toWorkspace(entity);
    }

    private String publicPath(ProviderType providerType, String slug) {
        return "/discover/" + switch (providerType) {
            case INDIVIDUAL_DOCTOR -> "doctors";
            case CLINIC -> "clinics";
            case HOSPITAL -> "hospitals";
        } + "/" + slug;
    }

    private Map<String, Object> sectionMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private Map<String, Object> contentMap(DiscoverPublicProfileDraftEntity entity) {
        return readMap(entity.getContentJson()).orElseGet(LinkedHashMap::new);
    }

    private Map<String, PublicProfileDraftFieldSourceRecord> sourceMap(DiscoverPublicProfileDraftEntity entity) {
        return readSourceMap(entity.getSourceAttributionJson()).orElseGet(LinkedHashMap::new);
    }

    private PublicProfileDraftReadinessRecord readReadiness(String json) {
        return readReadinessRecord(json).orElse(null);
    }

    private Optional<Map<String, PublicProfileDraftFieldSourceRecord>> readSourceMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            JavaType type = objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, PublicProfileDraftFieldSourceRecord.class);
            @SuppressWarnings("unchecked")
            Map<String, PublicProfileDraftFieldSourceRecord> value = objectMapper.readValue(json, type);
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<PublicProfileDraftReadinessRecord> readReadinessRecord(String json) {
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(json, PublicProfileDraftReadinessRecord.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<Map<String, Object>> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
            }));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String mediaReference(String publicProfileReference, ProviderDocumentType mediaType, byte[] bytes, String originalFilename, String contentType) {
        String hash = mediaHash(bytes);
        String seed = String.join("|",
                normalizeReference(publicProfileReference),
                mediaType.name(),
                hash,
                normalizeMediaContentType(contentType, originalFilename, bytes)
        );
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String storageKey(String publicProfileReference, String mediaReference) {
        return "discover/public-profile-drafts/" + normalizeReference(publicProfileReference) + "/media/" + mediaReference;
    }

    private String sanitizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        String fileName = originalFilename.trim().replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        fileName = fileName.replaceAll("[\\r\\n\\t]", " ");
        fileName = fileName.replaceAll("[^A-Za-z0-9._ -]", "_");
        return fileName.isBlank() ? null : fileName;
    }

    private String mediaHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash media bytes", ex);
        }
    }

    private String normalizeMediaContentType(String contentType, String originalFilename, byte[] bytes) {
        String detected = detectMediaContentType(bytes);
        String normalized = normalizeNullable(contentType);
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (StringUtils.hasText(detected)) {
            if (StringUtils.hasText(normalized) && !detected.equals(normalized)) {
                throw new ProviderOwnershipConflictException("public_profile_media_type_not_supported", "Uploaded file type does not match its actual image content.");
            }
            return detected;
        }
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        String lower = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    private String detectMediaContentType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String normalizeReference(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<String, Object> normalizeContent(String sectionKey, Map<String, Object> content, ProviderType publicProfileType) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (content != null) {
            content.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
        }
        if ("seo".equalsIgnoreCase(sectionKey) && !normalized.containsKey("canonicalPublicPath") && hasText(stringValue(normalized, "slug"))) {
            normalized.put("canonicalPublicPath", publicPath(publicProfileType == null ? ProviderType.CLINIC : publicProfileType, stringValue(normalized, "slug")));
        }
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, nested) -> normalized.put(String.valueOf(key), normalizeValue(nested)));
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizeValue).toList();
        }
        return value;
    }

    private Map<String, Object> asSection(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, nested) -> normalized.put(String.valueOf(key), nested));
            return normalized;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> asSection(item))
                    .toList();
        }
        return List.of();
    }

    private List<String> stringList(Map<String, Object> section, String key) {
        Object value = section.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item == null ? null : String.valueOf(item).trim())
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            return List.of(string.trim());
        }
        return List.of();
    }

    private String stringValue(Map<String, Object> section, String key) {
        Object value = section.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            try {
                return Long.parseLong(string.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String slugify(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "")
                : null;
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ProviderOwnershipConflictException("public_profile_edit_not_allowed", "Public profile reference is required.");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (Exception ex) {
            throw new ProviderOwnershipConflictException("public_profile_edit_not_allowed", "Public profile reference is invalid.");
        }
    }

    private String fieldPath(String sectionKey, String field) {
        return sectionKey + "." + field;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize public profile draft", ex);
        }
    }

    private Integer parseInteger(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer resolveEstablishedYear(Map<String, Object> about) {
        Integer explicitYear = parseInteger(stringValue(about, "establishedYear"));
        if (explicitYear != null && explicitYear >= 1900 && explicitYear <= 2100) {
            return explicitYear;
        }
        return yearFromRegistration(stringValue(about, "registrationNumber"));
    }

    private Integer yearFromRegistration(String value) {
        if (!hasText(value)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return parseInteger(matcher.group(1));
    }

    private record SummaryValues(
            String displayName,
            String canonicalSlug,
            String city,
            String area,
            String state,
            String country,
            String publicPhone,
            String publicEmail,
            String website,
            String whatsappNumber,
            String registrationNumber,
            Integer establishedYear
    ) {
    }

    private record CanonicalDraftState(
            String contentStatus,
            String readinessStatus,
            int completenessPercentage,
            String tenantConsentStatus,
            PublicProfileDraftReadinessRecord readiness,
            List<String> allowedActions
    ) {
    }
}
