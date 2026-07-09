package com.deepthoughtnet.clinic.api.clinicaldocument.dto;

public record ClinicalDocumentAiOps(
        String lastAiRetryAt,
        String lastAiRetryStatus,
        String lastAiRetryMessage,
        String lastAiRetryJobId,
        String lastMemoryRepairAt,
        String lastMemoryRepairStatus,
        String lastMemoryRepairMessage,
        String lastMemoryRepairBy,
        Integer lastMemoryRepairDeletedPendingConceptCount,
        Integer lastMemoryRepairInsertedConceptCount,
        Integer lastMemoryRepairSkippedAcceptedConceptCount,
        Integer lastMemoryRepairFilteredPollutedConceptCount,
        String lastMemoryRepairCorrectedValues
) {
}
