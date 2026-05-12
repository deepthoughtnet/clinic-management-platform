package com.deepthoughtnet.clinic.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
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
        NotificationActionService notificationActionService = mock(NotificationActionService.class);
        BillingController controller = new BillingController(billingService, notificationActionService);
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
        NotificationActionService notificationActionService = mock(NotificationActionService.class);
        BillingController controller = new BillingController(billingService, notificationActionService);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actor, "sub", Set.of("BILLING_USER"), "BILLING_USER", "cid"));
        when(notificationActionService.sendInvoiceEmail(tenantId, billId, actor)).thenThrow(new IllegalArgumentException("Bill not found"));

        assertThatThrownBy(() -> controller.sendInvoiceEmail(billId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bill not found");
    }
}
