package com.deepthoughtnet.clinic.api.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderAttachmentRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderItemEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderOrigin;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderSampleEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderItemRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderSampleRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabTestParameterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestParameterRepository;
import com.deepthoughtnet.clinic.api.lab.LabCatalogueConfigService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDirectCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDoctorReviewCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultEntryCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultItemCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderSampleCollectionCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderVerificationCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleReceiveCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleRejectCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestUpsertCommand;
import com.deepthoughtnet.clinic.api.notifications.LabNotificationService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillLineRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.branding.BrandingProperties;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.stubbing.Answer;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@ExtendWith(MockitoExtension.class)
class LabServiceValidationTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CONSULTATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PATIENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TEST_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ORDER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Mock private LabTestMasterRepository labTestMasterRepository;
    @Mock private LabTestParameterRepository labTestParameterRepository;
    @Mock private LabOrderRepository labOrderRepository;
    @Mock private LabOrderItemRepository labOrderItemRepository;
    @Mock private LabOrderResultRepository labOrderResultRepository;
    @Mock private LabOrderAttachmentRepository labOrderAttachmentRepository;
    @Mock private LabOrderSampleRepository labOrderSampleRepository;
    @Mock private ConsultationService consultationService;
    @Mock private PatientRepository patientRepository;
    @Mock private TenantUserManagementService tenantUserManagementService;
    @Mock private BillingService billingService;
    @Mock private ClinicProfileService clinicProfileService;
    @Mock private ClinicalDocumentService clinicalDocumentService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private LabNotificationService labNotificationService;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private BrandingProperties brandingProperties;
    @Mock private LabCatalogueConfigService labCatalogueConfigService;
    @Mock private PlatformTransactionManager platformTransactionManager;

    private LabService service;

    @BeforeEach
    void setUp() {
        service = new LabService(
                labTestMasterRepository,
                labTestParameterRepository,
                labOrderRepository,
                labOrderItemRepository,
                labOrderResultRepository,
                labOrderAttachmentRepository,
                labOrderSampleRepository,
                consultationService,
                patientRepository,
                tenantUserManagementService,
                billingService,
                clinicProfileService,
                clinicalDocumentService,
                objectStorageService,
                labNotificationService,
                auditEventPublisher,
                new ObjectMapper(),
                brandingProperties,
                labCatalogueConfigService,
                platformTransactionManager
        );
        lenient().when(platformTransactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().doNothing().when(platformTransactionManager).commit(any());
        lenient().doNothing().when(platformTransactionManager).rollback(any());
        lenient().when(labOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labOrderSampleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labOrderSampleRepository.saveAll(anyList())).thenAnswer((Answer<List<?>>) invocation -> invocation.getArgument(0));
        lenient().when(labOrderSampleRepository.findByTenantIdAndAccessionNumber(any(), any())).thenReturn(Optional.empty());
        lenient().when(labOrderSampleRepository.findFirstByTenantIdAndAccessionNumberStartingWithOrderByAccessionNumberDesc(any(), any())).thenReturn(Optional.empty());
        lenient().when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(any(), any())).thenReturn(List.of());
        lenient().when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(any(), any())).thenReturn(List.of());
        lenient().when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(any(), any())).thenReturn(List.of());
        lenient().when(labOrderAttachmentRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        lenient().when(clinicProfileService.findByTenantId(any())).thenReturn(Optional.empty());
        lenient().when(brandingProperties.footerLine()).thenReturn("Generated by Clinic Management Platform");
        lenient().when(labCatalogueConfigService.listActiveCategoryCodes(any())).thenReturn(List.of("HEMATOLOGY", "BIOCHEMISTRY", "MICROBIOLOGY", "PATHOLOGY", "RADIOLOGY", "CARDIOLOGY", "IMMUNOLOGY", "SEROLOGY", "ENDOCRINOLOGY", "VIROLOGY", "MOLECULAR", "CYTOLOGY", "HISTOPATHOLOGY", "OTHER"));
        lenient().when(labCatalogueConfigService.isCategoryActive(any(), any())).thenReturn(true);
    }

    @Test
    void rejectsInvalidLabTestPrice() {
        assertThatThrownBy(() -> service.createTest(TENANT_ID, new LabTestUpsertCommand(
                "CBC",
                "Complete Blood Count",
                "HEMATOLOGY",
                null,
                "Blood",
                null,
                null,
                null,
                new BigDecimal("-1"),
                true,
                List.of()
        ), ACTOR_ID)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("price");
    }

    @Test
    void rejectsDuplicateLabTestCode() {
        LabTestMasterEntity existing = LabTestMasterEntity.create(TENANT_ID, "CBC", "Existing");
        existing.update("CBC", "Existing", "HEMATOLOGY", null, null, null, null, null, BigDecimal.valueOf(100), true);
        when(labTestMasterRepository.findByTenantIdAndTestCodeIgnoreCase(TENANT_ID, "CBC")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createTest(TENANT_ID, new LabTestUpsertCommand(
                "CBC",
                "Complete Blood Count",
                "HEMATOLOGY",
                null,
                "Blood",
                null,
                null,
                null,
                BigDecimal.valueOf(100),
                true,
                List.of()
        ), ACTOR_ID)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("code");
    }

    @Test
    void rejectsInactiveTestOrder() {
        setupConsultationAndPatient();
        LabTestMasterEntity inactive = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        setEntityId(inactive, TEST_ID);
        inactive.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), false);
        when(labTestMasterRepository.findAllById(anyList())).thenReturn(List.of(inactive));

        assertThatThrownBy(() -> service.createOrderFromConsultation(TENANT_ID, CONSULTATION_ID, new LabOrderCreateCommand(List.of(TEST_ID), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void rejectsDisabledOrCategoryHiddenTestsForOrdering() {
        setupConsultationAndPatient();
        LabTestMasterEntity disabled = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        setEntityId(disabled, TEST_ID);
        disabled.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), true);
        disabled.updateCatalogueConfig(false, null, null, null, true);
        when(labTestMasterRepository.findAllById(anyList())).thenReturn(List.of(disabled));

        assertThatThrownBy(() -> service.createOrderFromConsultation(TENANT_ID, CONSULTATION_ID, new LabOrderCreateCommand(List.of(TEST_ID), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");

        when(labCatalogueConfigService.isCategoryActive(TENANT_ID, "HEMATOLOGY")).thenReturn(false);
        disabled.updateCatalogueConfig(true, null, null, null, true);
        assertThatThrownBy(() -> service.createOrderFromConsultation(TENANT_ID, CONSULTATION_ID, new LabOrderCreateCommand(List.of(TEST_ID), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsSampleCollectionBeforeReadyState() {
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(sampleOrder(LabOrderStatus.ORDERED)));

        assertThatThrownBy(() -> service.collectSample(TENANT_ID, ORDER_ID, new LabOrderSampleCollectionCommand("Blood", "Tech", OffsetDateTime.now(), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ready for sample collection");
    }

    @Test
    void collectSampleCreatesAccessionAndMovesOrderToCollected() {
        var order = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        var samples = service.collectSamples(TENANT_ID, ORDER_ID, List.of(new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(
                null,
                "Blood",
                "EDTA",
                OffsetDateTime.now().minusMinutes(5),
                ACTOR_ID,
                null
        )), ACTOR_ID);

        assertThat(order.getStatus()).isEqualTo(LabOrderStatus.SAMPLE_COLLECTED);
        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().accessionNumber()).startsWith("LAB-");
        assertThat(samples.getFirst().barcodeValue()).isEqualTo(samples.getFirst().accessionNumber());
    }

    @Test
    void accessionSequenceContinuesAfterExistingTodaySample() {
        var order = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(order, ORDER_ID);
        String datePortion = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        LabOrderSampleEntity existing = sampleWithAccession(TENANT_ID, "LAB-" + datePortion + "-0001");
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderSampleRepository.findFirstByTenantIdAndAccessionNumberStartingWithOrderByAccessionNumberDesc(TENANT_ID, "LAB-" + datePortion + "-"))
                .thenReturn(Optional.of(existing));

        var samples = service.collectSamples(TENANT_ID, ORDER_ID, List.of(new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(
                null,
                "Blood",
                "EDTA",
                OffsetDateTime.now().minusMinutes(5),
                ACTOR_ID,
                null
        )), ACTOR_ID);

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().accessionNumber()).isEqualTo("LAB-" + datePortion + "-0002");
    }

    @Test
    void multipleSamplesSameTenantAndDateGetUniqueAccessions() {
        var order = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(order, ORDER_ID);
        String datePortion = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        LabOrderSampleEntity existing = sampleWithAccession(TENANT_ID, "LAB-" + datePortion + "-0001");
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderSampleRepository.findFirstByTenantIdAndAccessionNumberStartingWithOrderByAccessionNumberDesc(TENANT_ID, "LAB-" + datePortion + "-"))
                .thenReturn(Optional.of(existing));

        var samples = service.collectSamples(TENANT_ID, ORDER_ID, List.of(
                new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(null, "Blood", "EDTA", OffsetDateTime.now(), ACTOR_ID, null),
                new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(null, "Blood", "EDTA", OffsetDateTime.now(), ACTOR_ID, null)
        ), ACTOR_ID);

        assertThat(samples).extracting("accessionNumber").containsExactly(
                "LAB-" + datePortion + "-0002",
                "LAB-" + datePortion + "-0003"
        );
    }

    @Test
    void differentTenantCanUseSameAccessionSuffix() {
        UUID otherTenant = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var tenantOneOrder = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(tenantOneOrder, ORDER_ID);
        var tenantTwoOrder = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(tenantTwoOrder, ORDER_ID);
        String datePortion = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        LabOrderSampleEntity existing = sampleWithAccession(TENANT_ID, "LAB-" + datePortion + "-0001");
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(tenantOneOrder));
        when(labOrderRepository.findByTenantIdAndId(otherTenant, ORDER_ID)).thenReturn(Optional.of(tenantTwoOrder));
        when(labOrderSampleRepository.findFirstByTenantIdAndAccessionNumberStartingWithOrderByAccessionNumberDesc(TENANT_ID, "LAB-" + datePortion + "-"))
                .thenReturn(Optional.of(existing));
        when(labOrderSampleRepository.findFirstByTenantIdAndAccessionNumberStartingWithOrderByAccessionNumberDesc(otherTenant, "LAB-" + datePortion + "-"))
                .thenReturn(Optional.empty());

        var tenantOneSamples = service.collectSamples(TENANT_ID, ORDER_ID, List.of(new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(
                null,
                "Blood",
                "EDTA",
                OffsetDateTime.now(),
                ACTOR_ID,
                null
        )), ACTOR_ID);
        var tenantTwoSamples = service.collectSamples(otherTenant, ORDER_ID, List.of(new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(
                null,
                "Blood",
                "EDTA",
                OffsetDateTime.now(),
                ACTOR_ID,
                null
        )), ACTOR_ID);

        assertThat(tenantOneSamples.getFirst().accessionNumber()).isEqualTo("LAB-" + datePortion + "-0002");
        assertThat(tenantTwoSamples.getFirst().accessionNumber()).isEqualTo("LAB-" + datePortion + "-0001");
    }

    @Test
    void accessionCollisionRetriesBeforeFailing() {
        var order = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(order, ORDER_ID);
        String datePortion = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        LabOrderSampleEntity existing = sampleWithAccession(TENANT_ID, "LAB-" + datePortion + "-0001");
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderSampleRepository.findFirstByTenantIdAndAccessionNumberStartingWithOrderByAccessionNumberDesc(TENANT_ID, "LAB-" + datePortion + "-"))
                .thenReturn(Optional.of(existing));
        when(labOrderSampleRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_lab_order_samples_tenant_accession\""))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var samples = service.collectSamples(TENANT_ID, ORDER_ID, List.of(new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(
                null,
                "Blood",
                "EDTA",
                OffsetDateTime.now(),
                ACTOR_ID,
                null
        )), ACTOR_ID);

        assertThat(samples.getFirst().accessionNumber()).isEqualTo("LAB-" + datePortion + "-0002");
    }

    @Test
    void receiveSampleOnlyAllowedFromCollected() {
        LabOrderSampleEntity sample = collectedSample();
        when(labOrderSampleRepository.findByTenantIdAndId(TENANT_ID, sample.getId())).thenReturn(Optional.of(sample));

        var received = service.receiveSample(TENANT_ID, sample.getId(), new LabSampleReceiveCommand(OffsetDateTime.now(), ACTOR_ID), ACTOR_ID);

        assertThat(received.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabSampleStatusRecord.RECEIVED);

        LabOrderSampleEntity rejectedState = collectedSample();
        rejectedState.markRejected("Hemolysed sample", false, null, ACTOR_ID);
        when(labOrderSampleRepository.findByTenantIdAndId(TENANT_ID, rejectedState.getId())).thenReturn(Optional.of(rejectedState));

        assertThatThrownBy(() -> service.receiveSample(TENANT_ID, rejectedState.getId(), new LabSampleReceiveCommand(OffsetDateTime.now(), ACTOR_ID), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ready to receive");
    }

    @Test
    void rejectSampleRequiresReason() {
        assertThatThrownBy(() -> service.rejectSample(TENANT_ID, UUID.randomUUID(), new LabSampleRejectCommand(null, false, null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejectionReason");
    }

    @Test
    void rejectSampleCanRequireRecollection() {
        LabOrderSampleEntity sample = collectedSample();
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        when(labOrderSampleRepository.findByTenantIdAndId(TENANT_ID, sample.getId())).thenReturn(Optional.of(sample));
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sample));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of());

        var rejected = service.rejectSample(TENANT_ID, sample.getId(), new LabSampleRejectCommand("Hemolysed sample", true, "Recollect"), ACTOR_ID);

        assertThat(rejected.recollectionRequired()).isTrue();
        assertThat(rejected.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabSampleStatusRecord.RECOLLECTION_REQUIRED);
    }

    @Test
    void rejectsResultEntryBeforeSampleCollection() {
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(sampleOrder(LabOrderStatus.ORDERED)));

        assertThatThrownBy(() -> service.enterResults(TENANT_ID, ORDER_ID, new LabOrderResultEntryCommand(List.of(
                new LabOrderResultItemCommand(UUID.randomUUID(), "13.4", "mg/dL", "10-20", List.of())
        ), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result entry");
    }

    @Test
    void rejectsReportGenerationBeforeApproval() {
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(sampleOrder(LabOrderStatus.ORDERED)));

        assertThatThrownBy(() -> service.generateReportPdf(TENANT_ID, ORDER_ID, ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approved");
    }

    @Test
    void rejectsPublishingBeforeReportReady() {
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(sampleOrder(LabOrderStatus.RESULT_ENTERED)));

        assertThatThrownBy(() -> service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL"), null),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishing");
    }

    @Test
    void rejectsResultEntryWhenRecollectionRequired() {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        LabOrderSampleEntity sample = collectedSample();
        sample.markRejected("Hemolysed sample", true, null, ACTOR_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sample));

        assertThatThrownBy(() -> service.enterResults(TENANT_ID, ORDER_ID, new LabOrderResultEntryCommand(List.of(
                new LabOrderResultItemCommand(UUID.randomUUID(), "13.4", "mg/dL", "10-20", List.of())
        ), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recollection");
    }

    @Test
    void flagsNumericResultsRespectParsedRanges() {
        assertFlag("14", "13.0-17.0", null, "NORMAL", false);
        assertFlag("8", "4.0-11.0", null, "NORMAL", false);
        assertFlag("350", "150-450", null, "NORMAL", false);
        assertFlag("90", "70-100", null, "NORMAL", false);
        assertFlag("150", "<200", null, "NORMAL", false);
        assertFlag("25", ">20", null, "NORMAL", false);
        assertFlag("12.9", "13.0-17.0", null, "LOW", false);
        assertFlag("17.1", "13.0-17.0", null, "HIGH", false);
        assertFlag("250", "<200", null, "HIGH", false);
        assertFlag("20", ">20", null, "LOW", false);
        assertFlag("2.0", "3.5-5.1", "2.5-6.0", "CRITICAL_LOW", true);
        assertFlag("6.5", "3.5-5.1", "2.5-6.0", "CRITICAL_HIGH", true);
    }

    @Test
    void rendersReadableLabReportPdfWithJeevanamFooter() throws Exception {
        var order = sampleOrder(LabOrderStatus.REPORT_READY);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(new ClinicProfileRecord(
                UUID.randomUUID(),
                TENANT_ID,
                "Jeevanam Diagnostics",
                "Jeevanam Diagnostics",
                "1800-000-000",
                "lab@example.com",
                "12 Medical Street",
                "2nd Floor",
                "Bengaluru",
                "Karnataka",
                "India",
                "560001",
                null,
                null,
                null,
                true,
                true,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));

        var pdf = service.renderReportPdf(TENANT_ID, ORDER_ID);

        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("LABORATORY REPORT");
            assertThat(text).contains("Jeevanam Diagnostics");
            assertThat(text).contains("Generated by Jeevanam Healthcare | Powered by AIVA");
            assertThat(text).contains("Interpretation");
            assertThat(text).contains("Normal");
            assertThat(text).doesNotContain("Arogia");
        }
    }

    private void assertFlag(String value, String normalRange, String criticalRange, String expectedFlag, boolean expectedCritical) {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        UUID itemId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        var orderItem = LabOrderItemEntity.create(
                TENANT_ID,
                ORDER_ID,
                testId,
                "HB",
                "Hemoglobin",
                "BIOCHEMISTRY",
                null,
                "Serum",
                "g/dL",
                normalRange,
                criticalRange,
                BigDecimal.valueOf(100),
                1
        );
        setOrderItemId(orderItem, itemId);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(orderItem));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        when(labTestParameterRepository.findByTenantIdAndLabTestIdOrderBySortOrderAsc(TENANT_ID, testId)).thenReturn(List.of(
                LabTestParameterEntity.create(TENANT_ID, testId, "Hemoglobin", "g/dL", normalRange, criticalRange, 1)
        ));
        AtomicReference<List<LabOrderResultEntity>> savedResults = new AtomicReference<>(List.of());
        when(labOrderResultRepository.saveAll(anyList())).thenAnswer(invocation -> {
            savedResults.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        service.enterResults(TENANT_ID, ORDER_ID, new LabOrderResultEntryCommand(List.of(
                new LabOrderResultItemCommand(itemId, value, "g/dL", normalRange, List.of())
        ), null), ACTOR_ID);

        assertThat(savedResults.get()).hasSize(1);
        assertThat(savedResults.get().getFirst().getResultFlag()).isEqualTo(expectedFlag);
        assertThat(savedResults.get().getFirst().isCriticalResult()).isEqualTo(expectedCritical);
    }

    @Test
    void flagsCriticalNumericResults() {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        UUID itemId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        var orderItem = LabOrderItemEntity.create(
                TENANT_ID,
                ORDER_ID,
                testId,
                "K",
                "Potassium",
                "BIOCHEMISTRY",
                null,
                "Serum",
                "mmol/L",
                "3.5-5.1",
                null,
                BigDecimal.valueOf(100),
                1
        );
        setOrderItemId(orderItem, itemId);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(orderItem));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        when(labTestParameterRepository.findByTenantIdAndLabTestIdOrderBySortOrderAsc(TENANT_ID, testId)).thenReturn(List.of(
                LabTestParameterEntity.create(TENANT_ID, testId, "Potassium", "mmol/L", "3.5-5.1", "2.5-6.0", 1)
        ));
        AtomicReference<List<LabOrderResultEntity>> savedResults = new AtomicReference<>(List.of());
        when(labOrderResultRepository.saveAll(anyList())).thenAnswer(invocation -> {
            savedResults.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        service.enterResults(TENANT_ID, ORDER_ID, new LabOrderResultEntryCommand(List.of(
                new LabOrderResultItemCommand(itemId, "6.5", "mmol/L", "3.5-5.1", List.of())
        ), null), ACTOR_ID);

        assertThat(savedResults.get()).hasSize(1);
        assertThat(savedResults.get().getFirst().getResultFlag()).isEqualTo("CRITICAL_HIGH");
        assertThat(savedResults.get().getFirst().isCriticalResult()).isTrue();
    }

    @Test
    void verifyFromResultEnteredMovesOrderToReportReady() {
        var order = sampleOrder(LabOrderStatus.RESULT_ENTERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));

        var result = service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("APPROVE", null, "Verified"), ACTOR_ID);

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.REPORT_READY);
        assertThat(result.labVerificationDecision()).isEqualTo("APPROVE");
    }

    @Test
    void cannotVerifyBeforeResultEntry() {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("APPROVE", null, null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verification");
    }

    @Test
    void sendBackKeepsOrderEditable() {
        var order = sampleOrder(LabOrderStatus.RESULT_ENTERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));

        var result = service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("SEND_BACK", "Delta check", "Recheck"), ACTOR_ID);

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.RESULT_ENTERED);
        assertThat(result.labVerificationDecision()).isEqualTo("SEND_BACK");
    }

    @Test
    void cannotEditResultsAfterReportReady() {
        var order = sampleOrder(LabOrderStatus.REPORT_READY);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.enterResults(TENANT_ID, ORDER_ID, new LabOrderResultEntryCommand(List.of(
                new LabOrderResultItemCommand(UUID.randomUUID(), "13.4", "mg/dL", "10-20", List.of())
        ), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verified and cannot be edited");
    }

    @Test
    void publishReportMovesOrderToReportGeneratedAndStoresMetadata() {
        var order = sampleOrder(LabOrderStatus.REPORT_READY);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        var published = service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL", "PRINT"), "Published in batch 4"),
                ACTOR_ID
        );

        assertThat(published.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.REPORT_GENERATED);
        assertThat(published.reportDeliveryStatus()).isEqualTo("PUBLISHED");
        assertThat(published.reportDeliveryChannels()).containsExactly("PATIENT_PORTAL", "PRINT");
        assertThat(published.reportPublishedAt()).isNotNull();
        assertThat(published.reportGeneratedAt()).isNotNull();
    }

    @Test
    void rejectsTenantMismatchWhenOrderingLabTests() {
        setupConsultationAndPatient();
        LabTestMasterEntity otherTenant = LabTestMasterEntity.create(UUID.fromString("77777777-7777-4777-8777-777777777777"), "CBC", "Complete Blood Count");
        setEntityId(otherTenant, TEST_ID);
        otherTenant.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), true);
        when(labTestMasterRepository.findAllById(anyList())).thenReturn(List.of(otherTenant));

        assertThatThrownBy(() -> service.createOrderFromConsultation(TENANT_ID, CONSULTATION_ID, new LabOrderCreateCommand(List.of(TEST_ID), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected tenant");
    }

    @Test
    void createsStandaloneLabOrderWithBilling() {
        setupPatientOnly();
        LabTestMasterEntity test = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        setEntityId(test, TEST_ID);
        test.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), true);
        when(labTestMasterRepository.findAllById(anyList())).thenReturn(List.of(test));
        when(billingService.createDraft(any(), any(), any())).thenReturn(sampleBill());
        when(billingService.issue(any(), any(), any())).thenReturn(sampleBill());

        var result = service.createOrder(TENANT_ID, new LabOrderDirectCreateCommand(PATIENT_ID, LabOrderOrigin.WALK_IN, null, null, null, null, null, List.of(TEST_ID), "Standalone request"), ACTOR_ID);

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.PAYMENT_PENDING);
        assertThat(result.patientNumber()).isEqualTo("P-001");
        assertThat(result.patientName()).isEqualTo("Test Patient");
        assertThat(result.orderOrigin()).isEqualTo(LabOrderOrigin.WALK_IN);
    }

    @Test
    void newOrdersUseTenantPriceAndTatOverridesWithoutTouchingHistory() {
        setupPatientOnly();
        LabTestMasterEntity test = LabTestMasterEntity.create(TENANT_ID, "TSH", "Thyroid Stimulating Hormone");
        setEntityId(test, TEST_ID);
        test.update("TSH", "Thyroid Stimulating Hormone", "ENDOCRINOLOGY", null, "Blood", null, null, "48 hrs", BigDecimal.valueOf(150), true);
        test.updateCatalogueConfig(true, BigDecimal.valueOf(275), "24 hrs", 7, true);
        when(labTestMasterRepository.findAllById(anyList())).thenReturn(List.of(test));
        when(billingService.createDraft(any(), any(), any())).thenReturn(sampleBill());
        when(billingService.issue(any(), any(), any())).thenReturn(sampleBill());

        service.createOrder(TENANT_ID, new LabOrderDirectCreateCommand(PATIENT_ID, LabOrderOrigin.WALK_IN, null, null, null, null, null, List.of(TEST_ID), "Standalone request"), ACTOR_ID);

        ArgumentCaptor<LabOrderItemEntity> itemCaptor = ArgumentCaptor.forClass(LabOrderItemEntity.class);
        verify(labOrderItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getPrice()).isEqualByComparingTo("275.00");
        assertThat(itemCaptor.getValue().getTurnaroundTime()).isEqualTo("24 hrs");
    }

    @Test
    void rejectsStandaloneLabOrderWithoutPatient() {
        assertThatThrownBy(() -> service.createOrder(TENANT_ID, new LabOrderDirectCreateCommand(null, LabOrderOrigin.WALK_IN, null, null, null, null, null, List.of(TEST_ID), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("patientId");
    }

    @Test
    void consultationOriginOrderSetsConsultationOrigin() {
        setupConsultationAndPatient();
        LabTestMasterEntity test = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        setEntityId(test, TEST_ID);
        test.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), true);
        when(labTestMasterRepository.findAllById(anyList())).thenReturn(List.of(test));
        when(billingService.createDraft(any(), any(), any())).thenReturn(sampleBill());
        when(billingService.issue(any(), any(), any())).thenReturn(sampleBill());
        when(labOrderRepository.findByTenantIdAndConsultationIdOrderByCreatedAtDesc(TENANT_ID, CONSULTATION_ID)).thenReturn(List.of());

        var result = service.createOrderFromConsultation(TENANT_ID, CONSULTATION_ID, new LabOrderCreateCommand(List.of(TEST_ID), "Consultation request"), ACTOR_ID);

        assertThat(result.orderOrigin()).isEqualTo(LabOrderOrigin.CONSULTATION);
        assertThat(result.consultationId()).isEqualTo(CONSULTATION_ID);
    }

    @Test
    void blocksDuplicateConsultationLabOrderForSameTest() {
        setupConsultationAndPatient();
        LabTestMasterEntity test = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        setEntityId(test, TEST_ID);
        test.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), true);
        when(labTestMasterRepository.findAllById(anyList())).thenReturn(List.of(test));
        var existingOrder = sampleOrder(LabOrderStatus.PAYMENT_PENDING);
        when(labOrderRepository.findByTenantIdAndConsultationIdOrderByCreatedAtDesc(TENANT_ID, CONSULTATION_ID)).thenReturn(List.of(existingOrder));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, existingOrder.getId())).thenReturn(List.of(
                LabOrderItemEntity.create(TENANT_ID, existingOrder.getId(), TEST_ID, "CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), 1)
        ));

        assertThatThrownBy(() -> service.createOrderFromConsultation(TENANT_ID, CONSULTATION_ID, new LabOrderCreateCommand(List.of(TEST_ID), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has an active lab order");
    }

    @Test
    void rejectsSendBackWithoutReason() {
        assertThatThrownBy(() -> service.reviewReport(TENANT_ID, ORDER_ID, new LabOrderDoctorReviewCommand("SEND_BACK", null, null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    private void setupConsultationAndPatient() {
        lenient().when(consultationService.findById(TENANT_ID, CONSULTATION_ID)).thenReturn(Optional.of(new ConsultationRecord(
                CONSULTATION_ID,
                TENANT_ID,
                PATIENT_ID,
                "P-001",
                "Test Patient",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.now(),
                ConsultationStatus.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));
        PatientEntity patient = PatientEntity.create(TENANT_ID, "P-001");
        lenient().when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        lenient().when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of());
        lenient().when(labOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labOrderItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(any(), any())).thenReturn(List.of());
        lenient().when(labOrderSampleRepository.findByTenantIdAndAccessionNumber(any(), any())).thenReturn(Optional.empty());
        lenient().when(labOrderSampleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labOrderSampleRepository.saveAll(anyList())).thenAnswer((Answer<List<?>>) invocation -> invocation.getArgument(0));
    }

    private void setupPatientOnly() {
        PatientEntity patient = PatientEntity.create(TENANT_ID, "P-001");
        patient.update("Test", "Patient", null, null, null, "9876543210", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        lenient().when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        lenient().when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of());
        lenient().when(labOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labOrderItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(any(), any())).thenReturn(List.of());
        lenient().when(labOrderSampleRepository.findByTenantIdAndAccessionNumber(any(), any())).thenReturn(Optional.empty());
        lenient().when(labOrderSampleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labOrderSampleRepository.saveAll(anyList())).thenAnswer((Answer<List<?>>) invocation -> invocation.getArgument(0));
    }

    private BillRecord sampleBill() {
        return new BillRecord(
                UUID.randomUUID(),
                TENANT_ID,
                "BILL-001",
                PATIENT_ID,
                "P-001",
                "Test Patient",
                null,
                null,
                LocalDate.now(),
                BillStatus.ISSUED,
                BigDecimal.valueOf(100),
                DiscountType.NONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100),
                null,
                "Lab tests ordered",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.<BillLineRecord>of()
        );
    }

    private com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity sampleOrder(LabOrderStatus status) {
        com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity order = com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity.create(
                TENANT_ID,
                "LAB-0001",
                PATIENT_ID,
                "P-001",
                "Test Patient",
                null,
                null,
                CONSULTATION_ID,
                LabOrderOrigin.CONSULTATION,
                null,
                null,
                null,
                null,
                null,
                null
        );
        order.markStatus(status);
        return order;
    }

    private LabOrderSampleEntity collectedSample() {
        return LabOrderSampleEntity.create(
                TENANT_ID,
                ORDER_ID,
                null,
                "LAB-20260702-0001",
                "LAB-20260702-0001",
                "Blood",
                "EDTA",
                OffsetDateTime.now().minusMinutes(10),
                ACTOR_ID,
                null,
                ACTOR_ID
        );
    }

    private LabOrderSampleEntity sampleWithAccession(UUID tenantId, String accessionNumber) {
        return LabOrderSampleEntity.create(
                tenantId,
                ORDER_ID,
                null,
                accessionNumber,
                accessionNumber,
                "Blood",
                "EDTA",
                OffsetDateTime.now().minusMinutes(10),
                ACTOR_ID,
                null,
                ACTOR_ID
        );
    }

    private LabOrderResultEntity sampleResult() {
        return LabOrderResultEntity.create(
                TENANT_ID,
                ORDER_ID,
                UUID.randomUUID(),
                "CBC",
                "Complete Blood Count",
                "Hemoglobin",
                "Hemoglobin",
                "13.4",
                "g/dL",
                "12-16",
                1,
                "NORMAL",
                false
        );
    }

    private static void setEntityId(LabTestMasterEntity entity, UUID id) {
        try {
            Field field = LabTestMasterEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set lab test id for test", ex);
        }
    }

    private static void setOrderId(com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity order, UUID id) {
        try {
            Field field = com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set lab order id for test", ex);
        }
    }

    private static void setOrderItemId(LabOrderItemEntity orderItem, UUID id) {
        try {
            Field field = LabOrderItemEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(orderItem, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set lab order item id for test", ex);
        }
    }
}
