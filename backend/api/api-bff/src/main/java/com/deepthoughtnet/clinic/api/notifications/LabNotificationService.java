package com.deepthoughtnet.clinic.api.notifications;

import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notify.NotificationAttachment;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LabNotificationService {
    private final NotificationHistoryService notificationHistoryService;
    private final NotificationProvider notificationProvider;
    private final PatientRepository patientRepository;

    public LabNotificationService(
            NotificationHistoryService notificationHistoryService,
            NotificationProvider notificationProvider,
            PatientRepository patientRepository
    ) {
        this.notificationHistoryService = notificationHistoryService;
        this.notificationProvider = notificationProvider;
        this.patientRepository = patientRepository;
    }

    public void notifyOrderCreated(UUID tenantId, UUID patientId, UUID sourceId, String orderNumber, String patientName, String doctorName, List<String> tests, UUID actorAppUserId) {
        String subject = "Lab order created " + orderNumber;
        String body = buildBody(
                "Your lab order has been created.",
                "Order: " + orderNumber,
                patientName,
                doctorName,
                tests == null || tests.isEmpty() ? null : "Tests: " + String.join(", ", tests)
        );
        queueAndSend(tenantId, patientId, sourceId, "LAB_ORDER_CREATED", subject, body, actorAppUserId, null);
    }

    public void notifySampleCollected(UUID tenantId, UUID patientId, UUID sourceId, String orderNumber, String patientName, String doctorName, UUID actorAppUserId) {
        String subject = "Lab sample collected " + orderNumber;
        String body = buildBody(
                "Your lab sample has been collected.",
                "Order: " + orderNumber,
                patientName,
                doctorName
        );
        queueAndSend(tenantId, patientId, sourceId, "LAB_SAMPLE_COLLECTED", subject, body, actorAppUserId, null);
    }

    public void notifyReportReady(UUID tenantId, UUID patientId, UUID sourceId, String orderNumber, String patientName, String doctorName, byte[] pdfContent, String pdfFilename, UUID actorAppUserId) {
        String subject = "Lab report ready " + orderNumber;
        String body = buildBody(
                "Your lab report is ready.",
                "Order: " + orderNumber,
                patientName,
                doctorName,
                "Please download the report from the patient portal."
        );
        queueAndSend(
                tenantId,
                patientId,
                sourceId,
                "LAB_REPORT_READY",
                subject,
                body,
                actorAppUserId,
                pdfContent == null ? null : new NotificationAttachment(pdfFilename, "application/pdf", pdfContent)
        );
    }

    public void notifyDoctorReviewed(UUID tenantId, UUID patientId, UUID sourceId, String orderNumber, String patientName, String doctorName, String doctorComments, UUID actorAppUserId) {
        String subject = "Lab report reviewed " + orderNumber;
        String body = buildBody(
                "Your lab report was reviewed by the doctor.",
                "Order: " + orderNumber,
                patientName,
                doctorName,
                StringUtils.hasText(doctorComments) ? "Doctor comments: " + doctorComments : null
        );
        queueAndSend(tenantId, patientId, sourceId, "LAB_REPORT_REVIEWED", subject, body, actorAppUserId, null);
    }

    private void queueAndSend(
            UUID tenantId,
            UUID patientId,
            UUID sourceId,
            String eventType,
            String subject,
            String body,
            UUID actorAppUserId,
            NotificationAttachment attachment
    ) {
        notificationHistoryService.queueDetailed(
                tenantId,
                patientId,
                eventType,
                "IN_APP",
                patientInboxRecipient(patientId),
                subject,
                body,
                "LAB_ORDER",
                sourceId,
                actorAppUserId
        );
        PatientEntity patient = patientId == null ? null : patientRepository.findByTenantIdAndId(tenantId, patientId).orElse(null);
        if (patient != null && StringUtils.hasText(patient.getEmail())) {
            try {
                List<NotificationAttachment> attachments = attachment == null ? List.of() : List.of(attachment);
                notificationProvider.send(new NotificationMessage(
                        tenantId,
                        "EMAIL",
                        patient.getEmail().trim(),
                        subject,
                        body,
                        "{\"sourceType\":\"LAB_ORDER\",\"sourceId\":\"" + sourceId + "\"}",
                        null,
                        attachments
                ));
            } catch (RuntimeException ignored) {
                // Email delivery is additive and must not block lab workflow progress.
            }
        }
    }

    private String patientInboxRecipient(UUID patientId) {
        return patientId == null ? "patient" : "patient:" + patientId;
    }

    private String buildBody(String lead, String orderLine, String patientName, String doctorName, String... extras) {
        StringBuilder builder = new StringBuilder(lead);
        if (StringUtils.hasText(orderLine)) {
            builder.append("\n").append(orderLine);
        }
        if (StringUtils.hasText(patientName)) {
            builder.append("\nPatient: ").append(patientName);
        }
        if (StringUtils.hasText(doctorName)) {
            builder.append("\nDoctor: ").append(doctorName);
        }
        if (extras != null) {
            for (String extra : extras) {
                if (StringUtils.hasText(extra)) {
                    builder.append("\n").append(extra);
                }
            }
        }
        return builder.toString();
    }
}
