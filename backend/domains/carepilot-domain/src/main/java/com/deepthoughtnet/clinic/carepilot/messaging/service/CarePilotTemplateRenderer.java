package com.deepthoughtnet.clinic.carepilot.messaging.service;

import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lightweight token replacement for CarePilot campaign templates.
 *
 * <p>v1 intentionally avoids introducing a full template engine while still enabling
 * operational campaign personalization using stable placeholder tokens.</p>
 */
@Component
public class CarePilotTemplateRenderer {

    /**
     * Renders subject and body using known placeholders and safe fallbacks.
     */
    public RenderedTemplate render(UUID campaignId, CampaignTemplateEntity template, PatientEntity patient, OffsetDateTime scheduledAt) {
        return render(campaignId, template, patient, scheduledAt, Map.of());
    }

    /**
     * Renders subject and body using known placeholders, source-specific placeholders, and safe fallbacks.
     */
    public RenderedTemplate render(
            UUID campaignId,
            CampaignTemplateEntity template,
            PatientEntity patient,
            OffsetDateTime scheduledAt,
            Map<String, String> additionalValues
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("patientName", patient == null ? "Patient" : safeFullName(patient));
        values.put("appointmentDate", scheduledAt == null ? "" : scheduledAt.toLocalDate().toString());
        values.put("appointmentTime", scheduledAt == null ? "" : scheduledAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        values.put("appointmentDateTime", scheduledAt == null ? "" : scheduledAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        values.put("doctorName", "");
        values.put("followUpDate", scheduledAt == null ? "" : scheduledAt.toLocalDate().toString());
        values.put("followUpTime", scheduledAt == null ? "" : scheduledAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        values.put("consultationSummary", "");
        values.put("billDueDate", scheduledAt == null ? "" : scheduledAt.toLocalDate().toString());
        values.put("reschedulePhone", "");
        values.put("clinicName", "Clinic");
        values.put("clinicPhone", "");
        values.put("clinicAddress", "");
        values.put("campaignId", campaignId == null ? "" : campaignId.toString());
        if (additionalValues != null && !additionalValues.isEmpty()) {
            values.putAll(additionalValues);
        }

        String renderedSubject = replaceTokens(template.getSubjectLine(), values);
        String renderedBody = replaceTokens(template.getBodyTemplate(), values);
        return new RenderedTemplate(renderedSubject, renderedBody);
    }

    private String safeFullName(PatientEntity patient) {
        String fullName = ((patient.getFirstName() == null ? "" : patient.getFirstName()) + " " +
                (patient.getLastName() == null ? "" : patient.getLastName())).trim();
        return fullName.isEmpty() ? "Patient" : fullName;
    }

    private String replaceTokens(String template, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        // Strip unresolved placeholders to avoid leaking raw tokens in patient-facing reminders.
        return rendered.replaceAll("\\{\\{[^}]+}}", "");
    }

    /**
     * Rendered subject/body payload.
     */
    public record RenderedTemplate(String subject, String body) {
    }
}
