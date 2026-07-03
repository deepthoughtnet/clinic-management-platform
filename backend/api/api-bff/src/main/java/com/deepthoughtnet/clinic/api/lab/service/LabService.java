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
import com.deepthoughtnet.clinic.api.lab.LabCatalogueConfigService;
import com.deepthoughtnet.clinic.api.lab.LabCategoryCatalog;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDirectCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDoctorReviewCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderItemRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultComponentCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultEntryCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultPdf;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultItemCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabResultFlag;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPaymentCommand;
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
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.branding.BrandingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.springframework.dao.DataIntegrityViolationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
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
    private final ClinicalDocumentService clinicalDocumentService;
    private final ObjectStorageService objectStorageService;
    private final LabNotificationService labNotificationService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final BrandingProperties brandingProperties;
    private final LabCatalogueConfigService labCatalogueConfigService;
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
            ClinicalDocumentService clinicalDocumentService,
            ObjectStorageService objectStorageService,
            LabNotificationService labNotificationService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper,
            BrandingProperties brandingProperties,
            LabCatalogueConfigService labCatalogueConfigService,
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
        this.clinicalDocumentService = clinicalDocumentService;
        this.objectStorageService = objectStorageService;
        this.labNotificationService = labNotificationService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
        this.brandingProperties = brandingProperties;
        this.labCatalogueConfigService = labCatalogueConfigService;
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
        validateTest(command);
        ensureUniqueTestCode(tenantId, command.testCode(), null);
        ensureUniqueTestName(tenantId, command.testName(), null);
        try {
            LabTestMasterEntity entity = LabTestMasterEntity.create(tenantId, normalizeNullable(command.testCode()), normalize(command.testName()));
            entity.update(
                    normalizeNullable(command.testCode()),
                    normalize(command.testName()),
                    normalizeCategory(command.category()),
                    normalizeNullable(command.department()),
                    normalizeNullable(command.sampleType()),
                    normalizeNullable(command.unit()),
                    normalizeNullable(command.referenceRange()),
                    normalizeNullable(command.turnaroundTime()),
                    normalizeMoney(command.price()),
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
        validateTest(command);
        LabTestMasterEntity entity = labTestMasterRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Lab test not found"));
        ensureUniqueTestCode(tenantId, command.testCode(), id);
        ensureUniqueTestName(tenantId, command.testName(), id);
        try {
            entity.update(
                    normalizeNullable(command.testCode()),
                    normalize(command.testName()),
                    normalizeCategory(command.category()),
                    normalizeNullable(command.department()),
                    normalizeNullable(command.sampleType()),
                    normalizeNullable(command.unit()),
                    normalizeNullable(command.referenceRange()),
                    normalizeNullable(command.turnaroundTime()),
                    normalizeMoney(command.price()),
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
    public LabOrderRecord collectPayment(UUID tenantId, UUID orderId, LabOrderPaymentCommand command, UUID actorAppUserId) {
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
        auditOrder(tenantId, saved, "lab_order.payment_collected", actorAppUserId, "Collected lab order payment");
        return toRecord(tenantId, saved);
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
                actorAppUserId,
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
        return labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(tenantId, orderId).stream()
                .map(this::toRecord)
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
            LabOrderSampleEntity savedSample = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                String accessionNumber = generateAccessionNumber(tenantId, reservedAccessions);
                String barcodeValue = generateBarcodeValue(accessionNumber);
                OffsetDateTime collectedAt = command.collectedAt() == null ? OffsetDateTime.now() : command.collectedAt();
                UUID collectedBy = command.collectedBy() == null ? actorAppUserId : command.collectedBy();
                LabOrderSampleEntity entity = LabOrderSampleEntity.create(
                        tenantId,
                        orderId,
                        command.labOrderItemId(),
                        accessionNumber,
                        barcodeValue,
                        specimenType,
                        normalizeNullable(command.containerType()),
                        collectedAt,
                        collectedBy,
                        normalizeNullable(command.notes()),
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
            if (firstCollectedAt == null || savedSample.getCollectedAt().isBefore(firstCollectedAt)) {
                firstCollectedAt = savedSample.getCollectedAt();
                firstSpecimenType = specimenType;
                firstNotes = savedSample.getNotes();
                firstCollectedBy = savedSample.getCollectedBy();
            }
        }
        order.markSampleCollected(
                firstCollectedAt,
                firstCollectedBy == null ? actorAppUserId : firstCollectedBy,
                resolveUserDisplayName(tenantId, firstCollectedBy == null ? actorAppUserId : firstCollectedBy).orElse(null),
                firstSpecimenType,
                firstNotes
        );
        LabOrderEntity saved = labOrderRepository.save(order);
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
        return entities.stream().map(this::toRecord).toList();
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
        return toRecord(saved);
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
        ensureNoResultsEnteredForSample(tenantId, sample);
        sample.markRejected(normalize(command.rejectionReason()), command.recollectionRequired(), normalizeNullable(command.notes()), actorAppUserId);
        LabOrderSampleEntity savedSample = labOrderSampleRepository.save(sample);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, sample.getLabOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
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
        return toRecord(savedSample);
    }

    @Transactional
    public LabOrderRecord enterResults(UUID tenantId, UUID orderId, LabOrderResultEntryCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        validateResults(command);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() == LabOrderStatus.REPORT_READY
                || order.getStatus() == LabOrderStatus.REPORT_GENERATED
                || order.getStatus() == LabOrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Results are verified and cannot be edited. Use amendment workflow in a future release.");
        }
        if (order.getStatus() != LabOrderStatus.SAMPLE_COLLECTED
                && order.getStatus() != LabOrderStatus.PROCESSING
                && order.getStatus() != LabOrderStatus.RESULT_ENTERED
                && order.getStatus() != LabOrderStatus.DOCTOR_REVIEWED) {
            throw new IllegalArgumentException("Lab order is not ready for result entry");
        }
        if (order.getStatus() == LabOrderStatus.DOCTOR_REVIEWED) {
            throw new IllegalArgumentException("Lab order has already been reviewed and cannot be edited");
        }
        List<LabOrderSampleEntity> orderSamples = labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(tenantId, orderId);
        if (!orderSamples.isEmpty()) {
            boolean hasUsableSample = orderSamples.stream().anyMatch(sample -> sample.getStatus() == LabSampleStatus.COLLECTED || sample.getStatus() == LabSampleStatus.RECEIVED);
            if (!hasUsableSample) {
                throw new IllegalArgumentException("Lab order requires recollection before result entry");
            }
        }
        LabOrderStatus previousStatus = order.getStatus();
        Map<UUID, LabOrderItemEntity> orderItems = labOrderItemRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAsc(tenantId, orderId).stream()
                .collect(Collectors.toMap(LabOrderItemEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (orderItems.isEmpty()) {
            throw new IllegalArgumentException("Lab order has no ordered tests");
        }
        Map<UUID, List<LabTestParameterEntity>> parametersByTestId = orderItems.values().stream()
                .filter(item -> item.getLabTestId() != null)
                .collect(Collectors.toMap(
                        LabOrderItemEntity::getLabTestId,
                        item -> labTestParameterRepository.findByTenantIdAndLabTestIdOrderBySortOrderAsc(tenantId, item.getLabTestId()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        labOrderResultRepository.deleteByTenantIdAndLabOrderId(tenantId, orderId);
        List<LabOrderResultEntity> persistedResults = new ArrayList<>();
        List<String> criticalLabels = new ArrayList<>();
        int sortOrder = 1;
        for (LabOrderResultItemCommand itemCommand : command.items()) {
            LabOrderItemEntity orderItem = orderItems.get(itemCommand.labOrderItemId());
            if (orderItem == null) {
                throw new IllegalArgumentException("Unknown lab order item in results payload");
            }
            ensureUsableSampleForOrderItem(orderSamples, orderItem.getId());
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
                            sortOrder++,
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
                        sortOrder++,
                        resolution.flag().name(),
                        resolution.critical()
                ));
            }
        }
        labOrderResultRepository.saveAll(persistedResults);
        order.markProcessingStarted();
        order.markResultsEntered(normalizeNullable(command.comments()));
        LabOrderEntity saved = labOrderRepository.save(order);
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
        if (order.getStatus() != LabOrderStatus.RESULT_ENTERED) {
            throw new IllegalArgumentException("Lab order is not ready for verification");
        }
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
        if ("SEND_BACK".equals(decision)) {
            order.markLabVerificationSentBack(actorAppUserId, decision, normalizeNullable(command.reason()), normalizeNullable(command.comments()));
        } else {
            order.markLabVerified(actorAppUserId, decision, normalizeNullable(command.reason()), normalizeNullable(command.comments()));
        }
        LabOrderEntity saved = labOrderRepository.save(order);
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
        if (order.getStatus() != LabOrderStatus.REPORT_READY) {
            throw new IllegalArgumentException("Lab order is not ready for report publishing");
        }
        LabOrderResultPdf pdf = renderReportPdf(tenantId, orderId);
        String deliveryChannelsJson = serializeJson(normalizeDeliveryChannels(command == null ? null : command.deliveryChannels()));
        order.markReportPublished(
                actorAppUserId,
                pdf.filename(),
                "PUBLISHED",
                deliveryChannelsJson,
                normalizeNullable(command == null ? null : command.publishNotes())
        );
        LabOrderEntity saved = labOrderRepository.save(order);
        auditOrder(tenantId, saved, "lab_order.report_published", actorAppUserId, "Published lab report");

        clinicalDocumentService.upload(new ClinicalDocumentUploadCommand(
                tenantId,
                saved.getPatientId(),
                saved.getConsultationId(),
                null,
                actorAppUserId,
                ClinicalDocumentType.LAB_REPORT,
                pdf.filename(),
                "application/pdf",
                pdf.content(),
                normalizeNullable(command == null ? null : command.publishNotes()),
                null,
                null,
                "Published from lab order " + saved.getOrderNumber()
        ));

        labNotificationService.notifyReportPublished(
                tenantId,
                saved.getPatientId(),
                saved.getId(),
                saved.getOrderNumber(),
                saved.getPatientName(),
                saved.getDoctorName(),
                pdf.content(),
                pdf.filename(),
                actorAppUserId
        );
        notifyRequestingDoctor(tenantId, saved, actorAppUserId);
        return toRecord(tenantId, saved);
    }

    @Transactional
    public LabOrderResultPdf generateReportPdf(UUID tenantId, UUID orderId, UUID actorAppUserId) {
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        LabOrderStatus currentStatus = order.getStatus();
        if (currentStatus != LabOrderStatus.DOCTOR_REVIEWED
                && currentStatus != LabOrderStatus.REPORT_READY
                && currentStatus != LabOrderStatus.REPORT_GENERATED
                && currentStatus != LabOrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Lab order results are not approved for report generation");
        }
        LabOrderResultPdf pdf = renderReportPdf(tenantId, orderId);
        if (currentStatus == LabOrderStatus.DOCTOR_REVIEWED) {
            order.markReportGenerated(actorAppUserId, pdf.filename());
            LabOrderEntity saved = labOrderRepository.save(order);
            auditOrder(tenantId, saved, "lab_order.report_generated", actorAppUserId, "Generated lab report PDF");
            labNotificationService.notifyReportReady(
                    tenantId,
                    saved.getPatientId(),
                    saved.getId(),
                    saved.getOrderNumber(),
                    saved.getPatientName(),
                    saved.getDoctorName(),
                    pdf.content(),
                    pdf.filename(),
                    actorAppUserId
            );
        }
        return pdf;
    }

    @Transactional(readOnly = true)
    public LabOrderResultPdf renderReportPdf(UUID tenantId, UUID orderId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() != LabOrderStatus.DOCTOR_REVIEWED
                && order.getStatus() != LabOrderStatus.REPORT_READY
                && order.getStatus() != LabOrderStatus.REPORT_GENERATED
                && order.getStatus() != LabOrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Lab order results are not ready for report generation");
        }
        return buildReportPdf(tenantId, order);
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
        List<LabSampleRecord> samples = labOrderSampleRepository.findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(tenantId, order.getId()).stream()
                .map(this::toRecord)
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
                order.getResultComments(),
                order.getReportGeneratedAt(),
                order.getReportGeneratedByUserId(),
                resolveUserDisplayName(tenantId, order.getReportGeneratedByUserId()).orElse(null),
                order.getReportFilename(),
                order.getReportPublishedAt(),
                order.getReportPublishedByUserId(),
                order.getReportDeliveryStatus(),
                parseDeliveryChannels(order.getReportDeliveryChannels()),
                order.getReportDeliveryNotes(),
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
            case REPORT_READY -> LabOrderStatusRecord.REPORT_READY;
            case REPORT_GENERATED -> LabOrderStatusRecord.REPORT_GENERATED;
            case DOCTOR_REVIEWED -> LabOrderStatusRecord.DOCTOR_REVIEWED;
            case DELIVERED -> LabOrderStatusRecord.DELIVERED;
            case CANCELLED -> LabOrderStatusRecord.CANCELLED;
        };
    }

    private LabSampleRecord toRecord(LabOrderSampleEntity entity) {
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
                entity.getCollectedBy(),
                entity.getReceivedAt(),
                entity.getReceivedBy(),
                entity.getRejectionReason(),
                entity.isRecollectionRequired(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
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

    private LabOrderResultPdf buildReportPdf(UUID tenantId, LabOrderEntity order) {
        LabOrderRecord record = toRecord(tenantId, order);
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
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 28f;
                float width = page.getMediaBox().getWidth() - (margin * 2);
                float y = page.getMediaBox().getHeight() - margin;
                drawReportBorder(content, page, margin);
                y = drawReportHeader(document, content, clinicName, clinicAddress, clinicContact, logo, page, margin, y);
                y = drawMetaBlock(tenantId, content, record, page, margin, width, y);
                y = drawResultsTable(content, record, page, margin, width, y);
                y = drawNotesBlock(content, record, margin, width, y);
                drawFooter(content, "Generated by Jeevanam Healthcare | Powered by AIVA", margin, y, width);
            }
            document.save(output);
            return new LabOrderResultPdf(safeFilename(record.orderNumber()) + "-lab-report.pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate lab report PDF", ex);
        }
    }

    private float drawReportHeader(PDDocument document, PDPageContentStream content, String clinicName, String clinicAddress, String clinicContact, BufferedImage logo, PDPage page, float margin, float y) throws IOException {
        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();
        float logoSize = 42f;
        PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float titleFontSize = 18f;
        if (logo != null) {
            PDImageXObject image = LosslessFactory.createFromImage(document, logo);
            content.drawImage(image, margin + 2, pageHeight - margin - logoSize - 2, logoSize, logoSize);
        } else {
            setFillColor(content, 30, 64, 175);
            content.addRect(margin + 2, pageHeight - margin - logoSize - 2, logoSize, logoSize);
            content.fill();
        }
        float textX = margin + logoSize + 14f;
        writeLine(content, clinicName, 16, textX, y - 4, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        y -= 16;
        if (StringUtils.hasText(clinicAddress)) {
            y = writeWrapped(content, clinicAddress, 9f, textX, y, pageWidth - textX - margin);
        }
        if (StringUtils.hasText(clinicContact)) {
            y = writeWrapped(content, clinicContact, 9f, textX, y - 2, pageWidth - textX - margin);
        }
        float titleWidth = textWidth(boldFont, titleFontSize, "LABORATORY REPORT");
        float titleX = Math.max(textX + 16f, pageWidth - margin - titleWidth);
        writeLine(content, "LABORATORY REPORT", titleFontSize, titleX, pageHeight - margin - 4, boldFont);
        y -= 10;
        content.moveTo(margin, y);
        content.lineTo(pageWidth - margin, y);
        content.stroke();
        return y - 16;
    }

    private float drawMetaBlock(UUID tenantId, PDPageContentStream content, LabOrderRecord record, PDPage page, float margin, float width, float y) throws IOException {
        List<MetaPair> pairs = List.of(
                new MetaPair("Order No", record.orderNumber()),
                new MetaPair("Generated", formatDateTime(record.reportGeneratedAt() == null ? OffsetDateTime.now() : record.reportGeneratedAt())),
                new MetaPair("Accession", record.sampleAccessionNumber()),
                new MetaPair("Patient", record.patientName()),
                new MetaPair("Patient ID", record.patientNumber()),
                new MetaPair("Doctor", record.doctorName()),
                new MetaPair("External Lab", record.externalLabVendor()),
                new MetaPair("External Ref", record.externalReferenceNumber()),
                new MetaPair("Sample Type", firstText(record.sampleType(), fallbackSampleType(record))),
                new MetaPair("Sample By", record.sampleCollectedBy()),
                new MetaPair("Reviewed By", record.doctorReviewedBy()),
                new MetaPair("Reviewed At", formatDateTime(record.doctorReviewedAt())),
                new MetaPair("Delivered By", resolveUserDisplayName(tenantId, record.deliveredByUserId()).orElse(null)),
                new MetaPair("Delivered At", formatDateTime(record.deliveredAt())),
                new MetaPair("Status", String.valueOf(record.status()))
        );
        float columnWidth = width / 2f;
        for (int i = 0; i < pairs.size(); i += 2) {
            MetaPair left = pairs.get(i);
            MetaPair right = i + 1 < pairs.size() ? pairs.get(i + 1) : new MetaPair("", "");
            float rowHeight = Math.max(28f, Math.max(measureMetaCellHeight(left, columnWidth), measureMetaCellHeight(right, columnWidth)));
            ensureSpace(page, margin, y, rowHeight + 4);
            drawMetaCell(content, margin, y, columnWidth, rowHeight, left);
            drawMetaCell(content, margin + columnWidth, y, columnWidth, rowHeight, right);
            y -= rowHeight;
        }
        return y - 12;
    }

    private void drawMetaCell(PDPageContentStream content, float x, float y, float width, float height, MetaPair pair) throws IOException {
        content.addRect(x, y - height, width, height);
        content.stroke();
        writeLine(content, pair.label(), 8.5f, x + 6, y - 9, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeWrapped(content, safe(pair.value()), 9f, x + 6, y - 19, width - 12f);
    }

    private float drawResultsTable(PDPageContentStream content, LabOrderRecord record, PDPage page, float margin, float width, float y) throws IOException {
        writeSectionHeader(content, "Results", margin, y, width);
        y -= 20;
        String[] headers = new String[]{"Test / Component", "Result", "Unit", "Reference Range", "Interpretation"};
        float[] columns = new float[]{0.40f, 0.12f, 0.10f, 0.20f, 0.18f};
        float headerHeight = 18f;
        PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        content.setNonStrokingColor(232 / 255f, 240 / 255f, 254 / 255f);
        content.addRect(margin, y - headerHeight, width, headerHeight);
        content.fill();
        content.setNonStrokingColor(0f, 0f, 0f);
        float x = margin;
        for (int i = 0; i < headers.length; i++) {
            float colWidth = width * columns[i];
            content.addRect(x, y - headerHeight, colWidth, headerHeight);
            content.stroke();
            writeLine(content, headers[i], 8.2f, x + 4, y - 11, boldFont);
            x += colWidth;
        }
        y -= headerHeight;
        if (record.results().isEmpty()) {
            float rowHeight = 20f;
            content.addRect(margin, y - rowHeight, width, rowHeight);
            content.stroke();
            writeLine(content, "No results entered", 9f, margin + 6, y - 12, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
            return y - rowHeight - 10;
        }
        for (LabOrderResultRecord row : record.results()) {
            x = margin;
            String label = StringUtils.hasText(row.parameterName()) ? row.testName() + " / " + row.parameterName() : (StringUtils.hasText(row.componentName()) ? row.testName() + " / " + row.componentName() : row.testName());
            String[] values = new String[]{label, safe(row.resultValue()), safe(row.unit()), safe(row.referenceRange()), interpretationLabel(row.resultFlag())};
            float rowHeight = measureResultRowHeight(values, columns, width);
            ensureSpace(page, margin, y, rowHeight + 4);
            for (int i = 0; i < values.length; i++) {
                float colWidth = width * columns[i];
                content.addRect(x, y - rowHeight, colWidth, rowHeight);
                content.stroke();
                if (i == 4 && StringUtils.hasText(values[i])) {
                    if (isCriticalInterpretation(values[i])) {
                        setFillColor(content, 185, 28, 28);
                    } else if ("Low".equalsIgnoreCase(values[i]) || "High".equalsIgnoreCase(values[i])) {
                        setFillColor(content, 237, 108, 2);
                    } else {
                        setFillColor(content, 27, 94, 32);
                    }
                }
                writeWrappedInCell(content, values[i], 8.6f, x + 4, y - 10, colWidth - 8);
                if (i == 4) {
                    setFillColor(content, 0, 0, 0);
                }
                x += colWidth;
            }
            y -= rowHeight;
        }
        return y - 10;
    }

    private float drawNotesBlock(PDPageContentStream content, LabOrderRecord record, float margin, float width, float y) throws IOException {
        List<String> notes = new ArrayList<>();
        if (StringUtils.hasText(record.sampleCollectionNotes())) {
            notes.add("Sample collection notes: " + record.sampleCollectionNotes());
        }
        if (StringUtils.hasText(record.resultComments())) {
            notes.add("Comments: " + record.resultComments());
        }
        if (StringUtils.hasText(record.doctorComments())) {
            notes.add("Doctor review: " + record.doctorComments());
        }
        if (notes.isEmpty()) {
            return y;
        }
        writeSectionHeader(content, "Comments", margin, y, width);
        y -= 20;
        for (String note : notes) {
            y = writeWrapped(content, note, 9f, margin, y, width);
            y -= 4;
        }
        return y - 4;
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
        List<String> lines = wrap(text, new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize, maxWidth);
        if (lines.isEmpty()) {
            lines = List.of("");
        }
        float lineY = y;
        for (String line : lines) {
            writeLine(content, line, fontSize, x, lineY, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            lineY -= fontSize + 1;
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
        for (String token : value.split("\\s+")) {
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
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
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

    private void drawFooter(PDPageContentStream content, String text, float margin, float y, float width) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSize = 8f;
        float textWidth = textWidth(font, fontSize, text);
        if (textWidth <= width) {
            float x = margin + Math.max(0f, (width - textWidth) / 2f);
            writeLine(content, text, fontSize, x, y - 10, font);
        } else {
            writeWrapped(content, text, fontSize, margin, y - 10, width);
        }
    }

    private float measureMetaCellHeight(MetaPair pair, float width) throws IOException {
        List<String> lines = wrap(safe(pair.value()), new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9f, width - 12f);
        return Math.max(28f, 14f + Math.max(1, lines.size()) * 10f);
    }

    private float measureResultRowHeight(String[] values, float[] columns, float width) throws IOException {
        float max = 22f;
        for (int i = 0; i < values.length; i++) {
            float colWidth = width * columns[i] - 8f;
            List<String> lines = wrap(values[i], new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.6f, colWidth);
            max = Math.max(max, 8f + Math.max(1, lines.size()) * 9.6f);
        }
        return max;
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

    private void ensureUsableSampleForOrderItem(List<LabOrderSampleEntity> samples, UUID labOrderItemId) {
        if (samples == null || samples.isEmpty()) {
            return;
        }
        List<LabOrderSampleEntity> relevantSamples = samples.stream()
                .filter(sample -> sample.getLabOrderItemId() == null || sample.getLabOrderItemId().equals(labOrderItemId))
                .toList();
        if (relevantSamples.isEmpty()) {
            throw new IllegalArgumentException("A collected sample is required before result entry");
        }
        boolean hasUsable = relevantSamples.stream().anyMatch(sample -> sample.getStatus() == LabSampleStatus.COLLECTED || sample.getStatus() == LabSampleStatus.RECEIVED);
        if (!hasUsable) {
            throw new IllegalArgumentException("Result entry is blocked for a rejected or recollection-required sample");
        }
    }

    private void ensureNoResultsEnteredForSample(UUID tenantId, LabOrderSampleEntity sample) {
        if (sample.getLabOrderItemId() == null) {
            if (!labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, sample.getLabOrderId()).isEmpty()) {
                throw new IllegalArgumentException("Lab sample cannot be rejected after results are entered");
            }
            return;
        }
        if (!labOrderResultRepository.findByTenantIdAndLabOrderItemId(tenantId, sample.getLabOrderItemId()).isEmpty()) {
            throw new IllegalArgumentException("Lab sample cannot be rejected after results are entered");
        }
    }

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? "-" : value.format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a"));
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

    private void ensureSpace(PDPage page, float margin, float y, float needed) {
        if (y - needed < margin) {
            throw new IllegalStateException("Lab report PDF content overflow");
        }
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

    private void validateTest(LabTestUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        String testCode = normalizeNullable(command.testCode());
        if (StringUtils.hasText(testCode) && !testCode.matches("^[A-Za-z0-9/_-]+$")) {
            throw new IllegalArgumentException("testCode contains invalid characters");
        }
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
        if (normalize(command.category()).length() > 30) {
            throw new IllegalArgumentException("category must be 30 characters or fewer");
        }
        if (command.price() == null || command.price().compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("price is required");
        }
        if (command.price().compareTo(new BigDecimal("999999.00")) > 0) {
            throw new IllegalArgumentException("price exceeds the allowed maximum");
        }
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
        if (!StringUtils.hasText(testCode)) {
            return;
        }
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
        if (StringUtils.hasText(command.collectedBy()) && command.collectedBy().trim().length() > 60) {
            throw new IllegalArgumentException("collectedBy must be 60 characters or fewer");
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
            if (command == null || !StringUtils.hasText(command.parameterName())) {
                continue;
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
        if (message.contains("uq_lab_tests_tenant_code") || message.contains("test_code")) {
            return new IllegalArgumentException("Lab test code already exists");
        }
        if (message.contains("uq_lab_tests_tenant_name") || message.contains("test_name")) {
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
