package com.deepthoughtnet.clinic.realtime.voice.db;

import com.deepthoughtnet.clinic.realtime.voice.events.VoiceSessionEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ordered event stream row for voice session observability and replay. */
@Entity
@Table(name = "voice_session_events", indexes = {
        @Index(name = "ix_voice_session_events_session_seq", columnList = "session_id,sequence_number")
})
public class VoiceSessionEventEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private VoiceSessionEventType eventType;

    @Column(name = "event_timestamp", nullable = false)
    private OffsetDateTime eventTimestamp;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "payload_summary", columnDefinition = "text")
    private String payloadSummary;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected VoiceSessionEventEntity() {
    }

    public static VoiceSessionEventEntity create(UUID sessionId, VoiceSessionEventType eventType, long sequenceNumber,
                                                 String payloadSummary, String correlationId) {
        VoiceSessionEventEntity row = new VoiceSessionEventEntity();
        row.id = UUID.randomUUID();
        row.sessionId = sessionId;
        row.eventType = eventType;
        row.sequenceNumber = sequenceNumber;
        row.payloadSummary = payloadSummary;
        row.correlationId = correlationId;
        row.eventTimestamp = OffsetDateTime.now();
        row.createdAt = row.eventTimestamp;
        return row;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public VoiceSessionEventType getEventType() { return eventType; }
    public OffsetDateTime getEventTimestamp() { return eventTimestamp; }
    public long getSequenceNumber() { return sequenceNumber; }
    public String getPayloadSummary() { return payloadSummary; }
    public String getCorrelationId() { return correlationId; }
}
