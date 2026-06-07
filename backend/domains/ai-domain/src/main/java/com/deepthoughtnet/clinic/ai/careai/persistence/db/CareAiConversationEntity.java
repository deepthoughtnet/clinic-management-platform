package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "careai_conversations", indexes = {
        @Index(name = "ix_careai_conversations_tenant_status_updated", columnList = "tenant_id,status,updated_at"),
        @Index(name = "ix_careai_conversations_tenant_patient", columnList = "tenant_id,patient_id"),
        @Index(name = "ix_careai_conversations_tenant_external_session", columnList = "tenant_id,external_session_id")
})
public class CareAiConversationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "current_workflow_id")
    private UUID currentWorkflowId;

    @Column(name = "external_session_id", length = 128)
    private String externalSessionId;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected CareAiConversationEntity() {
    }

    public static CareAiConversationEntity create(
            UUID tenantId,
            String channel,
            UUID patientId,
            UUID leadId,
            String externalSessionId
    ) {
        CareAiConversationEntity entity = new CareAiConversationEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.channel = channel;
        entity.patientId = patientId;
        entity.leadId = leadId;
        entity.externalSessionId = externalSessionId;
        entity.status = CareAiConversationStatus.ACTIVE.name();
        entity.metadataJson = new LinkedHashMap<>();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void applyStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
        if (!CareAiConversationStatus.ACTIVE.name().equals(status)) {
            this.completedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getChannel() { return channel; }
    public UUID getPatientId() { return patientId; }
    public UUID getLeadId() { return leadId; }
    public UUID getAppointmentId() { return appointmentId; }
    public String getStatus() { return status; }
    public UUID getCurrentWorkflowId() { return currentWorkflowId; }
    public String getExternalSessionId() { return externalSessionId; }
    public String getSummary() { return summary; }
    public String getMetadataJson() { return CareAiJsonSupport.writeObject(metadataJson); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }

    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public void setLeadId(UUID leadId) { this.leadId = leadId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }
    public void setCurrentWorkflowId(UUID currentWorkflowId) { this.currentWorkflowId = currentWorkflowId; }
    public void setExternalSessionId(String externalSessionId) { this.externalSessionId = externalSessionId; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = CareAiJsonSupport.parseObject(metadataJson); }
}
