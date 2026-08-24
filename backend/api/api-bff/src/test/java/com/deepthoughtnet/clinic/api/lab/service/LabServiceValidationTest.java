package com.deepthoughtnet.clinic.api.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
import com.deepthoughtnet.clinic.api.lab.LabCatalogueConfigService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDirectCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDoctorReviewCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultEntryCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultItemCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultPdf;
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
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.audit.AuditEventRecord;
import com.deepthoughtnet.clinic.platform.branding.BrandingProperties;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.laboratory.service.LaboratoryWorkflowService;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratoryOrderedTestView;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratoryReportArtifactView;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratorySpecimenLinkView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
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
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
    @Mock private com.deepthoughtnet.clinic.api.lab.db.LabTestParameterRepository labTestParameterRepository;
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
    @Mock private ClinicTimeZoneResolver clinicTimeZoneResolver;
    @Mock private ClinicalDocumentService clinicalDocumentService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private LabNotificationService labNotificationService;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private AuditEventQueryService auditEventQueryService;
    @Mock private BrandingProperties brandingProperties;
    @Mock private LabReportVerificationProperties labReportVerificationProperties;
    @Mock private LabCatalogueConfigService labCatalogueConfigService;
    @Mock private LaboratoryWorkflowService laboratoryWorkflowService;
    @Mock private PlatformTransactionManager platformTransactionManager;
    @Mock private ModuleBusinessEventPublisher moduleBusinessEventPublisher;

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
                clinicTimeZoneResolver,
                clinicalDocumentService,
                objectStorageService,
                labNotificationService,
                moduleBusinessEventPublisher,
                auditEventQueryService,
                auditEventPublisher,
                new ObjectMapper(),
                brandingProperties,
                labReportVerificationProperties,
                labCatalogueConfigService,
                laboratoryWorkflowService,
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
        lenient().when(auditEventQueryService.listForEntity(any(), any(), any())).thenReturn(List.of());
        lenient().when(clinicProfileService.findByTenantId(any())).thenReturn(Optional.empty());
        lenient().when(clinicTimeZoneResolver.resolve(any())).thenReturn(ZoneId.of("Asia/Kolkata"));
        lenient().when(labReportVerificationProperties.getPublicBaseUrl()).thenReturn("https://reports.example.com");
        lenient().when(brandingProperties.footerLine()).thenReturn("Generated by Clinic Management Platform");
        lenient().when(labCatalogueConfigService.listActiveCategoryCodes(any())).thenReturn(List.of("HEMATOLOGY", "BIOCHEMISTRY", "MICROBIOLOGY", "PATHOLOGY", "RADIOLOGY", "CARDIOLOGY", "IMMUNOLOGY", "SEROLOGY", "ENDOCRINOLOGY", "VIROLOGY", "MOLECULAR", "CYTOLOGY", "HISTOPATHOLOGY", "OTHER"));
        lenient().when(labCatalogueConfigService.isCategoryActive(any(), any())).thenReturn(true);
        lenient().when(laboratoryWorkflowService.initializeOrderTests(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(laboratoryWorkflowService.listOrderTests(any(), any())).thenReturn(List.of());
        lenient().when(laboratoryWorkflowService.linkSpecimenToTests(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(laboratoryWorkflowService.rejectSpecimenForTests(any(), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(laboratoryWorkflowService.recordResultRevisions(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(laboratoryWorkflowService.verifyTests(any(), any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(List.of());
        lenient().when(laboratoryWorkflowService.publishTests(
                any(),
                any(),
                anyCollection(),
                anyString(),
                anyString(),
                anyString(),
                anyList(),
                anyString(),
                anyString(),
                nullable(String.class),
                any()
        )).thenReturn(List.of());
        lenient().when(laboratoryWorkflowService.deriveAggregateOrderState(any(), any())).thenReturn("ORDERED");
        lenient().when(laboratoryWorkflowService.latestPublicationSelection(any(), any())).thenReturn(List.of());
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

        assertThatThrownBy(() -> service.collectSample(TENANT_ID, ORDER_ID, new LabOrderSampleCollectionCommand("Blood", OffsetDateTime.now(), null), ACTOR_ID))
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
                null
        )), ACTOR_ID);

        assertThat(order.getStatus()).isEqualTo(LabOrderStatus.SAMPLE_COLLECTED);
        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().accessionNumber()).startsWith("LAB-");
        assertThat(samples.getFirst().barcodeValue()).isEqualTo(samples.getFirst().accessionNumber());
    }

    @Test
    void collectSamplesCoalescesSharedSpecimenRowsIntoOneAccession() {
        var order = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(order, ORDER_ID);
        LabOrderItemEntity cbc = sampleOrderItem(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "CBC", "Complete Blood Count", 1);
        LabOrderItemEntity hba1c = sampleOrderItem(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "HBA1C", "HbA1c", 2);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbc, hba1c));

        OffsetDateTime collectedAt = OffsetDateTime.parse("2026-08-23T04:44:00Z");
        var samples = service.collectSamples(TENANT_ID, ORDER_ID, List.of(
                new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(cbc.getId(), "Blood", "EDTA", collectedAt, "End-to-end laboratory UAT retest - normal results."),
                new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(hba1c.getId(), "Blood", "EDTA", collectedAt, "End-to-end laboratory UAT retest - normal results.")
        ), ACTOR_ID);

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().labOrderItemId()).isNull();
        assertThat(samples.getFirst().accessionNumber()).startsWith("LAB-");
        assertThat(samples.getFirst().linkedLabOrderItemIds()).containsExactly(cbc.getId(), hba1c.getId());
        assertThat(samples.getFirst().linkedTestNames()).containsExactly("Complete Blood Count", "HbA1c");
        verify(laboratoryWorkflowService).linkSpecimenToTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(ids -> ids != null && ids.containsAll(List.of(cbc.getId(), hba1c.getId())) && ids.size() == 2),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
    }

    @Test
    void collectSamplesCreatesSeparateSpecimensWhenContainerDiffers() {
        var order = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(order, ORDER_ID);
        LabOrderItemEntity cbc = sampleOrderItem(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "CBC", "Complete Blood Count", 1);
        LabOrderItemEntity lipid = sampleOrderItem(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "LIPID", "Lipid Profile", 2);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbc, lipid));

        OffsetDateTime collectedAt = OffsetDateTime.parse("2026-08-23T04:44:00Z");
        var samples = service.collectSamples(TENANT_ID, ORDER_ID, List.of(
                new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(cbc.getId(), "Blood", "EDTA", collectedAt, null),
                new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(lipid.getId(), "Blood", "Plain Tube", collectedAt, null)
        ), ACTOR_ID);

        assertThat(samples).hasSize(2);
        assertThat(samples).extracting("accessionNumber").doesNotHaveDuplicates();
        assertThat(samples.get(0).linkedLabOrderItemIds()).containsExactly(cbc.getId());
        assertThat(samples.get(1).linkedLabOrderItemIds()).containsExactly(lipid.getId());
    }

    @Test
    void rejectSharedSpecimenMarksEveryLinkedTestForRecollection() {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        LabOrderItemEntity cbc = sampleOrderItem(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "CBC", "Complete Blood Count", 1);
        LabOrderItemEntity hba1c = sampleOrderItem(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "HBA1C", "HbA1c", 2);
        LabOrderSampleEntity sample = LabOrderSampleEntity.create(
                TENANT_ID,
                ORDER_ID,
                null,
                "LAB-20260823-0003",
                "LAB-20260823-0003",
                "Blood",
                "EDTA",
                OffsetDateTime.parse("2026-08-23T04:44:00Z"),
                ACTOR_ID,
                null,
                ACTOR_ID
        );
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderSampleRepository.findByTenantIdAndId(TENANT_ID, sample.getId())).thenReturn(Optional.of(sample));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbc, hba1c));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                specimenView(cbc.getId(), cbc.getTestName(), sample.getId(), "CBC"),
                specimenView(hba1c.getId(), hba1c.getTestName(), sample.getId(), "HBA1C")
        ));
        when(labOrderResultRepository.findByTenantIdAndLabOrderItemId(TENANT_ID, cbc.getId())).thenReturn(List.of());
        when(labOrderResultRepository.findByTenantIdAndLabOrderItemId(TENANT_ID, hba1c.getId())).thenReturn(List.of());
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sample));

        var rejected = service.rejectSample(TENANT_ID, sample.getId(), new LabSampleRejectCommand("Hemolysed sample", true, "Recollect"), ACTOR_ID);

        assertThat(rejected.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabSampleStatusRecord.RECOLLECTION_REQUIRED);
        verify(laboratoryWorkflowService).rejectSpecimenForTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                org.mockito.ArgumentMatchers.eq(sample.getId()),
                org.mockito.ArgumentMatchers.argThat(ids -> ids != null && ids.containsAll(List.of(cbc.getId(), hba1c.getId())) && ids.size() == 2),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
    }

    @Test
    void rejectIndependentSpecimenLeavesOtherTestsUnaffected() {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        LabOrderItemEntity cbc = sampleOrderItem(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "CBC", "Complete Blood Count", 1);
        LabOrderItemEntity lipid = sampleOrderItem(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "LIPID", "Lipid Profile", 2);
        LabOrderSampleEntity cbcSample = LabOrderSampleEntity.create(
                TENANT_ID,
                ORDER_ID,
                cbc.getId(),
                "LAB-20260823-0003",
                "LAB-20260823-0003",
                "Blood",
                "EDTA",
                OffsetDateTime.parse("2026-08-23T04:44:00Z"),
                ACTOR_ID,
                null,
                ACTOR_ID
        );
        LabOrderSampleEntity lipidSample = LabOrderSampleEntity.create(
                TENANT_ID,
                ORDER_ID,
                lipid.getId(),
                "LAB-20260823-0004",
                "LAB-20260823-0004",
                "Blood",
                "Plain Tube",
                OffsetDateTime.parse("2026-08-23T04:44:00Z"),
                ACTOR_ID,
                null,
                ACTOR_ID
        );
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderSampleRepository.findByTenantIdAndId(TENANT_ID, cbcSample.getId())).thenReturn(Optional.of(cbcSample));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbc, lipid));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                specimenView(cbc.getId(), cbc.getTestName(), cbcSample.getId(), "CBC"),
                specimenView(lipid.getId(), lipid.getTestName(), lipidSample.getId(), "LIPID")
        ));
        when(labOrderResultRepository.findByTenantIdAndLabOrderItemId(TENANT_ID, cbc.getId())).thenReturn(List.of());

        var rejected = service.rejectSample(TENANT_ID, cbcSample.getId(), new LabSampleRejectCommand("Wrong container", true, null), ACTOR_ID);

        assertThat(rejected.linkedLabOrderItemIds()).containsExactly(cbc.getId());
        verify(laboratoryWorkflowService).rejectSpecimenForTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                org.mockito.ArgumentMatchers.eq(cbcSample.getId()),
                org.mockito.ArgumentMatchers.argThat(ids -> ids != null && ids.size() == 1 && ids.contains(cbc.getId())),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
    }

    @Test
    void resolvePaymentReceiptUsesReadableCollectorLabel() {
        UUID billId = UUID.fromString("77777777-7777-4777-8777-777777777777");
        UUID paymentId = UUID.fromString("88888888-8888-4888-8888-888888888888");
        UUID receiptId = UUID.fromString("99999999-9999-4999-9999-999999999999");
        var bill = sampleBill();
        var payment = new com.deepthoughtnet.clinic.billing.service.model.PaymentRecord(
                paymentId,
                TENANT_ID,
                billId,
                LocalDate.now(),
                OffsetDateTime.now(),
                BigDecimal.valueOf(650),
                com.deepthoughtnet.clinic.billing.service.model.PaymentMode.UPI,
                "TXN-123",
                "Payment notes",
                ACTOR_ID,
                receiptId,
                "REC-0001",
                LocalDate.now(),
                OffsetDateTime.now()
        );
        when(billingService.listReceipts(TENANT_ID, billId)).thenReturn(List.of(new ReceiptRecord(
                receiptId,
                TENANT_ID,
                "REC-0001",
                billId,
                paymentId,
                LocalDate.now(),
                BigDecimal.valueOf(650),
                OffsetDateTime.now()
        )));
        when(billingService.listPayments(TENANT_ID, billId)).thenReturn(List.of(payment));
        when(billingService.findById(TENANT_ID, billId)).thenReturn(Optional.of(bill));
        when(billingService.receivedByDisplayLabel(TENANT_ID, ACTOR_ID)).thenReturn("UAT Automation Lab Front Desk");

        var receipt = service.resolvePaymentReceipt(TENANT_ID, billId).orElseThrow();

        assertThat(receipt.collectedBy()).isEqualTo("UAT Automation Lab Front Desk");
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
                new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(null, "Blood", "EDTA", OffsetDateTime.now(), null),
                new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(null, "Blood", "EDTA", OffsetDateTime.now(), null)
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
                null
        )), ACTOR_ID);
        var tenantTwoSamples = service.collectSamples(otherTenant, ORDER_ID, List.of(new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(
                null,
                "Blood",
                "EDTA",
                OffsetDateTime.now(),
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
                null
        )), ACTOR_ID);

        assertThat(samples.getFirst().accessionNumber()).isEqualTo("LAB-" + datePortion + "-0002");
    }

    @Test
    void collectSamplesDerivesCollectorFromAuthenticatedUser() {
        var order = sampleOrder(LabOrderStatus.READY_FOR_COLLECTION);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        var samples = service.collectSamples(TENANT_ID, ORDER_ID, List.of(new com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand(
                null,
                "Blood",
                "EDTA",
                OffsetDateTime.now(),
                null
        )), ACTOR_ID);

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().collectedBy()).isEqualTo(ACTOR_ID);
        assertThat(order.getSampleCollectedByUserId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void receiveSampleOnlyAllowedFromCollected() {
        LabOrderSampleEntity sample = collectedOnlySample();
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
                .hasMessageContaining("received sample");
    }

    @Test
    void rejectsResultEntryBeforeReceive() {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.enterResults(TENANT_ID, ORDER_ID, new LabOrderResultEntryCommand(List.of(
                new LabOrderResultItemCommand(UUID.randomUUID(), "13.4", "mg/dL", "10-20", List.of())
        ), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("received sample");
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
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL"), List.<UUID>of(), null, null),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishing");
    }

    @Test
    void rejectsResultEntryWhenRecollectionRequired() {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        LabOrderSampleEntity sample = collectedOnlySample();
        sample.markRejected("Hemolysed sample", true, null, ACTOR_ID);
        UUID itemId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        LabOrderItemEntity orderItem = LabOrderItemEntity.create(
                TENANT_ID,
                ORDER_ID,
                testId,
                "CBC",
                "Complete Blood Count",
                "HEMATOLOGY",
                null,
                "Blood",
                "g/dL",
                "13.0-17.0",
                "<7.0",
                BigDecimal.valueOf(100),
                1
        );
        setOrderItemId(orderItem, itemId);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.enterResults(TENANT_ID, ORDER_ID, new LabOrderResultEntryCommand(List.of(
                new LabOrderResultItemCommand(itemId, "13.4", "mg/dL", "10-20", List.of())
        ), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("received sample");
    }

    @Test
    void flagsNumericResultsRespectParsedRanges() {
        assertFlag("4.5", "4.0-5.6", "<7.0", "NORMAL", false);
        assertFlag("14", "13.0-17.0", "<7.0", "NORMAL", false);
        assertFlag("16", "13.0-17.0", "<7.0", "NORMAL", false);
        assertFlag("7", "4.0-11.0", ">20.0", "NORMAL", false);
        assertFlag("350", "150-450", "<50", "NORMAL", false);
        assertFlag("130", "70-100", ">300", "HIGH", false);
        assertFlag("350", "70-100", ">300", "CRITICAL_HIGH", true);
        assertFlag("6.5", "13.0-17.0", "<7.0", "CRITICAL_LOW", true);
        assertFlag("22", "4.0-11.0", ">20.0", "CRITICAL_HIGH", true);
        assertFlag("40", "150-450", "<50", "CRITICAL_LOW", true);

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
        order.ensureReportVerificationToken();
        setOrderField(order, "labVerifiedBy", ACTOR_ID);
        setOrderField(order, "labVerifiedAt", OffsetDateTime.now().minusMinutes(15));
        setOrderField(order, "reportPublishedAt", OffsetDateTime.now().minusMinutes(10));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(new TenantUserRecord(
                ACTOR_ID,
                TENANT_ID,
                "approver-sub",
                "approver@example.com",
                "UAT Automation Lab Approver",
                "ACTIVE",
                "LAB_APPROVER",
                "ACTIVE",
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                "ACTIVE"
        )));
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
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(text).contains("LABORATORY REPORT");
            assertThat(text).contains("Jeevanam Diagnostics");
            assertThat(text).contains("Generated by Jeevanam Healthcare | Powered by AIVA");
            assertThat(text).contains("Patient & Report Details");
            assertThat(text).contains("Approved By");
            assertThat(text).contains("Approved At");
            assertThat(text).contains("Verification Reference");
            assertThat(text).contains("Authorized Signatory");
            assertThat(text).contains("Interpretation");
            assertThat(text).contains("Normal");
            assertThat(text).doesNotContain("CRITICAL_HIGH");
            assertThat(text).doesNotContain("CRITICAL_LOW");
            assertThat(text).doesNotContain("Arogia");
            assertThat(text).doesNotContain("Collected By");
            assertThat(text).doesNotContain("Report Type");
            assertThat(text).doesNotContain("Report Status");
            assertThat(text).doesNotContain("Published By");
            assertThat(text).doesNotContain("Published At");
            assertThat(text).doesNotContain("Reviewed By");
            assertThat(text).doesNotContain("Reviewed At");
            assertThat(text).doesNotContain("Result Entered By");
            assertThat(text).doesNotContain("Result Entered At");
        }
    }

    @Test
    void rendersGroupedLabReportPdfWithExplicitGroupedHeaderAndPerTestSections() throws Exception {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.DELIVERED);
        setOrderId(order, ORDER_ID);
        order.ensureReportVerificationToken();
        setOrderField(order, "labVerifiedBy", ACTOR_ID);
        setOrderField(order, "labVerifiedAt", OffsetDateTime.now().minusMinutes(15));
        setOrderField(order, "reportPublishedAt", OffsetDateTime.now().minusMinutes(10));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(new TenantUserRecord(
                ACTOR_ID,
                TENANT_ID,
                "approver-sub",
                "approver@example.com",
                "UAT Automation Lab Approver",
                "ACTIVE",
                "LAB_APPROVER",
                "ACTIVE",
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                "ACTIVE"
        )));
        LabOrderItemEntity cbcItem = sampleOrderItem(cbcItemId, "CBC", "Complete Blood Count", 1);
        LabOrderItemEntity hba1cItem = sampleOrderItem(hba1cItemId, "HBA1C", "HbA1c", 2);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbcItem, hba1cItem));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                LabOrderResultEntity.create(TENANT_ID, ORDER_ID, cbcItemId, "CBC", "Complete Blood Count", "Hemoglobin", null, "14.2", "g/dL", "13.0-17.0", 1, "NORMAL", false),
                LabOrderResultEntity.create(TENANT_ID, ORDER_ID, hba1cItemId, "HBA1C", "HbA1c", "HbA1c", null, "5.8", "%", "4.0-6.0", 2, "NORMAL", false)
        ));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, 2, OffsetDateTime.now().minusMinutes(10), ACTOR_ID, List.of("PATIENT_PORTAL"), null, OffsetDateTime.now().minusMinutes(20), ACTOR_ID, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "PUBLISHED", 1, null, null, null, 2, OffsetDateTime.now().minusMinutes(10), ACTOR_ID, List.of("PATIENT_PORTAL"), null, OffsetDateTime.now().minusMinutes(15), ACTOR_ID, List.of())
        ));
        when(laboratoryWorkflowService.listReportArtifacts(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryReportArtifactView(
                        UUID.randomUUID(),
                        TENANT_ID,
                        ORDER_ID,
                        2,
                        "CONSOLIDATED",
                        "FINAL",
                        "CURRENT",
                        "lab-report-v2.pdf",
                        "lab/report/lab-report-v2.pdf",
                        "verification-token",
                        "https://reports.example.com/verify/verification-token",
                        List.of("PATIENT_PORTAL"),
                        List.of(cbcItemId, hba1cItemId),
                        OffsetDateTime.now().minusMinutes(5),
                        ACTOR_ID,
                        OffsetDateTime.now().minusMinutes(5),
                        ACTOR_ID,
                        null,
                        null,
                        null
                )
        ));

        var pdf = service.renderReportPdf(TENANT_ID, ORDER_ID, List.of(cbcItemId, hba1cItemId), "GROUPED", "FINAL");

        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(text).contains("GROUPED LABORATORY REPORT");
            assertThat(text).contains("Results");
            assertThat(text).contains("Complete Blood Count");
            assertThat(text).contains("HbA1c");
        }
    }

    @Test
    void rendersOnlyClinicalNotesInPatientFacingPdf() throws Exception {
        var order = sampleOrder(LabOrderStatus.REPORT_READY);
        setOrderId(order, ORDER_ID);
        setOrderField(order, "sampleCollectionNotes", "Sample mildly hemolyzed; interpret with caution.");
        setOrderField(order, "resultComments", "E2E retest sample collected");
        setOrderField(order, "labVerifiedBy", ACTOR_ID);
        setOrderField(order, "labVerifiedAt", OffsetDateTime.now().minusMinutes(15));
        setOrderField(order, "reportPublishedAt", OffsetDateTime.now().minusMinutes(10));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(new TenantUserRecord(
                ACTOR_ID,
                TENANT_ID,
                "approver-sub",
                "approver@example.com",
                "UAT Automation Lab Approver",
                "ACTIVE",
                "LAB_APPROVER",
                "ACTIVE",
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                "ACTIVE"
        )));
        order.ensureReportVerificationToken();
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));

        var pdf = service.renderReportPdf(TENANT_ID, ORDER_ID);

        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(text).contains("Clinical / Laboratory Notes");
            assertThat(text).contains("Sample mildly hemolyzed; interpret with caution.");
            assertThat(text).doesNotContain("E2E retest sample collected");
        }
    }

    @Test
    void rendersLongMultiTestReportAcrossA4PagesWithRepeatedHeadersAndStableFooters() throws Exception {
        var order = sampleOrder(LabOrderStatus.REPORT_READY);
        setOrderId(order, ORDER_ID);
        order.ensureReportVerificationToken();
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID))
                .thenReturn(IntStream.range(0, 40).mapToObj(index -> sampleResult()).toList());
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(collectedSample()));
        when(labReportVerificationProperties.getPublicBaseUrl()).thenReturn(
                "https://published-reports.uat.jeevanam-healthcare.example/tenant-safe/report-authenticity");

        var pdf = service.renderReportPdf(TENANT_ID, ORDER_ID);

        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            int qrImageCount = 0;
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                var page = document.getPage(pageIndex);
                assertThat(page.getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
                assertThat(page.getMediaBox().getHeight()).isEqualTo(PDRectangle.A4.getHeight());
                PDFTextStripper pageStripper = new PDFTextStripper();
                pageStripper.setStartPage(pageIndex + 1);
                pageStripper.setEndPage(pageIndex + 1);
                String pageText = pageStripper.getText(document);
                assertThat(pageText).contains("Generated by Jeevanam Healthcare | Powered by AIVA");
                assertThat(pageText).contains("Page ");
                for (var ignored : page.getResources().getXObjectNames()) {
                    qrImageCount++;
                }
            }
            String text = new PDFTextStripper().getText(document);
            assertThat(text.split("Parameter / Component", -1).length - 1).isGreaterThan(1);
            assertThat(text).contains("Verification Reference");
            assertThat(qrImageCount).isGreaterThanOrEqualTo(1);
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
                com.deepthoughtnet.clinic.api.lab.db.LabTestParameterEntity.create(TENANT_ID, testId, "Hemoglobin", "g/dL", normalRange, criticalRange, 1)
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
                com.deepthoughtnet.clinic.api.lab.db.LabTestParameterEntity.create(TENANT_ID, testId, "Potassium", "mmol/L", "3.5-5.1", "2.5-6.0", 1)
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
    void enterResultsCanResubmitOnlySentBackTestInMixedState() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.PARTIALLY_READY);
        setOrderId(order, ORDER_ID);
        LabOrderItemEntity cbcItem = sampleOrderItem(cbcItemId, "CBC", "Complete Blood Count", 1);
        LabOrderItemEntity hba1cItem = sampleOrderItem(hba1cItemId, "HbA1c", "Hemoglobin A1c", 2);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbcItem, hba1cItem));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                LabOrderResultEntity.create(TENANT_ID, ORDER_ID, cbcItemId, "CBC", "Complete Blood Count", "Hemoglobin", null, "14.5", "g/dL", "13.0-17.0", 1, "NORMAL", false),
                LabOrderResultEntity.create(TENANT_ID, ORDER_ID, hba1cItemId, "HbA1c", "Hemoglobin A1c", "HbA1c", null, "5.3", "%", "4.0-6.0", 2, "NORMAL", false)
        ));
        when(labTestParameterRepository.findByTenantIdAndLabTestIdOrderBySortOrderAsc(TENANT_ID, cbcItem.getLabTestId())).thenReturn(List.of(
                com.deepthoughtnet.clinic.api.lab.db.LabTestParameterEntity.create(TENANT_ID, cbcItem.getLabTestId(), "Hemoglobin", "g/dL", "13.0-17.0", "<7.0", 1)
        ));
        when(labTestParameterRepository.findByTenantIdAndLabTestIdOrderBySortOrderAsc(TENANT_ID, hba1cItem.getLabTestId())).thenReturn(List.of(
                com.deepthoughtnet.clinic.api.lab.db.LabTestParameterEntity.create(TENANT_ID, hba1cItem.getLabTestId(), "HbA1c", "%", "4.0-6.0", ">10.0", 1)
        ));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "VERIFIED", 1, "APPROVE", OffsetDateTime.now().minusMinutes(20), ACTOR_ID, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(20), ACTOR_ID, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "RESULT_SENT_BACK", 1, "SEND_BACK", OffsetDateTime.now().minusMinutes(5), ACTOR_ID, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(5), ACTOR_ID, List.of())
        ));
        when(laboratoryWorkflowService.deriveAggregateOrderState(TENANT_ID, ORDER_ID)).thenReturn("PARTIALLY_READY");
        AtomicReference<List<LabOrderResultEntity>> savedResults = new AtomicReference<>(List.of());
        when(labOrderResultRepository.saveAll(anyList())).thenAnswer(invocation -> {
            savedResults.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        var result = service.enterResults(
                TENANT_ID,
                ORDER_ID,
                new LabOrderResultEntryCommand(List.of(
                        new LabOrderResultItemCommand(hba1cItemId, "5.2", "%", "4.0-6.0", List.of())
                ), "Corrected after verification", List.of(hba1cItemId)),
                ACTOR_ID
        );

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.PARTIALLY_READY);
        assertThat(savedResults.get()).hasSize(1);
        assertThat(savedResults.get().getFirst().getLabOrderItemId()).isEqualTo(hba1cItemId);
        assertThat(savedResults.get().getFirst().getResultValue()).isEqualTo("5.2");
    }

    @Test
    void enterResultsRejectsExplicitVerifiedSiblingSelection() {
        UUID cbcItemId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID hba1cItemId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        var order = sampleOrder(LabOrderStatus.PARTIALLY_READY);
        setOrderId(order, ORDER_ID);
        LabOrderItemEntity cbcItem = sampleOrderItem(cbcItemId, "CBC", "Complete Blood Count", 1);
        LabOrderItemEntity hba1cItem = sampleOrderItem(hba1cItemId, "HbA1c", "Hemoglobin A1c", 2);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbcItem, hba1cItem));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "VERIFIED", 1, "APPROVE", OffsetDateTime.now().minusMinutes(20), ACTOR_ID, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(20), ACTOR_ID, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "RESULT_SENT_BACK", 1, "SEND_BACK", OffsetDateTime.now().minusMinutes(5), ACTOR_ID, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(5), ACTOR_ID, List.of())
        ));

        assertThatThrownBy(() -> service.enterResults(
                TENANT_ID,
                ORDER_ID,
                new LabOrderResultEntryCommand(List.of(
                        new LabOrderResultItemCommand(cbcItemId, "14.7", "g/dL", "13.0-17.0", List.of())
                ), "Attempted CBC edit", List.of(cbcItemId)),
                ACTOR_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already verified");
    }

    @Test
    void verifyFromResultEnteredMovesOrderToReportReady() {
        var order = sampleOrder(LabOrderStatus.RESULT_ENTERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));

        var result = service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("APPROVE", List.of(), false, null, "Verified"), ACTOR_ID);

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.REPORT_READY);
        assertThat(result.labVerificationDecision()).isEqualTo("APPROVE");
    }

    @Test
    void verifyApproveAllowsMissingReasonAndComments() {
        var order = sampleOrder(LabOrderStatus.RESULT_ENTERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));

        var result = service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("APPROVE", List.of(), false, null, null), ACTOR_ID);

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.REPORT_READY);
        assertThat(result.labVerificationDecision()).isEqualTo("APPROVE");
    }

    @Test
    void verifyAllEligibleTestsStillWorksForBulkHappyPath() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.RESULT_ENTERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult(), sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        LabOrderItemEntity cbcItem = LabOrderItemEntity.create(TENANT_ID, ORDER_ID, cbcItemId, "CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), 1);
        LabOrderItemEntity hba1cItem = LabOrderItemEntity.create(TENANT_ID, ORDER_ID, hba1cItemId, "HbA1c", "Hemoglobin A1c", "CHEMISTRY", null, "Blood", null, null, null, BigDecimal.valueOf(100), 2);
        setEntityId(cbcItem, cbcItemId);
        setEntityId(hba1cItem, hba1cItemId);
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbcItem, hba1cItem));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "RESULT_ENTERED", 1, null, null, null, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(10), ACTOR_ID, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "RESULT_ENTERED", 1, null, null, null, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(5), ACTOR_ID, List.of())
        ));
        when(laboratoryWorkflowService.deriveAggregateOrderState(TENANT_ID, ORDER_ID)).thenReturn("REPORT_READY");

        var result = service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("APPROVE", List.of(), false, null, null), ACTOR_ID);

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.REPORT_READY);
        assertThat(result.labVerificationDecision()).isEqualTo("APPROVE");
        verify(laboratoryWorkflowService).verifyTests(TENANT_ID, ORDER_ID, List.of(cbcItemId, hba1cItemId), "APPROVE", null, null, false, ACTOR_ID);
    }

    @Test
    void verifySelectedTestInMixedStateCanSendBackWithoutAggregateGate() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.PARTIALLY_READY);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult(), sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        LabOrderItemEntity cbcItem = LabOrderItemEntity.create(TENANT_ID, ORDER_ID, cbcItemId, "CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, null, BigDecimal.valueOf(100), 1);
        LabOrderItemEntity hba1cItem = LabOrderItemEntity.create(TENANT_ID, ORDER_ID, hba1cItemId, "HbA1c", "Hemoglobin A1c", "CHEMISTRY", null, "Blood", null, null, null, BigDecimal.valueOf(100), 2);
        setEntityId(cbcItem, cbcItemId);
        setEntityId(hba1cItem, hba1cItemId);
        when(labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(cbcItem, hba1cItem));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "VERIFIED", 1, "APPROVE", OffsetDateTime.now().minusMinutes(20), ACTOR_ID, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(20), ACTOR_ID, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "RESULT_ENTERED", 1, null, null, null, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(5), ACTOR_ID, List.of())
        ));
        when(laboratoryWorkflowService.deriveAggregateOrderState(TENANT_ID, ORDER_ID)).thenReturn("PARTIALLY_READY");

        var result = service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("SEND_BACK", List.of(hba1cItemId), false, "Incorrect result value", null), ACTOR_ID);

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.PARTIALLY_READY);
        assertThat(result.labVerificationDecision()).isEqualTo("SEND_BACK");
        verify(laboratoryWorkflowService).verifyTests(TENANT_ID, ORDER_ID, List.of(hba1cItemId), "SEND_BACK", "Incorrect result value", null, false, ACTOR_ID);
    }

    @Test
    void verifyExplicitAlreadyVerifiedTestIsRejected() {
        UUID cbcItemId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID hba1cItemId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        var order = sampleOrder(LabOrderStatus.PARTIALLY_READY);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult(), sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "VERIFIED", 1, "APPROVE", OffsetDateTime.now().minusMinutes(20), ACTOR_ID, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(20), ACTOR_ID, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "RESULT_ENTERED", 1, null, null, null, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(5), ACTOR_ID, List.of())
        ));

        assertThatThrownBy(() -> service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("SEND_BACK", List.of(cbcItemId), false, "Incorrect result value", null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already verified");
        verify(laboratoryWorkflowService, never()).verifyTests(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void findOrderIncludesTechnicianResultEntryAuditMetadata() {
        var order = sampleOrder(LabOrderStatus.RESULT_ENTERED);
        setOrderId(order, ORDER_ID);
        order.markResultsEntered("Technician-entered comments.");
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult()));
        when(auditEventQueryService.listForEntity(TENANT_ID, "LAB_ORDER", ORDER_ID)).thenReturn(List.of(new AuditEventRecord(
                UUID.randomUUID(),
                TENANT_ID,
                "LAB_ORDER",
                ORDER_ID,
                "lab_order.results_entered",
                ACTOR_ID,
                OffsetDateTime.now().minusMinutes(3),
                "Entered lab results",
                "{\"id\":\"" + ORDER_ID + "\"}"
        )));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(new TenantUserRecord(
                ACTOR_ID,
                TENANT_ID,
                "keycloak-sub",
                "lab.tech@example.com",
                "labtech",
                null,
                "UAT Lab Technician",
                "ACTIVE",
                "LAB_TECHNICIAN",
                "ACTIVE",
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                "ACTIVE"
        )));

        var record = service.findOrder(TENANT_ID, ORDER_ID).orElseThrow();

        assertThat(record.resultEnteredAt()).isEqualTo(order.getResultEnteredAt());
        assertThat(record.resultEnteredByUserId()).isEqualTo(ACTOR_ID);
        assertThat(record.resultEnteredBy()).isEqualTo("UAT Lab Technician");
        assertThat(record.resultComments()).isEqualTo("Technician-entered comments.");
        assertThat(record.results()).hasSize(1);
    }

    @Test
    void cannotVerifyBeforeResultEntry() {
        var order = sampleOrder(LabOrderStatus.SAMPLE_COLLECTED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("APPROVE", List.of(), false, null, null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result values");
    }

    @Test
    void sendBackKeepsOrderEditableWithoutCommentsOrRecollection() {
        var order = sampleOrder(LabOrderStatus.RESULT_ENTERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(sampleResult()));
        when(labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(TENANT_ID, ORDER_ID)).thenReturn(List.of(collectedSample()));

        var recollectionCaptor = ArgumentCaptor.forClass(Boolean.class);
        var result = service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("SEND_BACK", List.of(), true, "Delta check", null), ACTOR_ID);

        assertThat(result.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.RESULT_ENTERED);
        assertThat(result.labVerificationDecision()).isEqualTo("SEND_BACK");
        verify(laboratoryWorkflowService).verifyTests(any(), any(), any(), any(), any(), any(), recollectionCaptor.capture(), any());
        assertThat(recollectionCaptor.getValue()).isFalse();
    }

    @Test
    void sendBackRejectsMissingReason() {
        assertThatThrownBy(() -> service.verifyResults(TENANT_ID, ORDER_ID, new LabOrderVerificationCommand("SEND_BACK", List.of(), false, null, null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
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
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL", "PRINT"), List.<UUID>of(), "Published in batch 4", null),
                ACTOR_ID
        );

        assertThat(published.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.REPORT_GENERATED);
        assertThat(published.reportDeliveryStatus()).isEqualTo("PUBLISHED");
        assertThat(published.reportDeliveryChannels()).containsExactly("PATIENT_PORTAL", "PRINT");
        assertThat(published.reportVerificationToken()).isNotBlank();
        assertThat(published.reportPublishedAt()).isNotNull();
        assertThat(published.reportGeneratedAt()).isNotNull();
        verify(clinicalDocumentService).publishLabReport(any());
        verify(labNotificationService, never()).notifyDoctorReportPublished(any(), any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<LabReportPublishedEvent> eventCaptor = ArgumentCaptor.forClass(LabReportPublishedEvent.class);
        verify(moduleBusinessEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().timezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void publishReportDoesNotAutoNotifyDoctorWhenChannelIsNotRequested() {
        var order = sampleOrder(LabOrderStatus.REPORT_READY);
        setOrderId(order, ORDER_ID);
        UUID doctorUserId = UUID.randomUUID();
        setOrderField(order, "requestedByInternalDoctorId", doctorUserId);
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(new TenantUserRecord(
                doctorUserId,
                TENANT_ID,
                "doctor-sub",
                "doctor@example.com",
                "Dr Example",
                "ACTIVE",
                "DOCTOR",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "READY"
        )));
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL"), List.<UUID>of(), null, null),
                ACTOR_ID
        );

        verify(labNotificationService, never()).notifyDoctorReportPublished(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void publishReportPublishesOnlySelectedVerifiedTestInSecondStagePartialPublication() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.PARTIALLY_PUBLISHED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "VERIFIED", 1, "APPROVE", OffsetDateTime.now().minusMinutes(5), ACTOR_ID, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(5), ACTOR_ID, List.of())
        ));
        when(laboratoryWorkflowService.deriveAggregateOrderState(TENANT_ID, ORDER_ID)).thenReturn("DELIVERED");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> selectedCaptor = ArgumentCaptor.forClass((Class) List.class);

        var published = service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL"), List.of(hba1cItemId), "Second stage publication", null),
                ACTOR_ID
        );

        verify(laboratoryWorkflowService).publishTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                selectedCaptor.capture(),
                anyString(),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                anyString(),
                anyString(),
                nullable(String.class),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
        assertThat(selectedCaptor.getValue()).containsExactly(hba1cItemId);
        assertThat(published.status()).isEqualTo(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord.DELIVERED);
        assertThat(published.reportDeliveryStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void publishReportAllowsAlreadyPublishedTestsAsGroupedReportContent() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.DELIVERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of())
        ));
        when(laboratoryWorkflowService.deriveAggregateOrderState(TENANT_ID, ORDER_ID)).thenReturn("DELIVERED");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> selectedCaptor = ArgumentCaptor.forClass((Class) List.class);

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(
                        List.of("PATIENT_PORTAL"), List.of(cbcItemId, hba1cItemId), "Regenerate grouped report", "GROUPED"),
                ACTOR_ID
        );

        verify(laboratoryWorkflowService).publishTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                selectedCaptor.capture(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyString(),
                nullable(String.class), org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
        assertThat(selectedCaptor.getValue()).containsExactly(cbcItemId, hba1cItemId);
        verify(laboratoryWorkflowService, times(1)).publishTests(any(), any(), anyCollection(), anyString(), anyString(), anyString(), anyList(), anyString(), anyString(), nullable(String.class), any());
    }

    @Test
    void publishReportDoesNotCreateDuplicateVersionForIdenticalCurrentFinalReport() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.DELIVERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of())
        ));
        when(laboratoryWorkflowService.listReportArtifacts(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryReportArtifactView(
                        UUID.randomUUID(),
                        TENANT_ID,
                        ORDER_ID,
                        2,
                        "CONSOLIDATED",
                        "FINAL",
                        "CURRENT",
                        "lab-report-v2.pdf",
                        "lab/report/lab-report-v2.pdf",
                        "verification-token",
                        "https://reports.example.com/verify/verification-token",
                        List.of("PATIENT_PORTAL"),
                        List.of(cbcItemId, hba1cItemId),
                        OffsetDateTime.now().minusMinutes(5),
                        ACTOR_ID,
                        OffsetDateTime.now().minusMinutes(5),
                        ACTOR_ID,
                        null,
                        null,
                        null
                )
        ));

        var result = service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(
                        List.of("PATIENT_PORTAL"),
                        List.of(cbcItemId, hba1cItemId),
                        null,
                        "CONSOLIDATED"),
                ACTOR_ID
        );

        assertThat(result.id()).isEqualTo(ORDER_ID);
        verify(laboratoryWorkflowService, never()).publishTests(any(), any(), anyCollection(), anyString(), anyString(), anyString(), anyList(), anyString(), anyString(), nullable(String.class), any());
        verify(clinicalDocumentService, never()).publishLabReport(any());
    }

    @Test
    void publishReportSupersedesInterimCurrentWhenFinalConsolidatedContentExpands() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.DELIVERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of())
        ));
        when(laboratoryWorkflowService.listReportArtifacts(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryReportArtifactView(UUID.randomUUID(), TENANT_ID, ORDER_ID, 1, "GROUPED", "INTERIM", "CURRENT",
                        "lab-report-v1.pdf", "lab/report/lab-report-v1.pdf", "token-v1", "https://reports.example.com/verify/token-v1",
                        List.of("PATIENT_PORTAL"), List.of(cbcItemId), OffsetDateTime.now().minusMinutes(20), ACTOR_ID,
                        OffsetDateTime.now().minusMinutes(20), ACTOR_ID, null, null, null)
        ));

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(
                        List.of("PATIENT_PORTAL"),
                        List.of(cbcItemId, hba1cItemId),
                        "Finalize all available tests",
                        "CONSOLIDATED"),
                ACTOR_ID
        );

        verify(laboratoryWorkflowService).publishTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                anyCollection(),
                anyString(),
                anyString(),
                anyString(),
                anyList(),
                anyString(),
                anyString(),
                nullable(String.class),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
        verify(laboratoryWorkflowService, never()).generateReportArtifact(any(), any(), anyCollection(), anyString(), anyString(), anyString(), anyList(), anyString(), anyString(), anyString(), nullable(String.class), any());
    }

    @Test
    void publishReportGeneratesHistoricalIndividualAfterCurrentFinalWithoutRepublishing() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.DELIVERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of())
        ));
        when(laboratoryWorkflowService.listReportArtifacts(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryReportArtifactView(UUID.randomUUID(), TENANT_ID, ORDER_ID, 2, "CONSOLIDATED", "FINAL", "CURRENT",
                        "lab-report-v2.pdf", "lab/report/lab-report-v2.pdf", "token", "https://reports.example.com/verify/token",
                        List.of("PATIENT_PORTAL"), List.of(cbcItemId, hba1cItemId), OffsetDateTime.now(), ACTOR_ID,
                        OffsetDateTime.now(), ACTOR_ID, null, null, null)
        ));

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(
                        List.of("PATIENT_PORTAL"), List.of(cbcItemId), null, "INDIVIDUAL"),
                ACTOR_ID
        );

        verify(laboratoryWorkflowService).generateReportArtifact(
                any(), any(), anyCollection(), anyString(), anyString(), anyString(), anyList(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq("HISTORICAL"),
                nullable(String.class), any()
        );
        verify(laboratoryWorkflowService, never()).publishTests(any(), any(), anyCollection(), anyString(), anyString(), anyString(), anyList(), anyString(), anyString(), nullable(String.class), any());
    }

    @Test
    void publishReportGeneratesHistoricalGroupedAfterCurrentFinalWithoutRepublishing() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.DELIVERED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of())
        ));
        when(laboratoryWorkflowService.listReportArtifacts(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryReportArtifactView(UUID.randomUUID(), TENANT_ID, ORDER_ID, 2, "CONSOLIDATED", "FINAL", "CURRENT",
                        "lab-report-v2.pdf", "lab/report/lab-report-v2.pdf", "token", "https://reports.example.com/verify/token",
                        List.of("PATIENT_PORTAL"), List.of(cbcItemId, hba1cItemId), OffsetDateTime.now(), ACTOR_ID,
                        OffsetDateTime.now(), ACTOR_ID, null, null, null)
        ));

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(
                        List.of("PATIENT_PORTAL"), List.of(cbcItemId, hba1cItemId), null, "GROUPED"),
                ACTOR_ID
        );

        verify(laboratoryWorkflowService).generateReportArtifact(
                any(), any(), anyCollection(), anyString(), anyString(), anyString(), anyList(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq("HISTORICAL"),
                nullable(String.class), any()
        );
        verify(laboratoryWorkflowService, never()).publishTests(any(), any(), anyCollection(), anyString(), anyString(), anyString(), anyList(), anyString(), anyString(), nullable(String.class), any());
    }

    @Test
    void publishReportIncludesPublishedAndTransitionsOnlyVerifiedTest() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.PARTIALLY_PUBLISHED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "VERIFIED", 1, "APPROVE", OffsetDateTime.now(), ACTOR_ID, null, null, null, List.of(), null, OffsetDateTime.now(), ACTOR_ID, List.of())
        ));
        when(laboratoryWorkflowService.deriveAggregateOrderState(TENANT_ID, ORDER_ID)).thenReturn("DELIVERED");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> selectedCaptor = ArgumentCaptor.forClass((Class) List.class);

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(
                        List.of("PATIENT_PORTAL"), List.of(cbcItemId, hba1cItemId), "Consolidate reports", "GROUPED"),
                ACTOR_ID
        );

        verify(laboratoryWorkflowService).publishTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                selectedCaptor.capture(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyString(),
                nullable(String.class), org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
        assertThat(selectedCaptor.getValue()).containsExactly(cbcItemId, hba1cItemId);
    }

    @Test
    void publishReportPublishesOnlyVerifiedChildrenWhenSelectionIsImplicit() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.REPORT_READY);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "VERIFIED", 1, null, null, null, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(10), ACTOR_ID, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "RESULT_ENTERED", 1, null, null, null, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(5), ACTOR_ID, List.of())
        ));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> selectedCaptor = ArgumentCaptor.forClass((Class) List.class);

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL"), List.<UUID>of(), null, null),
                ACTOR_ID
        );

        verify(laboratoryWorkflowService).publishTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                selectedCaptor.capture(),
                anyString(),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                anyString(),
                anyString(),
                nullable(String.class),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
        assertThat(selectedCaptor.getValue()).containsExactly(cbcItemId);
    }

    @Test
    void publishReportAllowsAlreadyPublishedSelectedTestAsReportContent() {
        UUID cbcItemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID hba1cItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var order = sampleOrder(LabOrderStatus.PARTIALLY_PUBLISHED);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
        when(laboratoryWorkflowService.listOrderTests(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                new LaboratoryOrderedTestView(cbcItemId, "PUBLISHED", 1, null, null, null, null, null, null, List.of(), null, null, null, List.of()),
                new LaboratoryOrderedTestView(hba1cItemId, "VERIFIED", 1, null, null, null, null, null, null, List.of(), null, OffsetDateTime.now().minusMinutes(5), ACTOR_ID, List.of())
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> selectedCaptor = ArgumentCaptor.forClass((Class) List.class);

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL"), List.of(cbcItemId), null, null),
                ACTOR_ID
        );

        verify(laboratoryWorkflowService).publishTests(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ORDER_ID),
                selectedCaptor.capture(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyString(),
                nullable(String.class), org.mockito.ArgumentMatchers.eq(ACTOR_ID)
        );
        assertThat(selectedCaptor.getValue()).containsExactly(cbcItemId);
    }

    @Test
    void renderReportPdfUsesTenantTimezoneAndAbsoluteVerificationUrl() throws Exception {
        var order = sampleOrder(LabOrderStatus.REPORT_READY);
        setOrderId(order, ORDER_ID);
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));

        service.publishReport(
                TENANT_ID,
                ORDER_ID,
                new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand(List.of("PATIENT_PORTAL"), List.<UUID>of(), null, null),
                ACTOR_ID
        );

        LabOrderResultPdf pdf = service.renderReportPdf(TENANT_ID, ORDER_ID);
        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("https://reports.example.com/api/public/lab/reports/");
            assertThat(text).contains("IST");
        }
    }

    @Test
    void verifyPublishedReportResolvesByOpaqueTokenWithoutTenantLeakage() {
        var order = sampleOrder(LabOrderStatus.REPORT_GENERATED);
        setOrderId(order, ORDER_ID);
        order.markReportPublished(ACTOR_ID, "LAB-0001-lab-report.pdf", "PUBLISHED", "[\"PATIENT_PORTAL\"]", null);
        when(labOrderRepository.findByReportVerificationToken(order.getReportVerificationToken())).thenReturn(Optional.of(order));
        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(new ClinicProfileRecord(
                UUID.randomUUID(),
                TENANT_ID,
                "Jeevanam Lab",
                "Jeevanam Lab",
                null,
                null,
                "1 Main St",
                null,
                "Bengaluru",
                "Karnataka",
                "India",
                "560001",
                null,
                null,
                null,
                true,
                true,
                "jeevanam-lab",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));

        var verification = service.verifyPublishedReport(order.getReportVerificationToken());

        assertThat(verification.valid()).isTrue();
        assertThat(verification.orderNumber()).isEqualTo("LAB-0001");
        assertThat(verification.clinicName()).isEqualTo("Jeevanam Lab");
        assertThat(verification.status()).isEqualTo("Published");
        assertThat(verification.verificationState()).isEqualTo("VERIFIED");
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
        LabOrderSampleEntity sample = LabOrderSampleEntity.create(
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
        sample.markReceived(OffsetDateTime.now().minusMinutes(9), ACTOR_ID, ACTOR_ID);
        return sample;
    }

    private LabOrderSampleEntity collectedOnlySample() {
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

    private LabOrderItemEntity sampleOrderItem(UUID id, String testCode, String testName, int sortOrder) {
        LabOrderItemEntity entity = LabOrderItemEntity.create(
                TENANT_ID,
                ORDER_ID,
                UUID.randomUUID(),
                testCode,
                testName,
                "BIOCHEMISTRY",
                null,
                "Blood",
                "g/dL",
                "10-20",
                "<7.0",
                BigDecimal.valueOf(100),
                sortOrder
        );
        setOrderItemId(entity, id);
        return entity;
    }

    private LaboratoryOrderedTestView specimenView(UUID labOrderItemId, String testName, UUID sampleId, String testCode) {
        return new LaboratoryOrderedTestView(
                labOrderItemId,
                "SAMPLE_COLLECTED",
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                List.of(new LaboratorySpecimenLinkView(
                        sampleId,
                        "LAB-20260823-0003",
                        "LAB-20260823-0003",
                        "Blood",
                        "EDTA",
                        "COLLECTED",
                        true,
                        OffsetDateTime.parse("2026-08-23T04:44:00Z"),
                        null,
                        OffsetDateTime.parse("2026-08-23T04:44:00Z"),
                        null
                ))
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

    private static void setEntityId(LabOrderItemEntity entity, UUID id) {
        try {
            Field field = LabOrderItemEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set lab order item id for test", ex);
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

    private static void setOrderField(com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity order, String fieldName, Object value) {
        try {
            Field field = com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(order, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set lab order field for test", ex);
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
