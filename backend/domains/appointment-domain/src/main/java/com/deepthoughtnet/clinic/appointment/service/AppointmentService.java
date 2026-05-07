package com.deepthoughtnet.clinic.appointment.service;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatusUpdateCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.WalkInAppointmentCommand;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AppointmentService {
    private static final String APPOINTMENT_ENTITY = "APPOINTMENT";
    private static final String AVAILABILITY_ENTITY = "DOCTOR_AVAILABILITY";

    private final AppointmentRepository appointmentRepository;
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;
    private final PatientRepository patientRepository;
    private final TenantUserManagementService tenantUserManagementService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            DoctorAvailabilityRepository doctorAvailabilityRepository,
            PatientRepository patientRepository,
            TenantUserManagementService tenantUserManagementService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.appointmentRepository = appointmentRepository;
        this.doctorAvailabilityRepository = doctorAvailabilityRepository;
        this.patientRepository = patientRepository;
        this.tenantUserManagementService = tenantUserManagementService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailabilityRecord> listAvailabilities(UUID tenantId) {
        requireTenant(tenantId);
        return toAvailabilityRecords(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(tenantId));
    }

    @Transactional
    public DoctorAvailabilityRecord createAvailability(UUID tenantId, UUID doctorUserId, DoctorAvailabilityUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        validateAvailability(command);
        ensureDoctorInTenant(tenantId, doctorUserId);

        DoctorAvailabilityEntity entity = DoctorAvailabilityEntity.create(tenantId, doctorUserId);
        entity.update(command.dayOfWeek(), command.startTime(), command.endTime(), command.consultationDurationMinutes(), command.active());
        DoctorAvailabilityEntity saved = doctorAvailabilityRepository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AVAILABILITY_ENTITY,
                saved.getId(),
                "doctor.availability.created",
                actorAppUserId,
                OffsetDateTime.now(),
                "Created doctor availability",
                detailsJson(saved)
        ));
        return toRecord(saved);
    }

    @Transactional
    public DoctorAvailabilityRecord updateAvailability(UUID tenantId, UUID id, DoctorAvailabilityUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validateAvailability(command);
        DoctorAvailabilityEntity entity = doctorAvailabilityRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor availability not found"));
        entity.update(command.dayOfWeek(), command.startTime(), command.endTime(), command.consultationDurationMinutes(), command.active());
        DoctorAvailabilityEntity saved = doctorAvailabilityRepository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AVAILABILITY_ENTITY,
                saved.getId(),
                "doctor.availability.updated",
                actorAppUserId,
                OffsetDateTime.now(),
                "Updated doctor availability",
                detailsJson(saved)
        ));
        return toRecord(saved);
    }

    @Transactional
    public DoctorAvailabilityRecord deactivateAvailability(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        DoctorAvailabilityEntity entity = doctorAvailabilityRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor availability not found"));
        entity.update(entity.getDayOfWeek(), entity.getStartTime(), entity.getEndTime(), entity.getConsultationDurationMinutes(), false);
        DoctorAvailabilityEntity saved = doctorAvailabilityRepository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AVAILABILITY_ENTITY,
                saved.getId(),
                "doctor.availability.deactivated",
                actorAppUserId,
                OffsetDateTime.now(),
                "Deactivated doctor availability",
                detailsJson(saved)
        ));
        return toRecord(saved);
    }

    @Transactional(readOnly = true)
    public List<AppointmentRecord> search(UUID tenantId, AppointmentSearchCriteria criteria) {
        requireTenant(tenantId);
        AppointmentSearchCriteria safe = criteria == null ? new AppointmentSearchCriteria(null, null, null, null, null) : criteria;
        Specification<AppointmentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (safe.doctorUserId() != null) {
                predicates.add(cb.equal(root.get("doctorUserId"), safe.doctorUserId()));
            }
            if (safe.patientId() != null) {
                predicates.add(cb.equal(root.get("patientId"), safe.patientId()));
            }
            if (safe.appointmentDate() != null) {
                predicates.add(cb.equal(root.get("appointmentDate"), safe.appointmentDate()));
            }
            if (safe.status() != null) {
                predicates.add(cb.equal(root.get("status"), safe.status()));
            }
            if (safe.type() != null) {
                predicates.add(cb.equal(root.get("type"), safe.type()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Sort sort = Sort.by(
                Sort.Order.desc("appointmentDate"),
                Sort.Order.desc("appointmentTime"),
                Sort.Order.desc("createdAt")
        );
        return mapAppointments(tenantId, appointmentRepository.findAll(spec, sort));
    }

    @Transactional(readOnly = true)
    public List<AppointmentRecord> listToday(UUID tenantId) {
        requireTenant(tenantId);
        return mapAppointments(tenantId, appointmentRepository.findByTenantIdAndAppointmentDateOrderByAppointmentTimeAscCreatedAtAsc(tenantId, LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public List<AppointmentRecord> listQueueToday(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        return mapAppointments(tenantId, appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(
                tenantId,
                doctorUserId,
                LocalDate.now()
        ));
    }

    @Transactional(readOnly = true)
    public List<AppointmentRecord> listByPatient(UUID tenantId, UUID patientId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        return mapAppointments(tenantId, appointmentRepository.findByTenantIdAndPatientIdOrderByAppointmentDateDescAppointmentTimeDescCreatedAtDesc(tenantId, patientId));
    }

    @Transactional(readOnly = true)
    public AppointmentRecord findById(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        AppointmentEntity entity = appointmentRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        return toRecord(entity, tenantUsersById(tenantId), patientsByIds(tenantId, List.of(entity.getPatientId())));
    }

    @Transactional
    public AppointmentRecord createScheduled(UUID tenantId, AppointmentUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateAppointment(command);
        ensurePatientInTenant(tenantId, command.patientId());
        ensureDoctorInTenant(tenantId, command.doctorUserId());

        AppointmentEntity entity = AppointmentEntity.create(tenantId, command.patientId(), command.doctorUserId());
        AppointmentType type = command.type() == null ? AppointmentType.SCHEDULED : command.type();
        AppointmentStatus status = type == AppointmentType.WALK_IN
                ? AppointmentStatus.WAITING
                : (command.status() == null ? AppointmentStatus.BOOKED : command.status());
        Integer token = type == AppointmentType.WALK_IN ? nextToken(tenantId, command.doctorUserId(), command.appointmentDate()) : null;
        entity.update(command.appointmentDate(), command.appointmentTime(), token, normalizeNullable(command.reason()), type, status);
        AppointmentEntity saved = appointmentRepository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                APPOINTMENT_ENTITY,
                saved.getId(),
                "appointment.created",
                actorAppUserId,
                OffsetDateTime.now(),
                "Created appointment",
                detailsJson(saved)
        ));
        return toRecord(saved, tenantUsersById(tenantId), patientsByIds(tenantId, List.of(saved.getPatientId())));
    }

    @Transactional
    public AppointmentRecord createWalkIn(UUID tenantId, WalkInAppointmentCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateWalkIn(command);
        ensurePatientInTenant(tenantId, command.patientId());
        ensureDoctorInTenant(tenantId, command.doctorUserId());

        AppointmentEntity entity = AppointmentEntity.create(tenantId, command.patientId(), command.doctorUserId());
        Integer token = nextToken(tenantId, command.doctorUserId(), command.appointmentDate());
        entity.update(command.appointmentDate(), null, token, normalizeNullable(command.reason()), AppointmentType.WALK_IN, AppointmentStatus.WAITING);
        AppointmentEntity saved = appointmentRepository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                APPOINTMENT_ENTITY,
                saved.getId(),
                "walkin.created",
                actorAppUserId,
                OffsetDateTime.now(),
                "Created walk-in appointment",
                detailsJson(saved)
        ));
        return toRecord(saved, tenantUsersById(tenantId), patientsByIds(tenantId, List.of(saved.getPatientId())));
    }

    @Transactional
    public AppointmentRecord updateStatus(UUID tenantId, UUID id, AppointmentStatusUpdateCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        if (command == null || command.status() == null) {
            throw new IllegalArgumentException("status is required");
        }

        AppointmentEntity entity = appointmentRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        ensureTransitionAllowed(entity.getStatus(), command.status());
        entity.update(
                entity.getAppointmentDate(),
                entity.getAppointmentTime(),
                entity.getTokenNumber(),
                entity.getReason(),
                entity.getType(),
                command.status()
        );
        AppointmentEntity saved = appointmentRepository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                APPOINTMENT_ENTITY,
                saved.getId(),
                "appointment.status.updated",
                actorAppUserId,
                OffsetDateTime.now(),
                "Updated appointment status",
                detailsJson(saved)
        ));
        return toRecord(saved, tenantUsersById(tenantId), patientsByIds(tenantId, List.of(saved.getPatientId())));
    }

    private List<AppointmentRecord> mapAppointments(UUID tenantId, List<AppointmentEntity> appointments) {
        Map<UUID, PatientEntity> patients = patientsByIds(tenantId, appointments.stream().map(AppointmentEntity::getPatientId).distinct().toList());
        Map<UUID, TenantUserRecord> users = tenantUsersById(tenantId);
        return appointments.stream().map(entity -> toRecord(entity, users, patients)).toList();
    }

    private AppointmentRecord toRecord(AppointmentEntity entity, Map<UUID, TenantUserRecord> users, Map<UUID, PatientEntity> patients) {
        PatientEntity patient = patients.get(entity.getPatientId());
        TenantUserRecord doctor = users.get(entity.getDoctorUserId());
        return new AppointmentRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientId(),
                patient == null ? null : patient.getPatientNumber(),
                patient == null ? null : patient.getFirstName() + " " + patient.getLastName(),
                entity.getDoctorUserId(),
                doctor == null ? null : doctor.displayName(),
                entity.getAppointmentDate(),
                entity.getAppointmentTime(),
                entity.getTokenNumber(),
                entity.getReason(),
                entity.getType(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DoctorAvailabilityRecord toRecord(DoctorAvailabilityEntity entity) {
        TenantUserRecord doctor = tenantUsersById(entity.getTenantId()).get(entity.getDoctorUserId());
        return new DoctorAvailabilityRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getDoctorUserId(),
                doctor == null ? null : doctor.displayName(),
                entity.getDayOfWeek(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getConsultationDurationMinutes(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<DoctorAvailabilityRecord> toAvailabilityRecords(List<DoctorAvailabilityEntity> entities) {
        Map<UUID, TenantUserRecord> users = tenantUsersById(entities.isEmpty() ? null : entities.get(0).getTenantId());
        return entities.stream().map(entity -> new DoctorAvailabilityRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getDoctorUserId(),
                users.get(entity.getDoctorUserId()) == null ? null : users.get(entity.getDoctorUserId()).displayName(),
                entity.getDayOfWeek(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getConsultationDurationMinutes(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        )).toList();
    }

    private Map<UUID, TenantUserRecord> tenantUsersById(UUID tenantId) {
        if (tenantId == null) {
            return Map.of();
        }
        return tenantUserManagementService.list(tenantId).stream()
                .collect(Collectors.toMap(TenantUserRecord::appUserId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<UUID, PatientEntity> patientsByIds(UUID tenantId, Collection<UUID> ids) {
        if (tenantId == null) {
            return Map.of();
        }
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return patientRepository.findByTenantIdAndIdIn(tenantId, ids)
                .stream()
                .collect(Collectors.toMap(PatientEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
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
        if (doctor.membershipRole() == null || !"DOCTOR".equalsIgnoreCase(doctor.membershipRole())) {
            throw new IllegalArgumentException("Selected user is not a doctor");
        }
    }

    private Integer nextToken(UUID tenantId, UUID doctorUserId, LocalDate appointmentDate) {
        Integer current = appointmentRepository.findMaxTokenNumber(tenantId, doctorUserId, appointmentDate);
        return (current == null ? 0 : current) + 1;
    }

    private void validateAppointment(AppointmentUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireId(command.patientId(), "patientId");
        requireId(command.doctorUserId(), "doctorUserId");
        requireDate(command.appointmentDate(), "appointmentDate");
        if (command.type() == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (command.type() != AppointmentType.WALK_IN && command.type() != AppointmentType.SCHEDULED
                && command.type() != AppointmentType.FOLLOW_UP && command.type() != AppointmentType.VACCINATION) {
            throw new IllegalArgumentException("Invalid appointment type");
        }
    }

    private void validateWalkIn(WalkInAppointmentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireId(command.patientId(), "patientId");
        requireId(command.doctorUserId(), "doctorUserId");
        requireDate(command.appointmentDate(), "appointmentDate");
    }

    private void validateAvailability(DoctorAvailabilityUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.dayOfWeek() == null) {
            throw new IllegalArgumentException("dayOfWeek is required");
        }
        requireTime(command.startTime(), "startTime");
        requireTime(command.endTime(), "endTime");
        if (command.consultationDurationMinutes() == null || command.consultationDurationMinutes() <= 0) {
            throw new IllegalArgumentException("consultationDurationMinutes is required");
        }
        if (!command.startTime().isBefore(command.endTime())) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
    }

    private void ensureTransitionAllowed(AppointmentStatus current, AppointmentStatus target) {
        if (current == target) {
            return;
        }
        boolean allowed = switch (current) {
            case BOOKED -> target == AppointmentStatus.WAITING
                    || target == AppointmentStatus.CANCELLED
                    || target == AppointmentStatus.NO_SHOW;
            case WAITING -> target == AppointmentStatus.IN_CONSULTATION
                    || target == AppointmentStatus.CANCELLED
                    || target == AppointmentStatus.NO_SHOW;
            case IN_CONSULTATION -> target == AppointmentStatus.COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("Invalid appointment status transition from " + current + " to " + target);
        }
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireDoctor(UUID doctorUserId) {
        requireId(doctorUserId, "doctorUserId");
    }

    private void requireId(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void requireDate(LocalDate date, String field) {
        if (date == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void requireTime(LocalTime time, String field) {
        if (time == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String detailsJson(DoctorAvailabilityEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("doctorUserId", entity.getDoctorUserId());
        details.put("dayOfWeek", entity.getDayOfWeek());
        details.put("startTime", entity.getStartTime());
        details.put("endTime", entity.getEndTime());
        details.put("consultationDurationMinutes", entity.getConsultationDurationMinutes());
        details.put("active", entity.isActive());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String detailsJson(AppointmentEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("patientId", entity.getPatientId());
        details.put("doctorUserId", entity.getDoctorUserId());
        details.put("appointmentDate", entity.getAppointmentDate());
        details.put("appointmentTime", entity.getAppointmentTime());
        details.put("tokenNumber", entity.getTokenNumber());
        details.put("type", entity.getType());
        details.put("status", entity.getStatus());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
