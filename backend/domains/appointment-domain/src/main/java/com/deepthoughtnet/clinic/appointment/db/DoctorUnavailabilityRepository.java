package com.deepthoughtnet.clinic.appointment.db;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorUnavailabilityRepository extends JpaRepository<DoctorUnavailabilityEntity, UUID> {
    List<DoctorUnavailabilityEntity> findByTenantIdAndDoctorUserIdOrderByStartAtAsc(UUID tenantId, UUID doctorUserId);

    List<DoctorUnavailabilityEntity> findByTenantIdAndDoctorUserIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(
            UUID tenantId,
            UUID doctorUserId,
            OffsetDateTime windowEnd,
            OffsetDateTime windowStart
    );

    Optional<DoctorUnavailabilityEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
