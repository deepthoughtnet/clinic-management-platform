package com.deepthoughtnet.clinic.api.notifications;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationReminderSchedulerTest {

    @Test
    void schedulesOnlyNonAppointmentLegacyFamilies() {
        PlatformTenantManagementService tenants = mock(PlatformTenantManagementService.class);
        NotificationActionService actions = mock(NotificationActionService.class);
        NotificationsSchedulerProperties properties = new NotificationsSchedulerProperties(true, "PT30S");
        UUID tenantId = UUID.randomUUID();
        when(tenants.list()).thenReturn(List.of(new PlatformTenantRecord(
                tenantId, "clinic", "Clinic", null, "ACTIVE", false, null, null, null)));
        when(actions.queueMissedAppointmentReminders(tenantId, java.time.LocalDate.now(), null))
                .thenReturn(NotificationActionService.ReminderQueueSummary.empty());
        when(actions.queueFollowUpReminders(tenantId, java.time.LocalDate.now(), null))
                .thenReturn(NotificationActionService.ReminderQueueSummary.empty());
        when(actions.queueVaccinationReminders(tenantId, null))
                .thenReturn(NotificationActionService.ReminderQueueSummary.empty());
        when(actions.queuePaymentReminders(tenantId, null))
                .thenReturn(NotificationActionService.ReminderQueueSummary.empty());

        NotificationReminderScheduler scheduler = new NotificationReminderScheduler(tenants, actions, properties);
        scheduler.run();

        verify(actions).queueMissedAppointmentReminders(tenantId, java.time.LocalDate.now(), null);
        verify(actions).queueFollowUpReminders(tenantId, java.time.LocalDate.now(), null);
        verify(actions).queueVaccinationReminders(tenantId, null);
        verify(actions).queuePaymentReminders(tenantId, null);
    }
}
