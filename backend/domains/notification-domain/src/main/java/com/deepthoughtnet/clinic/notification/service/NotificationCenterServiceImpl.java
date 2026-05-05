package com.deepthoughtnet.clinic.notification.service;

import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationCenterServiceImpl implements NotificationCenterService {

    private final NotificationOutboxRepository repository;

    public NotificationCenterServiceImpl(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationOutboxEntity> list(UUID tenantId, NotificationFilter filter) {
        Pageable pageable = PageRequest.of(
                normalizePage(filter == null ? 0 : filter.page()),
                normalizeSize(filter == null ? 20 : filter.size()),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return repository.findAll(toSpecification(tenantId, filter), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationOutboxEntity get(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    }

    @Override
    @Transactional
    public NotificationOutboxEntity retry(UUID tenantId, UUID id) {
        NotificationOutboxEntity notification = get(tenantId, id);
        if (!"FAILED".equals(notification.getStatus())) {
            throw new IllegalStateException("Only failed notifications can be retried");
        }
        notification.retryNow();
        return notification;
    }

    @Override
    @Transactional
    public NotificationOutboxEntity markIgnored(UUID tenantId, UUID id, UUID ignoredByAppUserId) {
        NotificationOutboxEntity notification = get(tenantId, id);
        if (!"FAILED".equals(notification.getStatus())) {
            throw new IllegalStateException("Only failed notifications can be ignored");
        }
        notification.markIgnored(ignoredByAppUserId);
        return notification;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationSummary summarize(UUID tenantId) {
        OffsetDateTime todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
        return new NotificationSummary(
                repository.countByTenantIdAndStatus(tenantId, "PENDING"),
                repository.countByTenantIdAndStatus(tenantId, "FAILED"),
                repository.countByTenantIdAndStatusAndProcessedAtGreaterThanEqual(
                        tenantId,
                        "SENT",
                        todayStart
                ),
                repository.countByTenantIdAndStatus(tenantId, "IGNORED"),
                repository.findLastFailedAt(tenantId).orElse(null)
        );
    }

    private Specification<NotificationOutboxEntity> toSpecification(UUID tenantId, NotificationFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (filter != null) {
                if (hasText(filter.status())) {
                    predicates.add(cb.equal(root.get("status"), filter.status().trim().toUpperCase(Locale.ROOT)));
                }
                if (hasText(filter.eventType())) {
                    predicates.add(cb.equal(root.get("eventType"), filter.eventType().trim()));
                }
                if (hasText(filter.module())) {
                    predicates.add(cb.equal(root.get("module"), filter.module().trim().toLowerCase(Locale.ROOT)));
                }
                if (hasText(filter.entityType())) {
                    predicates.add(cb.equal(root.get("entityType"), filter.entityType().trim()));
                }
                if (filter.entityId() != null) {
                    predicates.add(cb.equal(root.get("entityId"), filter.entityId()));
                }
                if (filter.from() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
                }
                if (filter.to() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private int normalizePage(int page) {
        return Math.max(0, page);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
