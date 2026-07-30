package com.deepthoughtnet.clinic.discover.reference.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "discover_reference_options")
public class DiscoverReferenceOptionEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private DiscoverReferenceCategory category;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(name = "provider_types", nullable = false, length = 256)
    private String providerTypes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    protected DiscoverReferenceOptionEntity() {
    }

    public DiscoverReferenceOptionEntity(UUID id, DiscoverReferenceCategory category, String code, String displayName, List<ProviderType> providerTypes, int displayOrder, boolean active) {
        this.id = id;
        this.category = category;
        this.code = code;
        this.displayName = displayName;
        this.providerTypes = providerTypes == null || providerTypes.isEmpty()
                ? ""
                : providerTypes.stream().map(Enum::name).reduce((left, right) -> left + "," + right).orElse("");
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public DiscoverReferenceCategory getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<ProviderType> getProviderTypes() {
        if (providerTypes == null || providerTypes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(providerTypes.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(ProviderType::valueOf)
                .toList();
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}
