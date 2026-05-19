package com.deepthoughtnet.clinic.api.pharmacy;

import java.util.List;

public record MedicineImportResult(
        int totalRows,
        int created,
        int updated,
        int skipped,
        int failed,
        List<MedicineImportRowResult> rows,
        String failedRowsCsv
) {
}
