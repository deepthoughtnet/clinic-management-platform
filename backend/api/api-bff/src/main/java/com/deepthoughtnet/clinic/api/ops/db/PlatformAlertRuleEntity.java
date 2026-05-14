package com.deepthoughtnet.clinic.api.ops.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Configurable alert thresholds for automated observability rules. */
@Entity
@Table(name = "platform_alert_rules", indexes = {
        @Index(name = "ix_platform_alert_rules_tenant_source", columnList = "tenant_id,source_type,enabled")
})
public class PlatformAlertRuleEntity {
    public enum ThresholdType { COUNT, RATE, LATENCY, PERCENTAGE, STALENESS }

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "rule_key", nullable = false, length = 120)
    private String ruleKey;

    @Column(name = "source_type", nullable = false, length = 80)
    private String sourceType;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformOperationalAlertEntity.Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_type", nullable = false, length = 20)
    private ThresholdType thresholdType;

    @Column(name = "threshold_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes;

    @Column(name = "auto_resolve_enabled", nullable = false)
    private boolean autoResolveEnabled;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlatformAlertRuleEntity() {
    }

    public static PlatformAlertRuleEntity defaults(String ruleKey, String sourceType, PlatformOperationalAlertEntity.Severity severity,
                                                   ThresholdType thresholdType, BigDecimal thresholdValue, int cooldownMinutes) {
        PlatformAlertRuleEntity entity = new PlatformAlertRuleEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = null;
        entity.ruleKey = ruleKey;
        entity.sourceType = sourceType;
        entity.enabled = true;
        entity.severity = severity;
        entity.thresholdType = thresholdType;
        entity.thresholdValue = thresholdValue;
        entity.cooldownMinutes = cooldownMinutes;
        entity.autoResolveEnabled = true;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getRuleKey() { return ruleKey; }
    public String getSourceType() { return sourceType; }
    public boolean isEnabled() { return enabled; }
    public PlatformOperationalAlertEntity.Severity getSeverity() { return severity; }
    public ThresholdType getThresholdType() { return thresholdType; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public boolean isAutoResolveEnabled() { return autoResolveEnabled; }
}
