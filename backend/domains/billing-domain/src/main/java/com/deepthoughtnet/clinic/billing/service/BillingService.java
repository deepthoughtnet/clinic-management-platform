package com.deepthoughtnet.clinic.billing.service;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.billing.db.BillEntity;
import com.deepthoughtnet.clinic.billing.db.BillLineEntity;
import com.deepthoughtnet.clinic.billing.db.BillLineRepository;
import com.deepthoughtnet.clinic.billing.db.BillRefundEntity;
import com.deepthoughtnet.clinic.billing.db.BillRefundRepository;
import com.deepthoughtnet.clinic.billing.db.BillRepository;
import com.deepthoughtnet.clinic.billing.db.PaymentEntity;
import com.deepthoughtnet.clinic.billing.db.PaymentRepository;
import com.deepthoughtnet.clinic.billing.db.ReceiptEntity;
import com.deepthoughtnet.clinic.billing.db.ReceiptRepository;
import com.deepthoughtnet.clinic.billing.service.model.ConsultationFeePaymentCommand;
import com.deepthoughtnet.clinic.billing.service.model.ConsultationFeeStatusRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillLineRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillPdf;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillUpsertCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.billing.service.model.PaymentCommand;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.PatientBillingContextRecord;
import com.deepthoughtnet.clinic.billing.service.model.PendingConsultationFeeRecord;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptPdf;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.billing.service.model.RefundCommand;
import com.deepthoughtnet.clinic.billing.service.model.RefundRecord;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.branding.BrandingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BillingService {
    private static final String ENTITY_TYPE = "BILL";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter PDF_TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final BillRepository billRepository;
    private final BillLineRepository billLineRepository;
    private final PaymentRepository paymentRepository;
    private final BillRefundRepository billRefundRepository;
    private final ReceiptRepository receiptRepository;
    private final PatientRepository patientRepository;
    private final ClinicProfileService clinicProfileService;
    private final DoctorProfileService doctorProfileService;
    private final ConsultationService consultationService;
    private final AppointmentService appointmentService;
    private final InventoryService inventoryService;
    @SuppressWarnings("unused")
    private final TenantUserManagementService tenantUserManagementService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final BrandingProperties brandingProperties;

    public BillingService(
            BillRepository billRepository,
            BillLineRepository billLineRepository,
            PaymentRepository paymentRepository,
            BillRefundRepository billRefundRepository,
            ReceiptRepository receiptRepository,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            DoctorProfileService doctorProfileService,
            ConsultationService consultationService,
            AppointmentService appointmentService,
            InventoryService inventoryService,
            TenantUserManagementService tenantUserManagementService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper,
            BrandingProperties brandingProperties
    ) {
        this.billRepository = billRepository;
        this.billLineRepository = billLineRepository;
        this.paymentRepository = paymentRepository;
        this.billRefundRepository = billRefundRepository;
        this.receiptRepository = receiptRepository;
        this.patientRepository = patientRepository;
        this.clinicProfileService = clinicProfileService;
        this.doctorProfileService = doctorProfileService;
        this.consultationService = consultationService;
        this.appointmentService = appointmentService;
        this.inventoryService = inventoryService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
        this.brandingProperties = brandingProperties;
    }

    @Transactional(readOnly = true)
    public List<BillRecord> list(UUID tenantId, BillingSearchCriteria criteria) {
        requireTenant(tenantId);
        BillingSearchCriteria safe = criteria == null ? new BillingSearchCriteria(null, null, null, null, null, null, null) : criteria;
        List<BillEntity> bills = billRepository.search(tenantId, safe);
        return mapBills(tenantId, bills);
    }

    @Transactional(readOnly = true)
    public Optional<BillRecord> findById(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return billRepository.findByTenantIdAndId(tenantId, id).map(entity -> toRecord(entity, tenantData(tenantId, List.of(entity.getPatientId()))));
    }

    @Transactional(readOnly = true)
    public List<BillRecord> listByPatient(UUID tenantId, UUID patientId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        return mapBills(tenantId, billRepository.findByTenantIdAndPatientIdOrderByBillDateDescCreatedAtDesc(tenantId, patientId));
    }

    @Transactional(readOnly = true)
    public PatientBillingContextRecord patientContext(UUID tenantId, UUID patientId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found for tenant"));

        List<BillRecord> bills = listByPatient(tenantId, patientId);
        BigDecimal billDueAmount = bills.stream()
                .map(BillRecord::dueAmount)
                .map(this::normalizeMoney)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (billDueAmount.compareTo(ZERO) < 0) {
            billDueAmount = ZERO;
        }

        List<PendingConsultationFeeRecord> pendingFees = new ArrayList<>();
        List<AppointmentRecord> appointments = appointmentService.search(tenantId, new AppointmentSearchCriteria(null, patientId, null, null, null));
        for (AppointmentRecord appointment : appointments) {
            if (appointment.paymentBypassedAt() == null) {
                continue;
            }
            if (appointment.status() == AppointmentStatus.CANCELLED
                    || appointment.status() == AppointmentStatus.NO_SHOW) {
                continue;
            }
            if (appointment.doctorUserId() == null) {
                continue;
            }
            DoctorProfileRecord doctorProfile = doctorProfileService.findByDoctorUserId(tenantId, appointment.doctorUserId()).orElse(null);
            if (doctorProfile == null || doctorProfile.consultationFee() == null || doctorProfile.consultationFee().compareTo(ZERO) <= 0) {
                continue;
            }
            boolean alreadyInvoiced = bills.stream()
                    .filter(bill -> appointment.id().equals(bill.appointmentId()))
                    .filter(bill -> !isCancelledLifecycle(bill.status()))
                    .anyMatch(bill -> bill.lines().stream().anyMatch(line -> line.itemType() == BillItemType.CONSULTATION));
            if (alreadyInvoiced) {
                continue;
            }
            BigDecimal consultationFee = normalizeMoney(doctorProfile.consultationFee());
            if (consultationFee.compareTo(ZERO) <= 0) {
                continue;
            }
            pendingFees.add(new PendingConsultationFeeRecord(
                    appointment.id(),
                    appointment.appointmentDate(),
                    appointment.appointmentTime(),
                    appointment.doctorUserId(),
                    appointment.doctorName(),
                    consultationFee,
                    consultationFee,
                    appointment.paymentBypassReason(),
                    appointment.paymentBypassedAt()
            ));
        }

        pendingFees.sort(
                Comparator.comparing(PendingConsultationFeeRecord::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PendingConsultationFeeRecord::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
        );

        BigDecimal pendingConsultationFeeAmount = pendingFees.stream()
                .map(PendingConsultationFeeRecord::dueAmount)
                .map(this::normalizeMoney)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (pendingConsultationFeeAmount.compareTo(ZERO) < 0) {
            pendingConsultationFeeAmount = ZERO;
        }

        BigDecimal totalDueAmount = billDueAmount.add(pendingConsultationFeeAmount).setScale(2, RoundingMode.HALF_UP);
        return new PatientBillingContextRecord(
                patient.getId(),
                patient.getPatientNumber(),
                patient.getFirstName() + " " + patient.getLastName(),
                billDueAmount,
                pendingConsultationFeeAmount,
                totalDueAmount.compareTo(ZERO) < 0 ? ZERO : totalDueAmount,
                bills.size(),
                List.copyOf(pendingFees)
        );
    }

    @Transactional
    public BillRecord createDraft(UUID tenantId, BillUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validate(command);
        ensureNotDuplicateSubmission(tenantId, command);
        ensurePatient(tenantId, command.patientId());
        ensureConsultationMatches(tenantId, command.consultationId(), command.patientId());
        ensureAppointmentMatches(tenantId, command.appointmentId(), command.patientId());

        BillEntity entity = BillEntity.create(tenantId, generateBillNumber(tenantId), command.patientId(), command.consultationId(), command.appointmentId(), command.billDate());
        entity.update(command.patientId(), command.consultationId(), command.appointmentId(), command.billDate(), normalizeNullable(command.notes()), command.discountType(), normalizeMoney(command.discountValue()), normalizeNullable(command.discountReason()), command.discountApprovedBy(), normalizeMoney(command.taxAmount()));
        BillEntity saved = billRepository.save(entity);
        replaceLines(tenantId, saved.getId(), command.lines());
        refreshFinancials(saved);
        auditBill(tenantId, saved, "bill.created", actorAppUserId, "Created bill draft");
        return toRecord(saved, tenantData(tenantId, List.of(saved.getPatientId())));
    }

    @Transactional
    public BillRecord updateDraft(UUID tenantId, UUID id, BillUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validate(command);
        BillEntity entity = findBill(tenantId, id);
        ensureEditable(entity);
        ensurePatient(tenantId, command.patientId());
        ensureConsultationMatches(tenantId, command.consultationId(), command.patientId());
        ensureAppointmentMatches(tenantId, command.appointmentId(), command.patientId());

        entity.update(command.patientId(), command.consultationId(), command.appointmentId(), command.billDate(), normalizeNullable(command.notes()), command.discountType(), normalizeMoney(command.discountValue()), normalizeNullable(command.discountReason()), command.discountApprovedBy(), normalizeMoney(command.taxAmount()));
        BillEntity saved = billRepository.save(entity);
        replaceLines(tenantId, saved.getId(), command.lines());
        refreshFinancials(saved);
        auditBill(tenantId, saved, "bill.updated", actorAppUserId, "Updated bill draft");
        return toRecord(saved, tenantData(tenantId, List.of(saved.getPatientId())));
    }

    @Transactional
    public BillRecord issue(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        BillEntity entity = findBill(tenantId, id);
        if (isClosedForLifecycle(entity.getStatus())) {
            throw new IllegalArgumentException("Closed bill cannot be issued");
        }
        entity.markStatus(BillStatus.ISSUED);
        BillEntity saved = billRepository.save(entity);
        refreshFinancials(saved);
        auditBill(tenantId, saved, "bill.issued", actorAppUserId, "Issued bill");
        return toRecord(saved, tenantData(tenantId, List.of(saved.getPatientId())));
    }

    @Transactional
    public BillRecord addLineItem(UUID tenantId, UUID billId, BillLineCommand line, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(billId, "billId");
        validateLine(line);
        BillEntity entity = findBill(tenantId, billId);
        ensureEditable(entity);
        int sort = billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, billId).size() + 1;
        billLineRepository.save(createLineEntity(tenantId, billId, sort, line));
        refreshFinancials(entity);
        auditBill(tenantId, entity, "bill.line_added", actorAppUserId, "Added bill line " + line.itemName());
        return toRecord(entity, tenantData(tenantId, List.of(entity.getPatientId())));
    }

    @Transactional
    public BillRecord cancel(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        BillEntity entity = findBill(tenantId, id);
        if (entity.getStatus() == BillStatus.REFUNDED || entity.getStatus() == BillStatus.CANCELLED_REFUNDED) {
            throw new IllegalArgumentException("Refunded bill cannot be cancelled");
        }
        entity.cancel();
        BillEntity saved = billRepository.save(entity);
        refreshFinancials(saved);
        auditBill(tenantId, saved, "bill.cancelled", actorAppUserId, "Cancelled bill");
        return toRecord(saved, tenantData(tenantId, List.of(saved.getPatientId())));
    }

    @Transactional
    public PaymentRecord recordPayment(UUID tenantId, UUID billId, PaymentCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(billId, "billId");
        validate(command);
        BillEntity bill = findBill(tenantId, billId);
        if (isClosedForLifecycle(bill.getStatus())) {
            throw new IllegalArgumentException("Closed bill cannot accept payments");
        }
        if (bill.getStatus() == BillStatus.DRAFT) {
            bill.markStatus(BillStatus.ISSUED);
            billRepository.save(bill);
        }
        refreshFinancials(bill);
        if (command.amount().compareTo(bill.getDueAmount()) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed bill due amount");
        }
        return persistPayment(tenantId, bill, command, actorAppUserId);
    }

    private PaymentRecord persistPayment(UUID tenantId, BillEntity bill, PaymentCommand command, UUID actorAppUserId) {
        PaymentEntity payment = paymentRepository.save(PaymentEntity.create(
                tenantId,
                bill.getId(),
                command.paymentDate(),
                command.paymentDateTime(),
                normalizeMoney(command.amount()),
                command.paymentMode(),
                normalizeNullable(command.referenceNumber()),
                command.receivedBy(),
                normalizeNullable(command.notes())
        ));
        refreshFinancials(bill);
        ReceiptEntity receipt = receiptRepository.save(ReceiptEntity.create(
                tenantId,
                generateReceiptNumber(tenantId),
                bill.getId(),
                payment.getId(),
                payment.getPaymentDate(),
                normalizeMoney(command.amount())
        ));
        refreshFinancials(bill);
        auditPayment(tenantId, payment, receipt, actorAppUserId, "Collected bill payment");
        return toPaymentRecord(payment, receipt);
    }

    @Transactional
    public PaymentRecord collectConsultationFee(UUID tenantId, ConsultationFeePaymentCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireId(command.appointmentId(), "appointmentId");
        if (command.paymentMode() == null) {
            throw new IllegalArgumentException("paymentMode is required");
        }
        if (command.paymentMode() != PaymentMode.CASH && !StringUtils.hasText(command.referenceNumber())) {
            throw new IllegalArgumentException("referenceNumber is required for non-cash payments");
        }

        var appointment = appointmentService.findById(tenantId, command.appointmentId());
        DoctorProfileRecord doctorProfile = doctorProfileService.findByDoctorUserId(tenantId, appointment.doctorUserId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor consultation fee is not configured."));
        BigDecimal consultationFee = normalizeMoney(doctorProfile.consultationFee());
        if (consultationFee.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Doctor consultation fee is not configured.");
        }

        ConsultationFeeAssessment assessment = assessConsultationFee(tenantId, command.appointmentId(), consultationFee);
        BillEntity consultationBill = assessment.reusableBill();
        if (consultationBill == null) {
            BillUpsertCommand billCommand = new BillUpsertCommand(
                    appointment.patientId(),
                    null,
                    command.appointmentId(),
                    appointment.appointmentDate(),
                    DiscountType.NONE,
                    ZERO,
                    null,
                    null,
                    null,
                    ZERO,
                    "Consultation fee collected at reception",
                    List.of(new BillLineCommand(
                    BillItemType.CONSULTATION,
                            StringUtils.hasText(appointment.doctorName()) ? "Consultation fee - " + appointment.doctorName() : "Consultation fee",
                            1,
                            assessment.remainingDue(),
                            command.appointmentId(),
                            1,
                            ZERO,
                            null,
                            null
                    ))
            );
            BillRecord createdBill = createDraft(tenantId, billCommand, actorAppUserId);
            consultationBill = findBill(tenantId, createdBill.id());
        }

        if (assessment.remainingDue().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Consultation fee is already paid.");
        }

        BigDecimal paymentAmount = assessment.remainingDue();
        PaymentRecord payment = persistPayment(
                tenantId,
                consultationBill,
                new PaymentCommand(
                        appointment.appointmentDate(),
                        OffsetDateTime.now(),
                        paymentAmount,
                        command.paymentMode(),
                        normalizeNullable(command.referenceNumber()),
                        normalizeNullable(command.notes()),
                        actorAppUserId
                ),
                actorAppUserId
        );
        return payment;
    }

    @Transactional(readOnly = true)
    public void ensureConsultationFeePaid(UUID tenantId, UUID appointmentId) {
        requireTenant(tenantId);
        requireId(appointmentId, "appointmentId");
        var appointment = appointmentService.findById(tenantId, appointmentId);
        DoctorProfileRecord doctorProfile = doctorProfileService.findByDoctorUserId(tenantId, appointment.doctorUserId()).orElse(null);
        if (doctorProfile == null || doctorProfile.consultationFee() == null || doctorProfile.consultationFee().compareTo(ZERO) <= 0) {
            return;
        }
        ConsultationFeeAssessment assessment = assessConsultationFee(tenantId, appointmentId, normalizeMoney(doctorProfile.consultationFee()));
        if (assessment.reusableBill() == null && assessment.remainingDue().compareTo(ZERO) > 0) {
            throw new IllegalArgumentException("Consultation fee must be collected before check-in.");
        }
        if (assessment.remainingDue().compareTo(ZERO) > 0) {
            throw new IllegalArgumentException("Consultation fee must be collected before check-in.");
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal consultationFeeDueAmount(UUID tenantId, UUID appointmentId) {
        requireTenant(tenantId);
        requireId(appointmentId, "appointmentId");
        var appointment = appointmentService.findById(tenantId, appointmentId);
        DoctorProfileRecord doctorProfile = doctorProfileService.findByDoctorUserId(tenantId, appointment.doctorUserId()).orElse(null);
        if (doctorProfile == null || doctorProfile.consultationFee() == null || doctorProfile.consultationFee().compareTo(ZERO) <= 0) {
            return ZERO;
        }
        ConsultationFeeAssessment assessment = assessConsultationFee(tenantId, appointmentId, normalizeMoney(doctorProfile.consultationFee()));
        return assessment.remainingDue();
    }

    @Transactional(readOnly = true)
    public ConsultationFeeStatusRecord consultationFeeStatus(UUID tenantId, UUID appointmentId) {
        requireTenant(tenantId);
        requireId(appointmentId, "appointmentId");
        var appointment = appointmentService.findById(tenantId, appointmentId);
        DoctorProfileRecord doctorProfile = doctorProfileService.findByDoctorUserId(tenantId, appointment.doctorUserId()).orElse(null);
        if (doctorProfile == null || doctorProfile.consultationFee() == null || doctorProfile.consultationFee().compareTo(ZERO) <= 0) {
            return new ConsultationFeeStatusRecord("NOT_CONFIGURED", ZERO, ZERO, ZERO, null, null);
        }
        BigDecimal configuredFee = normalizeMoney(doctorProfile.consultationFee());
        ConsultationFeeAssessment assessment = assessConsultationFee(tenantId, appointmentId, configuredFee);
        String status = assessment.remainingDue().compareTo(ZERO) <= 0
                ? "PAID"
                : assessment.aggregateNetPaid().compareTo(ZERO) > 0
                    ? "PARTIAL"
                    : "UNPAID";
        BillEntity bill = assessment.reusableBill();
        return new ConsultationFeeStatusRecord(
                status,
                configuredFee,
                assessment.aggregateNetPaid(),
                assessment.remainingDue(),
                bill == null ? null : bill.getId(),
                bill == null ? null : bill.getBillNumber()
        );
    }

    @Transactional
    public RefundRecord refund(UUID tenantId, UUID billId, RefundCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(billId, "billId");
        validate(command);
        BillEntity bill = findBill(tenantId, billId);
        refreshFinancials(bill);
        BigDecimal refundable = refundableAmount(bill);
        if (command.amount().compareTo(refundable) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed refundable amount");
        }
        BillRefundEntity refund = billRefundRepository.save(BillRefundEntity.create(
                tenantId,
                billId,
                command.paymentId(),
                normalizeMoney(command.amount()),
                normalize(command.reason()),
                command.refundMode(),
                actorAppUserId,
                command.refundedAt(),
                normalizeNullable(command.notes())
        ));
        refreshFinancials(bill);
        auditEventPublisher.record(new AuditEventCommand(tenantId, "REFUND", refund.getId(), "refund.created", actorAppUserId, OffsetDateTime.now(), "Refund created", detailsJson(refund)));
        return toRefundRecord(refund);
    }

    @Transactional(readOnly = true)
    public List<PaymentRecord> listPayments(UUID tenantId, UUID billId) {
        requireTenant(tenantId);
        requireId(billId, "billId");
        Map<UUID, ReceiptEntity> receiptByPaymentId = receiptRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, billId)
                .stream()
                .collect(Collectors.toMap(ReceiptEntity::getPaymentId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, billId).stream().map(payment -> toPaymentRecord(payment, receiptByPaymentId.get(payment.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<RefundRecord> listRefunds(UUID tenantId, UUID billId) {
        requireTenant(tenantId);
        requireId(billId, "billId");
        return billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(tenantId, billId).stream().map(this::toRefundRecord).toList();
    }

    @Transactional(readOnly = true)
    public List<ReceiptRecord> listReceipts(UUID tenantId, UUID billId) {
        requireTenant(tenantId);
        requireId(billId, "billId");
        return receiptRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, billId).stream().map(this::toReceiptRecord).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReceiptRecord> findReceipt(UUID tenantId, UUID receiptId) {
        requireTenant(tenantId);
        requireId(receiptId, "receiptId");
        return receiptRepository.findByTenantIdAndId(tenantId, receiptId).map(this::toReceiptRecord);
    }

    @Transactional
    public void markInvoiceEmailed(UUID tenantId, UUID billId, UUID actorAppUserId) {
        BillEntity bill = findBill(tenantId, billId);
        bill.markInvoicedByEmail();
        billRepository.save(bill);
        auditBill(tenantId, bill, "bill.invoice_emailed", actorAppUserId, "Invoice emailed");
    }

    @Transactional(readOnly = true)
    public BillPdf generateBillPdf(UUID tenantId, UUID id, UUID actorAppUserId) {
        BillEntity entity = findBill(tenantId, id);
        refreshFinancials(entity);
        BillData data = tenantData(tenantId, List.of(entity.getPatientId()));
        auditBill(tenantId, entity, "bill.pdf_generated", actorAppUserId, "Generated bill PDF");
        return buildBillPdf(data.tenantName(), entity, data);
    }

    @Transactional(readOnly = true)
    public ReceiptPdf generateReceiptPdf(UUID tenantId, UUID receiptId, UUID actorAppUserId) {
        ReceiptEntity receipt = receiptRepository.findByTenantIdAndId(tenantId, receiptId).orElseThrow(() -> new IllegalArgumentException("Receipt not found"));
        BillEntity bill = findBill(tenantId, receipt.getBillId());
        PaymentEntity payment = paymentRepository.findByTenantIdAndId(tenantId, receipt.getPaymentId()).orElse(null);
        BillData data = tenantData(tenantId, List.of(bill.getPatientId()));
        auditReceipt(tenantId, receipt, actorAppUserId, "Generated receipt PDF");
        return buildReceiptPdf(data.tenantName(), bill, receipt, payment, data);
    }

    private BillEntity findBill(UUID tenantId, UUID billId) { return billRepository.findByTenantIdAndId(tenantId, billId).orElseThrow(() -> new IllegalArgumentException("Bill not found")); }

    private void replaceLines(UUID tenantId, UUID billId, List<BillLineCommand> lines) {
        billLineRepository.deleteByTenantIdAndBillId(tenantId, billId);
        if (lines == null) return;
        int index = 1;
        for (BillLineCommand line : lines) {
            billLineRepository.save(createLineEntity(tenantId, billId, line.sortOrder() == null ? index : line.sortOrder(), line));
            index++;
        }
    }

    private BillLineEntity createLineEntity(UUID tenantId, UUID billId, int sortOrder, BillLineCommand line) {
        validateLine(line);
        BigDecimal unitPrice = normalizeMoney(resolveUnitPrice(tenantId, line));
        BigDecimal lineDiscount = normalizeMoney(line.lineDiscountAmount());
        int quantity = line.quantity() == null || line.quantity() < 1 ? 1 : line.quantity();
        BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPrice = gross.subtract(lineDiscount).setScale(2, RoundingMode.HALF_UP);
        if (totalPrice.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("lineDiscountAmount cannot exceed line amount");
        }
        return BillLineEntity.create(tenantId, billId, line.itemType(), normalize(line.itemName()), quantity, unitPrice, lineDiscount, totalPrice, normalizeNullable(line.batchNumber()), line.referenceId(), line.dispensationReferenceId(), sortOrder);
    }

    private BigDecimal resolveUnitPrice(UUID tenantId, BillLineCommand line) {
        if (line.itemType() == BillItemType.MEDICINE && line.referenceId() != null) {
            MedicineRecord medicine = inventoryService.findMedicine(tenantId, line.referenceId()).orElse(null);
            if (medicine != null && medicine.defaultPrice() != null && (line.unitPrice() == null || line.unitPrice().compareTo(ZERO) <= 0)) {
                return medicine.defaultPrice();
            }
        }
        return line.unitPrice();
    }

    private void refreshFinancials(BillEntity entity) {
        List<BillLineEntity> lines = billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(entity.getTenantId(), entity.getId());
        List<PaymentEntity> payments = paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(entity.getTenantId(), entity.getId());
        List<BillRefundEntity> refunds = billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(entity.getTenantId(), entity.getId());

        BigDecimal subtotal = lines.stream().map(BillLineEntity::getTotalPrice).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = calculateDiscount(entity.getDiscountType(), entity.getDiscountValue(), subtotal);
        BigDecimal tax = normalizeMoney(entity.getTaxAmount());
        BigDecimal total = subtotal.subtract(discount).add(tax).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(ZERO) < 0) total = ZERO;

        BigDecimal paid = payments.stream().map(PaymentEntity::getAmount).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refunded = refunds.stream().map(BillRefundEntity::getAmount).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netPaid = paid.subtract(refunded).setScale(2, RoundingMode.HALF_UP);
        BigDecimal due = isCancelledLifecycle(entity.getStatus())
                ? ZERO
                : total.subtract(paid).setScale(2, RoundingMode.HALF_UP);
        if (due.compareTo(ZERO) < 0) due = ZERO;

        entity.setFinancials(subtotal, discount, tax, total, paid, refunded, netPaid, due);
        entity.markStatus(computeStatus(entity.getStatus(), total, paid, refunded));
        billRepository.save(entity);
    }

    private BillStatus computeStatus(BillStatus currentStatus, BigDecimal total, BigDecimal paid, BigDecimal refunded) {
        boolean cancelledLifecycle = isCancelledLifecycle(currentStatus);
        BigDecimal refundable = paid.subtract(refunded).setScale(2, RoundingMode.HALF_UP);
        if (cancelledLifecycle) {
            if (paid.compareTo(ZERO) == 0 && refunded.compareTo(ZERO) == 0) return BillStatus.CANCELLED;
            if (refundable.compareTo(ZERO) <= 0) return BillStatus.CANCELLED_REFUNDED;
            return BillStatus.REFUND_PENDING;
        }
        if (paid.compareTo(ZERO) == 0 && refunded.compareTo(ZERO) == 0) return BillStatus.UNPAID;
        if (refunded.compareTo(ZERO) > 0) {
            if (refunded.compareTo(paid) >= 0) return BillStatus.REFUNDED;
            return BillStatus.PARTIALLY_REFUNDED;
        }
        if (paid.compareTo(total) >= 0) return BillStatus.PAID;
        if (paid.compareTo(ZERO) > 0 && paid.compareTo(total) < 0) return BillStatus.PARTIALLY_PAID;
        return BillStatus.UNPAID;
    }

    private BigDecimal calculateDiscount(DiscountType discountType, BigDecimal discountValue, BigDecimal subtotal) {
        DiscountType type = discountType == null ? DiscountType.NONE : discountType;
        BigDecimal value = normalizeMoney(discountValue);
        if (type == DiscountType.NONE) {
            return ZERO;
        }
        if (type == DiscountType.PERCENTAGE) {
            if (value.compareTo(ZERO) < 0 || value.compareTo(new BigDecimal("100.00")) > 0) {
                throw new IllegalArgumentException("percentage discount must be between 0 and 100");
            }
            return subtotal.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        if (value.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("discount amount cannot exceed subtotal");
        }
        return value;
    }

    private BillRecord toRecord(BillEntity entity, BillData data) {
        PatientEntity patient = data.patients().get(entity.getPatientId());
        List<BillLineRecord> lines = billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(entity.getTenantId(), entity.getId()).stream().map(line -> new BillLineRecord(
                line.getId(), line.getItemType(), line.getItemName(), line.getQuantity(), line.getUnitPrice(), line.getTotalPrice(), line.getReferenceId(), line.getSortOrder(), line.getLineDiscountAmount(), line.getBatchNumber(), line.getDispensationReferenceId()
        )).toList();
        return new BillRecord(entity.getId(), entity.getTenantId(), entity.getBillNumber(), entity.getPatientId(), patient == null ? null : patient.getPatientNumber(), patient == null ? null : patient.getFirstName() + " " + patient.getLastName(), entity.getConsultationId(), entity.getAppointmentId(), entity.getBillDate(), entity.getStatus(), entity.getSubtotalAmount(), entity.getDiscountType(), entity.getDiscountValue(), entity.getDiscountAmount(), entity.getDiscountReason(), entity.getDiscountApprovedBy(), entity.getTaxAmount(), entity.getTotalAmount(), entity.getPaidAmount(), entity.getRefundedAmount(), entity.getNetPaidAmount(), entity.getDueAmount(), entity.getInvoiceEmailedAt(), entity.getNotes(), entity.getCreatedAt(), entity.getUpdatedAt(), lines);
    }

    private List<BillRecord> mapBills(UUID tenantId, List<BillEntity> bills) {
        if (bills.isEmpty()) return List.of();
        List<UUID> patientIds = bills.stream().map(BillEntity::getPatientId).distinct().toList();
        BillData data = tenantData(tenantId, patientIds);
        return bills.stream().map(bill -> toRecord(bill, data)).toList();
    }

    private BillData tenantData(UUID tenantId, List<UUID> patientIds) {
        Map<UUID, PatientEntity> patients = patientRepository.findByTenantIdAndIdIn(tenantId, patientIds).stream().collect(Collectors.toMap(PatientEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        String clinicName = clinic == null ? null : clinic.clinicName();
        String displayName = clinic == null ? null : clinic.displayName();
        String address = clinic == null ? null : formatAddress(clinic);
        String phone = clinic == null ? null : clinic.phone();
        String email = clinic == null ? null : clinic.email();
        String tenantName = StringUtils.hasText(displayName) ? displayName : (StringUtils.hasText(clinicName) ? clinicName : "Clinic");
        return new BillData(patients, tenantName, clinicName, displayName, address, phone, email);
    }

    private void ensurePatient(UUID tenantId, UUID patientId) {
        requireId(patientId, "patientId");
        if (patientRepository.findByTenantIdAndId(tenantId, patientId).isEmpty()) throw new IllegalArgumentException("Patient not found for tenant");
    }

    private void ensureConsultationMatches(UUID tenantId, UUID consultationId, UUID patientId) {
        if (consultationId == null) return;
        ConsultationRecord consultation = consultationService.findById(tenantId, consultationId).orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        if (!consultation.patientId().equals(patientId)) throw new IllegalArgumentException("Consultation does not belong to selected patient");
    }

    private void ensureAppointmentMatches(UUID tenantId, UUID appointmentId, UUID patientId) {
        if (appointmentId == null) return;
        if (appointmentService.findById(tenantId, appointmentId).patientId() == null) throw new IllegalArgumentException("Appointment not found");
        if (!appointmentService.findById(tenantId, appointmentId).patientId().equals(patientId)) throw new IllegalArgumentException("Appointment does not belong to selected patient");
    }

    private void ensureEditable(BillEntity entity) {
        if (isClosedForLifecycle(entity.getStatus())) {
            throw new IllegalArgumentException("Closed bill cannot be modified");
        }
    }

    private void validate(BillUpsertCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        requireId(command.patientId(), "patientId");
        if (command.billDate() != null && command.billDate().isAfter(LocalDate.now())) throw new IllegalArgumentException("billDate cannot be in the future");
        if (command.discountValue() != null && !hasAtMostTwoDecimals(command.discountValue())) throw new IllegalArgumentException("discountValue can have at most 2 decimal places");
        if (command.discountValue() != null && command.discountValue().compareTo(ZERO) < 0) throw new IllegalArgumentException("discountValue cannot be negative");
        if (command.taxAmount() != null && !hasAtMostTwoDecimals(command.taxAmount())) throw new IllegalArgumentException("taxAmount can have at most 2 decimal places");
        if (command.taxAmount() != null && command.taxAmount().compareTo(ZERO) < 0) throw new IllegalArgumentException("taxAmount cannot be negative");
        if (command.lines() == null || command.lines().isEmpty()) throw new IllegalArgumentException("At least one bill line is required");
        for (BillLineCommand line : command.lines()) validateLine(line);
        validateDiscount(command.discountType(), command.discountValue(), command.discountReason());
    }

    private void validateLine(BillLineCommand line) {
        if (line == null) throw new IllegalArgumentException("bill line is required");
        if (line.itemType() == null) throw new IllegalArgumentException("itemType is required");
        if (!StringUtils.hasText(line.itemName())) throw new IllegalArgumentException("itemName is required");
        if (normalize(line.itemName()).length() > 100) throw new IllegalArgumentException("itemName cannot exceed 100 characters");
        if (!normalize(line.itemName()).matches(".*[A-Za-z0-9].*")) throw new IllegalArgumentException("itemName must contain at least one letter or number");
        if (line.quantity() == null || line.quantity() < 1) throw new IllegalArgumentException("quantity must be at least 1");
        if (line.quantity() != null && line.quantity() > 999999) throw new IllegalArgumentException("quantity cannot exceed 999999");
        if (line.unitPrice() == null || line.unitPrice().compareTo(ZERO) < 0) throw new IllegalArgumentException("unitPrice is required");
        if (!hasAtMostTwoDecimals(line.unitPrice())) throw new IllegalArgumentException("unitPrice can have at most 2 decimal places");
        if (line.unitPrice().compareTo(new BigDecimal("999999.00")) > 0) throw new IllegalArgumentException("unitPrice cannot exceed 999999");
        if (line.lineDiscountAmount() != null && line.lineDiscountAmount().compareTo(ZERO) < 0) throw new IllegalArgumentException("lineDiscountAmount cannot be negative");
        if (line.lineDiscountAmount() != null && !hasAtMostTwoDecimals(line.lineDiscountAmount())) throw new IllegalArgumentException("lineDiscountAmount can have at most 2 decimal places");
        if (line.lineDiscountAmount() != null && line.unitPrice() != null && line.quantity() != null) {
            BigDecimal gross = normalizeMoney(line.unitPrice()).multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.HALF_UP);
            if (normalizeMoney(line.lineDiscountAmount()).compareTo(gross) > 0) throw new IllegalArgumentException("lineDiscountAmount cannot exceed line amount");
        }
    }

    private void validate(PaymentCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (command.amount() == null || command.amount().compareTo(ZERO) <= 0) throw new IllegalArgumentException("amount is required");
        if (!hasAtMostTwoDecimals(command.amount())) throw new IllegalArgumentException("amount can have at most 2 decimal places");
        if (command.paymentMode() == null) throw new IllegalArgumentException("paymentMode is required");
        if (command.paymentMode() != PaymentMode.CASH && !StringUtils.hasText(command.referenceNumber())) throw new IllegalArgumentException("referenceNumber is required for non-cash payments");
        if (StringUtils.hasText(command.referenceNumber()) && normalize(command.referenceNumber()).length() > 60) throw new IllegalArgumentException("referenceNumber cannot exceed 60 characters");
    }

    private Optional<BillEntity> findConsultationFeeBill(UUID tenantId, UUID appointmentId) {
        return consultationFeeBills(tenantId, appointmentId).stream().findFirst();
    }

    private ConsultationFeeAssessment assessConsultationFee(UUID tenantId, UUID appointmentId, BigDecimal consultationFee) {
        BigDecimal configuredFee = normalizeMoney(consultationFee);
        List<BillEntity> consultationBills = consultationFeeBills(tenantId, appointmentId);
        BigDecimal aggregateNetPaid = consultationBills.stream()
                .map(BillEntity::getNetPaidAmount)
                .map(this::normalizeMoney)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingDue = configuredFee.subtract(aggregateNetPaid).setScale(2, RoundingMode.HALF_UP);
        if (remainingDue.compareTo(ZERO) < 0) {
            remainingDue = ZERO;
        }

        BillEntity reusableBill = consultationBills.stream()
                .filter(bill -> normalizeMoney(bill.getDueAmount()).compareTo(ZERO) > 0)
                .sorted((left, right) -> normalizeMoney(left.getDueAmount()).compareTo(normalizeMoney(right.getDueAmount())))
                .findFirst()
                .orElse(consultationBills.stream().findFirst().orElse(null));
        return new ConsultationFeeAssessment(configuredFee, aggregateNetPaid, remainingDue, reusableBill);
    }

    private List<BillEntity> consultationFeeBills(UUID tenantId, UUID appointmentId) {
        List<BillEntity> bills = billRepository.findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, appointmentId);
        List<BillEntity> consultationBills = new ArrayList<>();
        for (BillEntity bill : bills) {
            if (isCancelledLifecycle(bill.getStatus())) {
                continue;
            }
            boolean consultationLine = billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, bill.getId()).stream()
                    .anyMatch(line -> line.getItemType() == BillItemType.CONSULTATION);
            if (!consultationLine) {
                continue;
            }
            refreshFinancials(bill);
            consultationBills.add(bill);
        }
        return consultationBills;
    }

    private boolean isCancelledLifecycle(BillStatus status) {
        return status == BillStatus.CANCELLED || status == BillStatus.REFUND_PENDING || status == BillStatus.CANCELLED_REFUNDED;
    }

    private boolean isClosedForLifecycle(BillStatus status) {
        return isCancelledLifecycle(status) || status == BillStatus.PAID || status == BillStatus.REFUNDED;
    }

    private BigDecimal refundableAmount(BillEntity bill) {
        return normalizeMoney(bill.getPaidAmount()).subtract(normalizeMoney(bill.getRefundedAmount())).setScale(2, RoundingMode.HALF_UP);
    }

    private record ConsultationFeeAssessment(
            BigDecimal configuredFee,
            BigDecimal aggregateNetPaid,
            BigDecimal remainingDue,
            BillEntity reusableBill
    ) {
    }

    private void validate(RefundCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (command.amount() == null || command.amount().compareTo(ZERO) <= 0) throw new IllegalArgumentException("refund amount must be positive");
        if (!hasAtMostTwoDecimals(command.amount())) throw new IllegalArgumentException("refund amount can have at most 2 decimal places");
        if (!StringUtils.hasText(command.reason())) throw new IllegalArgumentException("refund reason is required");
        if (normalize(command.reason()).length() > 100) throw new IllegalArgumentException("refund reason cannot exceed 100 characters");
    }

    private void validateDiscount(DiscountType discountType, BigDecimal discountValue, String discountReason) {
        DiscountType type = discountType == null ? DiscountType.NONE : discountType;
        BigDecimal value = normalizeMoney(discountValue);
        if (value.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("discount value cannot be negative");
        }
        if (type == DiscountType.NONE && value.compareTo(ZERO) > 0) {
            throw new IllegalArgumentException("discount value must be blank or zero when discount type is NONE");
        }
        if (type == DiscountType.PERCENTAGE && (value.compareTo(ZERO) < 0 || value.compareTo(new BigDecimal("100.00")) > 0)) {
            throw new IllegalArgumentException("percentage discount must be between 0 and 100");
        }
        if (type == DiscountType.AMOUNT && value.compareTo(ZERO) > 0 && value.compareTo(new BigDecimal("999999.00")) > 0) {
            throw new IllegalArgumentException("discount amount cannot exceed 999999");
        }
        if (type != DiscountType.NONE && value.compareTo(ZERO) > 0 && !StringUtils.hasText(discountReason)) {
            throw new IllegalArgumentException("discount reason is required when discount > 0");
        }
        if (StringUtils.hasText(discountReason) && normalize(discountReason).length() > 60) {
            throw new IllegalArgumentException("discount reason cannot exceed 60 characters");
        }
    }

    private void ensureNotDuplicateSubmission(UUID tenantId, BillUpsertCommand command) {
        List<BillEntity> existingBills = billRepository.findByTenantIdAndPatientIdOrderByBillDateDescCreatedAtDesc(tenantId, command.patientId());
        for (BillEntity existing : existingBills) {
            if (!java.util.Objects.equals(existing.getBillDate(), command.billDate())) {
                continue;
            }
            if (!java.util.Objects.equals(existing.getConsultationId(), command.consultationId())) {
                continue;
            }
            if (!java.util.Objects.equals(existing.getAppointmentId(), command.appointmentId())) {
                continue;
            }
            if (!java.util.Objects.equals(existing.getDiscountType(), command.discountType() == null ? DiscountType.NONE : command.discountType())) {
                continue;
            }
            if (normalizeMoney(existing.getDiscountValue()).compareTo(normalizeMoney(command.discountValue())) != 0) {
                continue;
            }
            if (!java.util.Objects.equals(normalize(existing.getDiscountReason()), normalize(command.discountReason()))) {
                continue;
            }
            if (normalizeMoney(existing.getTaxAmount()).compareTo(normalizeMoney(command.taxAmount())) != 0) {
                continue;
            }
            if (!java.util.Objects.equals(normalize(existing.getNotes()), normalize(command.notes()))) {
                continue;
            }
            List<BillLineEntity> existingLines = billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, existing.getId());
            if (existingLines.size() != command.lines().size()) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < existingLines.size(); i++) {
                BillLineEntity currentLine = existingLines.get(i);
                BillLineCommand newLine = command.lines().get(i);
                if (currentLine.getItemType() != newLine.itemType()
                        || !normalize(currentLine.getItemName()).equals(normalize(newLine.itemName()))
                        || !java.util.Objects.equals(currentLine.getQuantity(), newLine.quantity())
                        || normalizeMoney(currentLine.getUnitPrice()).compareTo(normalizeMoney(newLine.unitPrice())) != 0
                        || normalizeMoney(currentLine.getLineDiscountAmount()).compareTo(normalizeMoney(newLine.lineDiscountAmount())) != 0
                        || !java.util.Objects.equals(normalize(currentLine.getBatchNumber()), normalize(newLine.batchNumber()))
                        || !java.util.Objects.equals(currentLine.getReferenceId(), newLine.referenceId())
                        || !java.util.Objects.equals(currentLine.getDispensationReferenceId(), newLine.dispensationReferenceId())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                throw new IllegalArgumentException("Duplicate bill submission");
            }
        }
    }

    private void auditBill(UUID tenantId, BillEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, ENTITY_TYPE, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditPayment(UUID tenantId, PaymentEntity payment, ReceiptEntity receipt, UUID actorAppUserId, String message) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentId", payment.getId());
        details.put("billId", payment.getBillId());
        details.put("receiptId", receipt.getId());
        details.put("receiptNumber", receipt.getReceiptNumber());
        details.put("amount", payment.getAmount());
        try {
            auditEventPublisher.record(new AuditEventCommand(tenantId, "PAYMENT", payment.getId(), "payment.collected", actorAppUserId, OffsetDateTime.now(), message, objectMapper.writeValueAsString(details)));
            auditEventPublisher.record(new AuditEventCommand(tenantId, "RECEIPT", receipt.getId(), "receipt.generated", actorAppUserId, OffsetDateTime.now(), "Generated receipt", objectMapper.writeValueAsString(details)));
        } catch (JsonProcessingException ex) {
            auditEventPublisher.record(new AuditEventCommand(tenantId, "PAYMENT", payment.getId(), "payment.collected", actorAppUserId, OffsetDateTime.now(), message, "{\"paymentId\":\"" + payment.getId() + "\"}"));
        }
    }

    private void auditReceipt(UUID tenantId, ReceiptEntity receipt, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, "RECEIPT", receipt.getId(), "receipt.pdf_generated", actorAppUserId, OffsetDateTime.now(), message, detailsJson(receipt)));
    }

    private boolean hasAtMostTwoDecimals(BigDecimal value) {
        return value == null || value.stripTrailingZeros().scale() <= 2;
    }

    private String detailsJson(BillEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("billNumber", entity.getBillNumber());
        details.put("patientId", entity.getPatientId());
        details.put("status", entity.getStatus());
        details.put("totalAmount", entity.getTotalAmount());
        details.put("paidAmount", entity.getPaidAmount());
        details.put("refundedAmount", entity.getRefundedAmount());
        details.put("dueAmount", entity.getDueAmount());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private String detailsJson(ReceiptEntity receipt) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", receipt.getId());
        details.put("receiptNumber", receipt.getReceiptNumber());
        details.put("billId", receipt.getBillId());
        details.put("paymentId", receipt.getPaymentId());
        details.put("amount", receipt.getAmount());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + receipt.getId() + "\"}";
        }
    }

    private String detailsJson(BillRefundEntity refund) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", refund.getId());
        details.put("billId", refund.getBillId());
        details.put("paymentId", refund.getPaymentId());
        details.put("amount", refund.getAmount());
        details.put("reason", refund.getReason());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + refund.getId() + "\"}";
        }
    }

    private String generateBillNumber(UUID tenantId) { for (int attempt = 0; attempt < 8; attempt++) { String candidate = "BILL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); if (billRepository.findByTenantIdAndBillNumber(tenantId, candidate).isEmpty()) return candidate; } throw new IllegalStateException("Unable to generate bill number"); }
    private String generateReceiptNumber(UUID tenantId) { for (int attempt = 0; attempt < 8; attempt++) { String candidate = "RCT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); if (receiptRepository.findByTenantIdAndReceiptNumber(tenantId, candidate).isEmpty()) return candidate; } throw new IllegalStateException("Unable to generate receipt number"); }

    private PaymentRecord toPaymentRecord(PaymentEntity payment, ReceiptEntity receipt) {
        return new PaymentRecord(payment.getId(), payment.getTenantId(), payment.getBillId(), payment.getPaymentDate(), payment.getPaymentDateTime(), payment.getAmount(), payment.getPaymentMode(), payment.getReferenceNumber(), payment.getNotes(), payment.getReceivedBy(), receipt == null ? null : receipt.getId(), receipt == null ? null : receipt.getReceiptNumber(), receipt == null ? null : receipt.getReceiptDate(), payment.getCreatedAt());
    }

    private RefundRecord toRefundRecord(BillRefundEntity refund) {
        return new RefundRecord(refund.getId(), refund.getTenantId(), refund.getBillId(), refund.getPaymentId(), refund.getAmount(), refund.getReason(), refund.getRefundMode(), refund.getNotes(), refund.getRefundedBy(), refund.getRefundedAt(), refund.getCreatedAt());
    }

    private ReceiptRecord toReceiptRecord(ReceiptEntity receipt) {
        return new ReceiptRecord(receipt.getId(), receipt.getTenantId(), receipt.getReceiptNumber(), receipt.getBillId(), receipt.getPaymentId(), receipt.getReceiptDate(), receipt.getAmount(), receipt.getCreatedAt());
    }

    private BillPdf buildBillPdf(String tenantName, BillEntity entity, BillData data) {
        BillRecord record = toRecord(entity, data);
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(entity.getTenantId()).orElse(null);
        AppointmentRecord appointment = record.appointmentId() == null ? null : appointmentService.findById(entity.getTenantId(), record.appointmentId());
        ConsultationRecord consultation = record.consultationId() == null ? null : consultationService.findById(entity.getTenantId(), record.consultationId()).orElse(null);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 28f;
                float width = page.getMediaBox().getWidth() - (margin * 2);
                float y = page.getMediaBox().getHeight() - margin;

                drawDocumentFrame(content, page, margin);
                y = drawHeader(content, tenantName, "INVOICE", clinic, page, margin, y);
                y -= 4;
                y = drawBillMeta(content, record, appointment, consultation, margin, width, y);
                y -= 8;
                y = drawLineItemsTable(content, record, margin, width, y);
                y -= 8;
                y = drawSummaryBlock(content, record, margin, width, y);
                y -= 8;
                drawFooter(content, brandingProperties.footerLine(), margin, y);
            }
            document.save(output);
            return new BillPdf(safeFilename(record.billNumber()) + ".pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate bill PDF", ex);
        }
    }

    private ReceiptPdf buildReceiptPdf(String tenantName, BillEntity bill, ReceiptEntity receipt, PaymentEntity payment, BillData data) {
        BillRecord record = toRecord(bill, data);
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(bill.getTenantId()).orElse(null);
        AppointmentRecord appointment = record.appointmentId() == null ? null : appointmentService.findById(bill.getTenantId(), record.appointmentId());
        ConsultationRecord consultation = record.consultationId() == null ? null : consultationService.findById(bill.getTenantId(), record.consultationId()).orElse(null);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 28f;
                float width = page.getMediaBox().getWidth() - (margin * 2);
                float y = page.getMediaBox().getHeight() - margin;

                drawDocumentFrame(content, page, margin);
                y = drawHeader(content, tenantName, "RECEIPT", clinic, page, margin, y);
                y -= 4;
                y = drawReceiptMeta(content, record, receipt, payment, appointment, consultation, margin, width, y);
                y -= 8;
                y = drawReceiptBody(content, record, receipt, payment, margin, width, y);
                y -= 8;
                drawFooter(content, brandingProperties.footerLine(), margin, y);
            }
            document.save(output);
            return new ReceiptPdf(safeFilename(receipt.getReceiptNumber()) + ".pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate receipt PDF", ex);
        }
    }

    private float drawHeader(PDPageContentStream content, String clinicName, String title, ClinicProfileRecord clinic, PDPage page, float margin, float y) throws IOException {
        float pageWidth = page.getMediaBox().getWidth();
        float leftX = margin + 8;
        float rightX = pageWidth - margin - 8;
        setFillColor(content, 14, 165, 233);
        content.addRect(pageWidth - 112, page.getMediaBox().getHeight() - 112, 112, 112);
        content.fill();
        setFillColor(content, 244, 114, 182);
        content.addRect(margin, page.getMediaBox().getHeight() - 112, 92, 92);
        content.fill();
        setFillColor(content, 255, 255, 255);
        content.addRect(pageWidth - 118, page.getMediaBox().getHeight() - 118, 106, 106);
        content.fill();
        setFillColor(content, 18, 33, 43);

        writeLine(content, safe(clinicName), 15, leftX, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        y -= 15;
        writeWrapped(content, clinicLine(clinic), 9, leftX, y, pageWidth * 0.52f);
        y -= 10;
        writeWrapped(content, clinicContact(clinic), 9, leftX, y, pageWidth * 0.52f);
        y -= 12;
        writeLine(content, title, 25, rightX - 170, page.getMediaBox().getHeight() - margin - 4, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeLine(content, "A4 printable document", 9, rightX - 128, page.getMediaBox().getHeight() - margin - 22, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        return y - 6;
    }

    private float drawBillMeta(PDPageContentStream content, BillRecord record, AppointmentRecord appointment, ConsultationRecord consultation, float margin, float width, float y) throws IOException {
        List<MetaPair> pairs = List.of(
                new MetaPair("Invoice No", record.billNumber()),
                new MetaPair("Bill Date", formatDate(record.billDate())),
                new MetaPair("Patient", safe(record.patientName())),
                new MetaPair("Patient ID", safe(record.patientNumber())),
                new MetaPair("Mobile", patientMobile(record.patientId(), record.patientName())),
                new MetaPair("Doctor", doctorName(appointment, consultation)),
                new MetaPair("Appointment", appointmentSummary(appointment, consultation))
        );
        return drawMetaRows(content, pairs, margin, width, y);
    }

    private float drawReceiptMeta(PDPageContentStream content, BillRecord record, ReceiptEntity receipt, PaymentEntity payment, AppointmentRecord appointment, ConsultationRecord consultation, float margin, float width, float y) throws IOException {
        List<MetaPair> pairs = List.of(
                new MetaPair("Receipt No", safe(receipt.getReceiptNumber())),
                new MetaPair("Payment Date", payment == null ? formatDate(receipt.getReceiptDate()) : formatPaymentTimestamp(payment)),
                new MetaPair("Patient", safe(record.patientName())),
                new MetaPair("Bill No", record.billNumber()),
                new MetaPair("Payment Mode", payment == null || payment.getPaymentMode() == null ? "—" : payment.getPaymentMode().name()),
                new MetaPair("Amount Paid", money(receipt.getAmount())),
                new MetaPair("Remaining Due", money(record.dueAmount())),
                new MetaPair("Received By", receivedBy(payment))
        );
        return drawMetaRows(content, pairs, margin, width, y);
    }

    private float drawMetaRows(PDPageContentStream content, List<MetaPair> pairs, float margin, float width, float y) throws IOException {
        float cellWidth = width / 2f;
        float labelWidth = 78f;
        float rowHeight = 14f;
        for (int i = 0; i < pairs.size(); i += 2) {
            MetaPair left = pairs.get(i);
            MetaPair right = i + 1 < pairs.size() ? pairs.get(i + 1) : null;
            float rowY = y;
            drawKeyValue(content, left, margin + 4, rowY, cellWidth - 10, labelWidth);
            if (right != null) {
                drawKeyValue(content, right, margin + cellWidth + 6, rowY, cellWidth - 10, labelWidth);
            }
            y -= rowHeight;
        }
        return y - 4;
    }

    private void drawKeyValue(PDPageContentStream content, MetaPair pair, float x, float y, float width, float labelWidth) throws IOException {
        writeLine(content, pair.label() + ":", 9, x, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeWrapped(content, safe(pair.value()), 9, x + labelWidth, y, width - labelWidth);
    }

    private float drawLineItemsTable(PDPageContentStream content, BillRecord record, float margin, float width, float y) throws IOException {
        float[] cols = new float[] { 20f, 165f, 38f, 58f, 58f, 48f, 60f };
        float tableWidth = 447f;
        float startX = margin + 4;
        float rowH = 15f;
        float headerH = 18f;
        String[] headers = { "No", "Description", "Qty", "Unit", "Disc", "Tax", "Subtot" };

        setFillColor(content, 238, 248, 246);
        content.addRect(startX, y - headerH + 3, tableWidth, headerH);
        content.fill();
        setFillColor(content, 18, 33, 43);

        float x = startX;
        for (int i = 0; i < headers.length; i++) {
            writeLine(content, headers[i], 8.5f, x + 2, y - 8, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            x += cols[i];
        }
        y -= headerH;

        int index = 1;
        for (BillLineRecord line : record.lines()) {
            String description = safe(line.itemName());
            String itemType = safe(line.itemType() == null ? null : line.itemType().name());
            String desc = itemType.isBlank() ? description : description + " (" + itemType + ")";
            if (y < 120) {
                break;
            }
            x = startX;
            writeLine(content, String.valueOf(index), 8.5f, x + 2, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            x += cols[0];
            writeWrapped(content, desc, 8.5f, x + 2, y - 10, cols[1] - 4);
            x += cols[1];
            writeLine(content, String.valueOf(line.quantity()), 8.5f, x + 2, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            x += cols[2];
            writeLine(content, money(line.unitPrice()), 8.5f, x + 2, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            x += cols[3];
            writeLine(content, money(line.lineDiscountAmount()), 8.5f, x + 2, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            x += cols[4];
            writeLine(content, money(lineTaxShare(record, line.totalPrice())), 8.5f, x + 2, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            x += cols[5];
            writeLine(content, money(line.totalPrice()), 8.5f, x + 2, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            y -= rowH;
            index++;
        }

        return y - 4;
    }

    private float drawSummaryBlock(PDPageContentStream content, BillRecord record, float margin, float width, float y) throws IOException {
        float boxWidth = 220f;
        float boxX = margin + width - boxWidth + 4;
        float boxY = y - 4;
        float boxH = 82f;
        setFillColor(content, 250, 250, 250);
        content.addRect(boxX, boxY - boxH, boxWidth, boxH);
        content.fill();
        setStrokeColor(content, 220, 227, 232);
        content.addRect(boxX, boxY - boxH, boxWidth, boxH);
        content.stroke();
        setFillColor(content, 18, 33, 43);

        float lineY = boxY - 14;
        lineY = writeSummaryRow(content, "Subtotal", money(record.subtotalAmount()), boxX + 10, lineY, false);
        lineY = writeSummaryRow(content, "Discount", money(record.discountAmount()), boxX + 10, lineY, false);
        lineY = writeSummaryRow(content, "Tax", money(record.taxAmount()), boxX + 10, lineY, false);
        lineY = writeSummaryRow(content, "Grand Total", money(record.totalAmount()), boxX + 10, lineY, true);
        lineY = writeSummaryRow(content, "Paid", money(record.paidAmount()), boxX + 10, lineY, false);
        writeSummaryRow(content, "Due", money(record.dueAmount()), boxX + 10, lineY, true);

        float noteX = margin + 4;
        float noteW = width - boxWidth - 16;
        writeLine(content, "Notes", 9, noteX, boxY - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeWrapped(content, safe(record.notes()), 8.5f, noteX, boxY - 22, noteW);
        if (StringUtils.hasText(record.discountReason())) {
            writeWrapped(content, "Discount reason: " + record.discountReason(), 8.5f, noteX, boxY - 36, noteW);
        }
        return y - boxH - 4;
    }

    private float drawReceiptBody(PDPageContentStream content, BillRecord record, ReceiptEntity receipt, PaymentEntity payment, float margin, float width, float y) throws IOException {
        float bodyX = margin + 4;
        float bodyW = width - 8;
        float summaryBoxW = Math.min(220f, bodyW * 0.42f);
        float noteX = bodyX + summaryBoxW + 12;
        float noteW = Math.max(150f, bodyW - summaryBoxW - 20);
        float boxH = 84f;
        setFillColor(content, 249, 249, 249);
        content.addRect(bodyX, y - boxH, bodyW, boxH);
        content.fill();
        setStrokeColor(content, 220, 227, 232);
        content.addRect(bodyX, y - boxH, bodyW, boxH);
        content.stroke();
        setFillColor(content, 18, 33, 43);

        float lineY = y - 14;
        lineY = writeSummaryRow(content, "Amount Paid", money(receipt.getAmount()), bodyX + 10, lineY, true);
        lineY = writeSummaryRow(content, "Remaining Due", money(record.dueAmount()), bodyX + 10, lineY, true);
        lineY = writeSummaryRow(content, "Payment Mode", payment == null || payment.getPaymentMode() == null ? "—" : payment.getPaymentMode().name(), bodyX + 10, lineY, false);
        writeSummaryRow(content, "Received By", receivedBy(payment), bodyX + 10, lineY, false);

        writeLine(content, "Payment Notes", 9, noteX, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        float notesY = y - 22;
        float afterNotesY = writeWrapped(content, safe(payment == null ? null : payment.getNotes()), 8.5f, noteX, notesY, noteW);
        if (afterNotesY == notesY) {
            writeLine(content, "—", 8.5f, noteX, notesY, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        }
        return y - boxH - 4;
    }

    private float writeSummaryRow(PDPageContentStream content, String label, String value, float x, float y, boolean bold) throws IOException {
        writeLine(content, label + ":", 8.75f, x, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        writeLine(content, value, bold ? 9.5f : 8.75f, x + 76, y, bold ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD) : new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        return y - 12;
    }

    private void drawFooter(PDPageContentStream content, String text, float margin, float y) throws IOException {
        writeWrapped(content, text, 8.25f, margin + 8, Math.max(40, y), 520);
    }

    private void drawDocumentFrame(PDPageContentStream content, PDPage page, float margin) throws IOException {
        float w = page.getMediaBox().getWidth();
        float h = page.getMediaBox().getHeight();
        setStrokeColor(content, 187, 199, 205);
        content.addRect(margin, margin, w - (margin * 2), h - (margin * 2));
        content.stroke();
    }

    private void setFillColor(PDPageContentStream content, int red, int green, int blue) throws IOException {
        content.setNonStrokingColor(pdfColor(red), pdfColor(green), pdfColor(blue));
    }

    private void setStrokeColor(PDPageContentStream content, int red, int green, int blue) throws IOException {
        content.setStrokingColor(pdfColor(red), pdfColor(green), pdfColor(blue));
    }

    private float pdfColor(int channel) {
        return Math.max(0, Math.min(255, channel)) / 255f;
    }

    private BigDecimal lineTaxShare(BillRecord record, BigDecimal lineTotal) {
        if (record.taxAmount() == null || record.taxAmount().compareTo(ZERO) <= 0) return ZERO;
        if (record.subtotalAmount() == null || record.subtotalAmount().compareTo(ZERO) <= 0) return ZERO;
        return record.taxAmount().multiply(lineTotal).divide(record.subtotalAmount(), 2, RoundingMode.HALF_UP);
    }

    private float writeWrapped(PDPageContentStream content, String text, float fontSize, float x, float y, float maxWidth) throws IOException {
        if (!StringUtils.hasText(text)) return y;
        for (String line : wrap(text, Math.max(24, Math.round(maxWidth / (fontSize * 0.55f))))) {
            writeLine(content, line, fontSize, x, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            y -= fontSize + 3;
        }
        return y;
    }

    private void writeLine(PDPageContentStream content, String text, float fontSize, float x, float y, PDType1Font font) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text == null ? "" : text);
        content.endText();
    }

    private List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.length() == 0) current.append(word);
            else if (current.length() + 1 + word.length() <= maxChars) current.append(' ').append(word);
            else { lines.add(current.toString()); current.setLength(0); current.append(word); }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private String safe(String value) { return value == null ? "" : value; }
    private String safeFilename(String value) { return value == null ? "document" : value.replaceAll("[^a-zA-Z0-9-_]+", "-").replaceAll("-+", "-"); }
    private String normalize(String value) { return value == null ? null : value.trim(); }
    private String normalizeNullable(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private BigDecimal normalizeMoney(BigDecimal value) { return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    private void requireTenant(UUID tenantId) { if (tenantId == null) throw new IllegalArgumentException("tenantId is required"); }
    private void requireId(UUID id, String field) { if (id == null) throw new IllegalArgumentException(field + " is required"); }

    private String formatAddress(ClinicProfileRecord clinic) {
        if (clinic == null) return null;
        List<String> parts = java.util.stream.Stream.of(clinic.addressLine1(), clinic.addressLine2(), clinic.city(), clinic.state(), clinic.country(), clinic.postalCode()).filter(StringUtils::hasText).toList();
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String clinicLine(ClinicProfileRecord clinic) {
        if (clinic == null) return "";
        return StringUtils.hasText(clinic.registrationNumber()) ? "Reg: " + clinic.registrationNumber() : "";
    }

    private String clinicContact(ClinicProfileRecord clinic) {
        if (clinic == null) return "";
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(clinic.phone())) parts.add("Phone: " + clinic.phone());
        if (StringUtils.hasText(clinic.email())) parts.add("Email: " + clinic.email());
        if (StringUtils.hasText(clinic.addressLine1()) || StringUtils.hasText(clinic.city()) || StringUtils.hasText(clinic.state())) {
            parts.add(formatAddress(clinic));
        }
        return String.join("  |  ", parts);
    }

    private String patientMobile(UUID patientId, String patientName) {
        if (patientId == null) return "—";
        PatientEntity patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null || !StringUtils.hasText(patient.getMobile())) return "—";
        return patient.getMobile();
    }

    private String doctorName(AppointmentRecord appointment, ConsultationRecord consultation) {
        if (appointment != null && StringUtils.hasText(appointment.doctorName())) return appointment.doctorName();
        if (consultation != null && StringUtils.hasText(consultation.doctorName())) return consultation.doctorName();
        return "—";
    }

    private String appointmentSummary(AppointmentRecord appointment, ConsultationRecord consultation) {
        if (appointment == null && consultation == null) return "—";
        List<String> parts = new ArrayList<>();
        if (appointment != null && appointment.appointmentDate() != null) parts.add(appointment.appointmentDate().format(PDF_DATE));
        if (appointment != null && appointment.appointmentTime() != null) parts.add(appointment.appointmentTime().format(PDF_TIME));
        String doctor = doctorName(appointment, consultation);
        if (StringUtils.hasText(doctor) && !"—".equals(doctor)) parts.add(doctor);
        String status = appointment != null && appointment.status() != null ? appointment.status().name().replace('_', ' ') : consultation != null && consultation.status() != null ? consultation.status().name().replace('_', ' ') : null;
        if (StringUtils.hasText(status)) parts.add(status);
        return parts.isEmpty() ? "—" : String.join(" · ", parts);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "—" : date.format(PDF_DATE);
    }

    private String formatDateTime(OffsetDateTime dateTime) {
        return dateTime == null ? "—" : dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm a", Locale.ENGLISH));
    }

    private String formatPaymentTimestamp(PaymentEntity payment) {
        if (payment == null) {
            return "—";
        }
        if (payment.getPaymentDateTime() != null) {
            return formatDateTime(payment.getPaymentDateTime());
        }
        return formatDate(payment.getPaymentDate());
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String receivedBy(PaymentEntity payment) {
        if (payment == null || payment.getReceivedBy() == null) return "—";
        return payment.getReceivedBy().toString();
    }

    private record MetaPair(String label, String value) {}

    private record BillData(Map<UUID, PatientEntity> patients, String tenantName, String clinicName, String clinicDisplayNameValue, String clinicAddressValue, String clinicPhoneValue, String clinicEmailValue) {
        String clinicDisplayName() { return StringUtils.hasText(clinicDisplayNameValue) ? clinicDisplayNameValue : clinicName; }
        String clinicAddress() { return clinicAddressValue; }
    }
}
