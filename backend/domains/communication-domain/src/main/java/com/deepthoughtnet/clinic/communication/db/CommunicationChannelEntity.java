package com.deepthoughtnet.clinic.communication.db;

import com.deepthoughtnet.clinic.communication.model.CommunicationChannelStatus;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "communication_channels", indexes = {
        @Index(name = "ix_comm_channels_normalized_address", columnList = "normalized_address"),
        @Index(name = "ix_comm_channels_tenant_type", columnList = "tenant_id,channel_type"),
        @Index(name = "ix_comm_channels_tenant_clinic", columnList = "tenant_id,clinic_id"),
        @Index(name = "ix_comm_channels_tenant_clinic_location", columnList = "tenant_id,clinic_id,location_id")
})
public class CommunicationChannelEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "clinic_id") private UUID clinicId;
    @Column(name = "location_id") private UUID locationId;
    @Enumerated(EnumType.STRING) @Column(name = "channel_type", nullable = false, length = 24) private CommunicationChannelType channelType;
    @Column(nullable = false, length = 64) private String provider;
    @Column(nullable = false, length = 128) private String address;
    @Column(name = "normalized_address", nullable = false, length = 32) private String normalizedAddress;
    @Column(name = "display_name", length = 160) private String displayName;
    @Column(length = 128) private String trunk;
    @Column(name = "provider_config_ref", length = 256) private String providerConfigRef;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "inbound_enabled", nullable = false) private boolean inboundEnabled;
    @Column(name = "outbound_enabled", nullable = false) private boolean outboundEnabled;
    @Column(name = "default_outbound", nullable = false) private boolean defaultOutbound;
    @Column private Integer priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private CommunicationChannelStatus status;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected CommunicationChannelEntity() {}

    public static CommunicationChannelEntity create(UUID tenantId, UUID clinicId, UUID locationId,
            CommunicationChannelType channelType, String provider, String address, String normalizedAddress,
            String displayName, String trunk, String providerConfigRef, boolean enabled, boolean inboundEnabled,
            boolean outboundEnabled, boolean defaultOutbound, Integer priority) {
        CommunicationChannelEntity e = new CommunicationChannelEntity();
        e.id = UUID.randomUUID(); e.tenantId = tenantId; e.clinicId = clinicId; e.locationId = locationId;
        e.channelType = channelType; e.provider = provider; e.address = address; e.normalizedAddress = normalizedAddress;
        e.displayName = displayName; e.trunk = trunk; e.providerConfigRef = providerConfigRef;
        e.enabled = enabled; e.inboundEnabled = inboundEnabled; e.outboundEnabled = outboundEnabled;
        e.defaultOutbound = defaultOutbound; e.priority = priority; e.status = enabled ? CommunicationChannelStatus.ACTIVE : CommunicationChannelStatus.DISABLED;
        e.createdAt = OffsetDateTime.now(); e.updatedAt = e.createdAt; return e;
    }

    public void update(UUID clinicId, UUID locationId, CommunicationChannelType type, String provider, String address,
            String normalizedAddress, String displayName, String trunk, String providerConfigRef,
            boolean inboundEnabled, boolean outboundEnabled, boolean defaultOutbound, Integer priority, boolean enabled) {
        this.clinicId = clinicId; this.locationId = locationId; this.channelType = type; this.provider = provider;
        this.address = address; this.normalizedAddress = normalizedAddress; this.displayName = displayName;
        this.trunk = trunk; this.providerConfigRef = providerConfigRef; this.inboundEnabled = inboundEnabled;
        this.outboundEnabled = outboundEnabled; this.defaultOutbound = defaultOutbound; this.priority = priority;
        this.enabled = enabled; this.status = enabled ? CommunicationChannelStatus.ACTIVE : CommunicationChannelStatus.DISABLED; touch();
    }
    public void enable() { enabled = true; status = CommunicationChannelStatus.ACTIVE; touch(); }
    public void disable() { enabled = false; status = CommunicationChannelStatus.DISABLED; touch(); }
    private void touch() { updatedAt = OffsetDateTime.now(); }
    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; } public UUID getClinicId() { return clinicId; }
    public UUID getLocationId() { return locationId; } public CommunicationChannelType getChannelType() { return channelType; }
    public String getProvider() { return provider; } public String getAddress() { return address; } public String getNormalizedAddress() { return normalizedAddress; }
    public String getDisplayName() { return displayName; } public String getTrunk() { return trunk; } public String getProviderConfigRef() { return providerConfigRef; }
    public boolean isEnabled() { return enabled; } public boolean isInboundEnabled() { return inboundEnabled; } public boolean isOutboundEnabled() { return outboundEnabled; }
    public boolean isDefaultOutbound() { return defaultOutbound; } public Integer getPriority() { return priority; }
    public CommunicationChannelStatus getStatus() { return status; } public OffsetDateTime getCreatedAt() { return createdAt; } public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
