package com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto;

import com.deepthoughtnet.clinic.api.clinicaldocument.dto.ClinicalMemoryRepairCorrectedValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClinicalMemoryRepairResult(
        UUID documentId,
        String status,
        OffsetDateTime repairedAt,
        UUID repairedBy,
        int deletedPendingConceptCount,
        int insertedConceptCount,
        int skippedAcceptedConceptCount,
        List<ClinicalMemoryRepairCorrectedValue> correctedValues,
        int filteredPollutedConceptCount,
        String message
) {
}
