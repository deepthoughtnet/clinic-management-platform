package com.deepthoughtnet.clinic.api.help;

import java.util.Map;

final class HelpPageKeyResolver {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("PATIENT_MASTER", "PATIENTS"),
            Map.entry("PATIENT_DETAILS", "PATIENT_DETAILS"),
            Map.entry("PHARMACY_MEDICINE_MASTER", "MEDICINE_MASTER"),
            Map.entry("PHARMACY_DISPENSING", "DISPENSING"),
            Map.entry("PHARMACY", "PHARMACY_DASHBOARD"),
            Map.entry("FINANCE_BILLING", "BILLING"),
            Map.entry("BILL_BUILDER", "BILLING"),
            Map.entry("BILLING", "BILLING"),
            Map.entry("FINANCE_REPORTS", "REPORTS"),
            Map.entry("TENANT_REPORTS", "REPORTS")
    );

    private HelpPageKeyResolver() {
    }

    static String resolveLookupPageKey(String pageKey) {
        if (pageKey == null || pageKey.trim().isEmpty()) {
            return "";
        }
        String normalized = pageKey.trim().toUpperCase();
        return ALIASES.getOrDefault(normalized, normalized);
    }
}
