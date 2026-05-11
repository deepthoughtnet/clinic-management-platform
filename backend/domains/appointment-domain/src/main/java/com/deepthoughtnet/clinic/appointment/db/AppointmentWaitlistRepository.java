package com.deepthoughtnet.clinic.appointment.db;

import com.deepthoughtnet.clinic.appointment.service.model.WaitlistStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentWaitlistRepository extends JpaRepository<AppointmentWaitlistEntity, UUID> {
    Optional<AppointmentWaitlistEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AppointmentWaitlistEntity> findByTenantIdAndPreferredDateAndStatusOrderByCreatedAtAsc(UUID tenantId, LocalDate preferredDate, WaitlistStatus status);

    List<AppointmentWaitlistEntity> findByTenantIdAndDoctorUserIdAndPreferredDateAndStatusOrderByCreatedAtAsc(
            UUID tenantId,
            UUID doctorUserId,
            LocalDate preferredDate,
            WaitlistStatus status
    );

    List<AppointmentWaitlistEntity> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, WaitlistStatus status);
}
