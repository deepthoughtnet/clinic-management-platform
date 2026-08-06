package com.deepthoughtnet.clinic.api.discover.provider.publicprofile;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ClinicProfileConsentLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ClinicProfileConsentLookupService implements ClinicProfileConsentLookup {
    private final ClinicProfileService clinicProfileService;

    public ClinicProfileConsentLookupService(ClinicProfileService clinicProfileService) {
        this.clinicProfileService = clinicProfileService;
    }

    @Override
    public Optional<Boolean> findDiscoverPublicListingEnabled(UUID tenantId) {
        return clinicProfileService.findByTenantId(tenantId).map(profile -> profile.publicListingEnabled());
    }
}
