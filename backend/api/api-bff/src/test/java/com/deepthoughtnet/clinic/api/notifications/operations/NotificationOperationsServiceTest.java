package com.deepthoughtnet.clinic.api.notifications.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.admin.AdminIntegrationsStatusService;
import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatus;
import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatusRow;
import com.deepthoughtnet.clinic.api.notifications.AppointmentReminderProperties;
import com.deepthoughtnet.clinic.api.notifications.NotificationsSchedulerProperties;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsProviderRow;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsQuery;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsSummaryResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationCenterService;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.NotificationSummary;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryGroupRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationOperationsServiceTest {
    private NotificationHistoryService notificationHistoryService;
    private NotificationCenterService notificationCenterService;
    private PlatformTenantManagementService tenantManagementService;
    private PatientService patientService;
    private AppointmentService appointmentService;
    private BillingService billingService;
    private PrescriptionService prescriptionService;
    private LabService labService;
    private VaccinationService vaccinationService;
    private AdminIntegrationsStatusService integrationsStatusService;
    private ClinicTimeZoneResolver clinicTimeZoneResolver;
    private AuditEventQueryService auditEventQueryService;
    private AuditEventPublisher auditEventPublisher;
    private ObjectMapper objectMapper;
    private NotificationOperationsService service;

    @BeforeEach
    void setUp() {
        notificationHistoryService = mock(NotificationHistoryService.class);
        notificationCenterService = mock(NotificationCenterService.class);
        tenantManagementService = mock(PlatformTenantManagementService.class);
        patientService = mock(PatientService.class);
        appointmentService = mock(AppointmentService.class);
        billingService = mock(BillingService.class);
        prescriptionService = mock(PrescriptionService.class);
        labService = mock(LabService.class);
        vaccinationService = mock(VaccinationService.class);
        integrationsStatusService = mock(AdminIntegrationsStatusService.class);
        clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        auditEventQueryService = mock(AuditEventQueryService.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        objectMapper = new ObjectMapper();

        service = new NotificationOperationsService(
                notificationHistoryService,
                notificationCenterService,
                tenantManagementService,
                patientService,
                appointmentService,
                billingService,
                prescriptionService,
                labService,
                vaccinationService,
                integrationsStatusService,
                clinicTimeZoneResolver,
                new NotificationsSchedulerProperties(true, "PT30S"),
                createReminderProperties(),
                auditEventQueryService,
                auditEventPublisher,
                objectMapper
        );
    }

    @Test
    void summaryUsesNeutralNoDataStateWhenNoDeliveryRowsExist() {
        UUID tenantId = UUID.randomUUID();
        stubTenant(tenantId);
        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(ZoneId.of("UTC"));
        when(notificationHistoryService.listGrouped(any(), any())).thenReturn(List.of());
        when(notificationCenterService.summarize(tenantId)).thenReturn(new NotificationSummary(0, 0, 0, 0, null));

        NotificationOperationsSummaryResponse response = service.summary(tenantId, emptyQuery());

        assertThat(response.successRate()).isNull();
        assertThat(response.channelDeliveriesAttempted()).isZero();
        assertThat(response.kpis())
                .anySatisfy(kpi -> {
                    assertThat(kpi.label()).isEqualTo("Success rate");
                    assertThat(kpi.value()).isEqualTo("N/A");
                    assertThat(kpi.helper()).contains("No delivery activity");
                });
    }

    @Test
    void summaryCalculatesSuccessRateForRealDeliveryData() {
        UUID tenantId = UUID.randomUUID();
        stubTenant(tenantId);
        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(ZoneId.of("UTC"));
        when(notificationHistoryService.listGrouped(any(), any())).thenReturn(List.of(groupWithDeliveries(tenantId, 8, 2)));
        when(notificationCenterService.summarize(tenantId)).thenReturn(new NotificationSummary(0, 0, 0, 0, null));

        NotificationOperationsSummaryResponse response = service.summary(tenantId, emptyQuery());

        assertThat(response.successRate()).isEqualTo(80.0);
        assertThat(response.kpis())
                .anySatisfy(kpi -> {
                    assertThat(kpi.label()).isEqualTo("Success rate");
                    assertThat(kpi.value()).isEqualTo("80.0%");
                    assertThat(kpi.helper()).isEqualTo("Sent deliveries divided by all channel deliveries");
                });
    }

    @Test
    void providersReturnZeroCountsForNoTrafficWithoutChangingReadiness() {
        UUID tenantId = UUID.randomUUID();
        stubTenant(tenantId);
        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(ZoneId.of("UTC"));
        when(notificationHistoryService.listGrouped(any(), any())).thenReturn(List.of());
        when(integrationsStatusService.status(tenantId)).thenReturn(List.of(
                integrationRow("clinic.messaging.email", "Email / SMTP", IntegrationStatus.READY, true, true),
                integrationRow("clinic.messaging.sms", "SMS", IntegrationStatus.DISABLED, false, false),
                integrationRow("clinic.messaging.whatsapp", "WhatsApp", IntegrationStatus.NOT_CONFIGURED, false, false)
        ));

        List<NotificationOperationsProviderRow> providers = service.providers(tenantId, emptyQuery());

        assertThat(providers).extracting(NotificationOperationsProviderRow::readinessStatus)
                .contains("Ready", "Disabled", "Not configured");
        assertThat(providers).extracting(NotificationOperationsProviderRow::pendingCount).containsOnly(0L);
        assertThat(providers).extracting(NotificationOperationsProviderRow::failureCount).containsOnly(0L);
        assertThat(providers).extracting(NotificationOperationsProviderRow::successCount).containsOnly(0L);
        assertThat(providers).extracting(NotificationOperationsProviderRow::lastSuccessfulAt).containsOnlyNulls();
        assertThat(providers).extracting(NotificationOperationsProviderRow::lastFailedAt).containsOnlyNulls();
    }

    @Test
    void providersCalculateOperationalCountsForRealTraffic() {
        UUID tenantId = UUID.randomUUID();
        stubTenant(tenantId);
        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(ZoneId.of("UTC"));
        when(notificationHistoryService.listGrouped(any(), any())).thenReturn(List.of(groupWithDeliveries(tenantId, 8, 2)));
        when(integrationsStatusService.status(tenantId)).thenReturn(List.of(
                integrationRow("clinic.messaging.email", "Email / SMTP", IntegrationStatus.READY, true, true)
        ));

        List<NotificationOperationsProviderRow> providers = service.providers(tenantId, emptyQuery());

        assertThat(providers).hasSize(1);
        NotificationOperationsProviderRow provider = providers.getFirst();
        assertThat(provider.successCount()).isEqualTo(8);
        assertThat(provider.failureCount()).isEqualTo(2);
        assertThat(provider.pendingCount()).isZero();
        assertThat(provider.lastSuccessfulAt()).isNotNull();
        assertThat(provider.lastFailedAt()).isNotNull();
    }

    @Test
    void providersKeepDisabledAndUnconfiguredReadinessLabelsIntactWithNoTraffic() {
        UUID tenantId = UUID.randomUUID();
        stubTenant(tenantId);
        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(ZoneId.of("UTC"));
        when(notificationHistoryService.listGrouped(any(), any())).thenReturn(List.of());
        when(integrationsStatusService.status(tenantId)).thenReturn(List.of(
                integrationRow("clinic.messaging.sms", "SMS", IntegrationStatus.DISABLED, false, false),
                integrationRow("clinic.messaging.whatsapp", "WhatsApp", IntegrationStatus.NOT_CONFIGURED, false, false)
        ));

        List<NotificationOperationsProviderRow> providers = service.providers(tenantId, emptyQuery());

        assertThat(providers).extracting(NotificationOperationsProviderRow::name).containsExactly("SMS", "WhatsApp");
        assertThat(providers).extracting(NotificationOperationsProviderRow::readinessStatus).containsExactly("Disabled", "Not configured");
        assertThat(providers).extracting(NotificationOperationsProviderRow::configured).containsOnly(false);
        assertThat(providers).extracting(NotificationOperationsProviderRow::enabled).containsOnly(false);
        assertThat(providers).extracting(NotificationOperationsProviderRow::successCount).containsOnly(0L);
    }

    private void stubTenant(UUID tenantId) {
        when(tenantManagementService.get(tenantId)).thenReturn(new PlatformTenantRecord(
                tenantId,
                "tenant",
                "Tenant",
                "plan",
                "ACTIVE",
                true,
                new TenantModulesRecord(true, true, true, true, true, true, true, true, true, true),
                OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                OffsetDateTime.parse("2026-08-20T00:00:00Z")
        ));
    }

    private NotificationOperationsQuery emptyQuery() {
        return new NotificationOperationsQuery(null, "LAST_7_DAYS", null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10);
    }

    private NotificationHistoryGroupRecord groupWithDeliveries(UUID tenantId, int successes, int failures) {
        List<NotificationHistoryRecord> deliveries = new java.util.ArrayList<>();
        for (int index = 0; index < successes; index++) {
            deliveries.add(delivery(tenantId, "SENT", null, OffsetDateTime.parse("2026-08-20T09:00:00Z").plusMinutes(index), OffsetDateTime.parse("2026-08-20T09:01:00Z").plusMinutes(index)));
        }
        for (int index = 0; index < failures; index++) {
            deliveries.add(delivery(tenantId, "FAILED", "Provider unavailable", null, OffsetDateTime.parse("2026-08-20T09:10:00Z").plusMinutes(index)));
        }
        return new NotificationHistoryGroupRecord(
                "logical-1",
                tenantId,
                null,
                "APPOINTMENT_REMINDER",
                "Reminder",
                "Message",
                "PARTIAL",
                "UNREAD",
                OffsetDateTime.parse("2026-08-20T08:55:00Z"),
                OffsetDateTime.parse("2026-08-20T09:10:00Z"),
                deliveries
        );
    }

    private NotificationHistoryRecord delivery(UUID tenantId, String status, String failureReason, OffsetDateTime sentAt, OffsetDateTime updatedAt) {
        return new NotificationHistoryRecord(
                UUID.randomUUID(),
                tenantId,
                null,
                "APPOINTMENT_REMINDER",
                "EMAIL",
                "patient@example.com",
                "Reminder",
                "Message",
                status,
                failureReason,
                null,
                null,
                null,
                null,
                0,
                sentAt,
                null,
                OffsetDateTime.parse("2026-08-20T08:50:00Z"),
                updatedAt
        );
    }

    private IntegrationStatusRow integrationRow(String key, String name, IntegrationStatus status, boolean enabled, boolean configured) {
        return new IntegrationStatusRow(
                key,
                name,
                "MESSAGING",
                status,
                enabled,
                configured,
                "Provider",
                List.of(),
                List.of(),
                "ok",
                OffsetDateTime.parse("2026-08-20T10:00:00Z"),
                true
        );
    }

    private AppointmentReminderProperties createReminderProperties() {
        AppointmentReminderProperties properties = new AppointmentReminderProperties();
        properties.setEnabled(true);
        properties.setHoursBefore(24);
        properties.setGraceMinutes(30);
        return properties;
    }
}
