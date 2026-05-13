package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs for CarePilot lead management APIs. */
public final class LeadDtos {
    private LeadDtos() {}

    public record LeadResponse(
            UUID id,
            UUID tenantId,
            String firstName,
            String lastName,
            String fullName,
            String phone,
            String email,
            PatientGender gender,
            LocalDate dateOfBirth,
            LeadSource source,
            String sourceDetails,
            UUID campaignId,
            UUID assignedToAppUserId,
            LeadStatus status,
            LeadPriority priority,
            String notes,
            String tags,
            UUID convertedPatientId,
            UUID bookedAppointmentId,
            OffsetDateTime lastContactedAt,
            OffsetDateTime nextFollowUpAt,
            OffsetDateTime lastActivityAt,
            UUID createdBy,
            UUID updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record LeadListResponse(int page, int size, long total, List<LeadResponse> rows) {}

    public record LeadUpsertRequest(
            String firstName,
            String lastName,
            String phone,
            String email,
            PatientGender gender,
            LocalDate dateOfBirth,
            LeadSource source,
            String sourceDetails,
            UUID campaignId,
            UUID assignedToAppUserId,
            LeadStatus status,
            LeadPriority priority,
            String notes,
            String tags,
            OffsetDateTime lastContactedAt,
            OffsetDateTime nextFollowUpAt
    ) {}

    public record LeadStatusUpdateRequest(
            LeadStatus status,
            LeadPriority priority,
            UUID assignedToAppUserId,
            OffsetDateTime lastContactedAt,
            OffsetDateTime nextFollowUpAt,
            String comment
    ) {}

    public record LeadNoteRequest(String note) {}

    public record LeadActivityResponse(
            UUID id,
            UUID tenantId,
            UUID leadId,
            LeadActivityType activityType,
            String title,
            String description,
            LeadStatus oldStatus,
            LeadStatus newStatus,
            String relatedEntityType,
            UUID relatedEntityId,
            UUID createdByAppUserId,
            OffsetDateTime createdAt
    ) {}

    public record LeadActivityListResponse(int page, int size, long total, List<LeadActivityResponse> rows) {}

    public record LeadAppointmentBookingRequest(
            UUID doctorUserId,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String reason,
            String notes,
            AppointmentPriority priority
    ) {}

    public record LeadConvertRequest(
            boolean bookAppointment,
            LeadAppointmentBookingRequest appointment
    ) {}

    public record LeadConversionResponse(UUID leadId, UUID patientId, boolean newlyCreated, UUID appointmentId, String appointmentError) {}

    public record LeadAnalyticsResponse(
            long totalLeads,
            long newLeads,
            long qualifiedLeads,
            long convertedLeads,
            long lostLeads,
            long followUpsDue,
            long followUpsDueToday,
            long overdueFollowUps,
            double conversionRate,
            Map<String, Long> sourceBreakdown,
            long staleLeads,
            long highPriorityActiveLeads,
            long conversionsWithAppointment,
            Double avgHoursToConversion
    ) {}
}
