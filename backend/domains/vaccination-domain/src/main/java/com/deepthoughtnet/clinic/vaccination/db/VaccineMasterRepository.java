package com.deepthoughtnet.clinic.vaccination.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaccineMasterRepository extends JpaRepository<VaccineMasterEntity, UUID> {
    Optional<VaccineMasterEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<VaccineMasterEntity> findByTenantIdAndVaccineNameIgnoreCase(UUID tenantId, String vaccineName);

    List<VaccineMasterEntity> findByTenantIdOrderByVaccineNameAsc(UUID tenantId);
}
