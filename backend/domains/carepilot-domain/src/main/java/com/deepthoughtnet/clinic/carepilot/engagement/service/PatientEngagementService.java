package com.deepthoughtnet.clinic.carepilot.engagement.service;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.billing.db.BillEntity;
import com.deepthoughtnet.clinic.billing.db.BillRepository;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.carepilot.engagement.analytics.PatientEngagementOverviewRecord;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementCohortType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementLevel;
import com.deepthoughtnet.clinic.carepilot.engagement.model.PatientEngagementProfileRecord;
import com.deepthoughtnet.clinic.carepilot.engagement.model.RiskLevel;
import com.deepthoughtnet.clinic.carepilot.engagement.scoring.EngagementScoringConfig;
import com.deepthoughtnet.clinic.carepilot.engagement.scoring.RuleBasedEngagementScorer;
import com.deepthoughtnet.clinic.carepilot.engagement.scoring.RuleBasedEngagementScorer.ScoringSignals;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationEntity;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes engagement indicators and cohorts from existing patient operational data.
 */
@Service
public class PatientEngagementService {
    private static final Pattern DURATION_TOKEN_PATTERN = Pattern.compile("(?i)\\b(\\d+)\\s*(day|days|week|weeks|month|months|d|w|m)\\b");

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionMedicineRepository prescriptionMedicineRepository;
    private final BillRepository billRepository;
    private final PatientVaccinationRepository vaccinationRepository;
    private final CampaignExecutionRepository executionRepository;
    private final RuleBasedEngagementScorer scorer;
    private final EngagementScoringConfig scoringConfig;

    public PatientEngagementService(
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            ConsultationRepository consultationRepository,
            PrescriptionRepository prescriptionRepository,
            PrescriptionMedicineRepository prescriptionMedicineRepository,
            BillRepository billRepository,
            PatientVaccinationRepository vaccinationRepository,
            CampaignExecutionRepository executionRepository,
            @Value("${carepilot.engagement.inactive-days:90}") int inactiveDays,
            @Value("${carepilot.engagement.high-risk-no-show-count:2}") int highRiskNoShowCount,
            @Value("${carepilot.engagement.overdue-bill-days:3}") int overdueBillDays
    ) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionMedicineRepository = prescriptionMedicineRepository;
        this.billRepository = billRepository;
        this.vaccinationRepository = vaccinationRepository;
        this.executionRepository = executionRepository;
        this.scorer = new RuleBasedEngagementScorer();
        this.scoringConfig = new EngagementScoringConfig(
                Math.max(30, inactiveDays),
                Math.max(1, highRiskNoShowCount),
                Math.max(0, overdueBillDays)
        );
    }

    /** Returns aggregate engagement counts for analytics and dashboard cards. */
    @Transactional(readOnly = true)
    public PatientEngagementOverviewRecord overview(UUID tenantId) {
        List<PatientEngagementProfileRecord> profiles = profiles(tenantId);
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (EngagementLevel level : EngagementLevel.values()) {
            distribution.put(level.name(), profiles.stream().filter(p -> p.engagementLevel() == level).count());
        }

        Map<String, Long> cohorts = new LinkedHashMap<>();
        for (EngagementCohortType cohort : EngagementCohortType.values()) {
            cohorts.put(cohort.name(), profiles.stream().filter(p -> inCohort(p, cohort)).count());
        }

        return new PatientEngagementOverviewRecord(
                profiles.size(),
                countByLevel(profiles, EngagementLevel.HIGH),
                countByLevel(profiles, EngagementLevel.MEDIUM),
                countByLevel(profiles, EngagementLevel.LOW),
                countByLevel(profiles, EngagementLevel.CRITICAL),
                profiles.stream().filter(PatientEngagementProfileRecord::inactive).count(),
                profiles.stream().filter(this::isHighRisk).count(),
                profiles.stream().filter(p -> p.refillRisk() == RiskLevel.HIGH).count(),
                profiles.stream().filter(p -> p.followUpRisk() == RiskLevel.HIGH).count(),
                profiles.stream().filter(p -> p.overdueVaccinationsCount() > 0).count(),
                profiles.stream().filter(p -> p.overdueBalanceRisk() == RiskLevel.HIGH).count(),
                distribution,
                cohorts,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    /** Returns one cohort with pageable slice semantics for operations and targeting. */
    @Transactional(readOnly = true)
    public List<PatientEngagementProfileRecord> cohort(UUID tenantId, EngagementCohortType cohort, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(200, limit));
        return profiles(tenantId).stream()
                .filter(profile -> inCohort(profile, cohort))
                .sorted(Comparator.comparingInt(PatientEngagementProfileRecord::engagementScore)
                        .thenComparing(PatientEngagementProfileRecord::patientName, String.CASE_INSENSITIVE_ORDER))
                .skip(safeOffset)
                .limit(safeLimit)
                .toList();
    }

    /** Returns all computed profiles for tenant-scoped operational workflows. */
    @Transactional(readOnly = true)
    public List<PatientEngagementProfileRecord> profiles(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<CampaignExecutionEntity> campaignExecutions = executionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<UUID, OffsetDateTime> lastCampaignEngaged = new LinkedHashMap<>();
        for (CampaignExecutionEntity execution : campaignExecutions) {
            if (execution.getRecipientPatientId() == null || execution.getStatus() != ExecutionStatus.SUCCEEDED) {
                continue;
            }
            lastCampaignEngaged.putIfAbsent(execution.getRecipientPatientId(), execution.getExecutedAt());
        }

        List<PatientEngagementProfileRecord> result = new ArrayList<>();
        for (PatientEntity patient : patientRepository.findByTenantIdAndActiveTrue(tenantId)) {
            List<AppointmentEntity> appointments = appointmentRepository.findByTenantIdAndPatientIdOrderByAppointmentDateDescAppointmentTimeDescCreatedAtDesc(
                    tenantId,
                    patient.getId()
            );
            List<ConsultationEntity> consultations = consultationRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patient.getId());
            List<PrescriptionEntity> prescriptions = prescriptionRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patient.getId());
            List<BillEntity> bills = billRepository.findByTenantIdAndPatientIdOrderByBillDateDescCreatedAtDesc(tenantId, patient.getId());
            List<PatientVaccinationEntity> vaccinations = vaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(tenantId, patient.getId());

            int missedAppointments = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
            int completedAppointments = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
            LocalDate lastAppointmentAt = appointments.stream().map(AppointmentEntity::getAppointmentDate).max(LocalDate::compareTo).orElse(null);

            LocalDate lastConsultationAt = consultations.stream()
                    .map(c -> c.getCompletedAt() == null ? c.getCreatedAt().toLocalDate() : c.getCompletedAt().toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElse(null);
            int followUpMissedCount = (int) consultations.stream()
                    .filter(c -> c.getStatus() == ConsultationStatus.COMPLETED && c.getFollowUpDate() != null && c.getFollowUpDate().isBefore(today))
                    .count();

            int pendingRefillCount = pendingRefillCount(tenantId, prescriptions, today);
            int overdueBillsCount = (int) bills.stream()
                    .filter(b -> (b.getStatus() == BillStatus.UNPAID || b.getStatus() == BillStatus.ISSUED || b.getStatus() == BillStatus.PARTIALLY_PAID)
                            && b.getDueAmount() != null
                            && b.getDueAmount().signum() > 0
                            && b.getBillDate() != null
                            && b.getBillDate().plusDays(scoringConfig.overdueBillDays()).isBefore(today.plusDays(1)))
                    .count();

            int overdueVaccinationsCount = (int) vaccinations.stream()
                    .filter(v -> v.getNextDueDate() != null && v.getNextDueDate().isBefore(today))
                    .count();

            LocalDate lastInteraction = maxDate(lastAppointmentAt, lastConsultationAt);
            int inactiveDays = lastInteraction == null ? scoringConfig.inactiveDays() + 1 : (int) ChronoUnit.DAYS.between(lastInteraction, today);
            int recentVisitDays = lastInteraction == null ? -1 : (int) ChronoUnit.DAYS.between(lastInteraction, today);

            ScoringSignals signals = new ScoringSignals(
                    inactiveDays,
                    recentVisitDays,
                    missedAppointments,
                    completedAppointments,
                    overdueBillsCount,
                    overdueVaccinationsCount,
                    pendingRefillCount,
                    followUpMissedCount
            );
            int score = scorer.score(signals, scoringConfig);
            EngagementLevel level = scorer.level(score);

            RiskLevel inactiveRisk = riskForInactiveDays(inactiveDays);
            RiskLevel noShowRisk = riskForCount(missedAppointments, scoringConfig.highRiskNoShowCount());
            RiskLevel refillRisk = riskForCount(pendingRefillCount, 1);
            RiskLevel followUpRisk = riskForCount(followUpMissedCount, 1);
            RiskLevel balanceRisk = riskForCount(overdueBillsCount, 1);
            RiskLevel vaccinationRisk = riskForCount(overdueVaccinationsCount, 1);

            List<String> reasons = new ArrayList<>();
            if (inactiveRisk == RiskLevel.HIGH) {
                reasons.add("No recent appointment/consultation activity for " + inactiveDays + " days");
            }
            if (noShowRisk != RiskLevel.LOW) {
                reasons.add("Missed appointments: " + missedAppointments);
            }
            if (balanceRisk != RiskLevel.LOW) {
                reasons.add("Overdue bills: " + overdueBillsCount);
            }
            if (refillRisk != RiskLevel.LOW) {
                reasons.add("Pending refill risk prescriptions: " + pendingRefillCount);
            }
            if (followUpRisk != RiskLevel.LOW) {
                reasons.add("Missed follow-ups: " + followUpMissedCount);
            }
            if (vaccinationRisk != RiskLevel.LOW) {
                reasons.add("Overdue vaccinations: " + overdueVaccinationsCount);
            }

            result.add(new PatientEngagementProfileRecord(
                    patient.getId(),
                    patient.getTenantId(),
                    patient.getPatientNumber(),
                    patient.getFirstName() + " " + patient.getLastName(),
                    patient.getEmail(),
                    patient.getMobile(),
                    score,
                    level,
                    inactiveRisk,
                    noShowRisk,
                    refillRisk,
                    followUpRisk,
                    balanceRisk,
                    vaccinationRisk,
                    lastAppointmentAt,
                    lastConsultationAt,
                    lastCampaignEngaged.get(patient.getId()),
                    missedAppointments,
                    completedAppointments,
                    overdueBillsCount,
                    overdueVaccinationsCount,
                    pendingRefillCount,
                    followUpMissedCount,
                    inactiveRisk == RiskLevel.HIGH,
                    reasons,
                    suggestedCampaignType(inactiveRisk, noShowRisk, refillRisk, followUpRisk, balanceRisk, vaccinationRisk),
                    OffsetDateTime.now(ZoneOffset.UTC)
            ));
        }
        return result;
    }

    private boolean isHighRisk(PatientEngagementProfileRecord profile) {
        return profile.engagementLevel() == EngagementLevel.CRITICAL
                || profile.inactiveRisk() == RiskLevel.HIGH
                || profile.noShowRisk() == RiskLevel.HIGH
                || profile.refillRisk() == RiskLevel.HIGH
                || profile.followUpRisk() == RiskLevel.HIGH
                || profile.overdueBalanceRisk() == RiskLevel.HIGH
                || profile.vaccinationCompliance() == RiskLevel.HIGH;
    }

    private boolean inCohort(PatientEngagementProfileRecord profile, EngagementCohortType cohort) {
        return switch (cohort) {
            case HIGH_RISK_PATIENTS -> isHighRisk(profile);
            case INACTIVE_PATIENTS -> profile.inactive();
            case HIGH_NO_SHOW_RISK -> profile.noShowRisk() == RiskLevel.HIGH;
            case OVERDUE_BILL_PATIENTS -> profile.overdueBalanceRisk() == RiskLevel.HIGH;
            case REFILL_RISK_PATIENTS -> profile.refillRisk() == RiskLevel.HIGH;
            case VACCINATION_OVERDUE_PATIENTS -> profile.overdueVaccinationsCount() > 0;
            case HIGH_ENGAGEMENT_PATIENTS -> profile.engagementLevel() == EngagementLevel.HIGH;
            case LOW_ENGAGEMENT_PATIENTS -> profile.engagementLevel() == EngagementLevel.LOW || profile.engagementLevel() == EngagementLevel.CRITICAL;
            case FOLLOW_UP_OVERDUE_PATIENTS -> profile.followUpRisk() == RiskLevel.HIGH;
        };
    }

    private long countByLevel(List<PatientEngagementProfileRecord> profiles, EngagementLevel level) {
        return profiles.stream().filter(p -> p.engagementLevel() == level).count();
    }

    private RiskLevel riskForInactiveDays(int inactiveDays) {
        if (inactiveDays >= scoringConfig.inactiveDays()) {
            return RiskLevel.HIGH;
        }
        if (inactiveDays >= Math.max(30, scoringConfig.inactiveDays() / 2)) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private RiskLevel riskForCount(int count, int highThreshold) {
        if (count >= highThreshold) {
            return RiskLevel.HIGH;
        }
        if (count > 0) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String suggestedCampaignType(
            RiskLevel inactiveRisk,
            RiskLevel noShowRisk,
            RiskLevel refillRisk,
            RiskLevel followUpRisk,
            RiskLevel balanceRisk,
            RiskLevel vaccinationRisk
    ) {
        if (refillRisk == RiskLevel.HIGH) {
            return "REFILL_REMINDER";
        }
        if (followUpRisk == RiskLevel.HIGH || noShowRisk == RiskLevel.HIGH) {
            return "FOLLOW_UP_REMINDER";
        }
        if (balanceRisk == RiskLevel.HIGH) {
            return "BILLING_REMINDER";
        }
        if (vaccinationRisk == RiskLevel.HIGH) {
            return "VACCINATION_REMINDER";
        }
        if (inactiveRisk == RiskLevel.HIGH) {
            return "WELLNESS_MESSAGE";
        }
        return "APPOINTMENT_REMINDER";
    }

    private int pendingRefillCount(UUID tenantId, List<PrescriptionEntity> prescriptions, LocalDate today) {
        int count = 0;
        for (PrescriptionEntity prescription : prescriptions) {
            if (prescription.getStatus() == PrescriptionStatus.CANCELLED || prescription.getFinalizedAt() == null) {
                continue;
            }
            LocalDate finalizedDate = prescription.getFinalizedAt().toLocalDate();
            int refillDays = resolvePrescriptionRefillDays(tenantId, prescription.getId());
            LocalDate refillDue = finalizedDate.plusDays(refillDays);
            if (refillDue.isBefore(today.plusDays(1))) {
                count += 1;
            }
        }
        return count;
    }

    private int resolvePrescriptionRefillDays(UUID tenantId, UUID prescriptionId) {
        int best = 30;
        for (PrescriptionMedicineEntity medicine : prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescriptionId)) {
            int parsed = parseDurationDays(medicine.getDuration());
            if (parsed > 0) {
                best = Math.max(best, parsed);
            }
        }
        return best;
    }

    /**
     * Parses common duration strings like "7 days", "2 weeks", or "1 month" into whole days.
     */
    private int parseDurationDays(String duration) {
        if (duration == null || duration.isBlank()) {
            return 0;
        }
        Matcher matcher = DURATION_TOKEN_PATTERN.matcher(duration.toLowerCase(Locale.ROOT));
        if (!matcher.find()) {
            return 0;
        }
        int value;
        try {
            value = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return 0;
        }
        String unit = matcher.group(2);
        if (unit.startsWith("w")) {
            return value * 7;
        }
        if (unit.startsWith("m")) {
            return value * 30;
        }
        return value;
    }

    private LocalDate maxDate(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }
}
