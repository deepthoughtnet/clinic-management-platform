package com.deepthoughtnet.clinic.api.lab.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderAttachmentRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderItemRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabTestParameterRepository;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDoctorReviewCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultEntryCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultItemCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderSampleCollectionCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestUpsertCommand;
import com.deepthoughtnet.clinic.api.notifications.LabNotificationService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                brandingProperties
        );
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
    void rejectsSampleCollectionBeforeReadyState() {
        when(labOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(sampleOrder(LabOrderStatus.ORDERED)));

        assertThatThrownBy(() -> service.collectSample(TENANT_ID, ORDER_ID, new LabOrderSampleCollectionCommand("Blood", "Tech", OffsetDateTime.now(), null), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ready for sample collection");
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
                null
        );
        order.markStatus(status);
        return order;
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
}
