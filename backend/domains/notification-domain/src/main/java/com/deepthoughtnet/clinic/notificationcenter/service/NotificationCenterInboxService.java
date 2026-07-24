package com.deepthoughtnet.clinic.notificationcenter.service;

import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRecipientEntity;
import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRecipientRepository;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterItem;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterPage;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterPreview;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterQuery;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterSummary;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterUnreadCount;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationCategory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationPriority;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NotificationCenterInboxService {
    private final StaffNotificationRecipientRepository recipientRepository;

    public NotificationCenterInboxService(StaffNotificationRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    @Transactional(readOnly = true)
    public NotificationCenterUnreadCount unreadCount(UUID tenantId, UUID appUserId) {
        return new NotificationCenterUnreadCount(recipientRepository.countByTenantIdAndAppUserIdAndReadAtIsNull(tenantId, appUserId));
    }

    @Transactional(readOnly = true)
    public NotificationCenterPreview preview(UUID tenantId, UUID appUserId, int size) {
        int limit = normalizeSize(size, 10);
        List<NotificationCenterItem> items = recipientRepository.findByTenantIdAndAppUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                        tenantId,
                        appUserId,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(this::toItem)
                .toList();
        return new NotificationCenterPreview(items);
    }

    @Transactional(readOnly = true)
    public NotificationCenterPage list(UUID tenantId, UUID appUserId, NotificationCenterQuery query) {
        Pageable pageable = PageRequest.of(
                Math.max(0, query == null ? 0 : query.page()),
                normalizeSize(query == null ? 20 : query.size(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return toPage(recipientRepository.findAll(specification(tenantId, appUserId, query), pageable));
    }

    @Transactional(readOnly = true)
    public NotificationCenterSummary summary(UUID tenantId, UUID appUserId, ZoneId zone) {
        ZoneId effectiveZone = zone == null ? ZoneOffset.UTC : zone;
        long unread = recipientRepository.countByTenantIdAndAppUserIdAndReadAtIsNull(tenantId, appUserId);
        long requiresAction = recipientRepository.count(specification(tenantId, appUserId, new NotificationCenterQuery(null, null, null, Boolean.TRUE, null, null, null, 0, 0)));
        long critical = recipientRepository.count(specification(tenantId, appUserId, new NotificationCenterQuery(null, null, "CRITICAL", null, null, null, null, 0, 0)));
        LocalDate today = LocalDate.now(effectiveZone);
        OffsetDateTime from = today.atStartOfDay(effectiveZone).toOffsetDateTime();
        OffsetDateTime to = today.plusDays(1).atStartOfDay(effectiveZone).toOffsetDateTime();
        long todayCount = recipientRepository.count((root, cq, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId),
                cb.equal(root.get("appUserId"), appUserId),
                cb.greaterThanOrEqualTo(root.get("occurredAt"), from),
                cb.lessThan(root.get("occurredAt"), to)
        ));
        return new NotificationCenterSummary(unread, requiresAction, critical, todayCount);
    }

    @Transactional(readOnly = true)
    public NotificationCenterItem get(UUID tenantId, UUID appUserId, UUID id) {
        StaffNotificationRecipientEntity entity = recipientRepository.findByTenantIdAndIdAndAppUserId(tenantId, id, appUserId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        return toItem(entity);
    }

    @Transactional
    public NotificationCenterItem markRead(UUID tenantId, UUID appUserId, UUID id) {
        StaffNotificationRecipientEntity entity = recipientRepository.findByTenantIdAndIdAndAppUserId(tenantId, id, appUserId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        entity.markRead();
        return toItem(entity);
    }

    @Transactional
    public NotificationCenterItem markUnread(UUID tenantId, UUID appUserId, UUID id) {
        StaffNotificationRecipientEntity entity = recipientRepository.findByTenantIdAndIdAndAppUserId(tenantId, id, appUserId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        entity.markUnread();
        return toItem(entity);
    }

    @Transactional
    public long markAllRead(UUID tenantId, UUID appUserId) {
        List<StaffNotificationRecipientEntity> unread = recipientRepository.findByTenantIdAndAppUserIdAndReadAtIsNullOrderByCreatedAtDesc(tenantId, appUserId, Pageable.unpaged())
                .stream()
                .toList();
        unread.forEach(StaffNotificationRecipientEntity::markRead);
        return unread.size();
    }

    private NotificationCenterPage toPage(org.springframework.data.domain.Page<StaffNotificationRecipientEntity> page) {
        return new NotificationCenterPage(
                page.getContent().stream().map(this::toItem).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private NotificationCenterItem toItem(StaffNotificationRecipientEntity entity) {
        return new NotificationCenterItem(
                entity.getId(),
                entity.getNotificationId(),
                entity.getTenantId(),
                entity.getTitle(),
                entity.getPreview(),
                entity.getCategory(),
                entity.getPriority(),
                entity.getBusinessReference(),
                entity.getSourceModule(),
                entity.getSourceEventType(),
                entity.getTitle(),
                entity.getActionLabel(),
                entity.getActionRoute(),
                entity.getActionTargetId(),
                entity.getRecipientDisplayName(),
                entity.getRecipientRole(),
                entity.getMatchedAudience(),
                entity.isRead(),
                entity.getReadAt(),
                entity.getOccurredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCorrelationId(),
                entity.getCausationId(),
                entity.getVersion()
        );
    }

    private Specification<StaffNotificationRecipientEntity> specification(UUID tenantId, UUID appUserId, NotificationCenterQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("appUserId"), appUserId));

            if (query != null) {
                if (StringUtils.hasText(query.readState())) {
                    String normalized = query.readState().trim().toUpperCase(Locale.ROOT);
                    if ("READ".equals(normalized)) {
                        predicates.add(cb.isNotNull(root.get("readAt")));
                    } else if ("UNREAD".equals(normalized)) {
                        predicates.add(cb.isNull(root.get("readAt")));
                    }
                }
                if (StringUtils.hasText(query.category())) {
                    NotificationCategory category = parseCategory(query.category());
                    if (category != null) {
                        predicates.add(cb.equal(root.get("category"), category));
                    }
                }
                if (StringUtils.hasText(query.priority())) {
                    NotificationPriority priority = parsePriority(query.priority());
                    if (priority != null) {
                        predicates.add(cb.equal(root.get("priority"), priority));
                    }
                }
                if (Boolean.TRUE.equals(query.requiresAction())) {
                    predicates.add(cb.or(
                            cb.isNotNull(root.get("actionRoute")),
                            cb.isNotNull(root.get("actionLabel"))
                    ));
                }
                if (StringUtils.hasText(query.search())) {
                    String search = "%" + query.search().trim().toLowerCase(Locale.ROOT) + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("title")), search),
                            cb.like(cb.lower(root.get("preview")), search),
                            cb.like(cb.lower(root.get("businessReference")), search),
                            cb.like(cb.lower(root.get("recipientDisplayName")), search),
                            cb.like(cb.lower(root.get("recipientRole")), search),
                            cb.like(cb.lower(root.get("sourceEventType")), search)
                    ));
                }
                if (query.from() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
                }
                if (query.to() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), query.to()));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private int normalizeSize(int size, int defaultSize) {
        if (size <= 0) {
            return defaultSize;
        }
        return Math.min(size, 100);
    }

    private NotificationCategory parseCategory(String value) {
        try {
            return NotificationCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private NotificationPriority parsePriority(String value) {
        try {
            return NotificationPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
