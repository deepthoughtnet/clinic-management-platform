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

        Map<UUID, VaccineMasterEntity> vaccineById = vaccines.stream()
                .collect(Collectors.toMap(VaccineMasterEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<PatientVaccinationEntity> history = patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(tenantId, patientId);

        Map<String, List<PatientVaccinationEntity>> historyByKey = history.stream()
                .collect(Collectors.groupingBy(entity -> historyKey(entity, vaccineById), LinkedHashMap::new, Collectors.toList()));

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
                    historyByKey.getOrDefault(masterKey(vaccine), List.of())
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
            List<PatientVaccinationEntity> history
    ) {
        String recommendationPolicy = resolveRecommendationPolicy(vaccine);
        String catchUpPolicy = resolveCatchUpPolicy(vaccine);
        String applicableAgeGroup = resolveApplicableAgeGroup(vaccine);
        Integer recommendedAgeDays = firstNonNull(vaccine.getRecommendedAgeDays(), vaccine.getGapDays(), vaccine.getRecommendedGapDays(), vaccine.getMinAgeDays());
        Integer minAgeDays = vaccine.getMinAgeDays();
        Integer maxAgeDays = vaccine.getMaxAgeDays();
        Integer dueAgeDays = recommendedAgeDays != null ? recommendedAgeDays : minAgeDays;
        Integer overdueDays = null;

        PatientVaccinationEntity latestHistory = latest(history);
        LocalDate latestGivenDate = latestHistory == null ? null : latestHistory.getGivenDate();
        LocalDate completedDate = latestGivenDate;
        boolean recurring = "RECURRING".equals(recommendationPolicy) || (vaccine.isRecurring() && vaccine.getRecurrenceDays() != null && vaccine.getRecurrenceDays() >= 0);
        boolean boosterEligible = !recurring && vaccine.getBoosterGapDays() != null && vaccine.getBoosterGapDays() >= 0;
        boolean ageGroupEligible = isApplicableAgeGroup(applicableAgeGroup, patientAgeGroup);
        boolean adult = isAdult(patientAgeGroup);
        boolean olderAdult = "OLDER_ADULT".equals(patientAgeGroup);
        boolean completedByGroupDose = hasCompletedDose(history, vaccine);
        boolean completedByVaccine = latestHistory != null;

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
            return recurringRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, latestGivenDate, completedByVaccine);
        }

        if (boosterEligible) {
            return boosterRecommendation(vaccine, patientAgeDays, patientAgeGroup, today, latestGivenDate, completedByVaccine);
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

    private boolean hasCompletedDose(List<PatientVaccinationEntity> history, VaccineMasterEntity vaccine) {
        return latest(history) != null;
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

    private String historyKey(PatientVaccinationEntity entry, Map<UUID, VaccineMasterEntity> vaccineById) {
        if (entry.getVaccineId() != null) {
            VaccineMasterEntity vaccine = vaccineById.get(entry.getVaccineId());
            if (vaccine != null) {
                return masterKey(vaccine);
            }
        }
        return String.join("|", normalize(entry.getVaccineNameSnapshot()), "", "");
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
}
