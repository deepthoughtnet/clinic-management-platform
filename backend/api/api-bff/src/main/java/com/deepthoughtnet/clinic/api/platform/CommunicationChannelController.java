package com.deepthoughtnet.clinic.api.platform;

import com.deepthoughtnet.clinic.communication.model.CommunicationChannelType;
import com.deepthoughtnet.clinic.communication.service.CommunicationChannelModels;
import com.deepthoughtnet.clinic.communication.service.CommunicationChannelService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/tenants/{tenantId}/communication-channels")
@PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
public class CommunicationChannelController {
    private final CommunicationChannelService service;
    public CommunicationChannelController(CommunicationChannelService service) { this.service = service; }

    @GetMapping
    public List<CommunicationChannelModels.Record> list(@PathVariable UUID tenantId) { return service.list(tenantId); }
    @PostMapping
    public CommunicationChannelModels.Record create(@PathVariable UUID tenantId, @RequestBody ChannelRequest request) { return service.create(tenantId, request.command()); }
    @PutMapping("/{channelId}")
    public CommunicationChannelModels.Record update(@PathVariable UUID tenantId, @PathVariable UUID channelId, @RequestBody ChannelRequest request) { return service.update(tenantId, channelId, request.command()); }
    @PatchMapping("/{channelId}/status")
    public CommunicationChannelModels.Record status(@PathVariable UUID tenantId, @PathVariable UUID channelId, @RequestBody StatusRequest request) { return service.setEnabled(tenantId, channelId, request.enabled()); }

    public record StatusRequest(boolean enabled) {}
    public record ChannelRequest(UUID clinicId, UUID locationId, CommunicationChannelType channelType, String provider,
            String address, String displayName, String trunk, String providerConfigRef, boolean enabled,
            boolean inboundEnabled, boolean outboundEnabled, boolean defaultOutbound, Integer priority) {
        CommunicationChannelModels.UpsertCommand command() { return new CommunicationChannelModels.UpsertCommand(clinicId, locationId, channelType, provider, address, displayName, trunk, providerConfigRef, enabled, inboundEnabled, outboundEnabled, defaultOutbound, priority); }
    }
}
