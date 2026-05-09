package com.deepthoughtnet.clinic.identity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.LinkedHashMap;
import java.util.Map;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> features = new LinkedHashMap<>();

    protected TenantPlanEntity() {}

    public static TenantPlanEntity create(String id, String name, Map<String, Object> features) {
        TenantPlanEntity entity = new TenantPlanEntity();
        entity.id = id;
        entity.name = name;
        entity.features = features == null ? new LinkedHashMap<>() : new LinkedHashMap<>(features);
        return entity;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Integer getMaxDrivers() { return maxDrivers; }
    public Integer getMaxDevices() { return maxDevices; }
    public Integer getMaxRoutes() { return maxRoutes; }
    public Map<String, Object> getFeatures() { return features; }
}
