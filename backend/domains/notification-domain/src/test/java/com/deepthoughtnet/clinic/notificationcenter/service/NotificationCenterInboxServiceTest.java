package com.deepthoughtnet.clinic.notificationcenter.service;

import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRecipientEntity;
import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRecipientRepository;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationCategory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCenterInboxServiceTest {

    @Mock
    private StaffNotificationRecipientRepository recipientRepository;

    @Test
    void previewUsesUnreadProjectionAndUnreadCount() {
        NotificationCenterInboxService service = new NotificationCenterInboxService(recipientRepository);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaffNotificationRecipientEntity entity = mockRecipient("Appointment booked", "Your appointment is confirmed");

        when(recipientRepository.countByTenantIdAndAppUserIdAndReadAtIsNull(tenantId, userId)).thenReturn(3L);
        when(recipientRepository.findByTenantIdAndAppUserIdAndReadAtIsNullOrderByCreatedAtDesc(eq(tenantId), eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        assertThat(service.unreadCount(tenantId, userId).count()).isEqualTo(3L);
        assertThat(service.preview(tenantId, userId, 5).items()).hasSize(1);
        assertThat(service.preview(tenantId, userId, 5).items().get(0).title()).isEqualTo("Appointment booked");
    }

    @Test
    void markAllReadMarksAllUnreadRows() {
        NotificationCenterInboxService service = new NotificationCenterInboxService(recipientRepository);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaffNotificationRecipientEntity first = org.mockito.Mockito.mock(StaffNotificationRecipientEntity.class);
        StaffNotificationRecipientEntity second = org.mockito.Mockito.mock(StaffNotificationRecipientEntity.class);

        when(recipientRepository.findByTenantIdAndAppUserIdAndReadAtIsNullOrderByCreatedAtDesc(tenantId, userId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        long updated = service.markAllRead(tenantId, userId);

        assertThat(updated).isEqualTo(2L);
        verify(first).markRead();
        verify(second).markRead();
    }

    @Test
    void summaryIncludesUnreadRequiresActionCriticalAndTodayCounts() {
        NotificationCenterInboxService service = new NotificationCenterInboxService(recipientRepository);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(recipientRepository.countByTenantIdAndAppUserIdAndReadAtIsNull(tenantId, userId)).thenReturn(4L);
        when(recipientRepository.count(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<StaffNotificationRecipientEntity>>any())).thenReturn(2L, 1L, 3L);

        assertThat(service.summary(tenantId, userId, ZoneId.of("UTC")))
                .isEqualTo(new NotificationCenterDtos.NotificationCenterSummary(4L, 2L, 1L, 3L));
    }

    private StaffNotificationRecipientEntity mockRecipient(String title, String preview) {
        StaffNotificationRecipientEntity entity = org.mockito.Mockito.mock(StaffNotificationRecipientEntity.class);
        when(entity.getId()).thenReturn(UUID.randomUUID());
        when(entity.getTitle()).thenReturn(title);
        when(entity.getPreview()).thenReturn(preview);
        when(entity.getCategory()).thenReturn(NotificationCategory.APPOINTMENT);
        when(entity.getPriority()).thenReturn(com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationPriority.NORMAL);
        return entity;
    }
}
