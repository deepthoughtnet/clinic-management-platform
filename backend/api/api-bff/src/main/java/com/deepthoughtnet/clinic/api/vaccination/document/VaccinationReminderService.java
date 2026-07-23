package com.deepthoughtnet.clinic.api.vaccination.document;

import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import com.deepthoughtnet.clinic.vaccination.events.VaccinationDueEvent;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaccinationReminderService {
    private final VaccinationService vaccinationService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final ModuleBusinessEventPublisher moduleBusinessEventPublisher;

    public VaccinationReminderService(
            VaccinationService vaccinationService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            ModuleBusinessEventPublisher moduleBusinessEventPublisher
    ) {
        this.vaccinationService = vaccinationService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.moduleBusinessEventPublisher = moduleBusinessEventPublisher;
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
        ZoneId zone = clinicTimeZoneResolver.resolve(tenantId);
        for (PatientVaccinationRecord row : rows) {
            if (row.patientId() == null) {
                continue;
            }
            moduleBusinessEventPublisher.publish(VaccinationDueEvent.due(
                    tenantId,
                    row.id(),
                    row.patientId(),
                    row.vaccineName(),
                    row.doseNumber() == null ? null : "Dose " + row.doseNumber(),
                    row.nextDueDate(),
                    zone.getId(),
                    null,
                    "VACCINATION_DUE:" + row.id() + ":" + row.nextDueDate(),
                    actorAppUserId
            ));
            queued++;
        }
        return queued;
    }
}
