package com.deepthoughtnet.clinic.api.notifications;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillPdf;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionPdf;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.notify.NotificationAttachment;
import com.deepthoughtnet.clinic.notify.NotificationDeliveryException;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NotificationActionService {
    private static final Logger log = LoggerFactory.getLogger(NotificationActionService.class);

    private final NotificationHistoryService notificationHistoryService;
    private final PrescriptionService prescriptionService;
    private final BillingService billingService;
    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final VaccinationService vaccinationService;
    private final PlatformTenantManagementService tenantManagementService;
    private final PatientRepository patientRepository;
    private final NotificationProvider notificationProvider;
    private final PrescriptionTemplateService prescriptionTemplateService;

    public NotificationActionService(
            NotificationHistoryService notificationHistoryService,
            PrescriptionService prescriptionService,
            BillingService billingService,
            AppointmentService appointmentService,
            ConsultationService consultationService,
            VaccinationService vaccinationService,
            PlatformTenantManagementService tenantManagementService,
            PatientRepository patientRepository,
            NotificationProvider notificationProvider,
            PrescriptionTemplateService prescriptionTemplateService
    ) {
        this.notificationHistoryService = notificationHistoryService;
        this.prescriptionService = prescriptionService;
        this.billingService = billingService;
        this.appointmentService = appointmentService;
        this.consultationService = consultationService;
        this.vaccinationService = vaccinationService;
        this.tenantManagementService = tenantManagementService;
        this.patientRepository = patientRepository;
        this.notificationProvider = notificationProvider;
        this.prescriptionTemplateService = prescriptionTemplateService;
    }

    public NotificationHistoryRecord sendPrescription(UUID tenantId, UUID prescriptionId, String channel, UUID actorAppUserId) {
        PrescriptionRecord prescription = prescriptionService.findById(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        PatientEntity patient = patient(tenantId, prescription.patientId());
        String normalizedChannel = normalizeChannel(channel);
        String recipient = resolveRecipient(patient, normalizedChannel);
        String subject = "Prescription " + prescription.prescriptionNumber();
        String message = buildPrescriptionMessage(prescription, patient);
        NotificationHistoryRecord notification = notificationHistoryService.queue(
                tenantId,
                patient.getId(),
                "PRESCRIPTION_SENT",
                normalizedChannel,
                recipient,
                subject,
                message,
                "PRESCRIPTION",
                prescription.id(),
                actorAppUserId
        );
        if ("email".equals(normalizedChannel)) {
            PrescriptionPdf pdf = prescriptionService.generatePdf(tenantId, prescriptionId, actorAppUserId, prescriptionTemplateService.toPdfConfig(prescriptionTemplateService.getActive(tenantId)));
            notificationProvider.send(new NotificationMessage(
                    tenantId,
                    "EMAIL",
                    recipient,
                    subject,
                    message,
                    "{\"sourceType\":\"PRESCRIPTION\",\"sourceId\":\"" + prescription.id() + "\"}",
                    null,
                    List.of(new NotificationAttachment(pdf.filename(), "application/pdf", pdf.content()))
            ));
            notificationHistoryService.markSent(tenantId, notification.id());
        }
        prescriptionService.markSent(tenantId, prescriptionId, actorAppUserId);
        return notification;
    }

    public NotificationHistoryRecord sendReceipt(UUID tenantId, UUID receiptId, String channel, UUID actorAppUserId) {
        ReceiptRecord receipt = billingService.findReceipt(tenantId, receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));
        BillRecord bill = billingService.findById(tenantId, receipt.billId())
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        PatientEntity patient = patient(tenantId, bill.patientId());
        String normalizedChannel = normalizeChannel(channel);
        String recipient = resolveRecipient(patient, normalizedChannel);
        String subject = "Receipt " + receipt.receiptNumber();
        String message = "Receipt " + receipt.receiptNumber() + " for bill " + bill.billNumber() + " amount " + receipt.amount();
        return notificationHistoryService.queue(
                tenantId,
                patient.getId(),
                "RECEIPT_SENT",
                normalizedChannel,
                recipient,
                subject,
                message,
                "RECEIPT",
                receipt.id(),
                actorAppUserId
        );
    }

    public NotificationHistoryRecord sendAppointmentBooked(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        return sendAppointmentEvent(tenantId, appointmentId, "APPOINTMENT_BOOKED", "Appointment booked", "Your appointment has been booked successfully.", actorAppUserId);
    }

    public NotificationHistoryRecord sendAppointmentRescheduled(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        return sendAppointmentEvent(tenantId, appointmentId, "APPOINTMENT_RESCHEDULED", "Appointment rescheduled", "Your appointment has been rescheduled successfully.", actorAppUserId);
    }

    public NotificationHistoryRecord sendAppointmentCancelled(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        return sendAppointmentEvent(tenantId, appointmentId, "APPOINTMENT_CANCELLED", "Appointment cancelled", "Your appointment has been cancelled successfully.", actorAppUserId);
    }

    public NotificationHistoryRecord sendAppointmentNoShow(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        return sendAppointmentEvent(tenantId, appointmentId, "APPOINTMENT_NO_SHOW", "Appointment marked no-show", "Your appointment was marked as a no-show. Please reschedule or contact the clinic.", actorAppUserId);
    }

    public NotificationHistoryRecord sendPrescriptionReady(UUID tenantId, UUID prescriptionId, UUID actorAppUserId) {
        PrescriptionRecord prescription = prescriptionService.findById(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        PatientEntity patient = patient(tenantId, prescription.patientId());
        String subject = "Prescription ready " + prescription.prescriptionNumber();
        String message = "Your prescription is ready. You can view it in the patient portal.";
        return queuePatientNotification(tenantId, patient, "PRESCRIPTION_READY", subject, message, "PRESCRIPTION", prescription.id(), actorAppUserId, true, null);
    }

    public NotificationHistoryRecord sendBillGenerated(UUID tenantId, UUID billId, UUID actorAppUserId) {
        BillRecord bill = billingService.findById(tenantId, billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        PatientEntity patient = patient(tenantId, bill.patientId());
        return queuePatientNotification(
                tenantId,
                patient,
                "BILL_GENERATED",
                "Bill generated " + bill.billNumber(),
                "Your bill is ready. You can view it in the patient portal.",
                "BILL",
                bill.id(),
                actorAppUserId,
                true,
                null
        );
    }

    public NotificationHistoryRecord sendBillPaid(UUID tenantId, UUID billId, UUID actorAppUserId) {
        BillRecord bill = billingService.findById(tenantId, billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        PatientEntity patient = patient(tenantId, bill.patientId());
        return queuePatientNotification(
                tenantId,
                patient,
                "BILL_PAID",
                "Bill paid " + bill.billNumber(),
                "Your bill payment has been recorded.",
                "BILL",
                bill.id(),
                actorAppUserId,
                true,
                null
        );
    }

    public NotificationHistoryRecord sendReceiptReady(UUID tenantId, UUID receiptId, UUID actorAppUserId) {
        ReceiptRecord receipt = billingService.findReceipt(tenantId, receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));
        BillRecord bill = billingService.findById(tenantId, receipt.billId())
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        PatientEntity patient = patient(tenantId, bill.patientId());
        return queuePatientNotification(
                tenantId,
                patient,
                "RECEIPT_SENT",
                "Receipt ready " + receipt.receiptNumber(),
                "Your receipt is available in the patient portal.",
                "RECEIPT",
                receipt.id(),
                actorAppUserId,
                true,
                null
        );
    }

    public NotificationHistoryRecord sendRefundProcessed(UUID tenantId, UUID billId, UUID actorAppUserId) {
        BillRecord bill = billingService.findById(tenantId, billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        PatientEntity patient = patient(tenantId, bill.patientId());
        return queuePatientNotification(
                tenantId,
                patient,
                "REFUND_PROCESSED",
                "Refund processed " + bill.billNumber(),
                "Your refund has been processed. Please review the updated bill in the patient portal.",
                "BILL",
                bill.id(),
                actorAppUserId,
                true,
                null
        );
    }

    public NotificationHistoryRecord sendFollowUpDue(UUID tenantId, UUID consultationId, UUID patientId, String patientName, String doctorName, LocalDate followUpDate, UUID actorAppUserId) {
        PatientEntity patient = patient(tenantId, patientId);
        String subject = "Follow-up due";
        String message = "Your follow-up is due. Please book a visit or contact the clinic.";
        return queuePatientNotification(
                tenantId,
                patient,
                "FOLLOW_UP_DUE",
                subject,
                message,
                "CONSULTATION",
                consultationId,
                actorAppUserId,
                true,
                null
        );
    }

    public InvoiceEmailResult sendInvoiceEmail(UUID tenantId, UUID billId, UUID actorAppUserId) {
        BillRecord bill = billingService.findById(tenantId, billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        PatientEntity patient = patient(tenantId, bill.patientId());
        if (!StringUtils.hasText(patient.getEmail())) {
            throw new IllegalArgumentException("Patient email is required to send invoice");
        }
        String recipient = patient.getEmail().trim();
        String subject = "Invoice " + bill.billNumber();
        String message = "Invoice " + bill.billNumber() + " amount " + bill.totalAmount() + " for " + (bill.patientName() == null ? "patient" : bill.patientName());
        NotificationHistoryRecord queued = notificationHistoryService.queue(
                tenantId,
                patient.getId(),
                "INVOICE_SENT",
                "email",
                recipient,
                subject,
                message,
                "BILL",
                bill.id(),
                actorAppUserId
        );
        try {
            BillPdf pdf = billingService.generateBillPdf(tenantId, billId, actorAppUserId);
            notificationProvider.send(new NotificationMessage(
                    tenantId,
                    "EMAIL",
                    recipient,
                    subject,
                    message,
                    "{\"sourceType\":\"BILL\",\"sourceId\":\"" + bill.id() + "\"}",
                    null,
                    List.of(new NotificationAttachment(pdf.filename(), "application/pdf", pdf.content()))
            ));
            notificationHistoryService.markSent(tenantId, queued.id());
            billingService.markInvoiceEmailed(tenantId, billId, actorAppUserId);
            return new InvoiceEmailResult(true, "Invoice email sent", recipient, OffsetDateTime.now());
        } catch (NotificationDeliveryException ex) {
            throw new IllegalArgumentException("Invoice email could not be sent. Please check email provider configuration.");
        }
    }

    private NotificationHistoryRecord sendAppointmentEvent(
            UUID tenantId,
            UUID appointmentId,
            String eventType,
            String subject,
            String message,
            UUID actorAppUserId
    ) {
        var appointment = appointmentService.findById(tenantId, appointmentId);
        PatientEntity patient = patient(tenantId, appointment.patientId());
        return queuePatientNotification(
                tenantId,
                patient,
                eventType,
                subject + " " + appointment.appointmentDate(),
                message,
                "APPOINTMENT",
                appointment.id(),
                actorAppUserId,
                true,
                null
        );
    }

    private NotificationHistoryRecord queuePatientNotification(
            UUID tenantId,
            PatientEntity patient,
            String eventType,
            String subject,
            String message,
            String sourceType,
            UUID sourceId,
            UUID actorAppUserId,
            boolean sendEmail,
            NotificationAttachment attachment
    ) {
        String recipient = patientTargetLabel(patient);
        NotificationHistoryRecord notification = notificationHistoryService.queueDetailed(
                tenantId,
                patient.getId(),
                eventType,
                "in_app",
                recipient,
                subject,
                message,
                sourceType,
                sourceId,
                actorAppUserId
        ).notification();
        if (sendEmail && StringUtils.hasText(patient.getEmail())) {
            try {
                List<NotificationAttachment> attachments = attachment == null ? List.of() : List.of(attachment);
                notificationProvider.send(new NotificationMessage(
                        tenantId,
                        "EMAIL",
                        patient.getEmail().trim(),
                        subject,
                        message,
                        "{\"sourceType\":\"" + sourceType + "\",\"sourceId\":\"" + sourceId + "\"}",
                        null,
                        attachments
                ));
            } catch (RuntimeException ex) {
                log.warn("Unable to send notification email for {}", eventType, ex);
            }
        }
        return notification;
    }

    public ReminderQueueSummary queueAppointmentReminders(UUID tenantId, LocalDate appointmentDate, UUID actorAppUserId) {
        LocalDate windowDate = appointmentDate;
        OffsetDateTime now = OffsetDateTime.now();
        return appointmentService.search(tenantId, new com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria(null, null, windowDate, null, null))
                .stream()
                .filter(appointment -> appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.BOOKED)
                .filter(appointment -> appointment.appointmentTime() != null)
                .filter(appointment -> shouldSendReminder(now, appointment.appointmentDate(), appointment.appointmentTime()))
                .map(appointment -> queueAppointmentReminder(tenantId, appointment.id(), actorAppUserId))
                .reduce(ReminderQueueSummary.empty(), ReminderQueueSummary::add);
    }

    public ReminderQueueSummary queueFollowUpReminders(UUID tenantId, LocalDate dueDate, UUID actorAppUserId) {
        return consultationService.list(tenantId).stream()
                .filter(record -> record.followUpDate() != null && !record.followUpDate().isAfter(dueDate))
                .filter(record -> record.status() == ConsultationStatus.COMPLETED || record.status() == ConsultationStatus.DRAFT)
                .map(record -> queueFollowUpReminder(tenantId, record, actorAppUserId))
                .reduce(ReminderQueueSummary.empty(), ReminderQueueSummary::add);
    }

    public ReminderQueueSummary queueVaccinationReminders(UUID tenantId, UUID actorAppUserId) {
        return vaccinationService.listDue(tenantId).stream()
                .map(vaccination -> {
                    PatientEntity patient = patient(tenantId, vaccination.patientId());
                    String channel = normalizeChannel("email");
                    String recipient = resolveRecipient(patient, channel);
                    return queueReminder(() -> notificationHistoryService.queueDetailed(
                            tenantId,
                            patient.getId(),
                            "VACCINATION_REMINDER",
                            channel,
                            recipient,
                            "Vaccination reminder",
                            "Vaccination due for " + vaccination.vaccineName(),
                            "PATIENT_VACCINATION",
                            vaccination.id(),
                            actorAppUserId
                    ));
                }).reduce(ReminderQueueSummary.empty(), ReminderQueueSummary::add);
    }

    public ReminderQueueSummary queuePaymentReminders(UUID tenantId, UUID actorAppUserId) {
        return billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null, null, null)).stream()
                .filter(bill -> bill.dueAmount() != null && bill.dueAmount().compareTo(java.math.BigDecimal.ZERO) > 0)
                .filter(bill -> bill.status() == com.deepthoughtnet.clinic.billing.service.model.BillStatus.UNPAID
                        || bill.status() == com.deepthoughtnet.clinic.billing.service.model.BillStatus.PARTIALLY_PAID
                        || bill.status() == com.deepthoughtnet.clinic.billing.service.model.BillStatus.ISSUED)
                .map(bill -> queuePaymentReminder(tenantId, bill, actorAppUserId))
                .reduce(ReminderQueueSummary.empty(), ReminderQueueSummary::add);
    }

    public ReminderQueueSummary queueMissedAppointmentReminders(UUID tenantId, LocalDate missedBeforeDate, UUID actorAppUserId) {
        LocalDate cutoff = missedBeforeDate == null ? LocalDate.now() : missedBeforeDate;
        return appointmentService.search(tenantId, new com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria(null, null, null, null, null))
                .stream()
                .filter(appointment -> appointment.appointmentDate() != null && appointment.appointmentDate().isBefore(cutoff))
                .filter(appointment -> appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.BOOKED
                        || appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.WAITING)
                .map(appointment -> queueMissedAppointmentReminder(tenantId, appointment.id(), actorAppUserId))
                .reduce(ReminderQueueSummary.empty(), ReminderQueueSummary::add);
    }

    private ReminderQueueSummary queueAppointmentReminder(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        var appointment = appointmentService.findById(tenantId, appointmentId);
        PatientEntity patient = patient(tenantId, appointment.patientId());
        String channel = normalizeChannel("email");
        String recipient = resolveRecipient(patient, channel);
        return queueReminder(() -> notificationHistoryService.queueDetailed(
                tenantId,
                patient.getId(),
                "APPOINTMENT_REMINDER",
                channel,
                recipient,
                "Appointment reminder",
                "Appointment scheduled for " + appointment.appointmentDate() + (appointment.appointmentTime() == null ? "" : " at " + appointment.appointmentTime()),
                "APPOINTMENT",
                appointment.id(),
                actorAppUserId
        ));
    }

    private boolean shouldSendReminder(OffsetDateTime now, LocalDate appointmentDate, java.time.LocalTime appointmentTime) {
        LocalDateTime appointmentDateTime = LocalDateTime.of(appointmentDate, appointmentTime);
        OffsetDateTime target = appointmentDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        Duration diff = Duration.between(now, target);
        Duration targetWindow = Duration.ofHours(2);
        Duration slack = Duration.ofMinutes(15);
        return !diff.isNegative() && diff.minus(targetWindow).abs().compareTo(slack) <= 0;
    }

    private ReminderQueueSummary queueFollowUpReminder(UUID tenantId, ConsultationRecord consultation, UUID actorAppUserId) {
        PatientEntity patient = patient(tenantId, consultation.patientId());
        String channel = normalizeChannel("email");
        String recipient = resolveRecipient(patient, channel);
        return queueReminder(() -> notificationHistoryService.queueDetailed(
                tenantId,
                patient.getId(),
                "FOLLOW_UP_REMINDER",
                channel,
                recipient,
                "Follow-up reminder",
                "Follow-up due on " + consultation.followUpDate() + ". Please schedule the next visit.",
                "CONSULTATION",
                consultation.id(),
                actorAppUserId
        ));
    }

    private ReminderQueueSummary queuePaymentReminder(UUID tenantId, BillRecord bill, UUID actorAppUserId) {
        PatientEntity patient = patient(tenantId, bill.patientId());
        String channel = normalizeChannel("email");
        String recipient = resolveRecipient(patient, channel);
        return queueReminder(() -> notificationHistoryService.queueDetailed(
                tenantId,
                patient.getId(),
                "PAYMENT_REMINDER",
                channel,
                recipient,
                "Payment reminder",
                "Outstanding bill " + bill.billNumber() + " due amount " + bill.dueAmount(),
                "BILL",
                bill.id(),
                actorAppUserId
        ));
    }

    private ReminderQueueSummary queueMissedAppointmentReminder(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        var appointment = appointmentService.findById(tenantId, appointmentId);
        PatientEntity patient = patient(tenantId, appointment.patientId());
        String channel = normalizeChannel("email");
        String recipient = resolveRecipient(patient, channel);
        return queueReminder(() -> notificationHistoryService.queueDetailed(
                tenantId,
                patient.getId(),
                "MISSED_APPOINTMENT_REMINDER",
                channel,
                recipient,
                "Missed appointment reminder",
                "Appointment on " + appointment.appointmentDate() + " was not completed. Please reschedule.",
                "APPOINTMENT",
                appointment.id(),
                actorAppUserId
        ));
    }

    private ReminderQueueSummary queueReminder(ReminderQueueOperation operation) {
        try {
            NotificationQueueResult result = operation.queue();
            return result.created() ? ReminderQueueSummary.queued() : ReminderQueueSummary.skippedDuplicate();
        } catch (RuntimeException ex) {
            log.warn("Failed to queue reminder notification", ex);
            return ReminderQueueSummary.failed();
        }
    }

    private PatientEntity patient(UUID tenantId, UUID patientId) {
        return patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
    }

    private String resolveRecipient(PatientEntity patient, String channel) {
        if ("whatsapp".equals(channel) || "sms".equals(channel)) {
            if (StringUtils.hasText(patient.getMobile())) {
                return patient.getMobile();
            }
            throw new IllegalArgumentException("Patient mobile number is required for WhatsApp/SMS notifications");
        }
        if (StringUtils.hasText(patient.getEmail())) {
            return patient.getEmail();
        }
        if (StringUtils.hasText(patient.getMobile())) {
            return patient.getMobile();
        }
        throw new IllegalArgumentException("Patient contact is required");
    }

    private String patientTargetLabel(PatientEntity patient) {
        String name = (patient.getFirstName() + " " + patient.getLastName()).trim();
        if (StringUtils.hasText(patient.getPatientNumber())) {
            return "patient:" + patient.getPatientNumber() + " • " + name;
        }
        return StringUtils.hasText(name) ? "patient:" + name : "patient";
    }

    private String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return "email";
        }
        return channel.trim().toLowerCase();
    }

    private String buildPrescriptionMessage(PrescriptionRecord prescription, PatientEntity patient) {
        return "Prescription " + prescription.prescriptionNumber()
                + " is ready for " + patient.getFirstName() + " " + patient.getLastName()
                + ". Follow advice: " + (prescription.advice() == null ? "Please review with the doctor." : prescription.advice());
    }

    public record InvoiceEmailResult(boolean sent, String message, String recipientEmail, OffsetDateTime sentAt) {}

    @FunctionalInterface
    private interface ReminderQueueOperation {
        NotificationQueueResult queue();
    }

    public record ReminderQueueSummary(int queuedCount, int skippedDuplicateCount, int failedCount) {
        public static ReminderQueueSummary empty() {
            return new ReminderQueueSummary(0, 0, 0);
        }

        public static ReminderQueueSummary queued() {
            return new ReminderQueueSummary(1, 0, 0);
        }

        public static ReminderQueueSummary skippedDuplicate() {
            return new ReminderQueueSummary(0, 1, 0);
        }

        public static ReminderQueueSummary failed() {
            return new ReminderQueueSummary(0, 0, 1);
        }

        public ReminderQueueSummary add(ReminderQueueSummary other) {
            return new ReminderQueueSummary(
                    queuedCount + other.queuedCount,
                    skippedDuplicateCount + other.skippedDuplicateCount,
                    failedCount + other.failedCount
            );
        }
    }
}
