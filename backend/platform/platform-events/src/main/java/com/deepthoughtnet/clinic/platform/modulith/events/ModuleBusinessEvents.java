package com.deepthoughtnet.clinic.platform.modulith.events;

import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentBookedEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentCancelledEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentCancelledEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentReminderDueEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentReminderDueEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentRescheduledEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentRescheduledEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LeadConvertedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LeadConvertedEventPayload;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Legacy compatibility helper retained for the platform-events test suite while
 * the public business contracts live in the owning modules.
 */
public final class ModuleBusinessEvents {
    private static final String APPOINTMENT_MODULE = "APPOINTMENT";
    private static final String LAB_MODULE = "LAB";
    private static final String ENGAGE_MODULE = "ENGAGE";

    private ModuleBusinessEvents() {
    }

    public static AppointmentBookedEvent appointmentBooked(
            UUID tenantId,
            UUID appointmentId,
            UUID patientId,
            UUID doctorUserId,
            String doctorDisplayName,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String appointmentTimezone,
            String appointmentStatus,
            String appointmentType,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new AppointmentBookedEvent(
                deterministicEventId("APPOINTMENT_BOOKED", tenantId, appointmentId),
                "APPOINTMENT_BOOKED",
                1,
                occurredAt,
                tenantId,
                APPOINTMENT_MODULE,
                "APPOINTMENT",
                appointmentId,
                correlationId,
                correlationId,
                actorId,
                new AppointmentBookedEventPayload(
                        appointmentId,
                        patientId,
                        doctorUserId,
                        doctorDisplayName,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone,
                        appointmentStatus,
                        appointmentType
                )
        );
    }

    public static AppointmentRescheduledEvent appointmentRescheduled(
            UUID tenantId,
            UUID appointmentId,
            UUID patientId,
            UUID doctorUserId,
            String doctorDisplayName,
            String clinicDisplayName,
            LocalDate previousAppointmentDate,
            LocalTime previousAppointmentTime,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String appointmentTimezone,
            int appointmentVersion,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new AppointmentRescheduledEvent(
                deterministicEventId(
                        "APPOINTMENT_RESCHEDULED",
                        tenantId,
                        appointmentId,
                        appointmentVersion,
                        previousAppointmentDate,
                        previousAppointmentTime,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone
                ),
                "APPOINTMENT_RESCHEDULED",
                1,
                occurredAt,
                tenantId,
                APPOINTMENT_MODULE,
                "APPOINTMENT",
                appointmentId,
                correlationId,
                correlationId,
                actorId,
                new AppointmentRescheduledEventPayload(
                        appointmentId,
                        patientId,
                        doctorUserId,
                        doctorDisplayName,
                        clinicDisplayName,
                        previousAppointmentDate,
                        previousAppointmentTime,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone,
                        appointmentVersion
                )
        );
    }

    public static AppointmentCancelledEvent appointmentCancelled(
            UUID tenantId,
            UUID appointmentId,
            UUID patientId,
            UUID doctorUserId,
            String doctorDisplayName,
            String clinicDisplayName,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String appointmentTimezone,
            int appointmentVersion,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new AppointmentCancelledEvent(
                deterministicEventId(
                        "APPOINTMENT_CANCELLED",
                        tenantId,
                        appointmentId,
                        appointmentVersion,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone
                ),
                "APPOINTMENT_CANCELLED",
                1,
                occurredAt,
                tenantId,
                APPOINTMENT_MODULE,
                "APPOINTMENT",
                appointmentId,
                correlationId,
                correlationId,
                actorId,
                new AppointmentCancelledEventPayload(
                        appointmentId,
                        patientId,
                        doctorUserId,
                        doctorDisplayName,
                        clinicDisplayName,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone,
                        appointmentVersion
                )
        );
    }

    public static AppointmentReminderDueEvent appointmentReminderDue(
            UUID tenantId,
            UUID appointmentId,
            UUID patientId,
            UUID doctorUserId,
            String doctorDisplayName,
            String clinicDisplayName,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String appointmentTimezone,
            String reminderWindow,
            int appointmentVersion,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new AppointmentReminderDueEvent(
                deterministicEventId(
                        "APPOINTMENT_REMINDER_DUE",
                        tenantId,
                        appointmentId,
                        appointmentVersion,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone,
                        reminderWindow
                ),
                "APPOINTMENT_REMINDER_DUE",
                1,
                occurredAt,
                tenantId,
                APPOINTMENT_MODULE,
                "APPOINTMENT",
                appointmentId,
                correlationId,
                correlationId,
                actorId,
                new AppointmentReminderDueEventPayload(
                        appointmentId,
                        patientId,
                        doctorUserId,
                        doctorDisplayName,
                        clinicDisplayName,
                        appointmentDate,
                        appointmentTime,
                        appointmentTimezone,
                        reminderWindow,
                        appointmentVersion
                )
        );
    }

    public static LabReportPublishedEvent labReportPublished(
            UUID tenantId,
            UUID labOrderId,
            UUID patientId,
            UUID consultationId,
            String reportFilename,
            String deliveryStatus,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new LabReportPublishedEvent(
                deterministicEventId("LAB_REPORT_PUBLISHED", tenantId, labOrderId),
                "LAB_REPORT_PUBLISHED",
                1,
                occurredAt,
                tenantId,
                LAB_MODULE,
                "LAB_ORDER",
                labOrderId,
                correlationId,
                correlationId,
                actorId,
                new LabReportPublishedEventPayload(
                        labOrderId,
                        patientId,
                        consultationId,
                        reportFilename,
                        deliveryStatus
                )
        );
    }

    public static LeadConvertedEvent leadConverted(
            UUID tenantId,
            UUID leadId,
            UUID patientId,
            boolean createdNewPatient,
            UUID bookedAppointmentId,
            UUID actorId
    ) {
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new LeadConvertedEvent(
                deterministicEventId("LEAD_CONVERTED", tenantId, leadId),
                "LEAD_CONVERTED",
                1,
                occurredAt,
                tenantId,
                ENGAGE_MODULE,
                "LEAD",
                leadId,
                correlationId,
                correlationId,
                actorId,
                new LeadConvertedEventPayload(
                        leadId,
                        patientId,
                        createdNewPatient,
                        bookedAppointmentId
                )
        );
    }

    public static UUID deterministicEventId(Object... parts) {
        StringBuilder seed = new StringBuilder();
        if (parts != null) {
            for (Object part : parts) {
                if (seed.length() > 0) {
                    seed.append('|');
                }
                seed.append(part == null ? "" : part.toString());
            }
        }
        return UUID.nameUUIDFromBytes(seed.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String currentCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("X-Correlation-ID");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId.trim();
    }

}
