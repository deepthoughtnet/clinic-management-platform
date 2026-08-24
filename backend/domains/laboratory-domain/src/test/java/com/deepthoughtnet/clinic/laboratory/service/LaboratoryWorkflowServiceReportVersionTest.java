package com.deepthoughtnet.clinic.laboratory.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.laboratory.db.LaboratoryOrderedTestLifecycleEntity;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryOrderedTestLifecycleRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryReportPublicationArtifactEntity;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryReportPublicationArtifactRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryReportPublicationTestRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryResultRevisionRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratorySpecimenTestLinkRepository;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryTestVerificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class LaboratoryWorkflowServiceReportVersionTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private final LaboratoryOrderedTestLifecycleRepository lifecycleRepository = mock(LaboratoryOrderedTestLifecycleRepository.class);
    private final LaboratorySpecimenTestLinkRepository specimenLinkRepository = mock(LaboratorySpecimenTestLinkRepository.class);
    private final LaboratoryResultRevisionRepository resultRevisionRepository = mock(LaboratoryResultRevisionRepository.class);
    private final LaboratoryTestVerificationRepository verificationRepository = mock(LaboratoryTestVerificationRepository.class);
    private final LaboratoryReportPublicationArtifactRepository artifactRepository = mock(LaboratoryReportPublicationArtifactRepository.class);
    private final LaboratoryReportPublicationTestRepository publicationTestRepository = mock(LaboratoryReportPublicationTestRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query advisoryLockQuery = mock(Query.class);
    private LaboratoryReportPublicationArtifactEntity oldArtifact;

    private LaboratoryWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new LaboratoryWorkflowService(
                lifecycleRepository,
                specimenLinkRepository,
                resultRevisionRepository,
                verificationRepository,
                artifactRepository,
                publicationTestRepository,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.setParameter(eq(1), anyString())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.getSingleResult()).thenReturn(null);

        LaboratoryOrderedTestLifecycleEntity lifecycle = mock(LaboratoryOrderedTestLifecycleEntity.class);
        when(lifecycle.getLabOrderItemId()).thenReturn(itemId);
        when(lifecycle.getState()).thenReturn("VERIFIED");
        when(lifecycleRepository.findByTenantIdAndLabOrderIdAndLabOrderItemId(tenantId, orderId, itemId))
                .thenReturn(java.util.Optional.of(lifecycle));
        when(lifecycleRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtAsc(tenantId, orderId))
                .thenReturn(List.of());
        oldArtifact = currentArtifact();
        when(artifactRepository.findByTenantIdAndLabOrderIdOrderByArtifactNumberDesc(tenantId, orderId))
                .thenReturn(List.of(oldArtifact));
        when(resultRevisionRepository.findTopByTenantIdAndLabOrderItemIdOrderByRevisionNumberDesc(tenantId, itemId))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    void demotesAndFlushesCurrentArtifactBeforeInsertingNextCurrentArtifact() {
        service.publishTests(
                tenantId,
                orderId,
                List.of(itemId),
                "verification-token",
                "report.pdf",
                "lab/report/report.pdf",
                List.of("PATIENT_PORTAL"),
                "CONSOLIDATED",
                "FINAL",
                null,
                actorId
        );

        InOrder order = inOrder(artifactRepository);
        order.verify(artifactRepository).save(oldArtifact);
        order.verify(artifactRepository).flush();
        order.verify(artifactRepository).save(any(LaboratoryReportPublicationArtifactEntity.class));
        order.verify(artifactRepository).flush();
        verify(lifecycleRepository).save(any(LaboratoryOrderedTestLifecycleEntity.class));
    }

    private LaboratoryReportPublicationArtifactEntity currentArtifact() {
        return LaboratoryReportPublicationArtifactEntity.create(
                tenantId,
                orderId,
                2,
                "old-report.pdf",
                "lab/report/old-report.pdf",
                "old-token",
                null,
                "[]",
                "[\"" + itemId + "\"]",
                "INDIVIDUAL",
                "INTERIM",
                LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_CURRENT,
                java.time.OffsetDateTime.now(),
                actorId,
                null,
                actorId
        );
    }
}
