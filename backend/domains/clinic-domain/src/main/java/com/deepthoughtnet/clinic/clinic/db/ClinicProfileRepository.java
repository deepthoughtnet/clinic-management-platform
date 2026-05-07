package com.deepthoughtnet.clinic.clinic.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicProfileRepository extends JpaRepository<ClinicProfileEntity, UUID> {
    Optional<ClinicProfileEntity> findByTenantId(UUID tenantId);
    boolean existsByEmailIgnoreCase(String email);
}
