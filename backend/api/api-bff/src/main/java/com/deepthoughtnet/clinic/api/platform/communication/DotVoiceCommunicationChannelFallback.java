package com.deepthoughtnet.clinic.api.platform.communication;

import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceProperties;
import com.deepthoughtnet.clinic.communication.service.LegacyCommunicationChannelFallback;
import com.deepthoughtnet.clinic.communication.service.PhoneAddressNormalizer;
import com.deepthoughtnet.clinic.communication.service.CommunicationChannelModels.Resolution;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Opt-in bridge for existing process-level DotVoice local/UAT settings. */
@Component
public class DotVoiceCommunicationChannelFallback implements LegacyCommunicationChannelFallback {
    private final DotVoiceProperties properties;
    private final boolean enabled;

    public DotVoiceCommunicationChannelFallback(DotVoiceProperties properties,
            @Value("${clinic.communication.channel-fallback.enabled:false}") boolean enabled) {
        this.properties = properties; this.enabled = enabled;
    }
    @Override public boolean enabled() { return enabled && properties.isConfigured(); }
    @Override public Optional<Resolution> resolveInbound(String normalizedDid) {
        if (!enabled() || !normalizedDid.equals(normalize(properties.getFromNumber()))) return Optional.empty();
        return Optional.of(resolution(null, null, null, normalizedDid));
    }
    @Override public Optional<Resolution> resolveOutbound(UUID tenantId, UUID clinicId, UUID locationId) {
        if (!enabled()) return Optional.empty();
        return Optional.of(resolution(tenantId, clinicId, locationId, normalize(properties.getFromNumber())));
    }
    private Resolution resolution(UUID tenantId, UUID clinicId, UUID locationId, String normalized) {
        UUID channelId = UUID.nameUUIDFromBytes(("legacy-dotvoice:" + normalized).getBytes(StandardCharsets.UTF_8));
        return new Resolution(channelId, tenantId, clinicId, locationId, "DOTVOICE", normalized, properties.getTrunk(), null);
    }
    private String normalize(String value) { return StringUtils.hasText(value) ? PhoneAddressNormalizer.normalize(value) : ""; }
}
