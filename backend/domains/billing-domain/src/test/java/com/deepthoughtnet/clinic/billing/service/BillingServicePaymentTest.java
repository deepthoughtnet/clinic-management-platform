package com.deepthoughtnet.clinic.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
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
import com.deepthoughtnet.clinic.billing.service.model.BillUpsertCommand;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.PaymentCommand;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.RefundCommand;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    private BillingService service;

    @BeforeEach
    void setUp() {
        billRepository = mock(BillRepository.class);
        billLineRepository = mock(BillLineRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        billRefundRepository = mock(BillRefundRepository.class);
        receiptRepository = mock(ReceiptRepository.class);
        patientRepository = mock(PatientRepository.class);
        service = new BillingService(
                billRepository,
                billLineRepository,
                paymentRepository,
                billRefundRepository,
                receiptRepository,
                patientRepository,
                mock(ClinicProfileService.class),
                mock(ConsultationService.class),
                mock(AppointmentService.class),
                mock(InventoryService.class),
                mock(TenantUserManagementService.class),
                mock(AuditEventPublisher.class),
                new ObjectMapper()
        );
        when(billRepository.save(any(BillEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.save(any(ReceiptEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.findByTenantIdAndReceiptNumber(any(), any())).thenReturn(Optional.empty());
        when(billRefundRepository.findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(patientRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.of(mock(com.deepthoughtnet.clinic.patient.db.PatientEntity.class)));
        when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of());
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

    private PaymentCommand payment(String amount) {
        return new PaymentCommand(LocalDate.now(), null, new BigDecimal(amount), PaymentMode.CASH, null, null, null);
    }
}
