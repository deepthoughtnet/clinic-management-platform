package com.deepthoughtnet.clinic.carepilot.lead.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.lead.activity.db.LeadActivityEntity;
import com.deepthoughtnet.clinic.carepilot.lead.activity.db.LeadActivityRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class LeadActivityServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();

    private LeadActivityRepository repository;
    private LeadActivityService service;

    @BeforeEach
    void setUp() {
        repository = mock(LeadActivityRepository.class);
        service = new LeadActivityService(repository);
    }

    @Test
    void listOrdersEqualTimestampsByLifecyclePriorityNewestFirst() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-20T10:00:00Z");
        LeadActivityEntity created = activity(LeadActivityType.CREATED, timestamp);
        LeadActivityEntity followUpCompleted = activity(LeadActivityType.FOLLOW_UP_COMPLETED, timestamp);
        LeadActivityEntity converted = activity(LeadActivityType.CONVERTED_TO_PATIENT, timestamp);
        when(repository.findByTenantIdAndLeadIdOrderByCreatedAtDesc(eq(tenantId), eq(leadId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(created, followUpCompleted, converted)));

        var page = service.list(tenantId, leadId, 0, 25);

        assertThat(page.getContent()).extracting("activityType").containsExactly(
                LeadActivityType.CONVERTED_TO_PATIENT,
                LeadActivityType.FOLLOW_UP_COMPLETED,
                LeadActivityType.CREATED
        );
    }

    private LeadActivityEntity activity(LeadActivityType type, OffsetDateTime createdAt) {
        LeadActivityEntity entity = LeadActivityEntity.create(tenantId, leadId, type, type.name(), null, null, null, null, null, null);
        setField(entity, "createdAt", createdAt);
        return entity;
    }

    private void setField(LeadActivityEntity entity, String fieldName, Object value) {
        try {
            Field field = LeadActivityEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
