package com.deepthoughtnet.clinic.platform.providerintegration.model;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchConfidence;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchMethod;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;

import java.io.Serializable;

public record PublicDoctorPracticePlatformLinkUpsertRequest(
        ProviderSourceReference sourceReference,
        PublicProviderReference publicDoctorReference,
        PublicProviderReference publicPracticeReference,
        String tenantReference,
        String platformClinicReference,
        String tenantDoctorUserReference,
        String tenantDoctorProfileReference,
        LinkLifecycleStatus linkStatus,
        PlatformConnectionStatus connectionStatus,
        MatchMethod matchMethod,
        MatchConfidence matchConfidence,
        AvailabilityState availabilityState,
        String evidenceSnapshotJson,
        String actorType,
        String actorReference,
        String reason
) implements Serializable {
}
