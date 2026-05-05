package com.deepthoughtnet.clinic.identity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_plans")
public class TenantPlanEntity {

    @Id
    @Column(nullable = false, length = 32)
    private String id; // TRIAL, BASIC, PRO, ENTERPRISE

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "max_drivers")
    private Integer maxDrivers;

    @Column(name = "max_devices")
    private Integer maxDevices;

    @Column(name = "max_routes")
    private Integer maxRoutes;

    /**
     * Stored as JSONB in Postgres. We keep it as String in JPA to avoid extra deps.
     */
    @Column(nullable = false, columnDefinition = "jsonb")
    private String features = "{}";

    protected TenantPlanEntity() {}

    public String getId() { return id; }
    public String getName() { return name; }
    public Integer getMaxDrivers() { return maxDrivers; }
    public Integer getMaxDevices() { return maxDevices; }
    public Integer getMaxRoutes() { return maxRoutes; }
    public String getFeatures() { return features; }
}
