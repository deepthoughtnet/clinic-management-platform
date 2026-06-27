package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;

public record LabCsvImportResponse(
        int totalRows,
        int createdCount,
        int updatedCount,
        int failedCount,
        List<LabCsvImportRowError> rowErrors
) {
}
