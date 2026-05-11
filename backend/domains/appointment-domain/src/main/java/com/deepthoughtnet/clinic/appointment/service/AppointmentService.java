package com.deepthoughtnet.clinic.appointment.service;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatusUpdateCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorCalendarReconcileResult;
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
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final DayOfWeek DEFAULT_CALENDAR_DAY = DayOfWeek.MONDAY;
    private static final LocalTime DEFAULT_CALENDAR_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CALENDAR_END = LocalTime.of(17, 0);
    private static final int DEFAULT_SLOT_DURATION_MINUTES = 15;

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

    @Transactional(readOnly = true)
    public List<DoctorAvailabilityRecord> listDoctorAvailabilities(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        return toAvailabilityRecords(doctorAvailabilityRepository.findByTenantIdAndDoctorUserIdOrderByDayOfWeekAscStartTimeAsc(tenantId, doctorUserId));
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailabilitySlotRecord> listSlots(UUID tenantId, UUID doctorUserId, LocalDate appointmentDate) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        requireDate(appointmentDate, "appointmentDate");

        List<DoctorAvailabilityEntity> availabilities = doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(tenantId)
                .stream()
                .filter(record -> doctorUserId.equals(record.getDoctorUserId()))
                .filter(DoctorAvailabilityEntity::isActive)
                .filter(record -> record.getDayOfWeek() == appointmentDate.getDayOfWeek())
                .toList();
        if (availabilities.isEmpty()) {
            return List.of();
        }

        List<AppointmentEntity> appointments = appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(
                tenantId,
                doctorUserId,
                appointmentDate
        );
        Map<LocalTime, List<AppointmentEntity>> bookingsByTime = appointments.stream()
                .filter(appointment -> appointment.getAppointmentTime() != null)
                .filter(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED && appointment.getStatus() != AppointmentStatus.NO_SHOW)
                .collect(Collectors.groupingBy(
                        AppointmentEntity::getAppointmentTime,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<DoctorAvailabilitySlotRecord> slots = new ArrayList<>();
        Map<UUID, TenantUserRecord> users = tenantUsersById(tenantId);
        Map<UUID, PatientEntity> patients = patientsByIds(tenantId, appointments.stream().map(AppointmentEntity::getPatientId).distinct().toList());
        TenantUserRecord doctor = users.get(doctorUserId);
        for (DoctorAvailabilityEntity availability : availabilities) {
            int capacity = Math.max(1, availability.getMaxPatientsPerSlot() == null ? 1 : availability.getMaxPatientsPerSlot());
            LocalTime slotStart = availability.getStartTime();
            LocalTime slotEnd = availability.getEndTime();
            while (!slotStart.plusMinutes(availability.getConsultationDurationMinutes()).isAfter(slotEnd)) {
                LocalTime candidateEnd = slotStart.plusMinutes(availability.getConsultationDurationMinutes());
                boolean inBreak = isWithinBreak(slotStart, availability.getBreakStartTime(), availability.getBreakEndTime());
                List<AppointmentEntity> booked = bookingsByTime.getOrDefault(slotStart, List.of());
                DoctorAvailabilitySlotStatus status;
                boolean selectable;
                if (inBreak) {
                    status = DoctorAvailabilitySlotStatus.UNAVAILABLE;
                    selectable = false;
                } else if (booked.isEmpty()) {
                    status = DoctorAvailabilitySlotStatus.AVAILABLE;
                    selectable = true;
                } else if (booked.size() >= capacity) {
                    status = DoctorAvailabilitySlotStatus.BOOKED;
                    selectable = false;
                } else {
                    status = DoctorAvailabilitySlotStatus.BOOKED;
                    selectable = true;
                }

                AppointmentEntity firstBooking = booked.isEmpty() ? null : booked.get(0);
                PatientEntity patient = firstBooking == null ? null : patients.get(firstBooking.getPatientId());
                slots.add(new DoctorAvailabilitySlotRecord(
                        doctorUserId,
                        doctor == null ? null : doctor.displayName(),
                        appointmentDate,
                        slotStart,
                        candidateEnd,
                        status,
                        booked.size(),
                        capacity,
                        selectable,
                        firstBooking == null ? null : firstBooking.getId(),
                        firstBooking == null ? null : firstBooking.getPatientId(),
                        patient == null ? null : patient.getPatientNumber(),
                        patient == null ? null : patient.getFirstName() + " " + patient.getLastName(),
                        firstBooking == null ? null : firstBooking.getTokenNumber(),
                        firstBooking == null ? null : firstBooking.getStatus(),
                        firstBooking == null ? null : firstBooking.getReason()
                ));
                slotStart = candidateEnd;
            }
        }
        return slots;
    }

    @Transactional
    public DoctorAvailabilityRecord createAvailability(UUID tenantId, UUID doctorUserId, DoctorAvailabilityUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        validateAvailability(command);
        ensureDoctorInTenant(tenantId, doctorUserId);

        DoctorAvailabilityEntity entity = DoctorAvailabilityEntity.create(tenantId, doctorUserId);
        entity.update(
                command.dayOfWeek(),
                command.startTime(),
                command.endTime(),
                command.breakStartTime(),
                command.breakEndTime(),
                command.consultationDurationMinutes(),
                command.maxPatientsPerSlot(),
                command.active()
        );
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
        entity.update(
                command.dayOfWeek(),
                command.startTime(),
                command.endTime(),
                command.breakStartTime(),
                command.breakEndTime(),
                command.consultationDurationMinutes(),
                command.maxPatientsPerSlot(),
                command.active()
        );
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
        entity.update(
                entity.getDayOfWeek(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getBreakStartTime(),
                entity.getBreakEndTime(),
                entity.getConsultationDurationMinutes(),
                entity.getMaxPatientsPerSlot(),
                false
        );
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
    public DoctorAvailabilityRecord findAvailability(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        DoctorAvailabilityEntity entity = doctorAvailabilityRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor availability not found"));
        return toRecord(entity);
    }

    @Transactional
    public boolean ensureDoctorCalendarExists(UUID tenantId, UUID doctorUserId, UUID actorAppUserId, String action) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        ensureDoctorInTenant(tenantId, doctorUserId);
        if (doctorAvailabilityRepository.existsByTenantIdAndDoctorUserId(tenantId, doctorUserId)) {
            return false;
        }
        DoctorAvailabilityEntity entity = DoctorAvailabilityEntity.create(tenantId, doctorUserId);
        entity.update(
                DEFAULT_CALENDAR_DAY,
                DEFAULT_CALENDAR_START,
                DEFAULT_CALENDAR_END,
                null,
                null,
                DEFAULT_SLOT_DURATION_MINUTES,
                1,
                false
        );
        DoctorAvailabilityEntity saved = doctorAvailabilityRepository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AVAILABILITY_ENTITY,
                saved.getId(),
                "doctor.calendar.autocreated",
                actorAppUserId,
                OffsetDateTime.now(),
                "Auto-created doctor calendar",
                "{\"doctorUserId\":\"" + doctorUserId + "\",\"trigger\":\"" + normalizeNullable(action) + "\"}"
        ));
        return true;
    }

    @Transactional
    public DoctorCalendarReconcileResult reconcileDoctorCalendars(UUID tenantId, UUID actorAppUserId, String action) {
        requireTenant(tenantId);
        int created = 0;
        int skipped = 0;
        List<TenantUserRecord> users = tenantUserManagementService.list(tenantId);
        for (TenantUserRecord user : users) {
            if (user.appUserId() == null) {
                continue;
            }
            if (!"DOCTOR".equalsIgnoreCase(user.membershipRole())) {
                continue;
            }
            if (!"ACTIVE".equalsIgnoreCase(user.membershipStatus())) {
                skipped++;
                continue;
            }
            boolean createdNow = ensureDoctorCalendarExists(tenantId, user.appUserId(), actorAppUserId, action);
            if (createdNow) {
                created++;
            } else {
                skipped++;
            }
        }
        DoctorCalendarReconcileResult result = new DoctorCalendarReconcileResult(tenantId, created, skipped, OffsetDateTime.now());
        org.slf4j.LoggerFactory.getLogger(AppointmentService.class).info(
                "Doctor calendar reconciliation completed. tenantId={}, createdCount={}, skippedCount={}",
                tenantId,
                created,
                skipped
        );
        return result;
    }

    @Transactional
    public void deactivateDoctorCalendar(UUID tenantId, UUID doctorUserId, UUID actorAppUserId, String action) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        List<DoctorAvailabilityEntity> rows = doctorAvailabilityRepository.findByTenantIdAndDoctorUserIdOrderByDayOfWeekAscStartTimeAsc(tenantId, doctorUserId);
        boolean changed = false;
        for (DoctorAvailabilityEntity entity : rows) {
            if (!entity.isActive()) {
                continue;
            }
            entity.update(
                    entity.getDayOfWeek(),
                    entity.getStartTime(),
                    entity.getEndTime(),
                    entity.getBreakStartTime(),
                    entity.getBreakEndTime(),
                    entity.getConsultationDurationMinutes(),
                    entity.getMaxPatientsPerSlot(),
                    false
            );
            doctorAvailabilityRepository.save(entity);
            changed = true;
        }
        if (changed) {
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    AVAILABILITY_ENTITY,
                    null,
                    "doctor.calendar.deactivated",
                    actorAppUserId,
                    OffsetDateTime.now(),
                    "Deactivated doctor calendar",
                    "{\"doctorUserId\":\"" + doctorUserId + "\",\"trigger\":\"" + normalizeNullable(action) + "\"}"
            ));
        }
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
        return mapAppointments(tenantId, appointmentRepository.findByTenantIdAndAppointmentDateOrderByAppointmentTimeAscCreatedAtAsc(tenantId, LocalDate.now()))
                .stream()
                .filter(record -> record.status() != AppointmentStatus.CANCELLED)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRecord> listQueueToday(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        return sortQueueRecords(mapAppointments(tenantId, appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(
                tenantId,
                doctorUserId,
                LocalDate.now()
        )));
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
    public AppointmentRecord createScheduled(UUID tenantId, AppointmentUpsertCommand command, UUID actorAppUserId, boolean allowOverbooking) {
        requireTenant(tenantId);
        validateAppointment(command);
        validateNotPast(command.appointmentDate(), command.appointmentTime());
        ensurePatientInTenant(tenantId, command.patientId());
        ensureDoctorInTenant(tenantId, command.doctorUserId());
        ensureNoDuplicateActiveAppointment(tenantId, command.patientId(), command.doctorUserId(), command.appointmentDate(), command.appointmentTime());
        ensureScheduledSlotAvailable(tenantId, command, allowOverbooking);

        AppointmentEntity entity = AppointmentEntity.create(tenantId, command.patientId(), command.doctorUserId());
        AppointmentType type = command.type() == null ? AppointmentType.SCHEDULED : command.type();
        AppointmentStatus status = type == AppointmentType.WALK_IN
                ? AppointmentStatus.WAITING
                : (command.status() == null ? AppointmentStatus.BOOKED : command.status());
        Integer token = type == AppointmentType.WALK_IN ? nextToken(tenantId, command.doctorUserId(), command.appointmentDate()) : null;
        entity.update(command.appointmentDate(), command.appointmentTime(), token, normalizeNullable(command.reason()), type, status, AppointmentPriority.fromNullable(command.priority()));
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
    public AppointmentRecord createWalkIn(UUID tenantId, WalkInAppointmentCommand command, UUID actorAppUserId, boolean allowOverbooking) {
        requireTenant(tenantId);
        validateWalkIn(command);
        validateNotPast(command.appointmentDate(), null);
        ensurePatientInTenant(tenantId, command.patientId());
        ensureDoctorInTenant(tenantId, command.doctorUserId());

        AppointmentEntity entity = AppointmentEntity.create(tenantId, command.patientId(), command.doctorUserId());
        Integer token = nextToken(tenantId, command.doctorUserId(), command.appointmentDate());
        entity.update(command.appointmentDate(), null, token, normalizeNullable(command.reason()), AppointmentType.WALK_IN, AppointmentStatus.WAITING, AppointmentPriority.fromNullable(command.priority()));
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
        String normalizedComment = normalizeNullable(command.comment());
        if (entity.getStatus() == AppointmentStatus.WAITING
                && (command.status() == AppointmentStatus.CANCELLED || command.status() == AppointmentStatus.NO_SHOW)
                && !StringUtils.hasText(normalizedComment)) {
            throw new IllegalArgumentException("Reason/comment is required to cancel or mark no-show after check-in");
        }
        entity.update(
                entity.getAppointmentDate(),
                entity.getAppointmentTime(),
                entity.getTokenNumber(),
                normalizedComment == null ? entity.getReason() : normalizedComment,
                entity.getType(),
                command.status(),
                entity.getPriority()
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

    @Transactional
    public AppointmentRecord updatePriority(UUID tenantId, UUID id, AppointmentPriority priority, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        if (priority == null) {
            throw new IllegalArgumentException("priority is required");
        }

        AppointmentEntity entity = appointmentRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        entity.update(
                entity.getAppointmentDate(),
                entity.getAppointmentTime(),
                entity.getTokenNumber(),
                entity.getReason(),
                entity.getType(),
                entity.getStatus(),
                priority
        );
        AppointmentEntity saved = appointmentRepository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                APPOINTMENT_ENTITY,
                saved.getId(),
                "appointment.priority.updated",
                actorAppUserId,
                OffsetDateTime.now(),
                "Updated appointment priority",
                detailsJson(saved)
        ));
        return toRecord(saved, tenantUsersById(tenantId), patientsByIds(tenantId, List.of(saved.getPatientId())));
    }

    @Transactional
    public List<AppointmentRecord> reorderQueueToday(UUID tenantId, UUID doctorUserId, List<UUID> orderedAppointmentIds, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        if (orderedAppointmentIds == null || orderedAppointmentIds.isEmpty()) {
            throw new IllegalArgumentException("orderedAppointmentIds is required");
        }
        List<AppointmentEntity> queue = appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(
                tenantId,
                doctorUserId,
                LocalDate.now()
        );
        List<AppointmentEntity> reorderable = queue.stream()
                .filter(entity -> entity.getStatus() == AppointmentStatus.BOOKED || entity.getStatus() == AppointmentStatus.WAITING)
                .toList();
        Set<UUID> expected = reorderable.stream().map(AppointmentEntity::getId).collect(Collectors.toSet());
        Set<UUID> requested = new java.util.LinkedHashSet<>(orderedAppointmentIds);
        if (!expected.equals(requested) || orderedAppointmentIds.size() != expected.size()) {
            throw new IllegalArgumentException("Queue reorder request must include all and only active reorderable queue items");
        }
        Map<UUID, AppointmentEntity> byId = reorderable.stream().collect(Collectors.toMap(AppointmentEntity::getId, Function.identity()));
        int token = 1;
        for (UUID appointmentId : orderedAppointmentIds) {
            AppointmentEntity entity = byId.get(appointmentId);
            entity.update(
                    entity.getAppointmentDate(),
                    entity.getAppointmentTime(),
                    token++,
                    entity.getReason(),
                    entity.getType(),
                    entity.getStatus(),
                    entity.getPriority()
            );
            appointmentRepository.save(entity);
        }
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                APPOINTMENT_ENTITY,
                null,
                "appointment.queue.reordered",
                actorAppUserId,
                OffsetDateTime.now(),
                "Reordered doctor queue",
                "{\"doctorUserId\":\"" + doctorUserId + "\"}"
        ));
        return listQueueToday(tenantId, doctorUserId);
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
                patient == null ? null : patient.getMobile(),
                entity.getDoctorUserId(),
                doctor == null ? null : doctor.displayName(),
                null,
                entity.getAppointmentDate(),
                entity.getAppointmentTime(),
                entity.getTokenNumber(),
                entity.getReason(),
                entity.getType(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<AppointmentRecord> sortQueueRecords(List<AppointmentRecord> records) {
        return records.stream()
                .sorted((left, right) -> {
                    int tierCompare = Integer.compare(queueTier(left.status()), queueTier(right.status()));
                    if (tierCompare != 0) {
                        return tierCompare;
                    }
                    if (left.status() == AppointmentStatus.WAITING || left.status() == AppointmentStatus.BOOKED) {
                        int priorityCompare = Integer.compare(priorityRank(left.priority()), priorityRank(right.priority()));
                        if (priorityCompare != 0) {
                            return priorityCompare;
                        }
                    }
                    int tokenCompare = Integer.compare(
                            left.tokenNumber() == null ? Integer.MAX_VALUE : left.tokenNumber(),
                            right.tokenNumber() == null ? Integer.MAX_VALUE : right.tokenNumber()
                    );
                    if (tokenCompare != 0) {
                        return tokenCompare;
                    }
                    if (left.appointmentTime() != null && right.appointmentTime() != null) {
                        int timeCompare = left.appointmentTime().compareTo(right.appointmentTime());
                        if (timeCompare != 0) {
                            return timeCompare;
                        }
                    }
                    return left.createdAt().compareTo(right.createdAt());
                })
                .toList();
    }

    private int queueTier(AppointmentStatus status) {
        return switch (status) {
            case WAITING -> 0;
            case BOOKED -> 1;
            case IN_CONSULTATION -> 2;
            case COMPLETED, CANCELLED, NO_SHOW -> 3;
        };
    }

    private int priorityRank(AppointmentPriority priority) {
        return AppointmentPriority.fromNullable(priority).rank();
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
                entity.getBreakStartTime(),
                entity.getBreakEndTime(),
                entity.getConsultationDurationMinutes(),
                entity.getMaxPatientsPerSlot(),
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
                entity.getBreakStartTime(),
                entity.getBreakEndTime(),
                entity.getConsultationDurationMinutes(),
                entity.getMaxPatientsPerSlot(),
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
        if (command.type() != AppointmentType.WALK_IN && command.appointmentTime() == null) {
            throw new IllegalArgumentException("appointmentTime is required");
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
        if (command.breakStartTime() != null || command.breakEndTime() != null) {
            if (command.breakStartTime() == null || command.breakEndTime() == null) {
                throw new IllegalArgumentException("breakStartTime and breakEndTime must both be provided");
            }
            if (!command.breakStartTime().isBefore(command.breakEndTime())) {
                throw new IllegalArgumentException("breakStartTime must be before breakEndTime");
            }
            if (command.breakStartTime().isBefore(command.startTime()) || command.breakEndTime().isAfter(command.endTime())) {
                throw new IllegalArgumentException("break must fall within the working hours");
            }
        }
        if (command.maxPatientsPerSlot() != null && command.maxPatientsPerSlot() <= 0) {
            throw new IllegalArgumentException("maxPatientsPerSlot must be greater than zero");
        }
    }

    private void ensureScheduledSlotAvailable(UUID tenantId, AppointmentUpsertCommand command, boolean allowOverbooking) {
        if (command.type() == AppointmentType.WALK_IN) {
            return;
        }
        List<DoctorAvailabilitySlotRecord> slots = listSlots(tenantId, command.doctorUserId(), command.appointmentDate());
        if (slots.isEmpty()) {
            return;
        }
        DoctorAvailabilitySlotRecord slot = slots.stream()
                .filter(candidate -> candidate.slotTime().equals(command.appointmentTime()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected time is not available for the selected doctor"));
        if (slot.status() == DoctorAvailabilitySlotStatus.UNAVAILABLE) {
            throw new IllegalArgumentException("Selected time is unavailable for the selected doctor");
        }
        if (!slot.selectable() && !allowOverbooking) {
            throw new IllegalArgumentException("Selected time is already fully booked");
        }
        if (!StringUtils.hasText(command.reason()) && allowOverbooking && slot.bookedCount() >= slot.maxPatientsPerSlot()) {
            throw new IllegalArgumentException("Reason is required when overbooking a slot");
        }
    }

    private void ensureNoDuplicateActiveAppointment(UUID tenantId, UUID patientId, UUID doctorUserId, LocalDate appointmentDate, LocalTime appointmentTime) {
        boolean duplicate = appointmentRepository.existsByTenantIdAndDoctorUserIdAndPatientIdAndAppointmentDateAndAppointmentTimeAndStatusNotIn(
                tenantId,
                doctorUserId,
                patientId,
                appointmentDate,
                appointmentTime,
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)
        );
        if (duplicate) {
            throw new IllegalArgumentException("An active appointment already exists for the same patient, doctor, date, and time");
        }
    }

    private void validateNotPast(LocalDate appointmentDate, LocalTime appointmentTime) {
        LocalDate today = LocalDate.now();
        if (appointmentDate.isBefore(today)) {
            throw new IllegalArgumentException("Appointment date/time cannot be in the past");
        }
        if (appointmentDate.isEqual(today) && appointmentTime != null && !appointmentTime.isAfter(LocalTime.now())) {
            throw new IllegalArgumentException("Appointment date/time cannot be in the past");
        }
    }

    private boolean isWithinBreak(LocalTime slotStart, LocalTime breakStart, LocalTime breakEnd) {
        if (breakStart == null || breakEnd == null) {
            return false;
        }
        return !slotStart.isBefore(breakStart) && slotStart.isBefore(breakEnd);
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
        details.put("breakStartTime", entity.getBreakStartTime());
        details.put("breakEndTime", entity.getBreakEndTime());
        details.put("consultationDurationMinutes", entity.getConsultationDurationMinutes());
        details.put("maxPatientsPerSlot", entity.getMaxPatientsPerSlot());
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
        details.put("priority", entity.getPriority());
        details.put("status", entity.getStatus());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
