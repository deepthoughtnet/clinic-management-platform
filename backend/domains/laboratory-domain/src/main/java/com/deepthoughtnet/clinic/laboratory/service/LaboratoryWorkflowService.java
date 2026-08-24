package com.deepthoughtnet.clinic.laboratory.service;

import com.deepthoughtnet.clinic.laboratory.db.LaboratoryOrderedTestLifecycleEntity;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryOrderedTestLifecycleRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryReportPublicationArtifactEntity;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryReportPublicationArtifactRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryReportPublicationTestEntity;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryReportPublicationTestRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryResultRevisionEntity;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryResultRevisionRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratorySpecimenTestLinkEntity;
import com.deepthoughtnet.clinic.laboratory.db.LaboratorySpecimenTestLinkRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryTestVerificationEntity;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryTestVerificationRepository;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratoryOrderedTestView;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratoryReportArtifactView;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratoryResultRevisionCommand;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratorySpecimenLinkView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LaboratoryWorkflowService {
    private final LaboratoryOrderedTestLifecycleRepository lifecycleRepository;
    private final LaboratorySpecimenTestLinkRepository specimenLinkRepository;
    private final LaboratoryResultRevisionRepository resultRevisionRepository;
    private final LaboratoryTestVerificationRepository verificationRepository;
    private final LaboratoryReportPublicationArtifactRepository publicationArtifactRepository;
    private final LaboratoryReportPublicationTestRepository publicationTestRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public LaboratoryWorkflowService(
            LaboratoryOrderedTestLifecycleRepository lifecycleRepository,
            LaboratorySpecimenTestLinkRepository specimenLinkRepository,
            LaboratoryResultRevisionRepository resultRevisionRepository,
            LaboratoryTestVerificationRepository verificationRepository,
            LaboratoryReportPublicationArtifactRepository publicationArtifactRepository,
            LaboratoryReportPublicationTestRepository publicationTestRepository,
            ObjectMapper objectMapper
    ) {
        this.lifecycleRepository = lifecycleRepository;
        this.specimenLinkRepository = specimenLinkRepository;
        this.resultRevisionRepository = resultRevisionRepository;
        this.verificationRepository = verificationRepository;
        this.publicationArtifactRepository = publicationArtifactRepository;
        this.publicationTestRepository = publicationTestRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<LaboratoryOrderedTestView> initializeOrderTests(UUID tenantId, UUID labOrderId, Collection<UUID> labOrderItemIds, UUID actorAppUserId) {
        if (tenantId == null || labOrderId == null || labOrderItemIds == null) {
            return List.of();
        }
        List<LaboratoryOrderedTestLifecycleEntity> entities = new ArrayList<>();
        for (UUID labOrderItemId : distinctIds(labOrderItemIds)) {
            if (labOrderItemId == null) {
                continue;
            }
            LaboratoryOrderedTestLifecycleEntity entity = lifecycleRepository.findByTenantIdAndLabOrderIdAndLabOrderItemId(tenantId, labOrderId, labOrderItemId)
                    .orElseGet(() -> LaboratoryOrderedTestLifecycleEntity.create(tenantId, labOrderId, labOrderItemId, "ORDERED", actorAppUserId));
            entities.add(entity);
        }
        lifecycleRepository.saveAll(entities);
        return listOrderTests(tenantId, labOrderId);
    }

    @Transactional(readOnly = true)
    public List<LaboratoryOrderedTestView> listOrderTests(UUID tenantId, UUID labOrderId) {
        if (tenantId == null || labOrderId == null) {
            return List.of();
        }
        List<LaboratoryOrderedTestLifecycleEntity> lifecycles = lifecycleRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtAsc(tenantId, labOrderId);
        if (lifecycles.isEmpty()) {
            return List.of();
        }
        Map<UUID, LaboratoryOrderedTestLifecycleEntity> lifecycleByItem = lifecycles.stream()
                .collect(Collectors.toMap(LaboratoryOrderedTestLifecycleEntity::getLabOrderItemId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<UUID, List<LaboratorySpecimenTestLinkEntity>> specimenLinksByItem = specimenLinkRepository.findByTenantIdAndLabOrderIdOrderByLinkedAtAsc(tenantId, labOrderId).stream()
                .collect(Collectors.groupingBy(LaboratorySpecimenTestLinkEntity::getLabOrderItemId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, LaboratoryResultRevisionEntity> latestRevisionByItem = resultRevisionRepository.findByTenantIdAndLabOrderIdOrderByLabOrderItemIdAscRevisionNumberDesc(tenantId, labOrderId).stream()
                .collect(Collectors.toMap(
                        LaboratoryResultRevisionEntity::getLabOrderItemId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<UUID, LaboratoryTestVerificationEntity> latestVerificationByItem = verificationRepository.findByTenantIdAndLabOrderIdOrderByVerifiedAtDesc(tenantId, labOrderId).stream()
                .collect(Collectors.toMap(
                        LaboratoryTestVerificationEntity::getLabOrderItemId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<UUID, LaboratoryReportPublicationArtifactEntity> publicationArtifactById = publicationArtifactRepository.findByTenantIdAndLabOrderIdOrderByArtifactNumberDesc(tenantId, labOrderId).stream()
                .collect(Collectors.toMap(
                        LaboratoryReportPublicationArtifactEntity::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<UUID, LaboratoryReportPublicationTestEntity> latestPublicationTestByItem = new LinkedHashMap<>();
        for (LaboratoryOrderedTestLifecycleEntity lifecycle : lifecycles) {
            publicationTestRepository.findTopByTenantIdAndLabOrderItemIdOrderByIncludedAtDesc(tenantId, lifecycle.getLabOrderItemId())
                    .ifPresent(test -> latestPublicationTestByItem.put(lifecycle.getLabOrderItemId(), test));
        }
        return lifecycles.stream()
                .map(lifecycle -> new LaboratoryOrderedTestView(
                        lifecycle.getLabOrderItemId(),
                        lifecycle.getState(),
                        lifecycle.getLatestResultRevision(),
                        latestVerificationByItem.get(lifecycle.getLabOrderItemId()) == null ? null : latestVerificationByItem.get(lifecycle.getLabOrderItemId()).getDecision(),
                        latestVerificationByItem.get(lifecycle.getLabOrderItemId()) == null ? null : latestVerificationByItem.get(lifecycle.getLabOrderItemId()).getVerifiedAt(),
                        latestVerificationByItem.get(lifecycle.getLabOrderItemId()) == null ? null : latestVerificationByItem.get(lifecycle.getLabOrderItemId()).getVerifiedBy(),
                        latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()) == null
                                ? null
                                : publicationArtifactById.get(latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()).getPublicationArtifactId()) == null
                                ? null
                                : publicationArtifactById.get(latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()).getPublicationArtifactId()).getArtifactNumber(),
                        latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()) == null
                                ? null
                                : publicationArtifactById.get(latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()).getPublicationArtifactId()) == null
                                ? null
                                : publicationArtifactById.get(latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()).getPublicationArtifactId()).getPublishedAt(),
                        latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()) == null
                                ? null
                                : publicationArtifactById.get(latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()).getPublicationArtifactId()) == null
                                ? null
                                : publicationArtifactById.get(latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()).getPublicationArtifactId()).getPublishedBy(),
                        latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()) == null
                                ? List.of()
                                : publicationArtifactById.get(latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()).getPublicationArtifactId()) == null
                                ? List.of()
                                : parseStrings(publicationArtifactById.get(latestPublicationTestByItem.get(lifecycle.getLabOrderItemId()).getPublicationArtifactId()).getDeliveryChannels()),
                        latestRevisionByItem.get(lifecycle.getLabOrderItemId()) == null ? null : latestRevisionByItem.get(lifecycle.getLabOrderItemId()).getResultSnapshot(),
                        latestRevisionByItem.get(lifecycle.getLabOrderItemId()) == null ? null : latestRevisionByItem.get(lifecycle.getLabOrderItemId()).getEnteredAt(),
                        latestRevisionByItem.get(lifecycle.getLabOrderItemId()) == null ? null : latestRevisionByItem.get(lifecycle.getLabOrderItemId()).getEnteredBy(),
                        specimenLinksByItem.getOrDefault(lifecycle.getLabOrderItemId(), List.of()).stream()
                                .map(link -> new LaboratorySpecimenLinkView(
                                        link.getLabOrderSampleId(),
                                        link.getSampleAccessionNumber(),
                                        link.getSampleBarcodeValue(),
                                        link.getSampleSpecimenType(),
                                        link.getSampleContainerType(),
                                        link.getSampleStatus(),
                                        link.isActive(),
                                        link.getCollectedAt(),
                                        link.getReceivedAt(),
                                        link.getLinkedAt(),
                                        link.getUnlinkedAt()
                                ))
                                .toList()
                ))
                .toList();
    }

    @Transactional
    public List<LaboratoryOrderedTestView> linkSpecimenToTests(
            UUID tenantId,
            UUID labOrderId,
            UUID labOrderSampleId,
            Collection<UUID> orderedTestIds,
            String sampleAccessionNumber,
            String sampleBarcodeValue,
            String sampleSpecimenType,
            String sampleContainerType,
            String sampleStatus,
            OffsetDateTime collectedAt,
            OffsetDateTime receivedAt,
            UUID actorAppUserId
    ) {
        for (UUID orderedTestId : distinctIds(orderedTestIds)) {
            if (orderedTestId == null) {
                continue;
            }
            LaboratorySpecimenTestLinkEntity entity = specimenLinkRepository.findByTenantIdAndLabOrderSampleIdAndLabOrderItemId(tenantId, labOrderSampleId, orderedTestId)
                    .map(existing -> {
                        existing.reactivate(sampleAccessionNumber, sampleBarcodeValue, sampleSpecimenType, sampleContainerType, sampleStatus, collectedAt, receivedAt, actorAppUserId);
                        return existing;
                    })
                    .orElseGet(() -> LaboratorySpecimenTestLinkEntity.create(tenantId, labOrderId, labOrderSampleId, orderedTestId, sampleAccessionNumber, sampleBarcodeValue, sampleSpecimenType, sampleContainerType, sampleStatus, collectedAt, receivedAt, actorAppUserId));
            specimenLinkRepository.save(entity);
            LaboratoryOrderedTestLifecycleEntity lifecycle = lifecycleRepository.findByTenantIdAndLabOrderIdAndLabOrderItemId(tenantId, labOrderId, orderedTestId)
                    .orElseGet(() -> LaboratoryOrderedTestLifecycleEntity.create(tenantId, labOrderId, orderedTestId, "ORDERED", actorAppUserId));
            lifecycle.markState("SAMPLE_COLLECTED", actorAppUserId);
            lifecycleRepository.save(lifecycle);
        }
        return listOrderTests(tenantId, labOrderId);
    }

    @Transactional
    public List<LaboratoryOrderedTestView> rejectSpecimenForTests(
            UUID tenantId,
            UUID labOrderId,
            UUID labOrderSampleId,
            Collection<UUID> orderedTestIds,
            UUID actorAppUserId
    ) {
        List<LaboratorySpecimenTestLinkEntity> links = specimenLinkRepository.findByTenantIdAndLabOrderSampleIdAndActiveTrue(tenantId, labOrderSampleId);
        if (orderedTestIds != null && !orderedTestIds.isEmpty()) {
            links = links.stream().filter(link -> orderedTestIds.contains(link.getLabOrderItemId())).toList();
        }
        links.forEach(link -> {
            link.deactivate(actorAppUserId);
            specimenLinkRepository.save(link);
            lifecycleRepository.findByTenantIdAndLabOrderIdAndLabOrderItemId(tenantId, labOrderId, link.getLabOrderItemId())
                    .ifPresent(lifecycle -> {
                        lifecycle.markState("RECOLLECTION_REQUIRED", actorAppUserId);
                        lifecycleRepository.save(lifecycle);
                    });
        });
        return listOrderTests(tenantId, labOrderId);
    }

    @Transactional
    public List<LaboratoryOrderedTestView> recordResultRevisions(UUID tenantId, UUID labOrderId, List<LaboratoryResultRevisionCommand> commands, UUID actorAppUserId) {
        if (commands == null || commands.isEmpty()) {
            return listOrderTests(tenantId, labOrderId);
        }
        for (LaboratoryResultRevisionCommand command : commands) {
            if (command == null || command.labOrderItemId() == null) {
                continue;
            }
            int revisionNumber = command.revisionNumber() > 0
                    ? command.revisionNumber()
                    : resultRevisionRepository.findTopByTenantIdAndLabOrderItemIdOrderByRevisionNumberDesc(tenantId, command.labOrderItemId())
                            .map(existing -> existing.getRevisionNumber() + 1)
                            .orElse(1);
            LaboratoryResultRevisionEntity entity = LaboratoryResultRevisionEntity.create(
                    tenantId,
                    labOrderId,
                    command.labOrderItemId(),
                    revisionNumber,
                    command.sourceResultId(),
                    command.resultSnapshotJson(),
                    command.comments(),
                    StringUtils.hasText(command.submissionState()) ? command.submissionState().trim().toUpperCase() : "SUBMITTED",
                    command.enteredAt(),
                    command.enteredBy() == null ? actorAppUserId : command.enteredBy()
            );
            resultRevisionRepository.save(entity);
            LaboratoryOrderedTestLifecycleEntity lifecycle = lifecycleRepository.findByTenantIdAndLabOrderIdAndLabOrderItemId(tenantId, labOrderId, command.labOrderItemId())
                    .orElseGet(() -> LaboratoryOrderedTestLifecycleEntity.create(tenantId, labOrderId, command.labOrderItemId(), "ORDERED", actorAppUserId));
            lifecycle.markResultRevision(revisionNumber, "RESULT_SENT_BACK".equalsIgnoreCase(entity.getSubmissionState()) ? "RESULT_SENT_BACK" : "RESULT_ENTERED", actorAppUserId);
            lifecycleRepository.save(lifecycle);
        }
        return listOrderTests(tenantId, labOrderId);
    }

    @Transactional
    public List<LaboratoryOrderedTestView> verifyTests(
            UUID tenantId,
            UUID labOrderId,
            Collection<UUID> orderedTestIds,
            String decision,
            String reason,
            String comments,
            boolean recollectionRequired,
            UUID actorAppUserId
    ) {
        List<UUID> targetIds = distinctIds(orderedTestIds);
        if (targetIds.isEmpty()) {
            targetIds = lifecycleRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtAsc(tenantId, labOrderId).stream()
                    .map(LaboratoryOrderedTestLifecycleEntity::getLabOrderItemId)
                    .toList();
        }
        String normalizedDecision = normalize(decision);
        for (UUID labOrderItemId : targetIds) {
            LaboratoryOrderedTestLifecycleEntity lifecycle = lifecycleRepository.findByTenantIdAndLabOrderIdAndLabOrderItemId(tenantId, labOrderId, labOrderItemId)
                    .orElseGet(() -> LaboratoryOrderedTestLifecycleEntity.create(tenantId, labOrderId, labOrderItemId, "RESULT_ENTERED", actorAppUserId));
            Integer latestRevision = resultRevisionRepository.findTopByTenantIdAndLabOrderItemIdOrderByRevisionNumberDesc(tenantId, labOrderItemId)
                    .map(LaboratoryResultRevisionEntity::getRevisionNumber)
                    .orElse(null);
            verificationRepository.save(LaboratoryTestVerificationEntity.create(
                    tenantId,
                    labOrderId,
                    labOrderItemId,
                    latestRevision,
                    normalizedDecision,
                    reason,
                    comments,
                    actorAppUserId
            ));
            if ("APPROVE".equals(normalizedDecision)) {
                lifecycle.markState("VERIFIED", actorAppUserId);
            } else if (recollectionRequired) {
                lifecycle.markState("RECOLLECTION_REQUIRED", actorAppUserId);
            } else {
                lifecycle.markState("RESULT_SENT_BACK", actorAppUserId);
            }
            lifecycleRepository.save(lifecycle);
        }
        return listOrderTests(tenantId, labOrderId);
    }

    @Transactional
    public List<LaboratoryOrderedTestView> publishTests(
            UUID tenantId,
            UUID labOrderId,
            Collection<UUID> orderedTestIds,
            String verificationToken,
            String filename,
            String storageReference,
            List<String> deliveryChannels,
            String reportMode,
            String reportType,
            String notes,
            UUID actorAppUserId
    ) {
        return persistReportArtifact(
                tenantId,
                labOrderId,
                orderedTestIds,
                verificationToken,
                filename,
                storageReference,
                deliveryChannels,
                reportMode,
                reportType,
                LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_CURRENT,
                notes,
                actorAppUserId,
                true
        );
    }

    /**
     * Stores a supplementary report artifact without replacing the patient-facing
     * current report or replaying ordered-test publication transitions.
     */
    @Transactional
    public List<LaboratoryOrderedTestView> generateReportArtifact(
            UUID tenantId,
            UUID labOrderId,
            Collection<UUID> orderedTestIds,
            String verificationToken,
            String filename,
            String storageReference,
            List<String> deliveryChannels,
            String reportMode,
            String reportType,
            String reportStatus,
            String notes,
            UUID actorAppUserId
    ) {
        return persistReportArtifact(
                tenantId,
                labOrderId,
                orderedTestIds,
                verificationToken,
                filename,
                storageReference,
                deliveryChannels,
                reportMode,
                reportType,
                reportStatus,
                notes,
                actorAppUserId,
                false
        );
    }

    private List<LaboratoryOrderedTestView> persistReportArtifact(
            UUID tenantId,
            UUID labOrderId,
            Collection<UUID> orderedTestIds,
            String verificationToken,
            String filename,
            String storageReference,
            List<String> deliveryChannels,
            String reportMode,
            String reportType,
            String reportStatus,
            String notes,
            UUID actorAppUserId,
            boolean transitionLifecycle
    ) {
        lockReportGeneration(tenantId, labOrderId);
        List<UUID> targetIds = distinctIds(orderedTestIds);
        if (targetIds.isEmpty()) {
            targetIds = lifecycleRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtAsc(tenantId, labOrderId).stream()
                    .filter(lifecycle -> isReportComposableTestState(lifecycle.getState()))
                    .map(LaboratoryOrderedTestLifecycleEntity::getLabOrderItemId)
                    .toList();
        }
        if (targetIds.isEmpty()) {
            throw new IllegalArgumentException("Lab order has no eligible tests for report publishing");
        }
        List<LaboratoryReportPublicationArtifactEntity> previousCurrents = publicationArtifactRepository.findByTenantIdAndLabOrderIdOrderByArtifactNumberDesc(tenantId, labOrderId).stream()
                .filter(artifact -> LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_CURRENT.equalsIgnoreCase(artifact.getReportStatus()))
                .toList();
        int nextArtifactNumber = publicationArtifactRepository.findByTenantIdAndLabOrderIdOrderByArtifactNumberDesc(tenantId, labOrderId).stream()
                .map(LaboratoryReportPublicationArtifactEntity::getArtifactNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        String normalizedReportMode = normalizeReportMode(reportMode, targetIds.size());
        String normalizedReportType = normalizeReportType(reportType, tenantId, labOrderId, targetIds);
        OffsetDateTime generatedAt = OffsetDateTime.now();
        boolean makeCurrent = LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_CURRENT.equalsIgnoreCase(reportStatus);
        // Allocate the id before changing the existing artifact, but do not save
        // the new CURRENT row until the old CURRENT row is flushed as superseded.
        LaboratoryReportPublicationArtifactEntity artifact =
                LaboratoryReportPublicationArtifactEntity.create(
                        tenantId,
                        labOrderId,
                        nextArtifactNumber,
                        filename,
                        storageReference,
                        verificationToken,
                        null,
                        serializeStrings(deliveryChannels),
                        serializeIds(targetIds),
                        normalizedReportMode,
                        normalizedReportType,
                        reportStatus,
                        generatedAt,
                        actorAppUserId,
                        notes,
                        actorAppUserId
        );
        if (makeCurrent) {
            for (LaboratoryReportPublicationArtifactEntity previousCurrent : previousCurrents) {
                previousCurrent.markSuperseded(artifact.getId());
                publicationArtifactRepository.save(previousCurrent);
            }
        }
        try {
            if (makeCurrent && !previousCurrents.isEmpty()) {
                publicationArtifactRepository.flush();
            }
            publicationArtifactRepository.save(artifact);
            publicationArtifactRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "Report was updated by another action. Refresh and try again.",
                    ex
            );
        }
        List<LaboratoryReportPublicationTestEntity> tests = new ArrayList<>();
        for (UUID labOrderItemId : targetIds) {
            Integer latestRevision = resultRevisionRepository.findTopByTenantIdAndLabOrderItemIdOrderByRevisionNumberDesc(tenantId, labOrderItemId)
                    .map(LaboratoryResultRevisionEntity::getRevisionNumber)
                    .orElse(1);
            tests.add(LaboratoryReportPublicationTestEntity.create(tenantId, artifact.getId(), labOrderItemId, latestRevision));
            if (transitionLifecycle) {
                LaboratoryOrderedTestLifecycleEntity lifecycle = lifecycleRepository.findByTenantIdAndLabOrderIdAndLabOrderItemId(tenantId, labOrderId, labOrderItemId)
                        .orElseGet(() -> LaboratoryOrderedTestLifecycleEntity.create(tenantId, labOrderId, labOrderItemId, "VERIFIED", actorAppUserId));
                if (!"PUBLISHED".equalsIgnoreCase(lifecycle.getState())) {
                    lifecycle.markState("PUBLISHED", actorAppUserId);
                }
                lifecycleRepository.save(lifecycle);
            }
        }
        publicationTestRepository.saveAll(tests);
        return listOrderTests(tenantId, labOrderId);
    }

    /**
     * Serializes report generation for one tenant/order, including the first
     * generation where there is no database row available to pessimistically lock.
     */
    private void lockReportGeneration(UUID tenantId, UUID labOrderId) {
        if (tenantId == null || labOrderId == null) {
            return;
        }
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtextextended(cast(? as text), 0))"
                )
                .setParameter(1, tenantId + ":" + labOrderId)
                .getSingleResult();
    }

    @Transactional(readOnly = true)
    public List<LaboratoryReportArtifactView> listReportArtifacts(UUID tenantId, UUID labOrderId) {
        if (tenantId == null || labOrderId == null) {
            return List.of();
        }
        return publicationArtifactRepository.findByTenantIdAndLabOrderIdOrderByArtifactNumberDesc(tenantId, labOrderId).stream()
                .map(this::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<LaboratoryReportArtifactView> findReportArtifactByVerificationToken(String verificationToken) {
        if (!StringUtils.hasText(verificationToken)) {
            return java.util.Optional.empty();
        }
        return publicationArtifactRepository.findByVerificationToken(verificationToken.trim()).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public String deriveAggregateOrderState(UUID tenantId, UUID labOrderId) {
        List<String> states = lifecycleRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtAsc(tenantId, labOrderId).stream()
                .map(LaboratoryOrderedTestLifecycleEntity::getState)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase())
                .toList();
        if (states.isEmpty()) {
            return "ORDERED";
        }
        if (states.stream().distinct().count() == 1) {
            return states.getFirst();
        }
        boolean allPublished = states.stream().allMatch("PUBLISHED"::equals);
        if (allPublished) {
            return "DELIVERED";
        }
        boolean anyPublished = states.stream().anyMatch("PUBLISHED"::equals);
        if (anyPublished) {
            return "PARTIALLY_PUBLISHED";
        }
        boolean allVerified = states.stream().allMatch("VERIFIED"::equals);
        if (allVerified) {
            return "REPORT_READY";
        }
        boolean anyVerified = states.stream().anyMatch("VERIFIED"::equals);
        boolean anyBlocked = states.stream().anyMatch(value -> value.equals("RESULT_SENT_BACK") || value.equals("RECOLLECTION_REQUIRED"));
        boolean anyReady = states.stream().anyMatch(value -> value.equals("RESULT_ENTERED") || value.equals("RESULT_SENT_BACK") || value.equals("RECOLLECTION_REQUIRED"));
        if (anyVerified && (anyBlocked || anyReady)) {
            return "PARTIALLY_READY";
        }
        if (anyBlocked || anyReady) {
            return "IN_PROGRESS";
        }
        if (states.stream().allMatch(value -> value.equals("SAMPLE_RECEIVED") || value.equals("SAMPLE_COLLECTED") || value.equals("READY_FOR_COLLECTION") || value.equals("PAID") || value.equals("PAYMENT_PENDING") || value.equals("ORDERED"))) {
            return "IN_PROGRESS";
        }
        return "IN_PROGRESS";
    }

    @Transactional(readOnly = true)
    public List<UUID> latestPublicationSelection(UUID tenantId, UUID labOrderId) {
        return resolveLatestPublicationArtifact(tenantId, labOrderId)
                .map(LaboratoryReportPublicationArtifactEntity::getSelectedItemIds)
                .map(this::parseUuidList)
                .orElse(List.of());
    }

    private List<UUID> distinctIds(Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private boolean isReportComposableTestState(String state) {
        if (!StringUtils.hasText(state)) {
            return false;
        }
        String normalized = state.trim().toUpperCase();
        return "VERIFIED".equals(normalized)
                || "PUBLISHED".equals(normalized)
                || "REPORT_GENERATED".equals(normalized)
                || "DELIVERED".equals(normalized);
    }

    private String serializeStrings(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String serializeIds(Collection<UUID> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values.stream().map(UUID::toString).toList());
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<String> parseStrings(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of(value);
        }
    }

    private List<UUID> parseUuidList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return values.stream()
                    .filter(StringUtils::hasText)
                    .map(UUID::fromString)
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private java.util.Optional<LaboratoryReportPublicationArtifactEntity> resolveLatestPublicationArtifact(UUID tenantId, UUID labOrderId) {
        return publicationArtifactRepository.findTopByTenantIdAndLabOrderIdOrderByArtifactNumberDesc(tenantId, labOrderId);
    }

    private LaboratoryReportArtifactView toRecord(LaboratoryReportPublicationArtifactEntity entity) {
        return new LaboratoryReportArtifactView(
                entity.getId(),
                entity.getTenantId(),
                entity.getLabOrderId(),
                entity.getArtifactNumber(),
                entity.getReportMode(),
                entity.getReportType(),
                entity.getReportStatus(),
                entity.getFilename(),
                entity.getStorageReference(),
                entity.getVerificationToken(),
                entity.getVerificationUrl(),
                parseStrings(entity.getDeliveryChannels()),
                parseUuidList(entity.getSelectedItemIds()),
                entity.getGeneratedAt(),
                entity.getGeneratedBy(),
                entity.getPublishedAt(),
                entity.getPublishedBy(),
                entity.getSupersededByArtifactId(),
                entity.getSupersededAt(),
                entity.getNotes()
        );
    }

    private String normalizeReportMode(String reportMode, int selectedCount) {
        if (!StringUtils.hasText(reportMode)) {
            return selectedCount <= 1 ? LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL : LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED;
        }
        String normalized = reportMode.trim().toUpperCase();
        return switch (normalized) {
            case LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL,
                 LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED,
                 LaboratoryReportPublicationArtifactEntity.REPORT_MODE_CONSOLIDATED -> normalized;
            default -> selectedCount <= 1 ? LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL : LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED;
        };
    }

    private String normalizeReportType(String reportType, UUID tenantId, UUID labOrderId, Collection<UUID> targetIds) {
        if (StringUtils.hasText(reportType)) {
            String normalized = reportType.trim().toUpperCase();
            if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equals(normalized) || LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM.equals(normalized)) {
                return normalized;
            }
        }
        List<LaboratoryOrderedTestLifecycleEntity> lifecycles = lifecycleRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtAsc(tenantId, labOrderId);
        boolean allPublished = !lifecycles.isEmpty() && lifecycles.stream().allMatch(lifecycle -> "PUBLISHED".equalsIgnoreCase(lifecycle.getState()));
        boolean includedAllEligible = targetIds != null && !targetIds.isEmpty() && lifecycles.stream()
                .map(LaboratoryOrderedTestLifecycleEntity::getLabOrderItemId)
                .collect(Collectors.toSet())
                .containsAll(targetIds);
        if (allPublished || includedAllEligible && lifecycles.stream().allMatch(lifecycle -> "PUBLISHED".equalsIgnoreCase(lifecycle.getState()) || "VERIFIED".equalsIgnoreCase(lifecycle.getState()))) {
            return LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL;
        }
        return LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM;
    }
}
