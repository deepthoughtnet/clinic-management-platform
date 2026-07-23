package com.deepthoughtnet.clinic.appointment.service;

import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentReminderSnapshot;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only reminder snapshot contract used by notification processing to
 * validate whether a reminder still matches the current appointment lifecycle.
 */
@Service
public class AppointmentReminderReadService {
    private final AppointmentRepository appointmentRepository;
    private final TenantUserManagementService tenantUserManagementService;

    public AppointmentReminderReadService(
            AppointmentRepository appointmentRepository,
            TenantUserManagementService tenantUserManagementService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.tenantUserManagementService = tenantUserManagementService;
    }

    @Transactional(readOnly = true)
    public Optional<AppointmentReminderSnapshot> findById(UUID tenantId, UUID appointmentId) {
        if (tenantId == null || appointmentId == null) {
            return Optional.empty();
        }
        return appointmentRepository.findByTenantIdAndId(tenantId, appointmentId)
                .map(entity -> new AppointmentReminderSnapshot(
                        entity.getId(),
                        entity.getTenantId(),
                        entity.getPatientId(),
                        entity.getDoctorUserId(),
                        doctorDisplayName(tenantId, entity.getDoctorUserId()),
                        entity.getAppointmentDate(),
                        entity.getAppointmentTime(),
                        entity.getStatus(),
                        entity.getVersion(),
                        entity.getUpdatedAt()
                ));
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
}
