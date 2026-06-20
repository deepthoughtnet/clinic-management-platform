package com.deepthoughtnet.clinic.api.help;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HelpPageKeyResolverTest {
    @Test
    void resolvesFrontendAliasesToSeededHelpPageKeys() {
        assertEquals("MEDICINE_MASTER", HelpPageKeyResolver.resolveLookupPageKey("PHARMACY_MEDICINE_MASTER"));
        assertEquals("DISPENSING", HelpPageKeyResolver.resolveLookupPageKey("PHARMACY_DISPENSING"));
        assertEquals("PATIENTS", HelpPageKeyResolver.resolveLookupPageKey("PATIENT_MASTER"));
        assertEquals("PHARMACY_INVENTORY", HelpPageKeyResolver.resolveLookupPageKey("PHARMACY_INVENTORY"));
        assertEquals("BILLING", HelpPageKeyResolver.resolveLookupPageKey("FINANCE_BILLING"));
        assertEquals("BILLING", HelpPageKeyResolver.resolveLookupPageKey("BILL_BUILDER"));
        assertEquals("REPORTS", HelpPageKeyResolver.resolveLookupPageKey("FINANCE_REPORTS"));
        assertEquals("REPORTS", HelpPageKeyResolver.resolveLookupPageKey("TENANT_REPORTS"));
        assertEquals("CLINIC_DASHBOARD", HelpPageKeyResolver.resolveLookupPageKey("CLINIC_DASHBOARD"));
        assertEquals("DAY_BOARD", HelpPageKeyResolver.resolveLookupPageKey("DAY_BOARD"));
        assertEquals("NOTIFICATIONS", HelpPageKeyResolver.resolveLookupPageKey("NOTIFICATIONS"));
        assertEquals("VACCINATIONS", HelpPageKeyResolver.resolveLookupPageKey("VACCINATIONS"));
        assertEquals("CONSULTATION_WORKSPACE", HelpPageKeyResolver.resolveLookupPageKey("CONSULTATION"));
        assertEquals("CONSULTATION_WORKSPACE", HelpPageKeyResolver.resolveLookupPageKey("CONSULTATION_WORKSPACE"));
        assertEquals("CONSULTATION_PRESCRIPTION", HelpPageKeyResolver.resolveLookupPageKey("CONSULTATION_PRESCRIPTION"));
        assertEquals("CONSULTATION_HISTORY", HelpPageKeyResolver.resolveLookupPageKey("CONSULTATION_HISTORY"));
        assertEquals("CONSULTATION_INVESTIGATIONS", HelpPageKeyResolver.resolveLookupPageKey("CONSULTATION_INVESTIGATIONS"));
        assertEquals("CONSULTATION_LAB_ORDERS", HelpPageKeyResolver.resolveLookupPageKey("CONSULTATION_LAB_ORDERS"));
        assertEquals("CONSULTATION_AI_ASSIST", HelpPageKeyResolver.resolveLookupPageKey("CONSULTATION_AI_ASSIST"));
        assertEquals("LABORATORY", HelpPageKeyResolver.resolveLookupPageKey("LAB"));
        assertEquals("LABORATORY", HelpPageKeyResolver.resolveLookupPageKey("LAB_OPERATIONS"));
    }
}
