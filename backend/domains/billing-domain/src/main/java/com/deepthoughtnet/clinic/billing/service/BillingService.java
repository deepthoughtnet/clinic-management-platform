package com.deepthoughtnet.clinic.billing.service;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
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
import com.deepthoughtnet.clinic.billing.service.model.ReceiptPdf;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.billing.service.model.RefundCommand;
import com.deepthoughtnet.clinic.billing.service.model.RefundRecord;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    private final BillRepository billRepository;
    private final BillLineRepository billLineRepository;
    private final PaymentRepository paymentRepository;
    private final BillRefundRepository billRefundRepository;
    private final ReceiptRepository receiptRepository;
    private final PatientRepository patientRepository;
    private final ClinicProfileService clinicProfileService;
    private final ConsultationService consultationService;
    private final AppointmentService appointmentService;
    private final InventoryService inventoryService;
    @SuppressWarnings("unused")
    private final TenantUserManagementService tenantUserManagementService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public BillingService(
            BillRepository billRepository,
            BillLineRepository billLineRepository,
            PaymentRepository paymentRepository,
            BillRefundRepository billRefundRepository,
            ReceiptRepository receiptRepository,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            ConsultationService consultationService,
            AppointmentService appointmentService,
            InventoryService inventoryService,
            TenantUserManagementService tenantUserManagementService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.billRepository = billRepository;
        this.billLineRepository = billLineRepository;
        this.paymentRepository = paymentRepository;
        this.billRefundRepository = billRefundRepository;
        this.receiptRepository = receiptRepository;
        this.patientRepository = patientRepository;
        this.clinicProfileService = clinicProfileService;
        this.consultationService = consultationService;
        this.appointmentService = appointmentService;
        this.inventoryService = inventoryService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<BillRecord> list(UUID tenantId, BillingSearchCriteria criteria) {
        requireTenant(tenantId);
        BillingSearchCriteria safe = criteria == null ? new BillingSearchCriteria(null, null, null, null, null) : criteria;
        return mapBills(tenantId, billRepository.search(tenantId, safe.patientId(), safe.status(), safe.fromDate(), safe.toDate(), safe.paymentMode()));
    }

    @Transactional(readOnly = true)
    public Optional<BillRecord> findById(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return billRepository.findByTenantIdAndId(tenantId, id).map(entity -> toRecord(entity, tenantData(tenantId, List.of(entity.getPatientId()))));
    }

    @Transactional
    public BillRecord createDraft(UUID tenantId, BillUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validate(command);
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
        if (entity.getStatus() == BillStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled bill cannot be issued");
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
        if (entity.getStatus() == BillStatus.PAID) {
            throw new IllegalArgumentException("Paid bill cannot be cancelled");
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
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled bill cannot accept payments");
        }
        if (bill.getStatus() == BillStatus.DRAFT) {
            bill.markStatus(BillStatus.ISSUED);
            billRepository.save(bill);
        }
        refreshFinancials(bill);
        if (command.amount().compareTo(bill.getDueAmount()) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed bill due amount");
        }
        PaymentEntity payment = paymentRepository.save(PaymentEntity.create(
                tenantId,
                billId,
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
                billId,
                payment.getId(),
                payment.getPaymentDate(),
                normalizeMoney(command.amount())
        ));
        refreshFinancials(bill);
        auditPayment(tenantId, payment, receipt, actorAppUserId, "Collected bill payment");
        return toPaymentRecord(payment, receipt);
    }

    @Transactional
    public RefundRecord refund(UUID tenantId, UUID billId, RefundCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(billId, "billId");
        validate(command);
        BillEntity bill = findBill(tenantId, billId);
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled bill cannot be refunded");
        }
        refreshFinancials(bill);
        BigDecimal refundable = normalizeMoney(bill.getPaidAmount()).subtract(normalizeMoney(bill.getRefundedAmount())).setScale(2, RoundingMode.HALF_UP);
        if (command.amount().compareTo(refundable) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed paid amount minus previous refunds");
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
        BigDecimal due = total.subtract(netPaid).setScale(2, RoundingMode.HALF_UP);
        if (due.compareTo(ZERO) < 0) due = ZERO;

        entity.setFinancials(subtotal, discount, tax, total, paid, refunded, netPaid, due);
        if (entity.getStatus() != BillStatus.CANCELLED) {
            entity.markStatus(computeStatus(total, paid, refunded));
        }
        billRepository.save(entity);
    }

    private BillStatus computeStatus(BigDecimal total, BigDecimal paid, BigDecimal refunded) {
        BigDecimal netPaid = paid.subtract(refunded).setScale(2, RoundingMode.HALF_UP);
        if (paid.compareTo(ZERO) == 0 && refunded.compareTo(ZERO) == 0) return BillStatus.UNPAID;
        if (refunded.compareTo(ZERO) > 0) {
            if (refunded.compareTo(paid) >= 0) return BillStatus.REFUNDED;
            return BillStatus.PARTIALLY_REFUNDED;
        }
        if (netPaid.compareTo(total) >= 0) return BillStatus.PAID;
        if (netPaid.compareTo(ZERO) > 0 && netPaid.compareTo(total) < 0) return BillStatus.PARTIALLY_PAID;
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
        String tenantName = StringUtils.hasText(displayName) ? displayName : (StringUtils.hasText(clinicName) ? clinicName : "Clinic");
        return new BillData(patients, tenantName, clinicName, displayName, address);
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
        if (entity.getStatus() == BillStatus.CANCELLED || entity.getStatus() == BillStatus.PAID || entity.getStatus() == BillStatus.REFUNDED) {
            throw new IllegalArgumentException("Closed bill cannot be modified");
        }
    }

    private void validate(BillUpsertCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        requireId(command.patientId(), "patientId");
        if (command.lines() == null || command.lines().isEmpty()) throw new IllegalArgumentException("At least one bill line is required");
        for (BillLineCommand line : command.lines()) validateLine(line);
        validateDiscount(command.discountType(), command.discountValue(), command.discountReason());
    }

    private void validateLine(BillLineCommand line) {
        if (line == null) throw new IllegalArgumentException("bill line is required");
        if (line.itemType() == null) throw new IllegalArgumentException("itemType is required");
        if (!StringUtils.hasText(line.itemName())) throw new IllegalArgumentException("itemName is required");
        if (line.quantity() == null || line.quantity() < 1) throw new IllegalArgumentException("quantity must be at least 1");
        if (line.unitPrice() == null || line.unitPrice().compareTo(ZERO) < 0) throw new IllegalArgumentException("unitPrice is required");
        if (line.lineDiscountAmount() != null && line.lineDiscountAmount().compareTo(ZERO) < 0) throw new IllegalArgumentException("lineDiscountAmount cannot be negative");
    }

    private void validate(PaymentCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (command.amount() == null || command.amount().compareTo(ZERO) <= 0) throw new IllegalArgumentException("amount is required");
        if (command.paymentMode() == null) throw new IllegalArgumentException("paymentMode is required");
        if (command.paymentMode() != PaymentMode.CASH && !StringUtils.hasText(command.referenceNumber())) throw new IllegalArgumentException("referenceNumber is required for non-cash payments");
    }

    private void validate(RefundCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (command.amount() == null || command.amount().compareTo(ZERO) <= 0) throw new IllegalArgumentException("refund amount must be positive");
        if (!StringUtils.hasText(command.reason())) throw new IllegalArgumentException("refund reason is required");
    }

    private void validateDiscount(DiscountType discountType, BigDecimal discountValue, String discountReason) {
        DiscountType type = discountType == null ? DiscountType.NONE : discountType;
        BigDecimal value = normalizeMoney(discountValue);
        if (type == DiscountType.PERCENTAGE && (value.compareTo(ZERO) < 0 || value.compareTo(new BigDecimal("100.00")) > 0)) {
            throw new IllegalArgumentException("percentage discount must be between 0 and 100");
        }
        if (type != DiscountType.NONE && value.compareTo(ZERO) > 0 && !StringUtils.hasText(discountReason)) {
            throw new IllegalArgumentException("discount reason is required when discount > 0");
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
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 40;
                float y = page.getMediaBox().getHeight() - margin;
                writeLine(content, tenantName, 16, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 18;
                writeLine(content, "Bill: " + record.billNumber(), 13, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 14;
                writeLine(content, "Patient: " + safe(record.patientName()), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                y -= 12;
                writeLine(content, "Status: " + record.status(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                y -= 16;
                for (BillLineRecord line : record.lines()) {
                    String text = String.format("%s | %s x %s | %s", safe(line.itemName()), line.quantity(), line.unitPrice(), line.totalPrice());
                    y = writeWrapped(content, text, 9, margin, y, 520);
                }
                y -= 10;
                writeLine(content, "Subtotal: " + record.subtotalAmount(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 12;
                writeLine(content, "Discount: " + record.discountAmount(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 12;
                writeLine(content, "Tax: " + record.taxAmount(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 12;
                writeLine(content, "Total: " + record.totalAmount(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 12;
                writeLine(content, "Paid: " + record.paidAmount() + " | Refunded: " + record.refundedAmount() + " | Due: " + record.dueAmount(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            }
            document.save(output);
            return new BillPdf(safeFilename(record.billNumber()) + ".pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate bill PDF", ex);
        }
    }

    private ReceiptPdf buildReceiptPdf(String tenantName, BillEntity bill, ReceiptEntity receipt, PaymentEntity payment, BillData data) {
        BillRecord record = toRecord(bill, data);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 40;
                float y = page.getMediaBox().getHeight() - margin;
                writeLine(content, tenantName, 16, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 18;
                writeLine(content, "Receipt: " + receipt.getReceiptNumber(), 13, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 14;
                writeLine(content, "Bill: " + record.billNumber(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                y -= 12;
                writeLine(content, "Amount: " + receipt.getAmount(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 12;
                String paymentMode = payment == null || payment.getPaymentMode() == null ? "-" : payment.getPaymentMode().name();
                writeLine(content, "Payment Mode: " + paymentMode, 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            }
            document.save(output);
            return new ReceiptPdf(safeFilename(receipt.getReceiptNumber()) + ".pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate receipt PDF", ex);
        }
    }

    private float writeWrapped(PDPageContentStream content, String text, float fontSize, float x, float y, float maxWidth) throws IOException {
        if (!StringUtils.hasText(text)) return y;
        for (String line : wrap(text, 92)) {
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

    private record BillData(Map<UUID, PatientEntity> patients, String tenantName, String clinicName, String clinicDisplayNameValue, String clinicAddressValue) {
        String clinicDisplayName() { return StringUtils.hasText(clinicDisplayNameValue) ? clinicDisplayNameValue : clinicName; }
        String clinicAddress() { return clinicAddressValue; }
    }
}
