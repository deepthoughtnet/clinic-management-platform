package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record PhysicalCountAuditFields(
        @Size(max = 120)
        String createdBy,
        @Size(max = 40)
        String createdAt,
        @Size(max = 120)
        String startedBy,
        @Size(max = 40)
        String startedAt,
        @Size(max = 40)
        String lastUpdatedAt,
        @Size(max = 120)
        String submittedBy,
        @Size(max = 40)
        String submittedAt,
        @Size(max = 120)
        String reviewedBy,
        @Size(max = 40)
        String reviewedAt,
        @Size(max = 120)
        String reviewer,
        @Size(max = 40)
        String reviewedDate,
        @Size(max = 120)
        String approvedBy,
        @Size(max = 40)
        String approvedAt,
        @Size(max = 500)
        String approvalNotes,
        @Size(max = 120)
        String rejectedBy,
        @Size(max = 40)
        String rejectedAt,
        @Size(max = 500)
        String rejectionReason,
        @Size(max = 120)
        String returnedBy,
        @Size(max = 40)
        String returnedAt,
        @Size(max = 500)
        String returnReason,
        @Size(max = 120)
        String postedBy,
        @Size(max = 40)
        String postedAt,
        @Size(max = 120)
        String sessionDuration,
        @Size(max = 500)
        String generalNotes,
        @Size(max = 500)
        String counterNotes,
        @Size(max = 500)
        String reviewerNotes,
        @Size(max = 500)
        String auditNotes,
        @Valid
        PhysicalCountReviewChecklist reviewChecklist
) {
}
