package com.deepthoughtnet.clinic.identity.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
    Optional<TenantEntity> findByCode(String code);
    boolean existsByCode(String code);
    List<TenantEntity> findAllByOrderByCreatedAtDesc();
}
