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
    }
}
