package com.deepthoughtnet.clinic.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.notification.db.NotificationHistoryEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationHistoryRepository;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationHistoryServiceImplTest {

    @Test
    void queueDetailedReturnsCreatedFalseWhenDuplicateAlreadyExists() {
        NotificationHistoryRepository repository = mock(NotificationHistoryRepository.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        NotificationHistoryServiceImpl service = new NotificationHistoryServiceImpl(
                repository,
                outboxEventPublisher,
                auditEventPublisher,
                new ObjectMapper()
        );

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        NotificationHistoryEntity existing = NotificationHistoryEntity.create(
                tenantId,
                patientId,
                "APPOINTMENT_REMINDER",
                "email",
                "patient@example.com",
                "Appointment reminder",
                "Appointment scheduled",
                "APPOINTMENT",
                sourceId,
                "existing-dedup"
        );
        when(repository.findByTenantIdAndDeduplicationKey(any(), any())).thenReturn(Optional.of(existing));

        NotificationQueueResult result = service.queueDetailed(
                tenantId,
                patientId,
                "APPOINTMENT_REMINDER",
                "email",
                "patient@example.com",
                "Appointment reminder",
                "Appointment scheduled",
                "APPOINTMENT",
                sourceId,
                UUID.randomUUID()
        );

        assertThat(result.created()).isFalse();
        assertThat(result.notification().id()).isEqualTo(existing.getId());
        verify(repository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }
}
