package com.deepthoughtnet.clinic.appointment.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailabilityEntity, UUID> {
    List<DoctorAvailabilityEntity> findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(UUID tenantId);
    List<DoctorAvailabilityEntity> findByTenantIdAndDoctorUserIdOrderByDayOfWeekAscStartTimeAsc(UUID tenantId, UUID doctorUserId);
    boolean existsByTenantIdAndDoctorUserId(UUID tenantId, UUID doctorUserId);
    boolean existsByTenantIdAndDoctorUserIdAndDayOfWeekAndStartTimeAndEndTime(
            UUID tenantId,
            UUID doctorUserId,
            java.time.DayOfWeek dayOfWeek,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime
    );
    boolean existsByTenantIdAndDoctorUserIdAndDayOfWeekAndStartTimeAndEndTimeAndActiveTrue(
            UUID tenantId,
            UUID doctorUserId,
            java.time.DayOfWeek dayOfWeek,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime
    );
    List<DoctorAvailabilityEntity> findByTenantIdAndDoctorUserIdAndDayOfWeekAndActiveTrueAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAscEndTimeAsc(
            UUID tenantId,
            UUID doctorUserId,
            java.time.DayOfWeek dayOfWeek,
            java.time.LocalTime endTime,
            java.time.LocalTime startTime
    );
    List<DoctorAvailabilityEntity> findByTenantIdAndDoctorUserIdAndDayOfWeekAndActiveTrueAndIdNotAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAscEndTimeAsc(
            UUID tenantId,
            UUID doctorUserId,
            java.time.DayOfWeek dayOfWeek,
            UUID id,
            java.time.LocalTime endTime,
            java.time.LocalTime startTime
    );

    Optional<DoctorAvailabilityEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
