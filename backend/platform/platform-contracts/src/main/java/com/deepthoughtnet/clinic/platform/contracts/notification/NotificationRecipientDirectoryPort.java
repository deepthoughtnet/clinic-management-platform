package com.deepthoughtnet.clinic.platform.contracts.notification;

import java.util.List;
import java.util.UUID;

public interface NotificationRecipientDirectoryPort {
    List<String> resolveEmailsByRoles(UUID tenantId, List<String> roles);
}
