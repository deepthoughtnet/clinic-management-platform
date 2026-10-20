package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorUnavailabilityRecord;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.Daypart;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor.QueryType;
import java.time.DayOfWeek;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Explicit healthcare adapter boundary for server-side read-only AI tool execution. */
@Service
public class HealthcareAiReadOnlyToolGateway {
    public static final Set<String> VOICE_READ_ONLY_ALLOWED_TOOLS = Set.of(
            "FIND_DOCTOR", "FIND_DOCTOR_SCHEDULE", "FIND_CLINIC", "FIND_SERVICE", "FIND_SLOTS");

    private final PatientPortalCareAiBusinessLookupService lookup;
    private final AppointmentService appointmentService;
    private final Clock clock;

    @Autowired
    public HealthcareAiReadOnlyToolGateway(
            PatientPortalService patientPortalService,
            PublicCatalogFacade publicCatalogFacade,
            DiscoverReferenceDataService discoverReferenceDataService,
            ClinicProfileService clinicProfileService,
            AppointmentService appointmentService
    ) {
        this(patientPortalService, publicCatalogFacade, discoverReferenceDataService,
                clinicProfileService, appointmentService, Clock.systemUTC());
    }

    HealthcareAiReadOnlyToolGateway(
            PatientPortalService patientPortalService,
            PublicCatalogFacade publicCatalogFacade,
            DiscoverReferenceDataService discoverReferenceDataService,
            ClinicProfileService clinicProfileService,
            AppointmentService appointmentService,
            Clock clock
    ) {
        this.lookup = new PatientPortalCareAiBusinessLookupService(
                patientPortalService, publicCatalogFacade, discoverReferenceDataService, clinicProfileService);
        this.appointmentService = appointmentService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public ToolResult execute(String tool, HealthcareAiExecutionContext context, ToolInput input) {
        if (tool == null || !VOICE_READ_ONLY_ALLOWED_TOOLS.contains(tool)) {
            return ToolResult.denied(tool);
        }
        if (context == null || context.channel() != HealthcareAiChannel.VOICE) {
            return ToolResult.denied(tool);
        }
        return switch (tool) {
            case "FIND_DOCTOR" -> {
                PatientPortalCareAiBusinessLookupService.DoctorLookup lookupResult = lookup.findDoctorsWithDiagnostics(
                        context, input.query(), input.speciality(), input.clinic());
                yield ToolResult.success(tool, lookupResult.doctors(), lookupResult.doctors().size(),
                        lookupResult.sourceCount(), "sourceCount=" + lookupResult.sourceCount()
                                + "; specialty=" + lookupResult.canonicalSpeciality());
            }
            case "FIND_CLINIC" -> {
                List<PublicClinicSummaryResponse> clinics = lookup.findClinics(context, input.query());
                yield ToolResult.success(tool, clinics, clinics.size());
            }
            case "FIND_SERVICE" -> {
                List<DiscoverReferenceOptionRecord> services = lookup.findServices(input.query(), input.location());
                yield ToolResult.success(tool, services, services.size());
            }
            case "FIND_SLOTS" -> {
                yield availabilityResult(tool, context, input);
            }
            case "FIND_DOCTOR_SCHEDULE" -> {
                yield availabilityResult(tool, context, input);
            }
            default -> ToolResult.denied(tool);
        };
    }

    private ToolResult availabilityResult(String tool, HealthcareAiExecutionContext context, ToolInput input) {
        VoiceDoctorCandidateResolution resolution = resolveVoiceDoctor(context, input);
        if (resolution.doctor() == null) {
            VoiceAvailabilityData data = new VoiceAvailabilityData(null, null, input.queryType(), input.date(),
                    input.daypart(), List.of(), List.of(), List.of(), resolution.candidates(), resolution.ambiguous(), 0);
            return ToolResult.success(tool, data, resolution.candidates().size());
        }

        PublicDoctorSummaryResponse doctor = resolution.doctor();
        UUID doctorId;
        try {
            doctorId = UUID.fromString(doctor.publicDoctorId());
        } catch (RuntimeException ex) {
            return ToolResult.success(tool, new VoiceAvailabilityData(doctor.publicDoctorId(),
                    doctor.doctorDisplayName(), input.queryType(), input.date(), input.daypart(),
                    List.of(), List.of(), List.of(), List.of(), false, 0), 0);
        }

        List<DoctorAvailabilityRecord> allSchedule = appointmentService.listDoctorAvailabilities(
                context.tenantId(), doctorId).stream().filter(DoctorAvailabilityRecord::active).toList();
        List<DoctorAvailabilityRecord> relevantSchedule = allSchedule;
        if (input.queryType() != QueryType.RECURRING_SCHEDULE && input.date() != null) {
            relevantSchedule = relevantSchedule.stream()
                    .filter(row -> row.dayOfWeek() == input.date().getDayOfWeek()).toList();
        }
        relevantSchedule = relevantSchedule.stream().filter(row -> matchesDaypart(row.startTime(), row.endTime(),
                row.breakStartTime(), row.breakEndTime(), input.daypart())).toList();

        List<DoctorUnavailabilityRecord> unavailability = input.date() == null
                ? List.of() : appointmentService.listUnavailability(context.tenantId(), doctorId);
        List<DoctorUnavailabilityRecord> dateBlocks = unavailability == null ? List.of() : unavailability.stream()
                .filter(DoctorUnavailabilityRecord::active)
                .filter(block -> overlapsDate(block, input.date(), input.timezone()))
                .toList();
        List<VoiceScheduleWindow> effectiveSchedule = input.date() == null
                ? relevantSchedule.stream().map(row -> window(row, input.daypart())).toList()
                : effectiveWindows(relevantSchedule, input.date(), input.daypart(), dateBlocks, input.timezone());
        boolean unavailableForDate = !relevantSchedule.isEmpty() && effectiveSchedule.isEmpty()
                && !dateBlocks.isEmpty();

        List<VoiceSlot> allBookableSlots = List.of();
        List<Daypart> availableDayparts = List.of();
        List<DoctorAvailabilitySlotRecord> datedSlots = List.of();
        boolean scheduleOnly = input.queryType() == QueryType.RECURRING_SCHEDULE
                || input.queryType() == QueryType.EFFECTIVE_DATE_SCHEDULE;
        if (!scheduleOnly && input.date() != null) {
            ZoneId zone = input.timezone() == null ? ZoneId.of("Asia/Kolkata") : input.timezone();
            List<DoctorAvailabilitySlotRecord> generated = appointmentService.listSlots(
                    context.tenantId(), doctorId, input.date(), zone);
            datedSlots = generated;
            allBookableSlots = generated.stream().filter(DoctorAvailabilitySlotRecord::bookable)
                    .filter(slot -> slotStartsAfterNow(input.date(), slot.slotTime(), zone))
                    .map(slot -> new VoiceSlot(slot.appointmentDate(), slot.slotTime(), slot.slotEndTime())).toList();
            List<VoiceSlot> generatedBookableSlots = allBookableSlots;
            availableDayparts = java.util.Arrays.stream(Daypart.values())
                    .filter(part -> generatedBookableSlots.stream().anyMatch(slot -> isInDaypart(slot.startTime(), part)))
                    .toList();
        }
        List<VoiceSlot> selectedSlots = input.daypart() == null ? allBookableSlots
                : allBookableSlots.stream().filter(slot -> isInDaypart(slot.startTime(), input.daypart())).toList();
        List<DoctorAvailabilitySlotRecord> selectedDatedSlots = input.daypart() == null ? datedSlots
                : datedSlots.stream().filter(slot -> isInDaypart(slot.slotTime(), input.daypart())).toList();
        boolean onlyPastSlots = !selectedDatedSlots.isEmpty()
                && selectedDatedSlots.stream().allMatch(slot -> slot.past()
                || !slotStartsAfterNow(input.date(), slot.slotTime(), input.timezone() == null
                ? ZoneId.of("Asia/Kolkata") : input.timezone()));
        boolean fullSlotsOnly = !selectedDatedSlots.isEmpty()
                && selectedDatedSlots.stream().allMatch(slot -> !slot.bookable()
                && slot.status() == com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus.FULL);
        VoiceAvailabilityData data = new VoiceAvailabilityData(doctor.publicDoctorId(), doctor.doctorDisplayName(),
                input.queryType(), input.date(), input.daypart(), effectiveSchedule,
                selectedSlots, availableDayparts, List.of(new VoiceDoctorOption(doctor.publicDoctorId(),
                doctor.doctorDisplayName(), doctor.speciality())), false, input.pageOffset(), unavailableForDate,
                onlyPastSlots, fullSlotsOnly);
        return ToolResult.success(tool, data, selectedSlots.size(), allBookableSlots.size(),
                "scheduleWindows=" + effectiveSchedule.size() + "; slotCount=" + selectedSlots.size());
    }

    private boolean slotStartsAfterNow(LocalDate date, LocalTime start, ZoneId zone) {
        ZoneId localZone = zone == null ? ZoneId.of("Asia/Kolkata") : zone;
        LocalDate today = LocalDate.now(clock.withZone(localZone));
        if (date == null || !date.equals(today)) return true;
        LocalTime now = LocalTime.now(clock.withZone(localZone));
        return start != null && start.isAfter(now);
    }

    private List<VoiceScheduleWindow> effectiveWindows(List<DoctorAvailabilityRecord> schedule, LocalDate date,
                                                        Daypart daypart, List<DoctorUnavailabilityRecord> blocks,
                                                        ZoneId timezone) {
        ZoneId zone = timezone == null ? ZoneId.of("Asia/Kolkata") : timezone;
        List<VoiceScheduleWindow> windows = schedule.stream().map(row -> window(row, daypart)).toList();
        for (DoctorUnavailabilityRecord block : blocks) {
            LocalTime blockStart = block.startAt().atZoneSameInstant(zone).toLocalDate().isBefore(date)
                    ? LocalTime.MIN : block.startAt().atZoneSameInstant(zone).toLocalTime();
            LocalTime blockEnd = block.endAt().atZoneSameInstant(zone).toLocalDate().isAfter(date)
                    ? LocalTime.MAX : block.endAt().atZoneSameInstant(zone).toLocalTime();
            windows = windows.stream().flatMap(window -> subtract(window, blockStart, blockEnd).stream()).toList();
        }
        return windows;
    }

    private List<VoiceScheduleWindow> subtract(VoiceScheduleWindow window, LocalTime blockStart, LocalTime blockEnd) {
        LocalTime start = window.startTime();
        LocalTime end = window.endTime();
        if (!blockStart.isBefore(end) || !blockEnd.isAfter(start)) return List.of(window);
        List<VoiceScheduleWindow> remaining = new java.util.ArrayList<>(2);
        if (blockStart.isAfter(start)) {
            remaining.add(new VoiceScheduleWindow(window.dayOfWeek(), start, min(blockStart, end), null, null,
                    window.consultationDurationMinutes()));
        }
        if (blockEnd.isBefore(end)) {
            remaining.add(new VoiceScheduleWindow(window.dayOfWeek(), max(blockEnd, start), end, null, null,
                    window.consultationDurationMinutes()));
        }
        return remaining;
    }

    private LocalTime min(LocalTime left, LocalTime right) { return left.isBefore(right) ? left : right; }
    private LocalTime max(LocalTime left, LocalTime right) { return left.isAfter(right) ? left : right; }

    private boolean overlapsDate(DoctorUnavailabilityRecord block, LocalDate date, ZoneId timezone) {
        ZoneId zone = timezone == null ? ZoneId.of("Asia/Kolkata") : timezone;
        return block.startAt().atZoneSameInstant(zone).toLocalDate().compareTo(date.plusDays(1)) < 0
                && block.endAt().atZoneSameInstant(zone).toLocalDate().compareTo(date) >= 0;
    }

    private VoiceDoctorCandidateResolution resolveVoiceDoctor(HealthcareAiExecutionContext context, ToolInput input) {
        if (input.doctorId() != null) {
            PublicDoctorSummaryResponse doctor = lookup.findDoctorById(context, input.doctorId());
            return new VoiceDoctorCandidateResolution(doctor, List.of(), false);
        }
        if (input.query() == null || input.query().isBlank()) {
            return new VoiceDoctorCandidateResolution(null, List.of(), false);
        }
        List<PublicDoctorSummaryResponse> matches = lookup.findDoctorsWithDiagnostics(
                context, input.query(), null, input.clinic()).doctors();
        if (matches.size() == 1) return new VoiceDoctorCandidateResolution(matches.getFirst(), List.of(), false);
        List<VoiceDoctorOption> candidates = matches.stream().limit(3)
                .map(doctor -> new VoiceDoctorOption(doctor.publicDoctorId(), doctor.doctorDisplayName(), doctor.speciality()))
                .toList();
        return new VoiceDoctorCandidateResolution(null, candidates, matches.size() > 1);
    }

    private VoiceScheduleWindow window(DoctorAvailabilityRecord row, Daypart daypart) {
        if (daypart == null) {
            return new VoiceScheduleWindow(row.dayOfWeek(), row.startTime(), row.endTime(), row.breakStartTime(),
                    row.breakEndTime(), row.consultationDurationMinutes());
        }
        LocalTime lower = daypartStart(daypart);
        LocalTime upper = daypartEnd(daypart);
        LocalTime start = row.startTime().isAfter(lower) ? row.startTime() : lower;
        LocalTime end = row.endTime().isBefore(upper) ? row.endTime() : upper;
        LocalTime breakStart = row.breakStartTime() != null && row.breakStartTime().isAfter(start)
                ? row.breakStartTime() : null;
        LocalTime breakEnd = row.breakEndTime() != null && row.breakEndTime().isBefore(end)
                ? row.breakEndTime() : null;
        if (breakStart == null || breakEnd == null) {
            breakStart = null;
            breakEnd = null;
        }
        return new VoiceScheduleWindow(row.dayOfWeek(), start, end, breakStart, breakEnd,
                row.consultationDurationMinutes());
    }

    private LocalTime daypartStart(Daypart daypart) {
        return switch (daypart) {
            case MORNING -> LocalTime.MIN;
            case AFTERNOON -> LocalTime.NOON;
            case EVENING -> LocalTime.of(17, 0);
        };
    }

    private LocalTime daypartEnd(Daypart daypart) {
        return switch (daypart) {
            case MORNING -> LocalTime.NOON;
            case AFTERNOON -> LocalTime.of(17, 0);
            case EVENING -> LocalTime.MAX;
        };
    }

    private boolean matchesDaypart(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd,
                                   Daypart daypart) {
        if (daypart == null) return true;
        LocalTime lower = daypartStart(daypart);
        LocalTime upper = daypartEnd(daypart);
        LocalTime clippedStart = start.isAfter(lower) ? start : lower;
        LocalTime clippedEnd = end.isBefore(upper) ? end : upper;
        if (!clippedStart.isBefore(clippedEnd)) return false;
        if (breakStart == null || breakEnd == null || !breakStart.isBefore(clippedEnd)
                || !breakEnd.isAfter(clippedStart)) return true;
        return clippedStart.isBefore(breakStart) || clippedEnd.isAfter(breakEnd);
    }

    private boolean isInDaypart(LocalTime time, Daypart daypart) {
        if (time == null || daypart == null) return true;
        return switch (daypart) {
            case MORNING -> time.isBefore(LocalTime.NOON);
            case AFTERNOON -> !time.isBefore(LocalTime.NOON) && time.isBefore(LocalTime.of(17, 0));
            case EVENING -> !time.isBefore(LocalTime.of(17, 0));
        };
    }

    public record VoiceDoctorOption(String doctorId, String displayName, String specialty) { }
    public record VoiceScheduleWindow(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                                      LocalTime breakStartTime, LocalTime breakEndTime,
                                      Integer consultationDurationMinutes) { }
    public record VoiceSlot(LocalDate date, LocalTime startTime, LocalTime endTime) { }
    public record VoiceAvailabilityData(String doctorId, String doctorName, QueryType queryType,
                                        LocalDate date, Daypart requestedDaypart,
                                        List<VoiceScheduleWindow> configuredScheduleWindows,
                                        List<VoiceSlot> bookableSlots, List<Daypart> availableDayparts,
                                        List<VoiceDoctorOption> doctorCandidates, boolean ambiguousDoctor,
                                        int displayOffset, boolean unavailableForDate,
                                        boolean onlyPastSlots, boolean fullSlotsOnly) {
        public VoiceAvailabilityData(String doctorId, String doctorName, QueryType queryType,
                                     LocalDate date, Daypart requestedDaypart,
                                     List<VoiceScheduleWindow> configuredScheduleWindows,
                                     List<VoiceSlot> bookableSlots, List<Daypart> availableDayparts,
                                     List<VoiceDoctorOption> doctorCandidates, boolean ambiguousDoctor,
                                     int displayOffset) {
            this(doctorId, doctorName, queryType, date, requestedDaypart, configuredScheduleWindows,
                    bookableSlots, availableDayparts, doctorCandidates, ambiguousDoctor, displayOffset,
                    false, false, false);
        }

        public VoiceAvailabilityData {
            configuredScheduleWindows = configuredScheduleWindows == null ? List.of() : List.copyOf(configuredScheduleWindows);
            bookableSlots = bookableSlots == null ? List.of() : List.copyOf(bookableSlots);
            availableDayparts = availableDayparts == null ? List.of() : List.copyOf(availableDayparts);
            doctorCandidates = doctorCandidates == null ? List.of() : List.copyOf(doctorCandidates);
            displayOffset = Math.max(0, displayOffset);
        }
    }
    private record VoiceDoctorCandidateResolution(PublicDoctorSummaryResponse doctor,
                                                  List<VoiceDoctorOption> candidates, boolean ambiguous) { }

    public record ToolInput(String query, String speciality, String clinic, String location,
                            String doctorId, LocalDate date, QueryType queryType, Daypart daypart,
                            ZoneId timezone, int pageOffset) {
        public ToolInput(String query, String speciality, String clinic, String location,
                         String doctorId, LocalDate date) {
            this(query, speciality, clinic, location, doctorId, date, QueryType.SLOT_AVAILABILITY,
                    null, ZoneId.of("Asia/Kolkata"), 0);
        }
        public ToolInput {
            queryType = queryType == null ? QueryType.SLOT_AVAILABILITY : queryType;
            timezone = timezone == null ? ZoneId.of("Asia/Kolkata") : timezone;
            pageOffset = Math.max(0, pageOffset);
        }
        public static ToolInput empty() { return new ToolInput(null, null, null, null, null, null); }
    }

    public record ToolResult(String tool, boolean allowed, boolean success, Object value, int candidateCount,
                             int sourceCandidateCount, String diagnostics) {
        static ToolResult denied(String tool) { return new ToolResult(tool, false, false, null, 0, 0, null); }
        static ToolResult success(String tool, Object value, int count) {
            return new ToolResult(tool, true, true, value, count, count, null);
        }
        static ToolResult success(String tool, Object value, int count, int sourceCount, String diagnostics) {
            return new ToolResult(tool, true, true, value, count, sourceCount, diagnostics);
        }
    }
}
