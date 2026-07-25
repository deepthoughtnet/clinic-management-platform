package com.deepthoughtnet.clinic.commercial.subscription;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationSeverity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateRepository;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionRepository;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.ValidationState;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.CreateSubscriptionRequest;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.LifecycleActionRequest;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.PageResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.ReplaceSubscriptionRequest;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionDetailResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionHistoryResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionStatusCountsResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionSummaryResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.ValidationMessageResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.ValidationResultResponse;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialSubscriptionEventEntity;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialSubscriptionEventRepository;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionEntity;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventAction;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommercialSubscriptionService {
    private static final UUID PLATFORM_AUDIT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final CommercialTenantSubscriptionRepository subscriptionRepository;
    private final CommercialSubscriptionEventRepository eventRepository;
    private final CommercialPlanTemplateRepository planTemplateRepository;
    private final CommercialPlanVersionRepository planVersionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public CommercialSubscriptionService(
            CommercialTenantSubscriptionRepository subscriptionRepository,
            CommercialSubscriptionEventRepository eventRepository,
            CommercialPlanTemplateRepository planTemplateRepository,
            CommercialPlanVersionRepository planVersionRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
        this.planTemplateRepository = planTemplateRepository;
        this.planVersionRepository = planVersionRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusCountsResponse getStatusCounts() {
        return new SubscriptionStatusCountsResponse(
                subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.ACTIVE),
                subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.SCHEDULED),
                subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.PAUSED),
                subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.EXPIRED),
                subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.CANCELLED)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionSummaryResponse> listSubscriptions(String search, UUID tenantId, UUID planTemplateId, SubscriptionStatus status, int page, int size) {
        var result = subscriptionRepository.findAll(subscriptionSpec(search, tenantId, planTemplateId, status),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("startDate"), Sort.Order.desc("createdAt"))));
        return new PageResponse<>(result.map(this::toSummary).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SubscriptionDetailResponse getSubscription(UUID id) {
        CommercialTenantSubscriptionEntity entity = subscriptionRepository.findById(id).orElseThrow(() -> notFound("Subscription", id));
        return toDetail(entity, validate(entity));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionHistoryResponse> loadHistory(UUID id) {
        CommercialTenantSubscriptionEntity subscription = subscriptionRepository.findById(id).orElseThrow(() -> notFound("Subscription", id));
        return eventRepository.findBySubscription_IdOrderByPerformedAtDesc(subscription.getId()).stream().map(this::toHistory).toList();
    }

    @Transactional
    public SubscriptionDetailResponse createAssignment(CreateSubscriptionRequest request) {
        AssignmentContext context = resolveAssignment(request.tenantId(), request.publishedVersionId());
        SubscriptionStatus initialStatus = request.startDate() != null && request.startDate().isAfter(LocalDate.now(ZoneOffset.UTC)) ? SubscriptionStatus.SCHEDULED : SubscriptionStatus.DRAFT;
        ValidationResult validation = validate(request, null, initialStatus, context);
        if (validation.validationState() != ValidationState.VALID) {
            throw conflict(validationMessage(validation));
        }
        OffsetDateTime now = now();
        UUID actor = currentActor();
        CommercialTenantSubscriptionEntity entity = CommercialTenantSubscriptionEntity.create(
                UUID.randomUUID(),
                requireUuid(request.tenantId(), "tenantId is required"),
                context.template(),
                context.version(),
                initialStatus,
                requireDate(request.startDate(), "startDate is required"),
                request.endDate(),
                request.autoRenew(),
                blankToNull(request.displayName()),
                blankToNull(request.referenceNumber()),
                blankToNull(request.notes()),
                now,
                actor
        );
        subscriptionRepository.save(entity);
        recordEvent(entity, initialStatus == SubscriptionStatus.SCHEDULED ? AuditEventAction.COMMERCIAL_SUBSCRIPTION_SCHEDULED : AuditEventAction.COMMERCIAL_SUBSCRIPTION_CREATED, null, initialStatus, request == null ? null : request.notes());
        audit(entity.getId(), AuditEntityType.COMMERCIAL_TENANT_SUBSCRIPTION, AuditEventAction.COMMERCIAL_SUBSCRIPTION_CREATED, "Created commercial subscription", Map.of("tenantId", entity.getTenantId(), "version", entity.getPublishedVersion().getVersionNumber(), "status", entity.getSubscriptionStatus().name()));
        return toDetail(entity, validate(entity));
    }

    @Transactional
    public SubscriptionDetailResponse activate(UUID id, LifecycleActionRequest request) {
        CommercialTenantSubscriptionEntity entity = load(id);
        SubscriptionStatus previous = entity.getSubscriptionStatus();
        if (previous == SubscriptionStatus.CANCELLED || previous == SubscriptionStatus.EXPIRED || previous == SubscriptionStatus.SUPERSEDED) {
            throw conflict("Cannot activate a " + previous.name().toLowerCase(Locale.ROOT) + " subscription");
        }
        if (entity.getStartDate() != null && entity.getStartDate().isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw conflict("Scheduled subscriptions can only be activated on or after the start date");
        }
        ensureNoOtherActiveSubscription(entity.getTenantId(), entity.getId());
        entity.transition(SubscriptionStatus.ACTIVE, now(), currentActor());
        subscriptionRepository.save(entity);
        recordEvent(entity, AuditEventAction.COMMERCIAL_SUBSCRIPTION_ACTIVATED, previous, SubscriptionStatus.ACTIVE, remarks(request));
        audit(entity.getId(), AuditEntityType.COMMERCIAL_TENANT_SUBSCRIPTION, AuditEventAction.COMMERCIAL_SUBSCRIPTION_ACTIVATED, "Activated commercial subscription", Map.of("tenantId", entity.getTenantId(), "status", entity.getSubscriptionStatus().name()));
        return toDetail(entity, validate(entity));
    }

    @Transactional
    public SubscriptionDetailResponse pause(UUID id, LifecycleActionRequest request) {
        CommercialTenantSubscriptionEntity entity = load(id);
        if (entity.getSubscriptionStatus() == SubscriptionStatus.PAUSED) {
            throw conflict("Subscription is already paused");
        }
        if (entity.getSubscriptionStatus() != SubscriptionStatus.ACTIVE) {
            throw conflict("Only active subscriptions can be paused");
        }
        SubscriptionStatus previous = entity.getSubscriptionStatus();
        entity.transition(SubscriptionStatus.PAUSED, now(), currentActor());
        subscriptionRepository.save(entity);
        recordEvent(entity, AuditEventAction.COMMERCIAL_SUBSCRIPTION_PAUSED, previous, SubscriptionStatus.PAUSED, remarks(request));
        audit(entity.getId(), AuditEntityType.COMMERCIAL_TENANT_SUBSCRIPTION, AuditEventAction.COMMERCIAL_SUBSCRIPTION_PAUSED, "Paused commercial subscription", Map.of("tenantId", entity.getTenantId(), "status", entity.getSubscriptionStatus().name()));
        return toDetail(entity, validate(entity));
    }

    @Transactional
    public SubscriptionDetailResponse resume(UUID id, LifecycleActionRequest request) {
        CommercialTenantSubscriptionEntity entity = load(id);
        if (entity.getSubscriptionStatus() == SubscriptionStatus.EXPIRED) {
            throw conflict("Cannot resume an expired subscription");
        }
        if (entity.getSubscriptionStatus() == SubscriptionStatus.CANCELLED || entity.getSubscriptionStatus() == SubscriptionStatus.SUPERSEDED) {
            throw conflict("Cannot resume a terminal subscription");
        }
        if (entity.getSubscriptionStatus() != SubscriptionStatus.PAUSED) {
            throw conflict("Only paused subscriptions can be resumed");
        }
        ensureNoOtherActiveSubscription(entity.getTenantId(), entity.getId());
        SubscriptionStatus previous = entity.getSubscriptionStatus();
        entity.transition(SubscriptionStatus.ACTIVE, now(), currentActor());
        subscriptionRepository.save(entity);
        recordEvent(entity, AuditEventAction.COMMERCIAL_SUBSCRIPTION_RESUMED, previous, SubscriptionStatus.ACTIVE, remarks(request));
        audit(entity.getId(), AuditEntityType.COMMERCIAL_TENANT_SUBSCRIPTION, AuditEventAction.COMMERCIAL_SUBSCRIPTION_RESUMED, "Resumed commercial subscription", Map.of("tenantId", entity.getTenantId(), "status", entity.getSubscriptionStatus().name()));
        return toDetail(entity, validate(entity));
    }

    @Transactional
    public SubscriptionDetailResponse cancel(UUID id, LifecycleActionRequest request) {
        CommercialTenantSubscriptionEntity entity = load(id);
        if (entity.getSubscriptionStatus() == SubscriptionStatus.CANCELLED) {
            return toDetail(entity, validate(entity));
        }
        SubscriptionStatus previous = entity.getSubscriptionStatus();
        entity.transition(SubscriptionStatus.CANCELLED, now(), currentActor());
        subscriptionRepository.save(entity);
        recordEvent(entity, AuditEventAction.COMMERCIAL_SUBSCRIPTION_CANCELLED, previous, SubscriptionStatus.CANCELLED, remarks(request));
        audit(entity.getId(), AuditEntityType.COMMERCIAL_TENANT_SUBSCRIPTION, AuditEventAction.COMMERCIAL_SUBSCRIPTION_CANCELLED, "Cancelled commercial subscription", Map.of("tenantId", entity.getTenantId(), "status", entity.getSubscriptionStatus().name()));
        return toDetail(entity, validate(entity));
    }

    @Transactional
    public SubscriptionDetailResponse expire(UUID id, LifecycleActionRequest request) {
        CommercialTenantSubscriptionEntity entity = load(id);
        if (entity.getSubscriptionStatus() == SubscriptionStatus.EXPIRED) {
            return toDetail(entity, validate(entity));
        }
        if (entity.getSubscriptionStatus() == SubscriptionStatus.CANCELLED || entity.getSubscriptionStatus() == SubscriptionStatus.SUPERSEDED) {
            throw conflict("Cannot expire a terminal subscription");
        }
        SubscriptionStatus previous = entity.getSubscriptionStatus();
        entity.transition(SubscriptionStatus.EXPIRED, now(), currentActor());
        subscriptionRepository.save(entity);
        recordEvent(entity, AuditEventAction.COMMERCIAL_SUBSCRIPTION_EXPIRED, previous, SubscriptionStatus.EXPIRED, remarks(request));
        audit(entity.getId(), AuditEntityType.COMMERCIAL_TENANT_SUBSCRIPTION, AuditEventAction.COMMERCIAL_SUBSCRIPTION_EXPIRED, "Expired commercial subscription", Map.of("tenantId", entity.getTenantId(), "status", entity.getSubscriptionStatus().name()));
        return toDetail(entity, validate(entity));
    }

    @Transactional
    public SubscriptionDetailResponse replace(UUID subscriptionId, ReplaceSubscriptionRequest request) {
        CommercialTenantSubscriptionEntity current = load(subscriptionId);
        if (current.getSubscriptionStatus() == SubscriptionStatus.CANCELLED || current.getSubscriptionStatus() == SubscriptionStatus.EXPIRED) {
            throw conflict("Cannot replace a terminal subscription");
        }
        AssignmentContext context = resolveAssignment(current.getTenantId(), request.publishedVersionId());
        ValidationResult validation = validate(new CreateSubscriptionRequest(current.getTenantId(), request.publishedVersionId(), request.startDate(), request.endDate(), request.autoRenew(), request.displayName(), request.referenceNumber(), request.notes()), current.getId(), request.startDate() != null && request.startDate().isAfter(LocalDate.now(ZoneOffset.UTC)) ? SubscriptionStatus.SCHEDULED : SubscriptionStatus.DRAFT, context);
        if (validation.validationState() != ValidationState.VALID) {
            throw conflict(validationMessage(validation));
        }
        OffsetDateTime now = now();
        UUID actor = currentActor();
        CommercialTenantSubscriptionEntity replacement = CommercialTenantSubscriptionEntity.create(
                UUID.randomUUID(),
                current.getTenantId(),
                context.template(),
                context.version(),
                request.startDate() != null && request.startDate().isAfter(LocalDate.now(ZoneOffset.UTC)) ? SubscriptionStatus.SCHEDULED : SubscriptionStatus.DRAFT,
                requireDate(request.startDate(), "startDate is required"),
                request.endDate(),
                request.autoRenew(),
                blankToNull(request.displayName()),
                blankToNull(request.referenceNumber()),
                blankToNull(request.notes()),
                now,
                actor
        );
        subscriptionRepository.save(replacement);
        SubscriptionStatus previous = current.getSubscriptionStatus();
        current.transition(SubscriptionStatus.SUPERSEDED, now, actor);
        subscriptionRepository.save(current);
        recordEvent(current, AuditEventAction.COMMERCIAL_SUBSCRIPTION_REPLACED, previous, SubscriptionStatus.SUPERSEDED, request == null ? null : request.notes());
        recordEvent(replacement, replacement.getSubscriptionStatus() == SubscriptionStatus.SCHEDULED ? AuditEventAction.COMMERCIAL_SUBSCRIPTION_SCHEDULED : AuditEventAction.COMMERCIAL_SUBSCRIPTION_CREATED, null, replacement.getSubscriptionStatus(), request == null ? null : request.notes());
        audit(replacement.getId(), AuditEntityType.COMMERCIAL_TENANT_SUBSCRIPTION, AuditEventAction.COMMERCIAL_SUBSCRIPTION_REPLACED, "Replaced commercial subscription", Map.of("tenantId", replacement.getTenantId(), "replacedSubscriptionId", current.getId(), "status", replacement.getSubscriptionStatus().name()));
        return toDetail(replacement, validate(replacement));
    }

    @Transactional(readOnly = true)
    public ValidationResultResponse validateAssignment(CreateSubscriptionRequest request) {
        return validate(request, null, request != null && request.startDate() != null && request.startDate().isAfter(LocalDate.now(ZoneOffset.UTC)) ? SubscriptionStatus.SCHEDULED : SubscriptionStatus.DRAFT, null).toResponse();
    }

    private ValidationResult validate(CreateSubscriptionRequest request, UUID ignoreSubscriptionId, SubscriptionStatus plannedStatus, AssignmentContext context) {
        List<ValidationMessageResponse> messages = new ArrayList<>();
        UUID tenantId = request == null ? null : request.tenantId();
        UUID publishedVersionId = request == null ? null : request.publishedVersionId();
        LocalDate startDate = request == null ? null : request.startDate();
        LocalDate endDate = request == null ? null : request.endDate();

        if (tenantId == null) {
            messages.add(message("tenantId", "TENANT_REQUIRED", "Select a tenant for the subscription", "Choose an existing tenant.", ValidationSeverity.BLOCKING, true));
        }
        if (publishedVersionId == null) {
            messages.add(message("publishedVersionId", "PUBLISHED_VERSION_REQUIRED", "Select a published plan version", "Choose a published commercial plan version.", ValidationSeverity.BLOCKING, true));
        }
        if (startDate == null) {
            messages.add(message("startDate", "START_DATE_REQUIRED", "Choose an effective start date", "Select when the subscription should start.", ValidationSeverity.BLOCKING, true));
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            messages.add(message("endDate", "END_DATE_BEFORE_START", "End date cannot be earlier than the start date", "Choose a date on or after the start date.", ValidationSeverity.BLOCKING, true));
        }

        AssignmentContext resolved = context == null ? resolveAssignment(tenantId, publishedVersionId) : context;
        if (resolved.version() == null || resolved.template() == null) {
            messages.add(message("publishedVersionId", "PUBLISHED_VERSION_NOT_FOUND", "Selected published version was not found", "Choose a version that still exists in the catalog.", ValidationSeverity.BLOCKING, true));
        } else {
            if (resolved.version().getStatus() != com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.PublicationStatus.PUBLISHED) {
                messages.add(message("publishedVersionId", "PUBLISHED_VERSION_NOT_PUBLISHED", "Only published versions can be assigned", "Choose an active published version.", ValidationSeverity.BLOCKING, true));
            }
            if (resolved.template().getStatus() == com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TemplateStatus.RETIRED) {
                messages.add(message("publishedVersionId", "TEMPLATE_RETIRED", "Retired plan templates cannot be assigned", "Choose a published version from an active plan template.", ValidationSeverity.BLOCKING, true));
            }
        }

        if (tenantId != null && startDate != null) {
            List<CommercialTenantSubscriptionEntity> activeSubscriptions = subscriptionRepository.findByTenantIdAndSubscriptionStatusOrderByStartDateAscCreatedAtAsc(tenantId, SubscriptionStatus.ACTIVE);
            for (CommercialTenantSubscriptionEntity active : activeSubscriptions) {
                if (ignoreSubscriptionId != null && ignoreSubscriptionId.equals(active.getId())) {
                    continue;
                }
                if (overlaps(active.getStartDate(), active.getEndDate(), startDate, endDate)) {
                    messages.add(message("tenantId", "ACTIVE_SUBSCRIPTION_OVERLAP", "A tenant can only have one active subscription at a time", "End or replace the existing active subscription before assigning another one.", ValidationSeverity.BLOCKING, true));
                    break;
                }
            }
        }

        if (request != null) {
            if (!StringUtils.hasText(request.displayName()) && resolved.version() != null) {
                messages.add(message("displayName", "DISPLAY_NAME_REQUIRED", "Provide a display name for the subscription", "Enter a business-friendly subscription label.", ValidationSeverity.WARNING, false));
            }
        }

        int blockingCount = (int) messages.stream().filter(ValidationMessageResponse::blocking).count();
        int warningCount = (int) messages.stream().filter(m -> m.severity() == CommercialSubscriptionEnums.ValidationSeverity.WARNING).count();
        return new ValidationResult(
                blockingCount == 0 ? ValidationState.VALID : ValidationState.INVALID,
                blockingCount == 0 && resolved.version() != null && resolved.template() != null,
                blockingCount,
                warningCount,
                messages,
                now()
        );
    }

    private ValidationResult validate(CommercialTenantSubscriptionEntity entity) {
        if (entity == null) {
            return new ValidationResult(ValidationState.NOT_VALIDATED, false, 0, 0, List.of(), now());
        }
        AssignmentContext context = new AssignmentContext(entity.getPlanTemplate(), entity.getPublishedVersion());
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                entity.getTenantId(),
                entity.getPublishedVersion() == null ? null : entity.getPublishedVersion().getId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.isAutoRenew(),
                entity.getDisplayName(),
                entity.getReferenceNumber(),
                entity.getNotes()
        );
        return validate(request, entity.getId(), entity.getSubscriptionStatus(), context);
    }

    private AssignmentContext resolveAssignment(UUID tenantId, UUID publishedVersionId) {
        if (tenantId == null || publishedVersionId == null) {
            return new AssignmentContext(null, null);
        }
        CommercialPlanVersionEntity version = planVersionRepository.findById(publishedVersionId).orElse(null);
        CommercialPlanTemplateEntity template = version == null ? null : version.getTemplate();
        return new AssignmentContext(template, version);
    }

    private void ensureNoOtherActiveSubscription(UUID tenantId, UUID ignoreSubscriptionId) {
        List<CommercialTenantSubscriptionEntity> activeSubscriptions = subscriptionRepository.findByTenantIdAndSubscriptionStatusOrderByStartDateAscCreatedAtAsc(tenantId, SubscriptionStatus.ACTIVE);
        for (CommercialTenantSubscriptionEntity active : activeSubscriptions) {
            if (ignoreSubscriptionId == null || !ignoreSubscriptionId.equals(active.getId())) {
                throw conflict("Tenant already has an active subscription");
            }
        }
    }

    private CommercialTenantSubscriptionEntity load(UUID id) {
        return subscriptionRepository.findById(id).orElseThrow(() -> notFound("Subscription", id));
    }

    private SubscriptionSummaryResponse toSummary(CommercialTenantSubscriptionEntity entity) {
        return new SubscriptionSummaryResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getPlanTemplate().getId(),
                entity.getPlanTemplate().getCode(),
                entity.getPlanTemplate().getName(),
                entity.getPublishedVersion().getId(),
                entity.getPublishedVersion().getVersionNumber(),
                entity.getPublishedVersion().getVersionLabel(),
                entity.getSubscriptionStatus(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.isAutoRenew(),
                entity.getDisplayName(),
                entity.getReferenceNumber(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private SubscriptionDetailResponse toDetail(CommercialTenantSubscriptionEntity entity, ValidationResult validation) {
        return new SubscriptionDetailResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getPlanTemplate().getId(),
                entity.getPlanTemplate().getCode(),
                entity.getPlanTemplate().getName(),
                entity.getPublishedVersion().getId(),
                entity.getPublishedVersion().getVersionNumber(),
                entity.getPublishedVersion().getVersionLabel(),
                entity.getSubscriptionStatus(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.isAutoRenew(),
                entity.getDisplayName(),
                entity.getReferenceNumber(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy(),
                eventRepository.findBySubscription_IdOrderByPerformedAtDesc(entity.getId()).stream().map(this::toHistory).toList(),
                validation.toResponse()
        );
    }

    private SubscriptionHistoryResponse toHistory(CommercialSubscriptionEventEntity entity) {
        return new SubscriptionHistoryResponse(
                entity.getId(),
                entity.getEventType(),
                entity.getPreviousStatus(),
                entity.getNewStatus(),
                entity.getPerformedBy(),
                entity.getPerformedAt(),
                entity.getRemarks()
        );
    }

    private void recordEvent(CommercialTenantSubscriptionEntity entity, String eventType, SubscriptionStatus previous, SubscriptionStatus next, String remarks) {
        CommercialSubscriptionEventEntity event = CommercialSubscriptionEventEntity.create(
                UUID.randomUUID(),
                entity,
                eventType,
                previous == null ? null : previous.name(),
                next == null ? entity.getSubscriptionStatus().name() : next.name(),
                currentActor(),
                now(),
                blankToNull(remarks)
        );
        eventRepository.save(event);
    }

    private void audit(UUID entityId, String entityType, String action, String summary, Map<String, Object> details) {
        auditEventPublisher.record(new AuditEventCommand(
                PLATFORM_AUDIT_TENANT_ID,
                entityType,
                entityId,
                action,
                currentActor(),
                now(),
                summary,
                serialize(details == null ? Map.of() : details)
        ));
    }

    private String validationMessage(ValidationResult validation) {
        return validation.findings().stream().filter(ValidationMessageResponse::blocking).map(ValidationMessageResponse::message).findFirst().orElse("Commercial subscription assignment is invalid");
    }

    private boolean overlaps(LocalDate existingStart, LocalDate existingEnd, LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDate aStart = existingStart == null ? LocalDate.MIN : existingStart;
        LocalDate aEnd = existingEnd == null ? LocalDate.MAX : existingEnd;
        LocalDate bStart = requestedStart == null ? LocalDate.MIN : requestedStart;
        LocalDate bEnd = requestedEnd == null ? LocalDate.MAX : requestedEnd;
        return !aStart.isAfter(bEnd) && !bStart.isAfter(aEnd);
    }

    private Specification<CommercialTenantSubscriptionEntity> subscriptionSpec(String search, UUID tenantId, UUID planTemplateId, SubscriptionStatus status) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (planTemplateId != null) {
                predicates.add(cb.equal(root.get("planTemplate").get("id"), planTemplateId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("subscriptionStatus"), status));
            }
            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("displayName")), like),
                        cb.like(cb.lower(root.get("referenceNumber")), like),
                        cb.like(cb.lower(root.get("notes")), like),
                        cb.like(cb.lower(root.join("planTemplate").get("code")), like),
                        cb.like(cb.lower(root.join("planTemplate").get("name")), like),
                        cb.like(cb.lower(root.join("publishedVersion").get("versionLabel")), like),
                        cb.like(cb.lower(root.get("subscriptionStatus").as(String.class)), like)
                ));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private UUID currentActor() {
        return RequestContextHolder.get() == null ? null : RequestContextHolder.get().appUserId();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize commercial subscription audit details", ex);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String remarks(LifecycleActionRequest request) {
        return request == null ? null : blankToNull(request.remarks());
    }

    private UUID requireUuid(UUID value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private LocalDate requireDate(LocalDate value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private ResponseStatusException notFound(String type, UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found: " + id);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ValidationMessageResponse message(String field, String code, String message, String remediation, ValidationSeverity severity, boolean blocking) {
        return new ValidationMessageResponse(field, code, message, remediation, CommercialSubscriptionEnums.ValidationSeverity.valueOf(severity.name()), blocking);
    }

    private record AssignmentContext(CommercialPlanTemplateEntity template, CommercialPlanVersionEntity version) {
    }

    private record ValidationResult(
            ValidationState validationState,
            boolean readyToAssign,
            int blockingFindingCount,
            int warningFindingCount,
            List<ValidationMessageResponse> findings,
            OffsetDateTime validatedAt
    ) {
        ValidationResultResponse toResponse() {
            return new ValidationResultResponse(validationState, readyToAssign, blockingFindingCount, warningFindingCount, findings, validatedAt);
        }
    }
}
