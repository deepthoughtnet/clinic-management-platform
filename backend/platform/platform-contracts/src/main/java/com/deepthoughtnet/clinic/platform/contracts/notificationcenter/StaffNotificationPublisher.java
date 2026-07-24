package com.deepthoughtnet.clinic.platform.contracts.notificationcenter;

import java.util.UUID;

public interface StaffNotificationPublisher {
    UUID publish(StaffNotificationRequest request);
}
