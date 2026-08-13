package com.deepthoughtnet.clinic.api.discover.provider.publicprofile;

import java.util.List;

public final class ProviderHospitalDoctorModels {
    private ProviderHospitalDoctorModels() {
    }

    public record ProviderHospitalDoctorUpsertRequest(
            String publicDoctorReference
    ) {
    }

    public record ProviderHospitalDoctorResponse(
            String publicDoctorReference,
            String doctorDisplayName,
            String speciality,
            String qualification,
            String registrationNumber,
            Integer yearsOfExperience,
            String publicPath,
            String associationStatus,
            String hospitalDisplayName,
            String hospitalSlug,
            List<String> languages
    ) {
    }
}
