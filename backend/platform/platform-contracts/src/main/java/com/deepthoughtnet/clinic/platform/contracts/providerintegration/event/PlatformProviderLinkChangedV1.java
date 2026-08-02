package com.deepthoughtnet.clinic.platform.contracts.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;

public record PlatformProviderLinkChangedV1(
        PublicProfileType publicProfileType,
        ProviderSourceReference sourceReference,
        PublicProviderReference publicReference,
        String tenantReference,
        String platformClinicReference,
        LinkLifecycleStatus linkStatus,
        PlatformConnectionStatus connectionStatus,
        BookingCapability bookingCapability,
        String reason
) implements ProviderIntegrationEventPayload {
}
