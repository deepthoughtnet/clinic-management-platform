package com.deepthoughtnet.clinic.notificationcenter.service;

import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationAudience;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCenterAudienceResolverTest {

    @Mock
    private TenantUserManagementService tenantUserManagementService;

    @Test
    void resolvesActiveUsersForPermissionRoleAndUserAudiences() {
        NotificationCenterAudienceResolver resolver = new NotificationCenterAudienceResolver(tenantUserManagementService);
        UUID tenantId = UUID.randomUUID();
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T10:15:30Z");

        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(
                new TenantUserRecord(aliceId, tenantId, "kc-alice", "alice@example.com", "alice", "CLINIC", "Alice Rao", "ACTIVE", "DOCTOR", "ACTIVE", now, now, "EXISTING"),
                new TenantUserRecord(bobId, tenantId, "kc-bob", "bob@example.com", "bob", "CLINIC", "Bob Patel", "ACTIVE", "CLINIC_ADMIN", "ACTIVE", now, now, "EXISTING"),
                new TenantUserRecord(inactiveId, tenantId, "kc-inactive", "inactive@example.com", "inactive", "CLINIC", "In Active", "INACTIVE", "DOCTOR", "ACTIVE", now, now, "EXISTING")
        ));

        var recipients = resolver.resolve(tenantId, List.of(
                NotificationAudience.user(aliceId.toString()),
                NotificationAudience.permission("appointment.manage"),
                NotificationAudience.role("CLINIC_ADMIN"),
                NotificationAudience.tenantAdmin()
        ));

        assertThat(recipients).hasSize(2);
        assertThat(recipients.get(0).appUserId()).isEqualTo(aliceId);
        assertThat(recipients.get(0).displayName()).isEqualTo("Alice Rao");
        assertThat(recipients.get(0).matchedAudience()).contains("USER");
        assertThat(recipients.get(1).appUserId()).isEqualTo(bobId);
        assertThat(recipients.get(1).displayName()).isEqualTo("Bob Patel");
        assertThat(recipients.get(1).matchedAudience()).contains("ROLE");
    }
}
