package com.deepthoughtnet.clinic.api.notifications.operations;

import com.deepthoughtnet.clinic.api.admin.AdminIntegrationsStatusService;
import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatusRow;
import com.deepthoughtnet.clinic.api.notifications.AppointmentReminderProperties;
import com.deepthoughtnet.clinic.api.notifications.NotificationsSchedulerProperties;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsAnalyticsResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsAuditResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsAuditRow;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsChannelRow;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsDeliveryRow;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsKpiCard;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsPageResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsProviderRow;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsQuery;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsRetryResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsRetryResult;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsSchedulerStatus;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsSeriesPoint;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsSummaryResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationCenterService;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryFilter;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.NotificationSummary;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryGroupRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderRecord;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
import org.springframework.util.StringUtils;

@Service
public class NotificationOperationsService {
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final Set<String> RETRYABLE_FAILURE_CATEGORIES = Set.of(
            "PROVIDER_UNAVAILABLE",
            "PROVIDER_REJECTED",
            "RATE_LIMITED",
            "TRANSIENT_NETWORK_FAILURE",
            "INVALID_TEMPLATE",
            "UNKNOWN"
    );
    private static final Set<String> STALE_FAILURE_MARKERS = Set.of(
            "already paid",
            "no longer applicable",
            "cancelled",
            "canceled",
            "stale",
            "expired",
            "invalidated"
    );

    private final NotificationHistoryService notificationHistoryService;
    private final NotificationCenterService notificationCenterService;
    private final PlatformTenantManagementService tenantManagementService;
    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final BillingService billingService;
    private final PrescriptionService prescriptionService;
    private final LabService labService;
    private final VaccinationService vaccinationService;
    private final AdminIntegrationsStatusService integrationsStatusService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final NotificationsSchedulerProperties schedulerProperties;
    private final AppointmentReminderProperties appointmentReminderProperties;
    private final AuditEventQueryService auditEventQueryService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public NotificationOperationsService(
            NotificationHistoryService notificationHistoryService,
            NotificationCenterService notificationCenterService,
            PlatformTenantManagementService tenantManagementService,
            PatientService patientService,
            AppointmentService appointmentService,
            BillingService billingService,
            PrescriptionService prescriptionService,
            LabService labService,
            VaccinationService vaccinationService,
            AdminIntegrationsStatusService integrationsStatusService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            NotificationsSchedulerProperties schedulerProperties,
            AppointmentReminderProperties appointmentReminderProperties,
            AuditEventQueryService auditEventQueryService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.notificationHistoryService = notificationHistoryService;
        this.notificationCenterService = notificationCenterService;
        this.tenantManagementService = tenantManagementService;
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.billingService = billingService;
        this.prescriptionService = prescriptionService;
        this.labService = labService;
        this.vaccinationService = vaccinationService;
        this.integrationsStatusService = integrationsStatusService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.schedulerProperties = schedulerProperties;
        this.appointmentReminderProperties = appointmentReminderProperties;
        this.auditEventQueryService = auditEventQueryService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    public NotificationOperationsSummaryResponse summary(UUID tenantId, NotificationOperationsQuery query) {
        ResolvedWindow window = resolveWindow(tenantId, query);
        List<EnrichedGroup> groups = loadGroups(window, query);
        long logicalNotifications = groups.size();
        long attemptedDeliveries = groups.stream().mapToLong(group -> group.group().deliveries().size()).sum();
        long sent = countDeliveries(groups, Set.of("SENT", "DELIVERED"));
        long pending = countDeliveries(groups, Set.of("PENDING", "QUEUED", "RETRYING"));
        long failed = countDeliveries(groups, Set.of("FAILED"));
        long skipped = countDeliveries(groups, Set.of("SKIPPED"));
        long partial = groups.stream().filter(group -> "PARTIAL".equals(group.group().overallStatus())).count();
        long retries = groups.stream().flatMap(group -> group.group().deliveries().stream()).mapToLong(NotificationHistoryRecord::attemptCount).sum();
        long stale = groups.stream().flatMap(group -> group.group().deliveries().stream())
                .filter(record -> isStaleReason(record.failureReason()))
                .count();
        double successRate = attemptedDeliveries == 0 ? 0.0 : (sent * 100.0 / attemptedDeliveries);
        double averageLatencyMs = averageLatencyForGroups(groups);
        NotificationSummary outbox = notificationCenterService.summarize(tenantId);
        PlatformTenantRecord tenant = tenantManagementService.get(tenantId);

        List<NotificationOperationsKpiCard> kpis = List.of(
                kpi("Logical notifications", String.valueOf(logicalNotifications), "Grouped business notifications in the selected period"),
                kpi("Channel deliveries", String.valueOf(attemptedDeliveries), "Per-channel delivery rows across all notifications"),
                kpi("Sent / Delivered", String.valueOf(sent), "Successful channel deliveries"),
                kpi("Pending / Queued", String.valueOf(pending), "Deliveries waiting for dispatch or retry"),
                kpi("Failed", String.valueOf(failed), "Failed deliveries requiring investigation"),
                kpi("Skipped", String.valueOf(skipped), "Disabled, ineligible, or intentionally skipped deliveries"),
                kpi("Partial", String.valueOf(partial), "Logical notifications with mixed outcomes"),
                kpi("Success rate", formatPercentage(successRate), "Sent deliveries divided by all channel deliveries"),
                kpi("Average latency", formatDuration(averageLatencyMs), "Queued to sent latency for successful deliveries"),
                kpi("Retries", String.valueOf(retries), "Persisted retry attempts across the selected rows"),
                kpi("Stale suppressed", String.valueOf(stale), "Deliveries marked stale or no longer applicable")
        );

        return new NotificationOperationsSummaryResponse(
                tenantId,
                tenant.name(),
                window.periodLabel(),
                window.from(),
                window.to(),
                logicalNotifications,
                attemptedDeliveries,
                sent,
                pending,
                failed,
                skipped,
                partial,
                successRate,
                averageLatencyMs,
                retries,
                stale,
                new NotificationOperationsSchedulerStatus(
                        schedulerProperties.enabled(),
                        schedulerProperties.fixedDelay(),
                        appointmentReminderProperties.isEnabled(),
                        appointmentReminderProperties.getHoursBefore(),
                        appointmentReminderProperties.getGraceMinutes(),
                        outbox.pendingCount(),
                        outbox.failedCount()
                ),
                kpis
        );
    }

    public NotificationOperationsPageResponse deliveries(UUID tenantId, NotificationOperationsQuery query) {
        ResolvedWindow window = resolveWindow(tenantId, query);
        List<EnrichedGroup> groups = loadGroups(window, query);
        List<EnrichedGroup> filtered = applyFilters(groups, query);
        int page = Math.max(0, query.page());
        int size = normalizeSize(query.size());
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<NotificationOperationsDeliveryRow> items = filtered.subList(fromIndex, toIndex).stream()
                .map(EnrichedGroup::row)
                .toList();
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil(filtered.size() / (double) size);
        return new NotificationOperationsPageResponse(items, page, size, filtered.size(), totalPages);
    }

    public NotificationOperationsDeliveryRow delivery(UUID tenantId, String logicalNotificationId, NotificationOperationsQuery query) {
        return deliveries(tenantId, query).items().stream()
                .filter(row -> row.logicalNotificationId().equals(logicalNotificationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Logical notification not found"));
    }

    public NotificationOperationsPageResponse failures(UUID tenantId, NotificationOperationsQuery query) {
        ResolvedWindow window = resolveWindow(tenantId, query);
        List<EnrichedGroup> groups = loadGroups(window, query);
        List<EnrichedGroup> filtered = applyFilters(groups, query).stream()
                .filter(this::isActionableFailure)
                .toList();
        int page = Math.max(0, query.page());
        int size = normalizeSize(query.size());
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<NotificationOperationsDeliveryRow> items = filtered.subList(fromIndex, toIndex).stream().map(EnrichedGroup::row).toList();
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil(filtered.size() / (double) size);
        return new NotificationOperationsPageResponse(items, page, size, filtered.size(), totalPages);
    }

    public List<NotificationOperationsProviderRow> providers(UUID tenantId, NotificationOperationsQuery query) {
        ResolvedWindow window = resolveWindow(tenantId, query);
        List<EnrichedGroup> groups = applyFilters(loadGroups(window, query), query);
        List<IntegrationStatusRow> integrationRows = integrationsStatusService.status(tenantId);
        Map<String, List<NotificationHistoryRecord>> deliveriesByChannel = groups.stream()
                .flatMap(group -> group.group().deliveries().stream())
                .collect(Collectors.groupingBy(record -> normalizeChannel(record.channel())));

        return integrationRows.stream()
                .filter(row -> "MESSAGING".equals(row.category()))
                .map(row -> {
                    List<NotificationHistoryRecord> deliveries = deliveriesByChannel.getOrDefault(normalizeChannelFromIntegrationKey(row.key()), List.of());
                    long successCount = deliveries.stream().filter(this::isSuccess).count();
                    long failureCount = deliveries.stream().filter(this::isFailure).count();
                    long pendingCount = deliveries.stream().filter(this::isPending).count();
                    double averageLatencyMs = averageLatency(deliveries);
                    OffsetDateTime lastSuccess = deliveries.stream().filter(this::isSuccess).map(NotificationHistoryRecord::sentAt).filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
                    OffsetDateTime lastFailure = deliveries.stream().filter(this::isFailure).map(NotificationHistoryRecord::updatedAt).filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
                    return new NotificationOperationsProviderRow(
                            row.key(),
                            row.name(),
                            row.category(),
                            row.status().name(),
                            mapReadiness(row.status().name()),
                            row.enabled(),
                            row.configured(),
                            pendingCount,
                            failureCount,
                            successCount,
                            averageLatencyMs,
                            lastSuccess,
                            lastFailure,
                            row.lastCheckedAt(),
                            row.message()
                    );
                })
                .toList();
    }

    public NotificationOperationsAnalyticsResponse analytics(UUID tenantId, NotificationOperationsQuery query) {
        ResolvedWindow window = resolveWindow(tenantId, query);
        List<EnrichedGroup> groups = applyFilters(loadGroups(window, query), query);
        Map<String, Long> notificationsByDay = new LinkedHashMap<>();
        Map<String, Long> successFailureTrend = new LinkedHashMap<>();
        Map<String, Long> channelDistribution = new LinkedHashMap<>();
        Map<String, Long> statusDistribution = new LinkedHashMap<>();
        Map<String, Long> topCategories = new LinkedHashMap<>();
        Map<String, Long> topFailureReasons = new LinkedHashMap<>();
        Map<String, Long> providerPerformance = new LinkedHashMap<>();
        Map<String, Long> retryOutcomes = new LinkedHashMap<>();

        for (EnrichedGroup group : groups) {
            String day = formatDay(group.row().queuedAt(), window.zone());
            notificationsByDay.merge(day, 1L, Long::sum);
            topCategories.merge(group.row().category(), 1L, Long::sum);
            statusDistribution.merge(group.row().overallStatus(), 1L, Long::sum);
            for (NotificationHistoryRecord delivery : group.group().deliveries()) {
                channelDistribution.merge(channelLabel(normalizeChannel(delivery.channel())), 1L, Long::sum);
                providerPerformance.merge(providerLabel(delivery.channel()), 1L, Long::sum);
                retryOutcomes.merge(delivery.attemptCount() > 0 ? "Retried" : "First attempt", 1L, Long::sum);
                if (isSuccess(delivery)) {
                    successFailureTrend.merge("Success", 1L, Long::sum);
                } else if (isFailure(delivery)) {
                    successFailureTrend.merge("Failure", 1L, Long::sum);
                    failureReasonKey(delivery.failureReason()).ifPresent(reason -> topFailureReasons.merge(reason, 1L, Long::sum));
                }
            }
        }

        return new NotificationOperationsAnalyticsResponse(
                toSeries(notificationsByDay),
                toSeries(successFailureTrend),
                toSeries(channelDistribution),
                toSeries(statusDistribution),
                toSeries(topCategories),
                toSeries(topFailureReasons),
                toSeries(providerPerformance),
                toSeries(retryOutcomes)
        );
    }

    public NotificationOperationsAuditResponse audit(UUID tenantId, NotificationOperationsQuery query) {
        ResolvedWindow window = resolveWindow(tenantId, query);
        List<EnrichedGroup> groups = applyFilters(loadGroups(window, query), query);
        List<AuditEventRecord> audits = new ArrayList<>();
        for (EnrichedGroup group : groups) {
            for (NotificationHistoryRecord delivery : group.group().deliveries()) {
                audits.addAll(auditEventQueryService.listForEntity(tenantId, "NOTIFICATION_HISTORY", delivery.id()));
            }
        }
        List<NotificationOperationsAuditRow> rows = audits.stream()
                .sorted(Comparator.comparing(AuditEventRecord::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAuditRow)
                .toList();
        int page = Math.max(0, query.page());
        int size = normalizeSize(query.size());
        int fromIndex = Math.min(page * size, rows.size());
        int toIndex = Math.min(fromIndex + size, rows.size());
        int totalPages = rows.isEmpty() ? 0 : (int) Math.ceil(rows.size() / (double) size);
        return new NotificationOperationsAuditResponse(rows.subList(fromIndex, toIndex), page, size, rows.size(), totalPages);
    }

    public NotificationOperationsRetryResponse retry(UUID tenantId, UUID actorAppUserId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new NotificationOperationsRetryResponse(List.of(), 0, 0, 0);
        }
        List<NotificationOperationsRetryResult> results = new ArrayList<>();
        int retried = 0;
        int skipped = 0;
        for (UUID id : ids) {
            try {
                NotificationHistoryRecord retriedRecord = notificationHistoryService.retry(tenantId, id, actorAppUserId);
                retried++;
                recordRetryAudit(tenantId, actorAppUserId, retriedRecord);
                results.add(new NotificationOperationsRetryResult(id, "RETRIED", "Retry queued"));
            } catch (RuntimeException ex) {
                skipped++;
                results.add(new NotificationOperationsRetryResult(id, "SKIPPED", ex.getMessage()));
            }
        }
        return new NotificationOperationsRetryResponse(results, ids.size(), retried, skipped);
    }

    private void recordRetryAudit(UUID tenantId, UUID actorAppUserId, NotificationHistoryRecord record) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("notificationId", String.valueOf(record.id()));
            metadata.put("eventType", String.valueOf(record.eventType()));
            metadata.put("channel", String.valueOf(record.channel()));
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    "NOTIFICATION_HISTORY",
                    record.id(),
                    "notification.retry",
                    actorAppUserId,
                    OffsetDateTime.now(),
                    "Retried notification " + record.eventType(),
                    objectMapper.writeValueAsString(metadata)
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to record retry audit", ex);
        }
    }

    private List<NotificationOperationsSeriesPoint> toSeries(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new NotificationOperationsSeriesPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private long countDeliveries(List<EnrichedGroup> groups, Collection<String> statuses) {
        Set<String> wanted = statuses.stream().map(this::normalizeStatus).collect(Collectors.toSet());
        return groups.stream()
                .flatMap(group -> group.group().deliveries().stream())
                .filter(record -> wanted.contains(normalizeStatus(record.status())))
                .count();
    }

    private double averageLatencyForGroups(List<EnrichedGroup> groups) {
        return averageLatency(groups.stream().flatMap(group -> group.group().deliveries().stream()).toList());
    }

    private double averageLatency(List<NotificationHistoryRecord> deliveries) {
        long count = 0;
        long total = 0;
        for (NotificationHistoryRecord delivery : deliveries) {
            if (isSuccess(delivery) && delivery.sentAt() != null && delivery.createdAt() != null) {
                total += Math.max(0, Duration.between(delivery.createdAt(), delivery.sentAt()).toMillis());
                count++;
            }
        }
        return count == 0 ? 0.0 : total / (double) count;
    }

    private NotificationOperationsKpiCard kpi(String label, String value, String helper) {
        return new NotificationOperationsKpiCard(label, value, helper);
    }

    private String formatPercentage(double value) {
        return String.format(Locale.ENGLISH, "%.1f%%", value);
    }

    private String formatDuration(double value) {
        if (value <= 0) {
            return "0m";
        }
        long minutes = Math.max(0, Math.round(value / 60000.0));
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        long remainder = minutes % 60;
        return remainder == 0 ? hours + "h" : hours + "h " + remainder + "m";
    }

    private List<EnrichedGroup> loadGroups(ResolvedWindow window, NotificationOperationsQuery query) {
        NotificationHistoryFilter filter = new NotificationHistoryFilter(
                normalizeNullable(query.status()),
                normalizeNullable(query.eventType()),
                normalizeNullable(query.channel()),
                null,
                window.from(),
                window.to(),
                0,
                normalizeSize(query.size())
        );
        List<NotificationHistoryGroupRecord> groups = notificationHistoryService.listGrouped(window.tenantId(), filter);
        ReferenceData references = loadReferenceData(window.tenantId(), groups);
        return groups.stream()
                .map(group -> enrichGroup(window, group, references))
                .sorted(Comparator
                        .comparing((EnrichedGroup group) -> group.row().lastActivityAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(group -> group.row().queuedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(group -> group.row().logicalNotificationId()))
                .toList();
    }

    private EnrichedGroup enrichGroup(ResolvedWindow window, NotificationHistoryGroupRecord group, ReferenceData references) {
        NotificationHistoryRecord primary = group.deliveries().isEmpty() ? null : group.deliveries().getFirst();
        String patientName = resolvePatientName(group.patientId(), references.patients());
        String patientReference = resolvePatientReference(group.patientId(), references.patients());
        String businessReference = resolveBusinessReference(window.tenantId(), primary, references);
        String sourceModule = resolveSourceModule(primary);
        String category = resolveCategory(group.eventType());
        String eventLabel = humanizeEventType(group.eventType());
        NotificationOperationsDeliveryRow row = new NotificationOperationsDeliveryRow(
                group.logicalNotificationId(),
                group.tenantId(),
                tenantManagementService.get(group.tenantId()).name(),
                group.patientId(),
                patientName,
                patientReference,
                group.eventType(),
                eventLabel,
                category,
                sourceModule,
                businessReference,
                group.overallStatus(),
                group.readState(),
                summarizeMessage(group.message()),
                group.queuedAt(),
                group.lastActivityAt(),
                (int) group.deliveries().stream().mapToLong(NotificationHistoryRecord::attemptCount).sum(),
                group.deliveries().size(),
                group.deliveries().stream().sorted(Comparator.comparingInt(record -> channelOrder(normalizeChannel(record.channel())))).map(record -> toChannelRow(window.zone(), record)).toList()
        );
        return new EnrichedGroup(group, patientName, patientReference, businessReference, sourceModule, hasFailure(group.deliveries()), hasRetry(group.deliveries()), row);
    }

    private NotificationOperationsChannelRow toChannelRow(ZoneId zone, NotificationHistoryRecord record) {
        String channel = normalizeChannel(record.channel());
        return new NotificationOperationsChannelRow(
                record.id(),
                channel,
                normalizeStatus(record.status()),
                displayStatus(record),
                maskRecipient(channel, record.recipient()),
                providerLabel(channel),
                normalizeReason(record.failureReason()),
                failureCategory(record.failureReason()),
                isRetryable(record),
                record.createdAt(),
                record.sentAt(),
                record.attemptCount(),
                record.attemptCount() + 1,
                record.outboxEventId() == null ? null : record.outboxEventId().toString()
        );
    }

    private ReferenceData loadReferenceData(UUID tenantId, List<NotificationHistoryGroupRecord> groups) {
        Set<UUID> patientIds = groups.stream().map(NotificationHistoryGroupRecord::patientId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> appointmentIds = new java.util.HashSet<>();
        Set<UUID> billIds = new java.util.HashSet<>();
        Set<UUID> paymentIds = new java.util.HashSet<>();
        Set<UUID> receiptIds = new java.util.HashSet<>();
        Set<UUID> prescriptionIds = new java.util.HashSet<>();
        Set<UUID> labOrderIds = new java.util.HashSet<>();
        Set<UUID> vaccinationIds = new java.util.HashSet<>();

        for (NotificationHistoryGroupRecord group : groups) {
            group.deliveries().forEach(record -> {
                UUID sourceId = record.sourceId();
                if (sourceId == null) return;
                switch (normalizeSourceType(record.sourceType())) {
                    case "APPOINTMENT" -> appointmentIds.add(sourceId);
                    case "BILL", "REFUND" -> billIds.add(sourceId);
                    case "PAYMENT" -> paymentIds.add(sourceId);
                    case "RECEIPT" -> receiptIds.add(sourceId);
                    case "PRESCRIPTION" -> prescriptionIds.add(sourceId);
                    case "LAB", "LAB_ORDER" -> labOrderIds.add(sourceId);
                    case "VACCINATION" -> vaccinationIds.add(sourceId);
                }
            });
        }

        Map<UUID, PatientRecord> patients = loadPatients(tenantId, patientIds);
        Map<UUID, AppointmentRecord> appointments = loadAppointments(tenantId, appointmentIds);
        Map<UUID, BillRecord> bills = loadBills(tenantId, billIds);
        Map<UUID, PaymentRecord> payments = loadPayments(tenantId, paymentIds);
        Map<UUID, ReceiptRecord> receipts = loadReceipts(tenantId, receiptIds);
        Map<UUID, PrescriptionRecord> prescriptions = loadPrescriptions(tenantId, prescriptionIds);
        Map<UUID, LabOrderRecord> labOrders = loadLabOrders(tenantId, labOrderIds);
        Map<UUID, PatientVaccinationRecord> vaccinations = loadVaccinations(tenantId, vaccinationIds);
        return new ReferenceData(patients, appointments, bills, payments, receipts, prescriptions, labOrders, vaccinations);
    }

    private Map<UUID, PatientRecord> loadPatients(UUID tenantId, Set<UUID> ids) {
        Map<UUID, PatientRecord> rows = new LinkedHashMap<>();
        for (UUID id : ids) {
            patientService.findById(tenantId, id).ifPresent(record -> rows.put(id, record));
        }
        return rows;
    }

    private Map<UUID, AppointmentRecord> loadAppointments(UUID tenantId, Set<UUID> ids) {
        Map<UUID, AppointmentRecord> rows = new LinkedHashMap<>();
        for (UUID id : ids) {
            AppointmentRecord record = appointmentService.findById(tenantId, id);
            if (record != null) {
                rows.put(id, record);
            }
        }
        return rows;
    }

    private Map<UUID, BillRecord> loadBills(UUID tenantId, Set<UUID> ids) {
        Map<UUID, BillRecord> rows = new LinkedHashMap<>();
        for (UUID id : ids) {
            billingService.findById(tenantId, id).ifPresent(record -> rows.put(id, record));
        }
        return rows;
    }

    private Map<UUID, PaymentRecord> loadPayments(UUID tenantId, Set<UUID> ids) {
        Map<UUID, PaymentRecord> rows = new LinkedHashMap<>();
        for (UUID id : ids) {
            billingService.findPayment(tenantId, id).ifPresent(record -> rows.put(id, record));
        }
        return rows;
    }

    private Map<UUID, ReceiptRecord> loadReceipts(UUID tenantId, Set<UUID> ids) {
        Map<UUID, ReceiptRecord> rows = new LinkedHashMap<>();
        for (UUID id : ids) {
            billingService.findReceipt(tenantId, id).ifPresent(record -> rows.put(id, record));
        }
        return rows;
    }

    private Map<UUID, PrescriptionRecord> loadPrescriptions(UUID tenantId, Set<UUID> ids) {
        Map<UUID, PrescriptionRecord> rows = new LinkedHashMap<>();
        for (UUID id : ids) {
            prescriptionService.findById(tenantId, id).ifPresent(record -> rows.put(id, record));
        }
        return rows;
    }

    private Map<UUID, LabOrderRecord> loadLabOrders(UUID tenantId, Set<UUID> ids) {
        Map<UUID, LabOrderRecord> rows = new LinkedHashMap<>();
        for (UUID id : ids) {
            labService.findOrder(tenantId, id).ifPresent(record -> rows.put(id, record));
        }
        return rows;
    }

    private Map<UUID, PatientVaccinationRecord> loadVaccinations(UUID tenantId, Set<UUID> ids) {
        Map<UUID, PatientVaccinationRecord> rows = new LinkedHashMap<>();
        for (UUID id : ids) {
            vaccinationService.findById(tenantId, id).ifPresent(record -> rows.put(id, record));
        }
        return rows;
    }

    private String resolvePatientName(UUID patientId, Map<UUID, PatientRecord> patients) {
        if (patientId == null) {
            return "Patient record unavailable";
        }
        PatientRecord patient = patients.get(patientId);
        return patient == null ? "Patient record unavailable" : patient.fullName();
    }

    private String resolvePatientReference(UUID patientId, Map<UUID, PatientRecord> patients) {
        if (patientId == null) {
            return "Patient record unavailable";
        }
        PatientRecord patient = patients.get(patientId);
        if (patient == null) {
            return "Patient record unavailable";
        }
        if (StringUtils.hasText(patient.patientNumber())) {
            return patient.patientNumber();
        }
        return patient.fullName();
    }

    private String resolveBusinessReference(UUID tenantId, NotificationHistoryRecord primary, ReferenceData references) {
        if (primary == null || primary.sourceId() == null) {
            return null;
        }
        String sourceType = normalizeSourceType(primary.sourceType());
        UUID sourceId = primary.sourceId();
        return switch (sourceType) {
            case "APPOINTMENT" -> Optional.ofNullable(references.appointments().get(sourceId)).map(this::appointmentReference).orElse(null);
            case "BILL" -> Optional.ofNullable(references.bills().get(sourceId)).map(BillRecord::billNumber).orElse(null);
            case "RECEIPT" -> Optional.ofNullable(references.receipts().get(sourceId)).map(ReceiptRecord::receiptNumber).orElse(null);
            case "PAYMENT" -> Optional.ofNullable(references.payments().get(sourceId))
                    .map(payment -> StringUtils.hasText(payment.receiptNumber()) ? payment.receiptNumber() : payment.referenceNumber())
                    .orElse(null);
            case "REFUND" -> Optional.ofNullable(references.bills().get(sourceId)).map(BillRecord::billNumber).orElse(null);
            case "PRESCRIPTION" -> Optional.ofNullable(references.prescriptions().get(sourceId)).map(PrescriptionRecord::prescriptionNumber).orElse(null);
            case "LAB", "LAB_ORDER" -> Optional.ofNullable(references.labOrders().get(sourceId)).map(this::labReference).orElse(null);
            case "VACCINATION" -> Optional.ofNullable(references.vaccinations().get(sourceId)).map(this::vaccinationReference).orElse(null);
            default -> primary.sourceType();
        };
    }

    private String appointmentReference(AppointmentRecord appointment) {
        if (appointment == null) {
            return null;
        }
        if (StringUtils.hasText(appointment.displayReference())) {
            return appointment.displayReference();
        }
        if (appointment.tokenNumber() != null) {
            return "Token " + appointment.tokenNumber();
        }
        if (appointment.appointmentDate() != null) {
            return appointment.appointmentDate().format(DateTimeFormatter.ISO_DATE);
        }
        return "Appointment";
    }

    private String labReference(LabOrderRecord order) {
        if (order == null) {
            return null;
        }
        if (StringUtils.hasText(order.orderNumber())) {
            return order.orderNumber();
        }
        if (StringUtils.hasText(order.sampleAccessionNumber())) {
            return order.sampleAccessionNumber();
        }
        return "Lab order";
    }

    private String vaccinationReference(PatientVaccinationRecord vaccination) {
        if (vaccination == null) {
            return null;
        }
        if (StringUtils.hasText(vaccination.vaccineName()) && vaccination.doseNumber() != null) {
            return vaccination.vaccineName() + " Dose " + vaccination.doseNumber();
        }
        if (StringUtils.hasText(vaccination.vaccineName())) {
            return vaccination.vaccineName();
        }
        return "Vaccination";
    }

    private String resolveSourceModule(NotificationHistoryRecord primary) {
        if (primary == null || !StringUtils.hasText(primary.sourceType())) {
            return "Notification";
        }
        return primary.sourceType().trim().toUpperCase(Locale.ROOT);
    }

    private String resolveCategory(String eventType) {
        if (eventType == null) {
            return "Notification";
        }
        if (eventType.startsWith("APPOINTMENT")) return "Appointments";
        if (eventType.startsWith("BILL") || eventType.startsWith("PAYMENT") || eventType.startsWith("RECEIPT") || eventType.startsWith("REFUND")) return "Billing";
        if (eventType.startsWith("PRESCRIPTION")) return "Clinical";
        if (eventType.startsWith("LAB")) return "Laboratory";
        if (eventType.startsWith("FOLLOW_UP")) return "Clinical";
        if (eventType.startsWith("VACCINATION")) return "Vaccination";
        return "System";
    }

    private String humanizeEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return "-";
        }
        String[] tokens = eventType.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                builder.append(token.substring(1));
            }
        }
        return builder.length() == 0 ? eventType : builder.toString();
    }

    private String summarizeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "-";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() <= 180 ? compact : compact.substring(0, 177) + "...";
    }

    private List<EnrichedGroup> applyFilters(List<EnrichedGroup> groups, NotificationOperationsQuery query) {
        return groups.stream()
                .filter(group -> matchesText(group.row().patientName(), query.patientName()))
                .filter(group -> matchesText(group.row().patientReference(), query.patientReference()))
                .filter(group -> matchesText(group.row().businessReference(), query.businessReference()))
                .filter(group -> matchesText(group.row().sourceModule(), query.sourceModule()))
                .filter(group -> matchesText(group.row().eventLabel(), query.search())
                        || matchesText(group.row().patientName(), query.search())
                        || matchesText(group.row().patientReference(), query.search())
                        || matchesText(group.row().businessReference(), query.search())
                        || matchesText(group.row().messagePreview(), query.search()))
                .filter(group -> filterProvider(group, query.provider()))
                .filter(group -> filterStatus(group, query.channelStatus()))
                .filter(group -> filterHasFailure(group, query.hasFailure()))
                .filter(group -> filterHasRetry(group, query.hasRetry()))
                .toList();
    }

    private boolean filterProvider(EnrichedGroup group, String provider) {
        if (!StringUtils.hasText(provider)) {
            return true;
        }
        String needle = provider.trim().toLowerCase(Locale.ROOT);
        return group.row().deliveries().stream().anyMatch(delivery -> providerLabel(delivery.channel()).toLowerCase(Locale.ROOT).contains(needle));
    }

    private boolean filterStatus(EnrichedGroup group, String channelStatus) {
        if (!StringUtils.hasText(channelStatus)) {
            return true;
        }
        String needle = channelStatus.trim().toLowerCase(Locale.ROOT);
        return group.row().deliveries().stream().anyMatch(delivery -> delivery.displayStatus().toLowerCase(Locale.ROOT).contains(needle) || normalizeStatus(delivery.status()).toLowerCase(Locale.ROOT).contains(needle));
    }

    private boolean filterHasFailure(EnrichedGroup group, Boolean hasFailure) {
        if (hasFailure == null) {
            return true;
        }
        return hasFailure == hasFailure(group);
    }

    private boolean filterHasRetry(EnrichedGroup group, Boolean hasRetry) {
        if (hasRetry == null) {
            return true;
        }
        return hasRetry == hasRetry(group);
    }

    private boolean hasFailure(EnrichedGroup group) {
        return group.group().deliveries().stream().anyMatch(this::isFailure);
    }

    private boolean hasRetry(EnrichedGroup group) {
        return group.group().deliveries().stream().anyMatch(record -> record.attemptCount() > 0);
    }

    private boolean hasFailure(List<NotificationHistoryRecord> deliveries) {
        return deliveries.stream().anyMatch(this::isFailure);
    }

    private boolean hasRetry(List<NotificationHistoryRecord> deliveries) {
        return deliveries.stream().anyMatch(record -> record.attemptCount() > 0);
    }

    private boolean isActionableFailure(EnrichedGroup group) {
        return group.group().deliveries().stream().anyMatch(record -> isFailure(record) && isRetryable(record));
    }

    private boolean isSuccess(NotificationHistoryRecord record) {
        String status = normalizeStatus(record.status());
        return "SENT".equals(status) || "DELIVERED".equals(status);
    }

    private boolean isFailure(NotificationHistoryRecord record) {
        return "FAILED".equals(normalizeStatus(record.status()));
    }

    private boolean isPending(NotificationHistoryRecord record) {
        String status = normalizeStatus(record.status());
        return "PENDING".equals(status) || "QUEUED".equals(status) || "RETRYING".equals(status);
    }

    private boolean isRetryable(NotificationHistoryRecord record) {
        if (!isFailure(record)) {
            return false;
        }
        String category = failureCategory(record.failureReason());
        return RETRYABLE_FAILURE_CATEGORIES.contains(category);
    }

    private String displayStatus(NotificationHistoryRecord record) {
        String status = normalizeStatus(record.status());
        if ("SENT".equals(status) || "DELIVERED".equals(status)) return "Sent";
        if ("PENDING".equals(status) || "QUEUED".equals(status) || "RETRYING".equals(status)) return "Pending";
        if ("FAILED".equals(status)) return "Failed";
        if ("SKIPPED".equals(status)) {
            String reason = normalizeReason(record.failureReason());
            return reason != null && reason.toLowerCase(Locale.ROOT).contains("disabled") ? "Disabled" : "Skipped";
        }
        return status;
    }

    private String failureCategory(String reason) {
        String normalized = normalizeReason(reason);
        if (normalized == null) {
            return "UNKNOWN";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("patient record unavailable") || lower.contains("recipient unavailable")) return "MISSING_RECIPIENT_DATA";
        if (lower.contains("patient email unavailable") || lower.contains("patient mobile unavailable") || lower.contains("invalid patient email") || lower.contains("invalid patient mobile")) return "MISSING_RECIPIENT_DATA";
        if (lower.contains("opted out") || lower.contains("consent")) return "CONSENT_PREFERENCE";
        if (lower.contains("disabled")) return "CONFIGURATION";
        if (lower.contains("not configured")) return "CONFIGURATION";
        if (lower.contains("rate limit") || lower.contains("429")) return "RATE_LIMITED";
        if (lower.contains("timeout") || lower.contains("temporar") || lower.contains("network")) return "TRANSIENT_NETWORK_FAILURE";
        if (lower.contains("stale") || lower.contains("no longer applicable") || lower.contains("already paid") || lower.contains("cancelled") || lower.contains("canceled") || lower.contains("expired")) return "STALE_NOTIFICATION";
        if (lower.contains("invalid template")) return "INVALID_TEMPLATE";
        if (lower.contains("provider unavailable")) return "PROVIDER_UNAVAILABLE";
        if (lower.contains("rejected") || lower.contains("bounce")) return "PROVIDER_REJECTED";
        return "UNKNOWN";
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String compact = reason.replaceAll("\\s+", " ").trim();
        String lower = compact.toLowerCase(Locale.ROOT);
        if (lower.contains("clinic.carepilot.messaging.sms.enabled=false")) {
            return "SMS notifications disabled";
        }
        if (lower.contains("clinic.carepilot.messaging.whatsapp.enabled=false")) {
            return "WhatsApp notifications disabled";
        }
        if (lower.contains("patient record unavailable") || lower.contains("no active recipient available")) {
            return "Patient record unavailable";
        }
        if (lower.contains("patient email unavailable")) {
            return "Patient email unavailable";
        }
        if (lower.contains("patient mobile unavailable")) {
            return "Patient mobile unavailable";
        }
        if (lower.contains("email provider not configured")) {
            return "Email provider not configured";
        }
        if (lower.contains("sms provider not configured")) {
            return "SMS provider not configured";
        }
        if (lower.contains("whatsapp provider not configured")) {
            return "WhatsApp provider not configured";
        }
        if (lower.contains("patient opted out")) {
            return "Patient opted out";
        }
        if (lower.contains("skipped:")) {
            return normalizeReason(compact.substring(compact.indexOf(':') + 1));
        }
        if (lower.contains("provider disabled")) {
            return normalizeReason(compact.substring(compact.indexOf(':') + 1));
        }
        return compact;
    }

    private Optional<String> failureReasonKey(String reason) {
        String normalized = normalizeReason(reason);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return "UNKNOWN";
        }
        return switch (channel.trim().toUpperCase(Locale.ROOT)) {
            case "IN_APP", "IN-APP" -> "IN_APP";
            case "EMAIL" -> "EMAIL";
            case "SMS" -> "SMS";
            case "WHATSAPP" -> "WHATSAPP";
            default -> channel.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String normalizeChannelFromIntegrationKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "UNKNOWN";
        }
        if (key.toLowerCase(Locale.ROOT).contains("messaging.in_app")) return "IN_APP";
        if (key.toLowerCase(Locale.ROOT).contains("messaging.email")) return "EMAIL";
        if (key.toLowerCase(Locale.ROOT).contains("messaging.sms")) return "SMS";
        if (key.toLowerCase(Locale.ROOT).contains("messaging.whatsapp")) return "WHATSAPP";
        return normalizeChannel(key);
    }

    private int channelOrder(String channel) {
        return switch (normalizeChannel(channel)) {
            case "IN_APP" -> 0;
            case "EMAIL" -> 1;
            case "SMS" -> 2;
            case "WHATSAPP" -> 3;
            default -> 9;
        };
    }

    private String channelLabel(String channel) {
        return switch (normalizeChannel(channel)) {
            case "IN_APP" -> "In-App";
            case "EMAIL" -> "Email";
            case "SMS" -> "SMS";
            case "WHATSAPP" -> "WhatsApp";
            default -> normalizeChannel(channel);
        };
    }

    private String providerLabel(String channel) {
        return switch (normalizeChannel(channel)) {
            case "IN_APP" -> "Internal";
            case "EMAIL" -> "Email provider";
            case "SMS" -> "SMS provider";
            case "WHATSAPP" -> "WhatsApp provider";
            default -> "Unknown";
        };
    }

    private String maskRecipient(String channel, String recipient) {
        if (!StringUtils.hasText(recipient)) {
            return "Not available";
        }
        if (recipient.contains("@")) {
            int at = recipient.indexOf('@');
            if (at > 1) {
                return recipient.charAt(0) + "***" + recipient.substring(at);
            }
        }
        String digits = recipient.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return "******" + digits.substring(digits.length() - 4);
        }
        return recipient;
    }

    private String mapReadiness(String status) {
        return switch (normalizeStatus(status)) {
            case "READY" -> "Ready";
            case "DISABLED" -> "Disabled";
            case "NOT_CONFIGURED" -> "Not configured";
            case "ERROR" -> "Degraded";
            default -> "Unknown";
        };
    }

    private boolean matchesText(String actual, String needle) {
        if (!StringUtils.hasText(needle)) {
            return true;
        }
        if (!StringUtils.hasText(actual)) {
            return false;
        }
        return actual.toLowerCase(Locale.ROOT).contains(needle.trim().toLowerCase(Locale.ROOT));
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isStaleReason(String reason) {
        String normalized = normalizeReason(reason);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return STALE_FAILURE_MARKERS.stream().anyMatch(lower::contains);
    }

    private String formatDay(OffsetDateTime value, ZoneId zone) {
        if (value == null) {
            return "-";
        }
        return value.atZoneSameInstant(zone).toLocalDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
    }

    private NotificationOperationsAuditRow toAuditRowInternal(AuditEventRecord record, String tenantName) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("entityType", record.entityType());
        metadata.put("entityId", record.entityId() == null ? null : record.entityId().toString());
        return new NotificationOperationsAuditRow(
                record.id(),
                record.occurredAt(),
                record.actorAppUserId() == null ? "System" : record.actorAppUserId().toString(),
                tenantName,
                record.action(),
                record.entityId() == null ? null : record.entityId().toString(),
                record.entityType(),
                record.summary(),
                null,
                null,
                record.summary(),
                "Recorded",
                record.detailsJson(),
                metadata
        );
    }

    private NotificationOperationsAuditRow toAuditRow(AuditEventRecord record) {
        String tenantName = tenantManagementService.get(record.tenantId()).name();
        return toAuditRowInternal(record, tenantName);
    }

    private NotificationOperationsAuditRow toAuditRow(AuditEventRecord record, UUID tenantId) {
        String tenantName = tenantManagementService.get(tenantId).name();
        return toAuditRowInternal(record, tenantName);
    }

    private String normalizeSourceType(String value) {
        if (!StringUtils.hasText(value)) {
            return "UNKNOWN";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private ResolvedWindow resolveWindow(UUID tenantId, NotificationOperationsQuery query) {
        ZoneId zone = clinicTimeZoneResolver.resolve(tenantId);
        String period = query.period() == null ? "LAST_7_DAYS" : query.period().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        OffsetDateTime now = OffsetDateTime.now(zone);
        OffsetDateTime from = query.from();
        OffsetDateTime to = query.to();
        if (!"CUSTOM".equals(period) || from == null || to == null) {
            switch (period) {
                case "TODAY" -> {
                    LocalDate today = now.atZoneSameInstant(zone).toLocalDate();
                    from = today.atStartOfDay(zone).toOffsetDateTime();
                    to = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
                }
                case "LAST_30_DAYS" -> {
                    LocalDate toDate = now.atZoneSameInstant(zone).toLocalDate();
                    from = toDate.minusDays(30).atStartOfDay(zone).toOffsetDateTime();
                    to = toDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
                }
                default -> {
                    LocalDate toDate = now.atZoneSameInstant(zone).toLocalDate();
                    from = toDate.minusDays(7).atStartOfDay(zone).toOffsetDateTime();
                    to = toDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
                }
            }
        }
        PlatformTenantRecord tenant = tenantManagementService.get(tenantId);
        return new ResolvedWindow(tenantId, tenant.name(), zone, from, to, period.replace('_', ' '));
    }

    private int normalizeSize(int size) {
        return size <= 0 ? 25 : Math.min(size, 100);
    }

    private record ResolvedWindow(UUID tenantId, String tenantName, ZoneId zone, OffsetDateTime from, OffsetDateTime to, String periodLabel) {
    }

    private record ReferenceData(
            Map<UUID, PatientRecord> patients,
            Map<UUID, AppointmentRecord> appointments,
            Map<UUID, BillRecord> bills,
            Map<UUID, PaymentRecord> payments,
            Map<UUID, ReceiptRecord> receipts,
            Map<UUID, PrescriptionRecord> prescriptions,
            Map<UUID, LabOrderRecord> labOrders,
            Map<UUID, PatientVaccinationRecord> vaccinations
    ) {
    }

    private record EnrichedGroup(
            NotificationHistoryGroupRecord group,
            String patientName,
            String patientReference,
            String businessReference,
            String sourceModule,
            boolean hasFailure,
            boolean hasRetry,
            NotificationOperationsDeliveryRow row
    ) {
    }
}
