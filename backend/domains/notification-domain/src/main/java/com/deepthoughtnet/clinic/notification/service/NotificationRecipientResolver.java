package com.deepthoughtnet.clinic.notification.service;

import java.util.List;
import java.util.UUID;
import com.deepthoughtnet.clinic.platform.contracts.notification.NotificationRecipientDirectoryPort;
import org.springframework.stereotype.Service;

@Service
public class NotificationRecipientResolver {

    private final NotificationRecipientDirectoryPort recipientDirectoryPort;

    public NotificationRecipientResolver(NotificationRecipientDirectoryPort recipientDirectoryPort) {
        this.recipientDirectoryPort = recipientDirectoryPort;
    }

    public List<String> resolveEmailsByRoles(UUID tenantId, List<String> roles) {
        return recipientDirectoryPort.resolveEmailsByRoles(tenantId, roles);
    }
}
