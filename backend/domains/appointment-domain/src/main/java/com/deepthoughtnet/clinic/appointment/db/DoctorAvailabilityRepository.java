package com.deepthoughtnet.clinic.appointment.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailabilityEntity, UUID> {
    List<DoctorAvailabilityEntity> findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(UUID tenantId);
    List<DoctorAvailabilityEntity> findByTenantIdAndDoctorUserIdOrderByDayOfWeekAscStartTimeAsc(UUID tenantId, UUID doctorUserId);
    boolean existsByTenantIdAndDoctorUserId(UUID tenantId, UUID doctorUserId);

    Optional<DoctorAvailabilityEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
