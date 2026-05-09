package com.deepthoughtnet.clinic.api.notifications;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionPdf;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.notify.NotificationAttachment;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NotificationActionService {
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

    public int queueAppointmentReminders(UUID tenantId, LocalDate appointmentDate, UUID actorAppUserId) {
        LocalDate windowDate = appointmentDate;
        OffsetDateTime now = OffsetDateTime.now();
        return appointmentService.search(tenantId, new com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria(null, null, windowDate, null, null))
                .stream()
                .filter(appointment -> appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.BOOKED)
                .filter(appointment -> appointment.appointmentTime() != null)
                .filter(appointment -> shouldSendReminder(now, appointment.appointmentDate(), appointment.appointmentTime()))
                .mapToInt(appointment -> queueAppointmentReminder(tenantId, appointment.id(), actorAppUserId))
                .sum();
    }

    public int queueFollowUpReminders(UUID tenantId, LocalDate dueDate, UUID actorAppUserId) {
        return consultationService.list(tenantId).stream()
                .filter(record -> record.followUpDate() != null && !record.followUpDate().isAfter(dueDate))
                .filter(record -> record.status() == ConsultationStatus.COMPLETED || record.status() == ConsultationStatus.DRAFT)
                .mapToInt(record -> queueFollowUpReminder(tenantId, record, actorAppUserId))
                .sum();
    }

    public int queueVaccinationReminders(UUID tenantId, UUID actorAppUserId) {
        return vaccinationService.listDue(tenantId).stream()
                .mapToInt(vaccination -> {
                    PatientEntity patient = patient(tenantId, vaccination.patientId());
                    String channel = normalizeChannel("email");
                    String recipient = resolveRecipient(patient, channel);
                    notificationHistoryService.queue(
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
                    );
                    return 1;
                }).sum();
    }

    public int queuePaymentReminders(UUID tenantId, UUID actorAppUserId) {
        return billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null)).stream()
                .filter(bill -> bill.dueAmount() != null && bill.dueAmount().compareTo(java.math.BigDecimal.ZERO) > 0)
                .filter(bill -> bill.status() == com.deepthoughtnet.clinic.billing.service.model.BillStatus.UNPAID
                        || bill.status() == com.deepthoughtnet.clinic.billing.service.model.BillStatus.PARTIALLY_PAID
                        || bill.status() == com.deepthoughtnet.clinic.billing.service.model.BillStatus.ISSUED)
                .mapToInt(bill -> queuePaymentReminder(tenantId, bill, actorAppUserId))
                .sum();
    }

    public int queueMissedAppointmentReminders(UUID tenantId, LocalDate missedBeforeDate, UUID actorAppUserId) {
        LocalDate cutoff = missedBeforeDate == null ? LocalDate.now() : missedBeforeDate;
        return appointmentService.search(tenantId, new com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria(null, null, null, null, null))
                .stream()
                .filter(appointment -> appointment.appointmentDate() != null && appointment.appointmentDate().isBefore(cutoff))
                .filter(appointment -> appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.BOOKED
                        || appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.WAITING)
                .mapToInt(appointment -> queueMissedAppointmentReminder(tenantId, appointment.id(), actorAppUserId))
                .sum();
    }

    private int queueAppointmentReminder(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        var appointment = appointmentService.findById(tenantId, appointmentId);
        PatientEntity patient = patient(tenantId, appointment.patientId());
        String channel = normalizeChannel("email");
        String recipient = resolveRecipient(patient, channel);
        notificationHistoryService.queue(
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
        );
        return 1;
    }

    private boolean shouldSendReminder(OffsetDateTime now, LocalDate appointmentDate, java.time.LocalTime appointmentTime) {
        LocalDateTime appointmentDateTime = LocalDateTime.of(appointmentDate, appointmentTime);
        OffsetDateTime target = appointmentDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        Duration diff = Duration.between(now, target);
        Duration targetWindow = Duration.ofHours(2);
        Duration slack = Duration.ofMinutes(15);
        return !diff.isNegative() && diff.minus(targetWindow).abs().compareTo(slack) <= 0;
    }

    private int queueFollowUpReminder(UUID tenantId, ConsultationRecord consultation, UUID actorAppUserId) {
        PatientEntity patient = patient(tenantId, consultation.patientId());
        String channel = normalizeChannel("email");
        String recipient = resolveRecipient(patient, channel);
        notificationHistoryService.queue(
                tenantId,
                patient.getId(),
                "FOLLOW_UP_REMINDER",
                channel,
                recipient,
                "Follow-up reminder",
                "Follow-up due on " + consultation.followUpDate() + " for consultation " + consultation.id(),
                "CONSULTATION",
                consultation.id(),
                actorAppUserId
        );
        return 1;
    }

    private int queuePaymentReminder(UUID tenantId, BillRecord bill, UUID actorAppUserId) {
        PatientEntity patient = patient(tenantId, bill.patientId());
        String channel = normalizeChannel("email");
        String recipient = resolveRecipient(patient, channel);
        notificationHistoryService.queue(
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
        );
        return 1;
    }

    private int queueMissedAppointmentReminder(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        var appointment = appointmentService.findById(tenantId, appointmentId);
        PatientEntity patient = patient(tenantId, appointment.patientId());
        String channel = normalizeChannel("email");
        String recipient = resolveRecipient(patient, channel);
        notificationHistoryService.queue(
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
        );
        return 1;
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
}
