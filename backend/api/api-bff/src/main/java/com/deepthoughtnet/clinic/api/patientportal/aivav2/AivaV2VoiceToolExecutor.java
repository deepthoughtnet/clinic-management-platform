package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;

/** Channel-neutral bridge from the V2 Voice runtime to a healthcare tool adapter. */
@FunctionalInterface
public interface AivaV2VoiceToolExecutor {
    ToolResult execute(String tool, AivaExecutionContext context, ToolInput input);

    enum QueryType {
        RECURRING_SCHEDULE,
        EFFECTIVE_DATE_SCHEDULE,
        EFFECTIVE_DATE_AVAILABILITY,
        SLOT_AVAILABILITY
    }

    record ToolInput(String query, String speciality, String clinic, String location,
                     String doctorId, LocalDate date, QueryType queryType,
                     NormalizedUserTurn.Daypart daypart, ZoneId timezone, int pageOffset,
                     AvailabilityTimeConstraint availabilityTimeConstraint) {
        public ToolInput(String query, String speciality, String clinic, String location,
                         String doctorId, LocalDate date, QueryType queryType,
                         NormalizedUserTurn.Daypart daypart, ZoneId timezone, int pageOffset) {
            this(query, speciality, clinic, location, doctorId, date, queryType, daypart, timezone, pageOffset, null);
        }
        public ToolInput(String query, String speciality, String clinic, String location,
                         String doctorId, LocalDate date) {
            this(query, speciality, clinic, location, doctorId, date, QueryType.SLOT_AVAILABILITY,
                    null, ZoneId.of("Asia/Kolkata"), 0, null);
        }
        public ToolInput {
            queryType = queryType == null ? QueryType.SLOT_AVAILABILITY : queryType;
            timezone = timezone == null ? ZoneId.of("Asia/Kolkata") : timezone;
            pageOffset = Math.max(0, pageOffset);
        }
    }

    record ProviderOption(String doctorId, String displayName, String specialty,
                          String clinicId, String tenantId, String clinicSlug, String clinicName,
                          AivaV2Models.ProviderSource source, AivaV2Models.ProviderCapabilities capabilities,
                          String bookingReference) {
        public ProviderOption(String doctorId, String displayName, String specialty,
                              String clinicId, String tenantId, String clinicSlug, String clinicName) {
            this(doctorId, displayName, specialty, clinicId, tenantId, clinicSlug, clinicName, null, null, null);
        }
    }

    record ToolResult(boolean allowed, boolean success, Object value, int candidateCount,
                      int sourceCandidateCount, String diagnostics, List<ProviderOption> providers,
                      AivaV2Models.AvailabilityResult availabilityResult) {
        public ToolResult(boolean allowed, boolean success, Object value, int candidateCount,
                          int sourceCandidateCount, String diagnostics, List<ProviderOption> providers) {
            this(allowed, success, value, candidateCount, sourceCandidateCount, diagnostics, providers, null);
        }

        public ToolResult {
            providers = providers == null ? List.of() : List.copyOf(providers);
        }

        public static ToolResult denied(String diagnostics) {
            return new ToolResult(false, false, null, 0, 0, diagnostics, List.of(), null);
        }
    }
}
