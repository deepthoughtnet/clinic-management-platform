package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementCohortType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.PatientEngagementProfileRecord;
import com.deepthoughtnet.clinic.carepilot.engagement.service.PatientEngagementService;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionCreateCommand;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Queues manual campaign executions using existing scheduler-driven execution infrastructure.
 */
@Service
public class CarePilotCampaignTriggerService {
    private final CampaignRepository campaignRepository;
    private final CampaignTemplateRepository templateRepository;
    private final CampaignExecutionRepository executionRepository;
    private final CampaignExecutionService executionService;
    private final PatientRepository patientRepository;
    private final PatientEngagementService engagementService;

    public CarePilotCampaignTriggerService(
            CampaignRepository campaignRepository,
            CampaignTemplateRepository templateRepository,
            CampaignExecutionRepository executionRepository,
            CampaignExecutionService executionService,
            PatientRepository patientRepository,
            PatientEngagementService engagementService
    ) {
        this.campaignRepository = campaignRepository;
        this.templateRepository = templateRepository;
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.patientRepository = patientRepository;
        this.engagementService = engagementService;
    }

    @Transactional
    public CampaignTriggerResult trigger(UUID tenantId, UUID campaignId) {
        CampaignEntity campaign = campaignRepository.findByTenantIdAndId(tenantId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        if (!campaign.isActive()) {
            throw new IllegalArgumentException("Only active campaigns can be triggered");
        }
        if (campaign.getTemplateId() == null) {
            throw new IllegalArgumentException("Campaign template is required before triggering");
        }

        CampaignTemplateEntity template = templateRepository.findByTenantIdAndId(tenantId, campaign.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Campaign template not found"));
        if (!template.isActive()) {
            throw new IllegalArgumentException("Campaign template must be active before triggering");
        }

        List<PatientEntity> audience = resolveAudience(tenantId, campaign.getAudienceType());
        OffsetDateTime queuedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime dedupeTo = queuedAt.plusMinutes(1);

        int eligible = 0;
        int queued = 0;
        int skipped = 0;
        for (PatientEntity patient : audience) {
            if (!hasChannelAddress(patient, template.getChannelType())) {
                skipped += 1;
                continue;
            }
            eligible += 1;
            if (executionRepository.existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
                    tenantId,
                    campaign.getId(),
                    patient.getId(),
                    template.getChannelType(),
                    queuedAt,
                    dedupeTo
            )) {
                skipped += 1;
                continue;
            }

            executionService.create(tenantId, new CampaignExecutionCreateCommand(
                    campaign.getId(),
                    template.getId(),
                    template.getChannelType(),
                    patient.getId(),
                    queuedAt,
                    "CAMPAIGN_MANUAL_TRIGGER",
                    null,
                    null,
                    queuedAt
            ));
            queued += 1;
        }

        return new CampaignTriggerResult(
                campaign.getId(),
                campaign.getName(),
                campaign.getAudienceType(),
                template.getId(),
                template.getChannelType(),
                queued > 0,
                eligible,
                queued,
                skipped,
                queued == 0
                        ? "No eligible recipients found. Executions were not queued."
                        : "Campaign execution queued. Delivery will be processed by the scheduler.",
                queuedAt
        );
    }

    private List<PatientEntity> resolveAudience(UUID tenantId, AudienceType audienceType) {
        return switch (audienceType) {
            case ALL_PATIENTS -> patientRepository.findByTenantIdAndActiveTrue(tenantId);
            case HIGH_RISK_PATIENTS,
                 INACTIVE_PATIENTS,
                 REFILL_RISK_PATIENTS,
                 FOLLOW_UP_OVERDUE_PATIENTS -> mapProfilesToPatients(tenantId, engagementCohort(audienceType));
            case SPECIFIC_PATIENTS, TAG_BASED, RULE_BASED ->
                    throw new IllegalArgumentException("Manual trigger is not supported for audience type " + audienceType);
        };
    }

    private List<PatientEntity> mapProfilesToPatients(UUID tenantId, EngagementCohortType cohort) {
        List<PatientEngagementProfileRecord> profiles = engagementService.cohort(tenantId, cohort, 0, 10_000);
        if (profiles.isEmpty()) {
            return List.of();
        }
        Map<UUID, PatientEntity> patientById = new LinkedHashMap<>();
        for (PatientEntity patient : patientRepository.findByTenantIdAndIdIn(
                tenantId,
                profiles.stream().map(PatientEngagementProfileRecord::patientId).toList()
        )) {
            patientById.put(patient.getId(), patient);
        }
        return profiles.stream()
                .map(PatientEngagementProfileRecord::patientId)
                .map(patientById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private EngagementCohortType engagementCohort(AudienceType audienceType) {
        return switch (audienceType) {
            case HIGH_RISK_PATIENTS -> EngagementCohortType.HIGH_RISK_PATIENTS;
            case INACTIVE_PATIENTS -> EngagementCohortType.INACTIVE_PATIENTS;
            case REFILL_RISK_PATIENTS -> EngagementCohortType.REFILL_RISK_PATIENTS;
            case FOLLOW_UP_OVERDUE_PATIENTS -> EngagementCohortType.FOLLOW_UP_OVERDUE_PATIENTS;
            default -> throw new IllegalArgumentException("Unsupported engagement audience type " + audienceType);
        };
    }

    private boolean hasChannelAddress(PatientEntity patient, ChannelType channelType) {
        if (patient == null || !patient.isActive()) {
            return false;
        }
        return switch (channelType) {
            case EMAIL -> StringUtils.hasText(patient.getEmail());
            case SMS, WHATSAPP -> StringUtils.hasText(patient.getMobile());
            case IN_APP, APP_NOTIFICATION -> true;
        };
    }

    public record CampaignTriggerResult(
            UUID campaignId,
            String campaignName,
            AudienceType audienceType,
            UUID templateId,
            ChannelType channelType,
            boolean queued,
            int eligibleRecipients,
            int queuedExecutions,
            int skippedRecipients,
            String message,
            OffsetDateTime queuedAt
    ) {}
}
