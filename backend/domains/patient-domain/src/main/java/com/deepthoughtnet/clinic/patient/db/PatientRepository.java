package com.deepthoughtnet.clinic.patient.db;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PatientRepository extends JpaRepository<PatientEntity, UUID> {
    Optional<PatientEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<PatientEntity> findByTenantIdAndPatientNumber(UUID tenantId, String patientNumber);

    Optional<PatientEntity> findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(UUID tenantId, String mobile);

    boolean existsByTenantIdAndPatientNumber(UUID tenantId, String patientNumber);

    List<PatientEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    @Query("""
            select p
            from PatientEntity p
            where p.tenantId = :tenantId
              and (:patientNumber is null or lower(p.patientNumber) = lower(:patientNumber))
              and (:mobile is null or lower(p.mobile) = lower(:mobile))
              and (:active is null or p.active = :active)
              and (
                    :name is null
                    or lower(concat(coalesce(p.firstName, ''), ' ', coalesce(p.lastName, ''))) like lower(concat('%', :name, '%'))
                    or lower(p.firstName) like lower(concat('%', :name, '%'))
                    or lower(p.lastName) like lower(concat('%', :name, '%'))
              )
            order by p.createdAt desc
            """)
    List<PatientEntity> search(
            UUID tenantId,
            String patientNumber,
            String mobile,
            String name,
            Boolean active
    );
}
