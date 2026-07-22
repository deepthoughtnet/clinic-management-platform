package com.deepthoughtnet.clinic.platform.modulith.events;

import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentBookedEventPayload;
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
            LocalDate appointmentDate,
            LocalTime appointmentTime,
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
                        appointmentDate,
                        appointmentTime,
                        appointmentStatus,
                        appointmentType
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

    public static UUID deterministicEventId(String eventType, UUID tenantId, UUID aggregateId) {
        String seed = String.join("|",
                safe(eventType),
                tenantId == null ? "" : tenantId.toString(),
                aggregateId == null ? "" : aggregateId.toString()
        );
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
