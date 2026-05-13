package com.deepthoughtnet.clinic.carepilot.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CarePilotTemplateRendererTest {

    @Test
    void resolvesSupportedPlaceholders() {
        CarePilotTemplateRenderer renderer = new CarePilotTemplateRenderer();
        UUID tenantId = UUID.randomUUID();
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        patient.update("John", "Doe", null, null, null, "9999999999", "john@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId,
                "Reminder",
                ChannelType.EMAIL,
                "Reminder for {{patientName}} at {{appointmentDateTime}}",
                "Hi {{patientName}}, date {{appointmentDate}}, time {{appointmentTime}}, clinic {{clinicName}}.",
                true
        );

        var rendered = renderer.render(UUID.randomUUID(), template, patient, OffsetDateTime.parse("2026-05-13T10:00:00Z"));

        assertThat(rendered.subject()).contains("John Doe").contains("2026-05-13 10:00");
        assertThat(rendered.body()).contains("2026-05-13").contains("10:00").contains("Clinic");
    }

    @Test
    void unresolvedPlaceholdersAreStrippedSafely() {
        CarePilotTemplateRenderer renderer = new CarePilotTemplateRenderer();
        UUID tenantId = UUID.randomUUID();
        CampaignTemplateEntity template = CampaignTemplateEntity.create(
                tenantId,
                "Reminder",
                ChannelType.EMAIL,
                "{{unknownToken}}",
                "Body {{unknownToken}}",
                true
        );

        var rendered = renderer.render(UUID.randomUUID(), template, null, null);

        assertThat(rendered.subject()).isEmpty();
        assertThat(rendered.body()).isEqualTo("Body ");
    }
}
