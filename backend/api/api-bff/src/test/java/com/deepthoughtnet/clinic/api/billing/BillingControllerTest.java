package com.deepthoughtnet.clinic.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
import com.deepthoughtnet.clinic.api.billing.BillingAccessChecker;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.PaymentCommand;
import com.deepthoughtnet.clinic.billing.service.model.PatientBillingContextRecord;
import com.deepthoughtnet.clinic.billing.service.model.PendingConsultationFeeRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BillingControllerTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void sendInvoiceEmailReturnsPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        BillingService billingService = mock(BillingService.class);
        BillingAccessChecker billingAccessChecker = mock(BillingAccessChecker.class);
        NotificationActionService notificationActionService = mock(NotificationActionService.class);
        BillingController controller = new BillingController(billingService, billingAccessChecker, notificationActionService);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actor, "sub", Set.of("BILLING_USER"), "BILLING_USER", "cid"));
        when(notificationActionService.sendInvoiceEmail(tenantId, billId, actor)).thenReturn(
                new NotificationActionService.InvoiceEmailResult(true, "Invoice email sent", "patient@example.com", OffsetDateTime.now())
        );

        var response = controller.sendInvoiceEmail(billId);

        assertThat(response.sent()).isTrue();
        assertThat(response.recipientEmail()).isEqualTo("patient@example.com");
    }

    @Test
    void sendInvoiceEmailMissingBillBubblesCleanError() {
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        BillingService billingService = mock(BillingService.class);
        BillingAccessChecker billingAccessChecker = mock(BillingAccessChecker.class);
        NotificationActionService notificationActionService = mock(NotificationActionService.class);
        BillingController controller = new BillingController(billingService, billingAccessChecker, notificationActionService);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actor, "sub", Set.of("BILLING_USER"), "BILLING_USER", "cid"));
        when(notificationActionService.sendInvoiceEmail(tenantId, billId, actor)).thenThrow(new IllegalArgumentException("Bill not found"));

        assertThatThrownBy(() -> controller.sendInvoiceEmail(billId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bill not found");
    }

    @Test
    void patientContextReturnsPendingConsultationFeeSummary() {
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        BillingService billingService = mock(BillingService.class);
        BillingAccessChecker billingAccessChecker = mock(BillingAccessChecker.class);
        NotificationActionService notificationActionService = mock(NotificationActionService.class);
        BillingController controller = new BillingController(billingService, billingAccessChecker, notificationActionService);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actor, "sub", Set.of("BILLING_USER"), "BILLING_USER", "cid"));
        when(billingService.patientContext(tenantId, patientId)).thenReturn(new PatientBillingContextRecord(
                patientId,
                "PAT-1",
                "Test Patient",
                new BigDecimal("0.00"),
                new BigDecimal("800.00"),
                new BigDecimal("800.00"),
                0,
                List.of(new PendingConsultationFeeRecord(
                        UUID.randomUUID(),
                        LocalDate.of(2026, 6, 11),
                        LocalTime.of(10, 30),
                        UUID.randomUUID(),
                        "Dr Demo",
                        new BigDecimal("800.00"),
                        new BigDecimal("800.00"),
                        "PATIENT_WILL_PAY_AFTER_CONSULTATION",
                        OffsetDateTime.now()
                ))
        ));

        var response = controller.patientContext(patientId);

        assertThat(response.patientId()).isEqualTo(patientId.toString());
        assertThat(response.totalDueAmount()).isEqualByComparingTo("800.00");
        assertThat(response.pendingConsultationFees()).hasSize(1);
        assertThat(response.pendingConsultationFees().get(0).doctorName()).isEqualTo("Dr Demo");
    }

    @Test
    void addPaymentReturnsReceivedByLabelInsteadOfUuid() {
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID receivedBy = UUID.randomUUID();
        BillingService billingService = mock(BillingService.class);
        BillingAccessChecker billingAccessChecker = mock(BillingAccessChecker.class);
        NotificationActionService notificationActionService = mock(NotificationActionService.class);
        BillingController controller = new BillingController(billingService, billingAccessChecker, notificationActionService);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actor, "sub", Set.of("BILLING_USER"), "BILLING_USER", "cid"));
        PaymentRecord payment = new PaymentRecord(
                UUID.randomUUID(),
                tenantId,
                billId,
                LocalDate.of(2026, 7, 6),
                OffsetDateTime.now(),
                new BigDecimal("600.00"),
                PaymentMode.CASH,
                "REF-123",
                "Collected at queue",
                receivedBy,
                UUID.randomUUID(),
                "RCPT-001",
                LocalDate.of(2026, 7, 6),
                OffsetDateTime.now()
        );
        when(billingService.recordPayment(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(billId), org.mockito.ArgumentMatchers.any(PaymentCommand.class), org.mockito.ArgumentMatchers.eq(actor)))
                .thenReturn(payment);
        when(billingService.receivedByDisplayLabel(tenantId, receivedBy)).thenReturn("Priya Sharma");

        var response = controller.addPayment(billId, new com.deepthoughtnet.clinic.api.billing.dto.PaymentRequest(
                LocalDate.of(2026, 7, 6),
                OffsetDateTime.now(),
                new BigDecimal("600.00"),
                PaymentMode.CASH,
                "REF-123",
                "Collected at queue",
                receivedBy
        ));

        assertThat(response.receivedBy()).isEqualTo(receivedBy.toString());
        assertThat(response.receivedByLabel()).isEqualTo("Priya Sharma");
    }
}
