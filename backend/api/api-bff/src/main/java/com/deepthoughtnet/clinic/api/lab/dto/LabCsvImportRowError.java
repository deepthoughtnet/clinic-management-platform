package com.deepthoughtnet.clinic.api.lab.dto;

public record LabCsvImportRowError(
        int rowNumber,
        String message
) {
}
