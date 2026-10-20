package com.deepthoughtnet.clinic.communication.service;

import com.deepthoughtnet.clinic.communication.db.CommunicationChannelEntity;
import com.deepthoughtnet.clinic.communication.db.CommunicationChannelRepository;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelStatus;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.deepthoughtnet.clinic.communication.service.CommunicationChannelModels.Resolution;

@Service
public class CommunicationChannelResolver {
    private static final Logger log = LoggerFactory.getLogger(CommunicationChannelResolver.class);
    private final CommunicationChannelRepository repository;
    private final LegacyCommunicationChannelFallback fallback;

    public CommunicationChannelResolver(CommunicationChannelRepository repository) { this(repository, null); }
    @Autowired
    public CommunicationChannelResolver(CommunicationChannelRepository repository, LegacyCommunicationChannelFallback fallback) { this.repository = repository; this.fallback = fallback; }

    public Optional<Resolution> resolveInboundVoiceByDid(String did) {
        final String normalized;
        try { normalized = PhoneAddressNormalizer.normalize(did); }
        catch (IllegalArgumentException ex) { log.info("COMM_CHANNEL_INBOUND_NOT_FOUND did={}", mask(did)); return Optional.empty(); }
        List<CommunicationChannelEntity> matches = repository.findByNormalizedAddressAndChannelTypeAndEnabledTrueAndInboundEnabledTrueAndStatus(
                normalized, CommunicationChannelType.VOICE, CommunicationChannelStatus.ACTIVE);
        if (matches.size() != 1) {
            log.info("COMM_CHANNEL_INBOUND_NOT_FOUND did={} matches={}", mask(normalized), matches.size());
            return Optional.empty();
        }
        CommunicationChannelEntity e = matches.get(0);
        log.info("COMM_CHANNEL_INBOUND_RESOLVED did={} channelId={} tenantId={} clinicId={} locationId={} provider={}", mask(normalized), e.getId(), e.getTenantId(), e.getClinicId(), e.getLocationId(), e.getProvider());
        return Optional.of(toResolution(e, normalized));
    }

    public Optional<Resolution> resolveInboundVoiceByDidWithFallback(String did) {
        Optional<Resolution> persisted = resolveInboundVoiceByDid(did);
        if (persisted.isPresent() || fallback == null || !fallback.enabled()) return persisted;
        try {
            String normalized = PhoneAddressNormalizer.normalize(did);
            Optional<Resolution> legacy = fallback.resolveInbound(normalized);
            legacy.ifPresent(value -> log.info("COMM_CHANNEL_INBOUND_RESOLVED did={} channelId={} tenantId={} clinicId={} locationId={} provider={} source=LEGACY_FALLBACK", mask(normalized), value.channelId(), value.tenantId(), value.clinicId(), value.locationId(), value.provider()));
            if (legacy.isEmpty()) log.info("COMM_CHANNEL_INBOUND_NOT_FOUND did={} source=LEGACY_FALLBACK", mask(normalized));
            return legacy;
        } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }

    public Optional<Resolution> resolveOutboundVoice(UUID tenantId, UUID clinicId, UUID locationId) {
        if (tenantId == null) return Optional.empty();
        List<CommunicationChannelEntity> candidates = repository.findByTenantIdAndChannelTypeAndEnabledTrueAndOutboundEnabledTrueAndStatus(
                tenantId, CommunicationChannelType.VOICE, CommunicationChannelStatus.ACTIVE);
        for (Scope scope : scopes(clinicId, locationId)) {
            List<CommunicationChannelEntity> scoped = candidates.stream().filter(e -> scope.matches(e)).toList();
            if (scoped.isEmpty()) continue;
            List<CommunicationChannelEntity> defaults = scoped.stream().filter(CommunicationChannelEntity::isDefaultOutbound).toList();
            if (defaults.size() == 1) return outbound(defaults.get(0), tenantId);
            if (defaults.size() > 1 || scoped.size() > 1) {
                log.info("COMM_CHANNEL_OUTBOUND_NOT_FOUND tenantId={} clinicId={} locationId={} reason=AMBIGUOUS", tenantId, clinicId, locationId);
                return Optional.empty();
            }
            return outbound(scoped.get(0), tenantId);
        }
        log.info("COMM_CHANNEL_OUTBOUND_NOT_FOUND tenantId={} clinicId={} locationId={}", tenantId, clinicId, locationId);
        return Optional.empty();
    }

    public Optional<Resolution> resolveOutboundVoiceWithFallback(UUID tenantId, UUID clinicId, UUID locationId) {
        Optional<Resolution> persisted = resolveOutboundVoice(tenantId, clinicId, locationId);
        if (persisted.isPresent() || fallback == null || !fallback.enabled()) return persisted;
        Optional<Resolution> legacy = fallback.resolveOutbound(tenantId, clinicId, locationId);
        legacy.ifPresent(value -> log.info("COMM_CHANNEL_OUTBOUND_RESOLVED channelId={} tenantId={} clinicId={} locationId={} provider={} source=LEGACY_FALLBACK", value.channelId(), value.tenantId(), value.clinicId(), value.locationId(), value.provider()));
        if (legacy.isEmpty()) log.info("COMM_CHANNEL_OUTBOUND_NOT_FOUND tenantId={} clinicId={} locationId={} source=LEGACY_FALLBACK", tenantId, clinicId, locationId);
        return legacy;
    }

    private Optional<Resolution> outbound(CommunicationChannelEntity e, UUID tenantId) {
        log.info("COMM_CHANNEL_OUTBOUND_RESOLVED channelId={} tenantId={} clinicId={} locationId={} provider={}", e.getId(), tenantId, e.getClinicId(), e.getLocationId(), e.getProvider());
        return Optional.of(toResolution(e, e.getNormalizedAddress()));
    }
    private List<Scope> scopes(UUID clinicId, UUID locationId) {
        if (locationId != null) return List.of(new Scope(clinicId, locationId), new Scope(clinicId, null), new Scope(null, null));
        if (clinicId != null) return List.of(new Scope(clinicId, null), new Scope(null, null));
        return List.of(new Scope(null, null));
    }
    private Resolution toResolution(CommunicationChannelEntity e, String normalized) { return new Resolution(e.getId(), e.getTenantId(), e.getClinicId(), e.getLocationId(), e.getProvider(), normalized, e.getTrunk(), e.getProviderConfigRef()); }
    private String mask(String value) { if (value == null || value.length() < 5) return "***"; return "***" + value.substring(value.length() - 4); }
    private record Scope(UUID clinicId, UUID locationId) { boolean matches(CommunicationChannelEntity e) { return java.util.Objects.equals(e.getClinicId(), clinicId) && java.util.Objects.equals(e.getLocationId(), locationId); } }
}
