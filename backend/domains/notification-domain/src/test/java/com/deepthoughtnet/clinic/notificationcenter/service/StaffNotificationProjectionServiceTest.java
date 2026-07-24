package com.deepthoughtnet.clinic.notificationcenter.service;

import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationEntity;
import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRecipientEntity;
import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRecipientRepository;
import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRepository;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterAudienceResolver.ResolvedAudienceRecipient;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationAudience;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationCategory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationPriority;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationAction;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffNotificationProjectionServiceTest {

    @Mock
    private StaffNotificationRepository notificationRepository;

    @Mock
    private StaffNotificationRecipientRepository recipientRepository;

    @Mock
    private NotificationCenterAudienceResolver audienceResolver;

    @InjectMocks
    private StaffNotificationProjectionService projectionService;

    @Test
    void publishCreatesLogicalNotificationAndRecipientProjectionOnce() {
        UUID tenantId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID appUserId = UUID.randomUUID();
        StaffNotificationRequest request = new StaffNotificationRequest(
                tenantId,
                sourceEventId,
                "APPOINTMENT_BOOKED",
                "APPOINTMENT",
                "APPOINTMENT",
                aggregateId,
                "Appointment booked",
                "Appointment booked with Dr. Rao on 24 Jul 2026, 10:00 AM",
                NotificationCategory.APPOINTMENT,
                NotificationPriority.NORMAL,
                "APT-1001",
                StaffNotificationAction.of("Open", "/appointments", aggregateId.toString()),
                List.of(NotificationAudience.user(appUserId.toString())),
                OffsetDateTime.parse("2026-07-23T10:00:00Z"),
                "corr-1",
                "cause-1"
        );

        when(notificationRepository.findByTenantIdAndSourceEventId(tenantId, sourceEventId)).thenReturn(Optional.empty());
        when(notificationRepository.save(any(StaffNotificationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(audienceResolver.resolve(eq(tenantId), any())).thenReturn(List.of(
                new ResolvedAudienceRecipient(appUserId, "Alice Rao", "DOCTOR", "USER")
        ));
        when(recipientRepository.findByTenantIdAndNotification_IdAndAppUserId(eq(tenantId), any(UUID.class), eq(appUserId))).thenReturn(Optional.empty());
        when(recipientRepository.save(any(StaffNotificationRecipientEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID projectedId = projectionService.publish(request);

        assertThat(projectedId).isNotNull();
        verify(notificationRepository).save(any(StaffNotificationEntity.class));
        verify(recipientRepository).save(any(StaffNotificationRecipientEntity.class));
    }
}
