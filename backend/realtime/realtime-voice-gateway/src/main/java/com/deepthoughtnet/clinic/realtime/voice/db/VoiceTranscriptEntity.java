package com.deepthoughtnet.clinic.realtime.voice.db;

import com.deepthoughtnet.clinic.realtime.voice.transcript.SpeakerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Transcript timeline row for realtime session auditing and memory context. */
@Entity
@Table(name = "voice_transcripts", indexes = {
        @Index(name = "ix_voice_transcripts_session_time", columnList = "session_id,transcript_timestamp")
})
public class VoiceTranscriptEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaker_type", nullable = false, length = 24)
    private SpeakerType speakerType;

    @Column(name = "transcript_text", nullable = false, columnDefinition = "text")
    private String transcriptText;

    @Column(name = "transcript_timestamp", nullable = false)
    private OffsetDateTime transcriptTimestamp;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected VoiceTranscriptEntity() {
    }

    public static VoiceTranscriptEntity create(UUID sessionId, SpeakerType speakerType, String text, Double confidence) {
        VoiceTranscriptEntity row = new VoiceTranscriptEntity();
        row.id = UUID.randomUUID();
        row.sessionId = sessionId;
        row.speakerType = speakerType;
        row.transcriptText = text;
        row.confidence = confidence;
        row.transcriptTimestamp = OffsetDateTime.now();
        row.createdAt = row.transcriptTimestamp;
        return row;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public SpeakerType getSpeakerType() { return speakerType; }
    public String getTranscriptText() { return transcriptText; }
    public OffsetDateTime getTranscriptTimestamp() { return transcriptTimestamp; }
    public Double getConfidence() { return confidence; }
}
