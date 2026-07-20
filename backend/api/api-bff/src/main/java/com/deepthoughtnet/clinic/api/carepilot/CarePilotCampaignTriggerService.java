package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementCohortType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.PatientEngagementProfileRecord;
import com.deepthoughtnet.clinic.carepilot.engagement.service.PatientEngagementService;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionCreateCommand;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Queues manual campaign executions using existing scheduler-driven execution infrastructure.
 */
@Service
public class CarePilotCampaignTriggerService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9][0-9()\\-\\s]{6,19}$");

    private final CampaignRepository campaignRepository;
    private final CampaignTemplateRepository templateRepository;
    private final CampaignExecutionRepository executionRepository;
    private final CampaignExecutionService executionService;
    private final PatientRepository patientRepository;
    private final PatientEngagementService engagementService;
    private final TenantNotificationSettingsService notificationSettingsService;
    private final CarePilotMessagingStatusService messagingStatusService;
    private final Environment environment;
    private final boolean manualExecutionDispatcherEnabled;

    public CarePilotCampaignTriggerService(
            CampaignRepository campaignRepository,
            CampaignTemplateRepository templateRepository,
            CampaignExecutionRepository executionRepository,
            CampaignExecutionService executionService,
            PatientRepository patientRepository,
            PatientEngagementService engagementService,
            TenantNotificationSettingsService notificationSettingsService,
            CarePilotMessagingStatusService messagingStatusService,
            Environment environment,
            @Value("${clinic.carepilot.scheduler.enabled:false}") boolean manualExecutionDispatcherEnabled
    ) {
        this.campaignRepository = campaignRepository;
        this.templateRepository = templateRepository;
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.patientRepository = patientRepository;
        this.engagementService = engagementService;
        this.notificationSettingsService = notificationSettingsService;
        this.messagingStatusService = messagingStatusService;
        this.environment = environment;
        this.manualExecutionDispatcherEnabled = manualExecutionDispatcherEnabled;
    }

    @Transactional
    public CampaignTriggerPreviewResult preview(UUID tenantId, UUID campaignId) {
        CampaignEntity campaign = campaignRepository.findByTenantIdAndId(tenantId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        if (campaign.getTriggerType() != com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType.MANUAL) {
            return buildPreview(tenantId, campaign, null, manualExecutionDispatcherEnabled, false, List.of("Manual run is available only for MANUAL campaigns."));
        }
        CampaignTemplateEntity template = campaign.getTemplateId() == null ? null : templateRepository.findByTenantIdAndId(tenantId, campaign.getTemplateId()).orElse(null);
        List<String> manualBlockingReasons = manualExecutionDispatcherEnabled
                ? List.of()
                : List.of("Manual execution dispatcher is disabled.");
        return buildPreview(tenantId, campaign, template, manualExecutionDispatcherEnabled, manualExecutionDispatcherEnabled, manualBlockingReasons);
    }

    @Transactional
    public CampaignTriggerResult trigger(UUID tenantId, UUID campaignId) {
        CampaignTriggerPreviewResult preview = preview(tenantId, campaignId);
        if (!preview.canTrigger()) {
            throw new IllegalArgumentException(String.join(" ", preview.blockingReasons()));
        }

        CampaignEntity campaign = campaignRepository.findByTenantIdAndId(tenantId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        CampaignTemplateEntity template = templateRepository.findByTenantIdAndId(tenantId, campaign.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Campaign template not found"));

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
                campaign.getCampaignReference(),
                buildExecutionReference(campaign.getCampaignReference(), queuedAt),
                campaign.getName(),
                campaign.getAudienceType(),
                template.getChannelType(),
                campaign.getStatus(),
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

    private CampaignTriggerPreviewResult buildPreview(
            UUID tenantId,
            CampaignEntity campaign,
            CampaignTemplateEntity template,
            boolean manualDispatcherEnabled,
            boolean manualAllowed,
            List<String> manualBlockingReasons
    ) {
        NotificationSettingsRecord settings = notificationSettingsService.findByTenantId(tenantId).orElse(null);
        var providerStatuses = messagingStatusService.providerStatuses();
        var providerStatus = providerStatuses.stream()
                .filter(row -> row.channel() == toMessageChannel(template == null ? inferChannelForCampaign(campaign) : template.getChannelType()))
                .findFirst()
                .orElse(null);

        ChannelType channelType = template == null ? inferChannelForCampaign(campaign) : template.getChannelType();
        List<PatientEntity> audience = resolveAudience(tenantId, campaign.getAudienceType());
        int missingEmailOrPhone = 0;
        int invalidDestination = 0;
        int inactivePatient = 0;
        int consentOrOptOut = 0;
        int duplicateRecipient = 0;
        int missingRequiredTemplateData = 0;
        int eligible = 0;
        OffsetDateTime queuedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        for (PatientEntity patient : patientRepository.findByTenantIdAndIdIn(
                tenantId,
                audience.stream().map(PatientEntity::getId).toList()
        )) {
            if (patient == null || !patient.isActive()) {
                inactivePatient += 1;
                continue;
            }
            String destination = destinationFor(patient, channelType);
            if (!StringUtils.hasText(destination)) {
                missingEmailOrPhone += 1;
                continue;
            }
            if (!isValidDestination(channelType, destination)) {
                invalidDestination += 1;
                continue;
            }
            if (settings != null && settings.requirePatientConsent() && isMarketingLikeCampaign(campaign.getCampaignType())) {
                consentOrOptOut += 1;
                continue;
            }
            if (template == null || !template.isActive() || !StringUtils.hasText(template.getBodyTemplate())) {
                missingRequiredTemplateData += 1;
                continue;
            }
            if (executionRepository.existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
                    tenantId, campaign.getId(), patient.getId(), channelType, queuedAt, queuedAt.plusMinutes(1)
            )) {
                duplicateRecipient += 1;
                continue;
            }
            eligible += 1;
        }

        boolean approvedConfigurationValid = campaign.getApprovedConfigurationHash() != null
                && campaign.getApprovedConfigurationHash().equals(configurationHash(campaign));
        boolean providerReady = providerStatus == null
                || (providerStatus.status() == com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus.READY
                && providerStatus.configured()
                && providerStatus.available());
        boolean templateReady = template != null && template.isActive();
        boolean campaignReady = campaign.getStatus() == CampaignStatus.ACTIVE && campaign.getTriggerType() == com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType.MANUAL;
        boolean canTrigger = manualAllowed
                && campaignReady
                && providerReady
                && templateReady
                && approvedConfigurationValid
                && eligible > 0
                && manualBlockingReasons.isEmpty();

        List<String> blockingReasons = new ArrayList<>();
        if (!campaignReady) {
            blockingReasons.add("Run campaign is available only for active manual campaigns.");
        }
        if (!providerReady) {
            blockingReasons.add("Provider is not ready.");
        }
        if (!templateReady) {
            blockingReasons.add("Template must be active before running the campaign.");
        }
        if (!approvedConfigurationValid) {
            blockingReasons.add("Approved configuration no longer matches the campaign.");
        }
        if (eligible == 0) {
            blockingReasons.add("No eligible recipients are available.");
        }
        blockingReasons.addAll(manualBlockingReasons);

        String providerName = providerStatus == null ? (template == null ? "Unknown provider" : template.getChannelType().name()) : providerStatus.providerName();
        String providerMode = providerName != null && providerName.toLowerCase().contains("mock") ? "Mock/Test" : "Live";
        String environmentWarning = isLocalLikeEnvironment() ? "DEMO/UAT environment" : null;
        return new CampaignTriggerPreviewResult(
                campaign.getCampaignReference(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getTriggerType(),
                channelType.name(),
                template == null ? null : template.getName(),
                templateReady,
                providerName,
                providerMode,
                providerReady,
                manualDispatcherEnabled,
                eligible,
                missingEmailOrPhone + invalidDestination + consentOrOptOut + duplicateRecipient + inactivePatient + missingRequiredTemplateData,
                missingEmailOrPhone,
                invalidDestination,
                consentOrOptOut,
                duplicateRecipient,
                inactivePatient,
                missingRequiredTemplateData,
                eligible,
                null,
                environmentWarning,
                approvedConfigurationValid,
                canTrigger,
                blockingReasons
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

    private String destinationFor(PatientEntity patient, ChannelType channelType) {
        return switch (channelType) {
            case EMAIL -> patient.getEmail();
            case SMS, WHATSAPP -> patient.getMobile();
            case IN_APP, APP_NOTIFICATION -> patient.getPatientNumber();
        };
    }

    private boolean isValidDestination(ChannelType channelType, String destination) {
        if (!StringUtils.hasText(destination)) {
            return false;
        }
        return switch (channelType) {
            case EMAIL -> EMAIL_PATTERN.matcher(destination.trim()).matches();
            case SMS, WHATSAPP -> PHONE_PATTERN.matcher(destination.trim()).matches();
            case IN_APP, APP_NOTIFICATION -> true;
        };
    }

    private boolean isMarketingLikeCampaign(com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType campaignType) {
        return campaignType != com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType.APPOINTMENT_REMINDER;
    }

    private boolean isLocalLikeEnvironment() {
        for (String profile : environment.getActiveProfiles()) {
            String normalized = profile == null ? "" : profile.toLowerCase();
            if (normalized.contains("local") || normalized.contains("dev") || normalized.contains("test") || normalized.contains("uat")) {
                return true;
            }
        }
        return false;
    }

    private MessageChannel toMessageChannel(ChannelType channelType) {
        if (channelType == null) {
            return MessageChannel.EMAIL;
        }
        return switch (channelType) {
            case EMAIL -> MessageChannel.EMAIL;
            case SMS -> MessageChannel.SMS;
            case WHATSAPP -> MessageChannel.WHATSAPP;
            case IN_APP, APP_NOTIFICATION -> MessageChannel.EMAIL;
        };
    }

    private ChannelType inferChannelForCampaign(CampaignEntity campaign) {
        return campaign.getTemplateId() == null ? ChannelType.EMAIL : templateRepository.findByTenantIdAndId(campaign.getTenantId(), campaign.getTemplateId())
                .map(CampaignTemplateEntity::getChannelType)
                .orElse(ChannelType.EMAIL);
    }

    private String buildExecutionReference(String campaignReference, OffsetDateTime queuedAt) {
        return "EXE-" + campaignReference + "-" + queuedAt.toString().replace(":", "").replace("-", "").replace(".", "");
    }

    private String configurationHash(CampaignEntity entity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            CampaignTemplateEntity template = entity.getTemplateId() == null ? null : templateRepository.findByTenantIdAndId(entity.getTenantId(), entity.getTemplateId()).orElse(null);
            String payload = String.join("|",
                    safe(entity.getName()),
                    safe(entity.getCampaignType() == null ? null : entity.getCampaignType().name()),
                    safe(entity.getTriggerType() == null ? null : entity.getTriggerType().name()),
                    safe(entity.getAudienceType() == null ? null : entity.getAudienceType().name()),
                    safe(entity.getTemplateId() == null ? null : entity.getTemplateId().toString()),
                    safe(template == null ? null : template.getName()),
                    safe(template == null || template.getChannelType() == null ? null : template.getChannelType().name()),
                    safe(template == null ? null : template.getSubjectLine()),
                    safe(template == null ? null : template.getBodyTemplate()),
                    safe(template == null ? null : Boolean.toString(template.isActive())),
                    safe(entity.getNotes())
            );
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hashed) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record CampaignTriggerResult(
            String campaignReference,
            String executionReference,
            String campaignName,
            AudienceType audienceType,
            ChannelType channelType,
            CampaignStatus status,
            boolean queued,
            int eligibleRecipients,
            int queuedExecutions,
            int skippedRecipients,
            String message,
            OffsetDateTime queuedAt
    ) {}

    public record CampaignTriggerPreviewResult(
            String campaignReference,
            String campaignName,
            CampaignStatus status,
            com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType triggerType,
            String channelType,
            String templateName,
            boolean templateActive,
            String providerName,
            String providerMode,
            boolean providerReady,
            boolean manualDispatcherEnabled,
            int eligibleRecipients,
            int excludedRecipients,
            int missingEmailOrPhoneCount,
            int invalidDestinationCount,
            int consentOrOptOutCount,
            int duplicateRecipientCount,
            int inactivePatientCount,
            int missingRequiredTemplateDataCount,
            int estimatedMessages,
            BigDecimal estimatedBillableCost,
            String environmentWarning,
            boolean approvedConfigurationValid,
            boolean canTrigger,
            List<String> blockingReasons
    ) {}
}
