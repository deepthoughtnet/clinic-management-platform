package com.deepthoughtnet.clinic.appointment.service;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentReminderSnapshot;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Publishes durable appointment-reminder business events from the appointment
 * module without sending notifications directly.
 */
@Service
public class AppointmentReminderEventService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderEventService.class);

    private final AppointmentRepository appointmentRepository;
    private final TenantUserManagementService tenantUserManagementService;
    private final ModuleBusinessEventPublisher moduleBusinessEventPublisher;

    public AppointmentReminderEventService(
            AppointmentRepository appointmentRepository,
            TenantUserManagementService tenantUserManagementService,
            ModuleBusinessEventPublisher moduleBusinessEventPublisher
    ) {
        this.appointmentRepository = appointmentRepository;
        this.tenantUserManagementService = tenantUserManagementService;
        this.moduleBusinessEventPublisher = moduleBusinessEventPublisher;
    }

    @Transactional
    public ReminderPublicationSummary publishDueReminders(
            UUID tenantId,
            ZoneId bookingZone,
            Duration reminderOffset,
            Duration gracePeriod,
            UUID actorAppUserId
    ) {
        requireTenant(tenantId);
        ZoneId zone = bookingZone == null ? ZoneId.of("Asia/Kolkata") : bookingZone;
        Duration offset = reminderOffset == null || reminderOffset.isNegative() || reminderOffset.isZero()
                ? Duration.ofHours(24)
                : reminderOffset;
        Duration grace = gracePeriod == null || gracePeriod.isNegative()
                ? Duration.ofMinutes(30)
                : gracePeriod;
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate windowStart = now.toLocalDate().minusDays(1);
        LocalDate windowEnd = now.toLocalDate().plusDays(2);
        Specification<AppointmentEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId),
                cb.equal(root.get("status"), AppointmentStatus.BOOKED),
                cb.between(root.get("appointmentDate"), windowStart, windowEnd)
        );
        List<AppointmentEntity> candidates = appointmentRepository.findAll(spec, Sort.by(
                Sort.Order.asc("appointmentDate"),
                Sort.Order.asc("appointmentTime"),
                Sort.Order.asc("createdAt")
        ));

        int published = 0;
        int skipped = 0;
        for (AppointmentEntity entity : candidates) {
            if (!isDue(now, zone, entity.getAppointmentDate(), entity.getAppointmentTime(), offset, grace)) {
                skipped++;
                continue;
            }
            String doctorDisplayName = doctorDisplayName(tenantId, entity.getDoctorUserId());
            moduleBusinessEventPublisher.publish(AppointmentBookedEvent.reminderDue(
                    tenantId,
                    entity.getId(),
                    entity.getPatientId(),
                    entity.getDoctorUserId(),
                    doctorDisplayName,
                    null,
                    entity.getAppointmentDate(),
                    entity.getAppointmentTime(),
                    zone.getId(),
                    offset.toString(),
                    entity.getVersion(),
                    actorAppUserId
            ));
            published++;
            log.info(
                    "appointment_reminder_due_published tenantId={} appointmentId={} patientId={} doctorUserId={} appointmentDate={} appointmentTime={} reminderWindow={} version={}",
                    tenantId,
                    entity.getId(),
                    entity.getPatientId(),
                    entity.getDoctorUserId(),
                    entity.getAppointmentDate(),
                    entity.getAppointmentTime(),
                    offset,
                    entity.getVersion()
            );
        }
        return new ReminderPublicationSummary(published, skipped);
    }

    private boolean isDue(
            ZonedDateTime now,
            ZoneId zone,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            Duration reminderOffset,
            Duration gracePeriod
    ) {
        if (appointmentDate == null || appointmentTime == null) {
            return false;
        }
        ZonedDateTime appointmentStart = ZonedDateTime.of(appointmentDate, appointmentTime, zone);
        ZonedDateTime reminderTime = appointmentStart.minus(reminderOffset);
        Duration lag = Duration.between(reminderTime, now);
        return !lag.isNegative() && lag.compareTo(gracePeriod) <= 0;
    }

    private Map<UUID, TenantUserRecord> tenantUsersById(UUID tenantId) {
        if (tenantId == null) {
            return Map.of();
        }
        return tenantUserManagementService.list(tenantId).stream()
                .collect(Collectors.toMap(TenantUserRecord::appUserId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private String doctorDisplayName(UUID tenantId, UUID doctorUserId) {
        TenantUserRecord doctor = tenantUsersById(tenantId).get(doctorUserId);
        return doctor == null ? null : doctor.displayName();
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    public record ReminderPublicationSummary(int publishedCount, int skippedCount) {
    }
}
