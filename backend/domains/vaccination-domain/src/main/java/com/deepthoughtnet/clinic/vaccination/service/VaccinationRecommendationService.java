package com.deepthoughtnet.clinic.vaccination.service;

import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationEntity;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationRepository;
import com.deepthoughtnet.clinic.vaccination.db.VaccineMasterEntity;
import com.deepthoughtnet.clinic.vaccination.db.VaccineMasterRepository;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccinationRecommendationRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccinationRecommendationSummary;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VaccinationRecommendationService {
    private static final Set<String> ALLOWED_SCHEDULE_TYPES = Set.of("UIP", "IAP", "CLINIC_CUSTOM", "TRAVEL", "ADULT");
    private static final Set<String> ALLOWED_RECOMMENDATION_POLICIES = Set.of(
            "STANDARD_CHILDHOOD",
            "CHILDHOOD_CATCHUP",
            "ADULT_ROUTINE",
            "ADULT_RISK_BASED",
            "PREGNANCY",
            "TRAVEL",
            "OCCUPATIONAL",
            "RECURRING",
            "CLINIC_CUSTOM"
    );
    private static final Set<String> ALLOWED_CATCH_UP_POLICIES = Set.of("NONE", "ALLOWED_UNTIL_AGE", "LIFETIME", "CLINICIAN_DECISION");
    private static final Set<String> ALLOWED_APPLICABLE_AGE_GROUPS = Set.of("NEWBORN", "INFANT", "TODDLER", "CHILD", "ADOLESCENT", "ADULT", "OLDER_ADULT", "ALL");

    private final VaccineMasterRepository vaccineMasterRepository;
    private final PatientVaccinationRepository patientVaccinationRepository;
    private final PatientRepository patientRepository;

    public VaccinationRecommendationService(
            VaccineMasterRepository vaccineMasterRepository,
            PatientVaccinationRepository patientVaccinationRepository,
            PatientRepository patientRepository
    ) {
        this.vaccineMasterRepository = vaccineMasterRepository;
        this.patientVaccinationRepository = patientVaccinationRepository;
        this.patientRepository = patientRepository;
    }

    @Transactional(readOnly = true)
    public VaccinationRecommendationSummary recommend(UUID tenantId, UUID patientId, String requestedScheduleType) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        LocalDate today = LocalDate.now();
        Integer patientAgeDays = patientAgeDays(patient, today);
        String patientAgeGroup = patientAgeGroup(patientAgeDays);
        String scheduleType = normalizeScheduleType(requestedScheduleType);

        List<VaccineMasterEntity> vaccines = vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(tenantId).stream()
                .filter(VaccineMasterEntity::isActive)
                .filter(vaccine -> scheduleType == null || scheduleType.equals(normalizeScheduleTypeValue(vaccine.getScheduleType())))
                .toList();

        List<PatientVaccinationEntity> history = patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(tenantId, patientId);
        RecommendationHistoryIndex historyIndex = buildHistoryIndex(vaccines, history);

        List<VaccinationRecommendationRecord> recommendedToday = new ArrayList<>();
        List<VaccinationRecommendationRecord> overdue = new ArrayList<>();
        List<VaccinationRecommendationRecord> upcoming = new ArrayList<>();
        List<VaccinationRecommendationRecord> completed = new ArrayList<>();
        List<VaccinationRecommendationRecord> optionalRiskBased = new ArrayList<>();
        List<VaccinationRecommendationRecord> notApplicable = new ArrayList<>();

        for (VaccineMasterEntity vaccine : vaccines) {
            VaccinationRecommendationRecord recommendation = recommendForVaccine(
                    vaccine,
                    patientAgeDays,
                    patientAgeGroup,
                    today,
                    historyIndex.forVaccine(vaccine)
            );
            switch (recommendation.status()) {
                case "DUE" -> recommendedToday.add(recommendation);
                case "OVERDUE" -> overdue.add(recommendation);
                case "UPCOMING" -> upcoming.add(recommendation);
                case "COMPLETED" -> completed.add(recommendation);
                case "OPTIONAL_RISK_BASED" -> optionalRiskBased.add(recommendation);
                default -> notApplicable.add(recommendation);
            }
        }

        Comparator<VaccinationRecommendationRecord> byDueDate = Comparator
                .comparing(VaccinationRecommendationRecord::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(VaccinationRecommendationRecord::vaccineName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        recommendedToday.sort(byDueDate);
        overdue.sort(byDueDate);
        upcoming.sort(byDueDate);
        completed.sort(byDueDate);
        optionalRiskBased.sort(byDueDate);
        notApplicable.sort(byDueDate);

        return new VaccinationRecommendationSummary(
                patientId.toString(),
                scheduleType == null ? "ALL" : scheduleType,
                recommendedToday,
                overdue,
                upcoming,
                completed,
                optionalRiskBased,
                notApplicable
        );
    }

    private VaccinationRecommendationRecord recommendForVaccine(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            String patientAgeGroup,
            LocalDate today,
            RecommendationHistory recommendationHistory
    ) {
        String recommendationPolicy = resolveRecommendationPolicy(vaccine);
        String catchUpPolicy = resolveCatchUpPolicy(vaccine);
        String applicableAgeGroup = resolveApplicableAgeGroup(vaccine);
        Integer recommendedAgeDays = firstNonNull(vaccine.getRecommendedAgeDays(), vaccine.getGapDays(), vaccine.getRecommendedGapDays(), vaccine.getMinAgeDays());
        Integer minAgeDays = vaccine.getMinAgeDays();
        Integer maxAgeDays = vaccine.getMaxAgeDays();
        Integer dueAgeDays = recommendedAgeDays != null ? recommendedAgeDays : minAgeDays;
        Integer overdueDays = null;

        PatientVaccinationEntity latestExactHistory = recommendationHistory.latestExactHistory();
        LocalDate latestGivenDate = latestExactHistory == null ? null : latestExactHistory.getGivenDate();
        LocalDate completedDate = latestGivenDate;
        PatientVaccinationEntity latestPreviousDoseHistory = recommendationHistory.latestPreviousDoseHistory();
        LocalDate latestPreviousDoseDate = latestPreviousDoseHistory == null ? null : latestPreviousDoseHistory.getGivenDate();
        PatientVaccinationEntity latestSeriesHistory = recommendationHistory.latestSeriesHistory();
        LocalDate latestSeriesDate = latestSeriesHistory == null ? null : latestSeriesHistory.getGivenDate();
        boolean recurring = "RECURRING".equals(recommendationPolicy) || (vaccine.isRecurring() && vaccine.getRecurrenceDays() != null && vaccine.getRecurrenceDays() >= 0);
        boolean boosterEligible = !recurring && vaccine.getBoosterGapDays() != null && vaccine.getBoosterGapDays() >= 0;
        boolean ageGroupEligible = isApplicableAgeGroup(applicableAgeGroup, patientAgeGroup);
        boolean adult = isAdult(patientAgeGroup);
        boolean olderAdult = "OLDER_ADULT".equals(patientAgeGroup);
        boolean completedByGroupDose = latestExactHistory != null;
        boolean completedByVaccine = latestExactHistory != null;

        if (completedByGroupDose && !recurring && !boosterEligible) {
            return base(vaccine, patientAgeDays, latestGivenDate, "COMPLETED", null, latestGivenDate, patientAgeGroup, "Completed on " + formatDate(latestGivenDate));
        }

        if ("PREGNANCY".equals(recommendationPolicy)) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, null, patientAgeGroup, "Pregnancy vaccines require a pregnancy context");
        }

        if ("TRAVEL".equals(recommendationPolicy) || "OCCUPATIONAL".equals(recommendationPolicy) || "ADULT_RISK_BASED".equals(recommendationPolicy)) {
            if (!adult && !olderAdult) {
                return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, completedDate, patientAgeGroup, "Optional adult vaccine not applicable for this age");
            }
            String reason = "ADULT_RISK_BASED".equals(recommendationPolicy)
                    ? "Risk-based vaccine"
                    : recommendationPolicy.toLowerCase(Locale.ROOT).replace('_', ' ') + " vaccine";
            return base(vaccine, patientAgeDays, dueDateForOptional(latestGivenDate, vaccine), "OPTIONAL_RISK_BASED", null, completedDate, patientAgeGroup, reason);
        }

        if (requiresSeriesPreviousDose(vaccine)) {
            return seriesDoseRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, latestPreviousDoseDate, latestSeriesDate);
        }

        if ("STANDARD_CHILDHOOD".equals(recommendationPolicy)) {
            return childhoodRoutineRecommendation(
                    vaccine,
                    patientAgeDays,
                    patientAgeGroup,
                    today,
                    dueAgeDays,
                    minAgeDays,
                    maxAgeDays,
                    latestGivenDate,
                    completedByGroupDose
            );
        }

        if ("CHILDHOOD_CATCHUP".equals(recommendationPolicy)) {
            return childhoodCatchUpRecommendation(
                    vaccine,
                    patientAgeDays,
                    patientAgeGroup,
                    today,
                    dueAgeDays,
                    minAgeDays,
                    maxAgeDays,
                    latestGivenDate,
                    catchUpPolicy,
                    completedByGroupDose
            );
        }

        if ("ADULT_ROUTINE".equals(recommendationPolicy)) {
            if (!adult && !olderAdult) {
                return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, completedDate, patientAgeGroup, "Adult routine vaccine not applicable for this age");
            }
            return adultRoutineRecommendation(
                    vaccine,
                    patientAgeDays,
                    patientAgeGroup,
                    today,
                    dueAgeDays,
                    minAgeDays,
                    maxAgeDays,
                    latestGivenDate,
                    completedByVaccine
            );
        }

        if (recurring) {
            return recurringRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, latestSeriesDate, latestSeriesHistory != null);
        }

        if (boosterEligible) {
            return boosterRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, latestSeriesDate, latestSeriesHistory != null);
        }

        if (!ageGroupEligible) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, completedDate, patientAgeGroup, "Not applicable for this age group");
        }

        if (isChildhoodPolicy(recommendationPolicy) && patientAgeDays != null && maxAgeDays != null && patientAgeDays > maxAgeDays) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, completedDate, patientAgeGroup, "Routine childhood vaccine not shown for this age");
        }

        return ageBasedRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, dueAgeDays, minAgeDays, maxAgeDays, latestGivenDate, completedByVaccine, false);
    }

    private VaccinationRecommendationRecord childhoodRoutineRecommendation(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            String patientAgeGroup,
            LocalDate today,
            Integer dueAgeDays,
            Integer minAgeDays,
            Integer maxAgeDays,
            LocalDate latestGivenDate,
            boolean completed
    ) {
        if (!isWithinChildhoodWindow(patientAgeDays, minAgeDays, maxAgeDays)) {
            if (patientAgeDays != null && maxAgeDays != null && patientAgeDays > maxAgeDays) {
                return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, latestGivenDate, patientAgeGroup, "Routine childhood vaccine not shown for this age");
            }
            return base(vaccine, patientAgeDays, null, "UPCOMING", null, latestGivenDate, patientAgeGroup, dueText(minAgeDays, dueAgeDays));
        }
        if (completed) {
            return base(vaccine, patientAgeDays, latestGivenDate, "COMPLETED", null, latestGivenDate, patientAgeGroup, "Completed on " + formatDate(latestGivenDate));
        }
        return ageBasedRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, dueAgeDays, minAgeDays, maxAgeDays, latestGivenDate, false, false);
    }

    private VaccinationRecommendationRecord childhoodCatchUpRecommendation(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            String patientAgeGroup,
            LocalDate today,
            Integer dueAgeDays,
            Integer minAgeDays,
            Integer maxAgeDays,
            LocalDate latestGivenDate,
            String catchUpPolicy,
            boolean completed
    ) {
        if (patientAgeDays != null && maxAgeDays != null && patientAgeDays > maxAgeDays && !catchUpAllowed(catchUpPolicy, patientAgeDays, vaccine.getCatchUpMaxAgeDays())) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, latestGivenDate, patientAgeGroup, "Catch-up not allowed beyond this age");
        }
        if (!isWithinChildhoodWindow(patientAgeDays, minAgeDays, maxAgeDays) && !catchUpAllowed(catchUpPolicy, patientAgeDays, vaccine.getCatchUpMaxAgeDays())) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, latestGivenDate, patientAgeGroup, "Catch-up not allowed");
        }
        if (completed) {
            return base(vaccine, patientAgeDays, latestGivenDate, "COMPLETED", null, latestGivenDate, patientAgeGroup, "Completed on " + formatDate(latestGivenDate));
        }
        return ageBasedRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, dueAgeDays, minAgeDays, maxAgeDays, latestGivenDate, false, true);
    }

    private VaccinationRecommendationRecord adultRoutineRecommendation(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            String patientAgeGroup,
            LocalDate today,
            Integer dueAgeDays,
            Integer minAgeDays,
            Integer maxAgeDays,
            LocalDate latestGivenDate,
            boolean completed
    ) {
        if (patientAgeDays != null && maxAgeDays != null && patientAgeDays > maxAgeDays) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, latestGivenDate, patientAgeGroup, "Adult routine vaccine not applicable for this age");
        }
        if (completed) {
            if (vaccine.isRecurring()) {
                return recurringRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, latestGivenDate, true);
            }
            if (vaccine.getBoosterGapDays() != null) {
                return boosterRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, latestGivenDate, true);
            }
            return base(vaccine, patientAgeDays, latestGivenDate, "COMPLETED", null, latestGivenDate, patientAgeGroup, "Completed on " + formatDate(latestGivenDate));
        }
        return ageBasedRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, dueAgeDays, minAgeDays, maxAgeDays, latestGivenDate, false, false);
    }

    private VaccinationRecommendationRecord recurringRecommendation(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            String patientAgeGroup,
            LocalDate today,
            LocalDate latestGivenDate,
            boolean completed
    ) {
        Integer recurrenceDays = firstNonNull(vaccine.getRecurrenceDays(), vaccine.getBoosterGapDays());
        if (recurrenceDays == null || recurrenceDays < 0) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, latestGivenDate, patientAgeGroup, "Recurring interval not configured");
        }
        if (latestGivenDate == null) {
            return ageBasedRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, firstNonNull(vaccine.getRecommendedAgeDays(), vaccine.getMinAgeDays()), vaccine.getMinAgeDays(), vaccine.getMaxAgeDays(), null, false, false);
        }
        LocalDate nextDue = latestGivenDate.plusDays(recurrenceDays);
        if (today.isBefore(nextDue)) {
            return base(vaccine, patientAgeDays, nextDue, "COMPLETED", null, latestGivenDate, patientAgeGroup, recurringReason(vaccine, nextDue));
        }
        int overdueDays = Math.toIntExact(ChronoUnit.DAYS.between(nextDue, today));
        return base(vaccine, patientAgeDays, nextDue, overdueDays > 0 ? "OVERDUE" : "DUE", overdueDays > 0 ? overdueDays : null, latestGivenDate, patientAgeGroup, recurringReason(vaccine, nextDue));
    }

    private VaccinationRecommendationRecord boosterRecommendation(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            String patientAgeGroup,
            LocalDate today,
            LocalDate latestGivenDate,
            boolean completed
    ) {
        Integer boosterGapDays = vaccine.getBoosterGapDays();
        if (boosterGapDays == null || boosterGapDays < 0) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, latestGivenDate, patientAgeGroup, "Booster interval not configured");
        }
        if (latestGivenDate == null) {
            return ageBasedRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, firstNonNull(vaccine.getRecommendedAgeDays(), vaccine.getMinAgeDays()), vaccine.getMinAgeDays(), vaccine.getMaxAgeDays(), null, false, false);
        }
        LocalDate nextDue = latestGivenDate.plusDays(boosterGapDays);
        if (today.isBefore(nextDue)) {
            return base(vaccine, patientAgeDays, nextDue, "COMPLETED", null, latestGivenDate, patientAgeGroup, boosterReason(vaccine, nextDue));
        }
        int overdueDays = Math.toIntExact(ChronoUnit.DAYS.between(nextDue, today));
        return base(vaccine, patientAgeDays, nextDue, overdueDays > 0 ? "OVERDUE" : "DUE", overdueDays > 0 ? overdueDays : null, latestGivenDate, patientAgeGroup, boosterReason(vaccine, nextDue));
    }

    private VaccinationRecommendationRecord seriesDoseRecommendation(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            String patientAgeGroup,
            LocalDate today,
            LocalDate latestPreviousDoseDate,
            LocalDate latestSeriesDate
    ) {
        if (latestPreviousDoseDate == null) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, latestSeriesDate, patientAgeGroup, "Previous dose not completed");
        }
        Integer intervalDays = firstNonNull(vaccine.getGapDays(), vaccine.getRecommendedGapDays(), vaccine.getBoosterGapDays());
        if (intervalDays == null || intervalDays < 0) {
            return ageBasedRecommendation(
                    vaccine,
                    patientAgeDays,
                    patientAgeGroup,
                    today,
                    firstNonNull(vaccine.getRecommendedAgeDays(), vaccine.getMinAgeDays()),
                    vaccine.getMinAgeDays(),
                    vaccine.getMaxAgeDays(),
                    latestSeriesDate,
                    false,
                    false
            );
        }
        LocalDate dueDate = latestPreviousDoseDate.plusDays(intervalDays);
        if (today.isBefore(dueDate)) {
            return base(vaccine, patientAgeDays, dueDate, "UPCOMING", null, latestSeriesDate, patientAgeGroup, "Due after " + intervalDays + " days from previous dose");
        }
        int overdueDays = Math.toIntExact(ChronoUnit.DAYS.between(dueDate, today));
        return base(
                vaccine,
                patientAgeDays,
                dueDate,
                overdueDays > 0 ? "OVERDUE" : "DUE",
                overdueDays > 0 ? overdueDays : null,
                latestSeriesDate,
                patientAgeGroup,
                overdueDays > 0 ? "Overdue by " + overdueDays + " days" : "Due after " + intervalDays + " days from previous dose"
        );
    }

    private VaccinationRecommendationRecord ageBasedRecommendation(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            String patientAgeGroup,
            LocalDate today,
            Integer dueAgeDays,
            Integer minAgeDays,
            Integer maxAgeDays,
            LocalDate completedDate,
            boolean completed,
            boolean childhoodPolicy
    ) {
        if (patientAgeDays == null || dueAgeDays == null) {
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, completedDate, patientAgeGroup, "No due age configured");
        }
        if (minAgeDays != null && patientAgeDays < minAgeDays) {
            return base(vaccine, patientAgeDays, today.plusDays(minAgeDays - patientAgeDays), "UPCOMING", null, completedDate, patientAgeGroup, dueText(minAgeDays, dueAgeDays));
        }
        if (maxAgeDays != null && patientAgeDays > maxAgeDays) {
            if (childhoodPolicy) {
                return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, completedDate, patientAgeGroup, "Routine childhood vaccine not shown for this age");
            }
            return base(vaccine, patientAgeDays, null, "NOT_APPLICABLE", null, completedDate, patientAgeGroup, "Not applicable for this age group");
        }
        LocalDate dueDate = estimateDueDate(today, patientAgeDays, dueAgeDays);
        if (completed) {
            return base(vaccine, patientAgeDays, dueDate, "COMPLETED", null, completedDate, patientAgeGroup, "Completed on " + formatDate(completedDate));
        }
        if (patientAgeDays < dueAgeDays) {
            return base(vaccine, patientAgeDays, dueDate, "UPCOMING", null, completedDate, patientAgeGroup, dueText(minAgeDays, dueAgeDays));
        }
        int overdueDays = Math.max(0, patientAgeDays - dueAgeDays);
        return base(vaccine, patientAgeDays, dueDate, overdueDays > 0 ? "OVERDUE" : "DUE", overdueDays > 0 ? overdueDays : null, completedDate, patientAgeGroup, overdueDays > 0 ? "Overdue by " + overdueDays + " days" : dueText(minAgeDays, dueAgeDays));
    }

    private LocalDate estimateDueDate(LocalDate today, Integer patientAgeDays, Integer dueAgeDays) {
        if (patientAgeDays == null || dueAgeDays == null) {
            return null;
        }
        int delta = dueAgeDays - patientAgeDays;
        return delta == 0 ? today : today.plusDays(delta);
    }

    private LocalDate dueDateForOptional(LocalDate latestGivenDate, VaccineMasterEntity vaccine) {
        if (vaccine.isRecurring() && vaccine.getRecurrenceDays() != null && latestGivenDate != null) {
            return latestGivenDate.plusDays(vaccine.getRecurrenceDays());
        }
        if (vaccine.getBoosterGapDays() != null && latestGivenDate != null) {
            return latestGivenDate.plusDays(vaccine.getBoosterGapDays());
        }
        return null;
    }

    private boolean isWithinChildhoodWindow(Integer patientAgeDays, Integer minAgeDays, Integer maxAgeDays) {
        if (patientAgeDays == null) {
            return false;
        }
        if (minAgeDays != null && patientAgeDays < minAgeDays) {
            return false;
        }
        if (maxAgeDays != null && patientAgeDays > maxAgeDays) {
            return false;
        }
        return true;
    }

    private boolean catchUpAllowed(String catchUpPolicy, Integer patientAgeDays, Integer catchUpMaxAgeDays) {
        if ("LIFETIME".equals(catchUpPolicy) || "CLINICIAN_DECISION".equals(catchUpPolicy)) {
            return true;
        }
        if ("ALLOWED_UNTIL_AGE".equals(catchUpPolicy)) {
            return patientAgeDays != null && catchUpMaxAgeDays != null && patientAgeDays <= catchUpMaxAgeDays;
        }
        return false;
    }

    private boolean requiresSeriesPreviousDose(VaccineMasterEntity vaccine) {
        return vaccine.getDoseNumber() != null && vaccine.getDoseNumber() > 1;
    }

    private PatientVaccinationEntity latest(List<PatientVaccinationEntity> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        return history.stream()
                .max(Comparator.comparing(PatientVaccinationEntity::getGivenDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientVaccinationEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private RecommendationHistoryIndex buildHistoryIndex(List<VaccineMasterEntity> vaccines, List<PatientVaccinationEntity> history) {
        Map<UUID, VaccineMasterEntity> vaccineById = vaccines.stream()
                .collect(Collectors.toMap(VaccineMasterEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, List<VaccineMasterEntity>> vaccineAliases = new LinkedHashMap<>();
        for (VaccineMasterEntity vaccine : vaccines) {
            for (String alias : vaccineAliases(vaccine)) {
                vaccineAliases.computeIfAbsent(alias, ignored -> new ArrayList<>()).add(vaccine);
            }
        }

        Map<String, List<PatientVaccinationEntity>> historyByMaster = new LinkedHashMap<>();
        Map<String, List<PatientVaccinationEntity>> historyBySeries = new LinkedHashMap<>();
        for (PatientVaccinationEntity entry : history) {
            ResolvedHistory resolvedHistory = resolveHistory(entry, vaccineById, vaccineAliases);
            if (resolvedHistory.masterKey() != null) {
                historyByMaster.computeIfAbsent(resolvedHistory.masterKey(), ignored -> new ArrayList<>()).add(entry);
            }
            if (resolvedHistory.seriesKey() != null) {
                historyBySeries.computeIfAbsent(resolvedHistory.seriesKey(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        return new RecommendationHistoryIndex(historyByMaster, historyBySeries);
    }

    private String masterKey(VaccineMasterEntity vaccine) {
        String group = normalize(vaccine.getVaccineGroup());
        String name = normalize(vaccine.getVaccineName());
        String scheduleType = safeScheduleTypeValue(vaccine.getScheduleType());
        String dose = vaccine.getDoseNumber() == null ? "" : vaccine.getDoseNumber().toString();
        return String.join("|",
                group == null ? name : group,
                dose,
                scheduleType == null ? "" : scheduleType
        );
    }

    private String seriesKey(VaccineMasterEntity vaccine) {
        String group = normalize(vaccine.getVaccineGroup());
        String name = normalize(vaccine.getVaccineName());
        String scheduleType = safeScheduleTypeValue(vaccine.getScheduleType());
        return String.join("|",
                group == null ? name : group,
                scheduleType == null ? "" : scheduleType
        );
    }

    private ResolvedHistory resolveHistory(
            PatientVaccinationEntity entry,
            Map<UUID, VaccineMasterEntity> vaccineById,
            Map<String, List<VaccineMasterEntity>> vaccineAliases
    ) {
        if (entry.getVaccineId() != null) {
            VaccineMasterEntity vaccine = vaccineById.get(entry.getVaccineId());
            if (vaccine != null) {
                return new ResolvedHistory(masterKey(vaccine), seriesKey(vaccine));
            }
        }
        VaccineMasterEntity matchedVaccine = matchHistoryAlias(entry, vaccineAliases);
        if (matchedVaccine != null) {
            return new ResolvedHistory(masterKey(matchedVaccine), seriesKey(matchedVaccine));
        }
        String normalizedSnapshot = normalizeHistoryAlias(entry.getVaccineNameSnapshot());
        String scheduleType = "";
        String dose = entry.getDoseNumber() == null ? "" : entry.getDoseNumber().toString();
        return new ResolvedHistory(
                normalizedSnapshot == null ? null : String.join("|", normalizedSnapshot, dose, scheduleType),
                normalizedSnapshot == null ? null : String.join("|", normalizedSnapshot, scheduleType)
        );
    }

    private VaccineMasterEntity matchHistoryAlias(PatientVaccinationEntity entry, Map<String, List<VaccineMasterEntity>> vaccineAliases) {
        for (String alias : historyAliases(entry.getVaccineNameSnapshot())) {
            List<VaccineMasterEntity> matches = vaccineAliases.get(alias);
            if (matches == null || matches.isEmpty()) {
                continue;
            }
            if (entry.getDoseNumber() != null) {
                Optional<VaccineMasterEntity> doseMatch = matches.stream()
                        .filter(candidate -> entry.getDoseNumber().equals(candidate.getDoseNumber()))
                        .findFirst();
                if (doseMatch.isPresent()) {
                    return doseMatch.get();
                }
            }
            if (matches.size() == 1) {
                return matches.getFirst();
            }
            Optional<VaccineMasterEntity> groupMatch = matches.stream()
                    .filter(candidate -> normalize(candidate.getVaccineGroup()) != null)
                    .findFirst();
            if (groupMatch.isPresent()) {
                return groupMatch.get();
            }
            return matches.getFirst();
        }
        return null;
    }

    private List<String> vaccineAliases(VaccineMasterEntity vaccine) {
        List<String> aliases = new ArrayList<>();
        addAlias(aliases, vaccine.getVaccineName());
        addAlias(aliases, vaccine.getBrandName());
        addAlias(aliases, vaccine.getVaccineGroup());
        if (StringUtils.hasText(vaccine.getVaccineGroup()) && vaccine.getDoseNumber() != null) {
            addAlias(aliases, vaccine.getVaccineGroup() + "-" + vaccine.getDoseNumber());
            addAlias(aliases, vaccine.getVaccineGroup() + " dose " + vaccine.getDoseNumber());
        }
        return aliases.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private List<String> historyAliases(String value) {
        List<String> aliases = new ArrayList<>();
        addAlias(aliases, value);
        String normalized = normalizeHistoryAlias(value);
        if (normalized != null) {
            aliases.add(normalized);
            aliases.add(stripTrailingDoseToken(normalized));
        }
        return aliases.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private void addAlias(List<String> aliases, String value) {
        String normalized = normalizeHistoryAlias(value);
        if (normalized != null) {
            aliases.add(normalized);
            aliases.add(stripTrailingDoseToken(normalized));
        }
    }

    private String normalizeHistoryAlias(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String stripTrailingDoseToken(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.replaceFirst("(?:\\s+dose)?\\s+\\d+$", "").trim();
    }

    private VaccinationRecommendationRecord base(
            VaccineMasterEntity vaccine,
            Integer patientAgeDays,
            LocalDate dueDate,
            String status,
            Integer overdueDays,
            LocalDate completedDate,
            String patientAgeGroup,
            String reasonText
    ) {
        return new VaccinationRecommendationRecord(
                vaccine.getId(),
                vaccine.getVaccineName(),
                vaccine.getBrandName(),
                vaccine.getManufacturer(),
                vaccine.getVaccineGroup(),
                vaccine.getDoseNumber(),
                vaccine.getRoute(),
                vaccine.getAdministrationSite(),
                vaccine.getScheduleType(),
                dueDate,
                status,
                overdueDays,
                firstNonNull(vaccine.getRecommendedAgeDays(), vaccine.getRecommendedGapDays(), vaccine.getGapDays(), vaccine.getMinAgeDays()),
                patientAgeDays,
                patientAgeGroup,
                reasonText,
                completedDate
        );
    }

    private String dueText(Integer minAgeDays, Integer dueAgeDays) {
        Integer age = dueAgeDays != null ? dueAgeDays : minAgeDays;
        return age == null ? "No due age configured" : "Due at " + formatAgeLabel(age);
    }

    private String recurringReason(VaccineMasterEntity vaccine, LocalDate nextDue) {
        return StringUtils.hasText(vaccine.getBoosterRules())
                ? vaccine.getBoosterRules()
                : "Recurring vaccine due on " + formatDate(nextDue);
    }

    private String boosterReason(VaccineMasterEntity vaccine, LocalDate nextDue) {
        return StringUtils.hasText(vaccine.getBoosterRules())
                ? vaccine.getBoosterRules()
                : "Booster due on " + formatDate(nextDue);
    }

    private Integer patientAgeDays(PatientEntity patient, LocalDate today) {
        if (patient.getDateOfBirth() != null) {
            return Math.toIntExact(ChronoUnit.DAYS.between(patient.getDateOfBirth(), today));
        }
        if (patient.getAgeYears() != null) {
            return patient.getAgeYears() * 365;
        }
        return null;
    }

    private String patientAgeGroup(Integer patientAgeDays) {
        if (patientAgeDays == null) {
            return "ALL";
        }
        if (patientAgeDays <= 28) {
            return "NEWBORN";
        }
        if (patientAgeDays <= 365) {
            return "INFANT";
        }
        if (patientAgeDays <= 3 * 365) {
            return "TODDLER";
        }
        if (patientAgeDays <= 9 * 365) {
            return "CHILD";
        }
        if (patientAgeDays <= 17 * 365) {
            return "ADOLESCENT";
        }
        if (patientAgeDays <= 59 * 365) {
            return "ADULT";
        }
        return "OLDER_ADULT";
    }

    private boolean isAdult(String patientAgeGroup) {
        return "ADULT".equals(patientAgeGroup) || "OLDER_ADULT".equals(patientAgeGroup);
    }

    private boolean isApplicableAgeGroup(String applicableAgeGroup, String patientAgeGroup) {
        if (!StringUtils.hasText(applicableAgeGroup) || "ALL".equals(applicableAgeGroup)) {
            return true;
        }
        return applicableAgeGroup.equals(patientAgeGroup);
    }

    private String formatAgeLabel(Integer days) {
        if (days == null) {
            return "";
        }
        if (days >= 365) {
            int years = days / 365;
            return years + (years == 1 ? " year" : " years");
        }
        if (days >= 7) {
            int weeks = days / 7;
            return weeks + (weeks == 1 ? " week" : " weeks");
        }
        return days + (days == 1 ? " day" : " days");
    }

    private String formatDate(LocalDate date) {
        return date == null ? "unknown" : date.toString();
    }

    private Integer firstNonNull(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeScheduleType(String scheduleType) {
        String normalized = normalize(scheduleType);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_SCHEDULE_TYPES.contains(upper)) {
            throw new IllegalArgumentException("scheduleType must be one of " + String.join(", ", ALLOWED_SCHEDULE_TYPES));
        }
        return upper;
    }

    private String resolveRecommendationPolicy(VaccineMasterEntity vaccine) {
        String explicit = normalizeRecommendationPolicyValue(vaccine.getRecommendationPolicy());
        if (explicit != null) {
            return explicit;
        }
        String scheduleType = safeScheduleTypeValue(vaccine.getScheduleType());
        if ("ADULT".equals(scheduleType)) {
            return "ADULT_ROUTINE";
        }
        if (vaccine.isRecurring()) {
            return "RECURRING";
        }
        if (looksChildhoodSchedule(vaccine.getAgeGroup(), vaccine.getRecommendedAgeDays())) {
            return "STANDARD_CHILDHOOD";
        }
        return "CLINIC_CUSTOM";
    }

    private String resolveCatchUpPolicy(VaccineMasterEntity vaccine) {
        String explicit = normalizeCatchUpPolicyValue(vaccine.getCatchUpPolicy());
        return explicit == null ? "NONE" : explicit;
    }

    private String resolveApplicableAgeGroup(VaccineMasterEntity vaccine) {
        String explicit = normalizeApplicableAgeGroupValue(vaccine.getApplicableAgeGroup());
        if (explicit != null) {
            return explicit;
        }
        String ageGroup = normalize(vaccine.getAgeGroup());
        if (!StringUtils.hasText(ageGroup)) {
            Integer inferredDays = vaccine.getRecommendedAgeDays();
            if (inferredDays == null) {
                inferredDays = vaccine.getMinAgeDays();
            }
            return inferredDays == null ? "ALL" : patientAgeGroup(inferredDays);
        }
        String upper = ageGroup.toUpperCase(Locale.ROOT);
        if (upper.contains("NEWBORN") || upper.contains("BIRTH")) {
            return "NEWBORN";
        }
        if (upper.contains("INFANT") || upper.contains("6 WEEK") || upper.contains("10 WEEK") || upper.contains("14 WEEK") || upper.contains("9 MONTH")) {
            return "INFANT";
        }
        if (upper.contains("TODDLER") || upper.contains("1 YEAR") || upper.contains("2 YEAR") || upper.contains("3 YEAR")) {
            return "TODDLER";
        }
        if (upper.contains("CHILD") || upper.contains("4 YEAR") || upper.contains("5 YEAR") || upper.contains("6 YEAR") || upper.contains("7 YEAR") || upper.contains("8 YEAR") || upper.contains("9 YEAR")) {
            return "CHILD";
        }
        if (upper.contains("ADOLESCENT") || upper.contains("10 YEAR") || upper.contains("11 YEAR") || upper.contains("12 YEAR") || upper.contains("13 YEAR") || upper.contains("14 YEAR") || upper.contains("15 YEAR") || upper.contains("16 YEAR") || upper.contains("17 YEAR")) {
            return "ADOLESCENT";
        }
        if (upper.contains("OLDER") || upper.contains("SENIOR")) {
            return "OLDER_ADULT";
        }
        if (upper.contains("ADULT")) {
            return "ADULT";
        }
        return "ALL";
    }

    private boolean looksChildhoodSchedule(String ageGroup, Integer recommendedAgeDays) {
        if (recommendedAgeDays != null && recommendedAgeDays <= 365 * 17) {
            return true;
        }
        if (!StringUtils.hasText(ageGroup)) {
            return false;
        }
        String normalized = ageGroup.toUpperCase(Locale.ROOT);
        return normalized.contains("NEWBORN")
                || normalized.contains("INFANT")
                || normalized.contains("TODDLER")
                || normalized.contains("CHILD")
                || normalized.contains("ADOLESCENT")
                || normalized.contains("WEEK")
                || normalized.contains("MONTH");
    }

    private String normalizeScheduleTypeValue(String scheduleType) {
        String normalized = normalize(scheduleType);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_SCHEDULE_TYPES.contains(upper)) {
            throw new IllegalArgumentException("scheduleType must be one of " + String.join(", ", ALLOWED_SCHEDULE_TYPES));
        }
        return upper;
    }

    private String safeScheduleTypeValue(String scheduleType) {
        try {
            return normalizeScheduleTypeValue(scheduleType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeRecommendationPolicyValue(String policy) {
        String normalized = normalize(policy);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_RECOMMENDATION_POLICIES.contains(upper)) {
            throw new IllegalArgumentException("recommendationPolicy must be one of " + String.join(", ", ALLOWED_RECOMMENDATION_POLICIES));
        }
        return upper;
    }

    private String normalizeCatchUpPolicyValue(String policy) {
        String normalized = normalize(policy);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_CATCH_UP_POLICIES.contains(upper)) {
            throw new IllegalArgumentException("catchUpPolicy must be one of " + String.join(", ", ALLOWED_CATCH_UP_POLICIES));
        }
        return upper;
    }

    private String normalizeApplicableAgeGroupValue(String ageGroup) {
        String normalized = normalize(ageGroup);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_APPLICABLE_AGE_GROUPS.contains(upper)) {
            throw new IllegalArgumentException("applicableAgeGroup must be one of " + String.join(", ", ALLOWED_APPLICABLE_AGE_GROUPS));
        }
        return upper;
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireId(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private boolean isChildhoodPolicy(String recommendationPolicy) {
        return "STANDARD_CHILDHOOD".equals(recommendationPolicy) || "CHILDHOOD_CATCHUP".equals(recommendationPolicy) || "CLINIC_CUSTOM".equals(recommendationPolicy);
    }

    private record ResolvedHistory(String masterKey, String seriesKey) {
    }

    private record RecommendationHistory(
            List<PatientVaccinationEntity> exactHistory,
            PatientVaccinationEntity latestExactHistory,
            PatientVaccinationEntity latestPreviousDoseHistory,
            PatientVaccinationEntity latestSeriesHistory
    ) {
    }

    private class RecommendationHistoryIndex {
        private final Map<String, List<PatientVaccinationEntity>> historyByMaster;
        private final Map<String, List<PatientVaccinationEntity>> historyBySeries;

        private RecommendationHistoryIndex(
                Map<String, List<PatientVaccinationEntity>> historyByMaster,
                Map<String, List<PatientVaccinationEntity>> historyBySeries
        ) {
            this.historyByMaster = historyByMaster;
            this.historyBySeries = historyBySeries;
        }

        private RecommendationHistory forVaccine(VaccineMasterEntity vaccine) {
            List<PatientVaccinationEntity> exactHistory = historyByMaster.getOrDefault(masterKey(vaccine), List.of());
            PatientVaccinationEntity latestExact = latest(exactHistory);
            PatientVaccinationEntity latestPreviousDose = null;
            if (vaccine.getDoseNumber() != null && vaccine.getDoseNumber() > 1) {
                String previousDoseKey = String.join("|",
                        normalize(vaccine.getVaccineGroup()) == null ? normalize(vaccine.getVaccineName()) : normalize(vaccine.getVaccineGroup()),
                        Integer.toString(vaccine.getDoseNumber() - 1),
                        safeScheduleTypeValue(vaccine.getScheduleType()) == null ? "" : safeScheduleTypeValue(vaccine.getScheduleType())
                );
                latestPreviousDose = latest(historyByMaster.getOrDefault(previousDoseKey, List.of()));
            }
            PatientVaccinationEntity latestSeries = latest(historyBySeries.getOrDefault(seriesKey(vaccine), List.of()));
            return new RecommendationHistory(exactHistory, latestExact, latestPreviousDose, latestSeries);
        }
    }
}
