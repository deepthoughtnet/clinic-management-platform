package com.deepthoughtnet.clinic.api.lab.service.model;

import com.deepthoughtnet.clinic.api.lab.db.LabOrderOrigin;
import java.util.List;
import java.util.UUID;

public record LabOrderDirectCreateCommand(
        UUID patientId,
        LabOrderOrigin orderOrigin,
        UUID requestedByInternalDoctorId,
        String externalDoctorName,
        String externalDoctorMobile,
        String externalClinicName,
        String referralSource,
        List<UUID> testIds,
        String notes
) {
}
