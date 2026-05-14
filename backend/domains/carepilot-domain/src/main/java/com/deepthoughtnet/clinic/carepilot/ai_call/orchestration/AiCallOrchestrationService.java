package com.deepthoughtnet.clinic.carepilot.ai_call.orchestration;

import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallCampaignEntity;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallCampaignRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallEventEntity;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallEventRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallTranscriptEntity;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallTranscriptRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallCampaignStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallEventRecord;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallEventType;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionRecord;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallTranscriptRecord;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.VoiceCallProviderRegistry;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallExecutionSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallManualCallCommand;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallTriggerCommand;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallRequest;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallResult;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Tenant-safe orchestration service for AI call queueing, dispatch, retries, failover, and event history.
 */
@Service
public class AiCallOrchestrationService {
    private final AiCallCampaignRepository campaignRepository;
    private final AiCallExecutionRepository executionRepository;
    private final AiCallTranscriptRepository transcriptRepository;
    private final AiCallEventRepository eventRepository;
    private final VoiceCallProviderRegistry providerRegistry;
    private final TenantNotificationSettingsService notificationSettingsService;

    private final int schedulerBatchSize;
    private final int retryMaxAttempts;
    private final int retryInitialBackoffSeconds;
    private final int retryMaxBackoffSeconds;
    private final int maxCallsPerTenantPerMinute;
    private final int maxConcurrentCallsPerTenant;
    private final int maxCallsPerPatientPerDay;

    public AiCallOrchestrationService(
            AiCallCampaignRepository campaignRepository,
            AiCallExecutionRepository executionRepository,
            AiCallTranscriptRepository transcriptRepository,
            AiCallEventRepository eventRepository,
            VoiceCallProviderRegistry providerRegistry,
            TenantNotificationSettingsService notificationSettingsService,
            @Value("${carepilot.ai-calls.scheduler.batch-size:25}") int schedulerBatchSize,
            @Value("${carepilot.ai-calls.retry.max-attempts:3}") int retryMaxAttempts,
            @Value("${carepilot.ai-calls.retry.initial-backoff-seconds:300}") int retryInitialBackoffSeconds,
            @Value("${carepilot.ai-calls.retry.max-backoff-seconds:3600}") int retryMaxBackoffSeconds,
            @Value("${carepilot.ai-calls.throttle.max-calls-per-tenant-per-minute:10}") int maxCallsPerTenantPerMinute,
            @Value("${carepilot.ai-calls.throttle.max-concurrent-calls-per-tenant:5}") int maxConcurrentCallsPerTenant,
            @Value("${carepilot.ai-calls.throttle.max-calls-per-patient-per-day:2}") int maxCallsPerPatientPerDay
    ) {
        this.campaignRepository = campaignRepository;
        this.executionRepository = executionRepository;
        this.transcriptRepository = transcriptRepository;
        this.eventRepository = eventRepository;
        this.providerRegistry = providerRegistry;
        this.notificationSettingsService = notificationSettingsService;
        this.schedulerBatchSize = Math.max(1, schedulerBatchSize);
        this.retryMaxAttempts = Math.max(1, retryMaxAttempts);
        this.retryInitialBackoffSeconds = Math.max(1, retryInitialBackoffSeconds);
        this.retryMaxBackoffSeconds = Math.max(retryInitialBackoffSeconds, retryMaxBackoffSeconds);
        this.maxCallsPerTenantPerMinute = Math.max(1, maxCallsPerTenantPerMinute);
        this.maxConcurrentCallsPerTenant = Math.max(1, maxConcurrentCallsPerTenant);
        this.maxCallsPerPatientPerDay = Math.max(1, maxCallsPerPatientPerDay);
    }

    public record DispatchBatchResult(int processed, int dispatched, int failed, int skipped) {}

    @Transactional(readOnly = true)
    public Page<AiCallExecutionRecord> search(UUID tenantId, AiCallExecutionSearchCriteria criteria, int page, int size) {
        CarePilotValidators.requireTenant(tenantId);
        AiCallExecutionSearchCriteria safe = criteria == null
                ? new AiCallExecutionSearchCriteria(null, null, null, null, null, null, null, null, null)
                : criteria;
        return executionRepository.findAll(
                spec(tenantId, safe),
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 200)), Sort.by(Sort.Direction.DESC, "scheduledAt"))
        ).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public AiCallExecutionRecord get(UUID tenantId, UUID executionId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(executionId, "executionId");
        return toRecord(executionRepository.findByTenantIdAndId(tenantId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("AI call execution not found")));
    }

    @Transactional(readOnly = true)
    public AiCallTranscriptRecord transcript(UUID tenantId, UUID executionId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(executionId, "executionId");
        return transcriptRepository.findByTenantIdAndExecutionId(tenantId, executionId)
                .map(this::toTranscriptRecord)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AiCallEventRecord> events(UUID tenantId, UUID executionId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(executionId, "executionId");
        return eventRepository.findTop100ByTenantIdAndExecutionIdOrderByCreatedAtDesc(tenantId, executionId)
                .stream()
                .map(this::toEventRecord)
                .toList();
    }

    @Transactional
    public List<AiCallExecutionRecord> triggerCampaign(UUID tenantId, UUID campaignId, List<AiCallTriggerCommand> targets) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(campaignId, "campaignId");
        AiCallCampaignEntity campaign = campaignRepository.findByTenantIdAndId(tenantId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("AI call campaign not found"));
        if (campaign.getStatus() != AiCallCampaignStatus.ACTIVE && campaign.getStatus() != AiCallCampaignStatus.DRAFT) {
            throw new IllegalArgumentException("Campaign is not triggerable in status " + campaign.getStatus());
        }
        List<AiCallExecutionRecord> results = new ArrayList<>();
        for (AiCallTriggerCommand target : targets == null ? List.<AiCallTriggerCommand>of() : targets) {
            AiCallExecutionEntity row = AiCallExecutionEntity.create(
                    tenantId,
                    campaignId,
                    target.patientId(),
                    target.leadId(),
                    requirePhone(target.phoneNumber()),
                    target.scheduledAt()
            );
            row.setExecutionStatus(AiCallExecutionStatus.QUEUED);
            row = executionRepository.save(row);
            recordEvent(row, AiCallEventType.QUEUED, null, "queued for dispatch", row.getExecutionStatus());
            results.add(toRecord(row));
        }
        return results;
    }

    @Transactional
    public AiCallExecutionRecord triggerManual(UUID tenantId, AiCallManualCallCommand command) {
        CarePilotValidators.requireTenant(tenantId);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        AiCallCampaignEntity campaign = campaignRepository.findByTenantId(tenantId).stream()
                .filter(c -> c.getCallType() == command.callType() && c.getStatus() != AiCallCampaignStatus.CANCELLED)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No campaign available for call type " + command.callType()));
        AiCallExecutionEntity row = AiCallExecutionEntity.create(
                tenantId,
                campaign.getId(),
                command.patientId(),
                command.leadId(),
                requirePhone(command.phoneNumber()),
                command.scheduledAt()
        );
        row.setExecutionStatus(AiCallExecutionStatus.QUEUED);
        row = executionRepository.save(row);
        recordEvent(row, AiCallEventType.QUEUED, null, "manual queued", row.getExecutionStatus());
        return toRecord(row);
    }

    @Transactional
    public DispatchBatchResult dispatchDueExecutions(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        var settings = notificationSettingsService.getOrCreate(tenantId);
        OffsetDateTime now = OffsetDateTime.now();
        List<AiCallExecutionEntity> due = executionRepository.findByTenantIdAndExecutionStatusInAndScheduledAtLessThanEqual(
                tenantId,
                List.of(AiCallExecutionStatus.PENDING, AiCallExecutionStatus.QUEUED),
                now
        );
        int processed = 0;
        int dispatched = 0;
        int failed = 0;
        int skipped = 0;

        long inProgress = executionRepository.countByTenantIdAndStatuses(tenantId, List.of(AiCallExecutionStatus.DIALING, AiCallExecutionStatus.IN_PROGRESS));
        long minuteVolume = eventRepository.countByTenantIdAndCreatedAtGreaterThanEqual(tenantId, now.minusMinutes(1));

        for (AiCallExecutionEntity execution : due) {
            if (processed >= schedulerBatchSize) {
                break;
            }
            processed++;

            if (execution.getExecutionStatus() == AiCallExecutionStatus.CANCELLED
                    || execution.getExecutionStatus() == AiCallExecutionStatus.SUPPRESSED
                    || execution.getExecutionStatus() == AiCallExecutionStatus.COMPLETED) {
                skipped++;
                continue;
            }

            if (inProgress >= maxConcurrentCallsPerTenant || minuteVolume >= maxCallsPerTenantPerMinute) {
                skipped += skipExecution(execution, AiCallExecutionStatus.SKIPPED, "THROTTLED");
                continue;
            }

            if (execution.getPatientId() != null
                    && executionRepository.countByTenantIdAndPatientIdAndCreatedAtGreaterThanEqual(tenantId, execution.getPatientId(), now.minusDays(1)) >= maxCallsPerPatientPerDay) {
                skipped += skipExecution(execution, AiCallExecutionStatus.SUPPRESSED, "PATIENT_DAILY_LIMIT");
                continue;
            }

            OffsetDateTime quietAdjusted = notificationSettingsService.applyQuietHours(settings, execution.getScheduledAt());
            if (!quietAdjusted.equals(execution.getScheduledAt())) {
                execution.setScheduledAt(quietAdjusted);
                execution.touch();
                executionRepository.save(execution);
                skipped += skipExecution(execution, AiCallExecutionStatus.SKIPPED, "QUIET_HOURS_RESCHEDULED");
                continue;
            }

            boolean ok = dispatchSingle(execution);
            if (ok) {
                dispatched++;
            } else {
                failed++;
            }
            minuteVolume++;
        }

        return new DispatchBatchResult(processed, dispatched, failed, skipped);
    }

    @Transactional
    public int reconcileStaleExecutions(UUID tenantId, Duration staleAfter) {
        CarePilotValidators.requireTenant(tenantId);
        OffsetDateTime staleBefore = OffsetDateTime.now().minus(staleAfter == null ? Duration.ofMinutes(30) : staleAfter);
        int handled = 0;
        var stale = executionRepository.findByTenantIdAndExecutionStatusInAndLastAttemptAtLessThanEqual(
                tenantId,
                List.of(AiCallExecutionStatus.DIALING, AiCallExecutionStatus.IN_PROGRESS),
                staleBefore
        );
        for (AiCallExecutionEntity row : stale) {
            row.setExecutionStatus(AiCallExecutionStatus.FAILED);
            row.setFailureReason("STALE_RECONCILIATION_TIMEOUT");
            scheduleRetryOrEscalate(row, "STALE_RECONCILIATION");
            row.touch();
            executionRepository.save(row);
            recordEvent(row, AiCallEventType.FAILED, "STALE_TIMEOUT", "reconciled stale execution", row.getExecutionStatus());
            handled++;
        }
        return handled;
    }

    @Transactional
    public AiCallExecutionRecord retry(UUID tenantId, UUID executionId) {
        AiCallExecutionEntity row = requireExecution(tenantId, executionId);
        row.setExecutionStatus(AiCallExecutionStatus.QUEUED);
        row.setFailureReason(null);
        row.setEscalationRequired(false);
        row.setEscalationReason(null);
        row.setSuppressionReason(null);
        row.setNextRetryAt(null);
        row.touch();
        row = executionRepository.save(row);
        recordEvent(row, AiCallEventType.RETRY_SCHEDULED, null, "manual retry", row.getExecutionStatus());
        return toRecord(row);
    }

    @Transactional
    public AiCallExecutionRecord cancel(UUID tenantId, UUID executionId, String reason) {
        AiCallExecutionEntity row = requireExecution(tenantId, executionId);
        row.setExecutionStatus(AiCallExecutionStatus.CANCELLED);
        row.setSuppressionReason(safeReason(reason, "CANCELLED_BY_OPERATOR"));
        row.touch();
        row = executionRepository.save(row);
        recordEvent(row, AiCallEventType.CANCELLED, null, row.getSuppressionReason(), row.getExecutionStatus());
        return toRecord(row);
    }

    @Transactional
    public AiCallExecutionRecord suppress(UUID tenantId, UUID executionId, String reason) {
        AiCallExecutionEntity row = requireExecution(tenantId, executionId);
        row.setExecutionStatus(AiCallExecutionStatus.SUPPRESSED);
        row.setSuppressionReason(safeReason(reason, "SUPPRESSED_BY_OPERATOR"));
        row.touch();
        row = executionRepository.save(row);
        recordEvent(row, AiCallEventType.SUPPRESSED, null, row.getSuppressionReason(), row.getExecutionStatus());
        return toRecord(row);
    }

    @Transactional
    public AiCallExecutionRecord reschedule(UUID tenantId, UUID executionId, OffsetDateTime scheduledAt, String reason) {
        AiCallExecutionEntity row = requireExecution(tenantId, executionId);
        if (scheduledAt == null) {
            throw new IllegalArgumentException("scheduledAt is required");
        }
        row.setScheduledAt(scheduledAt);
        row.setExecutionStatus(AiCallExecutionStatus.QUEUED);
        row.setSuppressionReason(safeReason(reason, "RESCHEDULED"));
        row.touch();
        row = executionRepository.save(row);
        recordEvent(row, AiCallEventType.RETRY_SCHEDULED, null, row.getSuppressionReason(), row.getExecutionStatus());
        return toRecord(row);
    }

    @Transactional
    public void ingestWebhook(UUID tenantId, String provider, Map<String, Object> payload) {
        CarePilotValidators.requireTenant(tenantId);
        String providerCallId = payload == null ? null : stringVal(payload.get("providerCallId"));
        if (!StringUtils.hasText(providerCallId)) {
            eventRepository.save(AiCallEventEntity.create(tenantId, null, provider, null, AiCallEventType.FAILED, "UNKNOWN_CALL", null, OffsetDateTime.now(), redact(payload)));
            return;
        }
        AiCallExecutionEntity execution = executionRepository.findByTenantIdAndProviderCallId(tenantId, providerCallId).orElse(null);
        if (execution == null) {
            eventRepository.save(AiCallEventEntity.create(tenantId, null, provider, providerCallId, AiCallEventType.FAILED, "UNKNOWN_CALL", null, OffsetDateTime.now(), redact(payload)));
            return;
        }

        String external = stringVal(payload.get("status"));
        AiCallExecutionStatus mapped = mapWebhookStatus(external);
        if (mapped != null) {
            execution.setExecutionStatus(mapped);
        }
        execution.setProviderName(provider);
        execution.setLastAttemptAt(OffsetDateTime.now());

        if (payload != null && payload.containsKey("transcript")) {
            upsertTranscript(execution, payload);
            recordEvent(execution, AiCallEventType.TRANSCRIPT_RECEIVED, external, "transcript received", execution.getExecutionStatus());
        }

        execution.touch();
        executionRepository.save(execution);
        recordEvent(execution, toEventType(execution.getExecutionStatus()), external, "webhook update", execution.getExecutionStatus());
    }

    private AiCallExecutionEntity requireExecution(UUID tenantId, UUID executionId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(executionId, "executionId");
        return executionRepository.findByTenantIdAndId(tenantId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("AI call execution not found"));
    }

    private boolean dispatchSingle(AiCallExecutionEntity execution) {
        VoiceCallProvider primary = providerRegistry.resolvePrimary();
        if (primary == null) {
            execution.setExecutionStatus(AiCallExecutionStatus.FAILED);
            execution.setFailureReason("NO_PROVIDER_CONFIGURED");
            execution.setLastAttemptAt(OffsetDateTime.now());
            execution.touch();
            executionRepository.save(execution);
            recordEvent(execution, AiCallEventType.FAILED, null, "no voice provider configured", execution.getExecutionStatus());
            return false;
        }
        execution.setExecutionStatus(AiCallExecutionStatus.DIALING);
        execution.setProviderName(primary.providerName());
        execution.setLastAttemptAt(OffsetDateTime.now());
        execution.touch();
        executionRepository.save(execution);
        recordEvent(execution, AiCallEventType.DISPATCHED, null, "dispatch start", execution.getExecutionStatus());

        VoiceCallResult result = primary.placeCall(buildRequest(execution));
        if (result == null) {
            execution.setExecutionStatus(AiCallExecutionStatus.FAILED);
            execution.setFailureReason("PROVIDER_NULL_RESULT");
            execution.setLastAttemptAt(OffsetDateTime.now());
            execution.touch();
            executionRepository.save(execution);
            recordEvent(execution, AiCallEventType.FAILED, null, "provider returned null result", execution.getExecutionStatus());
            return false;
        }
        if (!isProviderFailureRetryable(result) || !providerRegistry.failoverEnabled()) {
            applyResult(execution, result, false, null);
            return execution.getExecutionStatus() == AiCallExecutionStatus.COMPLETED;
        }

        VoiceCallProvider fallback = providerRegistry.resolveFallback();
        if (fallback == null || java.util.Objects.equals(fallback.providerName(), primary.providerName())) {
            applyResult(execution, result, false, null);
            return false;
        }

        execution.setFailoverAttempted(true);
        execution.setFailoverReason("PRIMARY_RETRYABLE_FAILURE");
        recordEvent(execution, AiCallEventType.FAILOVER_ATTEMPTED, null, primary.providerName() + " -> " + fallback.providerName(), execution.getExecutionStatus());
        VoiceCallResult fallbackResult = fallback.placeCall(buildRequest(execution));
        applyResult(execution, fallbackResult, true, primary.providerName());
        return execution.getExecutionStatus() == AiCallExecutionStatus.COMPLETED;
    }

    private VoiceCallRequest buildRequest(AiCallExecutionEntity execution) {
        return new VoiceCallRequest(
                execution.getTenantId(),
                execution.getCampaignId(),
                execution.getId(),
                execution.getPhoneNumber(),
                null,
                execution.getScheduledAt(),
                Map.of()
        );
    }

    private void applyResult(AiCallExecutionEntity execution, VoiceCallResult result, boolean fromFailover, String primaryProvider) {
        execution.setProviderCallId(result.providerCallId());
        execution.setProviderName(result.providerName());
        execution.setStartedAt(result.startedAt());
        execution.setEndedAt(result.endedAt());
        execution.setLastAttemptAt(OffsetDateTime.now());

        if (result.status() == VoiceCallStatus.COMPLETED) {
            execution.setExecutionStatus(AiCallExecutionStatus.COMPLETED);
            execution.setFailureReason(null);
        } else if (result.status() == VoiceCallStatus.NO_ANSWER) {
            execution.setExecutionStatus(AiCallExecutionStatus.NO_ANSWER);
            execution.setFailureReason(result.failureReason());
            scheduleRetryOrEscalate(execution, "NO_ANSWER");
        } else if (result.status() == VoiceCallStatus.BUSY) {
            execution.setExecutionStatus(AiCallExecutionStatus.BUSY);
            execution.setFailureReason(result.failureReason());
            scheduleRetryOrEscalate(execution, "BUSY");
        } else if (result.status() == VoiceCallStatus.CANCELLED) {
            execution.setExecutionStatus(AiCallExecutionStatus.CANCELLED);
            execution.setFailureReason(result.failureReason());
        } else {
            execution.setExecutionStatus(AiCallExecutionStatus.FAILED);
            execution.setFailureReason(result.failureReason());
            scheduleRetryOrEscalate(execution, "PROVIDER_ERROR");
        }

        if (fromFailover) {
            execution.setFailoverAttempted(true);
            execution.setFailoverReason("Fallback provider used after failure from " + primaryProvider);
        }

        if (result.transcript() != null) {
            AiCallTranscriptEntity transcript = transcriptRepository.findByTenantIdAndExecutionId(execution.getTenantId(), execution.getId())
                    .orElseGet(() -> AiCallTranscriptEntity.create(
                            execution.getTenantId(),
                            execution.getId(),
                            result.transcript().text(),
                            result.transcript().summary(),
                            result.transcript().sentiment(),
                            result.transcript().outcome(),
                            null,
                            result.transcript().requiresFollowUp()
                    ));
            transcript.enrich(
                    result.transcript().summary(),
                    result.transcript().sentiment(),
                    result.transcript().outcome(),
                    null,
                    result.transcript().requiresFollowUp(),
                    result.transcript().requiresFollowUp() ? "FOLLOW_UP_REQUIRED" : null,
                    null
            );
            transcript = transcriptRepository.save(transcript);
            execution.setTranscriptId(transcript.getId());
            if (transcript.isRequiresFollowUp()) {
                execution.setEscalationRequired(true);
                execution.setEscalationReason("Transcript requires follow-up");
            }
        }

        execution.touch();
        executionRepository.save(execution);
        recordEvent(execution, toEventType(execution.getExecutionStatus()), null, execution.getFailureReason(), execution.getExecutionStatus());
    }

    private void scheduleRetryOrEscalate(AiCallExecutionEntity execution, String reason) {
        int nextRetry = execution.getRetryCount() + 1;
        execution.setRetryCount(nextRetry);
        if (nextRetry >= retryMaxAttempts) {
            execution.setExecutionStatus(AiCallExecutionStatus.ESCALATED);
            execution.setEscalationRequired(true);
            execution.setEscalationReason("Max retry reached: " + reason);
            execution.setNextRetryAt(null);
            return;
        }
        long backoff = Math.min((long) retryInitialBackoffSeconds * (1L << (nextRetry - 1)), retryMaxBackoffSeconds);
        execution.setNextRetryAt(OffsetDateTime.now().plusSeconds(backoff));
        execution.setScheduledAt(execution.getNextRetryAt());
        execution.setExecutionStatus(AiCallExecutionStatus.QUEUED);
        recordEvent(execution, AiCallEventType.RETRY_SCHEDULED, null, "retry in " + backoff + "s", execution.getExecutionStatus());
    }

    private int skipExecution(AiCallExecutionEntity execution, AiCallExecutionStatus status, String reason) {
        execution.setExecutionStatus(status);
        execution.setSuppressionReason(reason);
        execution.touch();
        executionRepository.save(execution);
        recordEvent(execution, status == AiCallExecutionStatus.SUPPRESSED ? AiCallEventType.SUPPRESSED : AiCallEventType.SKIPPED, null, reason, status);
        return 1;
    }

    private void recordEvent(AiCallExecutionEntity execution, AiCallEventType eventType, String externalStatus, String detail, AiCallExecutionStatus internalStatus) {
        eventRepository.save(AiCallEventEntity.create(
                execution.getTenantId(),
                execution.getId(),
                execution.getProviderName(),
                execution.getProviderCallId(),
                eventType,
                externalStatus,
                internalStatus,
                OffsetDateTime.now(),
                detail
        ));
    }

    private void upsertTranscript(AiCallExecutionEntity execution, Map<String, Object> payload) {
        String transcriptText = stringVal(payload.get("transcript"));
        String summary = stringVal(payload.get("summary"));
        String sentiment = stringVal(payload.get("sentiment"));
        String outcome = stringVal(payload.get("outcome"));
        String intent = stringVal(payload.get("intent"));
        boolean followUpRequired = boolVal(payload.get("followUpRequired"));
        String escalationReason = stringVal(payload.get("escalationReason"));
        String entities = stringVal(payload.get("extractedEntitiesJson"));

        AiCallTranscriptEntity transcript = transcriptRepository.findByTenantIdAndExecutionId(execution.getTenantId(), execution.getId())
                .orElseGet(() -> AiCallTranscriptEntity.create(
                        execution.getTenantId(),
                        execution.getId(),
                        transcriptText,
                        summary,
                        sentiment,
                        outcome,
                        intent,
                        followUpRequired
                ));
        transcript.enrich(summary, sentiment, outcome, intent, followUpRequired, escalationReason, entities);
        transcript = transcriptRepository.save(transcript);
        execution.setTranscriptId(transcript.getId());
        if (followUpRequired) {
            execution.setEscalationRequired(true);
            execution.setEscalationReason(escalationReason == null ? "Transcript follow-up required" : escalationReason);
        }
    }

    private AiCallExecutionStatus mapWebhookStatus(String externalStatus) {
        if (!StringUtils.hasText(externalStatus)) {
            return null;
        }
        return switch (externalStatus.trim().toUpperCase()) {
            case "RINGING" -> AiCallExecutionStatus.DIALING;
            case "ANSWERED", "IN_PROGRESS" -> AiCallExecutionStatus.IN_PROGRESS;
            case "COMPLETED" -> AiCallExecutionStatus.COMPLETED;
            case "NO_ANSWER" -> AiCallExecutionStatus.NO_ANSWER;
            case "BUSY" -> AiCallExecutionStatus.BUSY;
            case "CANCELLED" -> AiCallExecutionStatus.CANCELLED;
            case "FAILED", "ERROR" -> AiCallExecutionStatus.FAILED;
            default -> null;
        };
    }

    private AiCallEventType toEventType(AiCallExecutionStatus status) {
        return switch (status) {
            case QUEUED, PENDING -> AiCallEventType.QUEUED;
            case DIALING -> AiCallEventType.RINGING;
            case IN_PROGRESS -> AiCallEventType.ANSWERED;
            case COMPLETED -> AiCallEventType.COMPLETED;
            case FAILED -> AiCallEventType.FAILED;
            case NO_ANSWER -> AiCallEventType.NO_ANSWER;
            case BUSY -> AiCallEventType.BUSY;
            case CANCELLED -> AiCallEventType.CANCELLED;
            case ESCALATED -> AiCallEventType.ESCALATED;
            case SKIPPED -> AiCallEventType.SKIPPED;
            case SUPPRESSED -> AiCallEventType.SUPPRESSED;
        };
    }

    private boolean isProviderFailureRetryable(VoiceCallResult result) {
        if (result == null) {
            return true;
        }
        if (result.status() == VoiceCallStatus.NO_ANSWER || result.status() == VoiceCallStatus.BUSY) {
            return true;
        }
        String reason = result.failureReason() == null ? "" : result.failureReason().toUpperCase();
        return reason.contains("TIMEOUT") || reason.contains("TEMPORARY") || reason.contains("PROVIDER");
    }

    private String requirePhone(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            throw new IllegalArgumentException("phoneNumber is required");
        }
        return phoneNumber.trim();
    }

    private String safeReason(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean boolVal(Object value) {
        return value instanceof Boolean b ? b : value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String redact(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return payload.toString().replaceAll("(?i)(token|secret|apiKey)=([^,} ]+)", "$1=***");
        } catch (Exception ex) {
            return "payload_redaction_failed";
        }
    }

    private Specification<AiCallExecutionEntity> spec(UUID tenantId, AiCallExecutionSearchCriteria criteria) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("executionStatus"), criteria.status()));
            }
            if (criteria.patientId() != null) {
                predicates.add(cb.equal(root.get("patientId"), criteria.patientId()));
            }
            if (criteria.leadId() != null) {
                predicates.add(cb.equal(root.get("leadId"), criteria.leadId()));
            }
            if (criteria.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), criteria.startDate().atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            if (criteria.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), criteria.endDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1)));
            }
            if (criteria.escalationRequired() != null) {
                predicates.add(cb.equal(root.get("escalationRequired"), criteria.escalationRequired()));
            }
            if (StringUtils.hasText(criteria.provider())) {
                predicates.add(cb.equal(cb.lower(root.get("providerName")), criteria.provider().trim().toLowerCase()));
            }
            if (criteria.campaignId() != null) {
                predicates.add(cb.equal(root.get("campaignId"), criteria.campaignId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AiCallExecutionRecord toRecord(AiCallExecutionEntity row) {
        return new AiCallExecutionRecord(
                row.getId(), row.getTenantId(), row.getCampaignId(), row.getPatientId(), row.getLeadId(), row.getPhoneNumber(), row.getExecutionStatus(),
                row.getProviderName(), row.getProviderCallId(), row.getScheduledAt(), row.getStartedAt(), row.getEndedAt(), row.getRetryCount(),
                row.getNextRetryAt(), row.getLastAttemptAt(), row.getFailureReason(), row.getSuppressionReason(), row.isEscalationRequired(),
                row.getEscalationReason(), row.isFailoverAttempted(), row.getFailoverReason(), row.getTranscriptId(), row.getCreatedAt(), row.getUpdatedAt()
        );
    }

    private AiCallTranscriptRecord toTranscriptRecord(AiCallTranscriptEntity row) {
        return new AiCallTranscriptRecord(
                row.getId(), row.getTenantId(), row.getExecutionId(), row.getTranscriptText(), row.getSummary(), row.getSentiment(), row.getOutcome(),
                row.getIntent(), row.isRequiresFollowUp(), row.getEscalationReason(), row.getExtractedEntitiesJson(), row.getCreatedAt()
        );
    }

    private AiCallEventRecord toEventRecord(AiCallEventEntity row) {
        return new AiCallEventRecord(
                row.getId(), row.getTenantId(), row.getExecutionId(), row.getProviderName(), row.getProviderCallId(), row.getEventType(),
                row.getExternalStatus(), row.getInternalStatus(), row.getEventTimestamp(), row.getRawPayloadRedacted(), row.getCreatedAt()
        );
    }
}
