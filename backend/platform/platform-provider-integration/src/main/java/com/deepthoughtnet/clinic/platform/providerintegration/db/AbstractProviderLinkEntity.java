package com.deepthoughtnet.clinic.platform.providerintegration.db;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchMethod;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@MappedSuperclass
public abstract class AbstractProviderLinkEntity {

    protected AbstractProviderLinkEntity() {
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private PublicProfileType providerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false)
    private SourceSystem sourceSystem;

    @Column(name = "source_entity_reference", nullable = false, length = 160)
    private String sourceEntityReference;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;

    @Column(name = "source_updated_at")
    private OffsetDateTime sourceUpdatedAt;

    @Column(name = "tenant_reference", length = 160)
    private String tenantReference;

    @Column(name = "platform_clinic_reference", length = 160)
    private String platformClinicReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false, length = 40)
    private LinkLifecycleStatus linkStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 40)
    private PlatformConnectionStatus connectionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = false, length = 40)
    private MatchMethod matchMethod;

    @Column(name = "match_confidence", length = 40)
    private String matchConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_capability", nullable = false, length = 40)
    private BookingCapability bookingCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_state", nullable = false, length = 40)
    private AvailabilityState availabilityState;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 120)
    private String bookingReference;

    @Column(name = "capability_version", nullable = false)
    private long capabilityVersion;

    @Column(name = "connection_revision", nullable = false)
    private long connectionRevision;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "reason", length = 512)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_snapshot_json", columnDefinition = "jsonb")
    private String evidenceSnapshotJson;

    @Column(name = "linked_at")
    private OffsetDateTime linkedAt;

    @Column(name = "unlinked_at")
    private OffsetDateTime unlinkedAt;

    @Column(name = "projected_at")
    private OffsetDateTime projectedAt;

    @Column(name = "proposed_by", length = 160)
    private String proposedBy;

    @Column(name = "proposed_at")
    private OffsetDateTime proposedAt;

    @Column(name = "verified_by", length = 160)
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "activated_by", length = 160)
    private String activatedBy;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "suspended_by", length = 160)
    private String suspendedBy;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "disconnected_by", length = 160)
    private String disconnectedBy;

    @Column(name = "disconnected_at")
    private OffsetDateTime disconnectedAt;

    @Column(name = "capability_reason", length = 512)
    private String capabilityReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public abstract String naturalKey();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PublicProfileType getProviderType() {
        return providerType;
    }

    public void setProviderType(PublicProfileType providerType) {
        this.providerType = providerType;
    }

    public SourceSystem getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(SourceSystem sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceEntityReference() {
        return sourceEntityReference;
    }

    public void setSourceEntityReference(String sourceEntityReference) {
        this.sourceEntityReference = sourceEntityReference;
    }

    public long getSourceRevision() {
        return sourceRevision;
    }

    public void setSourceRevision(long sourceRevision) {
        this.sourceRevision = sourceRevision;
    }

    public OffsetDateTime getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setSourceUpdatedAt(OffsetDateTime sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public String getTenantReference() {
        return tenantReference;
    }

    public void setTenantReference(String tenantReference) {
        this.tenantReference = tenantReference;
    }

    public String getPlatformClinicReference() {
        return platformClinicReference;
    }

    public void setPlatformClinicReference(String platformClinicReference) {
        this.platformClinicReference = platformClinicReference;
    }

    public LinkLifecycleStatus getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(LinkLifecycleStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    public PlatformConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(PlatformConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public MatchMethod getMatchMethod() {
        return matchMethod;
    }

    public void setMatchMethod(MatchMethod matchMethod) {
        this.matchMethod = matchMethod;
    }

    public String getMatchConfidence() {
        return matchConfidence;
    }

    public void setMatchConfidence(String matchConfidence) {
        this.matchConfidence = matchConfidence;
    }

    public BookingCapability getBookingCapability() {
        return bookingCapability;
    }

    public void setBookingCapability(BookingCapability bookingCapability) {
        this.bookingCapability = bookingCapability;
    }

    public AvailabilityState getAvailabilityState() {
        return availabilityState;
    }

    public void setAvailabilityState(AvailabilityState availabilityState) {
        this.availabilityState = availabilityState;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public long getCapabilityVersion() {
        return capabilityVersion;
    }

    public void setCapabilityVersion(long capabilityVersion) {
        this.capabilityVersion = capabilityVersion;
    }

    public long getConnectionRevision() {
        return connectionRevision;
    }

    public void setConnectionRevision(long connectionRevision) {
        this.connectionRevision = connectionRevision;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEvidenceSnapshotJson() {
        return evidenceSnapshotJson;
    }

    public void setEvidenceSnapshotJson(String evidenceSnapshotJson) {
        this.evidenceSnapshotJson = evidenceSnapshotJson;
    }

    public OffsetDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(OffsetDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

    public OffsetDateTime getUnlinkedAt() {
        return unlinkedAt;
    }

    public void setUnlinkedAt(OffsetDateTime unlinkedAt) {
        this.unlinkedAt = unlinkedAt;
    }

    public OffsetDateTime getProjectedAt() {
        return projectedAt;
    }

    public void setProjectedAt(OffsetDateTime projectedAt) {
        this.projectedAt = projectedAt;
    }

    public String getProposedBy() { return proposedBy; }
    public void setProposedBy(String proposedBy) { this.proposedBy = proposedBy; }
    public OffsetDateTime getProposedAt() { return proposedAt; }
    public void setProposedAt(OffsetDateTime proposedAt) { this.proposedAt = proposedAt; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getActivatedBy() { return activatedBy; }
    public void setActivatedBy(String activatedBy) { this.activatedBy = activatedBy; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public String getSuspendedBy() { return suspendedBy; }
    public void setSuspendedBy(String suspendedBy) { this.suspendedBy = suspendedBy; }
    public OffsetDateTime getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(OffsetDateTime suspendedAt) { this.suspendedAt = suspendedAt; }
    public String getDisconnectedBy() { return disconnectedBy; }
    public void setDisconnectedBy(String disconnectedBy) { this.disconnectedBy = disconnectedBy; }
    public OffsetDateTime getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(OffsetDateTime disconnectedAt) { this.disconnectedAt = disconnectedAt; }
    public String getCapabilityReason() { return capabilityReason; }
    public void setCapabilityReason(String capabilityReason) { this.capabilityReason = capabilityReason; }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(long rowVersion) {
        this.rowVersion = rowVersion;
    }
}
