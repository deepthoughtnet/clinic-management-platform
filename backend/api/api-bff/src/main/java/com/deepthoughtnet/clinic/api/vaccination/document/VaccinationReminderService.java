package com.deepthoughtnet.clinic.api.vaccination.document;

import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class VaccinationReminderService {
    private final VaccinationService vaccinationService;
    private final NotificationHistoryService notificationHistoryService;
    private final PatientRepository patientRepository;

    public VaccinationReminderService(
            VaccinationService vaccinationService,
            NotificationHistoryService notificationHistoryService,
            PatientRepository patientRepository
    ) {
        this.vaccinationService = vaccinationService;
        this.notificationHistoryService = notificationHistoryService;
        this.patientRepository = patientRepository;
    }

    @Transactional
    public int queueDueAndOverdueReminders(UUID tenantId, UUID actorAppUserId) {
        return queue(vaccinationService.listDue(tenantId), tenantId, actorAppUserId)
                + queue(vaccinationService.listOverdue(tenantId), tenantId, actorAppUserId);
    }

    @Transactional
    public int queuePatientReminders(UUID tenantId, UUID patientId, UUID actorAppUserId) {
        List<PatientVaccinationRecord> rows = vaccinationService.listByPatient(tenantId, patientId).stream()
                .filter(row -> row.nextDueDate() != null)
                .toList();
        return queue(rows, tenantId, actorAppUserId);
    }

    private int queue(List<PatientVaccinationRecord> rows, UUID tenantId, UUID actorAppUserId) {
        int queued = 0;
        for (PatientVaccinationRecord row : rows) {
            if (row.patientId() == null) {
                continue;
            }
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, row.patientId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
            String channel = StringUtils.hasText(patient.getEmail()) ? "email" : "sms";
            String recipient = resolveRecipient(patient, channel);
            UUID reminderId = reminderUuid(tenantId, row.patientId(), row.vaccineId(), row.doseNumber(), row.nextDueDate(), row.source());
            NotificationQueueResult notification = notificationHistoryService.queueDetailed(
                    tenantId,
                    patient.getId(),
                    "VACCINATION_REMINDER",
                    channel,
                    recipient,
                    "Vaccination reminder",
                    buildMessage(row),
                    "PATIENT_VACCINATION",
                    reminderId,
                    actorAppUserId
            );
            if (notification != null && notification.created()) {
                queued++;
            }
        }
        return queued;
    }

    private UUID reminderUuid(UUID tenantId, UUID patientId, UUID vaccineId, Integer doseNumber, LocalDate dueDate, String source) {
        String basis = String.join("|",
                safe(tenantId),
                safe(patientId),
                safe(vaccineId),
                safe(doseNumber),
                safe(dueDate),
                safe(source)
        );
        return UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8));
    }

    private String buildMessage(PatientVaccinationRecord row) {
        String status = row.nextDueDate() == null ? "due" : (row.nextDueDate().isBefore(LocalDate.now()) ? "overdue" : "upcoming");
        return "Vaccination " + status + " for " + row.vaccineName() + (row.nextDueDate() == null ? "" : " on " + row.nextDueDate());
    }

    private String resolveRecipient(PatientEntity patient, String channel) {
        if ("sms".equals(channel) || "whatsapp".equals(channel)) {
            if (StringUtils.hasText(patient.getMobile())) {
                return patient.getMobile();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient mobile number is required");
        }
        if (StringUtils.hasText(patient.getEmail())) {
            return patient.getEmail();
        }
        if (StringUtils.hasText(patient.getMobile())) {
            return patient.getMobile();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient contact is required");
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}
