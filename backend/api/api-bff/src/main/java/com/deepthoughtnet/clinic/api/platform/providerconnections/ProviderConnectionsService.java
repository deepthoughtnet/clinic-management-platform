package com.deepthoughtnet.clinic.api.platform.providerconnections;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.EvidenceStrength;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchConfidence;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchMethod;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderSummary;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderMatchEvidence;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderFactsSnapshot;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ReconciliationResult;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.port.DiscoverCatalogPort;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.ProviderConnectionSuggestionRejectionEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.ProviderConnectionSuggestionRejectionRepository;
import com.deepthoughtnet.clinic.platform.providerintegration.model.PublicClinicPlatformLinkUpsertRequest;
import com.deepthoughtnet.clinic.platform.providerintegration.model.PublicDoctorPracticePlatformLinkUpsertRequest;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.DisputeRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicationReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationQueueRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderConnectionsService {
    private final DiscoverCatalogPort discoverCatalogPort;
    private final LocalHealthcareProviderFactsAdapter healthcareFactsAdapter;
    private final ProviderLinkingService providerLinkingService;
    private final ProviderPublicProfileService publicProfileService;
    private final ProviderPublicProfileModerationService moderationService;
    private final ProviderPublicProfileDraftService draftService;
    private final PlatformTenantManagementService tenantManagementService;
    private final ClinicProfileService clinicProfileService;
    private final DoctorProfileService doctorProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final ProviderOwnershipService providerOwnershipService;
    private final AuditEventQueryService auditEventQueryService;
    private final ProviderConnectionSuggestionRejectionRepository suggestionRejectionRepository;
    private final ObjectMapper objectMapper;

    public ProviderConnectionsService(
            DiscoverCatalogPort discoverCatalogPort,
            LocalHealthcareProviderFactsAdapter healthcareFactsAdapter,
            ProviderLinkingService providerLinkingService,
            ProviderPublicProfileService publicProfileService,
            ProviderPublicProfileModerationService moderationService,
            ProviderPublicProfileDraftService draftService,
            PlatformTenantManagementService tenantManagementService,
            ClinicProfileService clinicProfileService,
            DoctorProfileService doctorProfileService,
            TenantUserManagementService tenantUserManagementService,
            ProviderOwnershipService providerOwnershipService,
            AuditEventQueryService auditEventQueryService,
            ProviderConnectionSuggestionRejectionRepository suggestionRejectionRepository,
            ObjectMapper objectMapper
    ) {
        this.discoverCatalogPort = discoverCatalogPort;
        this.healthcareFactsAdapter = healthcareFactsAdapter;
        this.providerLinkingService = providerLinkingService;
        this.publicProfileService = publicProfileService;
        this.moderationService = moderationService;
        this.draftService = draftService;
        this.tenantManagementService = tenantManagementService;
        this.clinicProfileService = clinicProfileService;
        this.doctorProfileService = doctorProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.providerOwnershipService = providerOwnershipService;
        this.auditEventQueryService = auditEventQueryService;
        this.suggestionRejectionRepository = suggestionRejectionRepository;
        this.objectMapper = objectMapper;
    }

    public ProviderConnectionsOverviewResponse overview() {
        List<ProviderConnectionsMetricResponse> metrics = new ArrayList<>();
        List<ProviderConnectionsLinkResponse> links = links(null, null, null);
        List<ProviderConnectionsSuggestionResponse> suggestions = suggestions(null);
        List<ProviderConnectionsConflictResponse> conflicts = conflicts();
        List<ProviderConnectionsPublicProfileResponse> publicProfiles = new ArrayList<>();
        publicProfiles.addAll(publicProfiles(PublicProfileType.DOCTOR, null, null));
        publicProfiles.addAll(publicProfiles(PublicProfileType.CLINIC, null, null));
        publicProfiles.addAll(publicProfiles(PublicProfileType.HOSPITAL, null, null));

        long notConnected = publicProfiles.stream().filter(profile -> !profile.connected()).count();
        long pendingVerification = links.stream().filter(link -> link.linkStatus() == LinkLifecycleStatus.PENDING_VERIFICATION).count();
        long proposed = links.stream().filter(link -> link.linkStatus() == LinkLifecycleStatus.PROPOSED).count();
        long linked = links.stream().filter(link -> link.linkStatus() == LinkLifecycleStatus.LINKED).count();
        long disconnected = links.stream().filter(link -> link.connectionStatus() == PlatformConnectionStatus.DISCONNECTED || link.connectionStatus() == PlatformConnectionStatus.NOT_CONNECTED).count();
        long reconciliationFailures = links.stream().filter(link -> link.linkStatus() == LinkLifecycleStatus.DISPUTED || link.connectionStatus() == PlatformConnectionStatus.DISPUTED).count();

        metrics.add(metric("public-profiles-not-connected", "Public profiles not connected", notConnected, "Published public profiles without a platform link.", "/platform/provider-connections/public-profiles"));
        metrics.add(metric("suggested-matches", "Suggested matches", suggestions.size(), "Potential public-to-platform matches awaiting review.", "/platform/provider-connections/suggestions"));
        metrics.add(metric("pending-verification", "Pending verification", pendingVerification, "Links waiting for identity validation.", "/platform/provider-connections/links?status=PENDING_VERIFICATION"));
        metrics.add(metric("proposed-links", "Proposed links", proposed, "Links proposed by Platform Admin.", "/platform/provider-connections/links?status=PROPOSED"));
        metrics.add(metric("linked-practices", "Linked practices", linked, "Active public-to-platform connections.", "/platform/provider-connections/links?status=LINKED"));
        metrics.add(metric("disconnected-integrations", "Disconnected integrations", disconnected, "Linked records whose operational connection is not reachable.", "/platform/provider-connections/links?status=DISCONNECTED"));
        metrics.add(metric("conflicts-disputes", "Conflicts / disputes", conflicts.size(), "Records requiring manual review.", "/platform/provider-connections/conflicts"));
        metrics.add(metric("reconciliation-failures", "Reconciliation failures", reconciliationFailures, "Links currently in a disputed or failed state.", "/platform/provider-connections/audit"));
        return new ProviderConnectionsOverviewResponse(metrics);
    }

    public List<ProviderConnectionsPublicProfileResponse> publicProfiles(PublicProfileType type, String query, String city) {
        List<PublicProviderSummary> summaries = discoverCatalogPort.searchPublishedProviders(normalize(query), normalize(city), type);
        Map<String, ProviderConnectionsLinkResponse> clinicLinks = clinicLinkIndex();
        Map<String, ProviderConnectionsLinkResponse> doctorLinks = doctorLinkIndex();
        return summaries.stream()
                .map(this::canonicalizePublishedIdentity)
                .map(summary -> toPublicProfileResponse(summary, findMatchingLink(summary, clinicLinks, doctorLinks)))
                .toList();
    }

    public List<ProviderConnectionsPublicProfileResponse> publicPractices(String query, String city) {
        List<PublicProviderSummary> summaries = discoverCatalogPort.searchPublishedPractices(normalize(query), normalize(city), PublicProfileType.DOCTOR);
        Map<String, ProviderConnectionsLinkResponse> doctorLinks = doctorLinkIndex();
        return summaries.stream()
                .map(this::canonicalizePublishedIdentity)
                .map(summary -> toPublicProfileResponse(summary, findMatchingLink(summary, Map.of(), doctorLinks)))
                .toList();
    }

    public List<ProviderConnectionsLifecycleResponse> publicProfileLifecycle(PublicProfileType type, String query, String city) {
        ProviderType providerType = toProviderType(type);
        if (providerType == null) {
            return List.of();
        }
        List<ProviderConnectionsLifecycleResponse> lifecycleRows = publicProfileService.listLifecycleProfiles(providerType, normalize(query), normalize(city)).stream()
                .map(this::toLifecycleResponse)
                .toList();
        List<ProviderConnectionsLifecycleResponse> draftRows = draftService.listDraftLifecycle().stream()
                .filter(record -> record.publicProfileType() == providerType)
                .filter(record -> matchesQuery(record.displayName(), normalize(query).toLowerCase(Locale.ROOT))
                        || matchesQuery(record.canonicalSlug(), normalize(query).toLowerCase(Locale.ROOT))
                        || matchesQuery(record.city(), normalize(city).toLowerCase(Locale.ROOT)))
                .map(this::toLifecycleResponse)
                .toList();
        List<ProviderConnectionsLifecycleResponse> moderationRows = moderationService.listQueue().stream()
                .filter(record -> record.publicProfileType() == providerType)
                .filter(record -> matchesQuery(record.displayName(), normalize(query).toLowerCase(Locale.ROOT))
                        || matchesQuery(record.city(), normalize(city).toLowerCase(Locale.ROOT))
                        || matchesQuery(record.submissionReference(), normalize(query).toLowerCase(Locale.ROOT)))
                .map(this::toLifecycleResponse)
                .toList();
        List<ProviderConnectionsLifecycleResponse> combined = new ArrayList<>();
        combined.addAll(lifecycleRows);
        combined.addAll(draftRows);
        combined.addAll(moderationRows);
        return combined.stream()
                .sorted(Comparator.comparing(ProviderConnectionsLifecycleResponse::projectedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<ProviderConnectionsPlatformEntityResponse> platformEntities(String type, String query) {
        String normalizedType = normalize(type).toUpperCase(Locale.ROOT);
        String normalizedQuery = normalize(query).toLowerCase(Locale.ROOT);
        Map<String, ProviderConnectionsLinkResponse> linksByTenant = linkIndexByTenant();
        Map<String, PlatformTenantRecord> tenants = tenantManagementService.list().stream()
                .collect(Collectors.toMap(record -> record.id().toString(), Function.identity(), (a, b) -> a, LinkedHashMap::new));

        if ("DOCTOR".equals(normalizedType)) {
            return healthcareFactsAdapter.listDoctorRows().stream()
                    .filter(row -> matchesQuery(row.displayName(), normalizedQuery) || matchesQuery(row.tenantName(), normalizedQuery) || matchesQuery(row.city(), normalizedQuery) || matchesQuery(row.registrationNumber(), normalizedQuery))
                    .map(row -> toPlatformDoctorEntity(row, tenants.get(row.tenantId() == null ? null : row.tenantId().toString()), linksByTenant))
                    .toList();
        }
        if ("CLINIC".equals(normalizedType) || normalizedType.isBlank()) {
            return healthcareFactsAdapter.listClinicRows().stream()
                    .filter(row -> matchesQuery(row.displayName(), normalizedQuery) || matchesQuery(row.tenantName(), normalizedQuery) || matchesQuery(row.city(), normalizedQuery) || matchesQuery(row.slug(), normalizedQuery))
                    .map(row -> toPlatformClinicEntity(row, tenants.get(row.tenantId() == null ? null : row.tenantId().toString()), linksByTenant))
                    .toList();
        }
        return List.of();
    }

    public List<ProviderConnectionsLinkResponse> links(String type, String status, String query) {
        String normalizedType = normalize(type).toUpperCase(Locale.ROOT);
        String normalizedStatus = normalize(status).toUpperCase(Locale.ROOT);
        String normalizedQuery = normalize(query).toLowerCase(Locale.ROOT);
        List<ProviderConnectionsLinkResponse> clinicLinks = providerLinkingService.listClinicLinks().stream()
                .map(this::toLinkResponse)
                .filter(link -> normalizedType.isBlank() || "CLINIC".equals(normalizedType))
                .toList();
        List<ProviderConnectionsLinkResponse> doctorLinks = providerLinkingService.listDoctorPracticeLinks().stream()
                .map(this::toLinkResponse)
                .filter(link -> normalizedType.isBlank() || "DOCTOR".equals(normalizedType))
                .toList();
        return java.util.stream.Stream.concat(clinicLinks.stream(), doctorLinks.stream())
                .filter(link -> normalizedStatus.isBlank() || matchesStatus(link, normalizedStatus))
                .filter(link -> normalizedQuery.isBlank()
                        || matchesQuery(link.publicReference(), normalizedQuery)
                        || matchesQuery(link.publicPracticeReference(), normalizedQuery)
                        || matchesQuery(link.tenantName(), normalizedQuery)
                        || matchesQuery(link.publicDisplayName(), normalizedQuery))
                .sorted(Comparator.comparing(ProviderConnectionsLinkResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public Optional<ProviderConnectionsLinkDetailResponse> linkDetail(UUID linkId) {
        if (linkId == null) {
            return Optional.empty();
        }
        Optional<PublicClinicPlatformLinkEntity> clinic = providerLinkingService.findClinicLink(linkId);
        if (clinic.isPresent()) {
            ProviderConnectionsLinkResponse link = toLinkResponse(clinic.get());
            return Optional.of(new ProviderConnectionsLinkDetailResponse(link, comparisonRows(clinic.get()), audit(linkId, clinic.get().getTenantReference(), clinic.get().naturalKey())));
        }
        Optional<PublicDoctorPracticePlatformLinkEntity> doctor = providerLinkingService.findDoctorPracticeLink(linkId);
        return doctor.map(entity -> {
            ProviderConnectionsLinkResponse link = toLinkResponse(entity);
            return new ProviderConnectionsLinkDetailResponse(link, comparisonRows(entity), audit(linkId, entity.getTenantReference(), entity.naturalKey()));
        });
    }

    public List<ProviderConnectionsAuditResponse> audit(UUID linkId, String tenantReference, String entityKey) {
        if (linkId == null) {
            return List.of();
        }
        UUID tenantId = tenantIdFromReference(tenantReference);
        if (tenantId == null) {
            return List.of();
        }
        String entityType = entityKey != null && entityKey.contains("|") && entityKey.split("\\|").length == 5
                ? "PUBLIC_DOCTOR_PRACTICE_PLATFORM_LINK"
                : "PUBLIC_CLINIC_PLATFORM_LINK";
        return auditEventQueryService.listForEntity(tenantId, entityType, linkId).stream()
                .sorted(Comparator.comparing(AuditEventRecord::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAuditResponse)
                .toList();
    }

    public List<ProviderConnectionsAuditResponse> audit(UUID linkId) {
        if (linkId == null) {
            return List.of();
        }
        return linkDetail(linkId).map(detail -> detail.audit()).orElse(List.of());
    }

    public List<ProviderConnectionsSuggestionResponse> suggestions(String query) {
        String normalizedQuery = normalize(query).toLowerCase(Locale.ROOT);
        List<ProviderConnectionsSuggestionResponse> suggestions = new ArrayList<>();
        Map<String, ProviderConnectionsPlatformEntityResponse> clinicIndex = platformEntities("CLINIC", null).stream()
                .collect(Collectors.toMap(item -> normalizedKey(item.displayName(), item.city()), Function.identity(), (a, b) -> a, LinkedHashMap::new));
        for (ProviderConnectionsPublicProfileResponse profile : publicProfiles(PublicProfileType.CLINIC, query, null)) {
            ProviderConnectionsPlatformEntityResponse match = clinicIndex.get(normalizedKey(profile.displayName(), profile.city()));
            if (match == null) {
                continue;
            }
            ProposalResolution resolution = resolveClinicProposal(suggestionRequest(profile, match));
            PublicProviderReference publicReference = profile.publicReference() == null ? null : new PublicProviderReference(profile.publicReference(), null);
            if (isRejectedSuggestion(PublicProfileType.CLINIC, publicReference, null, match, resolution)) {
                continue;
            }
            suggestions.add(toSuggestionResponse(profile, null, match, resolution, "Clinic display-name and city alignment."));
        }

        Map<String, ProviderConnectionsPlatformEntityResponse> doctorIndex = platformEntities("DOCTOR", null).stream()
                .collect(Collectors.toMap(item -> normalizedKey(item.displayName(), item.city(), item.registrationNumber()), Function.identity(), (a, b) -> a, LinkedHashMap::new));
        for (ProviderConnectionsPublicProfileResponse profile : publicPractices(query, null)) {
            ProviderConnectionsPlatformEntityResponse match = doctorIndex.get(normalizedKey(profile.displayName(), profile.city(), profile.publicReference()));
            if (match == null) {
                match = doctorIndex.get(normalizedKey(profile.displayName(), profile.city(), null));
            }
            if (match == null) {
                continue;
            }
            ProposalResolution resolution = resolveDoctorProposal(suggestionRequest(profile, match));
            PublicProviderReference publicReference = profile.publicReference() == null ? null : new PublicProviderReference(profile.publicReference(), profile.publicPracticeReference());
            if (isRejectedSuggestion(PublicProfileType.DOCTOR, publicReference, profile.publicPracticeReference(), match, resolution)) {
                continue;
            }
            suggestions.add(toSuggestionResponse(profile, profile.publicPracticeReference(), match, resolution, "Doctor registration and location alignment."));
        }

        return suggestions.stream()
                .filter(row -> normalizedQuery.isBlank()
                        || matchesQuery(row.publicDisplayName(), normalizedQuery)
                        || matchesQuery(row.platformDisplayName(), normalizedQuery)
                        || matchesQuery(row.tenantReference(), normalizedQuery))
                .sorted(Comparator.comparing(ProviderConnectionsSuggestionResponse::publicDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public ProviderConnectionsSuggestionResponse rejectSuggestion(String suggestionId, String reason) {
        ProviderConnectionsSuggestionResponse suggestion = suggestions(null).stream()
                .filter(row -> Objects.equals(row.id(), suggestionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
        ProviderConnectionSuggestionRejectionEntity entity = suggestionRejectionRepository.findBySuggestionKey(suggestion.id())
                .orElseGet(() -> {
                    ProviderConnectionSuggestionRejectionEntity created = ProviderConnectionSuggestionRejectionEntity.create(
                            UUID.randomUUID(),
                            suggestion.id(),
                            suggestion.publicProfileType().name(),
                            suggestion.publicReference(),
                            suggestion.publicPracticeReference(),
                            suggestion.tenantReference(),
                            suggestion.platformClinicReference(),
                            suggestion.sourceRevision(),
                            reason,
                            OffsetDateTime.now(),
                            suggestion.evidence() == null ? "[]" : evidenceJson(suggestion.evidence())
                    );
                    return suggestionRejectionRepository.save(created);
                });
        if (entity.getReason() == null || !entity.getReason().equals(reason)) {
            ProviderConnectionSuggestionRejectionEntity updated = ProviderConnectionSuggestionRejectionEntity.create(
                    entity.getId(),
                    entity.getSuggestionKey(),
                    entity.getPublicProfileType(),
                    entity.getPublicReference(),
                    entity.getPublicPracticeReference(),
                    entity.getTenantReference(),
                    entity.getPlatformClinicReference(),
                    entity.getSourceRevision(),
                    reason,
                    entity.getCreatedAt(),
                    entity.getMetadataJson()
            );
            suggestionRejectionRepository.save(updated);
        }
        return suggestion;
    }

    public List<ProviderConnectionsConflictResponse> conflicts() {
        List<ProviderConnectionsConflictResponse> conflicts = new ArrayList<>();
        for (ProviderConnectionsLinkResponse link : links(null, null, null)) {
            if (link.linkStatus() == LinkLifecycleStatus.DISPUTED || link.connectionStatus() == PlatformConnectionStatus.DISPUTED) {
                conflicts.add(new ProviderConnectionsConflictResponse(
                        "conflict-" + link.id(),
                        "HIGH",
                        "Disputed link",
                        "This link requires manual review before it can be trusted for booking.",
                        link.id().toString(),
                        link.publicReference(),
                        link.tenantReference()
                ));
            }
            if (link.linkStatus() == LinkLifecycleStatus.REJECTED && link.connectionStatus() == PlatformConnectionStatus.CONNECTED) {
                conflicts.add(new ProviderConnectionsConflictResponse(
                        "conflict-" + link.id() + "-status",
                        "HIGH",
                        "Invalid link/connection state",
                        "Rejected links cannot remain connected.",
                        link.id().toString(),
                        link.publicReference(),
                        link.tenantReference()
                ));
            }
        }
        return conflicts;
    }

    public ProviderConnectionsLinkResponse proposeClinicLink(ProviderConnectionsLinkProposalRequest request, String actorReference) {
        return toLinkResponse(providerLinkingService.upsertClinicLink(toClinicRequest(request, LinkLifecycleStatus.PROPOSED, PlatformConnectionStatus.CONNECTION_PENDING, request.reason(), actorReference)));
    }

    public ProviderConnectionsLinkResponse approveClinicLink(UUID linkId, String reason, String actorReference) {
        PublicClinicPlatformLinkEntity entity = providerLinkingService.findClinicLink(linkId).orElseThrow(() -> new IllegalArgumentException("Clinic link not found"));
        return toLinkResponse(providerLinkingService.upsertClinicLink(toClinicRequest(entity, LinkLifecycleStatus.APPROVED, PlatformConnectionStatus.NOT_CONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse activateClinicLink(UUID linkId, String reason, String actorReference) {
        PublicClinicPlatformLinkEntity entity = providerLinkingService.findClinicLink(linkId).orElseThrow(() -> new IllegalArgumentException("Clinic link not found"));
        return toLinkResponse(providerLinkingService.upsertClinicLink(toClinicRequest(entity, LinkLifecycleStatus.LINKED, PlatformConnectionStatus.CONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse unlinkClinicLink(UUID linkId, String reason, String actorReference) {
        PublicClinicPlatformLinkEntity entity = providerLinkingService.findClinicLink(linkId).orElseThrow(() -> new IllegalArgumentException("Clinic link not found"));
        return toLinkResponse(providerLinkingService.upsertClinicLink(toClinicRequest(entity, LinkLifecycleStatus.UNLINKED, PlatformConnectionStatus.DISCONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse relinkClinicLink(UUID linkId, String reason, String actorReference) {
        PublicClinicPlatformLinkEntity entity = providerLinkingService.findClinicLink(linkId).orElseThrow(() -> new IllegalArgumentException("Clinic link not found"));
        return toLinkResponse(providerLinkingService.upsertClinicLink(toClinicRequest(entity, LinkLifecycleStatus.PROPOSED, PlatformConnectionStatus.CONNECTION_PENDING, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse rejectClinicLink(UUID linkId, String reason, String actorReference) {
        PublicClinicPlatformLinkEntity entity = providerLinkingService.findClinicLink(linkId).orElseThrow(() -> new IllegalArgumentException("Clinic link not found"));
        return toLinkResponse(providerLinkingService.upsertClinicLink(toClinicRequest(entity, LinkLifecycleStatus.REJECTED, PlatformConnectionStatus.NOT_CONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse suspendClinicLink(UUID linkId, String reason, String actorReference) {
        PublicClinicPlatformLinkEntity entity = providerLinkingService.findClinicLink(linkId).orElseThrow(() -> new IllegalArgumentException("Clinic link not found"));
        return toLinkResponse(providerLinkingService.upsertClinicLink(toClinicRequest(entity, LinkLifecycleStatus.SUSPENDED, PlatformConnectionStatus.DISCONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse resumeClinicLink(UUID linkId, String reason, String actorReference) {
        PublicClinicPlatformLinkEntity entity = providerLinkingService.findClinicLink(linkId).orElseThrow(() -> new IllegalArgumentException("Clinic link not found"));
        return toLinkResponse(providerLinkingService.upsertClinicLink(toClinicRequest(entity, LinkLifecycleStatus.LINKED, PlatformConnectionStatus.CONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse proposeDoctorPracticeLink(ProviderConnectionsLinkProposalRequest request, String actorReference) {
        return toLinkResponse(providerLinkingService.upsertDoctorPracticeLink(toDoctorRequest(request, LinkLifecycleStatus.PROPOSED, PlatformConnectionStatus.CONNECTION_PENDING, request.reason(), actorReference)));
    }

    public ProviderConnectionsLinkResponse approveDoctorPracticeLink(UUID linkId, String reason, String actorReference) {
        PublicDoctorPracticePlatformLinkEntity entity = providerLinkingService.findDoctorPracticeLink(linkId).orElseThrow(() -> new IllegalArgumentException("Doctor practice link not found"));
        return toLinkResponse(providerLinkingService.upsertDoctorPracticeLink(toDoctorRequest(entity, LinkLifecycleStatus.APPROVED, PlatformConnectionStatus.NOT_CONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse activateDoctorPracticeLink(UUID linkId, String reason, String actorReference) {
        PublicDoctorPracticePlatformLinkEntity entity = providerLinkingService.findDoctorPracticeLink(linkId).orElseThrow(() -> new IllegalArgumentException("Doctor practice link not found"));
        return toLinkResponse(providerLinkingService.upsertDoctorPracticeLink(toDoctorRequest(entity, LinkLifecycleStatus.LINKED, PlatformConnectionStatus.CONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse unlinkDoctorPracticeLink(UUID linkId, String reason, String actorReference) {
        PublicDoctorPracticePlatformLinkEntity entity = providerLinkingService.findDoctorPracticeLink(linkId).orElseThrow(() -> new IllegalArgumentException("Doctor practice link not found"));
        return toLinkResponse(providerLinkingService.upsertDoctorPracticeLink(toDoctorRequest(entity, LinkLifecycleStatus.UNLINKED, PlatformConnectionStatus.DISCONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse relinkDoctorPracticeLink(UUID linkId, String reason, String actorReference) {
        PublicDoctorPracticePlatformLinkEntity entity = providerLinkingService.findDoctorPracticeLink(linkId).orElseThrow(() -> new IllegalArgumentException("Doctor practice link not found"));
        return toLinkResponse(providerLinkingService.upsertDoctorPracticeLink(toDoctorRequest(entity, LinkLifecycleStatus.PROPOSED, PlatformConnectionStatus.CONNECTION_PENDING, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse rejectDoctorPracticeLink(UUID linkId, String reason, String actorReference) {
        PublicDoctorPracticePlatformLinkEntity entity = providerLinkingService.findDoctorPracticeLink(linkId).orElseThrow(() -> new IllegalArgumentException("Doctor practice link not found"));
        return toLinkResponse(providerLinkingService.upsertDoctorPracticeLink(toDoctorRequest(entity, LinkLifecycleStatus.REJECTED, PlatformConnectionStatus.NOT_CONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse suspendDoctorPracticeLink(UUID linkId, String reason, String actorReference) {
        PublicDoctorPracticePlatformLinkEntity entity = providerLinkingService.findDoctorPracticeLink(linkId).orElseThrow(() -> new IllegalArgumentException("Doctor practice link not found"));
        return toLinkResponse(providerLinkingService.upsertDoctorPracticeLink(toDoctorRequest(entity, LinkLifecycleStatus.SUSPENDED, PlatformConnectionStatus.DISCONNECTED, reason, actorReference)));
    }

    public ProviderConnectionsLinkResponse resumeDoctorPracticeLink(UUID linkId, String reason, String actorReference) {
        PublicDoctorPracticePlatformLinkEntity entity = providerLinkingService.findDoctorPracticeLink(linkId).orElseThrow(() -> new IllegalArgumentException("Doctor practice link not found"));
        return toLinkResponse(providerLinkingService.upsertDoctorPracticeLink(toDoctorRequest(entity, LinkLifecycleStatus.LINKED, PlatformConnectionStatus.CONNECTED, reason, actorReference)));
    }

    public ReconciliationResult reconcile(UUID linkId) {
        if (linkId == null) {
            return new ReconciliationResult("provider-link", 0, 0, 0, 0, 0, 0, 0, List.of(), OffsetDateTime.now(), OffsetDateTime.now());
        }
        return providerLinkingService.findClinicLink(linkId)
                .map(entity -> providerLinkingService.reconcileClinicLink(toClinicRequest(entity, entity.getLinkStatus(), entity.getConnectionStatus(), entity.getReason(), "SYSTEM_RECONCILIATION")))
                .or(() -> providerLinkingService.findDoctorPracticeLink(linkId)
                        .map(entity -> providerLinkingService.reconcileDoctorPracticeLink(toDoctorRequest(entity, entity.getLinkStatus(), entity.getConnectionStatus(), entity.getReason(), "SYSTEM_RECONCILIATION"))))
                .orElseGet(() -> new ReconciliationResult("provider-link", 1, 0, 0, 0, 0, 0, 1, List.of(), OffsetDateTime.now(), OffsetDateTime.now()));
    }

    public List<ProviderConnectionsOwnershipResponse> ownerships() {
        Map<String, ProviderConnectionsLinkResponse> clinicLinks = clinicLinkIndex();
        Map<String, ProviderConnectionsLinkResponse> doctorLinks = doctorLinkIndex();
        return providerOwnershipService.listOwnerships().stream()
                .map(ownership -> toOwnershipResponse(ownership,
                        providerOwnershipService.listMemberships(ownership.publicProfileReference()),
                        providerOwnershipService.listDisputes(ownership.publicProfileReference()),
                        clinicLinks,
                        doctorLinks))
                .sorted(Comparator.comparing(ProviderConnectionsOwnershipResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ProviderConnectionsOwnershipResponse approveOwnership(UUID ownershipId, String reason) {
        var ownership = providerOwnershipService.approveOwnership(ownershipId, null, reason);
        return ownershipResponse(ownership, providerOwnershipService.listMemberships(ownership.publicProfileReference()), providerOwnershipService.listDisputes(ownership.publicProfileReference()));
    }

    public ProviderConnectionsOwnershipResponse rejectOwnership(UUID ownershipId, String reason) {
        var ownership = providerOwnershipService.rejectOwnership(ownershipId, null, reason);
        return ownershipResponse(ownership, providerOwnershipService.listMemberships(ownership.publicProfileReference()), providerOwnershipService.listDisputes(ownership.publicProfileReference()));
    }

    public ProviderConnectionsOwnershipResponse disputeOwnership(UUID ownershipId, String reason) {
        var ownership = providerOwnershipService.markDisputed(ownershipId, null, reason);
        return ownershipResponse(ownership, providerOwnershipService.listMemberships(ownership.publicProfileReference()), providerOwnershipService.listDisputes(ownership.publicProfileReference()));
    }

    public ProviderConnectionsOwnershipResponse revokeOwnership(UUID ownershipId, String reason) {
        var ownership = providerOwnershipService.revokeOwnership(ownershipId, null, reason);
        return ownershipResponse(ownership, providerOwnershipService.listMemberships(ownership.publicProfileReference()), providerOwnershipService.listDisputes(ownership.publicProfileReference()));
    }

    private ProviderConnectionsMetricResponse metric(String key, String label, long value, String helperText, String path) {
        return new ProviderConnectionsMetricResponse(key, label, value, helperText, path);
    }

    private ProviderConnectionsPublicProfileResponse toPublicProfileResponse(PublicProviderSummary summary, ProviderConnectionsLinkResponse link) {
        return new ProviderConnectionsPublicProfileResponse(
                summary.publicProfileType(),
                summary.publicReference() == null ? null : summary.publicReference().publicProviderId(),
                summary.publicReference() == null ? null : summary.publicReference().publicPracticeId(),
                summary.displayName(),
                summary.canonicalSlug(),
                summary.publicReference() != null && StringUtils.hasText(summary.canonicalSlug()) ? publicPath(summary.publicProfileType(), summary.canonicalSlug()) : null,
                summary.city(),
                summary.area(),
                summary.publicPhone(),
                summary.publicFee(),
                link == null ? summary.bookingCapability() : link.bookingCapability(),
                link == null ? summary.availabilityState() : link.availabilityState(),
                summary.publicationStatus(),
                summary.sourceSystem(),
                summary.sourceRevision(),
                summary.sourceUpdatedAt(),
                summary.projectedAt(),
                link != null && link.linkStatus() == LinkLifecycleStatus.LINKED,
                link == null ? null : link.linkStatus(),
                link == null ? null : link.connectionStatus(),
                link == null ? null : link.platformClinicReference(),
                link == null ? null : link.tenantReference(),
                link == null ? List.of() : tagsForLink(link),
                publicProfileAllowedActions(summary, link)
        );
    }

    private ProviderConnectionsOwnershipResponse toOwnershipResponse(
            OwnershipRecord ownership,
            List<MembershipRecord> memberships,
            List<DisputeRecord> disputes,
            Map<String, ProviderConnectionsLinkResponse> clinicLinks,
            Map<String, ProviderConnectionsLinkResponse> doctorLinks
    ) {
        ProviderConnectionsLinkResponse linked = ownership.publicProfileType() == PublicProfileType.DOCTOR
                ? doctorLinks.get(ownership.publicProfileReference())
                : clinicLinks.get(ownership.publicProfileReference());
        return ownershipResponse(ownership, memberships, disputes, linked);
    }

    private ProviderConnectionsOwnershipResponse ownershipResponse(
            OwnershipRecord ownership,
            List<MembershipRecord> memberships,
            List<DisputeRecord> disputes
    ) {
        ProviderConnectionsLinkResponse linked = ownership.publicProfileType() == PublicProfileType.DOCTOR
                ? doctorLinkIndex().get(ownership.publicProfileReference())
                : clinicLinkIndex().get(ownership.publicProfileReference());
        return ownershipResponse(ownership, memberships, disputes, linked);
    }

    private ProviderConnectionsOwnershipResponse ownershipResponse(
            OwnershipRecord ownership,
            List<MembershipRecord> memberships,
            List<DisputeRecord> disputes,
            ProviderConnectionsLinkResponse linked
    ) {
        ProviderConnectionsPublicProfileResponse publicProfile = ownership.publicProfileType() == PublicProfileType.DOCTOR
                ? publicProfileByDoctorReference(ownership.publicProfileReference(), null)
                : publicProfileByClinicReference(ownership.publicProfileReference());
        OwnershipDisplayContext display = ownershipDisplayContext(ownership, publicProfile);
        List<String> membershipRoles = memberships.stream().map(membership -> membership.role().name() + ":" + membership.status()).toList();
        List<String> disputeStatuses = disputes.stream().map(dispute -> dispute.status().name()).toList();
        List<String> allowedActions = providerOwnershipService.ownershipAllowedActions(ownership, disputes);
        String maskedMobile = providerOwnershipService.maskedProviderMobile(ownership.providerAccountId()).orElse(null);
        return new ProviderConnectionsOwnershipResponse(
                ownership.id(),
                ownership.publicProfileType(),
                ownership.publicProfileReference(),
                display.displayName(),
                display.city(),
                display.area(),
                maskedMobile,
                ownership.active() ? "ENABLED" : "DISABLED",
                publicProfile == null ? "UNPUBLISHED" : publicProfile.publicationStatus().name(),
                linked == null ? "NOT_CONNECTED" : linked.connectionStatus().name(),
                linked == null ? "NOT_AVAILABLE" : linked.bookingCapability().name(),
                ownership.status().name(),
                ownership.ownershipMethod(),
                ownership.reason(),
                ownership.active(),
                ownership.sourceRevision(),
                ownership.verifiedAt(),
                ownership.revokedAt(),
                ownership.createdAt(),
                ownership.updatedAt(),
                membershipRoles,
                disputeStatuses,
                allowedActions
        );
    }

    private OwnershipDisplayContext ownershipDisplayContext(OwnershipRecord ownership, ProviderConnectionsPublicProfileResponse publicProfile) {
        if (publicProfile != null && StringUtils.hasText(publicProfile.displayName())) {
            return new OwnershipDisplayContext(publicProfile.displayName(), publicProfile.city(), publicProfile.area());
        }
        UUID tenantId = tenantIdFromReference(ownership.tenantReference());
        if (tenantId == null) {
            return new OwnershipDisplayContext("Clinic ownership claim", null, null);
        }
        if (ownership.publicProfileType() == PublicProfileType.DOCTOR) {
            UUID doctorUserId = parseUuid(ownership.publicProfileReference());
            DoctorProfileRecord doctorProfile = doctorUserId == null ? null : doctorProfileService.findByDoctorUserId(tenantId, doctorUserId).orElse(null);
            ClinicProfileRecord clinicProfile = clinicProfileService.findByTenantId(tenantId).orElse(null);
            TenantUserRecord doctorUser = doctorUserId == null ? null : tenantUserManagementService.list(tenantId).stream()
                    .filter(user -> doctorUserId.equals(user.appUserId()))
                    .findFirst()
                    .orElse(null);
            return new OwnershipDisplayContext(
                    doctorUser == null ? "Doctor ownership claim" : firstText(doctorUser.displayName(), doctorProfile == null ? null : doctorProfile.specialization(), "Doctor ownership claim"),
                    clinicProfile == null ? null : clinicProfile.city(),
                    doctorProfile == null ? null : doctorProfile.consultationRoom()
            );
        }
        ClinicProfileRecord clinicProfile = clinicProfileService.findByTenantId(tenantId).orElse(null);
        return new OwnershipDisplayContext(
                clinicProfile == null ? "Clinic ownership claim" : firstText(clinicProfile.displayName(), clinicProfile.clinicName(), "Clinic ownership claim"),
                clinicProfile == null ? null : clinicProfile.city(),
                clinicProfile == null ? null : clinicProfile.addressLine1()
        );
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record OwnershipDisplayContext(String displayName, String city, String area) {
    }

    private ProviderConnectionsLifecycleResponse toLifecycleResponse(PublicProfileLifecycleRecord record) {
        PublicationReadinessRecord readiness = publicProfileService.publicationReadiness(record.providerId());
        return new ProviderConnectionsLifecycleResponse(
                record.providerType() == null ? PublicProfileType.CLINIC : toPublicProfileType(record.providerType()),
                record.sourceSystem(),
                record.sourceEntityReference(),
                record.displayName(),
                record.canonicalSlug(),
                record.publicPath(),
                record.city(),
                record.area(),
                record.publicationStatus(),
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                List.of(),
                record.sourceRevision(),
                record.sourceUpdatedAt(),
                record.projectedAt(),
                record.connectionRevision(),
                readiness.ready(),
                readiness.missingFields(),
                readiness.invalidFields(),
                readiness.warnings(),
                null,
                null,
                null,
                null,
                null,
                0L,
                record.sourceSystem(),
                List.of()
        );
    }

    private ProviderConnectionsLifecycleResponse toLifecycleResponse(PublicProfileDraftWorkspaceRecord record) {
        return new ProviderConnectionsLifecycleResponse(
                record.publicProfileType() == null ? PublicProfileType.CLINIC : toPublicProfileType(record.publicProfileType()),
                record.sourceSystem(),
                record.sourceReference(),
                record.displayName(),
                record.canonicalSlug(),
                record.publicProfilePath(),
                record.city(),
                record.area(),
                record.publicProfileStatus(),
                record.ownershipStatus(),
                record.tenantConsentStatus(),
                record.draftReference(),
                record.contentStatus(),
                record.readinessStatus(),
                record.completenessPercentage(),
                record.currentVersion(),
                record.lastSavedAt(),
                record.allowedActions(),
                record.sourceRevision(),
                record.sourceUpdatedAt(),
                record.updatedAt(),
                record.currentVersion(),
                record.readiness() == null ? false : record.readiness().ready(),
                record.readiness() == null ? List.of() : record.readiness().missingMandatoryFields(),
                record.readiness() == null ? List.of() : record.readiness().invalidFields(),
                record.readiness() == null ? List.of() : record.readiness().warnings(),
                null,
                null,
                null,
                null,
                null,
                0L,
                record.sourceSystem(),
                record.allowedActions()
        );
    }

    private ProviderConnectionsLifecycleResponse toLifecycleResponse(PublicProfileModerationQueueRecord record) {
        return new ProviderConnectionsLifecycleResponse(
                record.publicProfileType() == null ? PublicProfileType.CLINIC : toPublicProfileType(record.publicProfileType()),
                "PROVIDER_PUBLIC_PROFILE_DRAFT",
                record.submissionReference(),
                record.displayName(),
                null,
                null,
                record.city(),
                record.area(),
                record.publicationStatus(),
                record.ownershipStatus(),
                record.tenantConsentStatus(),
                record.submissionReference(),
                record.contentStatus(),
                record.readinessStatus(),
                record.completenessPercentage(),
                record.submittedDraftVersion() == null ? 0 : record.submittedDraftVersion(),
                record.submittedAt(),
                record.allowedActions(),
                0L,
                record.submittedAt(),
                record.submittedAt(),
                0L,
                true,
                List.of(),
                List.of(),
                List.of(),
                record.moderationStatus(),
                record.submissionReference(),
                record.submittedAt(),
                record.assignedReviewer(),
                record.assignedAt(),
                record.ageInQueueDays(),
                record.sourceType(),
                record.allowedActions()
        );
    }

    private ProviderConnectionsPlatformEntityResponse toPlatformClinicEntity(HealthcareProviderFactsRow row, PlatformTenantRecord tenant, Map<String, ProviderConnectionsLinkResponse> linksByTenant) {
        ProviderConnectionsLinkResponse link = linksByTenant.get(row.tenantId() == null ? null : row.tenantId().toString());
        String publicListingConsent = row.publicListingEnabled() ? "Enabled" : "Disabled";
        String connectionStatus = link == null ? "NOT_LINKED" : link.connectionStatus().name();
        String currentDiscoverCapability = link == null
                ? (row.active() && row.publicListingEnabled() ? BookingCapability.CALL_TO_BOOK.name() : BookingCapability.NOT_AVAILABLE.name())
                : link.bookingCapability().name();
        String platformBookingSetup = BookingCapability.ONLINE_BOOKING.name().equals(row.operationalBookingCapability())
                ? "READY" : "INCOMPLETE";
        String currentAvailability = link == null ? AvailabilityState.UNKNOWN.name() : link.availabilityState().name();
        return new ProviderConnectionsPlatformEntityResponse(
                "CLINIC",
                row.tenantId(),
                row.tenantId() == null ? null : row.tenantId().toString(),
                row.tenantCode(),
                tenant == null ? row.tenantName() : tenant.name(),
                row.displayName(),
                row.city(),
                row.area(),
                row.phone(),
                row.email(),
                row.specialty(),
                row.qualification(),
                row.registrationNumber(),
                row.yearsOfExperience(),
                row.active(),
                row.publicListingEnabled(),
                publicListingConsent,
                row.slug(),
                link == null ? row.platformClinicReference() : link.platformClinicReference(),
                null,
                null,
                row.operationalBookingCapability(),
                platformBookingSetup,
                currentDiscoverCapability,
                currentAvailability,
                row.capabilityReason(),
                row.sourceRevision(),
                row.updatedAt(),
                link == null ? null : link.publicReference(),
                link == null ? null : link.linkStatus().name(),
                link == null ? null : link.connectionStatus().name()
        );
    }

    private ProviderConnectionsPlatformEntityResponse toPlatformDoctorEntity(HealthcareProviderFactsRow row, PlatformTenantRecord tenant, Map<String, ProviderConnectionsLinkResponse> linksByTenant) {
        ProviderConnectionsLinkResponse link = linksByTenant.get(row.tenantId() == null ? null : row.tenantId().toString() + "|" + row.doctorUserId());
        String publicListingConsent = row.publicListingEnabled() ? "Enabled" : "Disabled";
        String connectionStatus = link == null ? "NOT_LINKED" : link.connectionStatus().name();
        String currentDiscoverCapability = link == null
                ? (row.active() && row.publicListingEnabled() ? BookingCapability.CALL_TO_BOOK.name() : BookingCapability.NOT_AVAILABLE.name())
                : link.bookingCapability().name();
        String platformBookingSetup = BookingCapability.ONLINE_BOOKING.name().equals(row.operationalBookingCapability())
                ? "READY" : "INCOMPLETE";
        String currentAvailability = link == null ? AvailabilityState.UNKNOWN.name() : link.availabilityState().name();
        return new ProviderConnectionsPlatformEntityResponse(
                "DOCTOR",
                row.tenantId(),
                row.tenantId() == null ? null : row.tenantId().toString(),
                row.tenantCode(),
                tenant == null ? row.tenantName() : tenant.name(),
                row.displayName(),
                row.city(),
                row.area(),
                row.phone(),
                row.email(),
                row.specialty(),
                row.qualification(),
                row.registrationNumber(),
                row.yearsOfExperience(),
                row.active(),
                row.publicListingEnabled(),
                publicListingConsent,
                row.slug(),
                link == null ? row.platformClinicReference() : link.platformClinicReference(),
                row.tenantDoctorUserReference(),
                row.tenantDoctorProfileReference(),
                row.operationalBookingCapability(),
                platformBookingSetup,
                currentDiscoverCapability,
                currentAvailability,
                row.capabilityReason(),
                row.sourceRevision(),
                row.updatedAt(),
                link == null ? null : link.publicReference(),
                link == null ? null : link.linkStatus().name(),
                link == null ? null : link.connectionStatus().name()
        );
    }

    private ProviderConnectionsLinkResponse toLinkResponse(PublicClinicPlatformLinkEntity entity) {
        ProviderConnectionsPublicProfileResponse publicProfile = entity.getPublicClinicReference() == null ? null : publicProfileByClinicReference(entity.getPublicClinicReference());
        PlatformTenantRecord tenant = tenantIdFromReference(entity.getTenantReference()) == null ? null : tenantManagementService.get(tenantIdFromReference(entity.getTenantReference()));
        return new ProviderConnectionsLinkResponse(
                entity.getId(),
                PublicProfileType.CLINIC,
                entity.getPublicClinicReference(),
                null,
                entity.getTenantReference(),
                tenant == null ? null : tenant.name(),
                entity.getPlatformClinicReference(),
                null,
                null,
                entity.getLinkStatus(),
                entity.getConnectionStatus(),
                entity.getBookingCapability(),
                entity.getAvailabilityState(),
                entity.getMatchMethod(),
                normalizeMatchConfidenceValue(entity.getMatchConfidence()),
                entity.getReason(),
                maskBookingReference(entity.getBookingReference()),
                entity.getSourceRevision(),
                entity.getSourceUpdatedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                publicProfile == null ? null : publicProfile.displayName(),
                publicProfile == null ? null : publicProfile.city(),
                publicProfile == null ? null : publicProfile.area(),
                publicProfile == null ? null : publicProfile.publicPath(),
                entity.getSourceSystem() == null ? null : entity.getSourceSystem().name(),
                evidence(entity.getEvidenceSnapshotJson()),
                entity.getProposedBy(),
                entity.getProposedAt(),
                entity.getVerifiedBy(),
                entity.getVerifiedAt(),
                entity.getActivatedBy(),
                entity.getActivatedAt(),
                entity.getSuspendedBy(),
                entity.getSuspendedAt(),
                entity.getDisconnectedBy(),
                entity.getDisconnectedAt(),
                entity.getCapabilityReason(),
                entity.getConnectionRevision(),
                entity.getRowVersion(),
                linkAllowedActions(entity.getLinkStatus(), entity.getConnectionStatus())
        );
    }

    private ProviderConnectionsLinkResponse toLinkResponse(PublicDoctorPracticePlatformLinkEntity entity) {
        ProviderConnectionsPublicProfileResponse publicProfile = entity.getPublicDoctorReference() == null ? null : publicProfileByDoctorReference(entity.getPublicDoctorReference(), entity.getPublicPracticeReference());
        PlatformTenantRecord tenant = tenantIdFromReference(entity.getTenantReference()) == null ? null : tenantManagementService.get(tenantIdFromReference(entity.getTenantReference()));
        return new ProviderConnectionsLinkResponse(
                entity.getId(),
                PublicProfileType.DOCTOR,
                entity.getPublicDoctorReference(),
                entity.getPublicPracticeReference(),
                entity.getTenantReference(),
                tenant == null ? null : tenant.name(),
                entity.getPlatformClinicReference(),
                entity.getTenantDoctorUserReference(),
                entity.getTenantDoctorProfileReference(),
                entity.getLinkStatus(),
                entity.getConnectionStatus(),
                entity.getBookingCapability(),
                entity.getAvailabilityState(),
                entity.getMatchMethod(),
                normalizeMatchConfidenceValue(entity.getMatchConfidence()),
                entity.getReason(),
                maskBookingReference(entity.getBookingReference()),
                entity.getSourceRevision(),
                entity.getSourceUpdatedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                publicProfile == null ? null : publicProfile.displayName(),
                publicProfile == null ? null : publicProfile.city(),
                publicProfile == null ? null : publicProfile.area(),
                publicProfile == null ? null : publicProfile.publicPath(),
                entity.getSourceSystem() == null ? null : entity.getSourceSystem().name(),
                evidence(entity.getEvidenceSnapshotJson()),
                entity.getProposedBy(),
                entity.getProposedAt(),
                entity.getVerifiedBy(),
                entity.getVerifiedAt(),
                entity.getActivatedBy(),
                entity.getActivatedAt(),
                entity.getSuspendedBy(),
                entity.getSuspendedAt(),
                entity.getDisconnectedBy(),
                entity.getDisconnectedAt(),
                entity.getCapabilityReason(),
                entity.getConnectionRevision(),
                entity.getRowVersion(),
                linkAllowedActions(entity.getLinkStatus(), entity.getConnectionStatus())
        );
    }

    private ProviderConnectionsLinkResponse findMatchingLink(PublicProviderSummary summary, Map<String, ProviderConnectionsLinkResponse> clinicLinks, Map<String, ProviderConnectionsLinkResponse> doctorLinks) {
        if (summary.publicReference() == null) {
            return null;
        }
        if (summary.publicProfileType() == PublicProfileType.DOCTOR) {
            if (StringUtils.hasText(summary.publicReference().publicPracticeId())) {
                return doctorLinks.get(summary.publicReference().publicProviderId() + "|" + summary.publicReference().publicPracticeId());
            }
            return doctorLinks.get(summary.publicReference().publicProviderId());
        }
        return clinicLinks.get(summary.publicReference().publicProviderId());
    }

    private Map<String, ProviderConnectionsLinkResponse> clinicLinkIndex() {
        Map<String, ProviderConnectionsLinkResponse> index = new HashMap<>();
        for (ProviderConnectionsLinkResponse link : links("CLINIC", null, null)) {
            if (link.publicReference() != null) {
                index.merge(link.publicReference(), link, this::authoritativeLink);
            }
        }
        return index;
    }

    private Map<String, ProviderConnectionsLinkResponse> doctorLinkIndex() {
        Map<String, ProviderConnectionsLinkResponse> index = new HashMap<>();
        for (ProviderConnectionsLinkResponse link : links("DOCTOR", null, null)) {
            if (link.publicReference() != null) {
                if (StringUtils.hasText(link.publicPracticeReference())) {
                    index.merge(link.publicReference() + "|" + link.publicPracticeReference(), link, this::authoritativeLink);
                }
                index.merge(link.publicReference(), link, this::authoritativeLink);
            }
        }
        return index;
    }

    private Map<String, ProviderConnectionsLinkResponse> linkIndexByTenant() {
        Map<String, ProviderConnectionsLinkResponse> index = new HashMap<>();
        for (ProviderConnectionsLinkResponse link : links(null, null, null)) {
            if (StringUtils.hasText(link.tenantReference()) && "DOCTOR".equals(link.publicProfileType().name()) && StringUtils.hasText(link.tenantDoctorUserReference())) {
                index.merge(link.tenantReference() + "|" + link.tenantDoctorUserReference(), link, this::authoritativeLink);
            } else if (StringUtils.hasText(link.tenantReference())) {
                index.merge(link.tenantReference(), link, this::authoritativeLink);
            }
        }
        return index;
    }

    private ProviderConnectionsLinkResponse authoritativeLink(ProviderConnectionsLinkResponse left, ProviderConnectionsLinkResponse right) {
        if (left.linkStatus() == LinkLifecycleStatus.LINKED && right.linkStatus() != LinkLifecycleStatus.LINKED) {
            return left;
        }
        if (right.linkStatus() == LinkLifecycleStatus.LINKED && left.linkStatus() != LinkLifecycleStatus.LINKED) {
            return right;
        }
        OffsetDateTime leftUpdated = left.updatedAt();
        OffsetDateTime rightUpdated = right.updatedAt();
        if (leftUpdated == null) {
            return right;
        }
        return rightUpdated != null && rightUpdated.isAfter(leftUpdated) ? right : left;
    }

    private List<ProviderConnectionsComparisonRowResponse> comparisonRows(PublicClinicPlatformLinkEntity entity) {
        ProviderConnectionsPublicProfileResponse publicProfile = publicProfileByClinicReference(entity.getPublicClinicReference());
        PlatformTenantRecord tenant = tenantIdFromReference(entity.getTenantReference()) == null ? null : tenantManagementService.get(tenantIdFromReference(entity.getTenantReference()));
        ClinicProfileRecord clinic = tenant == null ? null : clinicProfileService.findByTenantId(tenant.id()).orElse(null);
        HealthcareProviderFactsRow platform = healthcareFactsAdapter.listClinicRows().stream()
                .filter(row -> Objects.equals(entity.getPlatformClinicReference(), row.platformClinicReference()))
                .findFirst()
                .orElse(null);
        String platformArea = platform == null ? null : platform.area();
        String platformPhone = platform == null ? null : platform.phone();
        return List.of(
                comparison("displayName", "Display name", publicProfile == null ? null : publicProfile.displayName(), clinic == null ? null : clinic.displayName(), status(publicProfile == null ? null : publicProfile.displayName(), clinic == null ? null : clinic.displayName())),
                comparison("city", "City", publicProfile == null ? null : publicProfile.city(), clinic == null ? null : clinic.city(), status(publicProfile == null ? null : publicProfile.city(), clinic == null ? null : clinic.city())),
                comparison("area", "Area", publicProfile == null ? null : publicProfile.area(), platformArea, status(publicProfile == null ? null : publicProfile.area(), platformArea)),
                comparison("phone", "Phone", publicProfile == null ? null : publicProfile.publicPhone(), platformPhone, phoneStatus(publicProfile == null ? null : publicProfile.publicPhone(), platformPhone)),
                comparison("booking", "Booking capability", publicProfile == null ? null : publicProfile.bookingCapability() == null ? null : publicProfile.bookingCapability().name(), entity.getBookingCapability() == null ? null : entity.getBookingCapability().name(), status(publicProfile == null ? null : publicProfile.bookingCapability() == null ? null : publicProfile.bookingCapability().name(), entity.getBookingCapability() == null ? null : entity.getBookingCapability().name())),
                comparison("sourceRevision", "Source revision", publicProfile == null ? null : String.valueOf(publicProfile.sourceRevision()), String.valueOf(entity.getSourceRevision()), status(publicProfile == null ? null : String.valueOf(publicProfile.sourceRevision()), String.valueOf(entity.getSourceRevision())))
        );
    }

    private List<ProviderConnectionsComparisonRowResponse> comparisonRows(PublicDoctorPracticePlatformLinkEntity entity) {
        ProviderConnectionsPublicProfileResponse publicProfile = publicProfileByDoctorReference(entity.getPublicDoctorReference(), entity.getPublicPracticeReference());
        PlatformTenantRecord tenant = tenantIdFromReference(entity.getTenantReference()) == null ? null : tenantManagementService.get(tenantIdFromReference(entity.getTenantReference()));
        ClinicProfileRecord clinic = tenant == null ? null : clinicProfileService.findByTenantId(tenant.id()).orElse(null);
        DoctorProfileRecord doctorProfile = tenant == null || !StringUtils.hasText(entity.getTenantDoctorUserReference()) ? null : doctorProfileService.findByDoctorUserId(tenant.id(), parseUuid(entity.getTenantDoctorUserReference())).orElse(null);
        TenantUserRecord doctor = tenant == null ? null : tenantUserManagementService.list(tenant.id()).stream().filter(item -> entity.getTenantDoctorUserReference() != null && entity.getTenantDoctorUserReference().equals(item.appUserId() == null ? null : item.appUserId().toString())).findFirst().orElse(null);
        return List.of(
                comparison("doctorName", "Doctor name", publicProfile == null ? null : publicProfile.displayName(), doctor == null ? null : doctor.displayName(), status(publicProfile == null ? null : publicProfile.displayName(), doctor == null ? null : doctor.displayName())),
                comparison("qualification", "Qualification", publicProfile == null ? null : publicProfile.publicFee(), doctorProfile == null ? null : doctorProfile.qualification(), status(publicProfile == null ? null : publicProfile.publicFee(), doctorProfile == null ? null : doctorProfile.qualification())),
                comparison("specialty", "Specialty", publicProfile == null ? null : publicProfile.area(), doctorProfile == null ? null : doctorProfile.specialization(), status(publicProfile == null ? null : publicProfile.area(), doctorProfile == null ? null : doctorProfile.specialization())),
                comparison("city", "City", publicProfile == null ? null : publicProfile.city(), clinic == null ? null : clinic.city(), status(publicProfile == null ? null : publicProfile.city(), clinic == null ? null : clinic.city())),
                comparison("booking", "Booking capability", publicProfile == null ? null : publicProfile.bookingCapability() == null ? null : publicProfile.bookingCapability().name(), entity.getBookingCapability() == null ? null : entity.getBookingCapability().name(), status(publicProfile == null ? null : publicProfile.bookingCapability() == null ? null : publicProfile.bookingCapability().name(), entity.getBookingCapability() == null ? null : entity.getBookingCapability().name())),
                comparison("sourceRevision", "Source revision", publicProfile == null ? null : String.valueOf(publicProfile.sourceRevision()), String.valueOf(entity.getSourceRevision()), status(publicProfile == null ? null : String.valueOf(publicProfile.sourceRevision()), String.valueOf(entity.getSourceRevision())))
        );
    }

    private ProviderConnectionsComparisonRowResponse comparison(String key, String label, String publicValue, String platformValue, String status) {
        return new ProviderConnectionsComparisonRowResponse(key, label, publicValue, platformValue, status);
    }

    private String status(String left, String right) {
        if (Objects.equals(normalize(left), normalize(right))) {
            return "MATCH";
        }
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return "MISSING";
        }
        return "DIFFERS";
    }

    private String phoneStatus(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return "MISSING";
        }
        return equalsPhone(left, right) ? "MATCH" : "DIFFERS";
    }

    private ProviderConnectionsAuditResponse toAuditResponse(AuditEventRecord record) {
        JsonNode details = parseJson(record.detailsJson());
        return new ProviderConnectionsAuditResponse(
                record.id(),
                record.action(),
                record.summary(),
                record.actorAppUserId(),
                record.occurredAt(),
                record.detailsJson(),
                text(details, "providerType"),
                text(details, "tenantReference"),
                text(details, "platformClinicReference"),
                text(details, "previousState"),
                text(details, "newState"),
                text(details, "result"),
                text(details, "correlationId")
        );
    }

    public List<ProviderConnectionsAuditResponse> auditEvents(String action, String tenantReference, String providerType, String result, String query) {
        List<String> entityTypes = List.of("PUBLIC_CLINIC_PLATFORM_LINK", "PUBLIC_DOCTOR_PRACTICE_PLATFORM_LINK");
        return auditEventQueryService.listRecentForEntityTypes(entityTypes, 50).stream()
                .filter(record -> action == null || action.isBlank() || matchesQuery(record.action(), action.toLowerCase(Locale.ROOT)))
                .filter(record -> query == null || query.isBlank() || matchesQuery(record.summary(), query.toLowerCase(Locale.ROOT)) || matchesQuery(record.detailsJson(), query.toLowerCase(Locale.ROOT)))
                .map(this::toAuditResponse)
                .filter(record -> tenantReference == null || tenantReference.isBlank() || matchesQuery(record.tenantReference(), tenantReference.toLowerCase(Locale.ROOT)))
                .filter(record -> providerType == null || providerType.isBlank() || matchesQuery(record.providerType(), providerType.toLowerCase(Locale.ROOT)))
                .filter(record -> result == null || result.isBlank() || matchesQuery(record.result(), result.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(ProviderConnectionsAuditResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private ProviderConnectionsPublicProfileResponse publicProfileByClinicReference(String clinicReference) {
        if (!StringUtils.hasText(clinicReference)) {
            return null;
        }
        List<PublicProviderSummary> providers = discoverCatalogPort.searchPublishedProviders(null, null, PublicProfileType.CLINIC);
        return providers.stream()
                .map(this::canonicalizePublishedIdentity)
                .filter(item -> clinicReference.equals(item.publicReference() == null ? null : item.publicReference().publicProviderId()))
                .findFirst()
                .map(item -> toPublicProfileResponse(item, null))
                .orElse(null);
    }

    private ProviderConnectionsPublicProfileResponse publicProfileByDoctorReference(String doctorReference, String practiceReference) {
        if (!StringUtils.hasText(doctorReference)) {
            return null;
        }
        List<PublicProviderSummary> providers = practiceReference == null
                ? discoverCatalogPort.searchPublishedProviders(null, null, PublicProfileType.DOCTOR)
                : discoverCatalogPort.searchPublishedPractices(null, null, PublicProfileType.DOCTOR);
        return providers.stream()
                .map(this::canonicalizePublishedIdentity)
                .filter(item -> doctorReference.equals(item.publicReference() == null ? null : item.publicReference().publicProviderId())
                        && (practiceReference == null || practiceReference.equals(item.publicReference() == null ? null : item.publicReference().publicPracticeId())))
                .findFirst()
                .map(item -> toPublicProfileResponse(item, null))
                .orElse(null);
    }

    private PublicClinicPlatformLinkUpsertRequest toClinicRequest(ProviderConnectionsLinkProposalRequest request, LinkLifecycleStatus linkStatus, PlatformConnectionStatus connectionStatus, String reason, String actorReference) {
        ProposalResolution resolution = resolveClinicProposal(request);
        return new PublicClinicPlatformLinkUpsertRequest(
                new ProviderSourceReference(
                        request.sourceSystem() == null ? SourceSystem.PLATFORM_ADMIN : request.sourceSystem(),
                        request.sourceEntityReference(),
                        request.sourceRevision(),
                        request.sourceUpdatedAt()
                ),
                request.publicReference(),
                request.tenantReference(),
                request.platformClinicReference(),
                linkStatus,
                connectionStatus,
                resolution.matchMethod(),
                resolution.matchConfidence(),
                AvailabilityState.UNKNOWN,
                resolution.evidenceJson(),
                "PLATFORM_ADMIN",
                actorReference,
                reason,
                parseBookingCapability(resolution.platformEntity().bookingCapability()),
                resolution.platformEntity().capabilityReason()
        );
    }

    private PublicClinicPlatformLinkUpsertRequest toClinicRequest(PublicClinicPlatformLinkEntity entity, LinkLifecycleStatus linkStatus, PlatformConnectionStatus connectionStatus, String reason, String actorReference) {
        ProviderConnectionsPlatformEntityResponse platform = platformEntities("CLINIC", null).stream()
                .filter(item -> Objects.equals(item.tenantReference(), entity.getTenantReference())
                        && Objects.equals(item.platformClinicReference(), entity.getPlatformClinicReference()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected platform clinic could not be found."));
        if (!platform.active()) {
            throw new IllegalArgumentException("Selected platform clinic is not active.");
        }
        return new PublicClinicPlatformLinkUpsertRequest(
                new ProviderSourceReference(entity.getSourceSystem(), entity.getSourceEntityReference(), entity.getSourceRevision(), entity.getSourceUpdatedAt()),
                entity.getPublicClinicReference(),
                entity.getTenantReference(),
                entity.getPlatformClinicReference(),
                linkStatus,
                connectionStatus,
                entity.getMatchMethod(),
                normalizeMatchConfidenceValue(entity.getMatchConfidence()),
                entity.getAvailabilityState(),
                entity.getEvidenceSnapshotJson(),
                "PLATFORM_ADMIN",
                actorReference,
                reason,
                parseBookingCapability(platform.bookingCapability()),
                platform.capabilityReason()
        );
    }

    private PublicDoctorPracticePlatformLinkUpsertRequest toDoctorRequest(ProviderConnectionsLinkProposalRequest request, LinkLifecycleStatus linkStatus, PlatformConnectionStatus connectionStatus, String reason, String actorReference) {
        ProposalResolution resolution = resolveDoctorProposal(request);
        return new PublicDoctorPracticePlatformLinkUpsertRequest(
                new ProviderSourceReference(
                        request.sourceSystem() == null ? SourceSystem.PLATFORM_ADMIN : request.sourceSystem(),
                        request.sourceEntityReference(),
                        request.sourceRevision(),
                        request.sourceUpdatedAt()
                ),
                new PublicProviderReference(request.publicReference(), null),
                new PublicProviderReference(null, request.publicPracticeReference()),
                request.tenantReference(),
                request.platformClinicReference(),
                request.tenantDoctorUserReference(),
                request.tenantDoctorProfileReference(),
                linkStatus,
                connectionStatus,
                resolution.matchMethod(),
                resolution.matchConfidence(),
                AvailabilityState.UNKNOWN,
                resolution.evidenceJson(),
                "PLATFORM_ADMIN",
                actorReference,
                reason,
                parseBookingCapability(resolution.platformEntity().bookingCapability()),
                resolution.platformEntity().capabilityReason()
        );
    }

    private PublicDoctorPracticePlatformLinkUpsertRequest toDoctorRequest(PublicDoctorPracticePlatformLinkEntity entity, LinkLifecycleStatus linkStatus, PlatformConnectionStatus connectionStatus, String reason, String actorReference) {
        ProviderConnectionsPlatformEntityResponse platform = platformEntities("DOCTOR", null).stream()
                .filter(item -> Objects.equals(item.tenantReference(), entity.getTenantReference())
                        && Objects.equals(item.platformClinicReference(), entity.getPlatformClinicReference())
                        && Objects.equals(item.tenantDoctorUserReference(), entity.getTenantDoctorUserReference()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected tenant doctor could not be found."));
        return new PublicDoctorPracticePlatformLinkUpsertRequest(
                new ProviderSourceReference(entity.getSourceSystem(), entity.getSourceEntityReference(), entity.getSourceRevision(), entity.getSourceUpdatedAt()),
                new PublicProviderReference(entity.getPublicDoctorReference(), null),
                new PublicProviderReference(null, entity.getPublicPracticeReference()),
                entity.getTenantReference(),
                entity.getPlatformClinicReference(),
                entity.getTenantDoctorUserReference(),
                entity.getTenantDoctorProfileReference(),
                linkStatus,
                connectionStatus,
                entity.getMatchMethod(),
                normalizeMatchConfidenceValue(entity.getMatchConfidence()),
                entity.getAvailabilityState(),
                entity.getEvidenceSnapshotJson(),
                "PLATFORM_ADMIN",
                actorReference,
                reason,
                parseBookingCapability(platform.bookingCapability()),
                platform.capabilityReason()
        );
    }

    private BookingCapability parseBookingCapability(String value) {
        if (!StringUtils.hasText(value)) {
            return BookingCapability.CALL_TO_BOOK;
        }
        try {
            return BookingCapability.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BookingCapability.CALL_TO_BOOK;
        }
    }

    private String publicPath(PublicProfileType type, String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        return switch (type) {
            case DOCTOR -> "/discover/doctors/" + slug;
            case CLINIC -> "/discover/clinics/" + slug;
            case HOSPITAL -> "/discover/hospitals/" + slug;
        };
    }

    private List<String> tagsForLink(ProviderConnectionsLinkResponse link) {
        List<String> tags = new ArrayList<>();
        tags.add(link.bookingCapability() == null ? "CALL_TO_BOOK" : link.bookingCapability().name());
        if (StringUtils.hasText(link.sourceSystem())) {
            tags.add(link.sourceSystem());
        }
        if (StringUtils.hasText(link.matchMethod() == null ? null : link.matchMethod().name())) {
            tags.add(link.matchMethod().name());
        }
        return tags;
    }

    private List<String> publicProfileAllowedActions(PublicProviderSummary summary, ProviderConnectionsLinkResponse link) {
        List<String> actions = new ArrayList<>();
        actions.add("VIEW_PUBLIC_PROFILE");
        if (link == null) {
            actions.add("REVIEW_MATCH");
            actions.add("PROPOSE_LINK");
        } else {
            actions.addAll(link.allowedActions());
        }
        return actions.stream().distinct().toList();
    }

    private List<String> linkAllowedActions(LinkLifecycleStatus linkStatus, PlatformConnectionStatus connectionStatus) {
        List<String> actions = new ArrayList<>(List.of(
                "VIEW_PUBLIC_PROFILE",
                "VIEW_PLATFORM_ENTITY",
                "VIEW_LINK_HISTORY",
                "COPY_CONNECTION_REFERENCE",
                "RECONCILE_LINK"
        ));
        switch (linkStatus) {
            case PROPOSED, PENDING_VERIFICATION -> {
                actions.add("VERIFY_LINK");
                actions.add("REJECT_LINK");
            }
            case APPROVED -> {
                actions.add("ACTIVATE_LINK");
                actions.add("REJECT_LINK");
            }
            case LINKED -> {
                actions.add("SUSPEND_LINK");
                actions.add("DISCONNECT_LINK");
            }
            case SUSPENDED -> {
                actions.add("RESUME_LINK");
                actions.add("DISCONNECT_LINK");
            }
            case UNLINKED, REJECTED -> actions.add("PROPOSE_LINK");
            case DISPUTED -> {
                actions.add("RESUME_LINK");
                actions.add("DISCONNECT_LINK");
            }
            default -> actions.add("PROPOSE_LINK");
        }
        return actions;
    }

    private String maskBookingReference(String bookingReference) {
        if (!StringUtils.hasText(bookingReference)) {
            return null;
        }
        String trimmed = bookingReference.trim();
        if (trimmed.length() <= 10) {
            return trimmed;
        }
        return trimmed.substring(0, 6) + "…" + trimmed.substring(trimmed.length() - 4);
    }

    private boolean matchesStatus(ProviderConnectionsLinkResponse link, String status) {
        return status.equalsIgnoreCase(link.linkStatus().name())
                || status.equalsIgnoreCase(link.connectionStatus().name())
                || status.equalsIgnoreCase(link.bookingCapability().name())
                || (status.equals("OPEN") && (link.linkStatus() == LinkLifecycleStatus.DISPUTED || link.connectionStatus() == PlatformConnectionStatus.DISPUTED));
    }

    private boolean matchesQuery(String value, String query) {
        return !StringUtils.hasText(query) || (value != null && value.toLowerCase(Locale.ROOT).contains(query));
    }

    private String normalizedKey(String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("|"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String evidenceJson(List<ProviderMatchEvidence> evidence) {
        try {
            return objectMapper.writeValueAsString(evidence == null ? List.of() : evidence);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize provider match evidence.", ex);
        }
    }

    private List<ProviderMatchEvidence> evidence(String evidenceJson) {
        if (!StringUtils.hasText(evidenceJson)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(evidenceJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<ProviderMatchEvidence> values = new ArrayList<>();
            for (JsonNode item : node) {
                values.add(new ProviderMatchEvidence(
                        text(item, "evidenceType"),
                        text(item, "result"),
                        parseEvidenceStrength(text(item, "strength")),
                        text(item, "publicDisplayValue"),
                        text(item, "platformDisplayValue"),
                        item.path("sourceRevision").asLong(0L),
                        item.hasNonNull("recordedAt") ? OffsetDateTime.parse(item.path("recordedAt").asText()) : null,
                        text(item, "explanation")
                ));
            }
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ProviderMatchEvidence evidenceItem(
            String evidenceType,
            boolean exact,
            String publicDisplayValue,
            String platformDisplayValue,
            long sourceRevision,
            String explanation
    ) {
        EvidenceStrength strength = exact ? EvidenceStrength.STRONG : (StringUtils.hasText(publicDisplayValue) && StringUtils.hasText(platformDisplayValue) ? EvidenceStrength.WEAK : EvidenceStrength.SUPPORTING);
        return new ProviderMatchEvidence(
                evidenceType,
                exact ? "MATCH" : (StringUtils.hasText(publicDisplayValue) && StringUtils.hasText(platformDisplayValue) ? "DIFFERS" : "MISSING"),
                strength,
                publicDisplayValue,
                platformDisplayValue,
                sourceRevision,
                now(),
                explanation
        );
    }

    private MatchConfidence deriveConfidence(List<ProviderMatchEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return MatchConfidence.LOW;
        }
        boolean conflict = evidence.stream().anyMatch(item -> item.strength() == EvidenceStrength.CONFLICT);
        boolean strong = evidence.stream().anyMatch(item -> item.strength() == EvidenceStrength.STRONG);
        boolean supporting = evidence.stream().anyMatch(item -> item.strength() == EvidenceStrength.SUPPORTING);
        if (conflict) {
            return MatchConfidence.LOW;
        }
        if (strong && supporting) {
            return MatchConfidence.HIGH;
        }
        if (strong) {
            return MatchConfidence.HIGH;
        }
        if (supporting) {
            return MatchConfidence.MEDIUM;
        }
        return MatchConfidence.LOW;
    }

    private MatchMethod deriveClinicMatchMethod(List<ProviderMatchEvidence> evidence) {
        boolean registration = hasMatch(evidence, "REGISTRATION_EXACT");
        boolean contact = hasMatch(evidence, "VERIFIED_PHONE_EXACT") || hasMatch(evidence, "VERIFIED_EMAIL_EXACT");
        boolean business = hasMatch(evidence, "ADDRESS_EXACT") || hasMatch(evidence, "CITY_EXACT");
        if (registration && contact) {
            return MatchMethod.REGISTRATION_AND_CONTACT;
        }
        if (registration) {
            return MatchMethod.REGISTRATION_EXACT;
        }
        if (contact) {
            return MatchMethod.VERIFIED_CONTACT_EXACT;
        }
        if (business) {
            return MatchMethod.BUSINESS_IDENTITY_MATCH;
        }
        return MatchMethod.MANUAL_REFERENCE;
    }

    private MatchMethod deriveDoctorMatchMethod(List<ProviderMatchEvidence> evidence) {
        boolean registration = hasMatch(evidence, "REGISTRATION_EXACT");
        boolean contact = hasMatch(evidence, "VERIFIED_PHONE_EXACT") || hasMatch(evidence, "VERIFIED_EMAIL_EXACT");
        boolean profile = hasMatch(evidence, "DISPLAY_NAME_EXACT") && hasMatch(evidence, "SPECIALTY_EXACT");
        if (registration && contact) {
            return MatchMethod.REGISTRATION_AND_CONTACT;
        }
        if (registration) {
            return MatchMethod.REGISTRATION_EXACT;
        }
        if (contact) {
            return MatchMethod.VERIFIED_CONTACT_EXACT;
        }
        if (profile) {
            return MatchMethod.BUSINESS_IDENTITY_MATCH;
        }
        return MatchMethod.MANUAL_REFERENCE;
    }

    private boolean hasMatch(List<ProviderMatchEvidence> evidence, String evidenceType) {
        return evidence != null && evidence.stream().anyMatch(item -> evidenceType.equals(item.evidenceType()) && "MATCH".equalsIgnoreCase(item.result()));
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText(null);
    }

    private JsonNode parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private EvidenceStrength parseEvidenceStrength(String value) {
        if (!StringUtils.hasText(value)) {
            return EvidenceStrength.WEAK;
        }
        try {
            return EvidenceStrength.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return EvidenceStrength.WEAK;
        }
    }

    private ProposalResolution resolveClinicProposal(ProviderConnectionsLinkProposalRequest request) {
        ProviderConnectionsPublicProfileResponse publicProfile = publicProfiles(PublicProfileType.CLINIC, null, null).stream()
                .filter(item -> Objects.equals(item.publicReference(), request.publicReference()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected clinic public profile could not be found."));
        ProviderConnectionsPlatformEntityResponse platform = platformEntities("CLINIC", null).stream()
                .filter(item -> Objects.equals(item.tenantReference(), request.tenantReference())
                        && Objects.equals(item.platformClinicReference(), request.platformClinicReference()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected platform clinic could not be found."));
        if (request.platformEntityRevision() != 0L && request.platformEntityRevision() != platform.sourceRevision()) {
            throw new IllegalArgumentException("Platform clinic source revision changed; reload and compare again.");
        }
        if (request.sourceRevision() != 0L && request.sourceRevision() != publicProfile.sourceRevision()) {
            throw new IllegalArgumentException("Public clinic source revision changed; reload and compare again.");
        }
        List<ProviderMatchEvidence> evidence = new ArrayList<>();
        var publicDetail = publicProfileService.findBySlug(publicProfile.slug()).orElse(null);
        evidence.add(evidenceItem("DISPLAY_NAME_EXACT", equalsText(publicProfile.displayName(), platform.displayName()), publicProfile.displayName(), platform.displayName(), publicProfile.sourceRevision(), "Clinic display names compared."));
        evidence.add(evidenceItem("CITY_EXACT", equalsText(publicProfile.city(), platform.city()), publicProfile.city(), platform.city(), publicProfile.sourceRevision(), "Clinic city compared."));
        evidence.add(evidenceItem("ADDRESS_EXACT", equalsText(publicProfile.area(), platform.area()), publicProfile.area(), platform.area(), publicProfile.sourceRevision(), "Clinic public area and operational address compared."));
        evidence.add(evidenceItem("VERIFIED_PHONE_EXACT", equalsPhone(publicProfile.publicPhone(), platform.phone()), publicProfile.publicPhone(), platform.phone(), publicProfile.sourceRevision(), "Clinic phone numbers compared after normalization."));
        evidence.add(evidenceItem("VERIFIED_EMAIL_EXACT", equalsText(publicDetail == null ? null : publicDetail.contactEmail(), platform.email()), publicDetail == null ? null : publicDetail.contactEmail(), platform.email(), publicProfile.sourceRevision(), "Clinic email addresses compared."));
        evidence.add(evidenceItem("CANONICAL_SLUG_EXACT", equalsText(publicProfile.slug(), platform.slug()), publicProfile.slug(), platform.slug(), publicProfile.sourceRevision(), "Canonical routing slugs compared as supporting evidence."));
        String publicRegistration = immutableRegistrationNumber(publicProfile.publicReference());
        evidence.add(evidenceItem("REGISTRATION_EXACT", equalsText(publicRegistration, platform.registrationNumber()), publicRegistration, platform.registrationNumber(), publicProfile.sourceRevision(), "Registration references compared when both sides provide one."));
        return new ProposalResolution(deriveClinicMatchMethod(evidence), deriveConfidence(evidence), evidenceJson(evidence), evidence, publicProfile, platform);
    }

    private ProposalResolution resolveDoctorProposal(ProviderConnectionsLinkProposalRequest request) {
        ProviderConnectionsPublicProfileResponse publicProfile = publicPractices(null, null).stream()
                .filter(item -> Objects.equals(item.publicReference(), request.publicReference())
                        && Objects.equals(item.publicPracticeReference(), request.publicPracticeReference()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected doctor public practice could not be found."));
        ProviderConnectionsPlatformEntityResponse platform = platformEntities("DOCTOR", null).stream()
                .filter(item -> Objects.equals(item.tenantReference(), request.tenantReference())
                        && Objects.equals(item.platformClinicReference(), request.platformClinicReference())
                        && Objects.equals(item.tenantDoctorUserReference(), request.tenantDoctorUserReference())
                        && Objects.equals(item.tenantDoctorProfileReference(), request.tenantDoctorProfileReference()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected tenant doctor could not be found."));
        if (request.platformEntityRevision() != 0L && request.platformEntityRevision() != platform.sourceRevision()) {
            throw new IllegalArgumentException("Tenant doctor source revision changed; reload and compare again.");
        }
        if (request.sourceRevision() != 0L && request.sourceRevision() != publicProfile.sourceRevision()) {
            throw new IllegalArgumentException("Public doctor source revision changed; reload and compare again.");
        }
        List<ProviderMatchEvidence> evidence = new ArrayList<>();
        evidence.add(evidenceItem("DISPLAY_NAME_EXACT", equalsText(publicProfile.displayName(), platform.displayName()), publicProfile.displayName(), platform.displayName(), publicProfile.sourceRevision(), "Doctor display names compared."));
        evidence.add(evidenceItem("SPECIALTY_EXACT", equalsText(publicProfile.area(), platform.specialty()), publicProfile.area(), platform.specialty(), publicProfile.sourceRevision(), "Doctor specialty compared."));
        evidence.add(evidenceItem("QUALIFICATION_COMPATIBLE", equalsText(publicProfile.publicFee(), platform.qualification()), publicProfile.publicFee(), platform.qualification(), publicProfile.sourceRevision(), "Qualification compared."));
        evidence.add(evidenceItem("REGISTRATION_EXACT", equalsText(platform.registrationNumber(), platform.registrationNumber()), platform.registrationNumber(), platform.registrationNumber(), platform.sourceRevision(), "Registration number validated."));
        evidence.add(evidenceItem("PHONE_EXACT", equalsText(publicProfile.publicPhone(), platform.phone()), publicProfile.publicPhone(), platform.phone(), publicProfile.sourceRevision(), "Phone compared."));
        return new ProposalResolution(deriveDoctorMatchMethod(evidence), deriveConfidence(evidence), evidenceJson(evidence), evidence, publicProfile, platform);
    }

    private boolean equalsText(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
            return true;
        }
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean equalsPhone(String left, String right) {
        String normalizedLeft = normalizePhone(left);
        String normalizedRight = normalizePhone(right);
        return StringUtils.hasText(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private String normalizePhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private MatchConfidence normalizeMatchConfidenceValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MatchConfidence.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return MatchConfidence.LOW;
        }
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private ProviderConnectionsLinkProposalRequest suggestionRequest(ProviderConnectionsPublicProfileResponse profile, ProviderConnectionsPlatformEntityResponse platform) {
        return new ProviderConnectionsLinkProposalRequest(
                profile.publicProfileType(),
                profile.publicReference(),
                profile.publicPracticeReference(),
                platform.tenantReference(),
                platform.platformClinicReference(),
                platform.tenantDoctorUserReference(),
                platform.tenantDoctorProfileReference(),
                platform.sourceRevision(),
                profile.sourceSystem() == null ? SourceSystem.PLATFORM_ADMIN : profile.sourceSystem(),
                profile.publicReference(),
                profile.sourceRevision(),
                profile.sourceUpdatedAt(),
                LinkLifecycleStatus.PROPOSED,
                PlatformConnectionStatus.CONNECTION_PENDING,
                MatchMethod.MANUAL_REFERENCE,
                MatchConfidence.LOW,
                null,
                List.of()
        );
    }

    private boolean isRejectedSuggestion(PublicProfileType type, PublicProviderReference publicReference, String publicPracticeReference, ProviderConnectionsPlatformEntityResponse platform, ProposalResolution resolution) {
        String suggestionKey = suggestionKey(type, publicReference, publicPracticeReference, platform, resolution);
        return suggestionRejectionRepository.findBySuggestionKey(suggestionKey)
                .filter(entity -> entity.getSourceRevision() == resolution.publicProfile().sourceRevision())
                .isPresent();
    }

    private String suggestionKey(PublicProfileType type, PublicProviderReference publicReference, String publicPracticeReference, ProviderConnectionsPlatformEntityResponse platform, ProposalResolution resolution) {
        return String.join("|",
                type == null ? "" : type.name(),
                publicReference == null ? "" : value(publicReference.publicProviderId()),
                value(publicPracticeReference),
                value(platform.tenantReference()),
                value(platform.platformClinicReference()),
                String.valueOf(resolution.publicProfile().sourceRevision()),
                String.valueOf(platform.sourceRevision())
        );
    }

    private ProviderConnectionsSuggestionResponse toSuggestionResponse(
            ProviderConnectionsPublicProfileResponse profile,
            String publicPracticeReference,
            ProviderConnectionsPlatformEntityResponse platform,
            ProposalResolution resolution,
            String reason
    ) {
        String suggestionId = suggestionKey(profile.publicProfileType(), profile.publicReference() == null ? null : new PublicProviderReference(profile.publicReference(), publicPracticeReference), publicPracticeReference, platform, resolution);
        return new ProviderConnectionsSuggestionResponse(
                suggestionId,
                profile.publicProfileType(),
                profile.publicReference(),
                publicPracticeReference,
                profile.displayName(),
                platform.displayName(),
                platform.tenantReference(),
                platform.platformClinicReference(),
                platform.tenantDoctorUserReference(),
                platform.tenantDoctorProfileReference(),
                platform.city(),
                platform.area(),
                platform.phone(),
                platform.email(),
                platform.specialty(),
                platform.qualification(),
                platform.registrationNumber(),
                platform.yearsOfExperience(),
                platform.platformBookingSetup(),
                platform.currentDiscoverCapability(),
                platform.currentAvailability(),
                null,
                resolution.matchMethod(),
                resolution.matchConfidence(),
                resolution.evidence(),
                reason,
                "SUGGESTED",
                profile.sourceUpdatedAt(),
                profile.sourceRevision(),
                List.of("REVIEW_MATCH", "PROPOSE_LINK")
        );
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private PublicProviderSummary canonicalizePublishedIdentity(PublicProviderSummary summary) {
        if (summary == null || !StringUtils.hasText(summary.canonicalSlug())) {
            return summary;
        }
        return moderationService.findCurrentPublishedPublicationBySlug(summary.canonicalSlug())
                .map(publication -> new PublicProviderSummary(
                        summary.publicProfileType(),
                        new PublicProviderReference(
                                publication.publicProfileReference(),
                                summary.publicReference() == null ? null : summary.publicReference().publicPracticeId()),
                        summary.canonicalSlug(),
                        summary.displayName(),
                        summary.area(),
                        summary.city(),
                        summary.state(),
                        summary.country(),
                        summary.publicPhone(),
                        summary.publicFee(),
                        summary.bookingCapability(),
                        summary.availabilityState(),
                        summary.publicationStatus(),
                        SourceSystem.DISCOVER_PROVIDER,
                        publication.publishedVersion(),
                        publication.publishedAt(),
                        summary.projectedAt()))
                .orElse(summary);
    }

    private String immutableRegistrationNumber(String publicProfileReference) {
        return moderationService.currentSubmission(publicProfileReference)
                .map(submission -> submission.contentSnapshot().get("about"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(about -> about.get("registrationNumber"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    private record ProposalResolution(
            MatchMethod matchMethod,
            MatchConfidence matchConfidence,
            String evidenceJson,
            List<ProviderMatchEvidence> evidence,
            ProviderConnectionsPublicProfileResponse publicProfile,
            ProviderConnectionsPlatformEntityResponse platformEntity
    ) {
    }

    private ProviderType toProviderType(PublicProfileType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case DOCTOR -> ProviderType.INDIVIDUAL_DOCTOR;
            case CLINIC -> ProviderType.CLINIC;
            case HOSPITAL -> ProviderType.HOSPITAL;
        };
    }

    private PublicProfileType toPublicProfileType(ProviderType type) {
        if (type == null) {
            return PublicProfileType.CLINIC;
        }
        return switch (type) {
            case INDIVIDUAL_DOCTOR -> PublicProfileType.DOCTOR;
            case CLINIC -> PublicProfileType.CLINIC;
            case HOSPITAL -> PublicProfileType.HOSPITAL;
        };
    }

    private UUID tenantIdFromReference(String tenantReference) {
        if (!StringUtils.hasText(tenantReference)) {
            return null;
        }
        try {
            return UUID.fromString(tenantReference.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
