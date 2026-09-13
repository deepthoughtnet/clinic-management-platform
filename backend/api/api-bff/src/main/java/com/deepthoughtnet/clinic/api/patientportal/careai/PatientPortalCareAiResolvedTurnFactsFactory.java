package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

/** Resolves CanonicalTurn candidates without performing I/O or mutating state. */
final class PatientPortalCareAiResolvedTurnFactsFactory {
    private final DoctorResolver doctorResolver;
    private final SpecialtyResolver specialtyResolver;
    private final ClinicResolver clinicResolver;
    private final SelectionResolver selectionResolver;
    private final Clock clock;
    private final Supplier<ZoneId> zoneSupplier;

    PatientPortalCareAiResolvedTurnFactsFactory(
            DoctorResolver doctorResolver,
            SpecialtyResolver specialtyResolver,
            ClinicResolver clinicResolver,
            SelectionResolver selectionResolver,
            Clock clock,
            Supplier<ZoneId> zoneSupplier
    ) {
        this.doctorResolver = doctorResolver;
        this.specialtyResolver = specialtyResolver;
        this.clinicResolver = clinicResolver;
        this.selectionResolver = selectionResolver;
        this.clock = clock;
        this.zoneSupplier = zoneSupplier;
    }

    PatientPortalCareAiResolvedTurnFacts resolve(
            PatientPortalCareAiCanonicalTurn turn,
            List<CanonicalEntityCandidate> doctors,
            List<String> supportedSpecialties,
            List<CanonicalEntityCandidate> clinics,
            List<CanonicalEntityCandidate> selections,
            String preferredDoctorId,
            String preferredClinicId
    ) {
        PatientPortalCareAiCanonicalEntities entities = turn == null ? null : turn.entities();
        return new PatientPortalCareAiResolvedTurnFacts(
                resolveDoctor(turn, entities == null ? null : entities.doctor(), doctors, preferredDoctorId),
                resolveSpeciality(turn, entities == null ? null : entities.speciality(), supportedSpecialties),
                resolveClinic(turn, entities == null ? null : entities.clinic(), clinics, preferredClinicId),
                resolveSelection(turn, selections),
                resolveDate(turn, entities == null ? null : entities.date()),
                value(entities == null ? null : entities.timeWindow()),
                resolveTime(entities == null ? null : entities.exactTime())
        );
    }

    private PatientPortalCareAiTurnFactDelta resolveDoctor(PatientPortalCareAiCanonicalTurn turn, String value,
                                                           List<CanonicalEntityCandidate> candidates, String preferred) {
        if (!hasText(value)) return explicitTarget(turn, "doctor") ? PatientPortalCareAiTurnFactDelta.clear() : unchanged();
        return PatientPortalCareAiTurnFactDelta.set(doctorResolver.resolve(value, candidates == null ? List.of() : candidates, preferred));
    }

    private PatientPortalCareAiTurnFactDelta resolveClinic(PatientPortalCareAiCanonicalTurn turn, String value,
                                                           List<CanonicalEntityCandidate> candidates, String preferred) {
        if (!hasText(value)) return explicitTarget(turn, "clinic") ? PatientPortalCareAiTurnFactDelta.clear() : unchanged();
        return PatientPortalCareAiTurnFactDelta.set(clinicResolver.resolve(value, candidates == null ? List.of() : candidates, preferred));
    }

    private PatientPortalCareAiTurnFactDelta resolveSpeciality(PatientPortalCareAiCanonicalTurn turn, String value, List<String> supported) {
        if (!hasText(value)) return explicitTarget(turn, "speciality") ? PatientPortalCareAiTurnFactDelta.clear() : unchanged();
        SpecialtyResolver.SpecialtyResolution result = specialtyResolver.resolve(value, supported);
        return PatientPortalCareAiTurnFactDelta.set(new CanonicalResolution(
                CanonicalResolutionStatus.valueOf(result.status().name()), result.canonicalSpecialty(), List.of(),
                result.candidates(), "specialty-resolver", result.confidence(), result.candidateText()));
    }

    private PatientPortalCareAiTurnFactDelta resolveSelection(PatientPortalCareAiCanonicalTurn turn, List<CanonicalEntityCandidate> candidates) {
        if (turn == null || !turn.selection().present()) return unchanged();
        String value = turn.entities().slotSelection();
        if (!hasText(value) && turn.selection().ordinal() != null) value = String.valueOf(turn.selection().ordinal());
        return PatientPortalCareAiTurnFactDelta.set(selectionResolver.resolve(value, candidates == null ? List.of() : candidates, null, String::trim));
    }

    private PatientPortalCareAiTurnFactDelta resolveDate(PatientPortalCareAiCanonicalTurn turn, String value) {
        if (!hasText(value)) return explicitTarget(turn, "date") ? PatientPortalCareAiTurnFactDelta.clear() : unchanged();
        return new PatientPortalCareAiTemporalResolver(clock, zoneSupplier.get()).resolveDate(value)
                .map(date -> PatientPortalCareAiTurnFactDelta.set(CanonicalResolution.resolved(date.toString(), List.of(), List.of(), "temporal-resolver", 0.98d, value)))
                .orElseGet(() -> PatientPortalCareAiTurnFactDelta.set(CanonicalResolution.unresolved(List.of(), "temporal-resolver", 0.0d, value)));
    }

    private PatientPortalCareAiTurnFactDelta resolveTime(String value) {
        if (!hasText(value)) return unchanged();
        return new PatientPortalCareAiTemporalResolver(clock, zoneSupplier.get()).resolveExactTime(value)
                .map(time -> PatientPortalCareAiTurnFactDelta.set(CanonicalResolution.resolved(time.toString(), List.of(), List.of(), "temporal-resolver", 0.98d, value)))
                .orElseGet(() -> PatientPortalCareAiTurnFactDelta.set(CanonicalResolution.unresolved(List.of(), "temporal-resolver", 0.0d, value)));
    }

    private PatientPortalCareAiTurnFactDelta value(String value) {
        return hasText(value) ? PatientPortalCareAiTurnFactDelta.set(CanonicalResolution.resolved(value, List.of(), List.of(), "canonical-turn", 0.9d, value)) : unchanged();
    }

    private PatientPortalCareAiTurnFactDelta unchanged() {
        return PatientPortalCareAiTurnFactDelta.unchanged();
    }

    private boolean explicitTarget(PatientPortalCareAiCanonicalTurn turn, String target) {
        if (turn == null) return false;
        return (turn.correction().present() && target.equalsIgnoreCase(turn.correction().target()))
                || (turn.alternative().present() && target.equalsIgnoreCase(turn.alternative().target()));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
