package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.util.List;

public record VaccineCsvImportResponse(
        int totalRows,
        int createdCount,
        int failedCount,
        List<VaccineCsvImportRowResult> rows
) {
}
