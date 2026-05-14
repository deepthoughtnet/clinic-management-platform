package com.deepthoughtnet.clinic.api.ops.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Escalation tracking foundation for future pager/on-call integrations. */
@Entity
@Table(name = "platform_alert_escalations", indexes = {
        @Index(name = "ix_platform_alert_escalations_alert", columnList = "alert_id,escalation_level")
})
public class PlatformAlertEscalationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "escalation_status", nullable = false, length = 30)
    private String escalationStatus;

    @Column(name = "escalation_target", nullable = false, length = 160)
    private String escalationTarget;

    @Column(name = "escalated_at", nullable = false)
    private OffsetDateTime escalatedAt;

    protected PlatformAlertEscalationEntity() {
    }

    public static PlatformAlertEscalationEntity raised(UUID alertId, int level, String target) {
        PlatformAlertEscalationEntity row = new PlatformAlertEscalationEntity();
        row.id = UUID.randomUUID();
        row.alertId = alertId;
        row.escalationLevel = level;
        row.escalationStatus = "OPEN";
        row.escalationTarget = target;
        row.escalatedAt = OffsetDateTime.now();
        return row;
    }
}
