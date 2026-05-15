package com.deepthoughtnet.clinic.realtime.voice.db;

import com.deepthoughtnet.clinic.realtime.voice.session.VoiceSessionStatus;
import com.deepthoughtnet.clinic.realtime.voice.session.VoiceSessionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Tenant-safe realtime voice session aggregate root. */
@Entity
@Table(name = "voice_sessions", indexes = {
        @Index(name = "ix_voice_sessions_tenant_status_started", columnList = "tenant_id,session_status,started_at")
})
public class VoiceSessionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 64)
    private VoiceSessionType sessionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 32)
    private VoiceSessionStatus sessionStatus;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "escalation_required", nullable = false)
    private boolean escalationRequired;

    @Column(name = "escalation_reason", columnDefinition = "text")
    private String escalationReason;

    @Column(name = "assigned_human_user_id")
    private UUID assignedHumanUserId;

    @Column(name = "ai_provider", length = 80)
    private String aiProvider;

    @Column(name = "stt_provider", length = 80)
    private String sttProvider;

    @Column(name = "tts_provider", length = 80)
    private String ttsProvider;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected VoiceSessionEntity() {
    }

    public static VoiceSessionEntity create(UUID tenantId, VoiceSessionType type, UUID patientId, UUID leadId,
                                            String aiProvider, String sttProvider, String ttsProvider, String metadataJson) {
        VoiceSessionEntity row = new VoiceSessionEntity();
        row.id = UUID.randomUUID();
        row.tenantId = tenantId;
        row.sessionType = type;
        row.sessionStatus = VoiceSessionStatus.CREATED;
        row.patientId = patientId;
        row.leadId = leadId;
        row.startedAt = OffsetDateTime.now();
        row.aiProvider = aiProvider;
        row.sttProvider = sttProvider;
        row.ttsProvider = ttsProvider;
        row.metadataJson = metadataJson;
        row.createdAt = row.startedAt;
        return row;
    }

    public void markActive() { this.sessionStatus = VoiceSessionStatus.ACTIVE; }
    public void markFailed(String reason) {
        this.sessionStatus = VoiceSessionStatus.FAILED;
        this.escalationRequired = true;
        this.escalationReason = reason;
        this.endedAt = OffsetDateTime.now();
    }
    public void markEscalated(String reason) {
        this.sessionStatus = VoiceSessionStatus.ESCALATED;
        this.escalationRequired = true;
        this.escalationReason = reason;
    }
    public void markCompleted() {
        this.sessionStatus = VoiceSessionStatus.COMPLETED;
        this.endedAt = OffsetDateTime.now();
    }
    public void assignHuman(UUID userId) { this.assignedHumanUserId = userId; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public VoiceSessionType getSessionType() { return sessionType; }
    public VoiceSessionStatus getSessionStatus() { return sessionStatus; }
    public UUID getPatientId() { return patientId; }
    public UUID getLeadId() { return leadId; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public boolean isEscalationRequired() { return escalationRequired; }
    public String getEscalationReason() { return escalationReason; }
    public UUID getAssignedHumanUserId() { return assignedHumanUserId; }
    public String getAiProvider() { return aiProvider; }
    public String getSttProvider() { return sttProvider; }
    public String getTtsProvider() { return ttsProvider; }
    public String getMetadataJson() { return metadataJson; }
}
