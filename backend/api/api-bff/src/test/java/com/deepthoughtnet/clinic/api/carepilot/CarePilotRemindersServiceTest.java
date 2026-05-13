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

        var response = service.list(tenantId, null, null, null, null, null, "anE", null, null, null, 0, 50);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.rows().get(0).patientName()).contains("Jane");
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
