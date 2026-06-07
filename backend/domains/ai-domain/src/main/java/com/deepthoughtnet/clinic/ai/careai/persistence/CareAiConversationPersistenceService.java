package com.deepthoughtnet.clinic.ai.careai.persistence;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationRepository;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiMessageEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiMessageRepository;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiSessionBindingEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiSessionBindingRepository;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import java.time.OffsetDateTime;
import java.util.List;
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
    public CareAiConversationEntity getConversation(UUID tenantId, UUID conversationId) {
        return conversationRepository.findByTenantIdAndId(tenantId, conversationId)
                .orElseThrow(() -> new IllegalArgumentException("CareAI conversation not found"));
    }

    @Transactional(readOnly = true)
    public List<CareAiMessageEntity> listMessages(UUID tenantId, UUID conversationId) {
        getConversation(tenantId, conversationId);
        return messageRepository.findByTenantIdAndConversationIdOrderByCreatedAtAsc(tenantId, conversationId);
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
        CareAiConversationEntity resolvedConversation = conversation.get();
        CareAiWorkflowEntity workflow = workflowLifecycleService.findLatestWorkflow(tenantId, resolvedConversation.getId());
        List<CareAiMessageEntity> messages = messageRepository
                .findByTenantIdAndConversationIdOrderByCreatedAtAsc(tenantId, resolvedConversation.getId());
        if (messageLimit > 0 && messages.size() > messageLimit) {
            messages = messages.subList(messages.size() - messageLimit, messages.size());
        }
        return new CareAiConversationSessionSnapshot(
                resolvedConversation,
                workflow,
                workflow == null ? null : workflowLifecycleService.findActivePendingConfirmation(tenantId, workflow.getId()),
                messages
        );
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
        CareAiSessionBindingEntity binding = sessionBindingRepository
                .findByTenantIdAndExternalSessionIdAndActiveTrue(command.tenantId(), command.externalSessionId())
                .orElseGet(() -> CareAiSessionBindingEntity.create(
                        command.tenantId(),
                        conversation.getId(),
                        command.transport().name(),
                        command.externalSessionId(),
                        command.activeInstanceId()
                ));
        binding.bindConversation(conversation.getId());
        binding.markSeen(command.activeInstanceId(), OffsetDateTime.now());
        sessionBindingRepository.save(binding);
    }
}
