package com.deepthoughtnet.clinic.notification.service;

import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentCancelledEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEventPayload;
import com.deepthoughtnet.clinic.appointment.events.AppointmentCancelledEventPayload;
import com.deepthoughtnet.clinic.appointment.events.AppointmentReminderDueEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentReminderDueEventPayload;
import com.deepthoughtnet.clinic.appointment.events.AppointmentRescheduledEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentRescheduledEventPayload;
import com.deepthoughtnet.clinic.billing.events.BillGeneratedEvent;
import com.deepthoughtnet.clinic.billing.events.BillGeneratedEventPayload;
import com.deepthoughtnet.clinic.billing.events.PaymentReceivedEvent;
import com.deepthoughtnet.clinic.billing.events.PaymentReceivedEventPayload;
import com.deepthoughtnet.clinic.billing.events.PaymentReminderEvent;
import com.deepthoughtnet.clinic.billing.events.PaymentReminderEventPayload;
import com.deepthoughtnet.clinic.billing.service.PaymentReminderStateReader;
import com.deepthoughtnet.clinic.billing.service.model.PaymentReminderState;
import com.deepthoughtnet.clinic.consultation.events.FollowUpDueEvent;
import com.deepthoughtnet.clinic.consultation.events.FollowUpDueEventPayload;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.prescription.events.PrescriptionReadyEvent;
import com.deepthoughtnet.clinic.prescription.events.PrescriptionReadyEventPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.model.VaccinationDueEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.VaccinationDueEventPayload;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final PaymentReminderStateReader paymentReminderStateReader;
    private final NotificationHistoryService notificationHistoryService;
    private final ObjectMapper objectMapper;

    public AppointmentBookedNotificationService(
            PatientService patientService,
            PaymentReminderStateReader paymentReminderStateReader,
            NotificationHistoryService notificationHistoryService,
            ObjectMapper objectMapper
    ) {
        this.patientService = patientService;
        this.paymentReminderStateReader = paymentReminderStateReader;
        this.notificationHistoryService = notificationHistoryService;
        this.objectMapper = objectMapper;
    }

    public NotificationQueueResult queue(AppointmentBookedEvent event) {
        return queueBooked(event);
    }

    public NotificationQueueResult queue(AppointmentRescheduledEvent event) {
        return queueRescheduled(event);
    }

    public NotificationQueueResult queue(AppointmentCancelledEvent event) {
        return queueCancelled(event);
    }

    public NotificationQueueResult queue(AppointmentReminderDueEvent event) {
        return queueReminderDue(event);
    }

    public NotificationQueueResult queueBooked(AppointmentBookedEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Appointment booked event is required");
        }
        AppointmentBookedEventPayload payload = event.payload();
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.appointmentId(),
                "APPOINTMENT_BOOKED",
                "APPOINTMENT",
                payload.appointmentTimezone(),
                payload.doctorDisplayName(),
                payload.appointmentDate(),
                payload.appointmentTime(),
                "Appointment confirmed",
                "Your appointment with " + safeDoctorName(payload.doctorDisplayName()) + " is confirmed for %s.".formatted(formatAppointmentDateTime(payload.appointmentDate(), payload.appointmentTime(), resolveZone(payload.appointmentTimezone()))),
                buildBookedDetailsJson(event, payload)
        );
    }

    public NotificationQueueResult queueRescheduled(AppointmentRescheduledEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Appointment rescheduled event is required");
        }
        AppointmentRescheduledEventPayload payload = event.payload();
        String zoneId = payload.appointmentTimezone();
        ZoneId zone = resolveZone(zoneId);
        String previousDateTime = formatAppointmentDateTime(payload.previousAppointmentDate(), payload.previousAppointmentTime(), zone);
        String nextDateTime = formatAppointmentDateTime(payload.appointmentDate(), payload.appointmentTime(), zone);
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.appointmentId(),
                "APPOINTMENT_RESCHEDULED",
                "APPOINTMENT",
                zoneId,
                payload.doctorDisplayName(),
                payload.appointmentDate(),
                payload.appointmentTime(),
                "Appointment rescheduled",
                "Your appointment with " + safeDoctorName(payload.doctorDisplayName()) + " has been rescheduled from " + previousDateTime + " to " + nextDateTime + ".",
                buildRescheduledDetailsJson(event, payload, previousDateTime, nextDateTime)
        );
    }

    public NotificationQueueResult queueCancelled(AppointmentCancelledEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Appointment cancelled event is required");
        }
        AppointmentCancelledEventPayload payload = event.payload();
        ZoneId zone = resolveZone(payload.appointmentTimezone());
        String when = formatAppointmentDateTime(payload.appointmentDate(), payload.appointmentTime(), zone);
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.appointmentId(),
                "APPOINTMENT_CANCELLED",
                "APPOINTMENT",
                payload.appointmentTimezone(),
                payload.doctorDisplayName(),
                payload.appointmentDate(),
                payload.appointmentTime(),
                "Appointment cancelled",
                "Your appointment with " + safeDoctorName(payload.doctorDisplayName()) + " scheduled for " + when + " has been cancelled.",
                buildCancelledDetailsJson(event, payload, when)
        );
    }

    public NotificationQueueResult queueReminderDue(AppointmentReminderDueEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Appointment reminder event is required");
        }
        AppointmentReminderDueEventPayload payload = event.payload();
        ZoneId zone = resolveZone(payload.appointmentTimezone());
        String when = formatAppointmentDateTime(payload.appointmentDate(), payload.appointmentTime(), zone);
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.appointmentId(),
                "APPOINTMENT_REMINDER_DUE",
                "APPOINTMENT",
                payload.appointmentTimezone(),
                payload.doctorDisplayName(),
                payload.appointmentDate(),
                payload.appointmentTime(),
                "Appointment reminder",
                "Reminder: You have an appointment with " + safeDoctorName(payload.doctorDisplayName()) + " on " + when + ".",
                buildReminderDetailsJson(event, payload, when)
        );
    }

    public NotificationQueueResult queue(PrescriptionReadyEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Prescription ready event is required");
        }
        PrescriptionReadyEventPayload payload = event.payload();
        ZoneId zone = resolveZone(payload.timezone());
        String followUp = payload.followUpDate() == null ? null : formatAppointmentDateTime(payload.followUpDate(), null, zone);
        String subject = "Prescription ready";
        StringBuilder message = new StringBuilder("Your prescription from ").append(safeDoctorName(payload.doctorDisplayName())).append(" is ready.");
        if (hasText(followUp)) {
            message.append(" Visit date: ").append(followUp).append(".");
        }
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.prescriptionId(),
                "PRESCRIPTION_READY",
                "PRESCRIPTION",
                payload.timezone(),
                payload.doctorDisplayName(),
                null,
                null,
                subject,
                message.toString(),
                buildPrescriptionReadyDetailsJson(event, payload, followUp)
        );
    }

    public NotificationQueueResult queue(LabReportPublishedEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Lab report published event is required");
        }
        LabReportPublishedEventPayload payload = event.payload();
        String subject = "Lab report ready";
        String message = hasText(payload.orderNumber())
                ? "Your lab report for order " + payload.orderNumber() + " is ready."
                : "Your lab report is ready.";
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.labOrderId(),
                "LAB_REPORT_READY",
                "LAB_ORDER",
                payload.timezone(),
                null,
                null,
                null,
                subject,
                message,
                buildLabReportReadyDetailsJson(event, payload)
        );
    }

    public NotificationQueueResult queue(BillGeneratedEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Bill generated event is required");
        }
        BillGeneratedEventPayload payload = event.payload();
        String amountText = formatMoney(payload.amount());
        StringBuilder message = new StringBuilder("Your bill of ").append(amountText).append(" is ready.");
        if (payload.dueAt() != null) {
            message.append(" Payment is due by ").append(formatAppointmentDateTime(payload.dueAt(), null, resolveZone(payload.timezone()))).append(".");
        }
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.billId(),
                "BILL_GENERATED",
                "BILL",
                payload.timezone(),
                null,
                null,
                null,
                "Bill generated",
                message.toString(),
                buildBillGeneratedDetailsJson(event, payload, amountText)
        );
    }

    public NotificationQueueResult queue(PaymentReceivedEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Payment received event is required");
        }
        PaymentReceivedEventPayload payload = event.payload();
        String amountText = formatMoney(payload.amount());
        StringBuilder message = new StringBuilder("We received your payment of ").append(amountText).append(". Thank you.");
        if (hasText(payload.receiptNumber())) {
            message.append(" Receipt ").append(payload.receiptNumber()).append(" has been generated.");
        }
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.paymentId(),
                "PAYMENT_RECEIVED",
                "BILL",
                payload.timezone(),
                null,
                null,
                null,
                "Payment received",
                message.toString(),
                buildPaymentReceivedDetailsJson(event, payload, amountText)
        );
    }

    public NotificationQueueResult queue(PaymentReminderEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Payment reminder event is required");
        }
        PaymentReminderEventPayload payload = event.payload();
        String reminderMessage = "Reminder: " + formatMoney(payload.outstandingAmount()) + " is outstanding on bill " + payload.billNumber() + ".";
        PaymentReminderState currentState = paymentReminderStateReader.findCurrentState(event.tenantId(), payload.billId());
        boolean stale = !currentState.found()
                || !currentState.reminderEligible()
                || currentState.outstandingAmount() == null
                || currentState.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0
                || (payload.outstandingAmount() != null && currentState.outstandingAmount().compareTo(payload.outstandingAmount()) != 0)
                || (payload.billStatus() != null && currentState.billStatus() != null && !payload.billStatus().equalsIgnoreCase(currentState.billStatus()))
                || (payload.billUpdatedAt() != null
                && currentState.updatedAt() != null
                && !payload.billUpdatedAt().toInstant().equals(currentState.updatedAt().toInstant()));
        if (stale) {
            PatientRecord patient = patientService.findById(event.tenantId(), payload.patientId()).orElse(null);
            String staleReason = stalePaymentReminderReason(currentState, payload);
            if (patient == null || !patient.active()) {
                return notificationHistoryService.recordSkipped(
                        event.tenantId(),
                        payload.patientId(),
                        "PAYMENT_REMINDER",
                        "in_app",
                        payload.patientId() == null ? null : payload.patientId().toString(),
                        "Payment reminder",
                        reminderMessage,
                        "BILL",
                        payload.billId(),
                        buildPaymentReminderDetailsJson(event, payload, reminderMessage),
                        event.actorId(),
                        buildPaymentReminderDedupeKey(event, payload, "in_app", payload.patientId() == null ? null : payload.patientId().toString()),
                        "Patient record unavailable"
                );
            }
            return notificationHistoryService.recordSkipped(
                    event.tenantId(),
                    payload.patientId(),
                    "PAYMENT_REMINDER",
                    "in_app",
                    payload.patientId().toString(),
                    "Payment reminder",
                    reminderMessage,
                    "BILL",
                    payload.billId(),
                    buildPaymentReminderDetailsJson(event, payload, reminderMessage),
                    event.actorId(),
                    buildPaymentReminderDedupeKey(event, payload, "in_app", payload.patientId().toString()),
                    staleReason
            );
        }

        PatientRecord patient = patientService.findById(event.tenantId(), payload.patientId()).orElse(null);
        if (patient == null || !patient.active()) {
            return notificationHistoryService.recordSkipped(
                    event.tenantId(),
                    payload.patientId(),
                    "PAYMENT_REMINDER",
                    "in_app",
                    payload.patientId() == null ? null : payload.patientId().toString(),
                    "Payment reminder",
                    reminderMessage,
                    "BILL",
                    payload.billId(),
                    buildPaymentReminderDetailsJson(event, payload, reminderMessage),
                    event.actorId(),
                    buildPaymentReminderDedupeKey(event, payload, "in_app", payload.patientId() == null ? null : payload.patientId().toString()),
                    "Patient record unavailable"
            );
        }
        String subject = "Payment reminder";
        String message = "Reminder: " + formatMoney(currentState.outstandingAmount()) + " is outstanding on bill " + payload.billNumber() + ".";
        NotificationQueueResult result = queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                patient.id(),
                payload.billId(),
                "PAYMENT_REMINDER",
                "BILL",
                payload.timezone(),
                null,
                null,
                null,
                subject,
                message,
                buildPaymentReminderDetailsJson(event, payload, message)
        );
        return result;
    }

    public NotificationQueueResult queue(FollowUpDueEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Follow-up due event is required");
        }
        FollowUpDueEventPayload payload = event.payload();
        ZoneId zone = resolveZone(payload.timezone());
        String followUpDate = payload.followUpDate() == null ? null : formatAppointmentDateTime(payload.followUpDate(), null, zone);
        String subject = "Follow-up reminder";
        String message = hasText(payload.doctorDisplayName())
                ? "Reminder: Your follow-up with " + safeDoctorName(payload.doctorDisplayName()) + " is due on " + followUpDate + "."
                : "Reminder: Your follow-up is due on " + followUpDate + ".";
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.consultationId(),
                "FOLLOW_UP_DUE",
                "CONSULTATION",
                payload.timezone(),
                payload.doctorDisplayName(),
                payload.followUpDate(),
                null,
                subject,
                message,
                buildFollowUpDueDetailsJson(event, payload, followUpDate)
        );
    }

    public NotificationQueueResult queue(VaccinationDueEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("Vaccination due event is required");
        }
        VaccinationDueEventPayload payload = event.payload();
        String dueDate = payload.dueDate() == null ? null : formatAppointmentDateTime(payload.dueDate(), null, resolveZone(payload.timezone()));
        String subject = "Vaccination due";
        String message = hasText(payload.doseDisplayName())
                ? payload.vaccineDisplayName() + " - " + payload.doseDisplayName() + " is due on " + dueDate + "."
                : payload.vaccineDisplayName() + " vaccination is due on " + dueDate + ".";
        return queueLifecycle(
                event.tenantId(),
                event.eventId(),
                event.actorId(),
                payload.patientId(),
                payload.vaccinationScheduleEntryId(),
                "VACCINATION_DUE",
                "PATIENT_VACCINATION",
                payload.timezone(),
                null,
                payload.dueDate(),
                null,
                subject,
                message,
                buildVaccinationDueDetailsJson(event, payload, dueDate)
        );
    }

    private NotificationQueueResult queueLifecycle(
            UUID tenantId,
            UUID eventId,
            UUID actorAppUserId,
            UUID patientId,
            UUID sourceId,
            String eventType,
            String sourceType,
            String appointmentTimezone,
            String doctorDisplayName,
            java.time.LocalDate appointmentDate,
            java.time.LocalTime appointmentTime,
            String subject,
            String message,
            String detailsJson
    ) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        PatientRecord patient = patientId == null ? null : patientService.findById(tenantId, patientId).orElse(null);
        if (patient == null || !patient.active()) {
            NotificationQueueResult skipped = notificationHistoryService.recordSkipped(
                    tenantId,
                    patientId,
                    eventType,
                    "in_app",
                    patientId == null ? null : patientId.toString(),
                    subject,
                    message,
                    sourceType,
                    sourceId,
                    detailsJson,
                    actorAppUserId,
                    buildDedupeKey(eventType, tenantId, eventId, patientId, "in_app", patientId == null ? null : patientId.toString(), sourceType, sourceId),
                    "Patient record unavailable"
            );
            log.info(
                    "appointment_notification_skipped eventType={} tenantId={} eventId={} appointmentId={} patientId={} reason={}",
                    eventType,
                    tenantId,
                    eventId,
                    sourceId,
                    patientId,
                    "Patient record unavailable"
            );
            return skipped;
        }

        String patientRecipientId = patientId.toString();
        String patientDisplayName = safePatientDisplayName(patient);
        NotificationQueueResult result = queueChannel(
                tenantId,
                patientId,
                eventType,
                sourceType,
                sourceId,
                subject,
                message,
                detailsJson,
                actorAppUserId,
                eventId,
                "in_app",
                patientRecipientId,
                "in_app",
                patientRecipientId,
                patientDisplayName,
                null
        );
        queueEmailChannel(tenantId, patient, eventType, sourceType, sourceId, subject, message, detailsJson, actorAppUserId, eventId, patientDisplayName);
        queueSmsChannel(tenantId, patient, eventType, sourceType, sourceId, subject, message, detailsJson, actorAppUserId, eventId, patientDisplayName);
        queueWhatsAppChannel(tenantId, patient, eventType, sourceType, sourceId, subject, message, detailsJson, actorAppUserId, eventId, patientDisplayName);
        log.info(
                "appointment_notification_queued eventType={} tenantId={} eventId={} appointmentId={} patientId={} doctorDisplayName={} appointmentDate={} appointmentTime={} recipient={} deliveryRecipient={} created={}",
                eventType,
                tenantId,
                eventId,
                sourceId,
                patientId,
                doctorDisplayName,
                appointmentDate,
                appointmentTime,
                patientDisplayName,
                mask(patientRecipientId),
                result.created()
        );
        return result;
    }

    private NotificationQueueResult queueChannel(
            UUID tenantId,
            UUID patientId,
            String eventType,
            String sourceType,
            UUID sourceId,
            String subject,
            String message,
            String detailsJson,
            UUID actorAppUserId,
            UUID eventId,
            String historyChannel,
            String historyRecipient,
            String deliveryChannel,
            String deliveryRecipient,
            String deliveryDisplayName,
            String skipReason
    ) {
        String augmentedDetailsJson = augmentDetailsJson(detailsJson, historyChannel, deliveryRecipient, deliveryDisplayName);
        if (skipReason != null) {
            return notificationHistoryService.recordSkipped(
                    tenantId,
                    patientId,
                    eventType,
                    historyChannel,
                    historyRecipient,
                    subject,
                    message,
                    sourceType,
                    sourceId,
                    augmentedDetailsJson,
                    actorAppUserId,
                    buildDedupeKey(eventType, tenantId, eventId, patientId, historyChannel, historyRecipient, sourceType, sourceId),
                    skipReason
            );
        }
        return notificationHistoryService.queueDetailed(
                tenantId,
                patientId,
                eventType,
                historyChannel,
                historyRecipient,
                deliveryChannel,
                deliveryRecipient,
                subject,
                message,
                sourceType,
                sourceId,
                augmentedDetailsJson,
                actorAppUserId,
                buildDedupeKey(eventType, tenantId, eventId, patientId, historyChannel, historyRecipient, sourceType, sourceId)
        );
    }

    private void queueEmailChannel(
            UUID tenantId,
            PatientRecord patient,
            String eventType,
            String sourceType,
            UUID sourceId,
            String subject,
            String message,
            String detailsJson,
            UUID actorAppUserId,
            UUID eventId,
            String patientDisplayName
    ) {
        String email = normalizeEmail(patient.email());
        if (!hasText(email)) {
            queueChannel(
                    tenantId,
                    patient.id(),
                    eventType,
                    sourceType,
                    sourceId,
                    subject,
                    message,
                    detailsJson,
                    actorAppUserId,
                    eventId,
                    "email",
                    patient.id().toString(),
                    "email",
                    patient.id().toString(),
                    patientDisplayName,
                    "Patient email unavailable"
            );
            return;
        }
        if (!isValidEmail(email)) {
            queueChannel(
                    tenantId,
                    patient.id(),
                    eventType,
                    sourceType,
                    sourceId,
                    subject,
                    message,
                    detailsJson,
                    actorAppUserId,
                    eventId,
                    "email",
                    patient.id().toString(),
                    "email",
                    email,
                    patientDisplayName,
                    "Invalid patient email"
            );
            return;
        }
        queueChannel(
                tenantId,
                patient.id(),
                eventType,
                sourceType,
                sourceId,
                subject,
                message,
                detailsJson,
                actorAppUserId,
                eventId,
                "email",
                email,
                "email",
                email,
                patientDisplayName,
                null
        );
    }

    private void queueSmsChannel(
            UUID tenantId,
            PatientRecord patient,
            String eventType,
            String sourceType,
            UUID sourceId,
            String subject,
            String message,
            String detailsJson,
            UUID actorAppUserId,
            UUID eventId,
            String patientDisplayName
    ) {
        String mobile = normalizeMobile(patient.mobile());
        if (!hasText(mobile)) {
            queueChannel(
                    tenantId,
                    patient.id(),
                    eventType,
                    sourceType,
                    sourceId,
                    subject,
                    message,
                    detailsJson,
                    actorAppUserId,
                    eventId,
                    "sms",
                    patient.id().toString(),
                    "sms",
                    patient.id().toString(),
                    patientDisplayName,
                    "Patient mobile unavailable"
            );
            return;
        }
        if (!isValidMobile(mobile)) {
            queueChannel(
                    tenantId,
                    patient.id(),
                    eventType,
                    sourceType,
                    sourceId,
                    subject,
                    message,
                    detailsJson,
                    actorAppUserId,
                    eventId,
                    "sms",
                    patient.id().toString(),
                    "sms",
                    mobile,
                    patientDisplayName,
                    "Invalid patient mobile"
            );
            return;
        }
        queueChannel(
                tenantId,
                patient.id(),
                eventType,
                sourceType,
                sourceId,
                subject,
                message,
                detailsJson,
                actorAppUserId,
                eventId,
                "sms",
                mobile,
                "sms",
                mobile,
                patientDisplayName,
                null
        );
    }

    private void queueWhatsAppChannel(
            UUID tenantId,
            PatientRecord patient,
            String eventType,
            String sourceType,
            UUID sourceId,
            String subject,
            String message,
            String detailsJson,
            UUID actorAppUserId,
            UUID eventId,
            String patientDisplayName
    ) {
        String mobile = normalizeMobile(patient.mobile());
        if (!hasText(mobile)) {
            queueChannel(
                    tenantId,
                    patient.id(),
                    eventType,
                    sourceType,
                    sourceId,
                    subject,
                    message,
                    detailsJson,
                    actorAppUserId,
                    eventId,
                    "whatsapp",
                    patient.id().toString(),
                    "whatsapp",
                    patient.id().toString(),
                    patientDisplayName,
                    "Patient mobile unavailable"
            );
            return;
        }
        if (!isValidMobile(mobile)) {
            queueChannel(
                    tenantId,
                    patient.id(),
                    eventType,
                    sourceType,
                    sourceId,
                    subject,
                    message,
                    detailsJson,
                    actorAppUserId,
                    eventId,
                    "whatsapp",
                    patient.id().toString(),
                    "whatsapp",
                    mobile,
                    patientDisplayName,
                    "Invalid patient mobile"
            );
            return;
        }
        queueChannel(
                tenantId,
                patient.id(),
                eventType,
                sourceType,
                sourceId,
                subject,
                message,
                detailsJson,
                actorAppUserId,
                eventId,
                "whatsapp",
                mobile,
                "whatsapp",
                mobile,
                patientDisplayName,
                null
        );
    }

    private String buildBookedDetailsJson(AppointmentBookedEvent event, AppointmentBookedEventPayload payload) {
        ZoneId zone = resolveZone(payload.appointmentTimezone());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("appointmentId", payload.appointmentId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("doctorUserId", payload.doctorUserId());
        details.put("appointmentTimezone", payload.appointmentTimezone());
        details.put("appointmentDateTime", formatAppointmentDateTime(payload.appointmentDate(), payload.appointmentTime(), zone));
        details.put("doctorDisplayName", safeDoctorName(payload.doctorDisplayName()));
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildRescheduledDetailsJson(
            AppointmentRescheduledEvent event,
            AppointmentRescheduledEventPayload payload,
            String previousDateTime,
            String nextDateTime
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("appointmentId", payload.appointmentId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("doctorUserId", payload.doctorUserId());
        details.put("appointmentTimezone", payload.appointmentTimezone());
        details.put("appointmentVersion", payload.appointmentVersion());
        details.put("previousAppointmentDateTime", previousDateTime);
        details.put("appointmentDateTime", nextDateTime);
        details.put("doctorDisplayName", safeDoctorName(payload.doctorDisplayName()));
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildCancelledDetailsJson(
            AppointmentCancelledEvent event,
            AppointmentCancelledEventPayload payload,
            String appointmentDateTime
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("appointmentId", payload.appointmentId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("doctorUserId", payload.doctorUserId());
        details.put("appointmentTimezone", payload.appointmentTimezone());
        details.put("appointmentVersion", payload.appointmentVersion());
        details.put("appointmentDateTime", appointmentDateTime);
        details.put("doctorDisplayName", safeDoctorName(payload.doctorDisplayName()));
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildReminderDetailsJson(
            AppointmentReminderDueEvent event,
            AppointmentReminderDueEventPayload payload,
            String appointmentDateTime
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("appointmentId", payload.appointmentId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("doctorUserId", payload.doctorUserId());
        details.put("appointmentTimezone", payload.appointmentTimezone());
        details.put("appointmentVersion", payload.appointmentVersion());
        details.put("appointmentDateTime", appointmentDateTime);
        details.put("reminderWindow", payload.reminderWindow());
        details.put("doctorDisplayName", safeDoctorName(payload.doctorDisplayName()));
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildPrescriptionReadyDetailsJson(
            PrescriptionReadyEvent event,
            PrescriptionReadyEventPayload payload,
            String followUpDate
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("prescriptionId", payload.prescriptionId());
        details.put("consultationId", payload.consultationId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("doctorUserId", payload.doctorUserId());
        details.put("doctorDisplayName", safeDoctorName(payload.doctorDisplayName()));
        details.put("prescriptionNumber", payload.prescriptionNumber());
        details.put("timezone", payload.timezone());
        details.put("finalizedAt", payload.finalizedAt());
        details.put("versionNumber", payload.versionNumber());
        if (hasText(followUpDate)) {
            details.put("followUpDate", followUpDate);
        }
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildLabReportReadyDetailsJson(
            LabReportPublishedEvent event,
            LabReportPublishedEventPayload payload
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("labOrderId", payload.labOrderId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("consultationId", payload.consultationId());
        details.put("orderNumber", payload.orderNumber());
        details.put("timezone", payload.timezone());
        details.put("publishedAt", payload.publishedAt());
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildBillGeneratedDetailsJson(BillGeneratedEvent event, BillGeneratedEventPayload payload, String amountText) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("billId", payload.billId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("billNumber", payload.billNumber());
        details.put("amount", amountText);
        details.put("currency", payload.currency());
        details.put("issuedAt", payload.issuedAt());
        if (payload.dueAt() != null) {
            details.put("dueAt", payload.dueAt());
        }
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        if (hasText(payload.timezone())) {
            details.put("timezone", payload.timezone());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildPaymentReceivedDetailsJson(PaymentReceivedEvent event, PaymentReceivedEventPayload payload, String amountText) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("paymentId", payload.paymentId());
        details.put("billId", payload.billId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("billNumber", payload.billNumber());
        details.put("receiptNumber", payload.receiptNumber());
        details.put("amount", amountText);
        details.put("currency", payload.currency());
        details.put("paymentMethodDisplayName", payload.paymentMethodDisplayName());
        details.put("receivedAt", payload.receivedAt());
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        if (hasText(payload.timezone())) {
            details.put("timezone", payload.timezone());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildPaymentReminderDetailsJson(PaymentReminderEvent event, PaymentReminderEventPayload payload, String reminderMessage) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("billId", payload.billId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("billNumber", payload.billNumber());
        details.put("outstandingAmount", payload.outstandingAmount());
        details.put("currency", payload.currency());
        details.put("billStatus", payload.billStatus());
        details.put("billUpdatedAt", payload.billUpdatedAt());
        details.put("reminderWindow", payload.reminderWindow());
        details.put("timezone", payload.timezone());
        details.put("subject", "Payment reminder");
        details.put("message", reminderMessage);
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildFollowUpDueDetailsJson(FollowUpDueEvent event, FollowUpDueEventPayload payload, String followUpDate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("consultationId", payload.consultationId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("doctorUserId", payload.doctorUserId());
        details.put("doctorDisplayName", safeDoctorName(payload.doctorDisplayName()));
        details.put("timezone", payload.timezone());
        details.put("reminderWindow", payload.reminderWindow());
        if (hasText(followUpDate)) {
            details.put("followUpDate", followUpDate);
        }
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String buildVaccinationDueDetailsJson(VaccinationDueEvent event, VaccinationDueEventPayload payload, String dueDate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("correlationId", event.correlationId());
        details.put("causationId", event.causationId());
        details.put("vaccinationScheduleEntryId", payload.vaccinationScheduleEntryId());
        details.put("patientId", payload.patientId());
        details.put("recipientType", "PATIENT");
        details.put("recipientId", payload.patientId());
        details.put("vaccineDisplayName", payload.vaccineDisplayName());
        details.put("doseDisplayName", payload.doseDisplayName());
        details.put("timezone", payload.timezone());
        details.put("reminderWindow", payload.reminderWindow());
        if (hasText(dueDate)) {
            details.put("dueDate", dueDate);
        }
        if (hasText(payload.clinicDisplayName())) {
            details.put("clinicDisplayName", payload.clinicDisplayName());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"eventId\":\"" + event.eventId() + "\"}";
        }
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "₹0.00";
        }
        return "₹" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
        return DATE_TIME_FORMATTER.format(LocalDateTime.of(date, time).atZone(zone));
    }

    private String patientTargetLabel(PatientRecord patient) {
        if (patient == null) {
            return "Patient record unavailable";
        }
        String name = patient.fullName();
        if (StringUtils.hasText(patient.patientNumber())) {
            return name + " • " + patient.patientNumber();
        }
        return StringUtils.hasText(name) ? name : "Patient";
    }

    private String safePatientDisplayName(PatientRecord patient) {
        if (patient == null) {
            return "Patient";
        }
        String name = patient.fullName();
        return hasText(name) ? name : patientTargetLabel(patient);
    }

    private String safeDoctorName(String doctorDisplayName) {
        if (!hasText(doctorDisplayName)) {
            return "your doctor";
        }
        String trimmed = doctorDisplayName.trim();
        String normalized = trimmed.replaceFirst("(?i)^dr\\.?\\s+", "");
        if (normalized.isBlank()) {
            return "your doctor";
        }
        return "Dr. " + normalized;
    }

    private String stalePaymentReminderReason(PaymentReminderState currentState, PaymentReminderEventPayload payload) {
        if (currentState == null || !currentState.found()) {
            return "Reminder no longer applicable";
        }
        if (!currentState.reminderEligible()
                || currentState.outstandingAmount() == null
                || currentState.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Bill already paid";
        }
        if (payload.outstandingAmount() != null
                && currentState.outstandingAmount() != null
                && currentState.outstandingAmount().compareTo(payload.outstandingAmount()) != 0) {
            return "Reminder no longer applicable";
        }
        if (payload.billStatus() != null
                && currentState.billStatus() != null
                && !payload.billStatus().equalsIgnoreCase(currentState.billStatus())) {
            return "Reminder no longer applicable";
        }
        if (payload.billUpdatedAt() != null
                && currentState.updatedAt() != null
                && !payload.billUpdatedAt().toInstant().equals(currentState.updatedAt().toInstant())) {
            return "Reminder no longer applicable";
        }
        return "Reminder no longer applicable";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeEmail(String email) {
        return hasText(email) ? email.trim() : null;
    }

    private String normalizeMobile(String mobile) {
        if (!hasText(mobile)) {
            return null;
        }
        return mobile.trim().replaceAll("[\\s-]", "");
    }

    private boolean isValidEmail(String value) {
        return hasText(value) && value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private boolean isValidMobile(String value) {
        return hasText(value) && value.matches("[0-9]{10}");
    }

    private String augmentDetailsJson(String detailsJson, String channel, String deliveryRecipient, String deliveryDisplayName) {
        try {
            Map<String, Object> details = detailsJson == null || detailsJson.isBlank()
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(detailsJson, LinkedHashMap.class);
            if (channel != null) {
                details.put("deliveryChannel", channel);
            }
            if (deliveryRecipient != null) {
                details.put("deliveryRecipient", deliveryRecipient);
            }
            if (deliveryDisplayName != null) {
                details.put("recipientDisplayName", deliveryDisplayName);
            }
            return objectMapper.writeValueAsString(details);
        } catch (Exception ex) {
            return detailsJson;
        }
    }

    private String buildDedupeKey(
            String eventType,
            UUID tenantId,
            UUID eventId,
            UUID patientId,
            String historyChannel,
            String historyRecipient,
            String sourceType,
            UUID sourceId
    ) {
        return eventType + ":"
                + tenantId + ":"
                + eventId + ":"
                + patientId + ":"
                + historyChannel + ":"
                + historyRecipient + ":"
                + sourceType + ":"
                + sourceId;
    }

    private String buildPaymentReminderDedupeKey(
            PaymentReminderEvent event,
            PaymentReminderEventPayload payload,
            String historyChannel,
            String historyRecipient
    ) {
        return buildDedupeKey(
                "PAYMENT_REMINDER",
                event.tenantId(),
                event.eventId(),
                payload.patientId(),
                historyChannel,
                historyRecipient,
                "BILL",
                payload.billId()
        );
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
