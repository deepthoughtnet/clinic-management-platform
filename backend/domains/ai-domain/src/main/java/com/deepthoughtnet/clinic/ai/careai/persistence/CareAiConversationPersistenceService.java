package com.deepthoughtnet.clinic.ai.careai.persistence;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationRepository;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiJsonSupport;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiMessageEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiMessageRepository;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiSessionBindingEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiSessionBindingRepository;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CareAiConversationPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(CareAiConversationPersistenceService.class);
    private static final Set<CareAiConversationStatus> ACTIVE_STATUSES = Set.of(CareAiConversationStatus.ACTIVE);

    private final CareAiConversationRepository conversationRepository;
    private final CareAiMessageRepository messageRepository;
    private final CareAiSessionBindingRepository sessionBindingRepository;
    private final CareAiWorkflowLifecycleService workflowLifecycleService;

    public CareAiConversationPersistenceService(
            CareAiConversationRepository conversationRepository,
            CareAiMessageRepository messageRepository,
            CareAiSessionBindingRepository sessionBindingRepository,
            CareAiWorkflowLifecycleService workflowLifecycleService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.sessionBindingRepository = sessionBindingRepository;
        this.workflowLifecycleService = workflowLifecycleService;
    }

    public void safeRecordTurn(CareAiConversationTurnCommand command) {
        log.info("careai.persistence.record-turn.start tenantId={} channel={} externalSessionId={} patientId={} workflowType={}",
                command == null ? null : command.tenantId(),
                command == null ? null : command.channel(),
                command == null ? null : command.externalSessionId(),
                command == null ? null : command.patientId(),
                command == null || command.workflowSnapshot() == null ? null : command.workflowSnapshot().workflowType());
        try {
            recordTurn(command);
            log.info("careai.persistence.record-turn.success tenantId={} channel={} externalSessionId={} patientId={}",
                    command == null ? null : command.tenantId(),
                    command == null ? null : command.channel(),
                    command == null ? null : command.externalSessionId(),
                    command == null ? null : command.patientId());
        } catch (RuntimeException ex) {
            log.warn("careai.persistence.record-turn.failed tenantId={} channel={} externalSessionId={} reason={}",
                    command == null ? null : command.tenantId(),
                    command == null ? null : command.channel(),
                    command == null ? null : command.externalSessionId(),
                    ex.getMessage(), ex);
        }
    }

    public void safeCloseConversation(
            UUID tenantId,
            CareAiChannel channel,
            UUID patientId,
            String externalSessionId,
            CareAiConversationStatus status,
            String summary
    ) {
        log.info("careai.persistence.close.start tenantId={} channel={} externalSessionId={} patientId={} status={}",
                tenantId, channel, externalSessionId, patientId, status);
        try {
            closeConversation(tenantId, channel, patientId, externalSessionId, status, summary);
            log.info("careai.persistence.close.success tenantId={} channel={} externalSessionId={} patientId={} status={}",
                    tenantId, channel, externalSessionId, patientId, status);
        } catch (RuntimeException ex) {
            log.warn("careai.persistence.close.failed tenantId={} channel={} externalSessionId={} reason={}",
                    tenantId, channel, externalSessionId, ex.getMessage(), ex);
        }
    }

    public CareAiConversationSessionSnapshot safeResumeSession(
            UUID tenantId,
            CareAiChannel channel,
            UUID patientId,
            String externalSessionId,
            CareAiTransport transport,
            String activeInstanceId,
            int messageLimit
    ) {
        try {
            return resumeSession(tenantId, channel, patientId, externalSessionId, transport, activeInstanceId, messageLimit);
        } catch (RuntimeException ex) {
            log.warn("careai.persistence.resume.failed tenantId={} channel={} externalSessionId={} reason={}",
                    tenantId, channel, externalSessionId, ex.getMessage(), ex);
            return null;
        }
    }

    public void safeMarkVoiceDisconnected(
            UUID tenantId,
            UUID patientId,
            String externalSessionId,
            String activeInstanceId
    ) {
        try {
            markVoiceDisconnected(tenantId, patientId, externalSessionId, activeInstanceId);
        } catch (RuntimeException ex) {
            log.warn("careai.persistence.voice-disconnect.failed tenantId={} externalSessionId={} reason={}",
                    tenantId, externalSessionId, ex.getMessage(), ex);
        }
    }

    public void safeMarkVoiceRecoveryFailed(
            UUID tenantId,
            UUID patientId,
            String externalSessionId,
            String activeInstanceId,
            String reason
    ) {
        try {
            markVoiceRecoveryFailed(tenantId, patientId, externalSessionId, activeInstanceId, reason);
        } catch (RuntimeException ex) {
            log.warn("careai.persistence.voice-recovery-failed.failed tenantId={} externalSessionId={} reason={}",
                    tenantId, externalSessionId, ex.getMessage(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTurn(CareAiConversationTurnCommand command) {
        if (command == null || command.tenantId() == null || command.channel() == null) {
            log.info("careai.persistence.record-turn.skipped tenantId={} channel={} externalSessionId={} reason=missing-required-fields",
                    command == null ? null : command.tenantId(),
                    command == null ? null : command.channel(),
                    command == null ? null : command.externalSessionId());
            return;
        }
        CareAiConversationEntity conversation = resolveConversation(command);
        boolean channelSwitched = !command.channel().name().equals(conversation.getChannel());
        String previousChannel = conversation.getChannel();
        conversation.touch();
        if (command.patientId() != null) {
            conversation.setPatientId(command.patientId());
        }
        if (command.leadId() != null) {
            conversation.setLeadId(command.leadId());
        }
        if (StringUtils.hasText(command.externalSessionId())) {
            conversation.setExternalSessionId(command.externalSessionId());
        }
        if (StringUtils.hasText(command.conversationSummary())) {
            conversation.setSummary(command.conversationSummary());
        }
        if (command.workflowSnapshot() != null && command.workflowSnapshot().appointmentId() != null) {
            conversation.setAppointmentId(command.workflowSnapshot().appointmentId());
        }
        if (command.conversationStatus() != null) {
            conversation.applyStatus(command.conversationStatus().name());
        }
        conversation = conversationRepository.save(conversation);

        upsertSessionBinding(command, conversation);
        appendMessage(conversation, CareAiSpeaker.USER, command.userMessage(), command.userIntent(), command.userEntitiesJson(), command.userMetadataJson());
        appendMessage(conversation, CareAiSpeaker.ASSISTANT, command.assistantMessage(), null, "{}", command.assistantMetadataJson());

        CareAiWorkflowEntity workflow = workflowLifecycleService.applySnapshot(
                command.tenantId(),
                conversation,
                command.workflowSnapshot()
        );
        if (workflow != null) {
            conversation.setCurrentWorkflowId(workflow.getId());
            if (channelSwitched) {
                Map<String, Object> channelSwitchPayload = new LinkedHashMap<>();
                channelSwitchPayload.put("fromChannel", previousChannel);
                channelSwitchPayload.put("toChannel", command.channel().name());
                channelSwitchPayload.put("externalSessionId", command.externalSessionId());
                workflowLifecycleService.appendWorkflowEvent(
                        command.tenantId(),
                        workflow.getId(),
                        "CHANNEL_SWITCHED",
                        CareAiJsonSupport.writeObject(channelSwitchPayload)
                );
            }
            if (command.workflowSnapshot().state() == CareAiWorkflowState.COMPLETED) {
                conversation.applyStatus(CareAiConversationStatus.COMPLETED.name());
            } else if (command.workflowSnapshot().state() == CareAiWorkflowState.ESCALATED) {
                conversation.applyStatus(CareAiConversationStatus.ESCALATED.name());
            }
        }
        conversationRepository.save(conversation);
        log.info("careai.persistence.record-turn.saved tenantId={} conversationId={} workflowId={} status={}",
                conversation.getTenantId(),
                conversation.getId(),
                conversation.getCurrentWorkflowId(),
                conversation.getStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeConversation(
            UUID tenantId,
            CareAiChannel channel,
            UUID patientId,
            String externalSessionId,
            CareAiConversationStatus status,
            String summary
    ) {
        if (tenantId == null || channel == null || status == null) {
            return;
        }
        Optional<CareAiConversationEntity> match = findActiveConversation(tenantId, channel, patientId, externalSessionId);
        if (match.isEmpty()) {
            return;
        }
        CareAiConversationEntity conversation = match.get();
        if (StringUtils.hasText(summary)) {
            conversation.setSummary(summary);
        }
        conversation.applyStatus(status.name());
        conversationRepository.save(conversation);
        if (StringUtils.hasText(externalSessionId)) {
            sessionBindingRepository.findByTenantIdAndExternalSessionIdAndActiveTrue(tenantId, externalSessionId)
                    .ifPresent(binding -> binding.deactivate());
        }
    }

    @Transactional(readOnly = true)
    public List<CareAiConversationEntity> listConversations(UUID tenantId) {
        return conversationRepository.findTop200ByTenantIdOrderByUpdatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<CareAiConversationEntity> listActiveConversations(UUID tenantId) {
        return conversationRepository.findTop200ByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .filter(conversation -> CareAiConversationStatus.ACTIVE.name().equals(conversation.getStatus())
                        || CareAiConversationStatus.ESCALATED.name().equals(conversation.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CareAiConversationEntity getConversation(UUID tenantId, UUID conversationId) {
        return conversationRepository.findByTenantIdAndId(tenantId, conversationId)
                .orElseThrow(() -> new IllegalArgumentException("AIVA conversation not found"));
    }

    @Transactional(readOnly = true)
    public List<CareAiMessageEntity> listMessages(UUID tenantId, UUID conversationId) {
        getConversation(tenantId, conversationId);
        return messageRepository.findByTenantIdAndConversationIdOrderByCreatedAtAsc(tenantId, conversationId);
    }

    @Transactional(readOnly = true)
    public CareAiConversationSessionSnapshot getConversationSessionSnapshot(UUID tenantId, UUID conversationId, int messageLimit) {
        if (tenantId == null || conversationId == null) {
            return null;
        }
        return conversationRepository.findByTenantIdAndId(tenantId, conversationId)
                .map(conversation -> buildSnapshot(tenantId, conversation, messageLimit))
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendConversationMessage(
            UUID tenantId,
            UUID conversationId,
            CareAiSpeaker speaker,
            CareAiChannel channel,
            String content,
            String metadataJson
    ) {
        if (tenantId == null || conversationId == null || speaker == null || channel == null || !StringUtils.hasText(content)) {
            return;
        }
        CareAiConversationEntity conversation = getConversation(tenantId, conversationId);
        appendMessage(conversation, speaker, content, null, "{}", metadataJson);
        conversation.touch();
        conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public CareAiConversationSessionSnapshot findLatestSessionSnapshot(
            UUID tenantId,
            CareAiChannel channel,
            UUID patientId,
            String externalSessionId,
            int messageLimit
    ) {
        if (tenantId == null || channel == null) {
            return null;
        }
        Optional<CareAiConversationEntity> conversation = findActiveConversation(tenantId, channel, patientId, externalSessionId);
        if (conversation.isEmpty()) {
            return null;
        }
        return buildSnapshot(tenantId, conversation.get(), messageLimit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CareAiConversationSessionSnapshot resumeSession(
            UUID tenantId,
            CareAiChannel channel,
            UUID patientId,
            String externalSessionId,
            CareAiTransport transport,
            String activeInstanceId,
            int messageLimit
    ) {
        if (tenantId == null || channel == null || !StringUtils.hasText(externalSessionId)) {
            return null;
        }
        Optional<CareAiConversationEntity> conversation = findActiveConversation(tenantId, channel, patientId, externalSessionId);
        if (conversation.isEmpty()) {
            return null;
        }
        CareAiConversationEntity resolvedConversation = conversation.get();
        if (transport != null) {
            touchSessionBinding(tenantId, resolvedConversation.getId(), transport.name(), externalSessionId, activeInstanceId);
        }
        CareAiConversationSessionSnapshot snapshot = buildSnapshot(tenantId, resolvedConversation, messageLimit);
        if (snapshot.workflow() != null) {
            workflowLifecycleService.appendWorkflowEvent(
                    tenantId,
                    snapshot.workflow().getId(),
                    "VOICE_RECOVERED",
                    sessionEventPayloadJson(externalSessionId, activeInstanceId, transport == null ? null : transport.name(), "VOICE_RECOVERED")
            );
        }
        return snapshot;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markVoiceDisconnected(
            UUID tenantId,
            UUID patientId,
            String externalSessionId,
            String activeInstanceId
    ) {
        if (tenantId == null || !StringUtils.hasText(externalSessionId)) {
            return;
        }
        Optional<CareAiConversationEntity> conversation = findActiveConversation(
                tenantId,
                CareAiChannel.PATIENT_PORTAL_VOICE,
                patientId,
                externalSessionId
        );
        if (conversation.isEmpty()) {
            return;
        }
        sessionBindingRepository.findByTenantIdAndExternalSessionIdAndActiveTrue(tenantId, externalSessionId)
                .ifPresent(binding -> binding.markSeen(activeInstanceId, OffsetDateTime.now()));
        CareAiWorkflowEntity workflow = workflowLifecycleService.findLatestWorkflow(tenantId, conversation.get().getId());
        if (workflow != null) {
            workflowLifecycleService.appendWorkflowEvent(
                    tenantId,
                    workflow.getId(),
                    "VOICE_DISCONNECTED",
                    sessionEventPayloadJson(externalSessionId, activeInstanceId, CareAiTransport.WEBSOCKET_PATIENT_PORTAL.name(), "VOICE_DISCONNECTED")
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markVoiceRecoveryFailed(
            UUID tenantId,
            UUID patientId,
            String externalSessionId,
            String activeInstanceId,
            String reason
    ) {
        if (tenantId == null) {
            return;
        }
        Optional<CareAiConversationEntity> conversation = findActiveConversation(
                tenantId,
                CareAiChannel.PATIENT_PORTAL_VOICE,
                patientId,
                externalSessionId
        );
        if (conversation.isEmpty()) {
            return;
        }
        CareAiWorkflowEntity workflow = workflowLifecycleService.findLatestWorkflow(tenantId, conversation.get().getId());
        if (workflow != null) {
            workflowLifecycleService.appendWorkflowEvent(
                    tenantId,
                    workflow.getId(),
                    "VOICE_RECOVERY_FAILED",
                    sessionEventPayloadJson(externalSessionId, activeInstanceId, CareAiTransport.WEBSOCKET_PATIENT_PORTAL.name(), reason)
            );
        }
    }

    private CareAiConversationEntity resolveConversation(CareAiConversationTurnCommand command) {
        return findActiveConversation(command.tenantId(), command.channel(), command.patientId(), command.externalSessionId())
                .orElseGet(() -> CareAiConversationEntity.create(
                        command.tenantId(),
                        command.channel().name(),
                        command.patientId(),
                        command.leadId(),
                        command.externalSessionId()
                ));
    }

    private Optional<CareAiConversationEntity> findActiveConversation(
            UUID tenantId,
            CareAiChannel channel,
            UUID patientId,
            String externalSessionId
    ) {
        if (StringUtils.hasText(externalSessionId)) {
            Optional<CareAiConversationEntity> byBinding = sessionBindingRepository
                    .findByTenantIdAndExternalSessionIdAndActiveTrue(tenantId, externalSessionId)
                    .flatMap(binding -> conversationRepository.findByTenantIdAndId(tenantId, binding.getConversationId()))
                    .filter(conversation -> ACTIVE_STATUSES.stream().map(Enum::name).toList().contains(conversation.getStatus()));
            if (byBinding.isPresent()) {
                return byBinding;
            }
            Optional<CareAiConversationEntity> bySession = conversationRepository
                    .findTopByTenantIdAndChannelAndExternalSessionIdAndStatusInOrderByUpdatedAtDesc(
                            tenantId,
                            channel.name(),
                            externalSessionId,
                            ACTIVE_STATUSES.stream().map(Enum::name).toList()
                    );
            if (bySession.isPresent()) {
                return bySession;
            }
        }
        if (patientId != null) {
            return conversationRepository.findTopByTenantIdAndChannelAndPatientIdAndStatusInOrderByUpdatedAtDesc(
                    tenantId,
                    channel.name(),
                    patientId,
                    ACTIVE_STATUSES.stream().map(Enum::name).toList()
            );
        }
        if (patientId != null) {
            Optional<CareAiConversationEntity> crossChannel = conversationRepository
                    .findTopByTenantIdAndPatientIdAndStatusInOrderByUpdatedAtDesc(
                            tenantId,
                            patientId,
                            ACTIVE_STATUSES.stream().map(Enum::name).toList()
                    );
            if (crossChannel.isPresent()) {
                return crossChannel;
            }
        }
        return Optional.empty();
    }

    private void appendMessage(
            CareAiConversationEntity conversation,
            CareAiSpeaker speaker,
            String content,
            String intent,
            String entitiesJson,
            String metadataJson
    ) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        messageRepository.save(CareAiMessageEntity.create(
                conversation.getTenantId(),
                conversation.getId(),
                speaker.name(),
                conversation.getChannel(),
                content,
                intent,
                entitiesJson,
                metadataJson
        ));
    }

    private void upsertSessionBinding(CareAiConversationTurnCommand command, CareAiConversationEntity conversation) {
        if (!StringUtils.hasText(command.externalSessionId()) || command.transport() == null) {
            return;
        }
        touchSessionBinding(
                command.tenantId(),
                conversation.getId(),
                command.transport().name(),
                command.externalSessionId(),
                command.activeInstanceId()
        );
    }

    private void touchSessionBinding(
            UUID tenantId,
            UUID conversationId,
            String transport,
            String externalSessionId,
            String activeInstanceId
    ) {
        CareAiSessionBindingEntity binding = sessionBindingRepository
                .findByTenantIdAndExternalSessionIdAndActiveTrue(tenantId, externalSessionId)
                .orElseGet(() -> CareAiSessionBindingEntity.create(
                        tenantId,
                        conversationId,
                        transport,
                        externalSessionId,
                        activeInstanceId
                ));
        binding.bindConversation(conversationId);
        binding.markSeen(activeInstanceId, OffsetDateTime.now());
        sessionBindingRepository.save(binding);
    }

    private CareAiConversationSessionSnapshot buildSnapshot(UUID tenantId, CareAiConversationEntity conversation, int messageLimit) {
        CareAiWorkflowEntity workflow = workflowLifecycleService.findLatestWorkflow(tenantId, conversation.getId());
        List<CareAiMessageEntity> messages = messageRepository
                .findByTenantIdAndConversationIdOrderByCreatedAtAsc(tenantId, conversation.getId());
        if (messageLimit > 0 && messages.size() > messageLimit) {
            messages = messages.subList(messages.size() - messageLimit, messages.size());
        }
        return new CareAiConversationSessionSnapshot(
                conversation,
                workflow,
                workflow == null ? null : workflowLifecycleService.findActivePendingConfirmation(tenantId, workflow.getId()),
                messages
        );
    }

    private String sessionEventPayloadJson(
            String externalSessionId,
            String activeInstanceId,
            String transport,
            String detail
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalSessionId", externalSessionId);
        payload.put("activeInstanceId", activeInstanceId);
        payload.put("transport", transport);
        payload.put("detail", detail);
        return CareAiJsonSupport.writeObject(payload);
    }
}
