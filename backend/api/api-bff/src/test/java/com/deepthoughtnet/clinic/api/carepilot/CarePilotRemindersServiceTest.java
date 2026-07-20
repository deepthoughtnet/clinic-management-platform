package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.analytics.service.CarePilotAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CarePilotRemindersServiceTest {

    @Test
    void patientNameFilterSupportsPartialCaseInsensitiveSearch() {
        CampaignExecutionRepository executionRepository = mock(CampaignExecutionRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        CampaignDeliveryEventRepository eventRepository = mock(CampaignDeliveryEventRepository.class);
        CarePilotAnalyticsService analyticsService = mock(CarePilotAnalyticsService.class);

        CarePilotRemindersService service = new CarePilotRemindersService(
                executionRepository,
                campaignRepository,
                patientRepository,
                eventRepository,
                analyticsService
        );

        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        UUID patientId = patient.getId();
        CampaignExecutionEntity execution = CampaignExecutionEntity.create(
                tenantId, campaignId, null, ChannelType.EMAIL, patientId, OffsetDateTime.now().plusDays(1), "APPOINTMENT", UUID.randomUUID(), "H24", null
        );
        patient.update("Jane", "Doe", PatientGender.FEMALE, null, null, "9999999999", "jane@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);

        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000001",
                "Appt Reminder",
                CampaignType.APPOINTMENT_REMINDER,
                TriggerType.SCHEDULED,
                AudienceType.ALL_PATIENTS,
                null,
                null,
                UUID.randomUUID()
        );
        setField(campaign, "id", campaignId);

        when(executionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(execution));
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));
        when(patientRepository.findByTenantIdAndIdIn(tenantId, List.of(patientId))).thenReturn(List.of(patient));
        when(eventRepository.findByTenantIdAndExecutionIdInOrderByEventTimestampAsc(tenantId, List.of(execution.getId()))).thenReturn(List.of());

        var response = service.list(tenantId, null, null, null, null, null, "anE", null, null, null, null, 0, 50);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.rows().get(0).patientName()).contains("Jane");
        assertThat(response.rows().get(0).patientReference()).isEqualTo("PAT-1");
        assertThat(response.rows().get(0).campaignReference()).isEqualTo("CAM-2026-000001");
        assertThat(response.rows().get(0).reasonCode()).isEqualTo("APPOINTMENT");
        assertThat(response.rows().get(0).reasonLabel()).isEqualTo("Appointment");
    }

    @Test
    void deliverySummaryCountsSentWithoutDoubleCountingAttempts() {
        CampaignExecutionRepository executionRepository = mock(CampaignExecutionRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        CampaignDeliveryEventRepository eventRepository = mock(CampaignDeliveryEventRepository.class);
        CarePilotAnalyticsService analyticsService = mock(CarePilotAnalyticsService.class);

        CarePilotRemindersService service = new CarePilotRemindersService(
                executionRepository,
                campaignRepository,
                patientRepository,
                eventRepository,
                analyticsService
        );

        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-2001");
        UUID patientId = patient.getId();
        patient.update("Rita", "Iyer", PatientGender.FEMALE, null, null, "9999999999", "rita@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);

        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000010",
                "Reminder",
                CampaignType.APPOINTMENT_REMINDER,
                TriggerType.MANUAL,
                AudienceType.ALL_PATIENTS,
                null,
                null,
                UUID.randomUUID()
        );
        setField(campaign, "id", campaignId);

        CampaignExecutionEntity sent = CampaignExecutionEntity.create(
                tenantId, campaignId, null, ChannelType.EMAIL, patientId, OffsetDateTime.now(), "CAMPAIGN_MANUAL_TRIGGER", UUID.randomUUID(), "H24", null
        );
        sent.markSucceeded("Provider", "msg-1");
        sent.markDeliveryLifecycleStatus(MessageDeliveryStatus.SENT, null, OffsetDateTime.now());

        when(executionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(sent));
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));
        when(patientRepository.findByTenantIdAndIdIn(tenantId, List.of(patientId))).thenReturn(List.of(patient));
        when(eventRepository.findByTenantIdAndExecutionIdInOrderByEventTimestampAsc(tenantId, List.of(sent.getId()))).thenReturn(List.of());

        var response = service.list(tenantId, "SENT", null, null, null, null, null, null, null, null, null, 0, 50);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.rows().get(0).executionStatus()).isEqualTo(com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus.SUCCEEDED);
        assertThat(response.rows().get(0).deliveryStatus()).isEqualTo(MessageDeliveryStatus.SENT);
    }

    @Test
    void patientQueryMatchesBusinessReference() {
        CampaignExecutionRepository executionRepository = mock(CampaignExecutionRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        CampaignDeliveryEventRepository eventRepository = mock(CampaignDeliveryEventRepository.class);
        CarePilotAnalyticsService analyticsService = mock(CarePilotAnalyticsService.class);

        CarePilotRemindersService service = new CarePilotRemindersService(
                executionRepository,
                campaignRepository,
                patientRepository,
                eventRepository,
                analyticsService
        );

        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-XYZ-9");
        UUID patientId = patient.getId();
        patient.update("Noah", "Brown", PatientGender.MALE, null, null, "9999999999", "noah@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);

        CampaignEntity campaign = CampaignEntity.create(
                tenantId,
                "CAM-2026-000011",
                "Reminder",
                CampaignType.APPOINTMENT_REMINDER,
                TriggerType.MANUAL,
                AudienceType.ALL_PATIENTS,
                null,
                null,
                UUID.randomUUID()
        );
        setField(campaign, "id", campaignId);

        CampaignExecutionEntity execution = CampaignExecutionEntity.create(
                tenantId, campaignId, null, ChannelType.EMAIL, patientId, OffsetDateTime.now(), "CAMPAIGN_MANUAL_TRIGGER", UUID.randomUUID(), "H24", null
        );

        when(executionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(execution));
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));
        when(patientRepository.findByTenantIdAndIdIn(tenantId, List.of(patientId))).thenReturn(List.of(patient));
        when(eventRepository.findByTenantIdAndExecutionIdInOrderByEventTimestampAsc(tenantId, List.of(execution.getId()))).thenReturn(List.of());

        var response = service.list(tenantId, null, null, null, null, null, null, "pat-xyz", null, null, null, 0, 50);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.rows().get(0).patientReference()).isEqualTo("PAT-XYZ-9");
        assertThat(response.rows().get(0).reasonCode()).isEqualTo("CAMPAIGN_MANUAL_TRIGGER");
        assertThat(response.rows().get(0).reasonLabel()).isEqualTo("Manual Campaign Run");
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
