package com.deepthoughtnet.clinic.api.lab.service;

import com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderAttachmentEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderAttachmentRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderItemEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderItemRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderOrigin;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderSampleEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderSampleRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus;
import com.deepthoughtnet.clinic.api.lab.db.LabSampleStatus;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabTestParameterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestParameterRepository;
import com.deepthoughtnet.clinic.api.lab.LabValidationSupport;
import com.deepthoughtnet.clinic.api.lab.LabCatalogueConfigService;
import com.deepthoughtnet.clinic.api.lab.LabCategoryCatalog;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.api.lab.dto.LabReportVerificationResponse;
import com.deepthoughtnet.clinic.api.lab.service.LabReportVerificationProperties;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDirectCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDoctorReviewCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderedTestRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderedTestSpecimenRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderItemRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultComponentCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultEntryCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultPdf;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultItemCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabResultFlag;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPaymentCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPaymentRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabPaymentReceiptRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderSampleCollectionCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderVerificationCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderAttachmentRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleReceiveCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleRejectCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleStatusRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestUpsertCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterUpsertCommand;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentUploadCommand;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.notifications.LabNotificationService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillUpsertCommand;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.billing.service.model.PaymentCommand;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.audit.AuditEventRecord;
import com.deepthoughtnet.clinic.platform.branding.BrandingProperties;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratoryOrderedTestView;
import com.deepthoughtnet.clinic.laboratory.service.LaboratoryWorkflowService;
import com.deepthoughtnet.clinic.laboratory.db.LaboratoryReportPublicationArtifactEntity;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratoryReportArtifactView;
import com.deepthoughtnet.clinic.laboratory.service.model.LaboratoryResultRevisionCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;

@Service
public class LabService {
    private static final String TEST_ENTITY_TYPE = "LAB_TEST";
    private static final String ORDER_ENTITY_TYPE = "LAB_ORDER";
    private static final String SAMPLE_ENTITY_TYPE = "LAB_SAMPLE";
    private static final Set<LabOrderStatus> ACTIVE_ORDER_STATUSES = Set.of(
            LabOrderStatus.ORDERED,
            LabOrderStatus.PAYMENT_PENDING,
            LabOrderStatus.PAID,
            LabOrderStatus.READY_FOR_COLLECTION,
            LabOrderStatus.SAMPLE_COLLECTED,
            LabOrderStatus.PROCESSING,
            LabOrderStatus.RESULT_ENTERED,
            LabOrderStatus.REPORT_READY,
            LabOrderStatus.REPORT_GENERATED,
            LabOrderStatus.DOCTOR_REVIEWED,
            LabOrderStatus.DELIVERED
    );
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final LabTestMasterRepository labTestMasterRepository;
    private final LabTestParameterRepository labTestParameterRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabOrderResultRepository labOrderResultRepository;
    private final LabOrderAttachmentRepository labOrderAttachmentRepository;
    private final LabOrderSampleRepository labOrderSampleRepository;
    private final ConsultationService consultationService;
    private final PatientRepository patientRepository;
    private final TenantUserManagementService tenantUserManagementService;
    private final BillingService billingService;
    private final ClinicProfileService clinicProfileService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final ClinicalDocumentService clinicalDocumentService;
    private final ObjectStorageService objectStorageService;
    private final LabNotificationService labNotificationService;
    private final ModuleBusinessEventPublisher moduleBusinessEventPublisher;
    private final AuditEventQueryService auditEventQueryService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final BrandingProperties brandingProperties;
    private final LabReportVerificationProperties labReportVerificationProperties;
    private final LabCatalogueConfigService labCatalogueConfigService;
    private final LaboratoryWorkflowService laboratoryWorkflowService;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public LabService(
            LabTestMasterRepository labTestMasterRepository,
            LabTestParameterRepository labTestParameterRepository,
            LabOrderRepository labOrderRepository,
            LabOrderItemRepository labOrderItemRepository,
            LabOrderResultRepository labOrderResultRepository,
            LabOrderAttachmentRepository labOrderAttachmentRepository,
            LabOrderSampleRepository labOrderSampleRepository,
            ConsultationService consultationService,
            PatientRepository patientRepository,
            TenantUserManagementService tenantUserManagementService,
            BillingService billingService,
            ClinicProfileService clinicProfileService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            ClinicalDocumentService clinicalDocumentService,
            ObjectStorageService objectStorageService,
            LabNotificationService labNotificationService,
            ModuleBusinessEventPublisher moduleBusinessEventPublisher,
            AuditEventQueryService auditEventQueryService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper,
            BrandingProperties brandingProperties,
            LabReportVerificationProperties labReportVerificationProperties,
            LabCatalogueConfigService labCatalogueConfigService,
            LaboratoryWorkflowService laboratoryWorkflowService,
            PlatformTransactionManager platformTransactionManager
    ) {
        this.labTestMasterRepository = labTestMasterRepository;
        this.labTestParameterRepository = labTestParameterRepository;
        this.labOrderRepository = labOrderRepository;
        this.labOrderItemRepository = labOrderItemRepository;
        this.labOrderResultRepository = labOrderResultRepository;
        this.labOrderAttachmentRepository = labOrderAttachmentRepository;
        this.labOrderSampleRepository = labOrderSampleRepository;
        this.consultationService = consultationService;
        this.patientRepository = patientRepository;
        this.tenantUserManagementService = tenantUserManagementService;
        this.billingService = billingService;
        this.clinicProfileService = clinicProfileService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.clinicalDocumentService = clinicalDocumentService;
        this.objectStorageService = objectStorageService;
        this.labNotificationService = labNotificationService;
        this.moduleBusinessEventPublisher = moduleBusinessEventPublisher;
        this.auditEventQueryService = auditEventQueryService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
        this.brandingProperties = brandingProperties;
        this.labReportVerificationProperties = labReportVerificationProperties;
        this.labCatalogueConfigService = labCatalogueConfigService;
        this.laboratoryWorkflowService = laboratoryWorkflowService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<String> listCategories(UUID tenantId) {
        requireTenant(tenantId);
        return labCatalogueConfigService.listActiveCategoryCodes(tenantId);
    }

    @Transactional(readOnly = true)
    public List<LabTestRecord> listTests(UUID tenantId, String search, Boolean activeOnly) {
        requireTenant(tenantId);
        List<LabTestMasterEntity> rows = Boolean.TRUE.equals(activeOnly)
                ? labTestMasterRepository.findByTenantIdAndActiveTrueOrderByTestNameAsc(tenantId)
                : labTestMasterRepository.findByTenantIdOrderByTestNameAsc(tenantId);
        String term = normalizeSearch(search);
        return rows.stream()
                .filter(row -> term == null || matches(row, term))
                .map(this::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<LabTestRecord> findTest(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return labTestMasterRepository.findByTenantIdAndId(tenantId, id).map(this::toRecord);
    }

    @Transactional
    public LabTestRecord createTest(UUID tenantId, LabTestUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateTest(tenantId, command);
        String normalizedTestCode = LabValidationSupport.normalizeTestCode(command.testCode(), "testCode");
        String normalizedTestName = normalize(command.testName());
        ensureUniqueTestCode(tenantId, normalizedTestCode, null);
        ensureUniqueTestName(tenantId, normalizedTestName, null);
        try {
            String normalizedTurnaroundTime = LabValidationSupport.normalizeWholeHours(command.turnaroundTime(), "turnaroundTime");
            BigDecimal normalizedPrice = LabValidationSupport.normalizeMoney(command.price(), "price");
            LabTestMasterEntity entity = LabTestMasterEntity.create(tenantId, normalizedTestCode, normalizedTestName);
            entity.update(
                    normalizedTestCode,
                    normalizedTestName,
                    normalizeCategory(command.category()),
                    normalizeNullable(command.department()),
                    normalizeNullable(command.sampleType()),
                    normalizeNullable(command.unit()),
                    normalizeNullable(command.referenceRange()),
                    normalizedTurnaroundTime,
                    normalizedPrice,
                    command.active()
            );
            LabTestMasterEntity saved = labTestMasterRepository.save(entity);
            saveParameters(tenantId, saved.getId(), command.parameters());
            auditTest(tenantId, saved, "lab_test.created", actorAppUserId, "Created lab test master");
            return toRecord(saved);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateTestException(command, ex);
        }
    }

    @Transactional
    public LabTestRecord updateTest(UUID tenantId, UUID id, LabTestUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validateTest(tenantId, command);
        LabTestMasterEntity entity = labTestMasterRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Lab test not found"));
        String normalizedTestCode = LabValidationSupport.normalizeTestCode(command.testCode(), "testCode");
        String normalizedTestName = normalize(command.testName());
        if (!Objects.equals(entity.getTestCode(), normalizedTestCode)) {
            throw new IllegalArgumentException("Test code cannot be changed after creation");
        }
        ensureUniqueTestCode(tenantId, normalizedTestCode, id);
        ensureUniqueTestName(tenantId, normalizedTestName, id);
        try {
            String normalizedTurnaroundTime = LabValidationSupport.normalizeWholeHours(command.turnaroundTime(), "turnaroundTime");
            BigDecimal normalizedPrice = LabValidationSupport.normalizeMoney(command.price(), "price");
            entity.update(
                    normalizedTestCode,
                    normalizedTestName,
                    normalizeCategory(command.category()),
                    normalizeNullable(command.department()),
                    normalizeNullable(command.sampleType()),
                    normalizeNullable(command.unit()),
                    normalizeNullable(command.referenceRange()),
                    normalizedTurnaroundTime,
                    normalizedPrice,
                    command.active()
            );
            LabTestMasterEntity saved = labTestMasterRepository.save(entity);
            saveParameters(tenantId, saved.getId(), command.parameters());
            auditTest(tenantId, saved, "lab_test.updated", actorAppUserId, "Updated lab test master");
            return toRecord(saved);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateTestException(command, ex);
        }
    }

    @Transactional
    public LabTestRecord deactivateTest(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        LabTestMasterEntity entity = labTestMasterRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Lab test not found"));
        entity.update(
                entity.getTestCode(),
                entity.getTestName(),
                entity.getCategory(),
                entity.getDepartment(),
                entity.getSampleType(),
                entity.getUnit(),
                entity.getReferenceRange(),
                entity.getTurnaroundTime(),
                entity.getPrice(),
                false
        );
        LabTestMasterEntity saved = labTestMasterRepository.save(entity);
        auditTest(tenantId, saved, "lab_test.deactivated", actorAppUserId, "Deactivated lab test master");
        return toRecord(saved);
    }

    @Transactional(readOnly = true)
    public List<LabOrderRecord> listOrders(UUID tenantId, UUID consultationId, UUID patientId, UUID doctorUserId, LabOrderStatus status, String search) {
        requireTenant(tenantId);
        List<LabOrderEntity> orders = labOrderRepository.findByTenantIdOrderByOrderedAtDescCreatedAtDesc(tenantId);
        String term = normalizeSearch(search);
        return mapOrders(tenantId, orders.stream()
                .filter(order -> consultationId == null || consultationId.equals(order.getConsultationId()))
                .filter(order -> patientId == null || patientId.equals(order.getPatientId()))
                .filter(order -> doctorUserId == null || doctorUserId.equals(order.getDoctorUserId()))
                .filter(order -> status == null || status.equals(order.getStatus()))
                .filter(order -> term == null || matches(tenantId, order, term))
                .toList());
    }

    @Transactional(readOnly = true)
    public Optional<LabOrderRecord> findOrder(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return labOrderRepository.findByTenantIdAndId(tenantId, id).map(order -> toRecord(tenantId, order));
    }

    @Transactional(readOnly = true)
    public Optional<LabOrderRecord> findOrderByNumber(UUID tenantId, String orderNumber) {
        requireTenant(tenantId);
        if (!StringUtils.hasText(orderNumber)) {
            return Optional.empty();
        }
        return labOrderRepository.findByTenantIdAndOrderNumber(tenantId, orderNumber.trim()).map(order -> toRecord(tenantId, order));
    }

    @Transactional(readOnly = true)
    public Optional<LabOrderAttachmentRecord> findAttachment(UUID tenantId, UUID orderId, UUID attachmentId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        requireId(attachmentId, "attachmentId");
        return labOrderAttachmentRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtDesc(tenantId, orderId).stream()
                .filter(attachment -> attachment.getId().equals(attachmentId))
                .findFirst()
                .map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public byte[] downloadAttachmentBytes(UUID tenantId, UUID orderId, UUID attachmentId) {
        LabOrderAttachmentRecord attachment = findAttachment(tenantId, orderId, attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order attachment not found"));
        if (!StringUtils.hasText(attachment.storageKey())) {
            throw new IllegalArgumentException("Lab order attachment storage key is missing");
        }
        byte[] bytes = objectStorageService.getObjectBytes(attachment.storageKey());
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Lab order attachment file is empty");
        }
        return bytes;
    }

    @Transactional
    public LabOrderRecord createOrderFromConsultation(UUID tenantId, UUID consultationId, LabOrderCreateCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(consultationId, "consultationId");
        validateOrder(command);
        ConsultationRecord consultation = consultationService.findById(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, consultation.patientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        List<LabTestMasterEntity> tests = loadTests(tenantId, command.testIds());
        ensureNoConsultationDuplicateOrders(tenantId, consultationId, tests);
        TenantUserRecord doctor = consultation.doctorUserId() == null ? null : tenantUserManagementService.list(tenantId).stream()
                .filter(user -> consultation.doctorUserId().equals(user.appUserId()))
                .findFirst()
                .orElse(null);
        return createOrderInternal(
                tenantId,
                patient,
                consultation.doctorUserId(),
                doctor == null ? consultation.doctorName() : doctor.displayName(),
                consultation.id(),
                LabOrderOrigin.CONSULTATION,
                consultation.doctorUserId(),
                null,
                null,
                null,
                null,
                consultation.appointmentId(),
                normalizeNullable(command.notes()),
                tests,
                actorAppUserId,
                "Created consultation lab order and billing"
        );
    }

    @Transactional
    public LabOrderRecord createOrder(UUID tenantId, LabOrderDirectCreateCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateDirectOrder(command);
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, command.patientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        UUID doctorUserId = command.requestedByInternalDoctorId();
        String doctorName = null;
        if (doctorUserId != null) {
            TenantUserRecord doctor = tenantUserManagementService.list(tenantId).stream()
                    .filter(user -> doctorUserId.equals(user.appUserId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
            doctorName = doctor.displayName();
        }
        LabOrderOrigin orderOrigin = command.orderOrigin() == null ? LabOrderOrigin.WALK_IN : command.orderOrigin();
        return createOrderInternal(
                tenantId,
                patient,
                doctorUserId,
                doctorName,
                null,
                orderOrigin,
                doctorUserId,
                normalizeNullable(command.externalDoctorName()),
                normalizeNullable(command.externalDoctorMobile()),
                normalizeNullable(command.externalClinicName()),
                normalizeNullable(command.referralSource()),
                null,
                normalizeNullable(command.notes()),
                loadTests(tenantId, command.testIds()),
                actorAppUserId,
                orderOrigin == LabOrderOrigin.WALK_IN
                        ? "Created direct walk-in lab registration and billing"
                        : "Created direct lab registration and billing"
        );
    }

    @Transactional
    public LabOrderPaymentRecord collectPayment(UUID tenantId, UUID orderId, LabOrderPaymentCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        validatePayment(command);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getBillId() == null) {
            throw new IllegalArgumentException("Lab order has no linked bill");
        }
        if (order.getStatus() == LabOrderStatus.CANCELLED || order.getStatus() == LabOrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Lab order cannot collect payment in its current state");
        }
        PaymentRecord payment = billingService.recordPayment(tenantId, order.getBillId(), new PaymentCommand(
                command.paymentDate(),
                command.paymentDateTime(),
                command.amount(),
                command.paymentMode(),
                command.referenceNumber(),
                command.notes(),
                command.receivedBy()
        ), actorAppUserId);
        order.markStatus(LabOrderStatus.PAID);
        order.markPaymentCollected();
        order.markReadyForCollection();
        LabOrderEntity saved = labOrderRepository.save(order);
        synchronizeAggregateStatus(tenantId, saved, actorAppUserId);
        auditOrder(tenantId, saved, "lab_order.payment_collected", actorAppUserId, "Collected lab order payment");
        return new LabOrderPaymentRecord(toRecord(tenantId, saved), payment);
    }

    public String receivedByDisplayLabel(UUID tenantId, UUID receivedByAppUserId) {
        return billingService.receivedByDisplayLabel(tenantId, receivedByAppUserId);
    }

    @Transactional(readOnly = true)
    public Optional<LabPaymentReceiptRecord> resolvePaymentReceipt(UUID tenantId, UUID billId) {
        requireTenant(tenantId);
        requireId(billId, "billId");
        List<ReceiptRecord> receipts = billingService.listReceipts(tenantId, billId);
        if (receipts.isEmpty()) {
            return Optional.empty();
        }
        ReceiptRecord receipt = receipts.get(0);
        PaymentRecord payment = billingService.listPayments(tenantId, billId).stream()
                .filter(row -> receipt.id().equals(row.receiptId()))
                .findFirst()
                .orElse(null);
        BillRecord bill = billingService.findById(tenantId, billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        OffsetDateTime collectedAt = payment != null && payment.paymentDateTime() != null
                ? payment.paymentDateTime()
                : receipt.createdAt();
        String printUrl = "/api/receipts/" + receipt.id() + "/pdf";
        return Optional.of(new LabPaymentReceiptRecord(
                receipt.id(),
                receipt.receiptNumber(),
                receipt.billId(),
                bill.billNumber(),
                receipt.amount(),
                payment == null ? null : payment.paymentMode(),
                payment == null ? null : payment.referenceNumber(),
                payment == null ? null : receivedByDisplayLabel(tenantId, payment.receivedBy()),
                collectedAt,
                printUrl,
                printUrl
        ));
    }

    @Transactional
    public LabOrderRecord collectSample(UUID tenantId, UUID orderId, LabOrderSampleCollectionCommand command, UUID actorAppUserId) {
        LabOrderRecord order = findOrder(tenantId, orderId).orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        String specimenType = firstText(command.sampleType(), order.sampleType(), fallbackSampleType(order));
        List<LabSampleRecord> samples = collectSamples(tenantId, orderId, List.of(new LabSampleCollectionCommand(
                null,
                specimenType,
                null,
                command.collectedAt(),
                command.notes()
        )), actorAppUserId);
        if (samples.isEmpty()) {
            throw new IllegalStateException("No lab samples were collected");
        }
        return findOrder(tenantId, orderId).orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
    }

    @Transactional(readOnly = true)
    public List<LabSampleRecord> listSamples(UUID tenantId, UUID orderId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        Map<UUID, SampleLinkSummary> sampleLinkSummaries = buildSampleLinkSummaries(tenantId, orderId);
        return labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(tenantId, orderId).stream()
                .map(sample -> toRecord(tenantId, sample, sampleLinkSummaries.get(sample.getId())))
                .toList();
    }

    @Transactional
    public List<LabSampleRecord> collectSamples(UUID tenantId, UUID orderId, List<LabSampleCollectionCommand> commands, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("At least one sample is required");
        }
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() != LabOrderStatus.READY_FOR_COLLECTION) {
            throw new IllegalArgumentException("Lab order is not ready for sample collection");
        }
        Map<UUID, LabOrderItemEntity> orderItems = labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, orderId).stream()
                .collect(Collectors.toMap(LabOrderItemEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<LabOrderSampleEntity> entities = new ArrayList<>();
        List<SampleCollectionGroup> groupedCommands = new ArrayList<>();
        HashSet<String> reservedAccessions = new HashSet<>();
        OffsetDateTime firstCollectedAt = null;
        String firstSpecimenType = null;
        String firstNotes = null;
        UUID firstCollectedBy = null;
        for (LabSampleCollectionCommand command : commands) {
            validateSampleCollection(command);
            LabOrderItemEntity orderItem = command.labOrderItemId() == null ? null : orderItems.get(command.labOrderItemId());
            if (command.labOrderItemId() != null && orderItem == null) {
                throw new IllegalArgumentException("Unknown lab order item for sample collection");
            }
            String specimenType = normalize(command.specimenType());
            OffsetDateTime collectedAt = command.collectedAt() == null ? OffsetDateTime.now() : command.collectedAt();
            SampleCollectionGroup group = groupedCommands.stream()
                    .filter(existing -> existing.matches(specimenType, normalizeNullable(command.containerType()), collectedAt, normalizeNullable(command.notes())))
                    .findFirst()
                    .orElseGet(() -> {
                        SampleCollectionGroup created = new SampleCollectionGroup(
                                specimenType,
                                normalizeNullable(command.containerType()),
                                collectedAt,
                                normalizeNullable(command.notes())
                        );
                        groupedCommands.add(created);
                        return created;
                    });
            if (command.labOrderItemId() == null) {
                group.assignToAllOrderItems = true;
            } else {
                group.linkedOrderItemIds.add(command.labOrderItemId());
            }
            if (firstCollectedAt == null || collectedAt.isBefore(firstCollectedAt)) {
                firstCollectedAt = collectedAt;
                firstSpecimenType = specimenType;
                firstNotes = normalizeNullable(command.notes());
                firstCollectedBy = actorAppUserId;
            }
        }
        Map<UUID, SampleLinkSummary> sampleLinkSummaries = new LinkedHashMap<>();
        for (SampleCollectionGroup group : groupedCommands) {
            List<UUID> linkedOrderItemIds = group.assignToAllOrderItems
                    ? new ArrayList<>(orderItems.keySet())
                    : new ArrayList<>(group.linkedOrderItemIds);
            if (linkedOrderItemIds.isEmpty()) {
                linkedOrderItemIds = new ArrayList<>(orderItems.keySet());
            }
            UUID primaryOrderItemId = linkedOrderItemIds.size() == 1 ? linkedOrderItemIds.getFirst() : null;
            LabOrderSampleEntity savedSample = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                String accessionNumber = generateAccessionNumber(tenantId, reservedAccessions);
                String barcodeValue = generateBarcodeValue(accessionNumber);
                LabOrderSampleEntity entity = LabOrderSampleEntity.create(
                        tenantId,
                        orderId,
                        primaryOrderItemId,
                        accessionNumber,
                        barcodeValue,
                        group.specimenType,
                        group.containerType,
                        group.collectedAt,
                        actorAppUserId,
                        group.notes,
                        actorAppUserId
                );
                try {
                    savedSample = requiresNewTransactionTemplate.execute(status -> labOrderSampleRepository.save(entity));
                    break;
                } catch (DataIntegrityViolationException ex) {
                    if (!isAccessionCollision(ex, accessionNumber)) {
                        throw ex;
                    }
                }
            }
            if (savedSample == null) {
                throw new IllegalStateException("Unable to generate unique lab accession number");
            }
            reservedAccessions.add(savedSample.getAccessionNumber());
            entities.add(savedSample);
            laboratoryWorkflowService.linkSpecimenToTests(
                    tenantId,
                    orderId,
                    savedSample.getId(),
                    linkedOrderItemIds,
                    savedSample.getAccessionNumber(),
                    savedSample.getBarcodeValue(),
                    savedSample.getSpecimenType(),
                    savedSample.getContainerType(),
                    savedSample.getStatus() == null ? null : savedSample.getStatus().name(),
                    savedSample.getCollectedAt(),
                    savedSample.getReceivedAt(),
                    actorAppUserId
            );
            List<String> linkedTestNames = linkedOrderItemIds.stream()
                    .map(orderItems::get)
                    .filter(Objects::nonNull)
                    .map(LabOrderItemEntity::getTestName)
                    .toList();
            sampleLinkSummaries.put(savedSample.getId(), new SampleLinkSummary(new ArrayList<>(linkedOrderItemIds), new ArrayList<>(linkedTestNames)));
        }
        order.markSampleCollected(
                firstCollectedAt,
                firstCollectedBy == null ? actorAppUserId : firstCollectedBy,
                resolveUserDisplayName(tenantId, firstCollectedBy == null ? actorAppUserId : firstCollectedBy).orElse(null),
                firstSpecimenType,
                firstNotes
        );
        LabOrderEntity saved = labOrderRepository.save(order);
        synchronizeAggregateStatus(tenantId, saved, actorAppUserId);
        auditOrder(tenantId, saved, "lab_order.sample_collected", actorAppUserId, "Collected lab sample");
        for (LabOrderSampleEntity sample : entities) {
            auditSample(tenantId, sample, "lab_sample.accession_generated", actorAppUserId, "Generated accession number");
            auditSample(tenantId, sample, "lab_sample.collected", actorAppUserId, "Collected lab sample");
        }
        labNotificationService.notifySampleCollected(
                tenantId,
                saved.getPatientId(),
                saved.getId(),
                saved.getOrderNumber(),
                saved.getPatientName(),
                saved.getDoctorName(),
                actorAppUserId
        );
        return entities.stream()
                .map(sample -> toRecord(tenantId, sample, sampleLinkSummaries.get(sample.getId())))
                .toList();
    }

    @Transactional
    public LabSampleRecord receiveSample(UUID tenantId, UUID sampleId, LabSampleReceiveCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(sampleId, "sampleId");
        LabOrderSampleEntity sample = labOrderSampleRepository.findByTenantIdAndId(tenantId, sampleId)
                .orElseThrow(() -> new IllegalArgumentException("Lab sample not found"));
        if (sample.getStatus() != LabSampleStatus.COLLECTED) {
            throw new IllegalArgumentException("Lab sample is not ready to receive");
        }
        UUID receivedBy = command == null || command.receivedBy() == null ? actorAppUserId : command.receivedBy();
        sample.markReceived(command == null ? null : command.receivedAt(), receivedBy, actorAppUserId);
        LabOrderSampleEntity saved = labOrderSampleRepository.save(sample);
        auditSample(tenantId, saved, "lab_sample.received", actorAppUserId, "Received lab sample");
        return toRecord(tenantId, saved, buildSampleLinkSummaries(tenantId, saved.getLabOrderId()).get(saved.getId()));
    }

    @Transactional
    public LabSampleRecord rejectSample(UUID tenantId, UUID sampleId, LabSampleRejectCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(sampleId, "sampleId");
        validateSampleRejection(command);
        LabOrderSampleEntity sample = labOrderSampleRepository.findByTenantIdAndId(tenantId, sampleId)
                .orElseThrow(() -> new IllegalArgumentException("Lab sample not found"));
        if (sample.getStatus() != LabSampleStatus.COLLECTED && sample.getStatus() != LabSampleStatus.RECEIVED) {
            throw new IllegalArgumentException("Lab sample cannot be rejected in its current state");
        }
        Map<UUID, SampleLinkSummary> sampleLinkSummaries = buildSampleLinkSummaries(tenantId, sample.getLabOrderId());
        SampleLinkSummary summary = sampleLinkSummaries.getOrDefault(sample.getId(), SampleLinkSummary.empty());
        ensureNoResultsEnteredForSample(tenantId, sample, summary);
        sample.markRejected(normalize(command.rejectionReason()), command.recollectionRequired(), normalizeNullable(command.notes()), actorAppUserId);
        LabOrderSampleEntity savedSample = labOrderSampleRepository.save(sample);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, sample.getLabOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        List<UUID> affectedItemIds = summary.linkedLabOrderItemIds().isEmpty()
                ? (sample.getLabOrderItemId() == null
                        ? labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, order.getId()).stream().map(LabOrderItemEntity::getId).toList()
                        : List.of(sample.getLabOrderItemId()))
                : summary.linkedLabOrderItemIds();
        laboratoryWorkflowService.rejectSpecimenForTests(tenantId, order.getId(), savedSample.getId(), affectedItemIds, actorAppUserId);
        if (savedSample.getStatus() == LabSampleStatus.RECOLLECTION_REQUIRED) {
            boolean allNeedRecollection = labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(tenantId, order.getId()).stream()
                    .allMatch(existing -> existing.getStatus() == LabSampleStatus.RECOLLECTION_REQUIRED || existing.getStatus() == LabSampleStatus.REJECTED);
            if (allNeedRecollection) {
                order.markReadyForCollection();
                labOrderRepository.save(order);
            }
            auditSample(tenantId, savedSample, "lab_sample.recollection_required", actorAppUserId, "Marked sample for recollection");
        } else {
            auditSample(tenantId, savedSample, "lab_sample.rejected", actorAppUserId, "Rejected lab sample");
        }
        return toRecord(tenantId, savedSample, sampleLinkSummaries.get(savedSample.getId()));
    }

    @Transactional
    public LabOrderRecord enterResults(UUID tenantId, UUID orderId, LabOrderResultEntryCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        validateResults(command);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() == LabOrderStatus.DOCTOR_REVIEWED
                || order.getStatus() == LabOrderStatus.REPORT_READY
                || order.getStatus() == LabOrderStatus.REPORT_GENERATED
                || order.getStatus() == LabOrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Results are verified and cannot be edited. Use amendment workflow in a future release.");
        }
        List<LabOrderSampleEntity> orderSamples = labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(tenantId, orderId);
        boolean hasReceivedSample = orderSamples.stream().anyMatch(sample -> sample.getStatus() == LabSampleStatus.RECEIVED);
        if (!hasReceivedSample) {
            throw new IllegalArgumentException("A received sample is required before result entry");
        }
        Map<UUID, LabOrderSampleEntity> samplesById = orderSamples.stream()
                .collect(Collectors.toMap(LabOrderSampleEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<UUID, LabOrderItemEntity> orderItems = labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, orderId).stream()
                .collect(Collectors.toMap(LabOrderItemEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, SampleLinkSummary> sampleLinkSummaries = buildSampleLinkSummaries(tenantId, orderId);
        List<LaboratoryOrderedTestView> lifecycleViews = laboratoryWorkflowService.listOrderTests(tenantId, orderId);
        Map<UUID, LaboratoryOrderedTestView> lifecycleByItemId = lifecycleViews.stream()
                .filter(view -> view.labOrderItemId() != null)
                .collect(Collectors.toMap(
                        LaboratoryOrderedTestView::labOrderItemId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        UUID scopedSampleId = command.labOrderSampleId();
        if (orderSamples.size() > 1 && scopedSampleId == null) {
            throw new IllegalArgumentException("A specimen accession must be selected for multi-specimen result entry");
        }
        List<UUID> selectedTestIds = command.orderedTestIds() == null ? List.of() : command.orderedTestIds().stream().filter(Objects::nonNull).distinct().toList();
        List<UUID> editableTestIds;
        if (scopedSampleId != null) {
            LabOrderSampleEntity scopedSample = samplesById.get(scopedSampleId);
            if (scopedSample == null) {
                throw new IllegalArgumentException("Selected sample is not part of this order");
            }
            editableTestIds = selectEditableTestsForResultEntry(orderItems.values(), lifecycleViews, scopedSampleId, scopedSample, sampleLinkSummaries);
            if (editableTestIds.isEmpty()) {
                throw new IllegalArgumentException("Selected sample has no editable tests for result entry");
            }
            Set<UUID> expectedTestIds = new LinkedHashSet<>(editableTestIds);
            Set<UUID> payloadTestIds = command.items().stream()
                    .map(LabOrderResultItemCommand::labOrderItemId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!payloadTestIds.equals(expectedTestIds)) {
                throw new IllegalArgumentException("Submitted results do not match the selected accession");
            }
            if (!selectedTestIds.isEmpty() && !new LinkedHashSet<>(selectedTestIds).equals(expectedTestIds)) {
                throw new IllegalArgumentException("Submitted results do not match the selected accession");
            }
        } else {
            editableTestIds = selectedTestIds.isEmpty()
                    ? selectEditableTestsForResultEntry(orderItems.values(), lifecycleViews)
                    : validateSelectedTestsForResultEntry(selectedTestIds, lifecycleByItemId);
        }
        if (editableTestIds.isEmpty()) {
            throw new IllegalArgumentException("Lab order has no editable tests for result entry");
        }
        LabOrderStatus previousStatus = order.getStatus();
        if (orderItems.isEmpty()) {
            throw new IllegalArgumentException("Lab order has no ordered tests");
        }
        Map<UUID, LabOrderResultItemCommand> itemCommandById = command.items().stream()
                .filter(item -> item != null && item.labOrderItemId() != null)
                .collect(Collectors.toMap(
                        LabOrderResultItemCommand::labOrderItemId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (UUID selectedTestId : editableTestIds) {
            if (!itemCommandById.containsKey(selectedTestId)) {
                throw new IllegalArgumentException("Missing result payload for selected test");
            }
        }
        for (UUID selectedTestId : editableTestIds) {
            ensureUsableSampleForOrderItem(orderSamples, sampleLinkSummaries, selectedTestId);
        }
        Map<UUID, List<LabTestParameterEntity>> parametersByTestId = orderItems.values().stream()
                .filter(item -> item.getLabTestId() != null)
                .collect(Collectors.toMap(
                        LabOrderItemEntity::getLabTestId,
                        item -> labTestParameterRepository.findByTenantIdAndLabTestIdOrderBySortOrderAsc(tenantId, item.getLabTestId()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<LabOrderResultEntity> currentResults = labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, orderId);
        Map<UUID, List<LabOrderResultEntity>> currentResultsByItem = currentResults.stream()
                .filter(result -> result.getLabOrderItemId() != null)
                .collect(Collectors.groupingBy(LabOrderResultEntity::getLabOrderItemId, LinkedHashMap::new, Collectors.toList()));
        labOrderResultRepository.deleteByTenantIdAndLabOrderItemIdIn(tenantId, editableTestIds);
        List<LabOrderResultEntity> persistedResults = new ArrayList<>();
        List<String> criticalLabels = new ArrayList<>();
        int sortOrder = currentResults.stream()
                .map(LabOrderResultEntity::getSortOrder)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
        for (UUID selectedTestId : editableTestIds) {
            LabOrderItemEntity orderItem = orderItems.get(selectedTestId);
            if (orderItem == null) {
                throw new IllegalArgumentException("Unknown lab order item in results payload");
            }
            LabOrderResultItemCommand itemCommand = itemCommandById.get(selectedTestId);
            List<LabOrderResultEntity> existingRows = currentResultsByItem.getOrDefault(selectedTestId, List.of());
            int rowSortOrder = sortOrder;
            List<LabOrderResultComponentCommand> components = itemCommand.componentResults() == null ? List.of() : itemCommand.componentResults();
            if (!components.isEmpty()) {
                for (LabOrderResultComponentCommand component : components) {
                    LabTestParameterEntity parameter = findParameter(parametersByTestId.get(orderItem.getLabTestId()), component.parameterName(), component.componentName());
                    ResultFlagResolution resolution = resolveResultFlag(component.resultValue(), parameter == null ? itemCommand.referenceRange() : parameter.getNormalRange(), parameter == null ? null : parameter.getCriticalRange());
                    ensureNumericResultWhenRangesPresent(component.resultValue(), parameter == null ? itemCommand.referenceRange() : parameter.getNormalRange(), parameter == null ? null : parameter.getCriticalRange());
                    if (resolution.critical()) {
                        criticalLabels.add(orderItem.getTestName() + " / " + firstText(component.parameterName(), component.componentName()));
                    }
                    persistedResults.add(LabOrderResultEntity.create(
                            tenantId,
                            orderId,
                            orderItem.getId(),
                            orderItem.getTestCode(),
                            orderItem.getTestName(),
                            firstText(component.parameterName(), component.componentName()),
                            normalizeNullable(component.componentName()),
                            normalizeNullable(component.resultValue()),
                            normalizeNullable(component.unit()),
                            normalizeNullable(component.referenceRange()),
                            rowSortOrder++,
                            resolution.flag().name(),
                            resolution.critical()
                    ));
                }
            } else {
                LabTestParameterEntity parameter = firstParameter(parametersByTestId.get(orderItem.getLabTestId()));
                ResultFlagResolution resolution = resolveResultFlag(itemCommand.resultValue(), parameter == null ? itemCommand.referenceRange() : parameter.getNormalRange(), parameter == null ? null : parameter.getCriticalRange());
                ensureNumericResultWhenRangesPresent(itemCommand.resultValue(), parameter == null ? itemCommand.referenceRange() : parameter.getNormalRange(), parameter == null ? null : parameter.getCriticalRange());
                if (resolution.critical()) {
                    criticalLabels.add(orderItem.getTestName());
                }
                persistedResults.add(LabOrderResultEntity.create(
                        tenantId,
                        orderId,
                        orderItem.getId(),
                        orderItem.getTestCode(),
                        orderItem.getTestName(),
                        parameter == null ? null : parameter.getParameterName(),
                        null,
                        normalizeNullable(itemCommand.resultValue()),
                        normalizeNullable(itemCommand.unit()),
                        normalizeNullable(itemCommand.referenceRange()),
                        rowSortOrder,
                        resolution.flag().name(),
                        resolution.critical()
                ));
                rowSortOrder++;
            }
            sortOrder = rowSortOrder;
        }
        labOrderResultRepository.saveAll(persistedResults);
        laboratoryWorkflowService.initializeOrderTests(tenantId, orderId, orderItems.keySet(), actorAppUserId);
        Map<UUID, List<LabOrderResultEntity>> resultsByItem = persistedResults.stream()
                .collect(Collectors.groupingBy(LabOrderResultEntity::getLabOrderItemId, LinkedHashMap::new, Collectors.toList()));
        List<LaboratoryResultRevisionCommand> revisionCommands = resultsByItem.entrySet().stream()
                .map(entry -> new LaboratoryResultRevisionCommand(
                        entry.getKey(),
                        entry.getValue().isEmpty() ? null : entry.getValue().getFirst().getId(),
                        0,
                        serializeResultSnapshot(entry.getValue()),
                        normalizeNullable(command.comments()),
                        "SUBMITTED",
                        OffsetDateTime.now(ZoneOffset.UTC),
                        actorAppUserId
                ))
                .toList();
        laboratoryWorkflowService.recordResultRevisions(tenantId, orderId, revisionCommands, actorAppUserId);
        order.markProcessingStarted();
        order.markResultsEntered(normalizeNullable(command.comments()));
        LabOrderEntity saved = labOrderRepository.save(order);
        synchronizeAggregateStatus(tenantId, saved, actorAppUserId);
        String resultAuditAction = previousStatus == LabOrderStatus.RESULT_ENTERED ? "lab_order.results_updated_before_verification" : "lab_order.results_entered";
        auditOrder(tenantId, saved, resultAuditAction, actorAppUserId, "Entered lab results");
        if (!criticalLabels.isEmpty()) {
            auditOrder(tenantId, saved, "lab_order.critical_results_entered", actorAppUserId, "Entered critical lab results");
            labNotificationService.notifyCriticalResult(
                    tenantId,
                    null,
                    saved.getId(),
                    saved.getOrderNumber(),
                    saved.getPatientName(),
                    saved.getDoctorName(),
                    criticalLabels,
                    actorAppUserId
            );
        }
        return toRecord(tenantId, saved);
    }

    @Transactional
    public LabOrderRecord verifyResults(UUID tenantId, UUID orderId, LabOrderVerificationCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        validateVerification(command);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        List<LabOrderResultEntity> results = labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, orderId);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Lab order has no result values to verify");
        }
        List<LabOrderSampleEntity> orderSamples = labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(tenantId, orderId);
        boolean hasUsableSample = orderSamples.stream().anyMatch(sample -> sample.getStatus() == LabSampleStatus.COLLECTED || sample.getStatus() == LabSampleStatus.RECEIVED);
        if (!hasUsableSample) {
            throw new IllegalArgumentException("Lab order requires a valid sample before verification");
        }
        String decision = normalizeReviewDecision(command.decision());
        List<LaboratoryOrderedTestView> lifecycleViews = laboratoryWorkflowService.listOrderTests(tenantId, orderId);
        Map<UUID, LaboratoryOrderedTestView> lifecycleByItemId = lifecycleViews.stream()
                .filter(view -> view.labOrderItemId() != null)
                .collect(Collectors.toMap(
                        LaboratoryOrderedTestView::labOrderItemId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<UUID> selectedTestIds = command.orderedTestIds() == null ? List.of() : command.orderedTestIds().stream().filter(Objects::nonNull).distinct().toList();
        List<UUID> eligibleTestIds = selectedTestIds.isEmpty()
                ? selectEligibleTestsForVerification(tenantId, orderId, lifecycleViews)
                : validateSelectedTestsForVerification(selectedTestIds, lifecycleByItemId);
        if (eligibleTestIds.isEmpty()) {
            throw new IllegalArgumentException("Lab order has no eligible tests for verification");
        }
        boolean recollectionRequired = !"SEND_BACK".equals(decision) && Boolean.TRUE.equals(command.recollectionRequired());
        laboratoryWorkflowService.verifyTests(
                tenantId,
                orderId,
                eligibleTestIds,
                decision,
                normalizeNullable(command.reason()),
                normalizeNullable(command.comments()),
                recollectionRequired,
                actorAppUserId
        );
        if ("SEND_BACK".equals(decision)) {
            order.markLabVerificationSentBack(actorAppUserId, decision, normalizeNullable(command.reason()), normalizeNullable(command.comments()));
        } else {
            order.markLabVerified(actorAppUserId, decision, normalizeNullable(command.reason()), normalizeNullable(command.comments()));
        }
        LabOrderEntity saved = labOrderRepository.save(order);
        synchronizeAggregateStatus(tenantId, saved, actorAppUserId);
        auditOrder(
                tenantId,
                saved,
                "SEND_BACK".equals(decision) ? "lab_order.verification_sent_back" : "lab_order.verification_approved",
                actorAppUserId,
                "SEND_BACK".equals(decision) ? "Sent lab results back for correction" : "Verified lab results"
        );
        return toRecord(tenantId, saved);
    }

    @Transactional
    public LabOrderRecord publishReport(UUID tenantId, UUID orderId, LabOrderPublishReportCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        List<String> requestedChannels = normalizeDeliveryChannels(command == null ? null : command.deliveryChannels());
        List<UUID> selectedTestIds = command == null || command.orderedTestIds() == null
                ? List.of()
                : command.orderedTestIds().stream().filter(Objects::nonNull).distinct().toList();
        List<LaboratoryOrderedTestView> lifecycleViews = laboratoryWorkflowService.listOrderTests(tenantId, orderId);
        Map<UUID, LaboratoryOrderedTestView> lifecycleByItemId = lifecycleViews.stream()
                .filter(view -> view.labOrderItemId() != null)
                .collect(Collectors.toMap(
                        LaboratoryOrderedTestView::labOrderItemId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        // The request IDs describe report content. Lifecycle publication is derived
        // separately so already-published tests can be included without republishing.
        List<UUID> reportContentTestIds = selectedTestIds.isEmpty()
                ? selectPublishableTestsForReportPublishing(lifecycleViews)
                : validateSelectedTestsForReportPublishing(selectedTestIds, lifecycleByItemId);
        if (reportContentTestIds.isEmpty()) {
            throw new IllegalArgumentException("Lab order has no eligible tests for report publishing");
        }
        List<UUID> lifecycleTransitionTestIds = reportContentTestIds.stream()
                .filter(testId -> {
                    LaboratoryOrderedTestView view = lifecycleByItemId.get(testId);
                    return view != null && "VERIFIED".equalsIgnoreCase(view.state());
                })
                .toList();
        String requestedReportMode = command == null ? null : command.reportMode();
        String effectiveReportMode = StringUtils.hasText(requestedReportMode)
                ? requestedReportMode.trim().toUpperCase(Locale.ROOT)
                : deriveReportModeForPublication(lifecycleViews, reportContentTestIds);
        if (StringUtils.hasText(requestedReportMode)) {
            validateReportModeSelection(effectiveReportMode, reportContentTestIds);
        }
        String effectiveReportType = deriveReportTypeForPublication(lifecycleViews, reportContentTestIds);
        // Re-selecting the exact current final artifact is a read-only report
        // lookup, not a publication/version transition.
        if (hasIdenticalCurrentReport(tenantId, orderId, effectiveReportMode, effectiveReportType, reportContentTestIds)) {
            return toRecord(tenantId, order);
        }
        order.ensureReportVerificationToken();
        LabOrderResultPdf pdf = buildReportPdf(tenantId, order, reportContentTestIds, effectiveReportMode, effectiveReportType);
        String deliveryChannelsJson = serializeJson(requestedChannels);
        boolean makeCurrent = shouldNewArtifactBecomeCurrent(
                tenantId,
                orderId,
                effectiveReportMode,
                effectiveReportType
        );
        if (makeCurrent) {
            laboratoryWorkflowService.publishTests(
                    tenantId,
                    orderId,
                    reportContentTestIds,
                    order.getReportVerificationToken(),
                    pdf.filename(),
                    "lab/report/" + pdf.filename(),
                    requestedChannels,
                    effectiveReportMode,
                    effectiveReportType,
                    normalizeNullable(command == null ? null : command.publishNotes()),
                    actorAppUserId
            );
        } else {
            laboratoryWorkflowService.generateReportArtifact(
                    tenantId,
                    orderId,
                    reportContentTestIds,
                    order.getReportVerificationToken(),
                    pdf.filename(),
                    "lab/report/" + pdf.filename(),
                    requestedChannels,
                    effectiveReportMode,
                    effectiveReportType,
                    LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_HISTORICAL,
                    normalizeNullable(command == null ? null : command.publishNotes()),
                    actorAppUserId
            );
        }
        if (!makeCurrent) {
            return toRecord(tenantId, order);
        }
        order.markReportPublished(
                actorAppUserId,
                pdf.filename(),
                "PUBLISHED",
                deliveryChannelsJson,
                normalizeNullable(command == null ? null : command.publishNotes())
        );
        LabOrderEntity saved = labOrderRepository.save(order);
        synchronizeAggregateStatus(tenantId, saved, actorAppUserId);
        auditOrder(tenantId, saved, "lab_order.report_published", actorAppUserId, "Published lab report");
        recordDeliveryChannelAudits(tenantId, saved, requestedChannels, actorAppUserId);

        clinicalDocumentService.publishLabReport(new ClinicalDocumentUploadCommand(
                tenantId,
                saved.getPatientId(),
                saved.getConsultationId(),
                actorAppUserId,
                ClinicalDocumentType.LAB_REPORT,
                "Lab Report",
                OffsetDateTime.now(ZoneOffset.UTC).toLocalDate(),
                "LABORATORY",
                "LABORATORY",
                saved.getId().toString(),
                "PATIENT_VISIBLE",
                pdf.filename(),
                "application/pdf",
                pdf.content(),
                normalizeNullable(command == null ? null : command.publishNotes())
        ));

        if (requestedChannels.contains("DOCTOR_NOTIFICATION")) {
            notifyRequestingDoctor(tenantId, saved, actorAppUserId);
        }
        var clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        String clinicDisplayName = clinic == null ? null : firstText(clinic.displayName(), clinic.clinicName(), "Clinic");
        String timezone = clinicTimeZoneResolver.resolve(tenantId).getId();
        OffsetDateTime publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        moduleBusinessEventPublisher.publish(LabReportPublishedEvent.published(
                tenantId,
                saved.getId(),
                saved.getPatientId(),
                saved.getConsultationId(),
                saved.getOrderNumber(),
                clinicDisplayName,
                timezone,
                publishedAt,
                pdf.filename(),
                saved.getReportDeliveryStatus(),
                actorAppUserId
        ));
        return toRecord(tenantId, saved);
    }

    @Transactional
    public LabOrderRecord recordReportDeliveryAction(UUID tenantId, UUID orderId, String action, String channel, String notes, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        if (!StringUtils.hasText(action)) {
            throw new IllegalArgumentException("action is required");
        }
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        String normalizedAction = normalizeNullable(action);
        String message = buildReportDeliveryMessage(normalizedAction, channel, notes);
        auditOrder(tenantId, order, normalizedAction, actorAppUserId, message);
        return toRecord(tenantId, order);
    }

    @Transactional(readOnly = true)
    public LabReportVerificationResponse verifyPublishedReport(String verificationToken) {
        if (!StringUtils.hasText(verificationToken)) {
            throw new IllegalArgumentException("verificationToken is required");
        }
        String normalizedToken = verificationToken.trim();
        Optional<LaboratoryReportArtifactView> artifactLookup = laboratoryWorkflowService.findReportArtifactByVerificationToken(normalizedToken);
        LabOrderEntity order;
        LaboratoryReportArtifactView artifact;
        boolean legacyFallback = false;
        if (artifactLookup.isPresent()) {
            artifact = artifactLookup.get();
            order = labOrderRepository.findByTenantIdAndId(artifact.tenantId(), artifact.labOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        } else {
            order = labOrderRepository.findByReportVerificationToken(normalizedToken)
                    .orElseThrow(() -> new IllegalArgumentException("Lab report verification record not found"));
            artifact = legacyReportArtifactView(order, normalizedToken);
            legacyFallback = true;
        }
        if (order.getReportPublishedAt() == null) {
            throw new IllegalArgumentException("Lab report is not published");
        }
        UUID tenantId = order.getTenantId();
        String clinicName = clinicProfileService.findByTenantId(tenantId)
                .map(clinic -> firstText(clinic.displayName(), clinic.clinicName(), "Clinic"))
                .filter(StringUtils::hasText)
                .orElse("Clinic");
        return new LabReportVerificationResponse(
                true,
                artifact.verificationToken(),
                order.getOrderNumber(),
                clinicName,
                legacyFallback ? businessReportStatusLabel(order) : businessReportStatusLabel(order, artifact),
                LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_SUPERSEDED.equalsIgnoreCase(artifact.reportStatus()) ? "SUPERSEDED" : "VERIFIED",
                artifact.publishedAt(),
                artifact.filename(),
                artifact.artifactNumber(),
                artifact.reportMode(),
                artifact.reportType(),
                artifact.reportStatus(),
                artifact.verificationUrl(),
                artifact.selectedItemIds()
        );
    }

    @Transactional
    public LabOrderResultPdf generateReportPdf(UUID tenantId, UUID orderId, UUID actorAppUserId) {
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        LabOrderStatus currentStatus = order.getStatus();
        if (currentStatus != LabOrderStatus.DOCTOR_REVIEWED
                && currentStatus != LabOrderStatus.REPORT_READY
                && currentStatus != LabOrderStatus.PARTIALLY_READY
                && currentStatus != LabOrderStatus.PARTIALLY_PUBLISHED
                && currentStatus != LabOrderStatus.REPORT_GENERATED
                && currentStatus != LabOrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Lab order results are not approved for report generation");
        }
        LabOrderResultPdf pdf = renderReportPdf(tenantId, orderId, latestPublicationSelection(tenantId, orderId));
        if (currentStatus == LabOrderStatus.DOCTOR_REVIEWED) {
            order.markReportGenerated(actorAppUserId, pdf.filename());
            LabOrderEntity saved = labOrderRepository.save(order);
            auditOrder(tenantId, saved, "lab_order.report_generated", actorAppUserId, "Generated lab report PDF");
        }
        return pdf;
    }

    @Transactional(readOnly = true)
    public LabOrderResultPdf renderReportPdf(UUID tenantId, UUID orderId) {
        return renderReportPdf(tenantId, orderId, List.of(), null, null);
    }

    @Transactional(readOnly = true)
    public LabOrderResultPdf renderReportPdf(UUID tenantId, UUID orderId, List<UUID> selectedItemIds) {
        return renderReportPdf(tenantId, orderId, selectedItemIds, null, null);
    }

    @Transactional(readOnly = true)
    LabOrderResultPdf renderReportPdf(UUID tenantId, UUID orderId, List<UUID> selectedItemIds, String reportMode, String reportType) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() != LabOrderStatus.DOCTOR_REVIEWED
                && order.getStatus() != LabOrderStatus.REPORT_READY
                && order.getStatus() != LabOrderStatus.PARTIALLY_READY
                && order.getStatus() != LabOrderStatus.PARTIALLY_PUBLISHED
                && order.getStatus() != LabOrderStatus.REPORT_GENERATED
                && order.getStatus() != LabOrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Lab order results are not ready for report generation");
        }
        return buildReportPdf(tenantId, order, selectedItemIds, reportMode, reportType);
    }

    @Transactional
    public LabOrderRecord reviewReport(UUID tenantId, UUID orderId, LabOrderDoctorReviewCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        validateReview(command);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() != LabOrderStatus.RESULT_ENTERED) {
            throw new IllegalArgumentException("Lab order report is not ready for review");
        }
        String reviewer = resolveUserDisplayName(tenantId, actorAppUserId).orElse(null);
        String decision = normalizeReviewDecision(command.decision());
        if ("SEND_BACK".equals(decision)) {
            order.markResultReturned(actorAppUserId, reviewer, decision, normalizeNullable(command.reason()), normalizeNullable(command.doctorComments()));
        } else {
            order.markDoctorReviewed(actorAppUserId, reviewer, decision, normalizeNullable(command.reason()), normalizeNullable(command.doctorComments()));
        }
        LabOrderEntity saved = labOrderRepository.save(order);
        auditOrder(tenantId, saved, "lab_order.doctor_reviewed", actorAppUserId, "Doctor reviewed lab report");
        labNotificationService.notifyDoctorReviewed(
                tenantId,
                saved.getPatientId(),
                saved.getId(),
                saved.getOrderNumber(),
                saved.getPatientName(),
                saved.getDoctorName(),
                saved.getDoctorComments(),
                actorAppUserId
        );
        return toRecord(tenantId, saved);
    }

    @Transactional
    public LabOrderRecord markDelivered(UUID tenantId, UUID orderId, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() == LabOrderStatus.DELIVERED) {
            return toRecord(tenantId, order);
        }
        if (order.getStatus() != LabOrderStatus.DOCTOR_REVIEWED
                && order.getStatus() != LabOrderStatus.REPORT_GENERATED
                && order.getStatus() != LabOrderStatus.PARTIALLY_PUBLISHED
                && order.getStatus() != LabOrderStatus.REPORT_READY) {
            return toRecord(tenantId, order);
        }
        order.markDelivered(actorAppUserId);
        LabOrderEntity saved = labOrderRepository.save(order);
        auditOrder(tenantId, saved, "lab_order.delivered", actorAppUserId, "Lab report delivered to patient");
        return toRecord(tenantId, saved);
    }

    private List<LabOrderRecord> mapOrders(UUID tenantId, List<LabOrderEntity> orders) {
        return orders.stream().map(order -> toRecord(tenantId, order)).toList();
    }

    private LabOrderRecord toRecord(UUID tenantId, LabOrderEntity order) {
        List<LabOrderAttachmentRecord> attachments = labOrderAttachmentRepository.findByTenantIdAndLabOrderIdOrderByCreatedAtDesc(tenantId, order.getId()).stream()
                .map(this::toRecord)
                .toList();
        List<LabOrderItemEntity> itemEntities = labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, order.getId());
        Map<UUID, LabOrderItemEntity> itemById = itemEntities.stream().collect(Collectors.toMap(LabOrderItemEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<LabOrderItemRecord> items = itemEntities.stream()
                .map(item -> new LabOrderItemRecord(
                        item.getId(),
                        item.getLabTestId(),
                        item.getTestCode(),
                        item.getTestName(),
                        item.getCategory(),
                        item.getDepartment(),
                        item.getSampleType(),
                        item.getUnit(),
                        item.getReferenceRange(),
                        item.getTurnaroundTime(),
                        item.getPrice(),
                        item.getSortOrder(),
                        item.getCreatedAt(),
                        resolveParameters(tenantId, item)
                ))
                .toList();
        List<LaboratoryOrderedTestView> lifecycleViews = laboratoryWorkflowService.listOrderTests(tenantId, order.getId());
        List<LabOrderedTestRecord> orderedTests = lifecycleViews.stream()
                .map(view -> {
                    LabOrderItemEntity item = itemById.get(view.labOrderItemId());
                    return new LabOrderedTestRecord(
                            view.labOrderItemId(),
                            item == null ? null : item.getLabTestId(),
                            item == null ? null : item.getTestCode(),
                            item == null ? null : item.getTestName(),
                            item == null ? null : item.getCategory(),
                            item == null ? null : item.getDepartment(),
                            item == null ? null : item.getSampleType(),
                            item == null ? null : item.getUnit(),
                            item == null ? null : item.getReferenceRange(),
                            item == null ? null : item.getTurnaroundTime(),
                            item == null ? null : item.getPrice(),
                            item == null ? 0 : item.getSortOrder(),
                            view.state(),
                            view.latestResultRevision(),
                            view.latestVerificationDecision(),
                            view.latestVerificationAt(),
                            view.latestVerificationBy(),
                            view.latestPublicationArtifactNumber(),
                            view.latestPublicationAt(),
                            view.latestPublicationBy(),
                            view.publicationChannels(),
                            view.latestResultSnapshotJson(),
                            view.latestResultEnteredAt(),
                            view.latestResultEnteredBy(),
                            view.specimenLinks().stream()
                                    .map(link -> new LabOrderedTestSpecimenRecord(
                                            link.labOrderSampleId(),
                                            link.accessionNumber(),
                                            link.barcodeValue(),
                                            link.specimenType(),
                                            link.containerType(),
                                            link.sampleStatus(),
                                            link.active(),
                                            link.collectedAt(),
                                            link.receivedAt(),
                                            link.linkedAt(),
                                            link.unlinkedAt()
                                    ))
                                    .toList()
                    );
                })
                .toList();
        Map<UUID, SampleLinkSummary> sampleLinkSummaries = buildSampleLinkSummaries(tenantId, order.getId());
        List<LabSampleRecord> samples = labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(tenantId, order.getId()).stream()
                .map(sample -> toRecord(tenantId, sample, sampleLinkSummaries.get(sample.getId())))
                .toList();
        LabSampleRecord primarySample = samples.isEmpty() ? null : samples.getFirst();
        LabSampleStatusRecord sampleSummaryStatus = summarizeSampleStatus(samples);
        List<LabOrderResultRecord> results = labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, order.getId()).stream()
                .map(result -> new LabOrderResultRecord(
                        result.getId(),
                        result.getLabOrderId(),
                        result.getLabOrderItemId(),
                        result.getTestCode(),
                        result.getTestName(),
                        result.getParameterName(),
                        result.getComponentName(),
                        result.getResultValue(),
                        result.getUnit(),
                        result.getReferenceRange(),
                        result.getSortOrder(),
                        result.getResultFlag(),
                        result.isCriticalResult(),
                        result.getCreatedAt(),
                        result.getUpdatedAt()
                ))
                .toList();
        List<LabOrderRecord.LabReportArtifactRecord> reportArtifacts = reportArtifacts(tenantId, order.getId());
        ResultEntryAuditMetadata resultEntryAudit = resolveResultEntryAudit(tenantId, order.getId());
        BillRecord bill = order.getBillId() == null ? null : billingService.findById(tenantId, order.getBillId()).orElse(null);
        return new LabOrderRecord(
                order.getId(),
                order.getTenantId(),
                order.getOrderNumber(),
                order.getPatientId(),
                order.getPatientNumber(),
                order.getPatientName(),
                order.getDoctorUserId(),
                order.getDoctorName(),
                order.getConsultationId(),
                order.getOrderOrigin(),
                order.getRequestedByInternalDoctorId(),
                order.getExternalDoctorName(),
                order.getExternalDoctorMobile(),
                order.getExternalClinicName(),
                order.getReferralSource(),
                order.getNotes(),
                toRecord(order.getStatus()),
                order.getOrderedAt(),
                order.getBillId(),
                bill == null ? null : bill.billNumber(),
                bill == null ? null : bill.status(),
                bill == null ? null : bill.totalAmount(),
                bill == null ? null : bill.dueAmount(),
                order.getExternalLabVendor(),
                order.getExternalReferenceNumber(),
                order.getDeliveredAt(),
                order.getDeliveredByUserId(),
                order.getPaymentCollectedAt(),
                order.getReadyForCollectionAt(),
                primarySample == null ? null : primarySample.accessionNumber(),
                primarySample == null ? null : primarySample.barcodeValue(),
                sampleSummaryStatus,
                order.getSampleType(),
                order.getSampleCollectedAt(),
                order.getSampleCollectedByUserId(),
                order.getSampleCollectedBy(),
                order.getSampleCollectionNotes(),
                order.getProcessingStartedAt(),
                order.getResultEnteredAt(),
                resultEntryAudit == null ? null : resultEntryAudit.actorAppUserId(),
                resultEntryAudit == null ? null : resultEntryAudit.actorDisplayName(),
                order.getResultComments(),
                order.getReportGeneratedAt(),
                order.getReportGeneratedByUserId(),
                resolveUserDisplayName(tenantId, order.getReportGeneratedByUserId()).orElse(null),
                order.getReportFilename(),
                order.getReportPublishedAt(),
                order.getReportPublishedByUserId(),
                order.getReportVerificationToken(),
                order.getReportDeliveryStatus(),
                parseDeliveryChannels(order.getReportDeliveryChannels()),
                order.getReportDeliveryNotes(),
                reportArtifacts,
                order.getDoctorReviewedAt(),
                order.getDoctorReviewedByUserId(),
                order.getDoctorReviewedBy(),
                order.getDoctorReviewDecision(),
                order.getDoctorReviewReason(),
                order.getDoctorComments(),
                order.getLabVerifiedAt(),
                order.getLabVerifiedBy(),
                resolveUserDisplayName(tenantId, order.getLabVerifiedBy()).orElse(null),
                order.getLabVerificationDecision(),
                order.getLabVerificationComments(),
                order.getLabVerificationReason(),
                attachments,
                items,
                orderedTests,
                samples,
                results,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private LabOrderStatusRecord toRecord(LabOrderStatus status) {
        return switch (status) {
            case ORDERED -> LabOrderStatusRecord.ORDERED;
            case PAYMENT_PENDING -> LabOrderStatusRecord.PAYMENT_PENDING;
            case PAID -> LabOrderStatusRecord.PAID;
            case READY_FOR_COLLECTION -> LabOrderStatusRecord.READY_FOR_COLLECTION;
            case SAMPLE_COLLECTED -> LabOrderStatusRecord.SAMPLE_COLLECTED;
            case PROCESSING -> LabOrderStatusRecord.PROCESSING;
            case RESULT_ENTERED -> LabOrderStatusRecord.RESULT_ENTERED;
            case IN_PROGRESS -> LabOrderStatusRecord.IN_PROGRESS;
            case PARTIALLY_READY -> LabOrderStatusRecord.PARTIALLY_READY;
            case PARTIALLY_PUBLISHED -> LabOrderStatusRecord.PARTIALLY_PUBLISHED;
            case REPORT_READY -> LabOrderStatusRecord.REPORT_READY;
            case REPORT_GENERATED -> LabOrderStatusRecord.REPORT_GENERATED;
            case DOCTOR_REVIEWED -> LabOrderStatusRecord.DOCTOR_REVIEWED;
            case DELIVERED -> LabOrderStatusRecord.DELIVERED;
            case CANCELLED -> LabOrderStatusRecord.CANCELLED;
        };
    }

    private LabSampleRecord toRecord(UUID tenantId, LabOrderSampleEntity entity, SampleLinkSummary summary) {
        return new LabSampleRecord(
                entity.getId(),
                entity.getLabOrderId(),
                entity.getLabOrderItemId(),
                entity.getAccessionNumber(),
                entity.getBarcodeValue(),
                entity.getSpecimenType(),
                entity.getContainerType(),
                toRecord(entity.getStatus()),
                entity.getCollectedAt(),
                entity.getCollectedBy() == null
                        ? null
                        : resolveUserDisplayName(tenantId, entity.getCollectedBy()).orElse(entity.getCollectedBy().toString()),
                entity.getReceivedAt(),
                entity.getReceivedBy(),
                entity.getRejectionReason(),
                entity.isRecollectionRequired(),
                entity.getNotes(),
                summary == null ? List.of() : summary.linkedLabOrderItemIds(),
                summary == null ? List.of() : summary.linkedTestNames(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }

    private LabSampleRecord toRecord(UUID tenantId, LabOrderSampleEntity entity) {
        return toRecord(tenantId, entity, SampleLinkSummary.empty());
    }

    private LabSampleStatusRecord toRecord(LabSampleStatus status) {
        return switch (status) {
            case PENDING_COLLECTION -> LabSampleStatusRecord.PENDING_COLLECTION;
            case COLLECTED -> LabSampleStatusRecord.COLLECTED;
            case RECEIVED -> LabSampleStatusRecord.RECEIVED;
            case REJECTED -> LabSampleStatusRecord.REJECTED;
            case RECOLLECTION_REQUIRED -> LabSampleStatusRecord.RECOLLECTION_REQUIRED;
            case CANCELLED -> LabSampleStatusRecord.CANCELLED;
        };
    }

    private LabOrderResultPdf buildReportPdf(UUID tenantId, LabOrderEntity order, List<UUID> selectedItemIds, String reportMode, String reportType) {
        LabOrderRecord record = filterRecordForSelectedTests(toRecord(tenantId, order), selectedItemIds);
        var clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        String clinicName = clinic == null ? "Clinic" : firstText(clinic.displayName(), clinic.clinicName(), "Clinic");
        if (!StringUtils.hasText(clinicName)) {
            clinicName = "Clinic";
        }
        String clinicContact = clinic == null ? "" : Stream.of(clinic.phone(), clinic.email(), clinic.city(), clinic.state(), clinic.country())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" | "));
        String clinicAddress = clinic == null ? "" : Stream.of(clinic.addressLine1(), clinic.addressLine2(), clinic.postalCode())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", "));
        BufferedImage logo = loadClinicLogo(tenantId, clinic);
        BufferedImage verificationQr = buildVerificationQr(verificationUrl(tenantId, record));
        ZoneId tenantZone = clinicTimeZoneResolver.resolve(tenantId);
        String reportTitle = reportTitleLabel(record, reportMode, reportType);
        float margin = 34f;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (PdfReportLayout layout = new PdfReportLayout(document, margin)) {
                drawReportHeader(layout, clinicName, clinicAddress, clinicContact, logo, reportTitle, businessReportStatusLabel(record, reportMode, reportType));
                drawMetaBlock(tenantId, layout, record, tenantZone);
                drawVerificationBlock(layout, verificationUrl(tenantId, record), record.reportVerificationToken(), verificationQr);
                drawResultsTable(layout, record);
                drawNotesBlock(layout, record);
                drawSignatureBlock(tenantId, layout, record);
            }
            drawPageFooters(document, margin, "Generated by Jeevanam Healthcare | Powered by AIVA");
            document.save(output);
            return new LabOrderResultPdf(safeFilename(record.orderNumber()) + "-lab-report.pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate lab report PDF", ex);
        }
    }

    private LabOrderRecord filterRecordForSelectedTests(LabOrderRecord record, List<UUID> selectedItemIds) {
        if (record == null || selectedItemIds == null || selectedItemIds.isEmpty()) {
            return record;
        }
        Set<UUID> selected = selectedItemIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (selected.isEmpty()) {
            return record;
        }
        List<com.deepthoughtnet.clinic.api.lab.service.model.LabOrderItemRecord> items = safeList(record.items()).stream()
                .filter(item -> selected.contains(item.id()))
                .toList();
        List<com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultRecord> results = safeList(record.results()).stream()
                .filter(result -> result.labOrderItemId() != null && selected.contains(result.labOrderItemId()))
                .toList();
        List<LabOrderedTestRecord> orderedTests = safeList(record.orderedTests()).stream()
                .filter(test -> selected.contains(test.labOrderItemId()))
                .toList();
        return new LabOrderRecord(
                record.id(),
                record.tenantId(),
                record.orderNumber(),
                record.patientId(),
                record.patientNumber(),
                record.patientName(),
                record.doctorUserId(),
                record.doctorName(),
                record.consultationId(),
                record.orderOrigin(),
                record.requestedByInternalDoctorId(),
                record.externalDoctorName(),
                record.externalDoctorMobile(),
                record.externalClinicName(),
                record.referralSource(),
                record.notes(),
                record.status(),
                record.orderedAt(),
                record.billId(),
                record.billNumber(),
                record.billStatus(),
                record.billTotalAmount(),
                record.billDueAmount(),
                record.externalLabVendor(),
                record.externalReferenceNumber(),
                record.deliveredAt(),
                record.deliveredByUserId(),
                record.paymentCollectedAt(),
                record.readyForCollectionAt(),
                record.sampleAccessionNumber(),
                record.sampleBarcodeValue(),
                record.sampleSummaryStatus(),
                record.sampleType(),
                record.sampleCollectedAt(),
                record.sampleCollectedByUserId(),
                record.sampleCollectedBy(),
                record.sampleCollectionNotes(),
                record.processingStartedAt(),
                record.resultEnteredAt(),
                record.resultEnteredByUserId(),
                record.resultEnteredBy(),
                record.resultComments(),
                record.reportGeneratedAt(),
                record.reportGeneratedByUserId(),
                record.reportGeneratedBy(),
                record.reportFilename(),
                record.reportPublishedAt(),
                record.reportPublishedByUserId(),
                record.reportVerificationToken(),
                record.reportDeliveryStatus(),
                record.reportDeliveryChannels(),
                record.reportDeliveryNotes(),
                record.reportArtifacts(),
                record.doctorReviewedAt(),
                record.doctorReviewedByUserId(),
                record.doctorReviewedBy(),
                record.doctorReviewDecision(),
                record.doctorReviewReason(),
                record.doctorComments(),
                record.labVerifiedAt(),
                record.labVerifiedBy(),
                record.labVerifiedByName(),
                record.labVerificationDecision(),
                record.labVerificationComments(),
                record.labVerificationReason(),
                record.attachments(),
                items.stream().map(item -> new com.deepthoughtnet.clinic.api.lab.service.model.LabOrderItemRecord(
                        item.id(),
                        item.labTestId(),
                        item.testCode(),
                        item.testName(),
                        item.category(),
                        item.department(),
                        item.sampleType(),
                        item.unit(),
                        item.referenceRange(),
                        item.turnaroundTime(),
                        item.price(),
                        item.sortOrder(),
                        item.createdAt(),
                        item.parameters()
                )).toList(),
                orderedTests,
                record.samples(),
                results,
                record.createdAt(),
                record.updatedAt()
        );
    }

    private void drawReportHeader(PdfReportLayout layout, String clinicName, String clinicAddress, String clinicContact, BufferedImage logo, String reportHeading, String reportStatus) throws IOException {
        float headerHeight = 88f;
        layout.ensureSpace(headerHeight + 14f);
        float headerTop = layout.y;
        float leftWidth = layout.width * 0.64f;
        float rightWidth = layout.width - leftWidth;
        float logoSize = 52f;
        float identityX = layout.margin + 12f + logoSize + 12f;
        PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        layout.content.addRect(layout.margin, headerTop - headerHeight, layout.width, headerHeight);
        layout.content.stroke();

        if (logo != null) {
            PDImageXObject image = LosslessFactory.createFromImage(layout.document, logo);
            layout.content.drawImage(image, layout.margin + 14f, headerTop - 62f, logoSize, logoSize);
        } else {
            layout.content.addRect(layout.margin + 14f, headerTop - 62f, logoSize, logoSize);
            layout.content.stroke();
            String fallback = logoFallbackText(clinicName);
            float fallbackWidth = textWidth(boldFont, 12f, fallback);
            writeLine(layout.content, fallback, 12f, layout.margin + 14f + Math.max(4f, (logoSize - fallbackWidth) / 2f), headerTop - 34f, boldFont);
        }

        float textY = headerTop - 18f;
        textY = writeWrapped(layout.content, clinicName, 14.5f, identityX, textY, leftWidth - logoSize - 34f, boldFont, 16f);
        if (StringUtils.hasText(clinicAddress)) {
            textY = writeWrapped(layout.content, clinicAddress, 9.2f, identityX, textY - 2f, leftWidth - logoSize - 34f, regularFont, 10.4f);
        }
        if (StringUtils.hasText(clinicContact)) {
            textY = writeWrapped(layout.content, clinicContact, 9.2f, identityX, textY - 2f, leftWidth - logoSize - 34f, regularFont, 10.4f);
        }

        float rightX = layout.margin + leftWidth + 12f;
        writeLine(layout.content, "LABORATORY REPORT", 16f, rightX, headerTop - 20f, boldFont);
        writeLine(layout.content, safe(reportHeading), 11f, rightX, headerTop - 38f, boldFont);
        writeLine(layout.content, safe(reportStatus), 9.2f, rightX, headerTop - 56f, regularFont);
        float dividerY = headerTop - headerHeight - 8f;
        layout.content.moveTo(layout.margin, dividerY);
        layout.content.lineTo(layout.margin + layout.width, dividerY);
        layout.content.stroke();
        layout.y = dividerY - 12f;
    }

    private void drawMetaBlock(UUID tenantId, PdfReportLayout layout, LabOrderRecord record, ZoneId tenantZone) throws IOException {
        List<MetaPair> pairs = Stream.of(
                new MetaPair("Patient Name", record.patientName()),
                new MetaPair("Patient ID", record.patientNumber()),
                new MetaPair("Order No", record.orderNumber()),
                new MetaPair("Accession No", record.sampleAccessionNumber()),
                new MetaPair("Sample Type", firstText(record.sampleType(), fallbackSampleType(record))),
                new MetaPair("Collected At", formatDateTime(record.sampleCollectedAt(), tenantZone)),
                new MetaPair("Approved By", firstText(record.labVerifiedByName(), resolveUserDisplayName(tenantId, record.labVerifiedBy()).orElse(null))),
                new MetaPair("Approved At", formatDateTime(record.labVerifiedAt() == null ? record.reportPublishedAt() : record.labVerifiedAt(), tenantZone)),
                new MetaPair("Reported At", formatDateTime(record.reportPublishedAt() == null ? record.reportGeneratedAt() : record.reportPublishedAt(), tenantZone))
        ).filter(pair -> StringUtils.hasText(pair.value()) && !"-".equals(pair.value())).toList();
        writeSectionHeader(layout.content, "Patient & Report Details", layout.margin, layout.y, layout.width);
        layout.y -= 18f;
        float columnWidth = layout.width / 2f;
        for (int i = 0; i < pairs.size(); i += 2) {
            MetaPair left = pairs.get(i);
            MetaPair right = i + 1 < pairs.size() ? pairs.get(i + 1) : new MetaPair("", "");
            float rowHeight = Math.max(24f, Math.max(measureMetaCellHeight(left, columnWidth), measureMetaCellHeight(right, columnWidth)));
            layout.ensureSpace(rowHeight + 2f);
            drawMetaCell(layout.content, layout.margin, layout.y, columnWidth, rowHeight, left);
            drawMetaCell(layout.content, layout.margin + columnWidth, layout.y, columnWidth, rowHeight, right);
            layout.y -= rowHeight;
        }
        layout.y -= 8f;
    }

    private void drawVerificationBlock(PdfReportLayout layout, String verificationUrl, String token, BufferedImage verificationQr) throws IOException {
        float blockHeight = 52f;
        layout.ensureSpace(blockHeight + 10f);
        writeSectionHeader(layout.content, "Verification", layout.margin, layout.y, layout.width);
        layout.y -= 17f;
        float top = layout.y;
        float qrColumnWidth = 68f;
        float qrSize = 44f;
        float textWidth = layout.width - qrColumnWidth - 24f;
        layout.content.addRect(layout.margin, top - blockHeight, layout.width, blockHeight);
        layout.content.stroke();
        writeLine(layout.content, "Verification Reference", 8.6f, layout.margin + 12f, top - 15f, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeLine(layout.content, shortVerificationReference(token), 8.8f, layout.margin + 12f, top - 26f, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        writeLine(layout.content, "Verify online:", 8.6f, layout.margin + 12f, top - 39f, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeWrapped(layout.content, verificationUrl, 8.1f, layout.margin + 12f, top - 49f, textWidth, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f);
        if (verificationQr != null) {
            PDImageXObject image = LosslessFactory.createFromImage(layout.document, verificationQr);
            float qrX = layout.margin + layout.width - qrColumnWidth + (qrColumnWidth - qrSize) / 2f;
            float qrY = top - qrSize - 6f;
            layout.content.drawImage(image, qrX, qrY, qrSize, qrSize);
            float captionWidth = textWidth(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.9f, "Scan to verify");
            writeLine(layout.content, "Scan to verify", 7.9f, qrX + (qrSize - captionWidth) / 2f, qrY - 8f, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        }
        layout.y = top - blockHeight - 8f;
    }

    private void drawSignatureBlock(UUID tenantId, PdfReportLayout layout, LabOrderRecord record) throws IOException {
        float blockHeight = 34f;
        layout.ensureSpace(blockHeight + 8f);
        writeSectionHeader(layout.content, "Authorized Signatory", layout.margin, layout.y, layout.width);
        layout.y -= 16f;
        float top = layout.y;
        layout.content.addRect(layout.margin, top - blockHeight, layout.width, blockHeight);
        layout.content.stroke();
        writeLine(layout.content, safe(firstText(record.labVerifiedByName(), resolveUserDisplayName(tenantId, record.labVerifiedBy()).orElse(null))), 9.2f, layout.margin + 12f, top - 13f, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeLine(layout.content, "Digitally verified", 8.7f, layout.margin + 12f, top - 25f, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        layout.y = top - blockHeight - 6f;
    }

    private void drawMetaCell(PDPageContentStream content, float x, float y, float width, float height, MetaPair pair) throws IOException {
        content.addRect(x, y - height, width, height);
        content.stroke();
        writeLine(content, pair.label(), 8.3f, x + 7f, y - 10.5f, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeWrapped(content, safe(pair.value()), 8.8f, x + 7f, y - 23f, width - 14f);
    }

    private void drawResultsTable(PdfReportLayout layout, LabOrderRecord record) throws IOException {
        List<LabOrderedTestRecord> orderedTests = safeList(record.orderedTests());
        Map<UUID, List<LabOrderResultRecord>> resultsByItem = safeList(record.results()).stream()
                .filter(result -> result.labOrderItemId() != null)
                .collect(Collectors.groupingBy(LabOrderResultRecord::labOrderItemId, LinkedHashMap::new, Collectors.toList()));
        writeSectionHeader(layout.content, "Results", layout.margin, layout.y, layout.width);
        layout.y -= 18f;
        if (orderedTests.isEmpty() && resultsByItem.isEmpty()) {
            layout.ensureSpace(32f);
            writeLine(layout.content, "No results entered", 9f, layout.margin + 8, layout.y - 14, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
            layout.y -= 22f;
            return;
        }
        float[] columns = new float[]{0.38f, 0.13f, 0.11f, 0.21f, 0.17f};
        String[] headers = new String[]{"Parameter / Component", "Result", "Unit", "Reference Range", "Interpretation"};
        if (!orderedTests.isEmpty()) {
            for (LabOrderedTestRecord orderedTest : orderedTests) {
                drawResultTestSection(layout, orderedTest.testName(), orderedTest.testCode(), resultsByItem.getOrDefault(orderedTest.labOrderItemId(), List.of()), headers, columns);
            }
        } else {
            for (Map.Entry<UUID, List<LabOrderResultRecord>> entry : resultsByItem.entrySet()) {
                drawResultTestSection(layout, "Ordered Test", entry.getKey() == null ? null : entry.getKey().toString(), entry.getValue(), headers, columns);
            }
        }
        layout.y -= 8f;
    }

    private void drawResultTestSection(PdfReportLayout layout, String testName, String testCode, List<LabOrderResultRecord> rows, String[] headers, float[] columns) throws IOException {
        String sectionTitle = StringUtils.hasText(testName) ? testName : firstText(testCode, "Test");
        List<LabOrderResultRecord> effectiveRows = rows == null ? List.of() : rows;
        float required = 34f;
        if (effectiveRows.isEmpty()) {
            required += 24f;
        } else {
            for (LabOrderResultRecord row : effectiveRows) {
                required += measureResultRowHeight(resultRowValues(sectionTitle, row), columns, layout.width) + 2f;
            }
        }
        layout.ensureSpace(required + 18f);
        layout.content.setNonStrokingColor(245 / 255f, 247 / 255f, 251 / 255f);
        layout.content.addRect(layout.margin, layout.y - 16f, layout.width, 16f);
        layout.content.fill();
        layout.content.setNonStrokingColor(0f, 0f, 0f);
        writeLine(layout.content, sectionTitle, 10f, layout.margin + 8f, layout.y - 11f, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        layout.y -= 18f;
        drawResultsHeader(layout, headers, columns);
        if (effectiveRows.isEmpty()) {
            float rowHeight = 24f;
            layout.content.addRect(layout.margin, layout.y - rowHeight, layout.width, rowHeight);
            layout.content.stroke();
            writeLine(layout.content, "No results entered", 9f, layout.margin + 8, layout.y - 15, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
            layout.y -= rowHeight + 8f;
            return;
        }
        for (LabOrderResultRecord row : effectiveRows) {
            String[] values = resultRowValues(sectionTitle, row);
            float rowHeight = measureResultRowHeight(values, columns, layout.width);
            if (!layout.hasSpace(rowHeight + 10f)) {
                layout.newPage();
                writeSectionHeader(layout.content, "Results (continued)", layout.margin, layout.y, layout.width);
                layout.y -= 18f;
                drawResultsHeader(layout, headers, columns);
            }
            float x = layout.margin;
            for (int i = 0; i < values.length; i++) {
                float colWidth = layout.width * columns[i];
                layout.content.addRect(x, layout.y - rowHeight, colWidth, rowHeight);
                layout.content.stroke();
                if (i == 4 && StringUtils.hasText(values[i])) {
                    if (isCriticalInterpretation(values[i])) {
                        setFillColor(layout.content, 185, 28, 28);
                    } else if ("Low".equalsIgnoreCase(values[i]) || "High".equalsIgnoreCase(values[i])) {
                        setFillColor(layout.content, 237, 108, 2);
                    } else {
                        setFillColor(layout.content, 27, 94, 32);
                    }
                }
                writeWrappedInCell(layout.content, values[i], 9f, x + 6, layout.y - 13, colWidth - 12);
                if (i == 4) {
                    setFillColor(layout.content, 0, 0, 0);
                }
                x += colWidth;
            }
            layout.y -= rowHeight;
        }
        layout.y -= 8f;
    }

    private void drawResultsHeader(PdfReportLayout layout, String[] headers, float[] columns) throws IOException {
        float headerHeight = 20f;
        layout.content.setNonStrokingColor(232 / 255f, 240 / 255f, 254 / 255f);
        layout.content.addRect(layout.margin, layout.y - headerHeight, layout.width, headerHeight);
        layout.content.fill();
        layout.content.setNonStrokingColor(0f, 0f, 0f);
        PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float x = layout.margin;
        for (int i = 0; i < headers.length; i++) {
            float colWidth = layout.width * columns[i];
            layout.content.addRect(x, layout.y - headerHeight, colWidth, headerHeight);
            layout.content.stroke();
            writeLine(layout.content, headers[i], 8.3f, x + 5f, layout.y - 13f, boldFont);
            x += colWidth;
        }
        layout.y -= headerHeight;
    }

    private void drawNotesBlock(PdfReportLayout layout, LabOrderRecord record) throws IOException {
        List<String> notes = clinicalNotes(record);
        if (notes.isEmpty()) {
            return;
        }
        float needed = 18f;
        for (String note : notes) {
            needed += 14f + wrap(note, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f, layout.width - 34f).size() * 9.5f;
        }
        layout.ensureSpace(Math.min(needed, 96f));
        writeSectionHeader(layout.content, "Clinical / Laboratory Notes", layout.margin, layout.y, layout.width);
        layout.y -= 16f;
        for (String note : notes) {
            List<String> lines = wrap(note, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f, layout.width - 34f);
            float boxHeight = 12f + Math.max(1, lines.size()) * 9.5f;
            layout.ensureSpace(boxHeight + 6f);
            layout.content.setNonStrokingColor(250 / 255f, 251 / 255f, 253 / 255f);
            layout.content.addRect(layout.margin, layout.y - boxHeight, layout.width, boxHeight);
            layout.content.fill();
            layout.content.setNonStrokingColor(0f, 0f, 0f);
            writeLine(layout.content, "•", 9.5f, layout.margin + 10f, layout.y - 10f, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            layout.y -= 12f;
            for (String line : lines) {
                if (!layout.hasSpace(14f)) {
                    layout.newPage();
                    writeSectionHeader(layout.content, "Clinical / Laboratory Notes (continued)", layout.margin, layout.y, layout.width);
                    layout.y -= 16f;
                }
                writeLine(layout.content, line, 8.8f, layout.margin + 18f, layout.y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                layout.y -= 9.5f;
            }
            layout.y -= 6f;
        }
        layout.y -= 1f;
    }

    private void drawReportBorder(PDPageContentStream content, PDPage page, float margin) throws IOException {
        content.addRect(margin, margin, page.getMediaBox().getWidth() - (margin * 2), page.getMediaBox().getHeight() - (margin * 2));
        content.stroke();
    }

    private void writeSectionHeader(PDPageContentStream content, String title, float margin, float y, float width) throws IOException {
        setFillColor(content, 30, 64, 175);
        content.addRect(margin, y - 14, width, 14);
        content.fill();
        setFillColor(content, 255, 255, 255);
        writeLine(content, title, 9.2f, margin + 6, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        setFillColor(content, 0, 0, 0);
    }

    private void writeWrappedInCell(PDPageContentStream content, String text, float fontSize, float x, float y, float maxWidth) throws IOException {
        List<String> lines = wrap(text, new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize, maxWidth);
        if (lines.isEmpty()) {
            lines = List.of("");
        }
        float lineY = y;
        for (String line : lines) {
            writeLine(content, line, fontSize, x, lineY, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            lineY -= fontSize + 1;
        }
    }

    private float writeWrapped(PDPageContentStream content, String text, float fontSize, float x, float y, float maxWidth) throws IOException {
        return writeWrapped(content, text, fontSize, x, y, maxWidth, new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize + 1f);
    }

    private float writeWrapped(PDPageContentStream content, String text, float fontSize, float x, float y, float maxWidth, PDType1Font font, float lineHeight) throws IOException {
        List<String> lines = wrap(text, font, fontSize, maxWidth);
        if (lines.isEmpty()) {
            lines = List.of("");
        }
        float lineY = y;
        for (String line : lines) {
            writeLine(content, line, fontSize, x, lineY, font);
            lineY -= lineHeight;
        }
        return lineY;
    }

    private List<String> wrap(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        String value = safe(text);
        if (!StringUtils.hasText(value)) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawToken : value.split("\\s+")) {
            List<String> tokenParts = splitLongToken(rawToken, font, fontSize, maxWidth);
            for (String token : tokenParts) {
                String candidate = current.isEmpty() ? token : current + " " + token;
                if (textWidth(font, fontSize, candidate) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    if (current.length() > 0) {
                        lines.add(current.toString());
                    }
                    current.setLength(0);
                    current.append(token);
                }
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private List<String> splitLongToken(String token, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        if (textWidth(font, fontSize, token) <= maxWidth) {
            return List.of(token);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            String candidate = part.toString() + token.charAt(i);
            if (part.length() > 0 && textWidth(font, fontSize, candidate) > maxWidth) {
                parts.add(part.toString());
                part.setLength(0);
            }
            part.append(token.charAt(i));
        }
        if (part.length() > 0) {
            parts.add(part.toString());
        }
        return parts;
    }

    private float textWidth(PDType1Font font, float fontSize, String text) throws IOException {
        return font.getStringWidth(text == null ? "" : text) / 1000f * fontSize;
    }

    private void writeLine(PDPageContentStream content, String text, float fontSize, float x, float y, PDType1Font font) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(safe(text));
        content.endText();
    }

    private void setFillColor(PDPageContentStream content, int red, int green, int blue) throws IOException {
        content.setNonStrokingColor(red / 255f, green / 255f, blue / 255f);
        content.setStrokingColor(red / 255f, green / 255f, blue / 255f);
    }

    private void drawPageFooters(PDDocument document, float margin, String footerText) throws IOException {
        int totalPages = document.getNumberOfPages();
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream content = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                String pageLabel = "Page " + (i + 1) + " of " + totalPages;
                float fontSize = 8f;
                float pageWidth = page.getMediaBox().getWidth();
                float pageLabelWidth = textWidth(font, fontSize, pageLabel);
                content.moveTo(margin + 8f, margin + 24f);
                content.lineTo(pageWidth - margin - 8f, margin + 24f);
                content.stroke();
                writeLine(content, footerText, fontSize, margin + 8f, margin + 11f, font);
                writeLine(content, pageLabel, fontSize, pageWidth - margin - 8f - pageLabelWidth, margin + 11f, font);
            }
        }
    }

    private float measureMetaCellHeight(MetaPair pair, float width) throws IOException {
        List<String> lines = wrap(safe(pair.value()), new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f, width - 14f);
        return Math.max(22f, 12f + Math.max(1, lines.size()) * 8.8f);
    }

    private List<String> clinicalNotes(LabOrderRecord record) {
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        if (record == null) {
            return List.of();
        }
        if (isPatientFacingClinicalNote(record.sampleCollectionNotes())) {
            notes.add(record.sampleCollectionNotes().trim());
        }
        if (isPatientFacingClinicalNote(record.resultComments())) {
            notes.add(record.resultComments().trim());
        }
        return List.copyOf(notes);
    }

    private boolean isPatientFacingClinicalNote(String note) {
        if (!StringUtils.hasText(note)) {
            return false;
        }
        String normalized = note.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("uat")
                || normalized.contains("internal")
                || normalized.contains("workflow")
                || normalized.contains("partial publication")
                || normalized.contains("shared specimen")
                || normalized.contains("e2e")
                || normalized.contains("retest")) {
            return false;
        }
        return normalized.contains("fasting")
                || normalized.contains("hemoly")
                || normalized.contains("haemoly")
                || normalized.contains("interpret with caution")
                || normalized.contains("repeat specimen")
                || normalized.contains("repeat analysis")
                || normalized.contains("confirmed")
                || normalized.contains("recommend");
    }

    private float measureResultRowHeight(String[] values, float[] columns, float width) throws IOException {
        float max = 28f;
        for (int i = 0; i < values.length; i++) {
            float colWidth = width * columns[i] - 12f;
            List<String> lines = wrap(values[i], new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9f, colWidth);
            max = Math.max(max, 12f + Math.max(1, lines.size()) * 10.5f);
        }
        return max;
    }

    private void drawLabelValue(PDPageContentStream content, String label, String value, float x, float y, float width) throws IOException {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        writeLine(content, label + ":", 8.8f, x, y, bold);
        float labelWidth = textWidth(bold, 8.8f, label + ":") + 8f;
        writeWrapped(content, value, 9f, x + labelWidth, y, width - labelWidth);
    }

    private String shortVerificationReference(String token) {
        if (!StringUtils.hasText(token)) {
            return "Not available";
        }
        String normalized = token.trim().toUpperCase();
        int visible = Math.min(12, normalized.length());
        return "LAB-" + normalized.substring(0, visible);
    }

    private final class PdfReportLayout implements AutoCloseable {
        private final PDDocument document;
        private final float margin;
        private final float width;
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        private PdfReportLayout(PDDocument document, float margin) throws IOException {
            this.document = document;
            this.margin = margin;
            this.width = PDRectangle.A4.getWidth() - (margin * 2f);
            newPage();
        }

        private boolean hasSpace(float needed) {
            return y - needed >= margin + 34f;
        }

        private void ensureSpace(float needed) throws IOException {
            if (!hasSpace(needed)) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            drawReportBorder(content, page, margin);
            y = page.getMediaBox().getHeight() - margin - 14f;
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }
    }

    private String interpretationLabel(String resultFlag) {
        if (!StringUtils.hasText(resultFlag)) {
            return "Normal";
        }
        return switch (resultFlag.trim().toUpperCase()) {
            case "LOW" -> "Low";
            case "HIGH" -> "High";
            case "CRITICAL_LOW" -> "Critical Low";
            case "CRITICAL_HIGH" -> "Critical High";
            case "CRITICAL" -> "Critical";
            default -> "NORMAL".equalsIgnoreCase(resultFlag.trim()) ? "Normal" : resultFlag.trim();
        };
    }

    private boolean isCriticalInterpretation(String label) {
        return StringUtils.hasText(label) && label.trim().toUpperCase().startsWith("CRITICAL");
    }

    private void recordDeliveryChannelAudits(UUID tenantId, LabOrderEntity order, List<String> channels, UUID actorAppUserId) {
        if (order == null || channels == null || channels.isEmpty()) {
            return;
        }
        for (String channel : channels) {
            String normalized = channel == null ? "" : channel.trim().toUpperCase(java.util.Locale.ROOT);
            switch (normalized) {
                case "PATIENT_PORTAL" -> auditOrder(tenantId, order, "lab_order.report_portal_published", actorAppUserId, "Published report to patient portal");
                case "DOCTOR_NOTIFICATION" -> auditOrder(tenantId, order, "lab_order.report_doctor_notified", actorAppUserId, "Doctor notified of published report");
                case "EMAIL" -> auditOrder(tenantId, order, "lab_order.report_email_queued", actorAppUserId, "Queued email delivery for published report");
                case "WHATSAPP" -> auditOrder(tenantId, order, "lab_order.report_whatsapp_queued", actorAppUserId, "Queued WhatsApp delivery for published report");
                case "PRINT" -> auditOrder(tenantId, order, "lab_order.report_print_ready", actorAppUserId, "Prepared report for printing");
                default -> {
                }
            }
        }
    }

    private String buildReportDeliveryMessage(String action, String channel, String notes) {
        String label = reportDeliveryActionLabel(action, channel);
        if (StringUtils.hasText(notes)) {
            return label + ": " + notes.trim();
        }
        return label;
    }

    private String reportDeliveryActionLabel(String action, String channel) {
        String normalized = action == null ? "" : action.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "lab_order.report_viewed", "view" -> "Portal viewed";
            case "lab_order.report_downloaded", "download" -> "PDF downloaded";
            case "lab_order.report_printed", "print" -> "Printed";
            case "lab_order.report_email_sent", "email" -> "Email sent";
            case "lab_order.report_whatsapp_sent", "whatsapp" -> "WhatsApp sent";
            case "lab_order.report_shared_link", "share_link" -> "Share link copied";
            default -> {
                if (StringUtils.hasText(channel)) {
                    yield channel.trim() + " action recorded";
                }
                yield "Report delivery action recorded";
            }
        };
    }

    private String verificationUrl(UUID tenantId, LabOrderRecord record) {
        String token = record == null ? null : record.reportVerificationToken();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String path = "/api/public/lab/reports/" + token.trim() + "/verify";
        String baseUrl = labReportVerificationProperties == null ? null : labReportVerificationProperties.getPublicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return path;
        }
        return trimTrailingSlash(baseUrl) + path;
    }

    private BufferedImage buildVerificationQr(String verificationUrl) {
        if (!StringUtils.hasText(verificationUrl)) {
            return null;
        }
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(verificationUrl, BarcodeFormat.QR_CODE, 240, 240, Map.of(EncodeHintType.MARGIN, 1));
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (WriterException ex) {
            return null;
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("/+$", "");
    }

    private String businessReportStatusLabel(LabOrderRecord record) {
        return businessReportStatusLabel(record, currentReportArtifact(record));
    }

    private String businessReportStatusLabel(LabOrderRecord record, String reportMode, String reportType) {
        if (StringUtils.hasText(reportType)) {
            if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(reportType)) {
                return "Final";
            }
            if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM.equalsIgnoreCase(reportType)) {
                if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL.equalsIgnoreCase(reportMode)) {
                    return "Individual Interim";
                }
                if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED.equalsIgnoreCase(reportMode)) {
                    return "Grouped Interim";
                }
                return "Interim";
            }
        }
        return businessReportStatusLabel(record);
    }

    private String businessReportStatusLabel(LabOrderEntity order) {
        if (order == null) {
            return "Published";
        }
        if (order.getReportPublishedAt() != null || StringUtils.hasText(order.getReportFilename())) {
            return "Published";
        }
        if (order.getStatus() == null) {
            return "Published";
        }
        return switch (order.getStatus()) {
            case REPORT_GENERATED, DELIVERED -> "Published";
            case REPORT_READY -> "Final";
            default -> order.getStatus().name().replace('_', ' ');
        };
    }

    private String businessReportStatusLabel(LabOrderEntity order, LaboratoryReportArtifactView artifact) {
        if (artifact != null) {
            if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(artifact.reportType())) {
                return "Final";
            }
            if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM.equalsIgnoreCase(artifact.reportType())) {
                if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL.equalsIgnoreCase(artifact.reportMode())) {
                    return "Individual Interim";
                }
                if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED.equalsIgnoreCase(artifact.reportMode())) {
                    return "Grouped Interim";
                }
                return "Interim";
            }
        }
        return businessReportStatusLabel(order);
    }

    private String businessReportStatusLabel(LabOrderRecord record, LabOrderRecord.LabReportArtifactRecord artifact) {
        if (artifact != null) {
            if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(artifact.reportType())) {
                return "Final";
            }
            if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM.equalsIgnoreCase(artifact.reportType())) {
                if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL.equalsIgnoreCase(artifact.reportMode())) {
                    return "Individual Interim";
                }
                if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED.equalsIgnoreCase(artifact.reportMode())) {
                    return "Grouped Interim";
                }
                return "Interim";
            }
        }
        if (record == null) {
            return "Published";
        }
        if (record.reportPublishedAt() != null || record.reportFilename() != null) {
            return "Published";
        }
        if (record.status() == null) {
            return "Published";
        }
        return switch (record.status()) {
            case REPORT_GENERATED, DELIVERED -> "Published";
            case REPORT_READY -> "Final";
            default -> record.status().name().replace('_', ' ');
        };
    }

    private String reportTitleLabel(LabOrderRecord record) {
        return reportTitleLabel(record, null, null);
    }

    private String reportTitleLabel(LabOrderRecord record, String reportMode, String reportType) {
        if (StringUtils.hasText(reportMode)) {
            if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_CONSOLIDATED.equalsIgnoreCase(reportMode)
                    && LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(reportType)) {
                return "FINAL LABORATORY REPORT";
            }
            if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL.equalsIgnoreCase(reportMode)) {
                return "INDIVIDUAL LABORATORY REPORT";
            }
            if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED.equalsIgnoreCase(reportMode)) {
                return "GROUPED LABORATORY REPORT";
            }
            if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(reportType)) {
                return "FINAL LABORATORY REPORT";
            }
        }
        LabOrderRecord.LabReportArtifactRecord artifact = currentReportArtifact(record);
        if (artifact == null) {
            return "Interim Report";
        }
        if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(artifact.reportType())) {
            return "FINAL LABORATORY REPORT";
        }
        if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL.equalsIgnoreCase(artifact.reportMode())) {
            return "INDIVIDUAL LABORATORY REPORT";
        }
        if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED.equalsIgnoreCase(artifact.reportMode())) {
            return "GROUPED LABORATORY REPORT";
        }
        return "INTERIM LABORATORY REPORT";
    }

    private String reportTypeLabel(LabOrderRecord.LabReportArtifactRecord artifact) {
        if (artifact == null) {
            return "Interim";
        }
        if (LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(artifact.reportType())) {
            return "Final";
        }
        if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL.equalsIgnoreCase(artifact.reportMode())) {
            return "Individual Interim";
        }
        if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED.equalsIgnoreCase(artifact.reportMode())) {
            return "Grouped Interim";
        }
        return "Interim";
    }

    private String[] resultRowValues(String testName, LabOrderResultRecord row) {
        String label = StringUtils.hasText(row.parameterName())
                ? testName + " / " + row.parameterName()
                : StringUtils.hasText(row.componentName())
                ? testName + " / " + row.componentName()
                : testName;
        return new String[]{
                label,
                safe(row.resultValue()),
                safe(row.unit()),
                safe(row.referenceRange()),
                interpretationLabel(row.resultFlag())
        };
    }

    private String logoFallbackText(String clinicName) {
        String value = StringUtils.hasText(clinicName) ? clinicName.trim() : "LAB";
        String[] parts = value.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(3, parts[0].length())).toUpperCase(java.util.Locale.ROOT);
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (builder.length() == 3) {
                break;
            }
        }
        return builder.length() == 0 ? "LAB" : builder.toString();
    }


    private BufferedImage loadClinicLogo(UUID tenantId, com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord clinic) {
        if (clinic == null || clinic.logoDocumentId() == null) {
            return null;
        }
        try {
            ClinicalDocumentRecord logoDocument = clinicalDocumentService.get(tenantId, clinic.logoDocumentId());
            byte[] bytes = objectStorageService.getObjectBytes(logoDocument.storageKey());
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (RuntimeException | IOException ex) {
            return null;
        }
    }

    private Optional<String> resolveUserDisplayName(UUID tenantId, UUID appUserId) {
        if (appUserId == null) {
            return Optional.empty();
        }
        return tenantUserManagementService.list(tenantId).stream()
                .filter(user -> appUserId.equals(user.appUserId()))
                .map(TenantUserRecord::displayName)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private String fallbackSampleType(LabOrderRecord record) {
        return record.items().stream()
                .map(LabOrderItemRecord::sampleType)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private LabSampleStatusRecord summarizeSampleStatus(List<LabSampleRecord> samples) {
        if (samples == null || samples.isEmpty()) {
            return null;
        }
        if (samples.stream().anyMatch(sample -> sample.status() == LabSampleStatusRecord.RECOLLECTION_REQUIRED)) {
            return LabSampleStatusRecord.RECOLLECTION_REQUIRED;
        }
        if (samples.stream().anyMatch(sample -> sample.status() == LabSampleStatusRecord.REJECTED)) {
            return LabSampleStatusRecord.REJECTED;
        }
        if (samples.stream().allMatch(sample -> sample.status() == LabSampleStatusRecord.RECEIVED)) {
            return LabSampleStatusRecord.RECEIVED;
        }
        if (samples.stream().anyMatch(sample -> sample.status() == LabSampleStatusRecord.RECEIVED)) {
            return LabSampleStatusRecord.RECEIVED;
        }
        if (samples.stream().anyMatch(sample -> sample.status() == LabSampleStatusRecord.COLLECTED)) {
            return LabSampleStatusRecord.COLLECTED;
        }
        return samples.getFirst().status();
    }

    private void ensureUsableSampleForOrderItem(List<LabOrderSampleEntity> samples, Map<UUID, SampleLinkSummary> sampleLinkSummaries, UUID labOrderItemId) {
        if (samples == null || samples.isEmpty()) {
            return;
        }
        List<LabOrderSampleEntity> relevantSamples = samples.stream()
                .filter(sample -> sampleAppliesToOrderItem(sample, sampleLinkSummaries == null ? null : sampleLinkSummaries.get(sample.getId()), labOrderItemId))
                .toList();
        if (relevantSamples.isEmpty()) {
            throw new IllegalArgumentException("A collected sample is required before result entry");
        }
        boolean hasUsable = relevantSamples.stream().anyMatch(sample -> sample.getStatus() == LabSampleStatus.COLLECTED || sample.getStatus() == LabSampleStatus.RECEIVED);
        if (!hasUsable) {
            throw new IllegalArgumentException("Result entry is blocked for a rejected or recollection-required sample");
        }
    }

    private void ensureNoResultsEnteredForSample(UUID tenantId, LabOrderSampleEntity sample, SampleLinkSummary summary) {
        List<UUID> linkedItemIds = summary == null ? List.of() : summary.linkedLabOrderItemIds();
        if (linkedItemIds.isEmpty()) {
            if (sample.getLabOrderItemId() == null) {
                if (!labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, sample.getLabOrderId()).isEmpty()) {
                    throw new IllegalArgumentException("Lab sample cannot be rejected after results are entered");
                }
                return;
            }
            linkedItemIds = List.of(sample.getLabOrderItemId());
        }
        for (UUID labOrderItemId : linkedItemIds) {
            if (!labOrderResultRepository.findByTenantIdAndLabOrderItemId(tenantId, labOrderItemId).isEmpty()) {
                throw new IllegalArgumentException("Lab sample cannot be rejected after results are entered");
            }
        }
    }

    private boolean sampleAppliesToOrderItem(LabOrderSampleEntity sample, SampleLinkSummary summary, UUID labOrderItemId) {
        if (sample == null || labOrderItemId == null) {
            return false;
        }
        if (summary != null && !summary.linkedLabOrderItemIds().isEmpty()) {
            return summary.linkedLabOrderItemIds().contains(labOrderItemId);
        }
        return sample.getLabOrderItemId() == null || sample.getLabOrderItemId().equals(labOrderItemId);
    }

    private Map<UUID, SampleLinkSummary> buildSampleLinkSummaries(UUID tenantId, UUID orderId) {
        if (tenantId == null || orderId == null) {
            return Map.of();
        }
        Map<UUID, LabOrderItemEntity> orderItems = labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, orderId).stream()
                .collect(Collectors.toMap(LabOrderItemEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<UUID, LinkedHashSet<UUID>> linkedItemIdsBySample = new LinkedHashMap<>();
        Map<UUID, LinkedHashSet<String>> linkedTestNamesBySample = new LinkedHashMap<>();
        for (LaboratoryOrderedTestView orderedTest : laboratoryWorkflowService.listOrderTests(tenantId, orderId)) {
            LabOrderItemEntity item = orderItems.get(orderedTest.labOrderItemId());
            String testName = item == null ? null : item.getTestName();
            if (orderedTest.specimenLinks() == null) {
                continue;
            }
            for (com.deepthoughtnet.clinic.laboratory.service.model.LaboratorySpecimenLinkView link : orderedTest.specimenLinks()) {
                if (link == null || link.labOrderSampleId() == null) {
                    continue;
                }
                linkedItemIdsBySample.computeIfAbsent(link.labOrderSampleId(), ignored -> new LinkedHashSet<>()).add(orderedTest.labOrderItemId());
                if (StringUtils.hasText(testName)) {
                    linkedTestNamesBySample.computeIfAbsent(link.labOrderSampleId(), ignored -> new LinkedHashSet<>()).add(testName);
                }
            }
        }
        Map<UUID, SampleLinkSummary> result = new LinkedHashMap<>();
        for (UUID sampleId : linkedItemIdsBySample.keySet()) {
            result.put(sampleId, new SampleLinkSummary(
                    new ArrayList<>(linkedItemIdsBySample.get(sampleId)),
                    new ArrayList<>(linkedTestNamesBySample.getOrDefault(sampleId, new LinkedHashSet<>()))
            ));
        }
        return result;
    }

    private Map<UUID, SampleLinkSummary> buildSampleLinkSummaries(List<LabOrderedTestRecord> orderedTests) {
        Map<UUID, LinkedHashSet<UUID>> linkedItemIdsBySample = new LinkedHashMap<>();
        Map<UUID, LinkedHashSet<String>> linkedTestNamesBySample = new LinkedHashMap<>();
        if (orderedTests != null) {
            for (LabOrderedTestRecord orderedTest : orderedTests) {
                if (orderedTest == null || orderedTest.specimenLinks() == null) {
                    continue;
                }
                for (LabOrderedTestSpecimenRecord link : orderedTest.specimenLinks()) {
                    if (link == null || link.labOrderSampleId() == null) {
                        continue;
                    }
                    linkedItemIdsBySample.computeIfAbsent(link.labOrderSampleId(), ignored -> new LinkedHashSet<>()).add(orderedTest.labOrderItemId());
                    linkedTestNamesBySample.computeIfAbsent(link.labOrderSampleId(), ignored -> new LinkedHashSet<>()).add(orderedTest.testName());
                }
            }
        }
        Map<UUID, SampleLinkSummary> result = new LinkedHashMap<>();
        for (UUID sampleId : linkedItemIdsBySample.keySet()) {
            result.put(sampleId, new SampleLinkSummary(
                    new ArrayList<>(linkedItemIdsBySample.get(sampleId)),
                    new ArrayList<>(linkedTestNamesBySample.getOrDefault(sampleId, new LinkedHashSet<>()))
            ));
        }
        return result;
    }

    private String formatDateTime(OffsetDateTime value, ZoneId zoneId) {
        if (value == null) {
            return "-";
        }
        ZoneId safeZone = zoneId == null ? ZoneId.of("UTC") : zoneId;
        return value.atZoneSameInstant(safeZone).format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a z"));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private record MetaPair(String label, String value) {
    }

    private static final class SampleCollectionGroup {
        private final String specimenType;
        private final String containerType;
        private final OffsetDateTime collectedAt;
        private final String notes;
        private final LinkedHashSet<UUID> linkedOrderItemIds = new LinkedHashSet<>();
        private boolean assignToAllOrderItems;

        private SampleCollectionGroup(String specimenType, String containerType, OffsetDateTime collectedAt, String notes) {
            this.specimenType = specimenType;
            this.containerType = containerType;
            this.collectedAt = collectedAt;
            this.notes = notes;
        }

        private boolean matches(String specimenType, String containerType, OffsetDateTime collectedAt, String notes) {
            return Objects.equals(this.specimenType, specimenType)
                    && Objects.equals(this.containerType, containerType)
                    && Objects.equals(this.collectedAt, collectedAt)
                    && Objects.equals(this.notes, notes);
        }
    }

    private record SampleLinkSummary(List<UUID> linkedLabOrderItemIds, List<String> linkedTestNames) {
        private static SampleLinkSummary empty() {
            return new SampleLinkSummary(List.of(), List.of());
        }
    }

    private record Range(BigDecimal low, boolean lowInclusive, BigDecimal high, boolean highInclusive) {
        boolean contains(BigDecimal value) {
            if (value == null) {
                return false;
            }
            if (low != null) {
                int comparison = value.compareTo(low);
                if (comparison < 0 || (comparison == 0 && !lowInclusive)) {
                    return false;
                }
            }
            if (high != null) {
                int comparison = value.compareTo(high);
                if (comparison > 0 || (comparison == 0 && !highInclusive)) {
                    return false;
                }
            }
            return true;
        }

        boolean isBelow(BigDecimal value) {
            if (value == null || low == null) {
                return false;
            }
            int comparison = value.compareTo(low);
            return comparison < 0 || (comparison == 0 && !lowInclusive);
        }

        boolean isAbove(BigDecimal value) {
            if (value == null || high == null) {
                return false;
            }
            int comparison = value.compareTo(high);
            return comparison > 0 || (comparison == 0 && !highInclusive);
        }

        boolean isCriticalLowCrossed(BigDecimal value) {
            if (value == null) {
                return false;
            }
            if (low == null && high != null) {
                int comparison = value.compareTo(high);
                return comparison < 0 || (comparison == 0 && !highInclusive);
            }
            if (low != null && high != null) {
                int comparison = value.compareTo(low);
                return comparison < 0 || (comparison == 0 && !lowInclusive);
            }
            return false;
        }

        boolean isCriticalHighCrossed(BigDecimal value) {
            if (value == null) {
                return false;
            }
            if (low != null && high == null) {
                int comparison = value.compareTo(low);
                return comparison > 0 || (comparison == 0 && !lowInclusive);
            }
            if (low != null && high != null) {
                int comparison = value.compareTo(high);
                return comparison > 0 || (comparison == 0 && !highInclusive);
            }
            return false;
        }
    }

    private record ResultFlagResolution(LabResultFlag flag, boolean critical) {
    }

    private LabTestRecord toRecord(LabTestMasterEntity entity) {
        List<LabTestParameterRecord> parameters = labTestParameterRepository.findByTenantIdAndLabTestIdOrderBySortOrderAsc(entity.getTenantId(), entity.getId()).stream()
                .map(this::toRecord)
                .toList();
        return new LabTestRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getTestCode(),
                entity.getTestName(),
                entity.getCategory(),
                entity.getDepartment(),
                entity.getSampleType(),
                entity.getUnit(),
                entity.getReferenceRange(),
                entity.getTurnaroundTime(),
                entity.getPrice(),
                entity.isEnabled(),
                entity.getTenantPriceOverride(),
                entity.getTenantTatOverride(),
                entity.getDisplayOrder(),
                entity.isActive(),
                parameters,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private LabTestParameterRecord toRecord(LabTestParameterEntity entity) {
        return new LabTestParameterRecord(
                entity.getId(),
                entity.getLabTestId(),
                entity.getParameterName(),
                entity.getUnit(),
                entity.getNormalRange(),
                entity.getCriticalRange(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<LabTestMasterEntity> loadTests(UUID tenantId, List<UUID> testIds) {
        if (testIds == null || testIds.isEmpty()) {
            throw new IllegalArgumentException("At least one test is required");
        }
        if (testIds.size() != testIds.stream().distinct().count()) {
            throw new IllegalArgumentException("Duplicate lab test selection is not allowed");
        }
        List<LabTestMasterEntity> rows = labTestMasterRepository.findAllById(testIds);
        Map<UUID, LabTestMasterEntity> byId = rows.stream().collect(Collectors.toMap(LabTestMasterEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (byId.size() != testIds.stream().distinct().count()) {
            throw new IllegalArgumentException("One or more lab tests were not found");
        }
        for (UUID testId : testIds) {
            LabTestMasterEntity test = byId.get(testId);
            if (!tenantId.equals(test.getTenantId())) {
                throw new IllegalArgumentException("Lab test does not belong to the selected tenant");
            }
            if (!test.isActive()) {
                throw new IllegalArgumentException("Lab test is inactive: " + test.getTestName());
            }
            if (!test.isEnabled()) {
                throw new IllegalArgumentException("Lab test is disabled: " + test.getTestName());
            }
            if (!labCatalogueConfigService.isCategoryActive(tenantId, test.getCategory())) {
                throw new IllegalArgumentException("Lab category is disabled for this tenant: " + test.getCategory());
            }
        }
        return testIds.stream().map(byId::get).toList();
    }

    private LabOrderRecord createOrderInternal(
            UUID tenantId,
            PatientEntity patient,
            UUID doctorUserId,
            String doctorName,
            UUID consultationId,
            LabOrderOrigin orderOrigin,
            UUID requestedByInternalDoctorId,
            String externalDoctorName,
            String externalDoctorMobile,
            String externalClinicName,
            String referralSource,
            UUID appointmentId,
            String notes,
            List<LabTestMasterEntity> tests,
            UUID actorAppUserId,
            String auditMessage
    ) {
        LabOrderEntity order = LabOrderEntity.create(
                tenantId,
                generateOrderNumber(tenantId),
                patient.getId(),
                patient.getPatientNumber(),
                patient.getFirstName() + " " + patient.getLastName(),
                doctorUserId,
                doctorName,
                consultationId,
                orderOrigin,
                requestedByInternalDoctorId,
                externalDoctorName,
                externalDoctorMobile,
                externalClinicName,
                referralSource,
                notes
        );
        LabOrderEntity savedOrder = labOrderRepository.save(order);
        int sortOrder = 1;
        List<LabOrderItemEntity> items = new ArrayList<>();
        for (LabTestMasterEntity test : tests) {
            BigDecimal effectivePrice = test.getTenantPriceOverride() != null ? test.getTenantPriceOverride() : test.getPrice();
            String effectiveTat = StringUtils.hasText(test.getTenantTatOverride()) ? test.getTenantTatOverride() : test.getTurnaroundTime();
            items.add(labOrderItemRepository.save(LabOrderItemEntity.create(
                    tenantId,
                    savedOrder.getId(),
                    test.getId(),
                    test.getTestCode(),
                    test.getTestName(),
                    test.getCategory(),
                    test.getDepartment(),
                    test.getSampleType(),
                    test.getUnit(),
                    test.getReferenceRange(),
                    effectiveTat,
                    normalizeMoney(effectivePrice),
                    sortOrder++
            )));
        }
        laboratoryWorkflowService.initializeOrderTests(
                tenantId,
                savedOrder.getId(),
                items.stream().map(LabOrderItemEntity::getId).toList(),
                actorAppUserId
        );
        List<BillLineCommand> billLines = items.stream()
                .map(item -> new BillLineCommand(
                        BillItemType.TEST,
                        item.getTestName(),
                        1,
                        item.getPrice(),
                        item.getLabTestId(),
                        item.getSortOrder(),
                        ZERO,
                        null,
                        item.getId()
                ))
                .toList();
        BillRecord bill = billingService.createDraft(tenantId, new BillUpsertCommand(
                patient.getId(),
                consultationId,
                appointmentId,
                LocalDate.now(),
                DiscountType.NONE,
                ZERO,
                null,
                null,
                null,
                ZERO,
                "Lab tests ordered",
                billLines
        ), actorAppUserId);
        BillRecord issuedBill = billingService.issue(tenantId, bill.id(), actorAppUserId);
        savedOrder.linkBill(issuedBill.id());
        savedOrder.markStatus(LabOrderStatus.PAYMENT_PENDING);
        LabOrderEntity persisted = labOrderRepository.save(savedOrder);
        synchronizeAggregateStatus(tenantId, persisted, actorAppUserId);
        auditOrder(tenantId, persisted, "lab_order.created", actorAppUserId, auditMessage);
        if (persisted.getOrderOrigin() == LabOrderOrigin.WALK_IN || persisted.getOrderOrigin() == LabOrderOrigin.DOCTOR_REFERRAL) {
            auditOrder(tenantId, persisted, "lab_order.direct_registration_created", actorAppUserId, "Created direct laboratory registration");
        }
        if (StringUtils.hasText(persisted.getExternalDoctorName()) || StringUtils.hasText(persisted.getReferralSource())) {
            auditOrder(tenantId, persisted, "lab_order.referral_captured", actorAppUserId, "Captured laboratory referral metadata");
        }
        labNotificationService.notifyOrderCreated(
                tenantId,
                patient.getId(),
                persisted.getId(),
                persisted.getOrderNumber(),
                persisted.getPatientName(),
                persisted.getDoctorName(),
                tests.stream().map(LabTestMasterEntity::getTestName).toList(),
                actorAppUserId
        );
        return toRecord(tenantId, persisted);
    }

    private void validateTest(UUID tenantId, LabTestUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        LabValidationSupport.normalizeTestCode(command.testCode(), "testCode");
        if (!StringUtils.hasText(command.testName())) {
            throw new IllegalArgumentException("testName is required");
        }
        if (normalize(command.testName()).length() > 100) {
            throw new IllegalArgumentException("testName must be 100 characters or fewer");
        }
        if (!hasLetterOrNumber(command.testName())) {
            throw new IllegalArgumentException("testName must contain letters or numbers");
        }
        if (!StringUtils.hasText(command.category())) {
            throw new IllegalArgumentException("category is required");
        }
        String category = normalizeCategory(command.category());
        if (category.length() > 30) {
            throw new IllegalArgumentException("category must be 30 characters or fewer");
        }
        if (!labCatalogueConfigService.isCategoryActive(tenantId, category)) {
            throw new IllegalArgumentException("category is inactive");
        }
        LabValidationSupport.normalizeWholeHours(command.turnaroundTime(), "turnaroundTime");
        LabValidationSupport.normalizeMoney(command.price(), "price");
        if (command.parameters() != null) {
            List<String> seen = new ArrayList<>();
            for (LabTestParameterUpsertCommand parameter : command.parameters()) {
                if (parameter != null && !StringUtils.hasText(parameter.parameterName())) {
                    throw new IllegalArgumentException("parameterName is required when parameters are provided");
                }
                if (parameter != null) {
                    String name = normalize(parameter.parameterName());
                    if (name.length() > 60) {
                        throw new IllegalArgumentException("parameterName must be 60 characters or fewer");
                    }
                    if (seen.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
                        throw new IllegalArgumentException("parameter names must be unique within a test");
                    }
                    seen.add(name);
                }
            }
        }
    }

    private void validateOrder(LabOrderCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.testIds() == null || command.testIds().isEmpty()) {
            throw new IllegalArgumentException("At least one test is required");
        }
        if (StringUtils.hasText(command.notes()) && command.notes().trim().length() > 250) {
            throw new IllegalArgumentException("notes must be 250 characters or fewer");
        }
    }

    private void validateDirectOrder(LabOrderDirectCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireId(command.patientId(), "patientId");
        if (command.orderOrigin() == null) {
            throw new IllegalArgumentException("orderOrigin is required");
        }
        if (command.testIds() == null || command.testIds().isEmpty()) {
            throw new IllegalArgumentException("At least one test is required");
        }
        if (command.orderOrigin() == LabOrderOrigin.CONSULTATION) {
            throw new IllegalArgumentException("Consultation-origin lab orders must be created from the consultation workspace");
        }
        if (command.orderOrigin() == LabOrderOrigin.DOCTOR_REFERRAL && !StringUtils.hasText(command.externalDoctorName())) {
            throw new IllegalArgumentException("externalDoctorName is required for doctor referral orders");
        }
        if (StringUtils.hasText(command.externalDoctorName()) && command.externalDoctorName().trim().length() > 256) {
            throw new IllegalArgumentException("externalDoctorName must be 256 characters or fewer");
        }
        if (StringUtils.hasText(command.externalDoctorMobile()) && command.externalDoctorMobile().trim().length() > 32) {
            throw new IllegalArgumentException("externalDoctorMobile must be 32 characters or fewer");
        }
        if (StringUtils.hasText(command.externalClinicName()) && command.externalClinicName().trim().length() > 256) {
            throw new IllegalArgumentException("externalClinicName must be 256 characters or fewer");
        }
        if (StringUtils.hasText(command.referralSource()) && command.referralSource().trim().length() > 128) {
            throw new IllegalArgumentException("referralSource must be 128 characters or fewer");
        }
        if (StringUtils.hasText(command.notes()) && command.notes().trim().length() > 250) {
            throw new IllegalArgumentException("notes must be 250 characters or fewer");
        }
    }

    private void ensureNoConsultationDuplicateOrders(UUID tenantId, UUID consultationId, List<LabTestMasterEntity> tests) {
        if (consultationId == null || tests == null || tests.isEmpty()) {
            return;
        }
        Set<UUID> requestedTestIds = tests.stream()
                .map(LabTestMasterEntity::getId)
                .collect(Collectors.toSet());
        labOrderRepository.findByTenantIdAndConsultationIdOrderByCreatedAtDesc(tenantId, consultationId).stream()
                .filter(order -> order.getStatus() != LabOrderStatus.CANCELLED)
                .filter(order -> ACTIVE_ORDER_STATUSES.contains(order.getStatus()))
                .forEach(order -> {
                    boolean duplicateExists = labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, order.getId()).stream()
                            .map(LabOrderItemEntity::getLabTestId)
                            .anyMatch(requestedTestIds::contains);
                    if (duplicateExists) {
                        throw new IllegalArgumentException("This consultation already has an active lab order for one of the selected tests");
                    }
                });
    }

    private void validatePayment(LabOrderPaymentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.amount() == null || command.amount().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("amount is required");
        }
        if (command.amount().compareTo(new BigDecimal("999999.00")) > 0) {
            throw new IllegalArgumentException("amount exceeds the allowed maximum");
        }
        if (command.paymentMode() == null) {
            throw new IllegalArgumentException("paymentMode is required");
        }
        if (command.paymentMode() != PaymentMode.CASH && !StringUtils.hasText(command.referenceNumber())) {
            throw new IllegalArgumentException("referenceNumber is required for non-cash payments");
        }
        if (StringUtils.hasText(command.referenceNumber()) && command.referenceNumber().trim().length() > 60) {
            throw new IllegalArgumentException("referenceNumber must be 60 characters or fewer");
        }
        if (StringUtils.hasText(command.notes()) && command.notes().trim().length() > 250) {
            throw new IllegalArgumentException("notes must be 250 characters or fewer");
        }
    }

    private void ensureUniqueTestCode(UUID tenantId, String testCode, UUID currentId) {
        labTestMasterRepository.findByTenantIdAndTestCodeIgnoreCase(tenantId, normalize(testCode))
                .filter(entity -> currentId == null || !currentId.equals(entity.getId()))
                .ifPresent(entity -> {
                    throw new IllegalArgumentException("Lab test code already exists");
                });
    }

    private void ensureUniqueTestName(UUID tenantId, String testName, UUID currentId) {
        labTestMasterRepository.findByTenantIdAndTestNameIgnoreCase(tenantId, normalize(testName))
                .filter(entity -> currentId == null || !currentId.equals(entity.getId()))
                .ifPresent(entity -> {
                    throw new IllegalArgumentException("Lab test already exists");
                });
    }

    private boolean matches(LabTestMasterEntity entity, String term) {
        return contains(entity.getTestCode(), term)
                || contains(entity.getTestName(), term)
                || contains(entity.getCategory(), term)
                || contains(entity.getDepartment(), term)
                || contains(entity.getSampleType(), term)
                || contains(entity.getTurnaroundTime(), term);
    }

    private boolean matches(UUID tenantId, LabOrderEntity entity, String term) {
        return contains(entity.getOrderNumber(), term)
                || contains(entity.getPatientNumber(), term)
                || contains(entity.getPatientName(), term)
                || contains(entity.getDoctorName(), term)
                || contains(entity.getNotes(), term)
                || labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, entity.getId()).stream()
                        .anyMatch(item -> contains(item.getTestCode(), term) || contains(item.getTestName(), term) || contains(item.getCategory(), term));
    }

    private boolean contains(String value, String term) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(term);
    }

    private String normalizeSearch(String search) {
        return StringUtils.hasText(search) ? search.trim().toLowerCase() : null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateSampleCollection(LabOrderSampleCollectionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        validateSampleTimestamp(command.collectedAt(), "collectedAt");
        if (StringUtils.hasText(command.sampleType()) && command.sampleType().trim().length() > 60) {
            throw new IllegalArgumentException("sampleType must be 60 characters or fewer");
        }
        if (StringUtils.hasText(command.notes()) && command.notes().trim().length() > 250) {
            throw new IllegalArgumentException("notes must be 250 characters or fewer");
        }
    }

    private void validateSampleCollection(LabSampleCollectionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (!StringUtils.hasText(command.specimenType())) {
            throw new IllegalArgumentException("specimenType is required");
        }
        if (normalize(command.specimenType()).length() > 128) {
            throw new IllegalArgumentException("specimenType must be 128 characters or fewer");
        }
        if (StringUtils.hasText(command.containerType()) && command.containerType().trim().length() > 128) {
            throw new IllegalArgumentException("containerType must be 128 characters or fewer");
        }
        validateSampleTimestamp(command.collectedAt(), "collectedAt");
        if (StringUtils.hasText(command.notes()) && command.notes().trim().length() > 1000) {
            throw new IllegalArgumentException("notes must be 1000 characters or fewer");
        }
    }

    private void validateSampleRejection(LabSampleRejectCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (!StringUtils.hasText(command.rejectionReason())) {
            throw new IllegalArgumentException("rejectionReason is required");
        }
        if (normalize(command.rejectionReason()).length() > 128) {
            throw new IllegalArgumentException("rejectionReason must be 128 characters or fewer");
        }
        if (StringUtils.hasText(command.notes()) && command.notes().trim().length() > 1000) {
            throw new IllegalArgumentException("notes must be 1000 characters or fewer");
        }
    }

    private void validateSampleTimestamp(OffsetDateTime value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("collection date cannot be in the future");
        }
    }

    private void validateResults(LabOrderResultEntryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("At least one result item is required");
        }
        for (LabOrderResultItemCommand item : command.items()) {
            if (item == null || item.labOrderItemId() == null) {
                throw new IllegalArgumentException("labOrderItemId is required");
            }
            boolean hasValue = StringUtils.hasText(item.resultValue());
            boolean hasComponents = item.componentResults() != null && !item.componentResults().isEmpty();
            if (!hasValue && !hasComponents) {
                throw new IllegalArgumentException("Each result item requires a value or component results");
            }
            if (hasValue && item.resultValue().trim().length() > 120) {
                throw new IllegalArgumentException("resultValue must be 120 characters or fewer");
            }
            if (StringUtils.hasText(item.unit()) && item.unit().trim().length() > 30) {
                throw new IllegalArgumentException("unit must be 30 characters or fewer");
            }
            if (StringUtils.hasText(item.referenceRange()) && item.referenceRange().trim().length() > 120) {
                throw new IllegalArgumentException("referenceRange must be 120 characters or fewer");
            }
            if (hasComponents) {
                for (LabOrderResultComponentCommand component : item.componentResults()) {
                    if (component == null) {
                        continue;
                    }
                    if (!StringUtils.hasText(component.resultValue())) {
                        throw new IllegalArgumentException("Each component result requires a value");
                    }
                    if (StringUtils.hasText(component.resultValue()) && component.resultValue().trim().length() > 120) {
                        throw new IllegalArgumentException("component resultValue must be 120 characters or fewer");
                    }
                    if (StringUtils.hasText(component.parameterName()) && component.parameterName().trim().length() > 60) {
                        throw new IllegalArgumentException("parameterName must be 60 characters or fewer");
                    }
                    if (StringUtils.hasText(component.componentName()) && component.componentName().trim().length() > 60) {
                        throw new IllegalArgumentException("componentName must be 60 characters or fewer");
                    }
                    if (StringUtils.hasText(component.unit()) && component.unit().trim().length() > 30) {
                        throw new IllegalArgumentException("unit must be 30 characters or fewer");
                    }
                    if (StringUtils.hasText(component.referenceRange()) && component.referenceRange().trim().length() > 120) {
                        throw new IllegalArgumentException("referenceRange must be 120 characters or fewer");
                    }
                }
            }
        }
        if (StringUtils.hasText(command.comments()) && command.comments().trim().length() > 250) {
            throw new IllegalArgumentException("comments must be 250 characters or fewer");
        }
    }

    private void validateReview(LabOrderDoctorReviewCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (!StringUtils.hasText(command.decision())) {
            throw new IllegalArgumentException("decision is required");
        }
        String decision = normalizeReviewDecision(command.decision());
        if (!"APPROVE".equals(decision) && !"SEND_BACK".equals(decision)) {
            throw new IllegalArgumentException("decision must be APPROVE or SEND_BACK");
        }
        if ("SEND_BACK".equals(decision)) {
            if (!StringUtils.hasText(command.reason())) {
                throw new IllegalArgumentException("reason is required when sending back a result");
            }
            if (!StringUtils.hasText(command.doctorComments())) {
                throw new IllegalArgumentException("remarks are required when sending back a result");
            }
        }
        if (StringUtils.hasText(command.reason()) && command.reason().trim().length() > 60) {
            throw new IllegalArgumentException("reason must be 60 characters or fewer");
        }
        if (StringUtils.hasText(command.doctorComments()) && command.doctorComments().trim().length() > 250) {
            throw new IllegalArgumentException("remarks must be 250 characters or fewer");
        }
    }

    private void validateVerification(LabOrderVerificationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (!StringUtils.hasText(command.decision())) {
            throw new IllegalArgumentException("decision is required");
        }
        String decision = normalizeReviewDecision(command.decision());
        if (!"APPROVE".equals(decision) && !"SEND_BACK".equals(decision)) {
            throw new IllegalArgumentException("decision must be APPROVE or SEND_BACK");
        }
        if ("SEND_BACK".equals(decision) && !StringUtils.hasText(command.reason())) {
            throw new IllegalArgumentException("reason is required when sending results back");
        }
        if (StringUtils.hasText(command.reason()) && command.reason().trim().length() > 128) {
            throw new IllegalArgumentException("reason must be 128 characters or fewer");
        }
        if (StringUtils.hasText(command.comments()) && command.comments().trim().length() > 1000) {
            throw new IllegalArgumentException("comments must be 1000 characters or fewer");
        }
    }

    private void saveParameters(UUID tenantId, UUID labTestId, List<LabTestParameterUpsertCommand> commands) {
        labTestParameterRepository.deleteByTenantIdAndLabTestId(tenantId, labTestId);
        labTestParameterRepository.flush();
        if (commands == null || commands.isEmpty()) {
            return;
        }
        int sortOrder = 1;
        List<LabTestParameterEntity> entities = new ArrayList<>();
        for (LabTestParameterUpsertCommand command : commands) {
            if (command == null) {
                continue;
            }
            if (!StringUtils.hasText(command.parameterName())) {
                throw new IllegalArgumentException("parameterName is required when parameters are provided");
            }
            entities.add(LabTestParameterEntity.create(
                    tenantId,
                    labTestId,
                    normalize(command.parameterName()),
                    normalizeNullable(command.unit()),
                    normalizeNullable(command.normalRange()),
                    normalizeNullable(command.criticalRange()),
                    command.sortOrder() > 0 ? command.sortOrder() : sortOrder
            ));
            sortOrder++;
        }
        labTestParameterRepository.saveAll(entities);
    }

    private IllegalArgumentException duplicateTestException(LabTestUpsertCommand command, DataIntegrityViolationException ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? "" : root.getMessage().toLowerCase();
        if (message.contains("uq_lab_tests_tenant_code") || message.contains("uq_lab_tests_tenant_code_ci") || message.contains("test_code")) {
            return new IllegalArgumentException("Lab test code already exists");
        }
        if (message.contains("uq_lab_tests_tenant_name") || message.contains("uq_lab_tests_tenant_name_ci") || message.contains("test_name")) {
            return new IllegalArgumentException("Lab test already exists");
        }
        if (command.parameters() != null && !command.parameters().isEmpty()) {
            return new IllegalArgumentException("parameter names must be unique within a test");
        }
        return new IllegalArgumentException("Lab test already exists");
    }

    private List<LabTestParameterRecord> resolveParameters(UUID tenantId, LabOrderItemEntity item) {
        if (item.getLabTestId() == null) {
            return List.of();
        }
        return labTestParameterRepository.findByTenantIdAndLabTestIdOrderBySortOrderAsc(tenantId, item.getLabTestId()).stream()
                .map(this::toRecord)
                .toList();
    }

    private LabTestParameterEntity findParameter(List<LabTestParameterEntity> parameters, String parameterName, String componentName) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        String term = firstText(parameterName, componentName);
        if (!StringUtils.hasText(term)) {
            return parameters.get(0);
        }
        return parameters.stream()
                .filter(parameter -> parameter.getParameterName() != null && parameter.getParameterName().equalsIgnoreCase(term))
                .findFirst()
                .orElse(parameters.get(0));
    }

    private LabTestParameterEntity firstParameter(List<LabTestParameterEntity> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        return parameters.get(0);
    }

    private ResultFlagResolution resolveResultFlag(String value, String normalRange, String criticalRange) {
        BigDecimal numeric = parseNumeric(value);
        if (numeric == null) {
            return new ResultFlagResolution(LabResultFlag.NORMAL, false);
        }
        Range normal = parseRange(normalRange);
        if (normal != null && normal.contains(numeric)) {
            return new ResultFlagResolution(LabResultFlag.NORMAL, false);
        }
        Range critical = parseRange(criticalRange);
        if (normal == null) {
            if (critical != null) {
                if (critical.isCriticalLowCrossed(numeric)) {
                    return new ResultFlagResolution(LabResultFlag.CRITICAL_LOW, true);
                }
                if (critical.isCriticalHighCrossed(numeric)) {
                    return new ResultFlagResolution(LabResultFlag.CRITICAL_HIGH, true);
                }
            }
            return new ResultFlagResolution(LabResultFlag.NORMAL, false);
        }
        if (normal.isBelow(numeric)) {
            if (critical != null && critical.isCriticalLowCrossed(numeric)) {
                return new ResultFlagResolution(LabResultFlag.CRITICAL_LOW, true);
            }
            return new ResultFlagResolution(LabResultFlag.LOW, false);
        }
        if (normal.isAbove(numeric)) {
            if (critical != null && critical.isCriticalHighCrossed(numeric)) {
                return new ResultFlagResolution(LabResultFlag.CRITICAL_HIGH, true);
            }
            return new ResultFlagResolution(LabResultFlag.HIGH, false);
        }
        return new ResultFlagResolution(LabResultFlag.NORMAL, false);
    }

    private void ensureNumericResultWhenRangesPresent(String value, String normalRange, String criticalRange) {
        boolean numericExpected = parseRange(normalRange) != null || parseRange(criticalRange) != null;
        if (!numericExpected) {
            return;
        }
        if (parseNumeric(value) == null) {
            throw new IllegalArgumentException("Numeric result value is required for ranged lab parameters");
        }
    }

    private BigDecimal parseNumeric(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("[^0-9.+\\-]", "");
        if (normalized.isBlank() || normalized.equals("+") || normalized.equals("-") || normalized.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Range parseRange(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace(" ", "");
        if (normalized.startsWith("<=")) {
            BigDecimal high = parseNumeric(normalized.substring(2));
            return high == null ? null : new Range(null, false, high, true);
        }
        if (normalized.startsWith("<")) {
            BigDecimal high = parseNumeric(normalized.substring(1));
            return high == null ? null : new Range(null, false, high, false);
        }
        if (normalized.startsWith(">=")) {
            BigDecimal low = parseNumeric(normalized.substring(2));
            return low == null ? null : new Range(low, true, null, false);
        }
        if (normalized.startsWith(">")) {
            BigDecimal low = parseNumeric(normalized.substring(1));
            return low == null ? null : new Range(low, false, null, false);
        }
        if (normalized.contains("-")) {
            String[] parts = normalized.split("-", 2);
            BigDecimal low = parseNumeric(parts[0]);
            BigDecimal high = parseNumeric(parts[1]);
            if (low != null && high != null) {
                return new Range(low.min(high), true, low.max(high), true);
            }
        }
        BigDecimal exact = parseNumeric(normalized);
        return exact == null ? null : new Range(exact, true, exact, true);
    }

    private LabOrderAttachmentRecord toRecord(LabOrderAttachmentEntity entity) {
        return new LabOrderAttachmentRecord(
                entity.getId(),
                entity.getLabOrderId(),
                entity.getAttachmentType(),
                entity.getOriginalFilename(),
                entity.getMediaType(),
                entity.getStorageKey(),
                entity.getSizeBytes(),
                entity.getChecksumSha256(),
                entity.getDicomMetadataJson(),
                entity.getUploadedByUserId(),
                entity.getCreatedAt()
        );
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String safeFilename(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().replaceAll("[^A-Za-z0-9._-]", "-") : "lab-report";
        return normalized.replaceAll("-+", "-");
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String normalizeCategory(String value) {
        return LabCategoryCatalog.normalize(value);
    }

    private String normalizeReviewDecision(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private boolean hasLetterOrNumber(String value) {
        return StringUtils.hasText(value) && value.chars().anyMatch(Character::isLetterOrDigit);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void auditTest(UUID tenantId, LabTestMasterEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, TEST_ENTITY_TYPE, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditOrder(UUID tenantId, LabOrderEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, ORDER_ENTITY_TYPE, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditSample(UUID tenantId, LabOrderSampleEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, SAMPLE_ENTITY_TYPE, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private ResultEntryAuditMetadata resolveResultEntryAudit(UUID tenantId, UUID orderId) {
        if (tenantId == null || orderId == null || auditEventQueryService == null) {
            return null;
        }
        List<AuditEventRecord> events = auditEventQueryService.listForEntity(tenantId, ORDER_ENTITY_TYPE, orderId);
        if (events == null || events.isEmpty()) {
            return null;
        }
        return events.stream()
                .filter(event -> isResultEntryAction(event.action()))
                .max(Comparator.comparing(AuditEventRecord::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(event -> new ResultEntryAuditMetadata(
                        event.actorAppUserId(),
                        event.actorAppUserId() == null ? null : resolveUserDisplayName(tenantId, event.actorAppUserId()).orElse(null),
                        event.occurredAt()
                ))
                .orElse(null);
    }

    private String detailsJson(LabTestMasterEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("testCode", entity.getTestCode());
        details.put("testName", entity.getTestName());
        details.put("category", entity.getCategory());
        details.put("active", entity.isActive());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private String detailsJson(LabOrderEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("orderNumber", entity.getOrderNumber());
        details.put("patientId", entity.getPatientId());
        details.put("orderOrigin", entity.getOrderOrigin());
        details.put("consultationId", entity.getConsultationId());
        details.put("requestedByInternalDoctorId", entity.getRequestedByInternalDoctorId());
        details.put("externalDoctorName", entity.getExternalDoctorName());
        details.put("externalClinicName", entity.getExternalClinicName());
        details.put("referralSource", entity.getReferralSource());
        details.put("status", entity.getStatus());
        details.put("billId", entity.getBillId());
        details.put("labVerifiedAt", entity.getLabVerifiedAt());
        details.put("labVerifiedBy", entity.getLabVerifiedBy());
        details.put("labVerificationDecision", entity.getLabVerificationDecision());
        details.put("labVerificationReason", entity.getLabVerificationReason());
        details.put("reportPublishedAt", entity.getReportPublishedAt());
        details.put("reportPublishedByUserId", entity.getReportPublishedByUserId());
        details.put("reportDeliveryStatus", entity.getReportDeliveryStatus());
        details.put("reportDeliveryChannels", entity.getReportDeliveryChannels());
        details.put("reportDeliveryNotes", entity.getReportDeliveryNotes());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private void synchronizeAggregateStatus(UUID tenantId, LabOrderEntity order, UUID actorAppUserId) {
        if (tenantId == null || order == null) {
            return;
        }
        String derived = laboratoryWorkflowService.deriveAggregateOrderState(tenantId, order.getId());
        LabOrderStatus target = toOrderStatus(derived);
        if (target != null && target != order.getStatus()) {
            if (target == LabOrderStatus.ORDERED && order.getStatus() != LabOrderStatus.ORDERED) {
                return;
            }
            order.markStatus(target);
            labOrderRepository.save(order);
        }
    }

    private List<UUID> selectEligibleTestsForVerification(UUID tenantId, UUID orderId, List<LaboratoryOrderedTestView> lifecycleViews) {
        List<UUID> eligibleFromLifecycle = labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, orderId).stream()
                .map(LabOrderItemEntity::getId)
                .filter(itemId -> {
                    String state = lifecycleViews.stream()
                            .filter(view -> itemId.equals(view.labOrderItemId()))
                            .map(LaboratoryOrderedTestView::state)
                            .findFirst()
                            .orElse(null);
                    return isReviewEligibleTestState(state);
                })
                .toList();
        if (!eligibleFromLifecycle.isEmpty() || lifecycleViews != null && !lifecycleViews.isEmpty()) {
            return eligibleFromLifecycle;
        }
        return labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, orderId).stream()
                .map(LabOrderResultEntity::getLabOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<UUID> validateSelectedTestsForVerification(List<UUID> selectedTestIds, Map<UUID, LaboratoryOrderedTestView> lifecycleByItemId) {
        for (UUID selectedTestId : selectedTestIds) {
            LaboratoryOrderedTestView lifecycle = lifecycleByItemId.get(selectedTestId);
            if (lifecycle == null) {
                throw new IllegalArgumentException("Selected test is not part of this order");
            }
            String state = lifecycle.state();
            if ("VERIFIED".equalsIgnoreCase(state)) {
                throw new IllegalArgumentException("Selected test is already verified");
            }
            if (isPublishedTestState(state)) {
                throw new IllegalArgumentException("Selected test is already published");
            }
            if (!isReviewEligibleTestState(state)) {
                throw new IllegalArgumentException("Selected test is not ready for review");
            }
        }
        return selectedTestIds;
    }

    private boolean isReviewEligibleTestState(String state) {
        return "RESULT_ENTERED".equalsIgnoreCase(state) || "PENDING_REVIEW".equalsIgnoreCase(state);
    }

    private List<UUID> selectEditableTestsForResultEntry(Collection<LabOrderItemEntity> orderItems, List<LaboratoryOrderedTestView> lifecycleViews) {
        if (orderItems == null || orderItems.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> stateByItemId = lifecycleViews == null ? Map.of() : lifecycleViews.stream()
                .filter(view -> view.labOrderItemId() != null)
                .collect(Collectors.toMap(
                        LaboratoryOrderedTestView::labOrderItemId,
                        view -> view.state(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<UUID> editableFromLifecycle = orderItems.stream()
                .map(LabOrderItemEntity::getId)
                .filter(itemId -> isEditableResultEntryState(stateByItemId.get(itemId)))
                .toList();
        if (!editableFromLifecycle.isEmpty() || lifecycleViews != null && !lifecycleViews.isEmpty()) {
            return editableFromLifecycle;
        }
        return orderItems.stream().map(LabOrderItemEntity::getId).toList();
    }

    private List<UUID> selectEditableTestsForResultEntry(
            Collection<LabOrderItemEntity> orderItems,
            List<LaboratoryOrderedTestView> lifecycleViews,
            UUID labOrderSampleId,
            LabOrderSampleEntity scopedSample,
            Map<UUID, SampleLinkSummary> sampleLinkSummaries
    ) {
        if (orderItems == null || orderItems.isEmpty() || labOrderSampleId == null) {
            return List.of();
        }
        Set<UUID> linkedItemIds = new LinkedHashSet<>();
        SampleLinkSummary summary = sampleLinkSummaries == null ? null : sampleLinkSummaries.get(labOrderSampleId);
        if (summary != null && summary.linkedLabOrderItemIds() != null) {
            linkedItemIds.addAll(summary.linkedLabOrderItemIds());
        }
        if (linkedItemIds.isEmpty()) {
            if (scopedSample != null && scopedSample.getLabOrderItemId() != null) {
                linkedItemIds.add(scopedSample.getLabOrderItemId());
            }
        }
        if (lifecycleViews == null || lifecycleViews.isEmpty()) {
            return new ArrayList<>(linkedItemIds);
        }
        Map<UUID, String> stateByItemId = lifecycleViews == null ? Map.of() : lifecycleViews.stream()
                .filter(view -> view.labOrderItemId() != null)
                .collect(Collectors.toMap(
                        LaboratoryOrderedTestView::labOrderItemId,
                        view -> view.state(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return linkedItemIds.stream()
                .filter(itemId -> isEditableResultEntryState(stateByItemId.get(itemId)))
                .toList();
    }

    private List<UUID> validateSelectedTestsForResultEntry(List<UUID> selectedTestIds, Map<UUID, LaboratoryOrderedTestView> lifecycleByItemId) {
        for (UUID selectedTestId : selectedTestIds) {
            LaboratoryOrderedTestView lifecycle = lifecycleByItemId.get(selectedTestId);
            if (lifecycle == null) {
                throw new IllegalArgumentException("Selected test is not part of this order");
            }
            String state = lifecycle.state();
            if ("VERIFIED".equalsIgnoreCase(state)) {
                throw new IllegalArgumentException("Selected test is already verified");
            }
            if ("PUBLISHED".equalsIgnoreCase(state) || "REPORT_READY".equalsIgnoreCase(state) || "REPORT_GENERATED".equalsIgnoreCase(state) || "DELIVERED".equalsIgnoreCase(state)) {
                throw new IllegalArgumentException("Selected test is already published");
            }
            if ("RECOLLECTION_REQUIRED".equalsIgnoreCase(state)) {
                throw new IllegalArgumentException("Selected test requires recollection before result entry");
            }
            if (!isEditableResultEntryState(state)) {
                throw new IllegalArgumentException("Selected test is not ready for result entry");
            }
        }
        return selectedTestIds;
    }

    private boolean isEditableResultEntryState(String state) {
        return "ORDERED".equalsIgnoreCase(state)
                || "SAMPLE_COLLECTED".equalsIgnoreCase(state)
                || "RESULT_ENTERED".equalsIgnoreCase(state)
                || "RESULT_SENT_BACK".equalsIgnoreCase(state)
                || "PENDING_REVIEW".equalsIgnoreCase(state)
                || "RESULT_DRAFT".equalsIgnoreCase(state);
    }

    private boolean isPublishedTestState(String state) {
        return "REPORT_READY".equalsIgnoreCase(state)
                || "PARTIALLY_READY".equalsIgnoreCase(state)
                || "PARTIALLY_PUBLISHED".equalsIgnoreCase(state)
                || "REPORT_GENERATED".equalsIgnoreCase(state)
                || "DELIVERED".equalsIgnoreCase(state)
                || "PUBLISHED".equalsIgnoreCase(state);
    }

    private List<UUID> selectPublishableTestsForReportPublishing(List<LaboratoryOrderedTestView> lifecycleViews) {
        if (lifecycleViews == null || lifecycleViews.isEmpty()) {
            return List.of();
        }
        return lifecycleViews.stream()
                .filter(view -> isReportComposableTestState(view.state()))
                .map(LaboratoryOrderedTestView::labOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<UUID> validateSelectedTestsForReportPublishing(List<UUID> selectedTestIds, Map<UUID, LaboratoryOrderedTestView> lifecycleByItemId) {
        for (UUID selectedTestId : selectedTestIds) {
            LaboratoryOrderedTestView lifecycle = lifecycleByItemId.get(selectedTestId);
            if (lifecycle == null) {
                throw new IllegalArgumentException("Selected test is not part of this order");
            }
            String state = lifecycle.state();
            if (!isReportComposableTestState(state)) {
                throw new IllegalArgumentException("Selected test is not ready for report publishing");
            }
        }
        return selectedTestIds;
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

    private String deriveReportModeForPublication(List<LaboratoryOrderedTestView> lifecycleViews, List<UUID> publishableTestIds) {
        int count = publishableTestIds == null ? 0 : publishableTestIds.size();
        if (count <= 1) {
            return LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL;
        }
        Set<UUID> selected = publishableTestIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> total = lifecycleViews == null ? Set.of() : lifecycleViews.stream()
                .map(LaboratoryOrderedTestView::labOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!total.isEmpty() && selected.containsAll(total)) {
            return LaboratoryReportPublicationArtifactEntity.REPORT_MODE_CONSOLIDATED;
        }
        return LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED;
    }

    private String deriveReportTypeForPublication(List<LaboratoryOrderedTestView> lifecycleViews, List<UUID> publishableTestIds) {
        Set<UUID> selected = publishableTestIds == null ? Set.of() : publishableTestIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (lifecycleViews == null || lifecycleViews.isEmpty()) {
            return LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM;
        }
        Set<UUID> total = lifecycleViews.stream()
                .map(LaboratoryOrderedTestView::labOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> alreadyPublished = lifecycleViews.stream()
                .filter(view -> "PUBLISHED".equalsIgnoreCase(view.state())
                        || "REPORT_GENERATED".equalsIgnoreCase(view.state())
                        || "DELIVERED".equalsIgnoreCase(view.state()))
                .map(LaboratoryOrderedTestView::labOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (selected.isEmpty()) {
            return LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM;
        }
        Set<UUID> finalSelection = new HashSet<>(alreadyPublished);
        finalSelection.addAll(selected);
        if (finalSelection.containsAll(total)) {
            return LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL;
        }
        return LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM;
    }

    private LaboratoryReportArtifactView legacyReportArtifactView(LabOrderEntity order, String verificationToken) {
        if (order == null) {
            return null;
        }
        List<UUID> selectedItemIds = latestPublicationSelection(order.getTenantId(), order.getId());
        String verificationUrl = verificationUrl(order.getTenantId(), toRecord(order.getTenantId(), order));
        String reportMode = selectedItemIds.size() <= 1
                ? LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL
                : LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED;
        return new LaboratoryReportArtifactView(
                order.getId(),
                order.getTenantId(),
                order.getId(),
                1,
                reportMode,
                null,
                order.getStatus() == LabOrderStatus.REPORT_READY
                        ? LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_CURRENT
                        : LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_HISTORICAL,
                order.getReportFilename(),
                order.getReportFilename(),
                verificationToken,
                verificationUrl,
                order.getReportDeliveryChannels() == null || order.getReportDeliveryChannels().isBlank()
                        ? List.of()
                        : Arrays.stream(order.getReportDeliveryChannels().split(","))
                                .map(String::trim)
                                .filter(StringUtils::hasText)
                                .toList(),
                selectedItemIds,
                order.getReportGeneratedAt(),
                order.getReportGeneratedByUserId(),
                order.getReportPublishedAt(),
                order.getReportPublishedByUserId(),
                null,
                null,
                order.getReportDeliveryNotes()
        );
    }

    private LabOrderStatus toOrderStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LabOrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String serializeResultSnapshot(List<LabOrderResultEntity> results) {
        List<Map<String, Object>> snapshot = results == null ? List.of() : results.stream().map(result -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resultId", result.getId());
            row.put("testCode", result.getTestCode());
            row.put("testName", result.getTestName());
            row.put("parameterName", result.getParameterName());
            row.put("componentName", result.getComponentName());
            row.put("resultValue", result.getResultValue());
            row.put("unit", result.getUnit());
            row.put("referenceRange", result.getReferenceRange());
            row.put("sortOrder", result.getSortOrder());
            row.put("resultFlag", result.getResultFlag());
            row.put("criticalResult", result.isCriticalResult());
            row.put("createdAt", result.getCreatedAt());
            return row;
        }).toList();
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<UUID> latestPublicationSelection(UUID tenantId, UUID orderId) {
        return laboratoryWorkflowService.latestPublicationSelection(tenantId, orderId);
    }

    private LabOrderRecord.LabReportArtifactRecord currentReportArtifact(LabOrderRecord record) {
        if (record == null || record.reportArtifacts() == null || record.reportArtifacts().isEmpty()) {
            return null;
        }
        return record.reportArtifacts().stream()
                .filter(artifact -> artifact != null && LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_CURRENT.equalsIgnoreCase(artifact.reportStatus()))
                .max(Comparator.comparingInt(LabOrderRecord.LabReportArtifactRecord::versionNumber))
                .orElse(record.reportArtifacts().getFirst());
    }

    private List<LabOrderRecord.LabReportArtifactRecord> reportArtifacts(UUID tenantId, UUID orderId) {
        return safeList(laboratoryWorkflowService.listReportArtifacts(tenantId, orderId)).stream()
                .map(this::toRecord)
                .toList();
    }

    private boolean hasIdenticalCurrentReport(
            UUID tenantId,
            UUID orderId,
            String reportMode,
            String reportType,
            List<UUID> selectedTestIds
    ) {
        Set<UUID> selected = selectedTestIds == null
                ? Set.of()
                : selectedTestIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return safeList(laboratoryWorkflowService.listReportArtifacts(tenantId, orderId)).stream()
                .filter(artifact -> artifact != null
                        && LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_CURRENT.equalsIgnoreCase(artifact.reportStatus()))
                .max(Comparator.comparingInt(LaboratoryReportArtifactView::artifactNumber))
                .map(artifact -> LaboratoryReportPublicationArtifactEntity.REPORT_MODE_CONSOLIDATED.equalsIgnoreCase(reportMode)
                        && LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(reportType)
                        && LaboratoryReportPublicationArtifactEntity.REPORT_MODE_CONSOLIDATED.equalsIgnoreCase(artifact.reportMode())
                        && LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(artifact.reportType())
                        && selected.equals(artifact.selectedItemIds() == null
                        ? Set.of()
                        : artifact.selectedItemIds().stream().filter(Objects::nonNull).collect(Collectors.toSet())))
                .orElse(false);
    }

    private void validateReportModeSelection(String reportMode, List<UUID> selectedTestIds) {
        int selectedCount = selectedTestIds == null ? 0 : selectedTestIds.size();
        if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_INDIVIDUAL.equalsIgnoreCase(reportMode)
                && selectedCount != 1) {
            throw new IllegalArgumentException("Individual reports require exactly one selected test");
        }
        if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_GROUPED.equalsIgnoreCase(reportMode)
                && selectedCount < 2) {
            throw new IllegalArgumentException("Grouped reports require at least two selected tests");
        }
    }

    private boolean shouldNewArtifactBecomeCurrent(
            UUID tenantId,
            UUID orderId,
            String reportMode,
            String reportType
    ) {
        List<LaboratoryReportArtifactView> artifacts = safeList(laboratoryWorkflowService.listReportArtifacts(tenantId, orderId));
        Optional<LaboratoryReportArtifactView> current = artifacts.stream()
                .filter(artifact -> artifact != null
                        && LaboratoryReportPublicationArtifactEntity.REPORT_STATUS_CURRENT.equalsIgnoreCase(artifact.reportStatus()))
                .max(Comparator.comparingInt(LaboratoryReportArtifactView::artifactNumber));
        if (current.isEmpty()) {
            return true;
        }
        if (LaboratoryReportPublicationArtifactEntity.REPORT_MODE_CONSOLIDATED.equalsIgnoreCase(reportMode)
                && LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(reportType)) {
            return true;
        }
        return LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_INTERIM.equalsIgnoreCase(current.get().reportType())
                && LaboratoryReportPublicationArtifactEntity.REPORT_TYPE_FINAL.equalsIgnoreCase(reportType);
    }

    private LabOrderRecord.LabReportArtifactRecord toRecord(LaboratoryReportArtifactView artifact) {
        if (artifact == null) {
            return null;
        }
        return new LabOrderRecord.LabReportArtifactRecord(
                artifact.id(),
                artifact.artifactNumber(),
                artifact.reportMode(),
                artifact.reportType(),
                artifact.reportStatus(),
                artifact.filename(),
                artifact.storageReference(),
                artifact.verificationToken(),
                artifact.verificationUrl(),
                artifact.deliveryChannels() == null ? List.of() : List.copyOf(artifact.deliveryChannels()),
                artifact.selectedItemIds() == null ? List.of() : List.copyOf(artifact.selectedItemIds()),
                artifact.generatedAt(),
                artifact.generatedBy(),
                artifact.publishedAt(),
                artifact.publishedBy(),
                artifact.supersededByArtifactId(),
                artifact.supersededAt(),
                artifact.notes()
        );
    }

    private boolean isResultEntryAction(String action) {
        if (!StringUtils.hasText(action)) {
            return false;
        }
        String normalized = action.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("lab_order.results_entered")
                || normalized.equals("lab_order.results_updated_before_verification");
    }

    private record ResultEntryAuditMetadata(UUID actorAppUserId, String actorDisplayName, OffsetDateTime occurredAt) {
    }

    private void notifyRequestingDoctor(UUID tenantId, LabOrderEntity order, UUID actorAppUserId) {
        UUID doctorUserId = order.getRequestedByInternalDoctorId() != null ? order.getRequestedByInternalDoctorId() : order.getDoctorUserId();
        if (doctorUserId == null) {
            return;
        }
        tenantUserManagementService.list(tenantId).stream()
                .filter(user -> doctorUserId.equals(user.appUserId()))
                .findFirst()
                .ifPresent(user -> labNotificationService.notifyDoctorReportPublished(
                tenantId,
                order.getId(),
                user.appUserId(),
                user.email(),
                order.getOrderNumber(),
                order.getPatientName(),
                user.displayName(),
                actorAppUserId
                ));
    }

    private List<String> normalizeDeliveryChannels(List<String> channels) {
        if (channels == null) {
            return List.of();
        }
        return channels.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    private String serializeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<String> parseDeliveryChannels(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of(value);
        }
    }

    private String detailsJson(LabOrderSampleEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("labOrderId", entity.getLabOrderId());
        details.put("labOrderItemId", entity.getLabOrderItemId());
        details.put("accessionNumber", entity.getAccessionNumber());
        details.put("barcodeValue", entity.getBarcodeValue());
        details.put("specimenType", entity.getSpecimenType());
        details.put("status", entity.getStatus());
        details.put("rejectionReason", entity.getRejectionReason());
        details.put("recollectionRequired", entity.isRecollectionRequired());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private String generateOrderNumber(UUID tenantId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = "LAB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            if (labOrderRepository.findByTenantIdAndOrderNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate lab order number");
    }

    private String generateAccessionNumber(UUID tenantId, Set<String> reservedAccessions) {
        String datePortion = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "LAB-" + datePortion + "-";
        int nextSequence = findNextAccessionSequence(tenantId, prefix);
        for (int sequence = nextSequence; sequence <= 9999; sequence++) {
            String candidate = prefix + String.format("%04d", sequence);
            if ((reservedAccessions == null || !reservedAccessions.contains(candidate))
                    && labOrderSampleRepository.findByTenantIdAndAccessionNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate lab accession number");
    }

    private int findNextAccessionSequence(UUID tenantId, String accessionPrefix) {
        return labOrderSampleRepository.findFirstByTenantIdAndAccessionNumberStartingWithOrderByAccessionNumberDesc(tenantId, accessionPrefix)
                .map(LabOrderSampleEntity::getAccessionNumber)
                .map(this::parseAccessionSequence)
                .map(sequence -> sequence + 1)
                .orElse(1);
    }

    private int parseAccessionSequence(String accessionNumber) {
        if (!StringUtils.hasText(accessionNumber)) {
            return 0;
        }
        int index = accessionNumber.lastIndexOf('-');
        if (index < 0 || index == accessionNumber.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(accessionNumber.substring(index + 1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean isAccessionCollision(DataIntegrityViolationException ex, String accessionNumber) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? "" : root.getMessage().toLowerCase();
        return StringUtils.hasText(accessionNumber)
                && (message.contains("uq_lab_order_samples_tenant_accession")
                || message.contains("accession_number")
                || message.contains(accessionNumber.toLowerCase()));
    }

    private String generateBarcodeValue(String accessionNumber) {
        return accessionNumber;
    }
}
