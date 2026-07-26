package com.deepthoughtnet.clinic.prescription.service;

import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionLogoAsset;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionLogoResolver {
    Optional<PrescriptionLogoAsset> resolve(UUID tenantId, UUID logoDocumentId);
}
