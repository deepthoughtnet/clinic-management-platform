package com.deepthoughtnet.clinic.platform.providerintegration.service;

import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchConfidence;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchMethod;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderSummary;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicationStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ReconciliationResult;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.port.PlatformConnectionPort;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import com.deepthoughtnet.clinic.platform.providerintegration.db.AbstractProviderLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkRepository;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkRepository;
import com.deepthoughtnet.clinic.platform.providerintegration.event.PlatformProviderLinkChangedEvent;
import com.deepthoughtnet.clinic.platform.providerintegration.model.PublicClinicPlatformLinkUpsertRequest;
import com.deepthoughtnet.clinic.platform.providerintegration.model.PublicDoctorPracticePlatformLinkUpsertRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderLinkingService implements PlatformConnectionPort {

    private static final String ENTITY_TYPE_CLINIC_LINK = "PUBLIC_CLINIC_PLATFORM_LINK";
    private static final String ENTITY_TYPE_DOCTOR_PRACTICE_LINK = "PUBLIC_DOCTOR_PRACTICE_PLATFORM_LINK";

    private final PublicClinicPlatformLinkRepository clinicRepository;
    private final PublicDoctorPracticePlatformLinkRepository doctorRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ModuleBusinessEventPublisher moduleBusinessEventPublisher;
    private final Clock providerIntegrationClock;

    public ProviderLinkingService(
            PublicClinicPlatformLinkRepository clinicRepository,
            PublicDoctorPracticePlatformLinkRepository doctorRepository,
            AuditEventPublisher auditEventPublisher,
            ModuleBusinessEventPublisher moduleBusinessEventPublisher,
            Clock providerIntegrationClock
    ) {
        this.clinicRepository = clinicRepository;
        this.doctorRepository = doctorRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.moduleBusinessEventPublisher = moduleBusinessEventPublisher;
        this.providerIntegrationClock = providerIntegrationClock;
    }

    @Transactional
    public PublicClinicPlatformLinkEntity upsertClinicLink(PublicClinicPlatformLinkUpsertRequest request) {
        return mutateClinicLink(request).entity();
    }

    @Transactional
    public PublicDoctorPracticePlatformLinkEntity upsertDoctorPracticeLink(PublicDoctorPracticePlatformLinkUpsertRequest request) {
        return mutateDoctorPracticeLink(request).entity();
    }

    @Transactional
    public ReconciliationResult reconcileClinicLink(PublicClinicPlatformLinkUpsertRequest request) {
        return toReconciliationResult("clinic-link", mutateClinicLink(request).outcome());
    }

    @Transactional
    public ReconciliationResult reconcileDoctorPracticeLink(PublicDoctorPracticePlatformLinkUpsertRequest request) {
        return toReconciliationResult("doctor-practice-link", mutateDoctorPracticeLink(request).outcome());
    }

    @Transactional(readOnly = true)
    public List<PublicClinicPlatformLinkEntity> listClinicLinks() {
        return clinicRepository.findAll().stream()
                .sorted(linkComparator())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicDoctorPracticePlatformLinkEntity> listDoctorPracticeLinks() {
        return doctorRepository.findAll().stream()
                .sorted(linkComparator())
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PublicClinicPlatformLinkEntity> findClinicLink(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return clinicRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<PublicDoctorPracticePlatformLinkEntity> findDoctorPracticeLink(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return doctorRepository.findById(id);
    }

    @Transactional
    public Optional<BookingTargetResolution> updateAvailabilityState(
            BookingTargetReference bookingTargetReference,
            AvailabilityState availabilityState,
            String actorReference,
            String reason
    ) {
        if (bookingTargetReference == null || blank(bookingTargetReference.opaqueBookingReference())) {
            return Optional.empty();
        }

        Optional<AbstractProviderLinkEntity> entity = findEntity(bookingTargetReference);
        if (entity.isEmpty()) {
            return Optional.empty();
        }

        AbstractProviderLinkEntity link = entity.get();
        AvailabilityState previousAvailability = link.getAvailabilityState();
        AvailabilityState nextAvailability = availabilityState == null ? AvailabilityState.UNKNOWN : availabilityState;
        if (Objects.equals(previousAvailability, nextAvailability)) {
            return Optional.of(toResolution(link));
        }

        link.setAvailabilityState(nextAvailability);
        link.setConnectionRevision(link.getConnectionRevision() + 1);
        OffsetDateTime now = now();
        link.setProjectedAt(now);
        touch(link, now);
        persist(link);
        recordAudit(link, "AVAILABILITY_STATE_UPDATED", actorReference, reason, json(
                "previousAvailability", previousAvailability,
                "newAvailability", nextAvailability
        ));
        return Optional.of(toResolution(link));
    }

    @Override
    @Transactional
    public Optional<BookingTargetResolution> findActivePracticeLink(BookingTargetReference bookingTargetReference) {
        return resolveBookingTarget(bookingTargetReference);
    }

    @Override
    @Transactional
    public BookingCapability resolveBookingCapability(BookingTargetReference bookingTargetReference) {
        return resolveBookingTarget(bookingTargetReference)
                .map(BookingTargetResolution::bookingCapability)
                .orElse(BookingCapability.NOT_AVAILABLE);
    }

    @Override
    @Transactional
    public AvailabilityState resolveAvailabilityState(BookingTargetReference bookingTargetReference) {
        return resolveBookingTarget(bookingTargetReference)
                .map(BookingTargetResolution::availabilityState)
                .orElse(AvailabilityState.UNKNOWN);
    }

    @Transactional
    public Optional<BookingTargetResolution> resolveBookingTarget(BookingTargetReference bookingTargetReference) {
        if (bookingTargetReference == null || blank(bookingTargetReference.opaqueBookingReference())) {
            return Optional.empty();
        }

        return findEntity(bookingTargetReference).map(this::toResolution);
    }

    @Transactional(readOnly = true)
    public Optional<BookingTargetResolution> resolveBookingTarget(PublicProviderReference publicReference) {
        if (publicReference == null || blank(publicReference.publicProviderId())) {
            return Optional.empty();
        }

        Optional<PublicClinicPlatformLinkEntity> clinic = clinicRepository.findAll().stream()
                .filter(AbstractProviderLinkEntity::isActive)
                .filter(entity -> publicReference.publicProviderId().equals(entity.getPublicClinicReference()))
                .findFirst();
        if (clinic.isPresent()) {
            return clinic.map(this::toResolution);
        }

        java.util.stream.Stream<PublicDoctorPracticePlatformLinkEntity> doctorLinks = doctorRepository.findAll().stream()
                .filter(AbstractProviderLinkEntity::isActive)
                .filter(entity -> publicReference.publicProviderId().equals(entity.getPublicDoctorReference()));
        if (blank(publicReference.publicPracticeId())) {
            return doctorLinks.findFirst().map(this::toResolution);
        }
        return doctorLinks
                .filter(entity -> publicReference.publicPracticeId().equals(entity.getPublicPracticeReference()))
                .findFirst()
                .map(this::toResolution);
    }

    @Transactional
    public Optional<PublicProviderSummary> toPublicSummary(BookingTargetReference bookingTargetReference) {
        return resolveBookingTarget(bookingTargetReference).map(resolution -> new PublicProviderSummary(
                resolution.publicProfileType(),
                resolution.publicReference(),
                resolution.sourceReference() == null ? null : resolution.sourceReference().sourceEntityReference(),
                resolution.publicReference() == null ? null : resolution.publicReference().publicProviderId(),
                null,
                null,
                null,
                null,
                null,
                null,
                resolution.bookingCapability(),
                resolution.availabilityState(),
                PublicationStatus.PUBLISHED,
                resolution.sourceReference() == null ? null : resolution.sourceReference().sourceSystem(),
                resolution.sourceReference() == null ? 0 : resolution.sourceReference().sourceRevision(),
                resolution.sourceReference() == null ? null : resolution.sourceReference().sourceUpdatedAt(),
                resolution.resolvedAt()
        ));
    }

    private MutationResult<PublicClinicPlatformLinkEntity> mutateClinicLink(PublicClinicPlatformLinkUpsertRequest request) {
        validateClinicRequest(request);
        OffsetDateTime now = now();
        Optional<PublicClinicPlatformLinkEntity> existingOpt = clinicRepository
                .findByPublicClinicReferenceAndTenantReferenceAndPlatformClinicReference(
                        request.publicClinicReference(),
                        request.tenantReference(),
                        request.platformClinicReference()
                );

        if (existingOpt.isPresent()) {
            PublicClinicPlatformLinkEntity existing = existingOpt.get();
            long previousRevision = existing.getSourceRevision();
            if (request.sourceReference().sourceRevision() < previousRevision) {
                throw new ProviderConnectionConflictException("stale_match_revision", "The reviewed public profile revision is stale. Reload the candidate before continuing.");
            }

            validateClinicTransition(existing, request);
            validateClinicActiveConflict(existing.getId(), request);
            if (sameClinicRequest(existing, request)) {
                return new MutationResult<>(existing, MutationOutcome.UNCHANGED);
            }

            LinkLifecycleStatus previousState = existing.getLinkStatus();
            boolean changed = applyClinicRequest(existing, request, now, false);
            clinicRepository.save(existing);
            recordAudit(existing, clinicAction(existing), request.actorReference(), request.reason(), transitionDetails(existing, previousState));
            publishLinkChanged(existing, clinicAction(existing), request.actorReference());
            return new MutationResult<>(existing, changed ? MutationOutcome.UPDATED : MutationOutcome.UNCHANGED);
        }

        validateClinicActiveConflict(null, request);
        PublicClinicPlatformLinkEntity created = new PublicClinicPlatformLinkEntity();
        created.setId(UUID.randomUUID());
        applyClinicRequest(created, request, now, true);
        clinicRepository.save(created);
        recordAudit(created, clinicAction(created), request.actorReference(), request.reason(), transitionDetails(created, null));
        publishLinkChanged(created, clinicAction(created), request.actorReference());
        return new MutationResult<>(created, MutationOutcome.INSERTED);
    }

    private MutationResult<PublicDoctorPracticePlatformLinkEntity> mutateDoctorPracticeLink(PublicDoctorPracticePlatformLinkUpsertRequest request) {
        validateDoctorRequest(request);
        OffsetDateTime now = now();
        Optional<PublicDoctorPracticePlatformLinkEntity> existingOpt = doctorRepository
                .findByPublicDoctorReferenceAndPublicPracticeReferenceAndTenantReferenceAndPlatformClinicReferenceAndTenantDoctorUserReference(
                        request.publicDoctorReference().publicProviderId(),
                        request.publicPracticeReference().publicPracticeId(),
                        request.tenantReference(),
                        request.platformClinicReference(),
                        request.tenantDoctorUserReference()
                );

        if (existingOpt.isPresent()) {
            PublicDoctorPracticePlatformLinkEntity existing = existingOpt.get();
            long previousRevision = existing.getSourceRevision();
            if (request.sourceReference().sourceRevision() < previousRevision) {
                throw new ProviderConnectionConflictException("stale_match_revision", "The reviewed public profile revision is stale. Reload the candidate before continuing.");
            }

            validateDoctorTransition(existing, request);
            validateDoctorActiveConflict(existing.getId(), request);
            if (sameDoctorRequest(existing, request)) {
                return new MutationResult<>(existing, MutationOutcome.UNCHANGED);
            }

            LinkLifecycleStatus previousState = existing.getLinkStatus();
            boolean changed = applyDoctorRequest(existing, request, now, false);
            doctorRepository.save(existing);
            recordAudit(existing, doctorAction(existing), request.actorReference(), request.reason(), transitionDetails(existing, previousState));
            publishLinkChanged(existing, doctorAction(existing), request.actorReference());
            return new MutationResult<>(existing, changed ? MutationOutcome.UPDATED : MutationOutcome.UNCHANGED);
        }

        validateDoctorActiveConflict(null, request);
        PublicDoctorPracticePlatformLinkEntity created = new PublicDoctorPracticePlatformLinkEntity();
        created.setId(UUID.randomUUID());
        applyDoctorRequest(created, request, now, true);
        doctorRepository.save(created);
        recordAudit(created, doctorAction(created), request.actorReference(), request.reason(), transitionDetails(created, null));
        publishLinkChanged(created, doctorAction(created), request.actorReference());
        return new MutationResult<>(created, MutationOutcome.INSERTED);
    }

    private boolean applyClinicRequest(PublicClinicPlatformLinkEntity entity, PublicClinicPlatformLinkUpsertRequest request, OffsetDateTime now, boolean isNew) {
        BookingCapability previousCapability = entity.getBookingCapability();
        AvailabilityState previousAvailability = entity.getAvailabilityState();
        long previousCapabilityVersion = entity.getCapabilityVersion();
        long previousConnectionRevision = entity.getConnectionRevision();
        boolean previousActive = entity.isActive();
        OffsetDateTime previousLinkedAt = entity.getLinkedAt();
        OffsetDateTime previousUnlinkedAt = entity.getUnlinkedAt();

        entity.setProviderType(PublicProfileType.CLINIC);
        entity.setSourceSystem(request.sourceReference().sourceSystem());
        entity.setSourceEntityReference(request.sourceReference().sourceEntityReference());
        entity.setSourceRevision(request.sourceReference().sourceRevision());
        entity.setSourceUpdatedAt(request.sourceReference().sourceUpdatedAt());
        entity.setTenantReference(request.tenantReference());
        entity.setPlatformClinicReference(request.platformClinicReference());
        entity.setPublicClinicReference(request.publicClinicReference());
        entity.setLinkStatus(normalizeLinkStatus(request.linkStatus()));
        entity.setConnectionStatus(normalizeConnectionStatus(request.connectionStatus()));
        validateLifecycleCompatibility(entity.getLinkStatus(), entity.getConnectionStatus());
        entity.setMatchMethod(normalizeMatchMethod(request.matchMethod()));
        entity.setMatchConfidence(normalizeMatchConfidence(request.matchConfidence()));
        entity.setAvailabilityState(normalizeAvailability(request.availabilityState()));
        entity.setEvidenceSnapshotJson(request.evidenceSnapshotJson());
        entity.setActive(isActive(entity.getLinkStatus()));
        entity.setReason(request.reason());
        applyLifecycleMetadata(entity, request.actorReference(), now);
        entity.setCapabilityReason(request.capabilityReason());
        entity.setLinkedAt(isNew || (!previousActive && entity.isActive()) ? now : previousLinkedAt);
        entity.setUnlinkedAt(isTerminalDisconnect(entity.getLinkStatus()) && previousActive ? now : previousUnlinkedAt);
        entity.setConnectionRevision(isNew ? 1 : previousConnectionRevision + 1);
        BookingCapability derived = deriveBookingCapability(entity.isActive(), entity.getLinkStatus(), entity.getConnectionStatus(), request.operationalBookingCapability());
        if (isNew || derived != previousCapability) {
            entity.setCapabilityVersion(isNew ? 1 : previousCapabilityVersion + 1);
        }
        entity.setBookingCapability(derived);
        if (!StringUtils.hasText(entity.getBookingReference())) {
            entity.setBookingReference(UUID.randomUUID().toString());
        }
        entity.setProjectedAt(now);
        touch(entity, now);
        return isNew
                || !Objects.equals(previousCapability, entity.getBookingCapability())
                || !Objects.equals(previousAvailability, entity.getAvailabilityState())
                || previousConnectionRevision != entity.getConnectionRevision()
                || previousCapabilityVersion != entity.getCapabilityVersion();
    }

    private boolean applyDoctorRequest(PublicDoctorPracticePlatformLinkEntity entity, PublicDoctorPracticePlatformLinkUpsertRequest request, OffsetDateTime now, boolean isNew) {
        BookingCapability previousCapability = entity.getBookingCapability();
        AvailabilityState previousAvailability = entity.getAvailabilityState();
        long previousCapabilityVersion = entity.getCapabilityVersion();
        long previousConnectionRevision = entity.getConnectionRevision();
        boolean previousActive = entity.isActive();
        OffsetDateTime previousLinkedAt = entity.getLinkedAt();
        OffsetDateTime previousUnlinkedAt = entity.getUnlinkedAt();

        entity.setProviderType(PublicProfileType.DOCTOR);
        entity.setSourceSystem(request.sourceReference().sourceSystem());
        entity.setSourceEntityReference(request.sourceReference().sourceEntityReference());
        entity.setSourceRevision(request.sourceReference().sourceRevision());
        entity.setSourceUpdatedAt(request.sourceReference().sourceUpdatedAt());
        entity.setTenantReference(request.tenantReference());
        entity.setPlatformClinicReference(request.platformClinicReference());
        entity.setPublicDoctorReference(request.publicDoctorReference().publicProviderId());
        entity.setPublicPracticeReference(request.publicPracticeReference().publicPracticeId());
        entity.setTenantDoctorUserReference(request.tenantDoctorUserReference());
        entity.setTenantDoctorProfileReference(request.tenantDoctorProfileReference());
        entity.setLinkStatus(normalizeLinkStatus(request.linkStatus()));
        entity.setConnectionStatus(normalizeConnectionStatus(request.connectionStatus()));
        validateLifecycleCompatibility(entity.getLinkStatus(), entity.getConnectionStatus());
        entity.setMatchMethod(normalizeMatchMethod(request.matchMethod()));
        entity.setMatchConfidence(normalizeMatchConfidence(request.matchConfidence()));
        entity.setAvailabilityState(normalizeAvailability(request.availabilityState()));
        entity.setEvidenceSnapshotJson(request.evidenceSnapshotJson());
        entity.setActive(isActive(entity.getLinkStatus()));
        entity.setReason(request.reason());
        applyLifecycleMetadata(entity, request.actorReference(), now);
        entity.setCapabilityReason(request.capabilityReason());
        entity.setLinkedAt(isNew || (!previousActive && entity.isActive()) ? now : previousLinkedAt);
        entity.setUnlinkedAt(isTerminalDisconnect(entity.getLinkStatus()) && previousActive ? now : previousUnlinkedAt);
        entity.setConnectionRevision(isNew ? 1 : previousConnectionRevision + 1);
        BookingCapability derived = deriveBookingCapability(entity.isActive(), entity.getLinkStatus(), entity.getConnectionStatus(), request.operationalBookingCapability());
        if (isNew || derived != previousCapability) {
            entity.setCapabilityVersion(isNew ? 1 : previousCapabilityVersion + 1);
        }
        entity.setBookingCapability(derived);
        if (!StringUtils.hasText(entity.getBookingReference())) {
            entity.setBookingReference(UUID.randomUUID().toString());
        }
        entity.setProjectedAt(now);
        touch(entity, now);
        return isNew
                || !Objects.equals(previousCapability, entity.getBookingCapability())
                || !Objects.equals(previousAvailability, entity.getAvailabilityState())
                || previousConnectionRevision != entity.getConnectionRevision()
                || previousCapabilityVersion != entity.getCapabilityVersion();
    }

    private BookingTargetResolution toResolution(AbstractProviderLinkEntity entity) {
        return new BookingTargetResolution(
                new BookingTargetReference(entity.getBookingReference(), entity.getCapabilityVersion()),
                new ProviderSourceReference(entity.getSourceSystem(), entity.getSourceEntityReference(), entity.getSourceRevision(), entity.getSourceUpdatedAt()),
                entity.getProviderType(),
                toPublicReference(entity),
                entity.getTenantReference(),
                entity.getPlatformClinicReference(),
                entity instanceof PublicDoctorPracticePlatformLinkEntity doctor ? doctor.getTenantDoctorUserReference() : null,
                entity instanceof PublicDoctorPracticePlatformLinkEntity doctor ? doctor.getTenantDoctorProfileReference() : null,
                entity.getBookingCapability(),
                entity.getAvailabilityState(),
                entity.getConnectionStatus(),
                entity.getLinkStatus(),
                entity.getCapabilityVersion(),
                entity.getConnectionRevision(),
                now()
        );
    }

    private PublicProviderReference toPublicReference(AbstractProviderLinkEntity entity) {
        if (entity instanceof PublicClinicPlatformLinkEntity clinic) {
            return new PublicProviderReference(clinic.getPublicClinicReference(), null);
        }
        PublicDoctorPracticePlatformLinkEntity doctor = (PublicDoctorPracticePlatformLinkEntity) entity;
        return new PublicProviderReference(doctor.getPublicDoctorReference(), doctor.getPublicPracticeReference());
    }

    private void persist(AbstractProviderLinkEntity entity) {
        if (entity instanceof PublicClinicPlatformLinkEntity clinic) {
            clinicRepository.save(clinic);
            return;
        }
        doctorRepository.save((PublicDoctorPracticePlatformLinkEntity) entity);
    }

    private void touch(AbstractProviderLinkEntity entity) {
        touch(entity, now());
    }

    private void touch(AbstractProviderLinkEntity entity, OffsetDateTime now) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }

    private void recordAudit(AbstractProviderLinkEntity entity, String action, String actorReference, String reason, String detailsJson) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantUuid(entity),
                entityType(entity),
                entity.getId(),
                action,
                actorUuid(entity, actorReference),
                now(),
                reason == null || reason.isBlank() ? action : reason,
                detailsJson
        ));
    }

    private void publishLinkChanged(AbstractProviderLinkEntity entity, String action, String actorReference) {
        moduleBusinessEventPublisher.publish(PlatformProviderLinkChangedEvent.changed(entity, action, actorReference, now()));
    }

    private ReconciliationResult toReconciliationResult(String scope, MutationOutcome outcome) {
        OffsetDateTime now = now();
        return switch (outcome) {
            case INSERTED -> new ReconciliationResult(scope, 1, 1, 0, 0, 0, 0, 0, List.of(), now, now);
            case UPDATED -> new ReconciliationResult(scope, 1, 0, 1, 0, 0, 0, 0, List.of(), now, now);
            case UNCHANGED -> new ReconciliationResult(scope, 1, 0, 0, 1, 0, 0, 0, List.of(), now, now);
            case SKIPPED -> new ReconciliationResult(scope, 1, 0, 0, 0, 1, 0, 0, List.of(), now, now);
            case CONFLICTED -> new ReconciliationResult(scope, 1, 0, 0, 0, 0, 1, 0, List.of(), now, now);
            case FAILED -> new ReconciliationResult(scope, 1, 0, 0, 0, 0, 0, 1, List.of(), now, now);
        };
    }

    private BookingCapability deriveBookingCapability(
            boolean active,
            LinkLifecycleStatus linkStatus,
            PlatformConnectionStatus connectionStatus,
            BookingCapability operationalCapability
    ) {
        if (!active) {
            return BookingCapability.CALL_TO_BOOK;
        }
        if (linkStatus == LinkLifecycleStatus.LINKED && connectionStatus == PlatformConnectionStatus.CONNECTED) {
            return operationalCapability == null ? BookingCapability.CALL_TO_BOOK : operationalCapability;
        }
        return BookingCapability.CALL_TO_BOOK;
    }

    private boolean isActive(LinkLifecycleStatus linkStatus) {
        return linkStatus == LinkLifecycleStatus.LINKED;
    }

    private LinkLifecycleStatus normalizeLinkStatus(LinkLifecycleStatus status) {
        return status == null ? LinkLifecycleStatus.SUGGESTED : status;
    }

    private PlatformConnectionStatus normalizeConnectionStatus(PlatformConnectionStatus status) {
        return status == null ? PlatformConnectionStatus.NOT_CONNECTED : status;
    }

    private MatchMethod normalizeMatchMethod(MatchMethod method) {
        return method == null ? MatchMethod.MANUAL_REFERENCE : method;
    }

    private String normalizeMatchConfidence(MatchConfidence confidence) {
        return confidence == null ? null : confidence.name();
    }

    private AvailabilityState normalizeAvailability(AvailabilityState state) {
        return state == null ? AvailabilityState.UNKNOWN : state;
    }

    private String entityType(AbstractProviderLinkEntity entity) {
        return entity instanceof PublicClinicPlatformLinkEntity ? ENTITY_TYPE_CLINIC_LINK : ENTITY_TYPE_DOCTOR_PRACTICE_LINK;
    }

    private String clinicAction(PublicClinicPlatformLinkEntity entity) {
        return "PUBLIC_CLINIC_PLATFORM_LINK_" + lifecycleAction(entity.getLinkStatus());
    }

    private String doctorAction(PublicDoctorPracticePlatformLinkEntity entity) {
        return "PUBLIC_DOCTOR_PRACTICE_PLATFORM_LINK_" + lifecycleAction(entity.getLinkStatus());
    }

    private String lifecycleAction(LinkLifecycleStatus status) {
        return switch (status) {
            case PROPOSED, PENDING_VERIFICATION -> "PROPOSED";
            case APPROVED -> "VERIFIED";
            case LINKED -> "ACTIVATED";
            case SUSPENDED -> "SUSPENDED";
            case REJECTED -> "REJECTED";
            case UNLINKED -> "DISCONNECTED";
            case DISPUTED -> "DISPUTED";
            case SUGGESTED -> "SUGGESTED";
        };
    }

    private void applyLifecycleMetadata(AbstractProviderLinkEntity entity, String actorReference, OffsetDateTime now) {
        switch (entity.getLinkStatus()) {
            case PROPOSED, PENDING_VERIFICATION -> {
                if (entity.getProposedAt() == null) {
                    entity.setProposedAt(now);
                    entity.setProposedBy(actorReference);
                }
            }
            case APPROVED -> {
                if (entity.getVerifiedAt() == null) {
                    entity.setVerifiedAt(now);
                    entity.setVerifiedBy(actorReference);
                }
            }
            case LINKED -> {
                if (entity.getActivatedAt() == null) {
                    entity.setActivatedAt(now);
                    entity.setActivatedBy(actorReference);
                }
            }
            case SUSPENDED -> {
                entity.setSuspendedAt(now);
                entity.setSuspendedBy(actorReference);
            }
            case UNLINKED, REJECTED -> {
                if (entity.getDisconnectedAt() == null) {
                    entity.setDisconnectedAt(now);
                    entity.setDisconnectedBy(actorReference);
                }
            }
            default -> { }
        }
    }

    private String transitionDetails(AbstractProviderLinkEntity entity, LinkLifecycleStatus previousState) {
        return json(
                "providerType", entity.getProviderType(),
                "publicProfileReference", toPublicReference(entity).publicProviderId(),
                "tenantReference", entity.getTenantReference(),
                "platformClinicReference", entity.getPlatformClinicReference(),
                "previousState", previousState,
                "newState", entity.getLinkStatus(),
                "connectionStatus", entity.getConnectionStatus(),
                "bookingCapability", entity.getBookingCapability(),
                "capabilityReason", entity.getCapabilityReason(),
                "sourceRevision", entity.getSourceRevision(),
                "result", "OK",
                "reason", entity.getReason(),
                "correlationId", currentCorrelationId()
        );
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (!StringUtils.hasText(correlationId)) {
            correlationId = MDC.get("X-Correlation-ID");
        }
        return correlationId;
    }

    private String clinicDetails(PublicClinicPlatformLinkEntity entity) {
        return json(
                "providerType", entity.getProviderType(),
                "sourceSystem", entity.getSourceSystem(),
                "publicClinicReference", entity.getPublicClinicReference(),
                "tenantReference", entity.getTenantReference(),
                "platformClinicReference", entity.getPlatformClinicReference(),
                "linkStatus", entity.getLinkStatus(),
                "connectionStatus", entity.getConnectionStatus(),
                "bookingCapability", entity.getBookingCapability(),
                "availabilityState", entity.getAvailabilityState(),
                "sourceRevision", entity.getSourceRevision(),
                "reason", entity.getReason()
        );
    }

    private String doctorDetails(PublicDoctorPracticePlatformLinkEntity entity) {
        return json(
                "providerType", entity.getProviderType(),
                "sourceSystem", entity.getSourceSystem(),
                "publicDoctorReference", entity.getPublicDoctorReference(),
                "publicPracticeReference", entity.getPublicPracticeReference(),
                "tenantReference", entity.getTenantReference(),
                "platformClinicReference", entity.getPlatformClinicReference(),
                "tenantDoctorUserReference", entity.getTenantDoctorUserReference(),
                "tenantDoctorProfileReference", entity.getTenantDoctorProfileReference(),
                "linkStatus", entity.getLinkStatus(),
                "connectionStatus", entity.getConnectionStatus(),
                "bookingCapability", entity.getBookingCapability(),
                "availabilityState", entity.getAvailabilityState(),
                "sourceRevision", entity.getSourceRevision(),
                "reason", entity.getReason()
        );
    }

    private String json(Object... values) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < values.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('\"').append(escape(String.valueOf(values[i]))).append('\"').append(':');
            Object value = values[i + 1];
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('\"').append(escape(String.valueOf(value))).append('\"');
            }
        }
        return builder.append('}').toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private UUID namespacedUuid(String namespace, AbstractProviderLinkEntity entity, String rawValue) {
        String seed = String.join("|",
                "provider-link-audit-v1",
                value(namespace),
                entity == null || entity.getProviderType() == null ? "" : entity.getProviderType().name(),
                entity == null || entity.getSourceSystem() == null ? "" : entity.getSourceSystem().name(),
                value(entity == null ? null : entity.getTenantReference()),
                value(entity == null ? null : entity.getSourceEntityReference()),
                value(rawValue)
        );
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private UUID actorUuid(AbstractProviderLinkEntity entity, String actorReference) {
        try {
            return UUID.fromString(actorReference);
        } catch (Exception ignored) {
            return namespacedUuid("actor", entity, actorReference);
        }
    }

    private UUID tenantUuid(AbstractProviderLinkEntity entity) {
        try {
            return UUID.fromString(entity.getTenantReference());
        } catch (Exception ignored) {
            return namespacedUuid("tenant", entity, entity.getTenantReference());
        }
    }

    private void validateLifecycleCompatibility(LinkLifecycleStatus linkStatus, PlatformConnectionStatus connectionStatus) {
        if (linkStatus == null || connectionStatus == null) {
            return;
        }
        if ((linkStatus == LinkLifecycleStatus.UNLINKED || linkStatus == LinkLifecycleStatus.REJECTED || linkStatus == LinkLifecycleStatus.SUSPENDED)
                && connectionStatus == PlatformConnectionStatus.CONNECTED) {
            throw new IllegalArgumentException("Invalid link/connection combination: " + linkStatus + " with " + connectionStatus);
        }
        if (linkStatus == LinkLifecycleStatus.SUGGESTED
                && connectionStatus == PlatformConnectionStatus.CONNECTED) {
            throw new IllegalArgumentException("Suggested links cannot be marked as connected.");
        }
        if (linkStatus == LinkLifecycleStatus.APPROVED && connectionStatus != PlatformConnectionStatus.NOT_CONNECTED) {
            throw new IllegalArgumentException("Verified links must remain not connected until activation.");
        }
        if (linkStatus == LinkLifecycleStatus.LINKED && connectionStatus != PlatformConnectionStatus.CONNECTED) {
            throw new IllegalArgumentException("Active links must be connected.");
        }
    }

    private boolean isTerminalDisconnect(LinkLifecycleStatus status) {
        return status == LinkLifecycleStatus.UNLINKED || status == LinkLifecycleStatus.REJECTED;
    }

    private void validateNewLifecycle(LinkLifecycleStatus status) {
        if (status != LinkLifecycleStatus.PROPOSED && status != LinkLifecycleStatus.PENDING_VERIFICATION && status != LinkLifecycleStatus.SUGGESTED) {
            throw new ProviderConnectionConflictException("link_not_verifiable", "A new connection must begin as a reviewed proposal.");
        }
    }

    private void validateClinicTransition(PublicClinicPlatformLinkEntity entity, PublicClinicPlatformLinkUpsertRequest request) {
        validateTransition(entity.getLinkStatus(), request.linkStatus());
        if (!Objects.equals(entity.getPublicClinicReference(), request.publicClinicReference())
                || !Objects.equals(entity.getTenantReference(), request.tenantReference())
                || !Objects.equals(entity.getPlatformClinicReference(), request.platformClinicReference())) {
            throw new ProviderConnectionConflictException("connection_target_changed", "Verification cannot change the reviewed connection target.");
        }
    }

    private void validateDoctorTransition(PublicDoctorPracticePlatformLinkEntity entity, PublicDoctorPracticePlatformLinkUpsertRequest request) {
        validateTransition(entity.getLinkStatus(), request.linkStatus());
        if (!Objects.equals(entity.getPublicDoctorReference(), request.publicDoctorReference().publicProviderId())
                || !Objects.equals(entity.getPublicPracticeReference(), request.publicPracticeReference().publicPracticeId())
                || !Objects.equals(entity.getTenantReference(), request.tenantReference())
                || !Objects.equals(entity.getPlatformClinicReference(), request.platformClinicReference())
                || !Objects.equals(entity.getTenantDoctorUserReference(), request.tenantDoctorUserReference())) {
            throw new ProviderConnectionConflictException("connection_target_changed", "Verification cannot change the reviewed connection target.");
        }
    }

    private void validateTransition(LinkLifecycleStatus previous, LinkLifecycleStatus next) {
        if (previous == next) {
            return;
        }
        boolean allowed = switch (previous) {
            case SUGGESTED -> next == LinkLifecycleStatus.PROPOSED || next == LinkLifecycleStatus.REJECTED;
            case PENDING_VERIFICATION, PROPOSED -> next == LinkLifecycleStatus.APPROVED || next == LinkLifecycleStatus.REJECTED;
            case APPROVED -> next == LinkLifecycleStatus.LINKED || next == LinkLifecycleStatus.REJECTED;
            case LINKED -> next == LinkLifecycleStatus.UNLINKED || next == LinkLifecycleStatus.SUSPENDED || next == LinkLifecycleStatus.DISPUTED;
            case SUSPENDED -> next == LinkLifecycleStatus.LINKED || next == LinkLifecycleStatus.UNLINKED;
            case UNLINKED, REJECTED -> next == LinkLifecycleStatus.PROPOSED;
            case DISPUTED -> next == LinkLifecycleStatus.LINKED || next == LinkLifecycleStatus.UNLINKED;
        };
        if (!allowed) {
            throw new ProviderConnectionConflictException("link_not_verifiable", "Connection transition " + previous + " -> " + next + " is not allowed.");
        }
    }

    private void validateClinicActiveConflict(UUID currentId, PublicClinicPlatformLinkUpsertRequest request) {
        if (request.linkStatus() != LinkLifecycleStatus.LINKED) {
            return;
        }
        boolean publicConflict = clinicRepository.findByPublicClinicReferenceAndActiveTrue(request.publicClinicReference()).stream()
                .anyMatch(row -> !Objects.equals(row.getId(), currentId));
        boolean targetConflict = clinicRepository.findByTenantReferenceAndPlatformClinicReferenceAndActiveTrue(request.tenantReference(), request.platformClinicReference()).stream()
                .anyMatch(row -> !Objects.equals(row.getId(), currentId));
        if (publicConflict || targetConflict) {
            throw new ProviderConnectionConflictException("conflicting_active_connection", "The public profile or operational clinic already has an active connection.");
        }
    }

    private void validateDoctorActiveConflict(UUID currentId, PublicDoctorPracticePlatformLinkUpsertRequest request) {
        if (request.linkStatus() != LinkLifecycleStatus.LINKED) {
            return;
        }
        boolean publicConflict = doctorRepository.findByPublicDoctorReferenceAndPublicPracticeReferenceAndActiveTrue(
                        request.publicDoctorReference().publicProviderId(), request.publicPracticeReference().publicPracticeId()).stream()
                .anyMatch(row -> !Objects.equals(row.getId(), currentId));
        boolean targetConflict = doctorRepository.findByTenantReferenceAndPlatformClinicReferenceAndTenantDoctorUserReferenceAndActiveTrue(
                        request.tenantReference(), request.platformClinicReference(), request.tenantDoctorUserReference()).stream()
                .anyMatch(row -> !Objects.equals(row.getId(), currentId));
        if (publicConflict || targetConflict) {
            throw new ProviderConnectionConflictException("conflicting_active_connection", "The public practice or operational doctor already has an active connection.");
        }
    }

    private boolean sameClinicRequest(PublicClinicPlatformLinkEntity entity, PublicClinicPlatformLinkUpsertRequest request) {
        return sameCommonRequest(entity, request.sourceReference(), request.linkStatus(), request.connectionStatus(), request.matchMethod(),
                request.matchConfidence(), request.availabilityState(), request.evidenceSnapshotJson(), request.reason(), request.operationalBookingCapability(), request.capabilityReason());
    }

    private boolean sameDoctorRequest(PublicDoctorPracticePlatformLinkEntity entity, PublicDoctorPracticePlatformLinkUpsertRequest request) {
        return sameCommonRequest(entity, request.sourceReference(), request.linkStatus(), request.connectionStatus(), request.matchMethod(),
                request.matchConfidence(), request.availabilityState(), request.evidenceSnapshotJson(), request.reason(), request.operationalBookingCapability(), request.capabilityReason());
    }

    private boolean sameCommonRequest(
            AbstractProviderLinkEntity entity,
            ProviderSourceReference sourceReference,
            LinkLifecycleStatus linkStatus,
            PlatformConnectionStatus connectionStatus,
            MatchMethod matchMethod,
            MatchConfidence matchConfidence,
            AvailabilityState availabilityState,
            String evidenceJson,
            String reason,
            BookingCapability operationalCapability,
            String capabilityReason
    ) {
        BookingCapability expected = deriveBookingCapability(isActive(linkStatus), linkStatus, connectionStatus, operationalCapability);
        return entity.getSourceRevision() == sourceReference.sourceRevision()
                && entity.getLinkStatus() == linkStatus
                && entity.getConnectionStatus() == connectionStatus
                && entity.getMatchMethod() == matchMethod
                && Objects.equals(entity.getMatchConfidence(), matchConfidence == null ? null : matchConfidence.name())
                && entity.getAvailabilityState() == normalizeAvailability(availabilityState)
                && Objects.equals(entity.getReason(), reason)
                && entity.getBookingCapability() == expected
                && Objects.equals(entity.getCapabilityReason(), capabilityReason);
    }

    private Comparator<AbstractProviderLinkEntity> linkComparator() {
        return Comparator
                .comparing(AbstractProviderLinkEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AbstractProviderLinkEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(providerIntegrationClock);
    }

    private void validateClinicRequest(PublicClinicPlatformLinkUpsertRequest request) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(request.sourceReference(), "sourceReference is required");
        if (blank(request.publicClinicReference())) {
            throw new IllegalArgumentException("publicClinicReference is required");
        }
        if (request.linkStatus() == null) {
            throw new IllegalArgumentException("linkStatus is required");
        }
        if (request.connectionStatus() == null) {
            throw new IllegalArgumentException("connectionStatus is required");
        }
        if (request.matchMethod() == null) {
            throw new IllegalArgumentException("matchMethod is required");
        }
    }

    private void validateDoctorRequest(PublicDoctorPracticePlatformLinkUpsertRequest request) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(request.sourceReference(), "sourceReference is required");
        Objects.requireNonNull(request.publicDoctorReference(), "publicDoctorReference is required");
        Objects.requireNonNull(request.publicPracticeReference(), "publicPracticeReference is required");
        if (blank(request.publicDoctorReference().publicProviderId())) {
            throw new IllegalArgumentException("doctor publicProviderId is required");
        }
        if (blank(request.publicPracticeReference().publicPracticeId())) {
            throw new IllegalArgumentException("practice publicPracticeId is required");
        }
        if (request.linkStatus() == null) {
            throw new IllegalArgumentException("linkStatus is required");
        }
        if (request.connectionStatus() == null) {
            throw new IllegalArgumentException("connectionStatus is required");
        }
        if (request.matchMethod() == null) {
            throw new IllegalArgumentException("matchMethod is required");
        }
    }

    private Optional<AbstractProviderLinkEntity> findEntity(BookingTargetReference bookingTargetReference) {
        Optional<PublicClinicPlatformLinkEntity> clinic = clinicRepository.findByBookingReferenceAndActiveTrue(bookingTargetReference.opaqueBookingReference());
        if (clinic.isPresent()) {
            return clinic.map(entity -> (AbstractProviderLinkEntity) entity);
        }

        Optional<PublicDoctorPracticePlatformLinkEntity> doctor = doctorRepository.findByBookingReferenceAndActiveTrue(bookingTargetReference.opaqueBookingReference());
        if (doctor.isPresent()) {
            return doctor.map(entity -> (AbstractProviderLinkEntity) entity);
        }

        return Optional.empty();
    }

    private enum MutationOutcome {
        INSERTED,
        UPDATED,
        UNCHANGED,
        SKIPPED,
        CONFLICTED,
        FAILED
    }

    private record MutationResult<T>(T entity, MutationOutcome outcome) {
    }
}
