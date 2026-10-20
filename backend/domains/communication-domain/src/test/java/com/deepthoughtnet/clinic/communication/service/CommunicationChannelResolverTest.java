package com.deepthoughtnet.clinic.communication.service;

import com.deepthoughtnet.clinic.communication.db.CommunicationChannelEntity;
import com.deepthoughtnet.clinic.communication.db.CommunicationChannelRepository;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelStatus;
import com.deepthoughtnet.clinic.communication.model.CommunicationChannelType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunicationChannelResolverTest {
    @Mock CommunicationChannelRepository repository;
    CommunicationChannelResolver resolver;
    UUID tenant = UUID.randomUUID();
    UUID clinic = UUID.randomUUID();
    UUID location = UUID.randomUUID();

    @BeforeEach void setUp() { resolver = new CommunicationChannelResolver(repository); }

    @Test void inboundResolutionUsesNormalizedDidAndReturnsTenantScope() {
        CommunicationChannelEntity channel = channel(tenant, null, null, "+919999000001", true, true, false, false);
        when(repository.findByNormalizedAddressAndChannelTypeAndEnabledTrueAndInboundEnabledTrueAndStatus(
                "+919999000001", CommunicationChannelType.VOICE, CommunicationChannelStatus.ACTIVE)).thenReturn(List.of(channel));
        assertEquals(tenant, resolver.resolveInboundVoiceByDid("00919999000001").orElseThrow().tenantId());
        assertEquals(channel.getId(), resolver.resolveInboundVoiceByDid("+919999000001").orElseThrow().channelId());
    }

    @Test void ambiguousOrUnknownInboundFailsSafely() {
        when(repository.findByNormalizedAddressAndChannelTypeAndEnabledTrueAndInboundEnabledTrueAndStatus(any(), any(), any())).thenReturn(List.of());
        assertTrue(resolver.resolveInboundVoiceByDid("+919999999999").isEmpty());
    }

    @Test void outboundPrefersLocationThenClinicThenTenant() {
        CommunicationChannelEntity tenantChannel = channel(tenant, null, null, "+917000000001", true, false, true, true);
        CommunicationChannelEntity clinicChannel = channel(tenant, clinic, null, "+917000000002", true, false, true, true);
        CommunicationChannelEntity locationChannel = channel(tenant, clinic, location, "+917000000003", true, false, true, true);
        when(repository.findByTenantIdAndChannelTypeAndEnabledTrueAndOutboundEnabledTrueAndStatus(tenant, CommunicationChannelType.VOICE, CommunicationChannelStatus.ACTIVE))
                .thenReturn(List.of(tenantChannel, clinicChannel, locationChannel));
        assertEquals(locationChannel.getId(), resolver.resolveOutboundVoice(tenant, clinic, location).orElseThrow().channelId());
    }

    @Test void multipleSameScopeWithoutDeterministicDefaultFails() {
        CommunicationChannelEntity one = channel(tenant, clinic, null, "+917000000004", true, false, true, false);
        CommunicationChannelEntity two = channel(tenant, clinic, null, "+917000000005", true, false, true, false);
        when(repository.findByTenantIdAndChannelTypeAndEnabledTrueAndOutboundEnabledTrueAndStatus(tenant, CommunicationChannelType.VOICE, CommunicationChannelStatus.ACTIVE))
                .thenReturn(List.of(one, two));
        assertTrue(resolver.resolveOutboundVoice(tenant, clinic, null).isEmpty());
    }

    @Test void disabledOrDirectionDisabledChannelsAreIgnoredByRepositoryContract() {
        when(repository.findByNormalizedAddressAndChannelTypeAndEnabledTrueAndInboundEnabledTrueAndStatus(any(), any(), any())).thenReturn(List.of());
        assertTrue(resolver.resolveInboundVoiceByDid("+917000000006").isEmpty());
    }

    private CommunicationChannelEntity channel(UUID tenantId, UUID clinicId, UUID locationId, String address,
            boolean enabled, boolean inbound, boolean outbound, boolean defaultOutbound) {
        return CommunicationChannelEntity.create(tenantId, clinicId, locationId, CommunicationChannelType.VOICE, "DOTVOICE",
                address, address, null, "trunk", null, enabled, inbound, outbound, defaultOutbound, null);
    }
}
