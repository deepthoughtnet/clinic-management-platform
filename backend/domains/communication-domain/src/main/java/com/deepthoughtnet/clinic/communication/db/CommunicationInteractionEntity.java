package com.deepthoughtnet.clinic.communication.db;

import com.deepthoughtnet.clinic.communication.model.ConversationPartyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Durable, non-clinical usage/audit record for one communication interaction. */
@Entity
@Table(name = "communication_interactions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_comm_interactions_provider_call", columnNames = "provider_call_id")
}, indexes = {
        @Index(name = "ix_comm_interactions_tenant_started", columnList = "tenant_id,started_at"),
        @Index(name = "ix_comm_interactions_usage", columnList = "tenant_id,channel_type,provider,direction,started_at"),
        @Index(name = "ix_comm_interactions_identification", columnList = "tenant_id,patient_identification_status")
})
public class CommunicationInteractionEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "provider_call_id", nullable = false, length = 128) private String providerCallId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "communication_channel_id") private UUID communicationChannelId;
    @Column(name = "clinic_id") private UUID clinicId;
    @Column(name = "location_id") private UUID locationId;
    @Column(name = "channel_type", nullable = false, length = 24) private String channelType;
    @Column(nullable = false, length = 64) private String provider;
    @Column(nullable = false, length = 16) private String direction;
    @Column(name = "destination_reference", length = 64) private String destinationReference;
    @Column(name = "caller_number_masked", length = 32) private String callerNumberMasked;
    @Column(name = "patient_identification_status", nullable = false, length = 32) private String patientIdentificationStatus;
    @Column(name = "identified_patient_id") private UUID identifiedPatientId;
    @Enumerated(EnumType.STRING) @Column(name = "conversation_party_type", nullable = false, length = 32)
    private ConversationPartyType conversationPartyType;
    @Column(name = "candidate_count", nullable = false) private int candidateCount;
    @Column(name = "call_status", nullable = false, length = 32) private String callStatus;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "answered_at") private OffsetDateTime answeredAt;
    @Column(name = "ended_at") private OffsetDateTime endedAt;
    @Column(name = "duration_seconds") private Long durationSeconds;
    @Column(name = "transcript_present", nullable = false) private boolean transcriptPresent;
    @Column(length = 64) private String outcome;
    @Column(name = "provider_end_cause", length = 160) private String providerEndCause;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected CommunicationInteractionEntity() {}

    public static CommunicationInteractionEntity create(String providerCallId, UUID tenantId,
            UUID communicationChannelId, UUID clinicId, UUID locationId, String channelType, String provider,
            String direction, String destinationReference, String callerNumberMasked,
            String patientIdentificationStatus, UUID identifiedPatientId, ConversationPartyType conversationPartyType,
            int candidateCount, String callStatus, OffsetDateTime startedAt) {
        CommunicationInteractionEntity row = new CommunicationInteractionEntity();
        row.id = UUID.randomUUID();
        row.providerCallId = providerCallId;
        row.tenantId = tenantId;
        row.communicationChannelId = communicationChannelId;
        row.clinicId = clinicId;
        row.locationId = locationId;
        row.channelType = channelType;
        row.provider = provider == null ? "UNKNOWN" : provider;
        row.direction = direction;
        row.destinationReference = destinationReference;
        row.callerNumberMasked = callerNumberMasked;
        row.patientIdentificationStatus = patientIdentificationStatus;
        row.identifiedPatientId = identifiedPatientId;
        row.conversationPartyType = conversationPartyType;
        row.candidateCount = candidateCount;
        row.callStatus = callStatus;
        row.startedAt = startedAt == null ? OffsetDateTime.now() : startedAt;
        row.transcriptPresent = false;
        row.createdAt = row.startedAt;
        row.updatedAt = row.startedAt;
        return row;
    }

    public void attachIdentification(String status, UUID patientId, ConversationPartyType party, int count) {
        this.patientIdentificationStatus = status;
        this.identifiedPatientId = patientId;
        this.conversationPartyType = party;
        this.candidateCount = count;
        touch();
    }

    public void attachRouting(UUID tenantId, UUID channelId, UUID clinicId, UUID locationId,
            String destinationReference, String callerNumberMasked) {
        this.tenantId = tenantId;
        this.communicationChannelId = channelId;
        this.clinicId = clinicId;
        this.locationId = locationId;
        this.destinationReference = destinationReference;
        this.callerNumberMasked = callerNumberMasked;
        touch();
    }

    public void markAnswered(OffsetDateTime answeredAt) {
        this.answeredAt = answeredAt == null ? OffsetDateTime.now() : answeredAt;
        this.callStatus = "ANSWERED";
        touch();
    }

    public void markTranscriptPresent() {
        this.transcriptPresent = true;
        touch();
    }

    public void markEnded(String status, String outcome, String providerEndCause, OffsetDateTime endedAt) {
        this.callStatus = status;
        this.outcome = outcome;
        this.providerEndCause = providerEndCause;
        this.endedAt = endedAt == null ? OffsetDateTime.now() : endedAt;
        this.durationSeconds = startedAt == null ? null : Math.max(0, java.time.Duration.between(startedAt, this.endedAt).getSeconds());
        touch();
    }

    private void touch() { updatedAt = OffsetDateTime.now(); }
    public UUID getId() { return id; }
    public String getProviderCallId() { return providerCallId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCommunicationChannelId() { return communicationChannelId; }
    public UUID getClinicId() { return clinicId; }
    public UUID getLocationId() { return locationId; }
    public String getChannelType() { return channelType; }
    public String getProvider() { return provider; }
    public String getDirection() { return direction; }
    public String getDestinationReference() { return destinationReference; }
    public String getCallerNumberMasked() { return callerNumberMasked; }
    public String getPatientIdentificationStatus() { return patientIdentificationStatus; }
    public UUID getIdentifiedPatientId() { return identifiedPatientId; }
    public ConversationPartyType getConversationPartyType() { return conversationPartyType; }
    public int getCandidateCount() { return candidateCount; }
    public String getCallStatus() { return callStatus; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getAnsweredAt() { return answeredAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public Long getDurationSeconds() { return durationSeconds; }
    public boolean isTranscriptPresent() { return transcriptPresent; }
    public String getOutcome() { return outcome; }
    public String getProviderEndCause() { return providerEndCause; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
