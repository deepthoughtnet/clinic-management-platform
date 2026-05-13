package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationSource;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** DTO models for CarePilot webinar automation endpoints. */
public final class WebinarDtos {
    private WebinarDtos() {}

    public record WebinarUpsertRequest(
            String title,
            String description,
            WebinarType webinarType,
            WebinarStatus status,
            String webinarUrl,
            String organizerName,
            String organizerEmail,
            OffsetDateTime scheduledStartAt,
            OffsetDateTime scheduledEndAt,
            String timezone,
            Integer capacity,
            Boolean registrationEnabled,
            Boolean reminderEnabled,
            Boolean followupEnabled,
            String tags
    ) {}

    public record WebinarStatusUpdateRequest(WebinarStatus status) {}

    public record WebinarResponse(
            UUID id,
            UUID tenantId,
            String title,
            String description,
            WebinarType webinarType,
            WebinarStatus status,
            String webinarUrl,
            String organizerName,
            String organizerEmail,
            OffsetDateTime scheduledStartAt,
            OffsetDateTime scheduledEndAt,
            String timezone,
            Integer capacity,
            boolean registrationEnabled,
            boolean reminderEnabled,
            boolean followupEnabled,
            String tags,
            UUID createdBy,
            UUID updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record WebinarListResponse(int page, int size, long total, java.util.List<WebinarResponse> rows) {}

    public record WebinarRegistrationRequest(
            UUID patientId,
            UUID leadId,
            String attendeeName,
            String attendeeEmail,
            String attendeePhone,
            WebinarRegistrationSource source,
            String notes
    ) {}

    public record WebinarAttendanceRequest(
            UUID registrationId,
            WebinarRegistrationStatus registrationStatus,
            String notes
    ) {}

    public record WebinarRegistrationResponse(
            UUID id,
            UUID tenantId,
            UUID webinarId,
            UUID patientId,
            UUID leadId,
            String attendeeName,
            String attendeeEmail,
            String attendeePhone,
            WebinarRegistrationStatus registrationStatus,
            boolean attended,
            OffsetDateTime attendedAt,
            WebinarRegistrationSource source,
            String notes,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record WebinarRegistrationListResponse(int page, int size, long total, java.util.List<WebinarRegistrationResponse> rows) {}

    public record WebinarAnalyticsSummaryResponse(
            long totalWebinars,
            long upcomingWebinars,
            long completedWebinars,
            long totalRegistrations,
            long attendedCount,
            long noShowCount,
            double attendanceRate,
            double noShowRate,
            long webinarConversions,
            Map<String, Long> registrationsBySource,
            long attendeeEngagementCount
    ) {}
}
