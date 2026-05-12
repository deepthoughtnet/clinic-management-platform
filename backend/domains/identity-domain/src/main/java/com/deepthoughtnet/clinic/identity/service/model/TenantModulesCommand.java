package com.deepthoughtnet.clinic.identity.service.model;

public record TenantModulesCommand(
        boolean clinicAutomation,
        boolean clinicGeneration,
        boolean reconciliation,
        boolean decisioning,
        boolean aiCopilot,
        boolean agentIntake,
        boolean gstFiling,
        boolean doctorIntelligence,
        boolean teleCalling,
        boolean carePilot
) {}
