package com.deepthoughtnet.clinic.clinic.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicProfileRepository extends JpaRepository<ClinicProfileEntity, UUID> {
    Optional<ClinicProfileEntity> findByTenantId(UUID tenantId);
    Optional<ClinicProfileEntity> findBySlugIgnoreCase(String slug);
    java.util.List<ClinicProfileEntity> findAllByActiveTrueOrderByDisplayNameAsc();
    boolean existsByEmailIgnoreCase(String email);
}
