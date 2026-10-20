package com.deepthoughtnet.clinic.communication.service;

import com.deepthoughtnet.clinic.communication.db.CommunicationChannelEntity;
import com.deepthoughtnet.clinic.communication.db.CommunicationChannelRepository;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelStatus;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelType;
import com.deepthoughtnet.clinic.clinic.db.ClinicProfileRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.deepthoughtnet.clinic.communication.service.CommunicationChannelModels.*;

@Service
public class CommunicationChannelService {
    private final CommunicationChannelRepository repository;
    private final TenantRepository tenantRepository;
    private final ClinicProfileRepository clinicProfileRepository;
    private final InventoryLocationRepository locationRepository;

    public CommunicationChannelService(CommunicationChannelRepository repository, TenantRepository tenantRepository,
            ClinicProfileRepository clinicProfileRepository, InventoryLocationRepository locationRepository) {
        this.repository = repository; this.tenantRepository = tenantRepository;
        this.clinicProfileRepository = clinicProfileRepository; this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<CommunicationChannelModels.Record> list(UUID tenantId) {
        requireTenant(tenantId);
        return repository.findByTenantIdOrderByPriorityAscCreatedAtAsc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional
    public CommunicationChannelModels.Record create(UUID tenantId, UpsertCommand command) {
        validate(tenantId, command, null);
        CommunicationChannelEntity entity = CommunicationChannelEntity.create(tenantId, command.clinicId(), command.locationId(),
                command.channelType(), text(command.provider(), "provider"), text(command.address(), "address"),
                PhoneAddressNormalizer.normalize(command.address()), nullable(command.displayName()), nullable(command.trunk()),
                nullable(command.providerConfigRef()), command.enabled(), command.inboundEnabled(), command.outboundEnabled(),
                command.defaultOutbound(), command.priority());
        return toRecord(repository.save(entity));
    }

    @Transactional
    public CommunicationChannelModels.Record update(UUID tenantId, UUID id, UpsertCommand command) {
        CommunicationChannelEntity entity = find(tenantId, id);
        validate(tenantId, command, id);
        entity.update(command.clinicId(), command.locationId(), command.channelType(), text(command.provider(), "provider"),
                text(command.address(), "address"), PhoneAddressNormalizer.normalize(command.address()), nullable(command.displayName()),
                nullable(command.trunk()), nullable(command.providerConfigRef()), command.inboundEnabled(), command.outboundEnabled(),
                command.defaultOutbound(), command.priority(), command.enabled());
        return toRecord(repository.save(entity));
    }

    @Transactional
    public CommunicationChannelModels.Record setEnabled(UUID tenantId, UUID id, boolean enabled) {
        CommunicationChannelEntity entity = find(tenantId, id);
        if (enabled && entity.isDefaultOutbound()) validateDefault(tenantId, entity, id);
        if (enabled) entity.enable(); else entity.disable();
        return toRecord(repository.save(entity));
    }

    private void validate(UUID tenantId, UpsertCommand command, UUID existingId) {
        requireTenant(tenantId);
        if (command == null || command.channelType() == null) throw new IllegalArgumentException("channelType is required");
        String provider = text(command.provider(), "provider");
        if (command.channelType() == CommunicationChannelType.VOICE && !StringUtils.hasText(provider)) throw new IllegalArgumentException("provider is required for voice channels");
        if (command.defaultOutbound() && !command.outboundEnabled()) throw new IllegalArgumentException("default outbound requires outbound to be enabled");
        String normalized = PhoneAddressNormalizer.normalize(command.address());
        if (command.inboundEnabled() && command.enabled() && command.channelType() == CommunicationChannelType.VOICE) {
            var matches = repository.findByNormalizedAddressAndChannelTypeAndEnabledTrueAndInboundEnabledTrueAndStatus(
                    normalized, command.channelType(), CommunicationChannelStatus.ACTIVE);
            if (matches.stream().anyMatch(row -> !row.getId().equals(existingId))) {
                throw new IllegalArgumentException("An active inbound voice channel already uses this DID");
            }
        }
        if (command.clinicId() != null && clinicProfileRepository.findByTenantId(tenantId)
                .map(profile -> !profile.getId().equals(command.clinicId())).orElse(true)) {
            throw new IllegalArgumentException("clinicId does not belong to tenant");
        }
        if (command.locationId() != null && locationRepository.findByTenantIdAndId(tenantId, command.locationId()).isEmpty()) {
            throw new IllegalArgumentException("locationId does not belong to tenant");
        }
        if (command.defaultOutbound()) validateDefault(tenantId, null, existingId, command);
    }

    private void validateDefault(UUID tenantId, CommunicationChannelEntity entity, UUID existingId) {
        validateDefault(tenantId, entity, existingId, new UpsertCommand(entity.getClinicId(), entity.getLocationId(),
                entity.getChannelType(), entity.getProvider(), entity.getAddress(), entity.getDisplayName(), entity.getTrunk(),
                entity.getProviderConfigRef(), true, entity.isInboundEnabled(), entity.isOutboundEnabled(), true, entity.getPriority()));
    }

    private void validateDefault(UUID tenantId, CommunicationChannelEntity ignored, UUID existingId, UpsertCommand command) {
        if (!command.defaultOutbound() || !command.outboundEnabled() || command.channelType() != CommunicationChannelType.VOICE) return;
        long conflicts = repository.findByTenantIdAndChannelTypeAndEnabledTrueAndOutboundEnabledTrueAndStatus(
                        tenantId, command.channelType(), CommunicationChannelStatus.ACTIVE).stream()
                .filter(row -> !row.getId().equals(existingId))
                .filter(row -> sameScope(row, command.clinicId(), command.locationId()))
                .count();
        if (conflicts > 0) throw new IllegalArgumentException("Another default outbound voice channel exists at this scope");
    }

    private boolean sameScope(CommunicationChannelEntity row, UUID clinicId, UUID locationId) {
        return java.util.Objects.equals(row.getClinicId(), clinicId) && java.util.Objects.equals(row.getLocationId(), locationId);
    }
    private CommunicationChannelEntity find(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        if (id == null) throw new IllegalArgumentException("channelId is required");
        return repository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Communication channel not found"));
    }
    private void requireTenant(UUID tenantId) {
        if (tenantId == null || tenantRepository.findById(tenantId).isEmpty()) throw new IllegalArgumentException("Tenant not found");
    }
    private String text(String value, String name) { if (!StringUtils.hasText(value)) throw new IllegalArgumentException(name + " is required"); return value.trim(); }
    private String nullable(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private CommunicationChannelModels.Record toRecord(CommunicationChannelEntity e) { return new CommunicationChannelModels.Record(e.getId(), e.getTenantId(), e.getClinicId(), e.getLocationId(), e.getChannelType(), e.getProvider(), e.getAddress(), e.getNormalizedAddress(), e.getDisplayName(), e.getTrunk(), e.getProviderConfigRef(), e.isEnabled(), e.isInboundEnabled(), e.isOutboundEnabled(), e.isDefaultOutbound(), e.getPriority(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt()); }
}
