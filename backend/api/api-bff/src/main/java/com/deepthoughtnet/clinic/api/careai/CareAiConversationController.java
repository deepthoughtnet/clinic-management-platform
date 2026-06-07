package com.deepthoughtnet.clinic.api.careai;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiMessageEntity;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/careai/conversations")
public class CareAiConversationController {
    private final CareAiConversationPersistenceService conversationPersistenceService;

    public CareAiConversationController(CareAiConversationPersistenceService conversationPersistenceService) {
        this.conversationPersistenceService = conversationPersistenceService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST')")
    public List<CareAiConversationResponse> list() {
        return conversationPersistenceService.listConversations(RequestContextHolder.requireTenantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST')")
    public CareAiConversationResponse get(@PathVariable UUID id) {
        return toResponse(conversationPersistenceService.getConversation(RequestContextHolder.requireTenantId(), id));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST')")
    public List<CareAiMessageResponse> messages(@PathVariable UUID id) {
        return conversationPersistenceService.listMessages(RequestContextHolder.requireTenantId(), id).stream()
                .map(this::toResponse)
                .toList();
    }

    private CareAiConversationResponse toResponse(CareAiConversationEntity entity) {
        return new CareAiConversationResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getChannel(),
                entity.getPatientId(),
                entity.getLeadId(),
                entity.getAppointmentId(),
                entity.getStatus(),
                entity.getCurrentWorkflowId(),
                entity.getExternalSessionId(),
                entity.getSummary(),
                entity.getMetadataJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCompletedAt()
        );
    }

    private CareAiMessageResponse toResponse(CareAiMessageEntity entity) {
        return new CareAiMessageResponse(
                entity.getId(),
                entity.getConversationId(),
                entity.getSpeaker(),
                entity.getChannel(),
                entity.getContent(),
                entity.getIntent(),
                entity.getEntitiesJson(),
                entity.getMetadataJson(),
                entity.getCreatedAt()
        );
    }

    public record CareAiConversationResponse(
            UUID id,
            UUID tenantId,
            String channel,
            UUID patientId,
            UUID leadId,
            UUID appointmentId,
            String status,
            UUID currentWorkflowId,
            String externalSessionId,
            String summary,
            String metadataJson,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime completedAt
    ) {
    }

    public record CareAiMessageResponse(
            UUID id,
            UUID conversationId,
            String speaker,
            String channel,
            String content,
            String intent,
            String entitiesJson,
            String metadataJson,
            OffsetDateTime createdAt
    ) {
    }
}
