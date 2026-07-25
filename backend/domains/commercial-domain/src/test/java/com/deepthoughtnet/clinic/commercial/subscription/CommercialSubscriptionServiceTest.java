package com.deepthoughtnet.clinic.commercial.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.PublicationStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TargetSegment;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TemplateStatus;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionRepository;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.CreateSubscriptionRequest;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.LifecycleActionRequest;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialSubscriptionEventRepository;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionEntity;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CommercialSubscriptionServiceTest {
    private CommercialTenantSubscriptionRepository subscriptionRepository;
    private CommercialSubscriptionEventRepository eventRepository;
    private CommercialPlanTemplateRepository templateRepository;
    private CommercialPlanVersionRepository versionRepository;
    private AuditEventPublisher auditEventPublisher;
    private CommercialSubscriptionService service;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        subscriptionRepository = org.mockito.Mockito.mock(CommercialTenantSubscriptionRepository.class);
        eventRepository = org.mockito.Mockito.mock(CommercialSubscriptionEventRepository.class);
        templateRepository = org.mockito.Mockito.mock(CommercialPlanTemplateRepository.class);
        versionRepository = org.mockito.Mockito.mock(CommercialPlanVersionRepository.class);
        auditEventPublisher = org.mockito.Mockito.mock(AuditEventPublisher.class);
        service = new CommercialSubscriptionService(subscriptionRepository, eventRepository, templateRepository, versionRepository, auditEventPublisher, new ObjectMapper().findAndRegisterModules());
        actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), actorId, "platform.admin@jeeva.test", Set.of("PLATFORM_ADMIN"), "PLATFORM_ADMIN", "commercial-subscription-test"));
        when(auditEventPublisher.record(any())).thenReturn(UUID.randomUUID());
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void createAssignmentRejectsUnpublishedVersion() {
        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = template(templateId, TemplateStatus.ACTIVE);
        CommercialPlanVersionEntity version = version(template, versionId, PublicationStatus.RETIRED, 1, "v1");

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(subscriptionRepository.findByTenantIdAndSubscriptionStatusOrderByStartDateAscCreatedAtAsc(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createAssignment(new CreateSubscriptionRequest(
                tenantId,
                versionId,
                LocalDate.now().plusDays(1),
                null,
                true,
                "Healthcare Core Subscription",
                "SUB-001",
                "Initial assignment"
        ))).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("published versions");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void createAssignmentCreatesScheduledSubscriptionForFutureStartDate() {
        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = template(templateId, TemplateStatus.ACTIVE);
        CommercialPlanVersionEntity version = version(template, versionId, PublicationStatus.PUBLISHED, 1, "v1");

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(subscriptionRepository.findByTenantIdAndSubscriptionStatusOrderByStartDateAscCreatedAtAsc(any(), any())).thenReturn(List.of());
        when(eventRepository.findBySubscription_IdOrderByPerformedAtDesc(any())).thenReturn(List.of());

        var response = service.createAssignment(new CreateSubscriptionRequest(
                tenantId,
                versionId,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(90),
                true,
                "Solo Clinic Subscription",
                "SUB-100",
                "Scheduled assignment"
        ));

        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.SCHEDULED);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.planTemplateId()).isEqualTo(templateId);
        assertThat(response.publishedVersionId()).isEqualTo(version.getId());
        verify(subscriptionRepository).save(any(CommercialTenantSubscriptionEntity.class));
        verify(eventRepository).save(any());
    }

    @Test
    void validateAssignmentBlocksOverlapWithActiveSubscription() {
        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID activeSubscriptionId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = template(templateId, TemplateStatus.ACTIVE);
        CommercialPlanVersionEntity version = version(template, versionId, PublicationStatus.PUBLISHED, 1, "v1");
        CommercialTenantSubscriptionEntity active = CommercialTenantSubscriptionEntity.create(
                activeSubscriptionId,
                tenantId,
                template,
                version,
                SubscriptionStatus.ACTIVE,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                true,
                "Current plan",
                "CUR-001",
                null,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(subscriptionRepository.findByTenantIdAndSubscriptionStatusOrderByStartDateAscCreatedAtAsc(tenantId, SubscriptionStatus.ACTIVE)).thenReturn(List.of(active));

        var validation = service.validateAssignment(new CreateSubscriptionRequest(
                tenantId,
                versionId,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(40),
                false,
                "Replacement plan",
                "NEW-001",
                null
        ));

        assertThat(validation.validationState()).isEqualTo(CommercialSubscriptionEnums.ValidationState.INVALID);
        assertThat(validation.readyToAssign()).isFalse();
        assertThat(validation.findings()).extracting("code").contains("ACTIVE_SUBSCRIPTION_OVERLAP");
    }

    @Test
    void pauseAndResumeTransitionActiveSubscription() {
        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        CommercialPlanTemplateEntity template = template(templateId, TemplateStatus.ACTIVE);
        CommercialPlanVersionEntity version = version(template, versionId, PublicationStatus.PUBLISHED, 1, "v1");
        CommercialTenantSubscriptionEntity subscription = CommercialTenantSubscriptionEntity.create(
                subscriptionId,
                tenantId,
                template,
                version,
                SubscriptionStatus.ACTIVE,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(30),
                true,
                "Current plan",
                "CUR-001",
                null,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(eventRepository.findBySubscription_IdOrderByPerformedAtDesc(subscriptionId)).thenReturn(List.of());

        var paused = service.pause(subscriptionId, new LifecycleActionRequest("Pausing for review"));
        assertThat(paused.subscriptionStatus()).isEqualTo(SubscriptionStatus.PAUSED);

        when(subscriptionRepository.findByTenantIdAndSubscriptionStatusOrderByStartDateAscCreatedAtAsc(tenantId, SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        var resumed = service.resume(subscriptionId, new LifecycleActionRequest("Resuming after review"));
        assertThat(resumed.subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void replaceSupersedesCurrentSubscription() {
        UUID tenantId = UUID.randomUUID();
        UUID currentTemplateId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();
        UUID replacementTemplateId = UUID.randomUUID();
        UUID replacementVersionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        CommercialPlanTemplateEntity currentTemplate = template(currentTemplateId, TemplateStatus.ACTIVE);
        CommercialPlanVersionEntity currentVersion = version(currentTemplate, currentVersionId, PublicationStatus.PUBLISHED, 1, "v1");
        CommercialTenantSubscriptionEntity current = CommercialTenantSubscriptionEntity.create(
                subscriptionId,
                tenantId,
                currentTemplate,
                currentVersion,
                SubscriptionStatus.ACTIVE,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(10),
                true,
                "Current plan",
                "CUR-001",
                null,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                actorId
        );
        CommercialPlanTemplateEntity replacementTemplate = template(replacementTemplateId, TemplateStatus.ACTIVE);
        CommercialPlanVersionEntity replacementVersion = version(replacementTemplate, replacementVersionId, PublicationStatus.PUBLISHED, 1, "v1");

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(current));
        when(templateRepository.findById(replacementTemplateId)).thenReturn(Optional.of(replacementTemplate));
        when(versionRepository.findById(replacementVersionId)).thenReturn(Optional.of(replacementVersion));
        when(subscriptionRepository.findByTenantIdAndSubscriptionStatusOrderByStartDateAscCreatedAtAsc(tenantId, SubscriptionStatus.ACTIVE)).thenReturn(List.of(current));
        when(eventRepository.findBySubscription_IdOrderByPerformedAtDesc(any())).thenReturn(List.of());

        var response = service.replace(subscriptionId, new com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.ReplaceSubscriptionRequest(
                replacementVersionId,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(60),
                false,
                "Replacement plan",
                "NEW-100",
                "Replacement"
        ));

        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.SCHEDULED);
        verify(subscriptionRepository, times(2)).save(any(CommercialTenantSubscriptionEntity.class));
    }

    private CommercialPlanTemplateEntity template(UUID id, TemplateStatus status) {
        return CommercialPlanTemplateEntity.create(id, "SOLO_CLINIC", "Solo Clinic", null, TargetSegment.SOLO, status, 0, OffsetDateTime.parse("2026-07-24T00:00:00Z"), actorId);
    }

    private CommercialPlanVersionEntity version(CommercialPlanTemplateEntity template, UUID versionId, PublicationStatus status, int versionNumber, String label) {
        return CommercialPlanVersionEntity.create(template, versionNumber, label, status, OffsetDateTime.parse("2026-07-24T00:00:00Z"), actorId, "notes", 1, "hash-" + versionNumber, "{\"templateId\":\"" + template.getId() + "\"}", 1, 1, 0, 0, 0, actorId);
    }
}
