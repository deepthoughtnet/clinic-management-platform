package com.deepthoughtnet.clinic.api.lab.service;

import com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderAttachmentEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderAttachmentRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderItemEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderItemRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabTestParameterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestParameterRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDoctorReviewCommand;
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
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderAttachmentRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestUpsertCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterUpsertCommand;
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
import org.springframework.util.StringUtils;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;

@Service
public class LabService {
    private static final String TEST_ENTITY_TYPE = "LAB_TEST";
    private static final String ORDER_ENTITY_TYPE = "LAB_ORDER";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final List<String> LAB_CATEGORIES = List.of(
            "HEMATOLOGY",
            "BIOCHEMISTRY",
            "MICROBIOLOGY",
            "PATHOLOGY",
            "RADIOLOGY",
            "CARDIOLOGY",
            "OTHER"
    );

    private final LabTestMasterRepository labTestMasterRepository;
    private final LabTestParameterRepository labTestParameterRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabOrderResultRepository labOrderResultRepository;
    private final LabOrderAttachmentRepository labOrderAttachmentRepository;
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

    public LabService(
            LabTestMasterRepository labTestMasterRepository,
            LabTestParameterRepository labTestParameterRepository,
            LabOrderRepository labOrderRepository,
            LabOrderItemRepository labOrderItemRepository,
            LabOrderResultRepository labOrderResultRepository,
            LabOrderAttachmentRepository labOrderAttachmentRepository,
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
            BrandingProperties brandingProperties
    ) {
        this.labTestMasterRepository = labTestMasterRepository;
        this.labTestParameterRepository = labTestParameterRepository;
        this.labOrderRepository = labOrderRepository;
        this.labOrderItemRepository = labOrderItemRepository;
        this.labOrderResultRepository = labOrderResultRepository;
        this.labOrderAttachmentRepository = labOrderAttachmentRepository;
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
    }

    @Transactional(readOnly = true)
    public List<String> listCategories() {
        return LAB_CATEGORIES;
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
        TenantUserRecord doctor = consultation.doctorUserId() == null ? null : tenantUserManagementService.list(tenantId).stream()
                .filter(user -> consultation.doctorUserId().equals(user.appUserId()))
                .findFirst()
                .orElse(null);

        List<LabTestMasterEntity> tests = loadTests(tenantId, command.testIds());
        LabOrderEntity order = LabOrderEntity.create(
                tenantId,
                generateOrderNumber(tenantId),
                patient.getId(),
                patient.getPatientNumber(),
                patient.getFirstName() + " " + patient.getLastName(),
                consultation.doctorUserId(),
                doctor == null ? consultation.doctorName() : doctor.displayName(),
                consultation.id(),
                normalizeNullable(command.notes())
        );
        LabOrderEntity savedOrder = labOrderRepository.save(order);
        int sortOrder = 1;
        List<LabOrderItemEntity> items = new ArrayList<>();
        for (LabTestMasterEntity test : tests) {
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
                    test.getTurnaroundTime(),
                    normalizeMoney(test.getPrice()),
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
                consultation.id(),
                consultation.appointmentId(),
                LocalDate.now(),
                DiscountType.NONE,
                ZERO,
                null,
                null,
                null,
                ZERO,
                "Lab tests ordered from consultation",
                billLines
        ), actorAppUserId);
        BillRecord issuedBill = billingService.issue(tenantId, bill.id(), actorAppUserId);
        savedOrder.linkBill(issuedBill.id());
        savedOrder.markStatus(LabOrderStatus.PAYMENT_PENDING);
        LabOrderEntity persisted = labOrderRepository.save(savedOrder);
        auditOrder(tenantId, persisted, "lab_order.created", actorAppUserId, "Created lab order and billing");
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
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        validateSampleCollection(command);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() != LabOrderStatus.READY_FOR_COLLECTION) {
            throw new IllegalArgumentException("Lab order is not ready for sample collection");
        }
        if (order.getSampleCollectedAt() != null) {
            throw new IllegalArgumentException("Sample collection has already been recorded");
        }
        String collectedBy = normalizeNullable(command.collectedBy());
        if (!StringUtils.hasText(collectedBy)) {
            collectedBy = resolveUserDisplayName(tenantId, actorAppUserId).orElse(null);
        }
        order.markSampleCollected(
                command.collectedAt(),
                actorAppUserId,
                collectedBy,
                normalizeNullable(command.sampleType()),
                normalizeNullable(command.notes())
        );
        LabOrderEntity saved = labOrderRepository.save(order);
        auditOrder(tenantId, saved, "lab_order.sample_collected", actorAppUserId, "Collected lab sample");
        labNotificationService.notifySampleCollected(
                tenantId,
                saved.getPatientId(),
                saved.getId(),
                saved.getOrderNumber(),
                saved.getPatientName(),
                saved.getDoctorName(),
                actorAppUserId
        );
        return toRecord(tenantId, saved);
    }

    @Transactional
    public LabOrderRecord enterResults(UUID tenantId, UUID orderId, LabOrderResultEntryCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(orderId, "orderId");
        validateResults(command);
        LabOrderEntity order = labOrderRepository.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        if (order.getStatus() != LabOrderStatus.SAMPLE_COLLECTED
                && order.getStatus() != LabOrderStatus.PROCESSING
                && order.getStatus() != LabOrderStatus.RESULT_ENTERED
                && order.getStatus() != LabOrderStatus.DOCTOR_REVIEWED) {
            throw new IllegalArgumentException("Lab order is not ready for result entry");
        }
        if (order.getStatus() == LabOrderStatus.DOCTOR_REVIEWED) {
            throw new IllegalArgumentException("Lab order has already been reviewed and cannot be edited");
        }
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
            List<LabOrderResultComponentCommand> components = itemCommand.componentResults() == null ? List.of() : itemCommand.componentResults();
            if (!components.isEmpty()) {
                for (LabOrderResultComponentCommand component : components) {
                    LabTestParameterEntity parameter = findParameter(parametersByTestId.get(orderItem.getLabTestId()), component.parameterName(), component.componentName());
                    ResultFlagResolution resolution = resolveResultFlag(component.resultValue(), parameter == null ? itemCommand.referenceRange() : parameter.getNormalRange(), parameter == null ? null : parameter.getCriticalRange());
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
        auditOrder(tenantId, saved, "lab_order.results_entered", actorAppUserId, "Entered lab results");
        if (!criticalLabels.isEmpty()) {
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
                order.getDoctorReviewedAt(),
                order.getDoctorReviewedByUserId(),
                order.getDoctorReviewedBy(),
                order.getDoctorReviewDecision(),
                order.getDoctorReviewReason(),
                order.getDoctorComments(),
                attachments,
                items,
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
                y = drawResultsTable(content, record, margin, width, y);
                y = drawNotesBlock(content, record, margin, width, y);
                drawFooter(content, brandingProperties.footerLine(), margin, y);
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
        writeLine(content, "LABORATORY REPORT", 22, pageWidth - margin - 206, pageHeight - margin - 4, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
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
        float rowHeight = 20f;
        float columnWidth = width / 2f;
        for (int i = 0; i < pairs.size(); i += 2) {
            ensureSpace(page, margin, y, rowHeight + 4);
            MetaPair left = pairs.get(i);
            MetaPair right = i + 1 < pairs.size() ? pairs.get(i + 1) : new MetaPair("", "");
            drawMetaCell(content, margin, y, columnWidth, rowHeight, left);
            drawMetaCell(content, margin + columnWidth, y, columnWidth, rowHeight, right);
            y -= rowHeight;
        }
        return y - 12;
    }

    private void drawMetaCell(PDPageContentStream content, float x, float y, float width, float height, MetaPair pair) throws IOException {
        content.addRect(x, y - height, width, height);
        content.stroke();
        writeLine(content, pair.label(), 8.5f, x + 6, y - 8, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeLine(content, safe(pair.value()), 9.2f, x + 72, y - 8, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
    }

    private float drawResultsTable(PDPageContentStream content, LabOrderRecord record, float margin, float width, float y) throws IOException {
        writeSectionHeader(content, "Results", margin, y, width);
        y -= 20;
        String[] headers = new String[]{"Test / Component", "Result", "Flag", "Unit", "Reference Range"};
        float[] columns = new float[]{0.30f, 0.18f, 0.12f, 0.12f, 0.28f};
        float headerHeight = 18f;
        float rowHeight = 20f;
        content.setNonStrokingColor(232, 240, 254);
        content.addRect(margin, y - headerHeight, width, headerHeight);
        content.fill();
        content.setNonStrokingColor(0, 0, 0);
        float x = margin;
        for (int i = 0; i < headers.length; i++) {
            float colWidth = width * columns[i];
            content.addRect(x, y - headerHeight, colWidth, headerHeight);
            content.stroke();
            writeLine(content, headers[i], 8.4f, x + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            x += colWidth;
        }
        y -= headerHeight;
        if (record.results().isEmpty()) {
            content.addRect(margin, y - rowHeight, width, rowHeight);
            content.stroke();
            writeLine(content, "No results entered", 9f, margin + 6, y - 12, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
            return y - rowHeight - 10;
        }
        for (LabOrderResultRecord row : record.results()) {
            x = margin;
            String label = StringUtils.hasText(row.parameterName()) ? row.testName() + " / " + row.parameterName() : (StringUtils.hasText(row.componentName()) ? row.testName() + " / " + row.componentName() : row.testName());
            String[] values = new String[]{label, safe(row.resultValue()), safe(row.resultFlag()), safe(row.unit()), safe(row.referenceRange())};
            for (int i = 0; i < values.length; i++) {
                float colWidth = width * columns[i];
                content.addRect(x, y - rowHeight, colWidth, rowHeight);
                content.stroke();
                if (i == 2 && StringUtils.hasText(values[i])) {
                    if ("CRITICAL".equalsIgnoreCase(values[i])) {
                        setFillColor(content, 185, 28, 28);
                    } else if ("LOW".equalsIgnoreCase(values[i]) || "HIGH".equalsIgnoreCase(values[i])) {
                        setFillColor(content, 237, 108, 2);
                    } else {
                        setFillColor(content, 27, 94, 32);
                    }
                }
                writeWrappedInCell(content, values[i], 8.8f, x + 4, y - 11, colWidth - 8);
                if (i == 2) {
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
        content.setNonStrokingColor(red, green, blue);
        content.setStrokingColor(red, green, blue);
    }

    private void drawFooter(PDPageContentStream content, String text, float margin, float y) throws IOException {
        writeWrapped(content, text, 8f, margin, y, 520);
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

    private record Range(BigDecimal low, BigDecimal high) {
        boolean contains(BigDecimal value) {
            return value != null && value.compareTo(low) >= 0 && value.compareTo(high) <= 0;
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
        }
        return testIds.stream().map(byId::get).toList();
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
        if (command.collectedAt() == null) {
            throw new IllegalArgumentException("collectedAt is required");
        }
        if (command.collectedAt().isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("collection date cannot be in the future");
        }
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

    private void saveParameters(UUID tenantId, UUID labTestId, List<LabTestParameterUpsertCommand> commands) {
        labTestParameterRepository.deleteByTenantIdAndLabTestId(tenantId, labTestId);
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
        Range critical = parseRange(criticalRange);
        if (critical != null && !critical.contains(numeric)) {
            return new ResultFlagResolution(LabResultFlag.CRITICAL, true);
        }
        Range normal = parseRange(normalRange);
        if (normal == null) {
            return new ResultFlagResolution(LabResultFlag.NORMAL, false);
        }
        if (numeric.compareTo(normal.low()) < 0) {
            return new ResultFlagResolution(LabResultFlag.LOW, false);
        }
        if (numeric.compareTo(normal.high()) > 0) {
            return new ResultFlagResolution(LabResultFlag.HIGH, false);
        }
        return new ResultFlagResolution(LabResultFlag.NORMAL, false);
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
        String normalized = value.trim();
        if (normalized.contains("-")) {
            String[] parts = normalized.split("-", 2);
            BigDecimal low = parseNumeric(parts[0]);
            BigDecimal high = parseNumeric(parts[1]);
            if (low != null && high != null) {
                return new Range(low.min(high), low.max(high));
            }
        }
        if (normalized.startsWith("<")) {
            BigDecimal high = parseNumeric(normalized.substring(1));
            if (high != null) {
                return new Range(ZERO, high);
            }
        }
        if (normalized.startsWith(">")) {
            BigDecimal low = parseNumeric(normalized.substring(1));
            if (low != null) {
                return new Range(low, low.add(BigDecimal.ONE));
            }
        }
        BigDecimal exact = parseNumeric(normalized);
        return exact == null ? null : new Range(exact, exact);
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
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("category is required");
        }
        for (String allowed : LAB_CATEGORIES) {
            if (allowed.equalsIgnoreCase(normalized)) {
                return allowed;
            }
        }
        throw new IllegalArgumentException("Unsupported category");
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
        details.put("status", entity.getStatus());
        details.put("billId", entity.getBillId());
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
}
