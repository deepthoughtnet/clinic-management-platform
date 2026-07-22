package com.deepthoughtnet.clinic.notification.service;

import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEventPayload;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Public notification application service for the appointment-booked reference flow.
 * <p>
 * The notification listener depends on this service instead of direct repository access so the notification
 * domain stays the sole owner of notification persistence and delivery orchestration.
 */
@Service
public class AppointmentBookedNotificationService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentBookedNotificationService.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);

    private final PatientService patientService;
    private final NotificationHistoryService notificationHistoryService;
    private final ObjectMapper objectMapper;

    public AppointmentBookedNotificationService(
            PatientService patientService,
            NotificationHistoryService notificationHistoryService,
            ObjectMapper objectMapper
    ) {
        this.patientService = patientService;
        this.notificationHistoryService = notificationHistoryService;
        this.objectMapper = objectMapper;
    }

    public NotificationQueueResult queue(AppointmentBookedEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Appointment booked event is required");
        }
        AppointmentBookedEventPayload payload = event.payload();
        UUID tenantId = event.tenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        PatientRecord patient = patientService.findById(tenantId, payload.patientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        ZoneId zone = resolveZone(payload.appointmentTimezone());
        String formattedDateTime = formatAppointmentDateTime(payload.appointmentDate(), payload.appointmentTime(), zone);
        String doctorDisplayName = hasText(payload.doctorDisplayName()) ? payload.doctorDisplayName() : "your doctor";
        String subject = "Appointment confirmed";
        String message = "Your appointment with " + doctorDisplayName + " is confirmed for " + formattedDateTime + ".";
        String historyRecipient = patientTargetLabel(patient);
        String deliveryRecipient = resolveDeliveryRecipient(patient);
        String detailsJson = buildDetailsJson(event, payload, formattedDateTime, doctorDisplayName);

        NotificationQueueResult result = notificationHistoryService.queueDetailed(
                tenantId,
                patient.id(),
                "APPOINTMENT_BOOKED",
                "in_app",
                historyRecipient,
                hasText(deliveryRecipient) ? "email" : null,
                deliveryRecipient,
                subject,
                message,
                "APPOINTMENT",
                payload.appointmentId(),
                detailsJson,
                event.actorId()
        );

        log.info(
                "appointment_booked_notification_queued tenantId={} eventId={} appointmentId={} patientId={} recipient={} deliveryRecipient={} created={}",
                tenantId,
                event.eventId(),
                payload.appointmentId(),
                patient.id(),
                historyRecipient,
                mask(deliveryRecipient),
                result.created()
        );
        return result;
    }

    private String buildDetailsJson(
            AppointmentBookedEvent event,
            AppointmentBookedEventPayload payload,
            String formattedDateTime,
            String doctorDisplayName
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("appointmentId", payload.appointmentId());
        details.put("patientId", payload.patientId());
        details.put("doctorUserId", payload.doctorUserId());
        details.put("appointmentTimezone", payload.appointmentTimezone());
        details.put("appointmentDateTime", formattedDateTime);
        details.put("doctorDisplayName", doctorDisplayName);
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private ZoneId resolveZone(String zoneId) {
        if (!hasText(zoneId)) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (Exception ex) {
            return DEFAULT_ZONE;
        }
    }

    private String formatAppointmentDateTime(LocalDate date, LocalTime time, ZoneId zone) {
        if (date == null) {
            return "";
        }
        if (time == null) {
            return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        }
        OffsetDateTime value = LocalDateTime.of(date, time).atZone(zone).toOffsetDateTime();
        return DATE_TIME_FORMATTER.format(value.atZoneSameInstant(zone)) + " " + zoneLabel(zone, value);
    }

    private String zoneLabel(ZoneId zone, OffsetDateTime value) {
        if (zone == null) {
            return "UTC+00:00";
        }
        if ("Asia/Kolkata".equals(zone.getId())) {
            return "IST (UTC+05:30)";
        }
        try {
            String shortName = DateTimeFormatter.ofPattern("z", Locale.ENGLISH).withZone(zone).format(value.atZoneSameInstant(zone));
            if (!hasText(shortName) || shortName.startsWith("GMT") || shortName.startsWith("UTC")) {
                return zone.getId() + " (" + offsetLabel(zone, value) + ")";
            }
            return shortName + " (" + offsetLabel(zone, value) + ")";
        } catch (Exception ex) {
            return zone.getId() + " (" + offsetLabel(zone, value) + ")";
        }
    }

    private String offsetLabel(ZoneId zone, OffsetDateTime value) {
        try {
            java.time.ZoneOffset offset = zone.getRules().getOffset(value.toInstant());
            int totalSeconds = offset.getTotalSeconds();
            int absoluteSeconds = Math.abs(totalSeconds);
            int hours = absoluteSeconds / 3600;
            int minutes = (absoluteSeconds % 3600) / 60;
            return String.format(Locale.ENGLISH, "UTC%s%02d:%02d", totalSeconds >= 0 ? "+" : "-", hours, minutes);
        } catch (Exception ex) {
            return "UTC+00:00";
        }
    }

    private String patientTargetLabel(PatientRecord patient) {
        String name = patient.fullName();
        if (StringUtils.hasText(patient.patientNumber())) {
            return "patient:" + patient.patientNumber() + " • " + name;
        }
        return StringUtils.hasText(name) ? "patient:" + name : "patient";
    }

    private String resolveDeliveryRecipient(PatientRecord patient) {
        if (patient == null) {
            return null;
        }
        if (hasText(patient.email())) {
            return patient.email().trim();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String mask(String value) {
        if (!hasText(value)) {
            return null;
        }
        if (value.contains("@")) {
            int at = value.indexOf('@');
            return value.charAt(0) + "***" + value.substring(at);
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
