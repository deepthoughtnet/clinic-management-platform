package com.deepthoughtnet.clinic.discover.publicprofiledraft;

import java.util.Optional;
import java.util.UUID;

public interface ClinicProfileConsentLookup {
    Optional<Boolean> findDiscoverPublicListingEnabled(UUID tenantId);
}
