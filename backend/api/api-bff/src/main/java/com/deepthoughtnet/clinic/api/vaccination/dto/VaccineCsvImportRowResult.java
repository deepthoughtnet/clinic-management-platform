package com.deepthoughtnet.clinic.api.vaccination.dto;

public record VaccineCsvImportRowResult(
        int rowNumber,
        String vaccineName,
        String status,
        String message
) {
}
