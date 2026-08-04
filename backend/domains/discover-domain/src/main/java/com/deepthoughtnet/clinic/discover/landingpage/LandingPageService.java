package com.deepthoughtnet.clinic.discover.landingpage;

import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageCompareRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageRevertRequest;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageSectionRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageSnapshotRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageTemplateRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageThemeRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageUpdateRequest;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageVersionRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.landingpage.db.LandingPageEntity;
import com.deepthoughtnet.clinic.discover.landingpage.db.LandingPageRepository;
import com.deepthoughtnet.clinic.discover.landingpage.db.LandingPageTemplateEntity;
import com.deepthoughtnet.clinic.discover.landingpage.db.LandingPageTemplateRepository;
import com.deepthoughtnet.clinic.discover.landingpage.db.LandingPageVersionEntity;
import com.deepthoughtnet.clinic.discover.landingpage.db.LandingPageVersionRepository;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderApplicationRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicationReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LandingPageService {
    private static final int TEMPLATE_VERSION = 1;

    private final ProviderOnboardingService onboardingService;
    private final ProviderPublicProfileService publicProfileService;
    private final LandingPageRepository landingPages;
    private final LandingPageVersionRepository versions;
    private final LandingPageTemplateRepository templates;
    private final ObjectMapper objectMapper;

    public LandingPageService(
            ProviderOnboardingService onboardingService,
            ProviderPublicProfileService publicProfileService,
            LandingPageRepository landingPages,
            LandingPageVersionRepository versions,
            LandingPageTemplateRepository templates,
            ObjectMapper objectMapper
    ) {
        this.onboardingService = onboardingService;
        this.publicProfileService = publicProfileService;
        this.landingPages = landingPages;
        this.versions = versions;
        this.templates = templates;
        this.objectMapper = objectMapper.copy().findAndRegisterModules().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Transactional
    public LandingPageWorkspaceRecord getDraft(String token) {
        ProviderApplicationRecord application = requireEditableProvider(token);
        return workspaceFor(application);
    }

    @Transactional
    public LandingPageWorkspaceRecord preview(String token) {
        ProviderApplicationRecord application = requireEditableProvider(token);
        return workspaceFor(application);
    }

    @Transactional
    public LandingPageWorkspaceRecord update(String token, LandingPageUpdateRequest request) {
        ProviderApplicationRecord application = requireEditableProvider(token);
        PublicProviderProfileDetailRecord profile = resolveProfile(application);
        LandingPageEntity entity = loadOrCreateEntity(application, profile);
        if (request.version() != null && request.version() != entity.getRowVersion()) {
            throw new IllegalStateException("landing page draft has changed; reload before saving");
        }

        LandingPageTemplateEntity template = selectTemplate(application.providerType(), request.templateKey());
        LandingPageSnapshotRecord updatedDraft = normalizeSnapshot(
                template,
                application.providerType(),
                request.theme(),
                request.sections(),
                currentDraft(entity, template)
        );
        entity.updateDraft(template.getTemplateKey(), toJson(updatedDraft), application.id(), application.referenceNumber(), application.providerType(), application.displayName());
        landingPages.save(entity);
        return workspaceFor(application);
    }

    @Transactional
    public LandingPageWorkspaceRecord publish(String token) {
        ProviderApplicationRecord application = requireEditableProvider(token);
        PublicProviderProfileDetailRecord profile = resolveProfile(application);
        LandingPageEntity entity = loadOrCreateEntity(application, profile);
        LandingPageSnapshotRecord draft = readSnapshot(entity.getDraftSnapshotJson()).orElseGet(() -> defaultSnapshot(selectTemplate(application.providerType(), entity.getTemplateKey()), application));
        int nextVersion = versions.findFirstByProviderIdOrderByVersionNumberDesc(application.id()).map(LandingPageVersionEntity::getVersionNumber).orElse(0) + 1;
        String snapshotJson = toJson(draft);
        String snapshotHash = digest(snapshotJson);
        LandingPageVersionEntity version = versions.save(LandingPageVersionEntity.create(
                application.id(),
                nextVersion,
                entity.getTemplateKey(),
                TEMPLATE_VERSION,
                "PUBLISHED",
                "Published landing page",
                snapshotHash,
                snapshotJson,
                OffsetDateTime.now()
        ));
        entity.publish(version.getId(), nextVersion, snapshotJson, application.providerType(), application.displayName());
        landingPages.save(entity);
        return workspaceFor(application);
    }

    @Transactional
    public LandingPageWorkspaceRecord revert(String token, LandingPageRevertRequest request) {
        ProviderApplicationRecord application = requireEditableProvider(token);
        PublicProviderProfileDetailRecord profile = resolveProfile(application);
        LandingPageEntity entity = loadOrCreateEntity(application, profile);
        LandingPageVersionEntity version = versions.findByProviderIdAndVersionNumber(application.id(), request.versionNumber())
                .orElseThrow(() -> new IllegalStateException("landing page version not found"));
        entity.updateDraft(version.getTemplateKey(), version.getSnapshotJson(), application.id(), application.referenceNumber(), application.providerType(), application.displayName());
        landingPages.save(entity);
        return workspaceFor(application);
    }

    @Transactional(readOnly = true)
    public List<LandingPageVersionRecord> listVersions(String token) {
        ProviderApplicationRecord application = requireEditableProvider(token);
        return versions.findByProviderIdOrderByVersionNumberDesc(application.id()).stream().map(this::toVersionRecord).toList();
    }

    @Transactional(readOnly = true)
    public LandingPageCompareRecord compareVersions(String token, int leftVersion, int rightVersion) {
        ProviderApplicationRecord application = requireEditableProvider(token);
        LandingPageVersionEntity left = versions.findByProviderIdAndVersionNumber(application.id(), leftVersion)
                .orElseThrow(() -> new IllegalStateException("left version not found"));
        LandingPageVersionEntity right = versions.findByProviderIdAndVersionNumber(application.id(), rightVersion)
                .orElseThrow(() -> new IllegalStateException("right version not found"));
        LandingPageSnapshotRecord leftSnapshot = readSnapshot(left.getSnapshotJson()).orElseThrow();
        LandingPageSnapshotRecord rightSnapshot = readSnapshot(right.getSnapshotJson()).orElseThrow();
        List<String> leftKeys = sectionKeys(leftSnapshot);
        List<String> rightKeys = sectionKeys(rightSnapshot);
        List<String> added = rightKeys.stream().filter(key -> !leftKeys.contains(key)).toList();
        List<String> removed = leftKeys.stream().filter(key -> !rightKeys.contains(key)).toList();
        List<String> changed = rightSnapshot.sections().stream()
                .filter(section -> leftSnapshot.sections().stream()
                        .filter(candidate -> candidate.key().equals(section.key()))
                        .findFirst()
                        .map(candidate -> !Objects.equals(candidate.title(), section.title())
                                || !Objects.equals(candidate.description(), section.description())
                                || candidate.enabled() != section.enabled()
                                || !Objects.equals(candidate.visibilityRule(), section.visibilityRule())
                                || !Objects.equals(candidate.content(), section.content()))
                        .orElse(true))
                .map(LandingPageSectionRecord::key)
                .toList();
        boolean orderChanged = !leftKeys.equals(rightKeys);
        return new LandingPageCompareRecord(leftVersion, rightVersion, !Objects.equals(left.getTemplateKey(), right.getTemplateKey()), !Objects.equals(leftSnapshot.theme(), rightSnapshot.theme()), orderChanged, added, removed, changed);
    }

    @Transactional(readOnly = true)
    public Optional<LandingPageWorkspaceRecord> findPublicBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return Optional.empty();
        }
        Optional<PublicProviderProfileDetailRecord> profile = publicProfileService.findBySlug(slug.trim());
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(publicWorkspace(profile.get(), false));
    }

    private LandingPageWorkspaceRecord workspaceFor(ProviderApplicationRecord application) {
        PublicProviderProfileDetailRecord profile = resolveProfile(application);
        PublicationReadinessRecord publicationReadiness = publicProfileService.publicationReadiness(profile.providerId());
        LandingPageEntity entity = loadOrCreateEntity(application, profile);
        LandingPageTemplateEntity draftTemplate = selectTemplate(application.providerType(), entity.getTemplateKey());
        LandingPageSnapshotRecord draft = readSnapshot(entity.getDraftSnapshotJson()).orElseGet(() -> defaultSnapshot(draftTemplate, application));
        LandingPageSnapshotRecord published = readSnapshot(entity.getPublishedSnapshotJson()).orElse(null);
        List<LandingPageTemplateRecord> availableTemplates = templates.findByProviderTypeAndActiveTrueOrderBySortOrderAsc(application.providerType()).stream()
                .map(this::toTemplateRecord)
                .toList();
        List<LandingPageVersionRecord> history = versions.findByProviderIdOrderByVersionNumberDesc(application.id()).stream().map(this::toVersionRecord).toList();
        return new LandingPageWorkspaceRecord(
                application.id(),
                application.providerType(),
                application.status(),
                application.displayName(),
                profile.canonicalSlug(),
                profile.publicPath(),
                true,
                published != null,
                pageMode(application.status(), publicationReadiness, published != null),
                publicationReadiness,
                allowedActions(application.status(), publicationReadiness, published != null),
                Math.toIntExact(entity.getRowVersion()),
                entity.getPublishedVersionNumber(),
                entity.getPublishedAt(),
                draft,
                published,
                profile,
                availableTemplates,
                history
        );
    }

    private LandingPageWorkspaceRecord publicWorkspace(PublicProviderProfileDetailRecord profile, boolean draftMode) {
        LandingPageEntity entity = landingPages.findByProviderId(profile.providerId()).orElse(null);
        LandingPageTemplateEntity template = selectTemplate(profile.providerType(), entity == null ? null : entity.getTemplateKey());
        LandingPageSnapshotRecord published = entity == null ? defaultSnapshot(template, profile) : readSnapshot(entity.getPublishedSnapshotJson()).orElseGet(() -> defaultSnapshot(template, profile));
        PublicationReadinessRecord publicationReadiness = publicProfileService.publicationReadiness(profile.providerId());
        List<LandingPageVersionRecord> history = entity == null ? List.of() : versions.findByProviderIdOrderByVersionNumberDesc(profile.providerId()).stream().map(this::toVersionRecord).toList();
        return new LandingPageWorkspaceRecord(
                profile.providerId(),
                profile.providerType(),
                ProviderLifecycleStatus.PUBLISHED,
                profile.displayName(),
                profile.canonicalSlug(),
                profile.publicPath(),
                false,
                true,
                "PUBLIC_PROFILE_PUBLISHED",
                publicationReadiness,
                List.of("VIEW_PUBLIC_PROFILE"),
                entity == null ? 0 : Math.toIntExact(entity.getRowVersion()),
                entity == null ? null : entity.getPublishedVersionNumber(),
                entity == null ? null : entity.getPublishedAt(),
                published,
                published,
                profile,
                List.of(toTemplateRecord(template)),
                history
        );
    }

    private ProviderApplicationRecord requireEditableProvider(String token) {
        ProviderApplicationRecord application = onboardingService.getMe(token);
        if (application.status() != ProviderLifecycleStatus.APPROVED && application.status() != ProviderLifecycleStatus.PUBLISHED) {
            throw new IllegalStateException("landing pages are available after approval");
        }
        return application;
    }

    private LandingPageEntity loadOrCreateEntity(ProviderApplicationRecord application, PublicProviderProfileDetailRecord profile) {
        return landingPages.findByProviderId(application.id()).orElseGet(() -> {
            LandingPageTemplateEntity template = selectTemplate(application.providerType(), null);
            LandingPageSnapshotRecord defaultSnapshot = defaultSnapshot(template, application);
            String canonicalSlug = profile == null ? slugFor(application) : profile.canonicalSlug();
            LandingPageEntity entity = LandingPageEntity.create(
                    application.id(),
                    application.providerType(),
                    canonicalSlug,
                    template.getTemplateKey(),
                    toJson(defaultSnapshot),
                    application.referenceNumber(),
                    application.displayName()
            );
            return landingPages.save(entity);
        });
    }

    private LandingPageTemplateEntity selectTemplate(com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType, String templateKey) {
        if (StringUtils.hasText(templateKey)) {
            LandingPageTemplateEntity template = templates.findById(templateKey).orElseThrow(() -> new IllegalStateException("landing page template not found"));
            if (template.getProviderType() != providerType) {
                throw new IllegalStateException("landing page template is not available for this provider type");
            }
            return template;
        }
        return templates.findFirstByProviderTypeAndActiveTrueOrderBySortOrderAsc(providerType)
                .orElseThrow(() -> new IllegalStateException("no landing page templates configured"));
    }

    private LandingPageSnapshotRecord currentDraft(LandingPageEntity entity, LandingPageTemplateEntity template) {
        return readSnapshot(entity.getDraftSnapshotJson()).orElseGet(() -> defaultSnapshot(template, entity.getProviderType(), entity.getCanonicalSlug()));
    }

    private LandingPageSnapshotRecord defaultSnapshot(LandingPageTemplateEntity template, ProviderApplicationRecord application) {
        return defaultSnapshot(template, application.providerType(), resolveProfile(application).canonicalSlug());
    }

    private LandingPageSnapshotRecord defaultSnapshot(LandingPageTemplateEntity template, PublicProviderProfileDetailRecord profile) {
        return defaultSnapshot(template, profile.providerType(), profile.canonicalSlug());
    }

    private LandingPageSnapshotRecord defaultSnapshot(LandingPageTemplateEntity template, com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType, String slug) {
        List<LandingPageSectionRecord> sections = readSections(template.getDefaultSectionsJson()).orElseGet(List::of);
        LandingPageThemeRecord theme = readTheme(template.getDefaultThemeJson()).orElse(defaultTheme());
        return new LandingPageSnapshotRecord(template.getTemplateKey(), template.getTemplateVersion(), theme, sections.stream()
                .sorted(Comparator.comparingInt(LandingPageSectionRecord::displayOrder))
                .map(section -> normalizeSection(section, sections))
                .toList());
    }

    private PublicProviderProfileDetailRecord resolveProfile(ProviderApplicationRecord application) {
        return publicProfileService.findByProviderId(application.id()).orElseGet(() -> fallbackProfile(application));
    }

    private PublicProviderProfileDetailRecord fallbackProfile(ProviderApplicationRecord application) {
        List<com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.LocationRecord> applicationLocations = application.locations() == null ? List.of() : application.locations();
        List<com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.DocumentRecord> applicationDocuments = application.documents() == null ? List.of() : application.documents();
        List<com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ServiceRecord> applicationServices = application.services() == null ? List.of() : application.services();
        List<String> specialities = application.specialities() == null ? List.of() : application.specialities();
        List<String> subSpecialities = application.subSpecialities() == null ? List.of() : application.subSpecialities();
        List<String> languages = application.languages() == null ? List.of() : application.languages();
        List<String> departments = application.departments() == null ? List.of() : application.departments();
        List<String> facilities = application.facilities() == null ? List.of() : application.facilities();
        List<PublicProviderLocationSnapshot> locations = applicationLocations.stream()
                .map(location -> new PublicProviderLocationSnapshot(
                        location.label(),
                        location.address(),
                        location.city(),
                        location.state(),
                        location.country(),
                        location.pinCode(),
                        location.workingHours(),
                        location.parkingAvailable(),
                        location.accessibilityAvailable(),
                        location.latitude(),
                        location.longitude()
                ))
                .toList();
        List<PublicProviderGalleryImageSnapshot> gallery = applicationDocuments.stream()
                .filter(document -> document.documentType() == com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType.GALLERY_IMAGE)
                .map(document -> new PublicProviderGalleryImageSnapshot(document.id(), document.originalFilename()))
                .toList();
        List<String> galleryImageUrls = gallery.stream()
                .map(image -> publicProfileService.resolveDocumentUrl(image.documentId()).orElse(null))
                .filter(StringUtils::hasText)
                .toList();
        String canonicalSlug = slugFor(application);
        String publicPath = publicPath(application.providerType(), canonicalSlug);
        var branding = application.branding();
        String logoUrl = publicProfileService.resolveDocumentUrl(branding == null ? null : branding.logoDocumentId()).orElse(null);
        String coverUrl = publicProfileService.resolveDocumentUrl(branding == null ? null : branding.coverImageDocumentId()).orElse(null);
        String doctorPhotoUrl = publicProfileService.resolveDocumentUrl(branding == null ? null : branding.doctorPhotoDocumentId()).orElse(null);
        String displayName = firstNonBlank(application.displayName(), application.legalName(), application.email());
        String legalName = firstNonBlank(application.legalName(), displayName);
        String summary = firstNonBlank(application.biography(), branding == null ? null : branding.tagline());
        String city = locations.isEmpty() ? null : locations.get(0).city();
        String area = locations.isEmpty() ? null : locations.get(0).label();
        String state = locations.isEmpty() ? null : locations.get(0).state();
        String country = locations.isEmpty() ? null : locations.get(0).country();
        return new PublicProviderProfileDetailRecord(
                application.id(),
                application.providerType(),
                application.referenceNumber(),
                canonicalSlug,
                publicPath,
                displayName,
                legalName,
                summary,
                summary,
                application.biography(),
                application.qualification(),
                application.medicalCouncil(),
                application.yearsOfExperience(),
                application.consultationFee(),
                application.appointmentDurationMinutes(),
                application.onlineConsultation(),
                languages,
                specialities,
                subSpecialities,
                applicationServices.stream().filter(com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ServiceRecord::enabled).map(com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ServiceRecord::label).toList(),
                departments,
                facilities,
                consultationModes(application),
                locations,
                gallery,
                galleryImageUrls,
                logoUrl,
                coverUrl,
                doctorPhotoUrl,
                application.phone(),
                application.email(),
                application.website(),
                city,
                area,
                state,
                country,
                first(application.specialities()),
                application.ownership(),
                application.hospitalType(),
                application.medicalDirector(),
                application.beds(),
                application.emergencyAvailable(),
                application.onlineConsultation() ? "ONLINE_BOOKING" : "CALL_TO_BOOK",
                true,
                OffsetDateTime.now(),
                0,
                canonicalSlug,
                null,
                false
        );
    }

    private String publicPath(com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType, String slug) {
        return "/discover/" + switch (providerType) {
            case INDIVIDUAL_DOCTOR -> "doctors";
            case CLINIC -> "clinics";
            case HOSPITAL -> "hospitals";
        } + "/" + slug;
    }

    private String slugFor(ProviderApplicationRecord application) {
        String base = firstNonBlank(application.displayName(), application.legalName(), application.referenceNumber());
        if (application.providerType() == com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType.INDIVIDUAL_DOCTOR && !normalize(base).startsWith("dr")) {
            base = "Dr " + base;
        }
        return slugify(base);
    }

    private LandingPageSnapshotRecord normalizeSnapshot(
            LandingPageTemplateEntity template,
            com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType,
            LandingPageThemeRecord theme,
            List<LandingPageSectionRecord> sections,
            LandingPageSnapshotRecord current
    ) {
        LandingPageThemeRecord normalizedTheme = theme == null ? current.theme() : normalizeTheme(theme);
        List<LandingPageSectionRecord> normalizedSections = sections == null || sections.isEmpty()
                ? current.sections()
                : sections.stream()
                .map(section -> normalizeSection(section, current.sections()))
                .sorted(Comparator.comparingInt(LandingPageSectionRecord::displayOrder))
                .toList();
        return new LandingPageSnapshotRecord(template.getTemplateKey(), template.getTemplateVersion(), normalizedTheme, normalizedSections);
    }

    private LandingPageSectionRecord normalizeSection(LandingPageSectionRecord section, List<LandingPageSectionRecord> existing) {
        if (section == null) {
            return null;
        }
        Map<String, Object> content = section.content() == null ? Map.of() : new LinkedHashMap<>(section.content());
        return new LandingPageSectionRecord(
                section.key(),
                section.enabled(),
                section.displayOrder(),
                StringUtils.hasText(section.title()) ? section.title().trim() : titleFor(section.key()),
                StringUtils.hasText(section.description()) ? section.description().trim() : null,
                StringUtils.hasText(section.visibilityRule()) ? section.visibilityRule().trim() : "PUBLIC",
                content
        );
    }

    private LandingPageThemeRecord normalizeTheme(LandingPageThemeRecord theme) {
        return new LandingPageThemeRecord(
                normalizeColor(theme.primaryColor(), "#0F8B8D"),
                normalizeColor(theme.accentColor(), "#1E88E5"),
                StringUtils.hasText(theme.typographyPreset()) ? theme.typographyPreset().trim() : "clean",
                StringUtils.hasText(theme.buttonStyle()) ? theme.buttonStyle().trim() : "solid",
                StringUtils.hasText(theme.borderRadiusPreset()) ? theme.borderRadiusPreset().trim() : "medium"
        );
    }

    private LandingPageThemeRecord defaultTheme() {
        return new LandingPageThemeRecord("#0F8B8D", "#1E88E5", "clean", "solid", "medium");
    }

    private String normalizeColor(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.matches("^#[0-9A-Fa-f]{6}$") ? trimmed.toUpperCase(Locale.ROOT) : fallback;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String slugify(String value) {
        return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String first(String... values) {
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

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : first(values.toArray(String[]::new));
    }

    private String firstNonBlank(String... values) {
        return first(values);
    }

    private List<String> consultationModes(ProviderApplicationRecord application) {
        List<String> modes = new ArrayList<>();
        if (application.onlineConsultation()) {
            modes.add("Online consultation");
        }
        modes.add("In-person consultation");
        if (application.appointmentDurationMinutes() != null) {
            modes.add("Appointment duration " + application.appointmentDurationMinutes() + " mins");
        }
        return modes.stream().distinct().toList();
    }

    private String pageMode(ProviderLifecycleStatus status, PublicationReadinessRecord publicationReadiness, boolean published) {
        if (published || status == ProviderLifecycleStatus.PUBLISHED) {
            return "PUBLIC_PROFILE_PUBLISHED";
        }
        if (status == ProviderLifecycleStatus.APPROVED) {
            return publicationReadiness != null && publicationReadiness.ready() ? "PUBLIC_PROFILE_READY" : "PUBLIC_PROFILE_DRAFT";
        }
        return "PUBLIC_PROFILE_READ_ONLY";
    }

    private List<String> allowedActions(ProviderLifecycleStatus status, PublicationReadinessRecord publicationReadiness, boolean published) {
        List<String> actions = new ArrayList<>();
        if (published) {
            actions.add("VIEW_PUBLIC_PROFILE");
            actions.add("EDIT_PUBLIC_PROFILE");
            return actions;
        }
        if (status == ProviderLifecycleStatus.APPROVED) {
            actions.add("PREVIEW_PUBLIC_PROFILE");
            actions.add("EDIT_PUBLIC_PROFILE");
            if (publicationReadiness != null && publicationReadiness.ready()) {
                actions.add("SUBMIT_FOR_PUBLICATION");
            }
            return actions;
        }
        actions.add("VIEW_PUBLIC_PROFILE");
        return actions;
    }

    private String titleFor(String key) {
        return switch (key == null ? "" : key.trim().toUpperCase(Locale.ROOT)) {
            case "HERO" -> "Welcome";
            case "ABOUT" -> "About";
            case "SERVICES" -> "Services";
            case "DOCTORS" -> "Our Doctors";
            case "DEPARTMENTS" -> "Departments";
            case "FACILITIES" -> "Facilities";
            case "CONSULTATION_MODES" -> "Consultation Modes";
            case "WORKING_HOURS" -> "Working Hours";
            case "GALLERY" -> "Gallery";
            case "INSURANCE" -> "Insurance Accepted";
            case "AWARDS" -> "Awards & Certifications";
            case "FAQ" -> "Frequently Asked Questions";
            case "CONTACT" -> "Contact & Map";
            case "CTA" -> "Book Appointment";
            default -> key;
        };
    }

    private LandingPageVersionRecord toVersionRecord(LandingPageVersionEntity entity) {
        LandingPageSnapshotRecord snapshot = readSnapshot(entity.getSnapshotJson()).orElseThrow();
        return new LandingPageVersionRecord(
                entity.getId(),
                entity.getVersionNumber(),
                entity.getTemplateKey(),
                entity.getTemplateVersion(),
                entity.getChangeSummary(),
                entity.getVersionKind(),
                entity.getPublishedAt(),
                sectionKeys(snapshot),
                snapshot.theme()
        );
    }

    private LandingPageTemplateRecord toTemplateRecord(LandingPageTemplateEntity entity) {
        return new LandingPageTemplateRecord(
                entity.getTemplateKey(),
                entity.getTemplateName(),
                entity.getProviderType(),
                entity.getTemplateVersion(),
                entity.getDescription(),
                readStringList(entity.getSupportedSectionsJson()).orElse(List.of()),
                readSections(entity.getDefaultSectionsJson()).orElse(List.of()),
                readTheme(entity.getDefaultThemeJson()).orElse(defaultTheme())
        );
    }

    private List<String> sectionKeys(LandingPageSnapshotRecord snapshot) {
        return snapshot.sections().stream().sorted(Comparator.comparingInt(LandingPageSectionRecord::displayOrder)).map(LandingPageSectionRecord::key).toList();
    }

    private Optional<LandingPageSnapshotRecord> readSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, LandingPageSnapshotRecord.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("unable to read landing page snapshot", ex);
        }
    }

    private Optional<List<LandingPageSectionRecord>> readSections(String json) {
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Arrays.asList(objectMapper.readValue(json, LandingPageSectionRecord[].class)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("unable to read landing page sections", ex);
        }
    }

    private Optional<LandingPageThemeRecord> readTheme(String json) {
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, LandingPageThemeRecord.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("unable to read landing page theme", ex);
        }
    }

    private Optional<List<String>> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Arrays.asList(objectMapper.readValue(json, String[].class)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("unable to read landing page list", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("unable to serialise landing page snapshot", ex);
        }
    }

    private String digest(String snapshotJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(snapshotJson.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("unable to hash landing page snapshot", ex);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            builder.append(String.format("%02x", aByte));
        }
        return builder.toString();
    }
}
