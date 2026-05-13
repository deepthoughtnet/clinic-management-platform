package com.deepthoughtnet.clinic.carepilot.lead.analytics;

import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes lightweight lead analytics for CarePilot dashboards. */
@Service
public class LeadAnalyticsService {
    private static final Collection<LeadStatus> TERMINAL = List.of(LeadStatus.CONVERTED, LeadStatus.LOST, LeadStatus.SPAM);

    private final LeadRepository repository;

    public LeadAnalyticsService(LeadRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public LeadAnalyticsRecord summary(UUID tenantId, LocalDate startDate, LocalDate endDate) {
        CarePilotValidators.requireTenant(tenantId);
        OffsetDateTime now = OffsetDateTime.now();
        long total = repository.countByTenantId(tenantId);
        long newLeads = repository.countByTenantIdAndStatus(tenantId, LeadStatus.NEW);
        long qualified = repository.countByTenantIdAndStatus(tenantId, LeadStatus.QUALIFIED);
        long converted = repository.countByTenantIdAndStatus(tenantId, LeadStatus.CONVERTED);
        long lost = repository.countByTenantIdAndStatus(tenantId, LeadStatus.LOST);
        long followUpsDue = repository.countByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(tenantId, now, TERMINAL);

        OffsetDateTime todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime todayEnd = todayStart.plusDays(1).minusNanos(1);
        List<LeadEntity> dueRows = repository.findByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(tenantId, todayEnd, TERMINAL);
        long dueToday = dueRows.stream().filter(r -> r.getNextFollowUpAt() != null && !r.getNextFollowUpAt().isBefore(todayStart) && !r.getNextFollowUpAt().isAfter(todayEnd)).count();
        long overdue = dueRows.stream().filter(r -> r.getNextFollowUpAt() != null && r.getNextFollowUpAt().isBefore(todayStart)).count();

        List<LeadEntity> staleWindow = repository.findByTenantIdAndCreatedAtBetween(tenantId, OffsetDateTime.now().minusDays(3650), OffsetDateTime.now().minusDays(7));
        long stale = staleWindow.stream().filter(row -> !row.getStatus().isTerminal()).count();

        List<LeadEntity> highPriorityWindow = repository.findByTenantIdAndCreatedAtBetween(tenantId, OffsetDateTime.now().minusDays(3650), OffsetDateTime.now().plusDays(1));
        long highPriority = highPriorityWindow.stream().filter(row -> row.getPriority() == LeadPriority.HIGH && row.getStatus().isActivePipeline()).count();

        LocalDate effectiveStart = startDate == null ? LocalDate.now(ZoneOffset.UTC).minusDays(30) : startDate;
        LocalDate effectiveEnd = endDate == null ? LocalDate.now(ZoneOffset.UTC) : endDate;
        OffsetDateTime from = effectiveStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = effectiveEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);
        List<LeadEntity> window = repository.findByTenantIdAndCreatedAtBetween(tenantId, from, to);
        Map<String, Long> sourceBreakdown = window.stream()
                .collect(Collectors.groupingBy(row -> row.getSource().name(), LinkedHashMap::new, Collectors.counting()));

        long conversionsWithAppointment = repository.countByTenantIdAndConvertedPatientIdIsNotNullAndBookedAppointmentIdIsNotNull(tenantId);
        List<LeadEntity> convertedRows = window.stream().filter(r -> r.getStatus() == LeadStatus.CONVERTED && r.getUpdatedAt() != null).toList();
        Double avgHoursToConversion = convertedRows.isEmpty() ? null : convertedRows.stream()
                .mapToLong(r -> Math.max(0, ChronoUnit.HOURS.between(r.getCreatedAt(), r.getUpdatedAt())))
                .average()
                .orElse(0d);

        return new LeadAnalyticsRecord(
                total,
                newLeads,
                qualified,
                converted,
                lost,
                followUpsDue,
                dueToday,
                overdue,
                total == 0 ? 0d : ((double) converted * 100d) / total,
                sourceBreakdown,
                stale,
                highPriority,
                conversionsWithAppointment,
                avgHoursToConversion
        );
    }
}
