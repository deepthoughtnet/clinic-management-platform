package com.deepthoughtnet.clinic.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReceiptControllerTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void getReturnsReceivedByLabelInsteadOfUuid() {
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID receivedBy = UUID.randomUUID();
        BillingService billingService = mock(BillingService.class);
        NotificationActionService notificationActionService = mock(NotificationActionService.class);
        ReceiptController controller = new ReceiptController(billingService, notificationActionService);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actor, "sub", Set.of("BILLING_USER"), "BILLING_USER", "cid"));

        when(billingService.findReceipt(tenantId, receiptId)).thenReturn(Optional.of(new ReceiptRecord(
                receiptId,
                tenantId,
                "RCPT-001",
                billId,
                paymentId,
                LocalDate.of(2026, 7, 6),
                new BigDecimal("600.00"),
                OffsetDateTime.now()
        )));
        when(billingService.findPayment(tenantId, paymentId)).thenReturn(Optional.of(new PaymentRecord(
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
                receiptId,
                "RCPT-001",
                LocalDate.of(2026, 7, 6),
                OffsetDateTime.now()
        )));
        when(billingService.receivedByDisplayLabel(tenantId, receivedBy)).thenReturn("Reception Desk");

        var response = controller.get(receiptId);

        assertThat(response.receiptNumber()).isEqualTo("RCPT-001");
        assertThat(response.receivedByLabel()).isEqualTo("Reception Desk");
    }
}
