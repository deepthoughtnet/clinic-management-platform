package com.deepthoughtnet.clinic.api.consultation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationSoapRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationSoapResponse;
import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.consultation.db.ConsultationSoapNoteEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationSoapNoteEntity.ConsultationSoapSource;
import com.deepthoughtnet.clinic.consultation.db.ConsultationSoapNoteEntity.ConsultationSoapStatus;
import com.deepthoughtnet.clinic.consultation.db.ConsultationSoapNoteRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsultationSoapServiceTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void getReturnsEmptySoapResponseWhenNoRecordExists() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ConsultationSoapContextHasher contextHasher = new ConsultationSoapContextHasher(new ObjectMapper());
        ConsultationSoapNoteRepository repository = mock(ConsultationSoapNoteRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        ConsultationRecord consultation = consultationRecord(tenantId, consultationId);
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(consultation.patientId()), eq(consultationId))).thenReturn(sampleContext(tenantId, consultation.patientId(), consultationId, "Chief complaint", "Diagnosis", 120, 80, 72, 36.8));
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());
        ConsultationSoapService service = new ConsultationSoapService(consultationService, clinicalContextService, contextHasher, repository, auditEventPublisher, new ObjectMapper());

        ConsultationSoapResponse response = service.get(tenantId, consultationId);

        assertThat(response.consultationId()).isEqualTo(consultationId.toString());
        assertThat(response.versionNumber()).isZero();
        assertThat(response.status()).isNull();
        assertThat(response.source()).isNull();
        assertThat(response.subjective()).isNull();
        assertThat(response.objective()).isNull();
        assertThat(response.assessment()).isNull();
        assertThat(response.plan()).isNull();
        assertThat(response.sourceHash()).isNull();
        assertThat(response.currentSourceHash()).isNotBlank();
        assertThat(response.stale()).isFalse();
    }

    @Test
    void saveManualPersistsDedicatedSoapRecordWithoutConsultationFieldSideEffects() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ConsultationSoapContextHasher contextHasher = new ConsultationSoapContextHasher(new ObjectMapper());
        ConsultationSoapNoteRepository repository = mock(ConsultationSoapNoteRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ConsultationRecord consultation = consultationRecord(tenantId, consultationId);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("DOCTOR"), "DOCTOR", "corr-soap-1"));
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(consultation.patientId()), eq(consultationId))).thenReturn(sampleContext(tenantId, consultation.patientId(), consultationId, "Chief complaint", "Diagnosis", 120, 80, 72, 36.8));
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ConsultationSoapService service = new ConsultationSoapService(consultationService, clinicalContextService, contextHasher, repository, auditEventPublisher, new ObjectMapper());

        ConsultationSoapResponse response = service.saveManual(tenantId, consultationId, new ConsultationSoapRequest(
                "Subjective text",
                "Objective text",
                "Assessment text",
                "Plan text",
                null,
                null,
                null
        ));

        ArgumentCaptor<ConsultationSoapNoteEntity> captor = ArgumentCaptor.forClass(ConsultationSoapNoteEntity.class);
        verify(repository).save(captor.capture());
        ConsultationSoapNoteEntity saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getConsultationId()).isEqualTo(consultationId);
        assertThat(saved.getVersionNumber()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(ConsultationSoapStatus.DRAFT);
        assertThat(saved.getSource()).isEqualTo(ConsultationSoapSource.MANUAL);
        assertThat(saved.getSubjective()).isEqualTo("Subjective text");
        assertThat(saved.getObjective()).isEqualTo("Objective text");
        assertThat(saved.getAssessment()).isEqualTo("Assessment text");
        assertThat(saved.getPlan()).isEqualTo("Plan text");
        assertThat(saved.getAcceptedAt()).isNull();
        assertThat(saved.getGeneratedByAppUserId()).isEqualTo(actorId);
        assertThat(saved.getSourceHash()).isNotBlank();
        assertThat(response.source()).isEqualTo("MANUAL");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.sourceHash()).isEqualTo(saved.getSourceHash());
        assertThat(response.stale()).isFalse();
        verify(consultationService).findById(eq(tenantId), eq(consultationId));
    }

    @Test
    void acceptAiDraftCreatesAcceptedSoapVersionAndSupersedesPreviousVersion() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ConsultationSoapContextHasher contextHasher = new ConsultationSoapContextHasher(new ObjectMapper());
        ConsultationSoapNoteRepository repository = mock(ConsultationSoapNoteRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ConsultationRecord consultation = consultationRecord(tenantId, consultationId);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("DOCTOR"), "DOCTOR", "corr-soap-2"));
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(consultation));
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(consultation.patientId()), eq(consultationId))).thenReturn(sampleContext(tenantId, consultation.patientId(), consultationId, "Chief complaint", "Diagnosis", 120, 80, 72, 36.8));
        ConsultationSoapNoteEntity previous = ConsultationSoapNoteEntity.create(
                tenantId,
                consultationId,
                1,
                ConsultationSoapStatus.DRAFT,
                ConsultationSoapSource.MANUAL,
                "Old subjective",
                "Old objective",
                "Old assessment",
                "Old plan",
                null,
                null,
                actorId,
                null,
                OffsetDateTime.parse("2026-07-10T09:00:00Z"),
                null,
                "base-source-hash"
        );
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ConsultationSoapService service = new ConsultationSoapService(consultationService, clinicalContextService, contextHasher, repository, auditEventPublisher, new ObjectMapper());

        ConsultationSoapResponse response = service.acceptAiDraft(tenantId, consultationId, new ConsultationSoapRequest(
                "New subjective",
                "New objective",
                "New assessment",
                "New plan",
                "GEMINI",
                "gemini-1.5-flash",
                OffsetDateTime.parse("2026-07-11T09:00:00Z")
        ));

        ArgumentCaptor<ConsultationSoapNoteEntity> captor = ArgumentCaptor.forClass(ConsultationSoapNoteEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<ConsultationSoapNoteEntity> savedVersions = captor.getAllValues();
        ConsultationSoapNoteEntity saved = savedVersions.stream()
                .filter(entity -> entity.getStatus() == ConsultationSoapStatus.ACCEPTED)
                .findFirst()
                .orElseThrow();
        assertThat(saved.getVersionNumber()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(ConsultationSoapStatus.ACCEPTED);
        assertThat(saved.getSource()).isEqualTo(ConsultationSoapSource.AI_ACCEPTED);
        assertThat(saved.getAiProvider()).isEqualTo("GEMINI");
        assertThat(saved.getAiModel()).isEqualTo("gemini-1.5-flash");
        assertThat(saved.getAcceptedByAppUserId()).isEqualTo(actorId);
        assertThat(saved.getGeneratedAt()).isEqualTo(OffsetDateTime.parse("2026-07-11T09:00:00Z"));
        assertThat(saved.getAcceptedAt()).isNotNull();
        assertThat(saved.getSourceHash()).isNotBlank();
        assertThat(previous.getStatus()).isEqualTo(ConsultationSoapStatus.SUPERSEDED);
        assertThat(previous.getSupersededById()).isEqualTo(saved.getId());
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.source()).isEqualTo("AI_ACCEPTED");
        assertThat(response.sourceHash()).isEqualTo(saved.getSourceHash());
        assertThat(response.stale()).isFalse();
        verify(consultationService).findById(eq(tenantId), eq(consultationId));
    }

    @Test
    void getThrowsForUnknownConsultation() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ConsultationSoapContextHasher contextHasher = new ConsultationSoapContextHasher(new ObjectMapper());
        ConsultationSoapNoteRepository repository = mock(ConsultationSoapNoteRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ConsultationSoapService service = new ConsultationSoapService(consultationService, clinicalContextService, contextHasher, repository, auditEventPublisher, new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(tenantId, consultationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consultation not found");
    }

    @Test
    void getMarksAcceptedSoapReviewRecommendedWhenSymptomsChange() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ConsultationSoapContextHasher contextHasher = new ConsultationSoapContextHasher(new ObjectMapper());
        ConsultationSoapNoteRepository repository = mock(ConsultationSoapNoteRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        ConsultationRecord baseline = consultationRecord(tenantId, consultationId);
        ConsultationRecord changed = new ConsultationRecord(
                baseline.id(),
                baseline.tenantId(),
                baseline.patientId(),
                baseline.patientNumber(),
                baseline.patientName(),
                baseline.doctorUserId(),
                baseline.doctorName(),
                baseline.appointmentId(),
                baseline.chiefComplaints(),
                "Symptoms changed",
                baseline.diagnosis(),
                baseline.clinicalNotes(),
                baseline.advice(),
                baseline.followUpDate(),
                baseline.status(),
                baseline.bloodPressureSystolic(),
                baseline.bloodPressureDiastolic(),
                baseline.pulseRate(),
                baseline.temperature(),
                baseline.temperatureUnit(),
                baseline.weightKg(),
                baseline.heightCm(),
                baseline.spo2(),
                baseline.respiratoryRate(),
                baseline.completedAt(),
                baseline.createdAt(),
                baseline.updatedAt()
        );
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(baseline), Optional.of(changed));
        ClinicalContextResponse context = sampleContext(tenantId, baseline.patientId(), consultationId, "Chief complaint", "Diagnosis", 120, 80, 72, 36.8);
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(baseline.patientId()), eq(consultationId))).thenReturn(context);
        ConsultationSoapService service = new ConsultationSoapService(consultationService, clinicalContextService, contextHasher, repository, auditEventPublisher, new ObjectMapper());
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("DOCTOR"), "DOCTOR", "corr-soap-3"));
        ConsultationSoapResponse saved = service.acceptAiDraft(tenantId, consultationId, new ConsultationSoapRequest("S", "O", "A", "P", "GEMINI", "model", OffsetDateTime.parse("2026-07-11T09:00:00Z")));
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(
                ConsultationSoapNoteEntity.create(
                        tenantId,
                        consultationId,
                        1,
                        ConsultationSoapStatus.ACCEPTED,
                        ConsultationSoapSource.AI_ACCEPTED,
                        "S",
                        "O",
                        "A",
                        "P",
                        "GEMINI",
                        "model",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OffsetDateTime.parse("2026-07-11T09:00:00Z"),
                        OffsetDateTime.parse("2026-07-11T09:01:00Z"),
                        saved.sourceHash()
                )
        ));

        ConsultationSoapResponse response = service.get(tenantId, consultationId);

        assertThat(response.stale()).isTrue();
        assertThat(response.currentSourceHash()).isNotEqualTo(response.sourceHash());
        assertThat(response.status()).isEqualTo("ACCEPTED");
    }

    @Test
    void getMarksAcceptedSoapReviewRecommendedWhenDiagnosisChanges() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ConsultationSoapContextHasher contextHasher = new ConsultationSoapContextHasher(new ObjectMapper());
        ConsultationSoapNoteRepository repository = mock(ConsultationSoapNoteRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        ConsultationRecord baseline = consultationRecord(tenantId, consultationId);
        ConsultationRecord changed = new ConsultationRecord(
                baseline.id(),
                baseline.tenantId(),
                baseline.patientId(),
                baseline.patientNumber(),
                baseline.patientName(),
                baseline.doctorUserId(),
                baseline.doctorName(),
                baseline.appointmentId(),
                baseline.chiefComplaints(),
                baseline.symptoms(),
                "Changed diagnosis",
                baseline.clinicalNotes(),
                baseline.advice(),
                baseline.followUpDate(),
                baseline.status(),
                baseline.bloodPressureSystolic(),
                baseline.bloodPressureDiastolic(),
                baseline.pulseRate(),
                baseline.temperature(),
                baseline.temperatureUnit(),
                baseline.weightKg(),
                baseline.heightCm(),
                baseline.spo2(),
                baseline.respiratoryRate(),
                baseline.completedAt(),
                baseline.createdAt(),
                baseline.updatedAt()
        );
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(baseline), Optional.of(changed));
        ClinicalContextResponse context = sampleContext(tenantId, baseline.patientId(), consultationId, "Chief complaint", "Diagnosis", 120, 80, 72, 36.8);
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(baseline.patientId()), eq(consultationId))).thenReturn(context);
        ConsultationSoapService service = new ConsultationSoapService(consultationService, clinicalContextService, contextHasher, repository, auditEventPublisher, new ObjectMapper());
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("DOCTOR"), "DOCTOR", "corr-soap-4"));
        ConsultationSoapResponse saved = service.acceptAiDraft(tenantId, consultationId, new ConsultationSoapRequest("S", "O", "A", "P", "GEMINI", "model", OffsetDateTime.parse("2026-07-11T09:00:00Z")));
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(
                ConsultationSoapNoteEntity.create(
                        tenantId,
                        consultationId,
                        1,
                        ConsultationSoapStatus.ACCEPTED,
                        ConsultationSoapSource.AI_ACCEPTED,
                        "S",
                        "O",
                        "A",
                        "P",
                        "GEMINI",
                        "model",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OffsetDateTime.parse("2026-07-11T09:00:00Z"),
                        OffsetDateTime.parse("2026-07-11T09:01:00Z"),
                        saved.sourceHash()
                )
        ));

        ConsultationSoapResponse response = service.get(tenantId, consultationId);

        assertThat(response.stale()).isTrue();
    }

    @Test
    void getMarksAcceptedSoapReviewRecommendedWhenVitalsChange() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        ConsultationSoapContextHasher contextHasher = new ConsultationSoapContextHasher(new ObjectMapper());
        ConsultationSoapNoteRepository repository = mock(ConsultationSoapNoteRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        UUID tenantId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        ConsultationRecord baseline = consultationRecord(tenantId, consultationId);
        ConsultationRecord changed = new ConsultationRecord(
                baseline.id(),
                baseline.tenantId(),
                baseline.patientId(),
                baseline.patientNumber(),
                baseline.patientName(),
                baseline.doctorUserId(),
                baseline.doctorName(),
                baseline.appointmentId(),
                baseline.chiefComplaints(),
                baseline.symptoms(),
                baseline.diagnosis(),
                baseline.clinicalNotes(),
                baseline.advice(),
                baseline.followUpDate(),
                baseline.status(),
                138,
                86,
                96,
                38.4,
                baseline.temperatureUnit(),
                baseline.weightKg(),
                baseline.heightCm(),
                97,
                18,
                baseline.completedAt(),
                baseline.createdAt(),
                baseline.updatedAt()
        );
        when(consultationService.findById(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(baseline), Optional.of(changed));
        ClinicalContextResponse context = sampleContext(tenantId, baseline.patientId(), consultationId, "Chief complaint", "Diagnosis", 120, 80, 72, 36.8);
        when(clinicalContextService.buildClinicalContext(eq(tenantId), eq(baseline.patientId()), eq(consultationId))).thenReturn(context);
        ConsultationSoapService service = new ConsultationSoapService(consultationService, clinicalContextService, contextHasher, repository, auditEventPublisher, new ObjectMapper());
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("DOCTOR"), "DOCTOR", "corr-soap-5"));
        ConsultationSoapResponse saved = service.acceptAiDraft(tenantId, consultationId, new ConsultationSoapRequest("S", "O", "A", "P", "GEMINI", "model", OffsetDateTime.parse("2026-07-11T09:00:00Z")));
        when(repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(eq(tenantId), eq(consultationId))).thenReturn(Optional.of(
                ConsultationSoapNoteEntity.create(
                        tenantId,
                        consultationId,
                        1,
                        ConsultationSoapStatus.ACCEPTED,
                        ConsultationSoapSource.AI_ACCEPTED,
                        "S",
                        "O",
                        "A",
                        "P",
                        "GEMINI",
                        "model",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OffsetDateTime.parse("2026-07-11T09:00:00Z"),
                        OffsetDateTime.parse("2026-07-11T09:01:00Z"),
                        saved.sourceHash()
                )
        ));

        ConsultationSoapResponse response = service.get(tenantId, consultationId);

        assertThat(response.stale()).isTrue();
    }

    private ConsultationRecord consultationRecord(UUID tenantId, UUID consultationId) {
        return new ConsultationRecord(
                consultationId,
                tenantId,
                UUID.randomUUID(),
                "PAT-1",
                "Sample Patient",
                UUID.randomUUID(),
                "Doctor",
                UUID.randomUUID(),
                "Chief complaint",
                "Symptoms",
                "Diagnosis",
                "Notes",
                "Advice",
                LocalDate.of(2026, 7, 8),
                ConsultationStatus.DRAFT,
                120,
                80,
                72,
                36.8,
                null,
                70.0,
                172.0,
                98,
                18,
                null,
                OffsetDateTime.parse("2026-07-08T09:00:00Z"),
                OffsetDateTime.parse("2026-07-08T10:00:00Z")
        );
    }

    private ClinicalContextResponse sampleContext(UUID tenantId,
                                                  UUID patientId,
                                                  UUID consultationId,
                                                  String chiefComplaint,
                                                  String diagnosis,
                                                  int bloodPressureSystolic,
                                                  int bloodPressureDiastolic,
                                                  int pulseRate,
                                                  double temperature) {
        return new ClinicalContextResponse(
                tenantId,
                patientId,
                consultationId,
                new ClinicalContextResponse.PatientSnapshot(
                        "Sample Patient",
                        44,
                        "MALE",
                        "Type 2 Diabetes Mellitus",
                        "Paracetamol",
                        List.of("Metformin 500 mg"),
                        "2026-07-01"
                ),
                List.of(),
                new ClinicalContextResponse.MedicationSummary(List.of("Metformin 500 mg"), List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.DiagnosisSummary(diagnosis, List.of("Viral syndrome")),
                new ClinicalContextResponse.IntakeSummary(
                        true,
                        chiefComplaint,
                        new ClinicalContextResponse.VitalsSnapshot(172.0, 70.0, 23.6, "Normal", bloodPressureSystolic, bloodPressureDiastolic, pulseRate, temperature, "CELSIUS", 97, 18, 186.0, 4),
                        "Stable",
                        List.of(),
                        null,
                        "Nurse note",
                        "Nurse",
                        "2026-07-08T09:00:00Z"
                ),
                new ClinicalContextResponse.LabIntelligence(
                        "HbA1c 8.4%",
                        List.of(),
                        List.of(),
                        List.of(),
                        "8.4%",
                        null,
                        null,
                        "186 mg/dL",
                        null,
                        "138/86",
                        "26.8"
                ),
                new ClinicalContextResponse.DocumentIntelligence(List.of("Latest report"), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), "Recent events"),
                new ClinicalContextResponse.LongitudinalMemory(
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        "Most recent laboratory summary"
                ),
                new ClinicalContextResponse.LongitudinalClinicalContext(
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of()
                ),
                "Patient profile",
                "Prompt context",
                "{\"patient\":\"sample\"}",
                OffsetDateTime.parse("2026-07-08T09:00:00Z")
        );
    }
}
