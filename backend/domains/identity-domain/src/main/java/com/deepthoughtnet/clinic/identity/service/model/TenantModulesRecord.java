package com.deepthoughtnet.clinic.identity.service.model;

public record TenantModulesRecord(
        boolean clinicAutomation,
        boolean clinicGeneration,
        boolean reconciliation,
        boolean decisioning,
        boolean aiCopilot,
        boolean agentIntake,
        boolean gstFiling,
        boolean doctorIntelligence,
        boolean teleCalling
) {}
