package com.deepthoughtnet.clinic.consultation.service;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatusUpdateCommand;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationUpsertCommand;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConsultationService {
    private static final String ENTITY_TYPE = "CONSULTATION";

    private final ConsultationRepository repository;
    private final PatientRepository patientRepository;
    private final TenantUserManagementService tenantUserManagementService;
    private final AppointmentService appointmentService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public ConsultationService(
            ConsultationRepository repository,
            PatientRepository patientRepository,
            TenantUserManagementService tenantUserManagementService,
            AppointmentService appointmentService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.tenantUserManagementService = tenantUserManagementService;
        this.appointmentService = appointmentService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ConsultationRecord> list(UUID tenantId) {
        requireTenant(tenantId);
        return mapRecords(tenantId, repository.findByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    @Transactional(readOnly = true)
    public List<ConsultationRecord> listByDoctor(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireId(doctorUserId, "doctorUserId");
        return mapRecords(tenantId, repository.findByTenantIdAndDoctorUserIdOrderByCreatedAtDesc(tenantId, doctorUserId));
    }

    @Transactional(readOnly = true)
    public Optional<ConsultationRecord> findById(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return repository.findByTenantIdAndId(tenantId, id).map(entity -> toRecord(entity, tenantData(tenantId)));
    }

    @Transactional(readOnly = true)
    public List<ConsultationRecord> listByPatient(UUID tenantId, UUID patientId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        return mapRecords(tenantId, repository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId));
    }

    @Transactional
    public ConsultationRecord createDraft(UUID tenantId, ConsultationUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validate(command, false);
        ensurePatientInTenant(tenantId, command.patientId());
        ensureDoctorInTenant(tenantId, command.doctorUserId());
        ensureAppointmentAssignment(tenantId, command);
        if (command.appointmentId() != null && repository.findByTenantIdAndAppointmentId(tenantId, command.appointmentId()).isPresent()) {
            throw new IllegalArgumentException("Consultation already exists for this appointment");
        }

        ConsultationEntity entity = ConsultationEntity.create(tenantId, command.patientId(), command.doctorUserId(), command.appointmentId());
        applyCommand(entity, command);
        ConsultationEntity saved = repository.save(entity);
        audit(tenantId, saved, "consultation.created", actorAppUserId, "Created consultation draft");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public ConsultationRecord startFromAppointment(UUID tenantId, UUID appointmentId, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(appointmentId, "appointmentId");

        AppointmentRecord appointment = appointmentService.findById(tenantId, appointmentId);
        if (appointment.status() == AppointmentStatus.COMPLETED || appointment.status() == AppointmentStatus.CANCELLED || appointment.status() == AppointmentStatus.NO_SHOW) {
            throw new IllegalArgumentException("Appointment cannot be started from its current status");
        }

        ConsultationEntity existing = repository.findByTenantIdAndAppointmentId(tenantId, appointmentId).orElse(null);
        if (existing != null) {
            appointmentService.updateStatus(tenantId, appointmentId, new AppointmentStatusUpdateCommand(AppointmentStatus.IN_CONSULTATION, null), actorAppUserId);
            return toRecord(existing, tenantData(tenantId));
        }

        appointmentService.updateStatus(tenantId, appointmentId, new AppointmentStatusUpdateCommand(AppointmentStatus.IN_CONSULTATION, null), actorAppUserId);
        ConsultationEntity entity = ConsultationEntity.create(tenantId, appointment.patientId(), appointment.doctorUserId(), appointmentId);
        entity.update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        ConsultationEntity saved = repository.save(entity);
        audit(tenantId, saved, "consultation.created", actorAppUserId, "Started consultation from appointment");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public ConsultationRecord update(UUID tenantId, UUID id, ConsultationUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validate(command, false);
        ConsultationEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        ensureEditable(entity);
        ensurePatientInTenant(tenantId, command.patientId());
        ensureDoctorInTenant(tenantId, command.doctorUserId());
        ensureAppointmentAssignment(tenantId, command);

        entity.update(
                normalizeNullable(command.chiefComplaints()),
                normalizeNullable(command.symptoms()),
                normalizeNullable(command.diagnosis()),
                normalizeNullable(command.clinicalNotes()),
                normalizeNullable(command.advice()),
                command.followUpDate(),
                command.bloodPressureSystolic(),
                command.bloodPressureDiastolic(),
                command.pulseRate(),
                command.temperature(),
                command.temperatureUnit(),
                command.weightKg(),
                command.heightCm(),
                command.spo2(),
                command.respiratoryRate()
        );
        ConsultationEntity saved = repository.save(entity);
        audit(tenantId, saved, "consultation.updated", actorAppUserId, "Updated consultation draft");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public ConsultationRecord complete(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        ConsultationEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        ensureEditable(entity);
        entity.complete();
        ConsultationEntity saved = repository.save(entity);
        if (saved.getAppointmentId() != null) {
            appointmentService.updateStatus(tenantId, saved.getAppointmentId(), new AppointmentStatusUpdateCommand(AppointmentStatus.COMPLETED, null), actorAppUserId);
        }
        audit(tenantId, saved, "consultation.completed", actorAppUserId, "Completed consultation");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public ConsultationRecord cancel(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        ConsultationEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        ensureEditable(entity);
        entity.cancel();
        ConsultationEntity saved = repository.save(entity);
        if (saved.getAppointmentId() != null) {
            appointmentService.updateStatus(tenantId, saved.getAppointmentId(), new AppointmentStatusUpdateCommand(AppointmentStatus.CANCELLED, null), actorAppUserId);
        }
        audit(tenantId, saved, "consultation.cancelled", actorAppUserId, "Cancelled consultation");
        return toRecord(saved, tenantData(tenantId));
    }

    private void applyCommand(ConsultationEntity entity, ConsultationUpsertCommand command) {
        entity.update(
                normalizeNullable(command.chiefComplaints()),
                normalizeNullable(command.symptoms()),
                normalizeNullable(command.diagnosis()),
                normalizeNullable(command.clinicalNotes()),
                normalizeNullable(command.advice()),
                command.followUpDate(),
                command.bloodPressureSystolic(),
                command.bloodPressureDiastolic(),
                command.pulseRate(),
                command.temperature(),
                command.temperatureUnit(),
                command.weightKg(),
                command.heightCm(),
                command.spo2(),
                command.respiratoryRate()
        );
    }

    private ConsultationRecord toRecord(ConsultationEntity entity, ConsultationData data) {
        PatientEntity patient = data.patients().get(entity.getPatientId());
        TenantUserRecord doctor = data.users().get(entity.getDoctorUserId());
        return new ConsultationRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientId(),
                patient == null ? null : patient.getPatientNumber(),
                patient == null ? null : patient.getFirstName() + " " + patient.getLastName(),
                entity.getDoctorUserId(),
                doctor == null ? null : doctor.displayName(),
                entity.getAppointmentId(),
                entity.getChiefComplaints(),
                entity.getSymptoms(),
                entity.getDiagnosis(),
                entity.getClinicalNotes(),
                entity.getAdvice(),
                entity.getFollowUpDate(),
                entity.getStatus(),
                entity.getBloodPressureSystolic(),
                entity.getBloodPressureDiastolic(),
                entity.getPulseRate(),
                entity.getTemperature(),
                entity.getTemperatureUnit(),
                entity.getWeightKg(),
                entity.getHeightCm(),
                entity.getSpo2(),
                entity.getRespiratoryRate(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<ConsultationRecord> mapRecords(UUID tenantId, List<ConsultationEntity> entities) {
        ConsultationData data = tenantData(tenantId);
        return entities.stream().map(entity -> toRecord(entity, data)).toList();
    }

    private ConsultationData tenantData(UUID tenantId) {
        Map<UUID, PatientEntity> patients = patientRepository.findByTenantIdAndIdIn(tenantId, patientIdsForTenant(tenantId))
                .stream()
                .collect(Collectors.toMap(PatientEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, TenantUserRecord> users = tenantUserManagementService.list(tenantId).stream()
                .collect(Collectors.toMap(TenantUserRecord::appUserId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return new ConsultationData(patients, users);
    }

    private Collection<UUID> patientIdsForTenant(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ConsultationEntity::getPatientId)
                .distinct()
                .toList();
    }

    private void ensureEditable(ConsultationEntity entity) {
        if (entity.getStatus() != ConsultationStatus.DRAFT) {
            throw new IllegalArgumentException("Completed or cancelled consultations cannot be modified");
        }
    }

    private void ensurePatientInTenant(UUID tenantId, UUID patientId) {
        if (patientRepository.findByTenantIdAndId(tenantId, patientId).isEmpty()) {
            throw new IllegalArgumentException("Patient not found for tenant");
        }
    }

    private void ensureDoctorInTenant(UUID tenantId, UUID doctorUserId) {
        TenantUserRecord doctor = tenantUserManagementService.list(tenantId).stream()
                .filter(record -> record.appUserId() != null && record.appUserId().equals(doctorUserId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found for tenant"));
        if (!"DOCTOR".equalsIgnoreCase(doctor.membershipRole())) {
            throw new IllegalArgumentException("Selected user is not a doctor");
        }
    }

    private void ensureAppointmentAssignment(UUID tenantId, ConsultationUpsertCommand command) {
        requireId(command.appointmentId(), "appointmentId");
        AppointmentRecord appointment = appointmentService.findById(tenantId, command.appointmentId());
        if (!appointment.patientId().equals(command.patientId()) || !appointment.doctorUserId().equals(command.doctorUserId())) {
            throw new IllegalArgumentException("Consultation appointment must match the assigned patient and doctor");
        }
    }

    private void validate(ConsultationUpsertCommand command, boolean allowNullPatientDoctor) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (!allowNullPatientDoctor) {
            requireId(command.patientId(), "patientId");
            requireId(command.doctorUserId(), "doctorUserId");
        }
        if (command.temperatureUnit() != null && command.temperature() == null) {
            throw new IllegalArgumentException("temperature is required when temperatureUnit is set");
        }
    }

    private void audit(UUID tenantId, ConsultationEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                entity.getId(),
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                message,
                detailsJson(entity)
        ));
    }

    private String detailsJson(ConsultationEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("patientId", entity.getPatientId());
        details.put("doctorUserId", entity.getDoctorUserId());
        details.put("appointmentId", entity.getAppointmentId());
        details.put("status", entity.getStatus());
        details.put("diagnosis", entity.getDiagnosis());
        details.put("followUpDate", entity.getFollowUpDate());
        details.put("respiratoryRate", entity.getRespiratoryRate());
        details.put("completedAt", entity.getCompletedAt());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireId(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ConsultationData(Map<UUID, PatientEntity> patients, Map<UUID, TenantUserRecord> users) {
    }
}
