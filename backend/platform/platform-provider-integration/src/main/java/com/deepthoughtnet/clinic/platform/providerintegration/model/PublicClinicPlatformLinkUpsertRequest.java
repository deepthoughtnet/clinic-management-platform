package com.deepthoughtnet.clinic.platform.providerintegration.model;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchConfidence;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchMethod;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;

import java.io.Serializable;

public record PublicClinicPlatformLinkUpsertRequest(
        ProviderSourceReference sourceReference,
        String publicClinicReference,
        String tenantReference,
        String platformClinicReference,
        LinkLifecycleStatus linkStatus,
        PlatformConnectionStatus connectionStatus,
        MatchMethod matchMethod,
        MatchConfidence matchConfidence,
        AvailabilityState availabilityState,
        String evidenceSnapshotJson,
        String actorType,
        String actorReference,
        String reason,
        BookingCapability operationalBookingCapability,
        String capabilityReason
) implements Serializable {
}
