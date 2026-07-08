package com.deepthoughtnet.clinic.api.clinicalmemory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LongitudinalConceptSnapshot(
        String conceptFamily,
        String conceptKey,
        String label,
        String valueText,
        String valueUnit,
        String sourceDocumentTitle,
        String sourceDocumentType,
        UUID sourceDocumentId,
        LocalDate observedOn,
        BigDecimal confidence,
        String verificationStatus,
        String evidenceText
) {
}
