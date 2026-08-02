package com.deepthoughtnet.clinic.platform.contracts.providerintegration.port;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderFactsSnapshot;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;

import java.util.Optional;

public interface HealthcareProviderFactsPort {
    Optional<ProviderFactsSnapshot> getClinicPublicFacts(ProviderSourceReference sourceReference);

    Optional<ProviderFactsSnapshot> getDoctorPublicFacts(ProviderSourceReference sourceReference);

    Optional<ProviderFactsSnapshot> getHospitalPublicFacts(ProviderSourceReference sourceReference);
}
