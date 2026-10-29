package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraintMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingReceipt;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ResolutionStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.DevanagariNameTransliterator;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.DoctorNameNormalizer;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorAvailabilityDayResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorAvailabilityResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiDoctorOption;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiEntityRegistry;
import com.deepthoughtnet.clinic.api.patientportal.careai.SpecialtyResolver;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class AivaV2BookingTools {
    private static final Logger log = LoggerFactory.getLogger(AivaV2BookingTools.class);
    private static final long CONFIRMATION_TTL_SECONDS = 5 * 60;

    private final PatientPortalService patientPortalService;
    private final PublicCatalogFacade publicCatalogFacade;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final Clock clock;
    private final SpecialtyResolver specialtyResolver = new SpecialtyResolver(new PatientPortalCareAiEntityRegistry());
    private Environment environment;

    @Autowired
    AivaV2BookingTools(PatientPortalService patientPortalService, PublicCatalogFacade publicCatalogFacade,
                       ClinicTimeZoneResolver clinicTimeZoneResolver) {
        this(patientPortalService, publicCatalogFacade, clinicTimeZoneResolver, Clock.systemUTC());
    }

    AivaV2BookingTools(PatientPortalService patientPortalService, PublicCatalogFacade publicCatalogFacade, Clock clock) {
        this(patientPortalService, publicCatalogFacade, null, clock);
    }

    AivaV2BookingTools(PatientPortalService patientPortalService, PublicCatalogFacade publicCatalogFacade,
                       ClinicTimeZoneResolver clinicTimeZoneResolver, Clock clock) {
        this.patientPortalService = patientPortalService;
        this.publicCatalogFacade = publicCatalogFacade;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.clock = clock;
    }

    @Autowired
    void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    ProviderSearchResult resolveBookingProvider(String doctorText, String specialtyText) {
        return resolveBookingProvider(doctorText, specialtyText, null);
    }

    SpecialtyMatch resolveSpecialty(String rawText) {
        List<String> supported = mergedProviders().stream()
                .map(ProviderCandidate::specialty)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        SpecialtyResolver.SpecialtyResolution resolution = specialtyResolver.resolve(rawText, supported);
        return new SpecialtyMatch(resolution.status().name(), resolution.canonicalSpecialty(), resolution.candidates());
    }

    record SpecialtyMatch(String status, String canonical, List<String> candidates) {
        SpecialtyMatch {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    ProviderSearchResult resolveBookingProvider(String doctorText, String specialtyText, String clinicText) {
        return resolveBookingProvider(doctorText, specialtyText, clinicText, mergedProviders());
    }

    /** Voice preparation resolves only inside the tenant supplied by trusted DID routing. */
    ProviderSearchResult resolveBookingProviderForTenant(String doctorText, String specialtyText,
                                                          String clinicText, UUID tenantId) {
        return resolveBookingProvider(doctorText, specialtyText, clinicText, mergedProviders(tenantId));
    }

    /**
     * Canonical boundary for any provider candidate entering a transactional draft.
     * Discovery projections are resolved against the trusted tenant and their identity
     * is checked against the canonical result before a draft may retain them.
     */
    ProviderCandidate adoptCanonicalBookingProvider(UUID tenantId, ProviderCandidate evidence) {
        if (tenantId == null || evidence == null || !StringUtils.hasText(evidence.doctorId())) return null;
        if (!StringUtils.hasText(evidence.displayName())) return null;
        ProviderSearchResult result = resolveBookingProviderForTenant(
                evidence.displayName(), null, evidence.clinicSlug(), tenantId);
        ProviderCandidate canonical = result == null || result.status() != ResolutionStatus.RESOLVED
                ? null : result.resolvedProvider();
        if (canonical == null || !evidence.doctorId().equals(canonical.doctorId())
                || !tenantId.toString().equals(canonical.tenantId())
                || (StringUtils.hasText(evidence.clinicId())
                && !evidence.clinicId().equals(canonical.clinicId()))) return null;
        return canonical;
    }

    private ProviderSearchResult resolveBookingProvider(String doctorText, String specialtyText, String clinicText,
                                                          List<ProviderCandidate> all) {
        Instant now = Instant.now(clock);
        String fingerprint = fingerprint("provider", normalize(doctorText), normalize(specialtyText), normalize(clinicText));
        log.info("AIVA_V2_PROVIDER_TRACE authorizedClinicCount={} providerCandidateCount={} candidateClinicCount={}",
                all.stream().map(ProviderCandidate::tenantId).filter(StringUtils::hasText).distinct().count(),
                all.size(), all.stream().map(ProviderCandidate::clinicDisplayName).filter(StringUtils::hasText).distinct().count());
        List<ProviderCandidate> specialtyMatches = all.stream()
                .filter(candidate -> !StringUtils.hasText(specialtyText)
                        || contains(candidate.specialty(), specialtyText))
                .filter(candidate -> !StringUtils.hasText(clinicText)
                        || contains(candidate.clinicDisplayName(), clinicText)
                        || contains(candidate.clinicSlug(), clinicText))
                .toList();
        if (!StringUtils.hasText(doctorText)) {
            return resultForCandidates(fingerprint, specialtyMatches, now, false);
        }

        String query = normalizeDoctorName(doctorText);
        List<ProviderCandidate> exact = specialtyMatches.stream()
                .filter(candidate -> normalizeDoctorName(candidate.displayName()).equals(query)
                        || DoctorNameNormalizer.matches(candidate.displayName(), doctorText))
                .toList();
        if (!exact.isEmpty()) {
            return resultForCandidates(fingerprint, exact, now, false);
        }
        List<ProviderCandidate> partial = specialtyMatches.stream()
                .filter(candidate -> contains(normalizeDoctorName(candidate.displayName()), query))
                .toList();
        if (!partial.isEmpty()) {
            return resultForCandidates(fingerprint, partial, now, false);
        }
        List<ProviderCandidate> suggestions = specialtyMatches.stream()
                .filter(candidate -> editDistance(query, normalizeDoctorName(candidate.displayName())) <= 2)
                .limit(3)
                .toList();
        if (!suggestions.isEmpty()) {
            return new ProviderSearchResult(UUID.randomUUID(), ResolutionStatus.SUGGESTION, fingerprint,
                    suggestions, null, now, now.plusSeconds(5 * 60));
        }
        return new ProviderSearchResult(UUID.randomUUID(), ResolutionStatus.NOT_FOUND, fingerprint,
                List.of(), null, now, now.plusSeconds(5 * 60));
    }

    ToolResult<AvailabilityResult> getBookingAvailability(BookingDraft draft, UUID tenantId) {
        if (tenantId == null) return ToolResult.failure("TENANT_REQUIRED", "Tenant scope is required.");
        if (draft == null || draft.selectedProvider() == null || draft.preferredDate() == null) {
            return ToolResult.failure("NEEDS_INPUT", "Provider and date are required.");
        }
        return getAvailability(draft.selectedProvider(), draft.preferredDate(), draft.preferredTimeWindow(),
                effectiveConstraint(draft), draft.draftId(), draft.revision(), criteriaFingerprint(draft), tenantId);
    }

    ToolResult<AvailabilityResult> getNextBookingAvailability(BookingDraft draft, UUID tenantId) {
        if (tenantId == null) return ToolResult.failure("TENANT_REQUIRED", "Tenant scope is required.");
        if (draft == null || draft.selectedProvider() == null || draft.preferredDate() == null) {
            return ToolResult.failure("NEEDS_INPUT", "Provider and date are required.");
        }
        ProviderCandidate provider = draft.selectedProvider();
        if (provider.capabilities() == null || !provider.capabilities().onlineBooking()
                || !provider.capabilities().liveAvailability()) {
            return ToolResult.failure("NOT_BOOKABLE", "Online scheduling is not connected for this doctor.");
        }
        try {
            PatientPortalDoctorAvailabilityResponse response = patientPortalService.doctorAvailability(
                    provider.bookingReference(), provider.doctorId(), provider.clinicSlug(),
                    tenantId.toString(), provider.clinicId(), draft.preferredDate());
            PatientPortalDoctorAvailabilityDayResponse next = response == null || response.nextAvailable() == null
                    ? null
                    : response.nextAvailable().stream()
                    .filter(day -> day != null && day.appointmentDate() != null
                            && day.appointmentDate().isAfter(draft.preferredDate()))
                    .map(day -> new PatientPortalDoctorAvailabilityDayResponse(day.appointmentDate(),
                            filteredSlots(day.slots(), draft.preferredTimeWindow(), effectiveConstraint(draft))))
                    .filter(day -> day.slots() != null && !day.slots().isEmpty())
                    .findFirst().orElse(null);
            if (next == null) return ToolResult.failure("NO_FUTURE_AVAILABILITY",
                    "No available slots were found in the current booking window.");
            Instant now = Instant.now(clock);
            ZoneId zone = clinicTimeZoneResolver == null
                    ? ZoneId.of("Asia/Kolkata")
                    : clinicTimeZoneResolver.resolve(tenantId);
            List<AvailabilitySlot> slots = next.slots().stream()
                    .map(slot -> new AvailabilitySlot(slot.slotReference(), slot.slotTime(), slot.slotEndTime(),
                            slot.slotTime() == null ? null : slot.slotTime().toString()))
                    .toList();
            return ToolResult.success(new AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(),
                    criteriaFingerprint(draft), provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                    next.appointmentDate(), draft.preferredTimeWindow(), effectiveConstraint(draft), zone.getId(),
                    slots, null, slots.size() > 3, now, now.plusSeconds(5 * 60), 0, 3));
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return ToolResult.failure(ex.getStatusCode().value() == 409 ? "STALE" : "NOT_BOOKABLE", ex.getReason());
        } catch (RuntimeException ex) {
            return ToolResult.failure("FAILED", "Availability could not be retrieved.");
        }
    }

    private List<PatientPortalDoctorSlotResponse> filteredSlots(List<PatientPortalDoctorSlotResponse> slots,
                                                                  String timeWindow,
                                                                  AvailabilityTimeConstraint constraint) {
        if (slots == null) return List.of();
        return slots.stream()
                .filter(PatientPortalDoctorSlotResponse::selectable)
                .filter(slot -> matchesWindow(slot.slotTime(), timeWindow))
                .filter(slot -> matchesTimeConstraint(slot.slotTime(), constraint))
                .toList();
    }

    ToolResult<AvailabilityResult> getAvailability(ProviderCandidate provider, LocalDate date, String timeWindow,
                                                   AvailabilityTimeConstraint constraint, UUID stateId, long stateRevision,
                                                   String requestedFingerprint, UUID tenantId) {
        if (tenantId == null) return ToolResult.failure("TENANT_REQUIRED", "Tenant scope is required.");
        if (provider == null || date == null) {
            return ToolResult.failure("NEEDS_INPUT", "Provider and date are required.");
        }
        if (provider.capabilities() == null || !provider.capabilities().onlineBooking()
                || !provider.capabilities().liveAvailability()) {
            return ToolResult.failure("NOT_BOOKABLE", "Online scheduling is not connected for this doctor.");
        }
        String stage = "CANONICAL_AVAILABILITY";
        try {
            stage = "PatientPortalService.doctorSlotsForTenant";
            log.info("AIVA_V2_AVAILABILITY_CALL_START stage={} tenantId={} providerId={} date={}",
                    stage, tenantId, provider.doctorId(), date);
            List<PatientPortalDoctorSlotResponse> source = canonicalSlots(provider, date, tenantId);
            log.info("AIVA_V2_AVAILABILITY_CALL_RESULT tenantId={} providerId={} date={} slotCount={} success=true",
                    tenantId, provider.doctorId(), date, source == null ? 0 : source.size());
            stage = "SLOT_FILTERING";
            List<AvailabilitySlot> slots = source.stream()
                    .filter(PatientPortalDoctorSlotResponse::selectable)
                    .filter(slot -> matchesWindow(slot.slotTime(), timeWindow))
                    .filter(slot -> matchesTimeConstraint(slot.slotTime(), constraint))
                    .map(slot -> new AvailabilitySlot(slot.slotReference(), slot.slotTime(), slot.slotEndTime(),
                            slot.slotTime() == null ? null : slot.slotTime().toString()))
                    .toList();
            Instant now = Instant.now(clock);
            stage = "TENANT_TIMEZONE_RESOLUTION";
            ZoneId zone = clinicTimeZoneResolver == null
                    ? ZoneId.of("Asia/Kolkata")
                    : clinicTimeZoneResolver.resolve(tenantId);
            AvailabilityResult result = new AvailabilityResult(UUID.randomUUID(), stateId, stateRevision,
                    StringUtils.hasText(requestedFingerprint) ? requestedFingerprint : fingerprint("availability", provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                            provider.tenantId(), date.toString(), timeWindow, String.valueOf(constraint)),
                    provider.providerHandle(), provider.doctorId(), provider.clinicId(), date, timeWindow, constraint, zone.getId(), slots, null,
                    slots.size() > 3, now, now.plusSeconds(5 * 60), 0, 3);
            return ToolResult.success(result);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return ToolResult.failure(ex.getStatusCode().value() == 409 ? "STALE" : "NOT_BOOKABLE", ex.getReason());
        } catch (RuntimeException ex) {
            Throwable root = rootCause(ex);
            if (isLocalRuntime()) {
                log.warn("AIVA_V2_BOOKING_AVAILABILITY_FAILED stage={} providerId={} tenantId={} date={} "
                                + "exceptionClass={} exceptionMessage={} rootCauseClass={} rootCauseMessage={}",
                        stage, provider.doctorId(), tenantId, date, ex.getClass().getName(), ex.getMessage(),
                        root.getClass().getName(), root.getMessage(), ex);
            } else {
                log.warn("AIVA_V2_BOOKING_AVAILABILITY_FAILED stage={} providerId={} tenantId={} date={} exceptionClass={}",
                        stage, provider.doctorId(), tenantId, date, ex.getClass().getName());
            }
            return ToolResult.failure("FAILED", "Availability could not be retrieved.");
        }
    }

    private boolean isLocalRuntime() {
        if (environment == null) return false;
        String configuredEnvironment = environment.getProperty("JEEVANAM_ENVIRONMENT");
        if (StringUtils.hasText(configuredEnvironment)) {
            return "LOCAL".equalsIgnoreCase(configuredEnvironment.trim());
        }
        return environment.acceptsProfiles(Profiles.of("local", "docker"));
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** Compatibility boundary retained for the existing reschedule adapter. */
    ToolResult<AvailabilityResult> getAvailability(ProviderCandidate provider, LocalDate date, String timeWindow,
                                                   AvailabilityTimeConstraint constraint, UUID stateId,
                                                   long stateRevision, String requestedFingerprint) {
        UUID tenantId = provider == null ? null : parseTenantId(provider.tenantId());
        return getAvailability(provider, date, timeWindow, constraint, stateId, stateRevision,
                requestedFingerprint, tenantId);
    }

    /**
     * Use the same tenant-scoped AppointmentService.listSlots source as Voice
     * FIND_SLOTS. The portal-facing doctorAvailability method requires an
     * authenticated Patient Portal request context and is not a Voice boundary.
     */
    private List<PatientPortalDoctorSlotResponse> canonicalSlots(ProviderCandidate provider, LocalDate date,
                                                                 UUID tenantId) {
        return patientPortalService.doctorSlotsForTenant(tenantId, provider.doctorId(),
                provider.bookingReference(), date);
    }

    ToolResult<BookingConfirmation> prepareBooking(BookingDraft draft, AvailabilityResult availability, UUID tenantId) {
        ToolResult<RevalidatedBookingSlot> revalidated = revalidateBookingSlot(draft, availability, tenantId);
        if (!"SUCCESS".equals(revalidated.category())) {
            return ToolResult.failure(revalidated.category(), revalidated.safeReason());
        }
        AvailabilitySlot selected = revalidated.value().slot();
        Instant now = Instant.now(clock);
        String confirmationRef = "v2-confirm-" + UUID.randomUUID();
        BookingConfirmation confirmation = new BookingConfirmation(confirmationRef, draft.draftId(), draft.revision(),
                draft.selectedProvider().providerHandle(), draft.selectedProvider().doctorId(),
                draft.selectedProvider().clinicId(), availability.requestId(), selected.slotReference(),
                draft.preferredDate(), selected.startsAt(), now.plusSeconds(CONFIRMATION_TTL_SECONDS),
                "aiva-v2-" + UUID.nameUUIDFromBytes(confirmationRef.getBytes(StandardCharsets.UTF_8)));
        return ToolResult.success(confirmation);
    }

    /** Revalidates a selected canonical slot without creating confirmation or executing a mutation. */
    ToolResult<RevalidatedBookingSlot> revalidateBookingSlot(BookingDraft draft, AvailabilityResult availability,
                                                              UUID tenantId) {
        if (tenantId == null) return ToolResult.failure("TENANT_REQUIRED", "Tenant scope is required.");
        if (draft == null || availability == null || draft.selectedProvider() == null
                || draft.revision() != availability.draftRevision()
                || !availability.requestId().equals(draft.latestAvailabilityRequestId())
                || !StringUtils.hasText(draft.selectedSlotReference())) {
            return ToolResult.failure("STALE", "The selected availability is no longer current.");
        }
        ToolResult<AvailabilityResult> refreshed = getBookingAvailability(draft, tenantId);
        if (!"SUCCESS".equals(refreshed.category())) {
            return ToolResult.failure(refreshed.category(), refreshed.safeReason());
        }
        AvailabilitySlot selected = refreshed.value().slots().stream()
                .filter(slot -> draft.selectedSlotReference().equals(slot.slotReference()))
                .findFirst().orElse(null);
        if (selected == null) return ToolResult.failure("STALE", "The selected slot is no longer available.");
        return ToolResult.success(new RevalidatedBookingSlot(refreshed.value(), selected));
    }

    record RevalidatedBookingSlot(AvailabilityResult availability, AvailabilitySlot slot) { }

    ToolResult<AvailabilitySlot> selectBookingSlot(BookingDraft draft, AvailabilityResult availability, Selection selection) {
        if (draft == null || availability == null || selection == null
                || !availability.draftId().equals(draft.draftId())
                || availability.draftRevision() != draft.revision()
                || !availability.requestId().equals(draft.latestAvailabilityRequestId())
                || !availability.criteriaFingerprint().equals(criteriaFingerprint(draft))) {
            return ToolResult.failure("STALE", "Those slots are no longer current.");
        }
        AvailabilitySlot selected = null;
        int pageStart = Math.min(availability.displayOffset(), availability.slots().size());
        int pageEnd = Math.min(availability.slots().size(), pageStart + availability.pageSize());
        if (StringUtils.hasText(selection.slotRef())) {
            selected = availability.slots().subList(pageStart, pageEnd).stream()
                    .filter(slot -> selection.slotRef().equals(slot.slotReference())).findFirst().orElse(null);
        } else if (StringUtils.hasText(selection.exactTime())) {
            try {
                LocalTime time = LocalTime.parse(selection.exactTime());
                List<AvailabilitySlot> matches = availability.slots().subList(pageStart, pageEnd).stream()
                        .filter(slot -> time.equals(slot.startsAt())).toList();
                selected = matches.size() == 1 ? matches.get(0) : null;
            } catch (RuntimeException ignored) { }
        } else if (selection.ordinal() != null) {
            int index = selection.ordinal() == -1 ? pageEnd - 1 : pageStart + selection.ordinal() - 1;
            selected = index >= pageStart && index < pageEnd ? availability.slots().get(index) : null;
        }
        return selected == null ? ToolResult.failure("NEEDS_INPUT", "Select one of the current slots.")
                : ToolResult.success(selected);
    }

    BookingDraft abandonBooking(BookingDraft draft) {
        if (draft == null) return null;
        return new BookingDraft(draft.draftId(), draft.patientSubjectId(), draft.tenantScope(),
                draft.selectedProvider(), draft.specialtyFilter(), draft.preferredDate(), draft.preferredTimeWindow(),
                draft.exactTime(), null, null, AivaV2Models.DraftStatus.ABANDONED,
                draft.revision() + 1, draft.expiresAt(), draft.confirmedAppointmentReference());
    }

    ToolResult<BookingReceipt> confirmBooking(BookingDraft draft, BookingConfirmation confirmation) {
        if (draft == null || confirmation == null || draft.status() == AivaV2Models.DraftStatus.CONFIRMED) {
            return draft != null && StringUtils.hasText(draft.confirmedAppointmentReference())
                    ? ToolResult.success(new BookingReceipt(draft.confirmedAppointmentReference(), "BOOKED", null))
                    : ToolResult.failure("STALE", "No current confirmation exists.");
        }
        String staleReason = confirmationStaleReason(draft, confirmation);
        if (!"NONE".equals(staleReason)) {
            Instant now = Instant.now(clock);
            log.info("AIVA_V2_CONFIRMATION_TRACE confirmationStaleReason={} "
                            + "draftRevision={} confirmationDraftRevision={} "
                            + "confirmationCreated=true confirmationExpired=true currentTimePresent=true "
                            + "availabilityRequestPresent={} slotReferencePresent={}",
                    staleReason, draft.revision(), confirmation.draftRevision(),
                    confirmation.availabilityRequestId() != null, StringUtils.hasText(confirmation.slotReference()));
            return ToolResult.failure("STALE", "The confirmation has expired or changed.");
        }
        ProviderCandidate provider = draft.selectedProvider();
        try {
            var response = patientPortalService.bookAppointment(new PatientPortalAppointmentBookingRequest(
                    provider.doctorId(), provider.clinicSlug(), provider.tenantId(), provider.clinicId(),
                    provider.bookingReference(), confirmation.slotReference(), confirmation.date(), confirmation.date(),
                    confirmation.startsAt(), null, null, null, null, "AIVA V2 booking",
                    confirmation.idempotencyKey(), null, "AIVA V2 booking"));
            return ToolResult.success(new BookingReceipt(response.appointmentReference(), response.status(), response.createdAt()));
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return ToolResult.failure(ex.getStatusCode().value() == 409 ? "STALE" : "FAILED", ex.getReason());
        } catch (RuntimeException ex) {
            return ToolResult.failure("FAILED", "Booking could not be confirmed.");
        }
    }

    String confirmationStaleReason(BookingDraft draft, BookingConfirmation confirmation) {
        if (draft == null || confirmation == null) return "MISSING";
        if (!confirmation.draftId().equals(draft.draftId())) return "DRAFT_ID_MISMATCH";
        if (confirmation.draftRevision() != draft.revision()) return "DRAFT_REVISION_MISMATCH";
        if (confirmation.expiresAt().isBefore(Instant.now(clock))) return "EXPIRED";
        return "NONE";
    }

    String criteriaFingerprint(BookingDraft draft) {
        return fingerprint("availability", draft.selectedProvider() == null ? null : draft.selectedProvider().providerHandle(),
                String.valueOf(draft.preferredDate()), draft.preferredTimeWindow(), String.valueOf(draft.exactTime()),
                draft.availabilityTimeConstraint() == null ? null : String.valueOf(draft.availabilityTimeConstraint().mode()),
                draft.availabilityTimeConstraint() == null ? null : String.valueOf(draft.availabilityTimeConstraint().startTime()),
                draft.availabilityTimeConstraint() == null ? null : String.valueOf(draft.availabilityTimeConstraint().endTime()),
                String.valueOf(draft.revision()));
    }

    private AvailabilityTimeConstraint effectiveConstraint(BookingDraft draft) {
        if (draft.availabilityTimeConstraint() != null) return draft.availabilityTimeConstraint();
        return draft.exactTime() == null ? null
                : new AvailabilityTimeConstraint(AvailabilityTimeConstraintMode.EXACT, draft.exactTime(), null);
    }

    private ProviderSearchResult resultForCandidates(String fingerprint, List<ProviderCandidate> candidates, Instant now, boolean suggestion) {
        if (candidates.isEmpty()) {
            return new ProviderSearchResult(UUID.randomUUID(), ResolutionStatus.NOT_FOUND, fingerprint,
                    List.of(), null, now, now.plusSeconds(5 * 60));
        }
        if (candidates.size() == 1 && !suggestion) {
            return new ProviderSearchResult(UUID.randomUUID(), ResolutionStatus.RESOLVED, fingerprint,
                    candidates, candidates.get(0), now, now.plusSeconds(5 * 60));
        }
        return new ProviderSearchResult(UUID.randomUUID(), suggestion ? ResolutionStatus.SUGGESTION : ResolutionStatus.AMBIGUOUS,
                fingerprint, candidates, null, now, now.plusSeconds(5 * 60));
    }

    private List<ProviderCandidate> mergedProviders() {
        LinkedHashMap<String, ProviderCandidate> merged = new LinkedHashMap<>();
        List<PatientPortalCareAiDoctorOption> privateDoctors = patientPortalService.careAiDoctorsAcrossAuthorizedClinics();
        if (privateDoctors != null) {
            privateDoctors.stream().map(this::privateCandidate)
                    .forEach(candidate -> merged.putIfAbsent(providerIdentityKey(candidate), candidate));
        }
        if (publicCatalogFacade != null) {
            var page = publicCatalogFacade.listDoctors(null, null, null, null, null,
                    null, null, null, null, 0, 50);
            if (page != null && page.items() != null) {
                page.items().stream().map(this::publicCandidate)
                        .forEach(candidate -> merged.putIfAbsent(providerIdentityKey(candidate), candidate));
            }
        }
        return List.copyOf(merged.values());
    }

    private List<ProviderCandidate> mergedProviders(UUID tenantId) {
        if (tenantId == null) return List.of();
        List<PatientPortalCareAiDoctorOption> privateDoctors = patientPortalService.careAiDoctorsForTenant(tenantId);
        if (privateDoctors == null) return List.of();
        return privateDoctors.stream()
                .filter(doctor -> tenantId.equals(doctor.tenantId()))
                .map(this::privateCandidate)
                .toList();
    }

    private ProviderCandidate privateCandidate(PatientPortalCareAiDoctorOption doctor) {
        // Active tenant doctors use the local AppointmentService slot path; an external
        // booking target is not part of CARE_PRIVATE bookability.
        String tenant = doctor.tenantId().toString();
        String handle = handle(ProviderSource.CARE_PRIVATE, doctor.publicDoctorId(), tenant, doctor.clinicSlug());
        return new ProviderCandidate(handle, handle, doctor.publicDoctorId(),
                doctor.clinicId() == null ? null : doctor.clinicId().toString(), tenant, doctor.clinicSlug(), null,
                doctor.doctorName(), doctor.specialization(), doctor.clinicName(), ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, false, true, false));
    }

    private ProviderCandidate publicCandidate(PublicDoctorSummaryResponse doctor) {
        boolean online = doctor.canBookOnline() || "ONLINE_BOOKING".equalsIgnoreCase(doctor.bookingMode());
        boolean call = "CALL_TO_BOOK".equalsIgnoreCase(doctor.bookingMode());
        String handle = handle(ProviderSource.DISCOVER_PUBLIC, doctor.publicDoctorId(), null, doctor.clinicSlug());
        return new ProviderCandidate(handle, handle, doctor.publicDoctorId(), null, null, doctor.clinicSlug(),
                doctor.bookingReference(), doctor.doctorDisplayName(), doctor.speciality(), doctor.clinicDisplayName(),
                ProviderSource.DISCOVER_PUBLIC,
                new ProviderCapabilities(online, online, call, StringUtils.hasText(doctor.contactPhone()),
                        StringUtils.hasText(doctor.publicPath()), false, true));
    }

    private String handle(ProviderSource source, String doctorId, String tenant, String clinicSlug) {
        return "provider-" + UUID.nameUUIDFromBytes(String.join("|", source.name(), blank(doctorId), blank(tenant), blank(clinicSlug))
                .getBytes(StandardCharsets.UTF_8));
    }

    private String providerIdentityKey(ProviderCandidate candidate) {
        return String.join("|", candidate.source().name(), blank(candidate.doctorId()),
                blank(candidate.tenantId()), blank(candidate.clinicId()), normalize(candidate.clinicSlug()));
    }

    private String fingerprint(String... values) {
        return UUID.nameUUIDFromBytes(String.join("|", values).getBytes(StandardCharsets.UTF_8)).toString();
    }
    private UUID parseTenantId(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private String blank(String value) { return value == null ? "" : value; }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim(); }
    private String normalizeDoctorName(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)) {
            normalized = DevanagariNameTransliterator.transliterate(normalized);
        }
        return normalize(normalized).replaceFirst("^(doctor|dr|doc)\\s+", "")
                .replace("aa", "a").replace("ee", "i").replace("oo", "u");
    }
    private boolean contains(String value, String query) {
        String normalizedValue = normalize(value); String normalizedQuery = normalize(query);
        return StringUtils.hasText(normalizedQuery) && (normalizedValue.contains(normalizedQuery) || normalizedQuery.contains(normalizedValue));
    }
    private boolean matchesWindow(LocalTime time, String window) {
        if (time == null || !StringUtils.hasText(window)) return true;
        String normalized = normalize(window);
        if (normalized.contains("morning")) return time.isBefore(LocalTime.NOON);
        if (normalized.contains("afternoon") || normalized.contains("after lunch")) return !time.isBefore(LocalTime.NOON) && time.isBefore(LocalTime.of(17, 0));
        if (normalized.contains("evening")) return !time.isBefore(LocalTime.of(17, 0));
        return true;
    }

    private boolean matchesTimeConstraint(LocalTime time, AvailabilityTimeConstraint constraint) {
        if (time == null || constraint == null) return true;
        return switch (constraint.mode()) {
            case EXACT -> time.equals(constraint.startTime());
            case AFTER -> time.isAfter(constraint.startTime());
            case BEFORE -> time.isBefore(constraint.endTime());
            case BETWEEN -> !time.isBefore(constraint.startTime()) && !time.isAfter(constraint.endTime());
            case EXCLUDE -> constraint.endTime() == null
                    ? !time.equals(constraint.startTime())
                    : time.isBefore(constraint.startTime()) || !time.isBefore(constraint.endTime());
        };
    }
    private int editDistance(String left, String right) {
        if (left.isEmpty()) return right.length();
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1]; current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[right.length()];
    }
}
