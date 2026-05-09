package com.deepthoughtnet.clinic.clinic.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfileEntity, UUID> {
    Optional<DoctorProfileEntity> findByTenantIdAndDoctorUserId(UUID tenantId, UUID doctorUserId);
}
