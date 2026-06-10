package com.deepthoughtnet.clinic.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.billing.db.BillEntity;
import com.deepthoughtnet.clinic.billing.db.BillLineEntity;
import com.deepthoughtnet.clinic.billing.db.BillLineRepository;
import com.deepthoughtnet.clinic.billing.db.BillRefundRepository;
import com.deepthoughtnet.clinic.billing.db.BillRepository;
import com.deepthoughtnet.clinic.billing.db.PaymentEntity;
import com.deepthoughtnet.clinic.billing.db.PaymentRepository;
import com.deepthoughtnet.clinic.billing.db.ReceiptEntity;
import com.deepthoughtnet.clinic.billing.db.ReceiptRepository;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.billing.service.model.BillUpsertCommand;
import com.deepthoughtnet.clinic.billing.service.model.ConsultationFeePaymentCommand;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.PaymentCommand;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.RefundCommand;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BillingServicePaymentTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private BillRepository billRepository;
    private BillLineRepository billLineRepository;
    private PaymentRepository paymentRepository;
    private BillRefundRepository billRefundRepository;
    private ReceiptRepository receiptRepository;
    private PatientRepository patientRepository;
    private AuditEventPublisher auditEventPublisher;
    private DoctorProfileService doctorProfileService;
    private AppointmentService appointmentService;
    private BillingService service;
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID doctorUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Map<UUID, BillEntity> savedBills = new HashMap<>();
        Map<UUID, List<BillLineEntity>> savedBillLines = new HashMap<>();
        billRepository = mock(BillRepository.class);
        billLineRepository = mock(BillLineRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        billRefundRepository = mock(BillRefundRepository.class);
        receiptRepository = mock(ReceiptRepository.class);
        patientRepository = mock(PatientRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        doctorProfileService = mock(DoctorProfileService.class);
        appointmentService = mock(AppointmentService.class);
        service = new BillingService(
                billRepository,
                billLineRepository,
                paymentRepository,
                billRefundRepository,
                receiptRepository,
                patientRepository,
                mock(ClinicProfileService.class),
                doctorProfileService,
                mock(ConsultationService.class),
                appointmentService,
                mock(InventoryService.class),
                mock(TenantUserManagementService.class),
                auditEventPublisher,
                new ObjectMapper()
        );
        when(billRepository.save(any(BillEntity.class))).thenAnswer(invocation -> {
            BillEntity entity = invocation.getArgument(0);
            savedBills.put(entity.getId(), entity);
            return entity;
        });
        when(billRepository.findByTenantIdAndId(any(), any())).thenAnswer(invocation -> Optional.ofNullable(savedBills.get(invocation.getArgument(1))));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.save(any(ReceiptEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.findByTenantIdAndReceiptNumber(any(), any())).thenReturn(Optional.empty());
        when(billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(billLineRepository.save(any(BillLineEntity.class))).thenAnswer(invocation -> {
            BillLineEntity line = invocation.getArgument(0);
            savedBillLines.computeIfAbsent(line.getBillId(), ignored -> new ArrayList<>()).add(line);
            return line;
        });
        when(billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(any(), any()))
                .thenAnswer(invocation -> List.copyOf(savedBillLines.getOrDefault(invocation.getArgument(1), List.of())));
        when(patientRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.of(mock(com.deepthoughtnet.clinic.patient.db.PatientEntity.class)));
        when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of());
        when(appointmentService.findById(tenantId, appointmentId)).thenReturn(new AppointmentRecord(
                appointmentId,
                tenantId,
                patientId,
                "PAT-1",
                "Test Patient",
                "9999999999",
                doctorUserId,
                "Dr Demo",
                null,
                LocalDate.now(),
                null,
                null,
                "Consultation",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
        when(doctorProfileService.findByDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.of(new DoctorProfileRecord(
                UUID.randomUUID(),
                tenantId,
                doctorUserId,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("900.00"),
                null,
                null,
                true,
                false,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));
    }

    @Test
    void recordPaymentIssuesDraftAndMarksPartiallyPaid() {
        List<PaymentEntity> payments = new ArrayList<>();
        BillEntity bill = billWithTotal(new BigDecimal("100.00"), payments);
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payments.add(payment);
            return payment;
        });

        service.recordPayment(tenantId, bill.getId(), payment("40.00"), actorId);

        assertThat(bill.getStatus()).isEqualTo(BillStatus.PARTIALLY_PAID);
        assertThat(bill.getPaidAmount()).isEqualByComparingTo("40.00");
        assertThat(bill.getDueAmount()).isEqualByComparingTo("60.00");
        org.mockito.Mockito.verify(auditEventPublisher, org.mockito.Mockito.atLeastOnce()).record(argThat(command -> "payment.collected".equals(command.action())));
    }

    @Test
    void recordPaymentMarksBillPaidWhenDueIsCleared() {
        List<PaymentEntity> payments = new ArrayList<>();
        BillEntity bill = billWithTotal(new BigDecimal("100.00"), payments);
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payments.add(payment);
            return payment;
        });

        service.recordPayment(tenantId, bill.getId(), payment("100.00"), actorId);

        assertThat(bill.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(bill.getPaidAmount()).isEqualByComparingTo("100.00");
        assertThat(bill.getDueAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void recordPaymentRequiresReferenceForNonCash() {
        List<PaymentEntity> payments = new ArrayList<>();
        BillEntity bill = billWithTotal(new BigDecimal("100.00"), payments);

        assertThatThrownBy(() -> service.recordPayment(
                tenantId,
                bill.getId(),
                new PaymentCommand(LocalDate.now(), null, new BigDecimal("10.00"), PaymentMode.UPI, null, null, null),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenceNumber is required for non-cash payments");
    }

    @Test
    void amountDiscountReducesTotalCorrectly() {
        var bill = createDraftWithDiscount(DiscountType.AMOUNT, new BigDecimal("10.00"), "manual");
        assertThat(bill.totalAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void percentageDiscountReducesTotalCorrectly() {
        var bill = createDraftWithDiscount(DiscountType.PERCENTAGE, new BigDecimal("10.00"), "promo");
        assertThat(bill.totalAmount()).isEqualByComparingTo("90.00");
        assertThat(bill.discountAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void zeroDiscountWorks() {
        var bill = createDraftWithDiscount(DiscountType.NONE, BigDecimal.ZERO, null);
        assertThat(bill.totalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void discountAmountGreaterThanSubtotalRejected() {
        assertThatThrownBy(() -> createDraftWithDiscount(DiscountType.AMOUNT, new BigDecimal("150.00"), "bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discount amount cannot exceed subtotal");
    }

    @Test
    void percentageGreaterThanHundredRejected() {
        assertThatThrownBy(() -> createDraftWithDiscount(DiscountType.PERCENTAGE, new BigDecimal("120.00"), "bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage discount must be between 0 and 100");
    }

    @Test
    void discountReasonRequiredWhenDiscountPositive() {
        assertThatThrownBy(() -> createDraftWithDiscount(DiscountType.AMOUNT, new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discount reason is required when discount > 0");
    }

    @Test
    void partialRefundUpdatesStatusToPartiallyRefunded() {
        List<PaymentEntity> payments = new ArrayList<>();
        List<com.deepthoughtnet.clinic.billing.db.BillRefundEntity> refunds = new ArrayList<>();
        BillEntity bill = billWithTotal(new BigDecimal("100.00"), payments, refunds);
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payments.add(payment);
            return payment;
        });
        when(billRefundRepository.save(any())).thenAnswer(invocation -> {
            com.deepthoughtnet.clinic.billing.db.BillRefundEntity refund = invocation.getArgument(0);
            refunds.add(refund);
            return refund;
        });
        service.recordPayment(tenantId, bill.getId(), payment("100.00"), actorId);

        service.refund(tenantId, bill.getId(), new RefundCommand(payments.get(0).getId(), new BigDecimal("20.00"), "partial", PaymentMode.CASH, null, null), actorId);

        assertThat(bill.getStatus()).isEqualTo(BillStatus.PARTIALLY_REFUNDED);
        org.mockito.Mockito.verify(auditEventPublisher, org.mockito.Mockito.atLeastOnce()).record(argThat(command -> "refund.created".equals(command.action())));
    }

    @Test
    void fullRefundUpdatesStatusToRefunded() {
        List<PaymentEntity> payments = new ArrayList<>();
        List<com.deepthoughtnet.clinic.billing.db.BillRefundEntity> refunds = new ArrayList<>();
        BillEntity bill = billWithTotal(new BigDecimal("100.00"), payments, refunds);
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payments.add(payment);
            return payment;
        });
        when(billRefundRepository.save(any())).thenAnswer(invocation -> {
            com.deepthoughtnet.clinic.billing.db.BillRefundEntity refund = invocation.getArgument(0);
            refunds.add(refund);
            return refund;
        });
        service.recordPayment(tenantId, bill.getId(), payment("100.00"), actorId);

        service.refund(tenantId, bill.getId(), new RefundCommand(payments.get(0).getId(), new BigDecimal("100.00"), "full", PaymentMode.CASH, null, null), actorId);

        assertThat(bill.getStatus()).isEqualTo(BillStatus.REFUNDED);
    }

    @Test
    void refundGreaterThanRefundableRejected() {
        List<PaymentEntity> payments = new ArrayList<>();
        BillEntity bill = billWithTotal(new BigDecimal("100.00"), payments);
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payments.add(payment);
            return payment;
        });
        service.recordPayment(tenantId, bill.getId(), payment("50.00"), actorId);
        assertThatThrownBy(() -> service.refund(tenantId, bill.getId(), new RefundCommand(payments.get(0).getId(), new BigDecimal("60.00"), "bad", PaymentMode.CASH, null, null), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund amount cannot exceed paid amount minus previous refunds");
    }

    @Test
    void refundReasonRequired() {
        assertThatThrownBy(() -> service.refund(tenantId, UUID.randomUUID(), new RefundCommand(null, new BigDecimal("10.00"), null, PaymentMode.CASH, null, null), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refund reason is required");
    }

    @Test
    void listUsesNonPaymentModeQueryWhenPaymentModeFilterMissing() {
        when(billRepository.search(any(), any(BillingSearchCriteria.class))).thenReturn(List.of());

        var rows = service.list(tenantId, new BillingSearchCriteria(null, null, null, null, null, null, null));

        assertThat(rows).isEmpty();
    }

    @Test
    void listUsesPaymentModeQueryWhenPaymentModeFilterPresent() {
        when(billRepository.search(any(), any(BillingSearchCriteria.class))).thenReturn(List.of());

        var rows = service.list(tenantId, new BillingSearchCriteria(null, null, null, null, PaymentMode.UPI, null, null));

        assertThat(rows).isEmpty();
    }

    @Test
    void ensureConsultationFeePaidIgnoresCancelledBill() {
        BillEntity cancelled = consultationBill(appointmentId, new BigDecimal("900.00"));
        cancelled.markStatus(BillStatus.CANCELLED);
        when(billRepository.findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, appointmentId)).thenReturn(List.of(cancelled));
        when(billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, cancelled.getId()))
                .thenReturn(List.of(consultationLine(cancelled.getId(), new BigDecimal("900.00"))));
        when(paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, cancelled.getId())).thenReturn(List.of());
        when(billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(tenantId, cancelled.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.ensureConsultationFeePaid(tenantId, appointmentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Consultation fee must be collected before check-in.");
    }

    @Test
    void ensureConsultationFeePaidUsesNetPaidAfterRefund() {
        List<PaymentEntity> payments = new ArrayList<>();
        List<com.deepthoughtnet.clinic.billing.db.BillRefundEntity> refunds = new ArrayList<>();
        BillEntity bill = consultationBill(appointmentId, new BigDecimal("900.00"));
        when(billRepository.findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, appointmentId)).thenReturn(List.of(bill));
        when(billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, bill.getId()))
                .thenReturn(List.of(consultationLine(bill.getId(), new BigDecimal("900.00"))));
        when(paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, bill.getId())).thenAnswer(invocation -> List.copyOf(payments));
        when(billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(tenantId, bill.getId())).thenAnswer(invocation -> List.copyOf(refunds));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payments.add(payment);
            return payment;
        });
        when(billRefundRepository.save(any())).thenAnswer(invocation -> {
            com.deepthoughtnet.clinic.billing.db.BillRefundEntity refund = invocation.getArgument(0);
            refunds.add(refund);
            return refund;
        });

        service.recordPayment(tenantId, bill.getId(), payment("900.00"), actorId);
        service.refund(tenantId, bill.getId(), new RefundCommand(payments.get(0).getId(), new BigDecimal("900.00"), "full", PaymentMode.CASH, null, null), actorId);

        assertThatThrownBy(() -> service.ensureConsultationFeePaid(tenantId, appointmentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Consultation fee must be collected before check-in.");
    }

    @Test
    void collectConsultationFeeUsesRemainingDueAfterPartialRefund() {
        List<PaymentEntity> payments = new ArrayList<>();
        List<com.deepthoughtnet.clinic.billing.db.BillRefundEntity> refunds = new ArrayList<>();
        BillEntity bill = consultationBill(appointmentId, new BigDecimal("900.00"));
        when(billRepository.findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, appointmentId)).thenReturn(List.of(bill));
        when(billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, bill.getId()))
                .thenReturn(List.of(consultationLine(bill.getId(), new BigDecimal("900.00"))));
        when(paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, bill.getId())).thenAnswer(invocation -> List.copyOf(payments));
        when(billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(tenantId, bill.getId())).thenAnswer(invocation -> List.copyOf(refunds));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payments.add(payment);
            return payment;
        });
        when(billRefundRepository.save(any())).thenAnswer(invocation -> {
            com.deepthoughtnet.clinic.billing.db.BillRefundEntity refund = invocation.getArgument(0);
            refunds.add(refund);
            return refund;
        });

        service.recordPayment(tenantId, bill.getId(), payment("900.00"), actorId);
        service.refund(tenantId, bill.getId(), new RefundCommand(payments.get(0).getId(), new BigDecimal("300.00"), "partial", PaymentMode.CASH, null, null), actorId);

        PaymentRecord recollected = service.collectConsultationFee(
                tenantId,
                new ConsultationFeePaymentCommand(appointmentId, PaymentMode.CASH, null, null),
                actorId
        );

        assertThat(recollected.amount()).isEqualByComparingTo("300.00");
        assertThat(bill.getDueAmount()).isEqualByComparingTo("0.00");
        assertThat(bill.getNetPaidAmount()).isEqualByComparingTo("900.00");
        assertThat(bill.getStatus()).isEqualTo(BillStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void collectConsultationFeeReusesExistingConsultationBillWhenDueIsPending() {
        List<PaymentEntity> payments = new ArrayList<>();
        BillEntity bill = consultationBill(appointmentId, new BigDecimal("900.00"));
        when(billRepository.findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, appointmentId)).thenReturn(List.of(bill));
        when(billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, bill.getId()))
                .thenReturn(List.of(consultationLine(bill.getId(), new BigDecimal("900.00"))));
        when(paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, bill.getId())).thenAnswer(invocation -> List.copyOf(payments));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payments.add(payment);
            return payment;
        });

        PaymentRecord collected = service.collectConsultationFee(
                tenantId,
                new ConsultationFeePaymentCommand(appointmentId, PaymentMode.CASH, null, null),
                actorId
        );

        assertThat(collected.billId()).isEqualTo(bill.getId());
        assertThat(collected.amount()).isEqualByComparingTo("900.00");
        org.mockito.Mockito.verify(billRepository, org.mockito.Mockito.never())
                .save(argThat(entity -> appointmentId.equals(entity.getAppointmentId()) && !bill.getId().equals(entity.getId())));
    }

    @Test
    void collectConsultationFeeCreatesFreshBillWhenOnlyCancelledConsultationBillExists() {
        BillEntity cancelled = consultationBill(appointmentId, new BigDecimal("900.00"));
        cancelled.markStatus(BillStatus.CANCELLED);
        when(billRepository.findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, appointmentId)).thenReturn(List.of(cancelled));
        when(billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, cancelled.getId()))
                .thenReturn(List.of(consultationLine(cancelled.getId(), new BigDecimal("900.00"))));
        when(paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, cancelled.getId())).thenReturn(List.of());
        when(billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(tenantId, cancelled.getId())).thenReturn(List.of());

        service.collectConsultationFee(
                tenantId,
                new ConsultationFeePaymentCommand(appointmentId, PaymentMode.CASH, null, null),
                actorId
        );

        org.mockito.Mockito.verify(billRepository, org.mockito.Mockito.atLeastOnce())
                .save(argThat(entity -> !entity.getId().equals(cancelled.getId()) && appointmentId.equals(entity.getAppointmentId())));
    }

    private BillEntity billWithTotal(BigDecimal total, List<PaymentEntity> payments) {
        return billWithTotal(total, payments, List.of());
    }

    private BillEntity billWithTotal(BigDecimal total, List<PaymentEntity> payments, List<com.deepthoughtnet.clinic.billing.db.BillRefundEntity> refunds) {
        BillEntity bill = BillEntity.create(tenantId, "BILL-TEST", patientId, null, null, LocalDate.now());
        BillLineEntity line = BillLineEntity.create(tenantId, bill.getId(), BillItemType.CONSULTATION, "Consultation", 1, total, BigDecimal.ZERO, total, null, null, null, 1);
        when(billRepository.findByTenantIdAndId(tenantId, bill.getId())).thenReturn(Optional.of(bill));
        when(billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(tenantId, bill.getId())).thenReturn(List.of(line));
        when(paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(tenantId, bill.getId())).thenAnswer(invocation -> List.copyOf(payments));
        when(billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(tenantId, bill.getId())).thenAnswer(invocation -> List.copyOf(refunds));
        return bill;
    }

    private com.deepthoughtnet.clinic.billing.service.model.BillRecord createDraftWithDiscount(DiscountType type, BigDecimal value, String reason) {
        when(billRepository.save(any(BillEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(billLineRepository.save(any(BillLineEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(billLineRepository.findByTenantIdAndBillIdOrderBySortOrderAsc(any(), any())).thenAnswer(invocation -> {
            UUID billId = invocation.getArgument(1);
            return List.of(BillLineEntity.create(tenantId, billId, BillItemType.CONSULTATION, "Consultation", 1, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), null, null, null, 1));
        });
        when(paymentRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(any(), any())).thenReturn(List.of());
        return service.createDraft(tenantId, new BillUpsertCommand(
                patientId, null, null, LocalDate.now(), type, value, null, reason, null, BigDecimal.ZERO, null,
                List.of(new BillLineCommand(BillItemType.CONSULTATION, "Consultation", 1, new BigDecimal("100.00"), null, 1, BigDecimal.ZERO, null, null))
        ), actorId);
    }

    private BillEntity consultationBill(UUID appointmentId, BigDecimal total) {
        BillEntity bill = BillEntity.create(tenantId, "BILL-" + UUID.randomUUID(), patientId, null, appointmentId, LocalDate.now());
        when(billRepository.findByTenantIdAndId(tenantId, bill.getId())).thenReturn(Optional.of(bill));
        return bill;
    }

    private BillLineEntity consultationLine(UUID billId, BigDecimal total) {
        return BillLineEntity.create(tenantId, billId, BillItemType.CONSULTATION, "Consultation", 1, total, BigDecimal.ZERO, total, null, appointmentId, null, 1);
    }

    private PaymentCommand payment(String amount) {
        return new PaymentCommand(LocalDate.now(), null, new BigDecimal(amount), PaymentMode.CASH, null, null, null);
    }
}
