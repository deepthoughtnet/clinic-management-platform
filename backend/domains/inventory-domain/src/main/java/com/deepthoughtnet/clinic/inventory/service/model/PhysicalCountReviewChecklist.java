package com.deepthoughtnet.clinic.inventory.service.model;

public record PhysicalCountReviewChecklist(
        boolean randomSampleVerified,
        boolean largeVariancesInvestigated,
        boolean batchVerificationComplete,
        boolean supportingRemarksAdded
) {
}
