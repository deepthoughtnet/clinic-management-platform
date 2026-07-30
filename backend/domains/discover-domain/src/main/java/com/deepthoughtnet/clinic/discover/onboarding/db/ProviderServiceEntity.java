package com.deepthoughtnet.clinic.discover.onboarding.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_services")
public class ProviderServiceEntity {
    @Id
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Convert(converter = ProviderServiceTypeConverter.class)
    @Column(name = "service_type", nullable = false, length = 64)
    private ProviderServiceType serviceType;
    @Column(nullable = false, length = 128)
    private String label;
    @Column(length = 512)
    private String description;
    @Column(nullable = false)
    private boolean enabled;

    protected ProviderServiceEntity() {
    }

    public ProviderServiceEntity(UUID id, UUID providerId, ProviderServiceType serviceType, String label, String description, boolean enabled) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.providerId = providerId;
        this.serviceType = serviceType;
        this.label = label;
        this.description = description;
        this.enabled = enabled;
    }
    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public ProviderServiceType getServiceType() { return serviceType; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
}
