package com.deepthoughtnet.clinic.carepilot.ai_call.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Persistence entity for AI call transcript foundation data. */
@Entity
@Table(name = "carepilot_ai_call_transcripts", indexes = {
        @Index(name = "ix_cp_ai_call_transcripts_tenant_execution", columnList = "tenant_id,execution_id"),
        @Index(name = "ix_cp_ai_call_transcripts_tenant_created", columnList = "tenant_id,created_at")
})
public class AiCallTranscriptEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "transcript_text", columnDefinition = "text")
    private String transcriptText;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(length = 24)
    private String sentiment;

    @Column(length = 48)
    private String outcome;

    @Column(length = 64)
    private String intent;

    @Column(name = "requires_follow_up", nullable = false)
    private boolean requiresFollowUp;

    @Column(name = "escalation_reason", columnDefinition = "text")
    private String escalationReason;

    @Column(name = "extracted_entities_json", columnDefinition = "text")
    private String extractedEntitiesJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AiCallTranscriptEntity() {}

    public static AiCallTranscriptEntity create(
            UUID tenantId,
            UUID executionId,
            String transcriptText,
            String summary,
            String sentiment,
            String outcome,
            String intent,
            boolean requiresFollowUp
    ) {
        AiCallTranscriptEntity entity = new AiCallTranscriptEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.executionId = executionId;
        entity.transcriptText = transcriptText;
        entity.summary = summary;
        entity.sentiment = sentiment;
        entity.outcome = outcome;
        entity.intent = intent;
        entity.requiresFollowUp = requiresFollowUp;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void enrich(String summary, String sentiment, String outcome, String intent, boolean requiresFollowUp, String escalationReason, String extractedEntitiesJson) {
        this.summary = summary;
        this.sentiment = sentiment;
        this.outcome = outcome;
        this.intent = intent;
        this.requiresFollowUp = requiresFollowUp;
        this.escalationReason = escalationReason;
        this.extractedEntitiesJson = extractedEntitiesJson;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getExecutionId() { return executionId; }
    public String getTranscriptText() { return transcriptText; }
    public String getSummary() { return summary; }
    public String getSentiment() { return sentiment; }
    public String getOutcome() { return outcome; }
    public String getIntent() { return intent; }
    public boolean isRequiresFollowUp() { return requiresFollowUp; }
    public String getEscalationReason() { return escalationReason; }
    public String getExtractedEntitiesJson() { return extractedEntitiesJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
