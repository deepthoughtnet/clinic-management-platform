package com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto;

import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.ValidationSeverity;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.ValidationState;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CommercialSubscriptionDtos {
    private CommercialSubscriptionDtos() {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    }

    public record SubscriptionStatusCountsResponse(long activeCount, long scheduledCount, long pausedCount, long expiredCount, long cancelledCount) {
    }

    public record ValidationMessageResponse(String field, String code, String message, String remediation, ValidationSeverity severity, boolean blocking) {
    }

    public record ValidationResultResponse(
            ValidationState validationState,
            boolean readyToAssign,
            int blockingFindingCount,
            int warningFindingCount,
            List<ValidationMessageResponse> findings,
            OffsetDateTime validatedAt
    ) {
    }

    public record SubscriptionHistoryResponse(
            UUID id,
            String eventType,
            String previousStatus,
            String newStatus,
            UUID performedBy,
            OffsetDateTime performedAt,
            String remarks
    ) {
    }

    public record SubscriptionSummaryResponse(
            UUID id,
            UUID tenantId,
            UUID planTemplateId,
            String planTemplateCode,
            String planTemplateName,
            UUID publishedVersionId,
            int publishedVersionNumber,
            String publishedVersionLabel,
            SubscriptionStatus subscriptionStatus,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoRenew,
            String displayName,
            String referenceNumber,
            String notes,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record SubscriptionDetailResponse(
            UUID id,
            UUID tenantId,
            UUID planTemplateId,
            String planTemplateCode,
            String planTemplateName,
            UUID publishedVersionId,
            int publishedVersionNumber,
            String publishedVersionLabel,
            SubscriptionStatus subscriptionStatus,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoRenew,
            String displayName,
            String referenceNumber,
            String notes,
            OffsetDateTime createdAt,
            UUID createdBy,
            OffsetDateTime updatedAt,
            UUID updatedBy,
            List<SubscriptionHistoryResponse> history,
            ValidationResultResponse validation
    ) {
    }

    public record CreateSubscriptionRequest(
            UUID tenantId,
            UUID publishedVersionId,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoRenew,
            String displayName,
            String referenceNumber,
            String notes
    ) {
    }

    public record ReplaceSubscriptionRequest(
            UUID publishedVersionId,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoRenew,
            String displayName,
            String referenceNumber,
            String notes
    ) {
    }

    public record LifecycleActionRequest(String remarks) {
    }
}
