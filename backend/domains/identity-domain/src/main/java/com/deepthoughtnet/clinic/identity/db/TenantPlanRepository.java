package com.deepthoughtnet.clinic.identity.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPlanRepository extends JpaRepository<TenantPlanEntity, String> {}
