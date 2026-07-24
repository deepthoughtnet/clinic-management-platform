package com.deepthoughtnet.clinic.notificationcenter.service;

import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentCancelledEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentReminderDueEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentRescheduledEvent;
import com.deepthoughtnet.clinic.billing.events.BillGeneratedEvent;
import com.deepthoughtnet.clinic.billing.events.PaymentReceivedEvent;
import com.deepthoughtnet.clinic.billing.events.PaymentReminderEvent;
import com.deepthoughtnet.clinic.consultation.events.FollowUpDueEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterAudienceResolver.ResolvedAudienceRecipient;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationAudience;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationCategory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationPriority;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationAction;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import com.deepthoughtnet.clinic.prescription.events.PrescriptionReadyEvent;
import com.deepthoughtnet.clinic.vaccination.events.VaccinationDueEvent;
import com.deepthoughtnet.clinic.api.lab.events.LabReportPublishedEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationCenterRequestFactory {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);

    public StaffNotificationRequest fromAppointmentBooked(AppointmentBookedEvent event) {
        var payload = event.payload();
        String displayDateTime = formatDateTime(payload.appointmentDate(), payload.appointmentTime(), payload.appointmentTimezone());
        return baseRequest(
                event,
                NotificationCategory.APPOINTMENT,
                NotificationPriority.NORMAL,
                "Appointment booked",
                "Appointment booked with " + doctorName(payload.doctorDisplayName()) + " on " + displayDateTime,
                businessReference("Appointment", displayDateTime),
                StaffNotificationAction.of("Open day board", "/appointments/day-board", id(payload.appointmentId())),
                audiences(event, List.of(
                        NotificationAudience.permission("appointment.manage"),
                        NotificationAudience.permission("consultation.read"),
                        NotificationAudience.role("DOCTOR"),
                        NotificationAudience.role("RECEPTIONIST"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().appointmentId(),
                event.payload().patientId(),
                payload.appointmentTimezone(),
                displayDateTime
        );
    }

    public StaffNotificationRequest fromAppointmentRescheduled(AppointmentRescheduledEvent event) {
        var payload = event.payload();
        String previousDateTime = formatDateTime(payload.previousAppointmentDate(), payload.previousAppointmentTime(), payload.appointmentTimezone());
        String newDateTime = formatDateTime(payload.appointmentDate(), payload.appointmentTime(), payload.appointmentTimezone());
        return baseRequest(
                event,
                NotificationCategory.APPOINTMENT,
                NotificationPriority.NORMAL,
                "Appointment rescheduled",
                "Appointment with " + doctorName(payload.doctorDisplayName()) + " was rescheduled from " + previousDateTime + " to " + newDateTime,
                businessReference("Appointment", newDateTime),
                StaffNotificationAction.of("Open day board", "/appointments/day-board", id(payload.appointmentId())),
                audiences(event, List.of(
                        NotificationAudience.permission("appointment.manage"),
                        NotificationAudience.permission("consultation.read"),
                        NotificationAudience.role("DOCTOR"),
                        NotificationAudience.role("RECEPTIONIST"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().appointmentId(),
                event.payload().patientId(),
                payload.appointmentTimezone(),
                newDateTime
        );
    }

    public StaffNotificationRequest fromAppointmentCancelled(AppointmentCancelledEvent event) {
        var payload = event.payload();
        String displayDateTime = formatDateTime(payload.appointmentDate(), payload.appointmentTime(), payload.appointmentTimezone());
        return baseRequest(
                event,
                NotificationCategory.APPOINTMENT,
                NotificationPriority.NORMAL,
                "Appointment cancelled",
                "Appointment with " + doctorName(payload.doctorDisplayName()) + " scheduled for " + displayDateTime + " was cancelled",
                businessReference("Appointment", displayDateTime),
                StaffNotificationAction.of("Open day board", "/appointments/day-board", id(payload.appointmentId())),
                audiences(event, List.of(
                        NotificationAudience.permission("appointment.manage"),
                        NotificationAudience.permission("consultation.read"),
                        NotificationAudience.role("DOCTOR"),
                        NotificationAudience.role("RECEPTIONIST"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().appointmentId(),
                event.payload().patientId(),
                payload.appointmentTimezone(),
                displayDateTime
        );
    }

    public StaffNotificationRequest fromAppointmentReminderDue(AppointmentReminderDueEvent event) {
        var payload = event.payload();
        String displayDateTime = formatDateTime(payload.appointmentDate(), payload.appointmentTime(), payload.appointmentTimezone());
        return baseRequest(
                event,
                NotificationCategory.APPOINTMENT,
                NotificationPriority.NORMAL,
                "Appointment reminder",
                "Reminder: " + doctorName(payload.doctorDisplayName()) + " is scheduled for " + displayDateTime,
                businessReference("Appointment", displayDateTime),
                StaffNotificationAction.of("Open day board", "/appointments/day-board", id(payload.appointmentId())),
                audiences(event, List.of(
                        NotificationAudience.permission("appointment.manage"),
                        NotificationAudience.permission("consultation.read"),
                        NotificationAudience.role("DOCTOR"),
                        NotificationAudience.role("RECEPTIONIST"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().appointmentId(),
                event.payload().patientId(),
                payload.appointmentTimezone(),
                displayDateTime
        );
    }

    public StaffNotificationRequest fromBillGenerated(BillGeneratedEvent event) {
        var payload = event.payload();
        String amount = formatMoney(payload.amount(), payload.currency());
        String dueAt = payload.dueAt() == null ? null : payload.dueAt().toString();
        String preview = dueAt == null
                ? "Bill " + payload.billNumber() + " for " + amount + " is ready"
                : "Bill " + payload.billNumber() + " for " + amount + " is ready. Due by " + dueAt;
        return baseRequest(
                event,
                NotificationCategory.BILLING,
                NotificationPriority.NORMAL,
                "Bill generated",
                preview,
                payload.billNumber(),
                StaffNotificationAction.of("Open billing", "/billing", id(payload.billId())),
                audiences(event, List.of(
                        NotificationAudience.permission("billing.read"),
                        NotificationAudience.permission("billing.create"),
                        NotificationAudience.permission("billing.receipt"),
                        NotificationAudience.permission("payment.collect"),
                        NotificationAudience.role("BILLING_USER"),
                        NotificationAudience.role("RECEPTIONIST"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().billId(),
                event.payload().patientId(),
                payload.timezone(),
                dueAt
        );
    }

    public StaffNotificationRequest fromPaymentReceived(PaymentReceivedEvent event) {
        var payload = event.payload();
        String amount = formatMoney(payload.amount(), payload.currency());
        String preview = "Payment of " + amount + " was received";
        if (payload.receiptNumber() != null && !payload.receiptNumber().isBlank()) {
            preview += " for receipt " + payload.receiptNumber();
        }
        return baseRequest(
                event,
                NotificationCategory.BILLING,
                NotificationPriority.NORMAL,
                "Payment received",
                preview,
                payload.receiptNumber(),
                StaffNotificationAction.of("Open payments", "/finance/payments", id(payload.paymentId())),
                audiences(event, List.of(
                        NotificationAudience.permission("billing.read"),
                        NotificationAudience.permission("billing.receipt"),
                        NotificationAudience.permission("payment.collect"),
                        NotificationAudience.role("BILLING_USER"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().paymentId(),
                event.payload().patientId(),
                payload.timezone(),
                payload.receivedAt() == null ? null : payload.receivedAt().toString()
        );
    }

    public StaffNotificationRequest fromPaymentReminder(PaymentReminderEvent event) {
        var payload = event.payload();
        String amount = formatMoney(payload.outstandingAmount(), payload.currency());
        String preview = "Reminder: " + amount + " remains outstanding on bill " + payload.billNumber();
        if (payload.billUpdatedAt() != null) {
            preview += " (updated " + payload.billUpdatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) + ")";
        }
        return baseRequest(
                event,
                NotificationCategory.BILLING,
                NotificationPriority.NORMAL,
                "Payment reminder",
                preview,
                payload.billNumber(),
                StaffNotificationAction.of("Open billing", "/billing", id(payload.billId())),
                audiences(event, List.of(
                        NotificationAudience.permission("billing.read"),
                        NotificationAudience.permission("billing.receipt"),
                        NotificationAudience.permission("payment.collect"),
                        NotificationAudience.role("BILLING_USER"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().billId(),
                event.payload().patientId(),
                payload.timezone(),
                payload.billUpdatedAt() == null ? null : payload.billUpdatedAt().toString()
        );
    }

    public StaffNotificationRequest fromPrescriptionReady(PrescriptionReadyEvent event) {
        var payload = event.payload();
        String followUp = payload.followUpDate() == null ? null : payload.followUpDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        String preview = followUp == null
                ? "Prescription from " + doctorName(payload.doctorDisplayName()) + " is ready"
                : "Prescription from " + doctorName(payload.doctorDisplayName()) + " for " + followUp + " is ready";
        return baseRequest(
                event,
                NotificationCategory.PHARMACY,
                NotificationPriority.NORMAL,
                "Prescription ready",
                preview,
                payload.prescriptionNumber(),
                StaffNotificationAction.of("Open prescriptions", "/prescriptions", id(payload.prescriptionId())),
                audiences(event, List.of(
                        NotificationAudience.permission("prescription.read"),
                        NotificationAudience.role("DOCTOR"),
                        NotificationAudience.role("PHARMACIST"),
                        NotificationAudience.role("PHARMACY"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().prescriptionId(),
                event.payload().patientId(),
                payload.timezone(),
                followUp
        );
    }

    public StaffNotificationRequest fromLabReportPublished(LabReportPublishedEvent event) {
        var payload = event.payload();
        return baseRequest(
                event,
                NotificationCategory.LAB,
                NotificationPriority.NORMAL,
                "Lab report ready",
                "Lab report for order " + payload.orderNumber() + " is ready",
                payload.orderNumber(),
                StaffNotificationAction.of("Open laboratory", "/lab", id(payload.labOrderId())),
                audiences(event, List.of(
                        NotificationAudience.permission("lab.order.read"),
                        NotificationAudience.permission("lab.order.review"),
                        NotificationAudience.role("LAB_TECHNICIAN"),
                        NotificationAudience.role("LAB_ASSISTANT"),
                        NotificationAudience.role("LAB_APPROVER"),
                        NotificationAudience.role("LAB_FRONT_DESK"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().labOrderId(),
                event.payload().patientId(),
                payload.timezone(),
                payload.publishedAt() == null ? null : payload.publishedAt().toString()
        );
    }

    public StaffNotificationRequest fromFollowUpDue(FollowUpDueEvent event) {
        var payload = event.payload();
        String dateTime = formatDateTime(payload.followUpDate(), LocalTime.NOON, payload.timezone());
        String preview = "Follow-up due " + dateTime;
        if (payload.doctorDisplayName() != null && !payload.doctorDisplayName().isBlank()) {
            preview = "Follow-up with " + doctorName(payload.doctorDisplayName()) + " is due on " + dateTime;
        }
        return baseRequest(
                event,
                NotificationCategory.CLINICAL,
                NotificationPriority.NORMAL,
                "Follow-up due",
                preview,
                businessReference("Follow-up", dateTime),
                StaffNotificationAction.of("Open consultations", "/consultations", id(payload.consultationId())),
                audiences(event, List.of(
                        NotificationAudience.permission("consultation.read"),
                        NotificationAudience.permission("appointment.manage"),
                        NotificationAudience.role("DOCTOR"),
                        NotificationAudience.role("RECEPTIONIST"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().consultationId(),
                event.payload().patientId(),
                payload.timezone(),
                dateTime
        );
    }

    public StaffNotificationRequest fromVaccinationDue(VaccinationDueEvent event) {
        var payload = event.payload();
        String dateText = payload.dueDate() == null ? null : payload.dueDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        String vaccine = payload.doseDisplayName() == null || payload.doseDisplayName().isBlank()
                ? payload.vaccineDisplayName()
                : payload.vaccineDisplayName() + " - " + payload.doseDisplayName();
        String preview = dateText == null
                ? vaccine + " vaccination is due"
                : vaccine + " vaccination is due on " + dateText;
        return baseRequest(
                event,
                NotificationCategory.CLINICAL,
                NotificationPriority.NORMAL,
                "Vaccination due",
                preview,
                vaccine,
                StaffNotificationAction.of("Open vaccinations", "/vaccinations", id(payload.vaccinationScheduleEntryId())),
                audiences(event, List.of(
                        NotificationAudience.permission("vaccination.manage"),
                        NotificationAudience.role("RECEPTIONIST"),
                        NotificationAudience.role("DOCTOR"),
                        NotificationAudience.tenantAdmin(),
                        NotificationAudience.platformAdmin()
                )),
                event.payload().vaccinationScheduleEntryId(),
                event.payload().patientId(),
                payload.timezone(),
                dateText
        );
    }

    private StaffNotificationRequest baseRequest(
            com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent event,
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String preview,
            String businessReference,
            StaffNotificationAction action,
            List<NotificationAudience> audiences,
            UUID aggregateId,
            UUID patientId,
            String timezone,
            String reference
    ) {
        String messagePreview = preview;
        if (patientId != null && reference != null && !reference.isBlank()) {
            messagePreview = preview;
        }
        return new StaffNotificationRequest(
                event.tenantId(),
                event.eventId(),
                event.eventType(),
                event.sourceModule(),
                event.aggregateType(),
                aggregateId,
                title,
                messagePreview,
                category,
                priority,
                businessReference,
                action,
                audiences,
                event.occurredAt(),
                event.correlationId(),
                event.causationId()
        );
    }

    private List<NotificationAudience> audiences(
            com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent event,
            List<NotificationAudience> audiences
    ) {
        ArrayList<NotificationAudience> resolved = new ArrayList<>();
        if (event.actorId() != null) {
            resolved.add(NotificationAudience.user(event.actorId().toString()));
        }
        resolved.addAll(audiences);
        return resolved;
    }

    private String formatDateTime(LocalDate date, LocalTime time, String timezone) {
        if (date == null) {
            return "";
        }
        LocalDateTime localDateTime = LocalDateTime.of(date, time == null ? LocalTime.MIDNIGHT : time);
        try {
            ZoneId zoneId = timezone == null || timezone.isBlank() ? ZoneId.of("UTC") : ZoneId.of(timezone);
            return DISPLAY_DATE_TIME.format(localDateTime.atZone(zoneId));
        } catch (RuntimeException ex) {
            return DISPLAY_DATE_TIME.format(localDateTime.atZone(ZoneId.of("UTC")));
        }
    }

    private String doctorName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "Doctor";
        }
        String normalized = displayName.trim();
        normalized = normalized.replaceAll("(?i)^dr\\.?\\s*", "");
        return "Dr. " + normalized;
    }

    private String businessReference(String prefix, String reference) {
        if (reference == null || reference.isBlank()) {
            return prefix;
        }
        return prefix + " " + reference;
    }

    private String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return "";
        }
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ENGLISH);
        formatter.setMinimumFractionDigits(amount.scale() > 0 ? amount.scale() : 0);
        formatter.setMaximumFractionDigits(Math.max(amount.scale(), 0));
        BigDecimal normalized = amount.setScale(Math.max(amount.scale(), 0), RoundingMode.HALF_UP);
        String symbol = currency == null || currency.isBlank() ? "" : currency.trim() + " ";
        return symbol + formatter.format(normalized);
    }

    private String id(UUID value) {
        return value == null ? null : value.toString();
    }
}
