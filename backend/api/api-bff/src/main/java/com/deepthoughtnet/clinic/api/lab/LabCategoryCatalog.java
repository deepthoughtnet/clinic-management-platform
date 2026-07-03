package com.deepthoughtnet.clinic.api.lab;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LabCategoryCatalog {
    public static final List<String> CATEGORY_CODES = List.of(
            "HEMATOLOGY",
            "BIOCHEMISTRY",
            "MICROBIOLOGY",
            "PATHOLOGY",
            "RADIOLOGY",
            "CARDIOLOGY",
            "IMMUNOLOGY",
            "SEROLOGY",
            "ENDOCRINOLOGY",
            "VIROLOGY",
            "MOLECULAR",
            "CYTOLOGY",
            "HISTOPATHOLOGY",
            "OTHER"
    );

    public static final Set<String> CATEGORY_ALIASES = Set.of("THYROID");

    private LabCategoryCatalog() {
    }

    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("category is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (CATEGORY_ALIASES.contains(normalized)) {
            return "ENDOCRINOLOGY";
        }
        for (String allowed : CATEGORY_CODES) {
            if (allowed.equalsIgnoreCase(normalized)) {
                return allowed;
            }
        }
        throw new IllegalArgumentException("category '" + value.trim() + "' is invalid. Allowed values: " + String.join(", ", CATEGORY_CODES) + ". THYROID is normalized to ENDOCRINOLOGY.");
    }

    public static String displayName(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "";
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HEMATOLOGY" -> "Hematology";
            case "BIOCHEMISTRY" -> "Biochemistry";
            case "MICROBIOLOGY" -> "Microbiology";
            case "PATHOLOGY" -> "Pathology";
            case "RADIOLOGY" -> "Radiology";
            case "CARDIOLOGY" -> "Cardiology";
            case "IMMUNOLOGY" -> "Immunology";
            case "SEROLOGY" -> "Serology";
            case "ENDOCRINOLOGY" -> "Endocrinology";
            case "VIROLOGY" -> "Virology";
            case "MOLECULAR" -> "Molecular";
            case "CYTOLOGY" -> "Cytology";
            case "HISTOPATHOLOGY" -> "Histopathology";
            case "OTHER" -> "Other";
            default -> normalized;
        };
    }
}
