package com.deepthoughtnet.clinic.api.help;

import java.util.Map;

final class HelpPageKeyResolver {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("PATIENT_MASTER", "PATIENTS"),
            Map.entry("PATIENT_DETAILS", "PATIENT_DETAILS"),
            Map.entry("PHARMACY_MEDICINE_MASTER", "MEDICINE_MASTER"),
            Map.entry("PHARMACY_DISPENSING", "DISPENSING"),
            Map.entry("PHARMACY", "PHARMACY_DASHBOARD"),
            Map.entry("CLINIC_DASHBOARD", "CLINIC_DASHBOARD"),
            Map.entry("DAY_BOARD", "DAY_BOARD"),
            Map.entry("NOTIFICATIONS", "NOTIFICATIONS"),
            Map.entry("VACCINATIONS", "VACCINATIONS"),
            Map.entry("LAB", "LABORATORY"),
            Map.entry("LAB_OPERATIONS", "LABORATORY"),
            Map.entry("FINANCE_BILLING", "BILLING"),
            Map.entry("BILL_BUILDER", "BILLING"),
            Map.entry("BILLING", "BILLING"),
            Map.entry("FINANCE_REPORTS", "REPORTS"),
            Map.entry("TENANT_REPORTS", "REPORTS"),
            Map.entry("CONSULTATION", "CONSULTATION_WORKSPACE"),
            Map.entry("CONSULTATION_WORKSPACE", "CONSULTATION_WORKSPACE"),
            Map.entry("CONSULTATION_PRESCRIPTION", "CONSULTATION_PRESCRIPTION"),
            Map.entry("CONSULTATION_HISTORY", "CONSULTATION_HISTORY"),
            Map.entry("CONSULTATION_INVESTIGATIONS", "CONSULTATION_INVESTIGATIONS"),
            Map.entry("CONSULTATION_LAB_ORDERS", "CONSULTATION_LAB_ORDERS"),
            Map.entry("CONSULTATION_AI_ASSIST", "CONSULTATION_AI_ASSIST")
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
