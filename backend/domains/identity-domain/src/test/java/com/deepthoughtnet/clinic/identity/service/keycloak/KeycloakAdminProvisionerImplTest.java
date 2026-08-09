package com.deepthoughtnet.clinic.identity.service.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.service.TenantIdentityConflictException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

class KeycloakAdminProvisionerImplTest {

    @Test
    void createOrGetTenantUserIdRejectsExistingIdentityWhenUsernameAndEmailResolveToSameUser() {
        UUID tenantId = UUID.randomUUID();
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);
        when(keycloak.realm("realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        UserRepresentation existing = user("kc-123", "priya.nair@example.com", "Priya.Nair");
        existing.setAttributes(Map.of("tenant_id", List.of(tenantId.toString())));
        when(usersResource.search("priya.nair@example.com", true)).thenReturn(List.of(existing));
        when(usersResource.search("Priya.Nair", true)).thenReturn(List.of(existing));

        KeycloakAdminProvisionerImpl provisioner = new KeycloakAdminProvisionerImpl(keycloak, "realm");

        TenantIdentityConflictException ex = assertThrows(TenantIdentityConflictException.class, () ->
                provisioner.createOrGetTenantUserId(
                        tenantId,
                        "  priya.nair@example.com  ",
                        "  Priya.Nair  ",
                        "Priya",
                        "Nair",
                        "Priya Nair",
                        null,
                        true
                ));

        assertEquals(2, ex.conflicts().size());
        assertEquals("EMAIL", ex.conflicts().get(0).field().name());
        assertEquals("USERNAME", ex.conflicts().get(1).field().name());
        verify(usersResource, never()).create(any());
    }

    @Test
    void createOrGetTenantUserIdRejectsAmbiguousDuplicateEmailMatches() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);
        when(keycloak.realm("realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        UserRepresentation first = user("kc-1", "priya.nair@example.com", "Priya.One");
        UserRepresentation second = user("kc-2", "priya.nair@example.com", "Priya.Two");
        when(usersResource.search("priya.nair@example.com", true)).thenReturn(List.of(first, second));

        KeycloakAdminProvisionerImpl provisioner = new KeycloakAdminProvisionerImpl(keycloak, "realm");

        TenantIdentityConflictException ex = assertThrows(TenantIdentityConflictException.class, () ->
                provisioner.createOrGetTenantUserId(
                        UUID.randomUUID(),
                        "priya.nair@example.com",
                        null,
                        "Priya",
                        "Nair",
                        "Priya Nair",
                        null,
                        true
                ));

        assertEquals(1, ex.conflicts().size());
        assertEquals("EMAIL_ALREADY_IN_USE", ex.conflicts().get(0).code());
        verify(usersResource, never()).create(any());
    }

    @Test
    void createOrGetTenantUserIdReturnsBothConflictsWhenUsernameAndEmailAreAlreadyUsed() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);
        when(keycloak.realm("realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        UserRepresentation emailMatch = user("kc-1", "priya.nair@example.com", "Priya.One");
        UserRepresentation usernameMatch = user("kc-2", "priya.two@example.com", "Priya.Nair");
        when(usersResource.search("priya.nair@example.com", true)).thenReturn(List.of(emailMatch));
        when(usersResource.search("Priya.Nair", true)).thenReturn(List.of(usernameMatch));

        KeycloakAdminProvisionerImpl provisioner = new KeycloakAdminProvisionerImpl(keycloak, "realm");

        TenantIdentityConflictException ex = assertThrows(TenantIdentityConflictException.class, () ->
                provisioner.createOrGetTenantUserId(
                        UUID.randomUUID(),
                        "priya.nair@example.com",
                        "Priya.Nair",
                        "Priya",
                        "Nair",
                        "Priya Nair",
                        null,
                        true
                ));

        assertEquals(2, ex.conflicts().size());
        assertEquals("EMAIL", ex.conflicts().get(0).field().name());
        assertEquals("USERNAME", ex.conflicts().get(1).field().name());
        verify(usersResource, never()).create(any());
    }

    @Test
    void updateTenantUserIdentityAllowsUnchangedCurrentIdentity() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);
        org.keycloak.admin.client.resource.UserResource userResource = mock(org.keycloak.admin.client.resource.UserResource.class);
        when(keycloak.realm("realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("kc-123")).thenReturn(userResource);

        UserRepresentation existing = user("kc-123", "priya.nair@example.com", "Priya.Nair");
        existing.setEmailVerified(Boolean.TRUE);
        when(userResource.toRepresentation()).thenReturn(existing);
        when(usersResource.search("priya.nair@example.com", true)).thenReturn(List.of(existing));
        when(usersResource.search("Priya.Nair", true)).thenReturn(List.of(existing));

        KeycloakAdminProvisionerImpl provisioner = new KeycloakAdminProvisionerImpl(keycloak, "realm");

        assertEquals("kc-123", provisioner.findUserIdByEmailOrUsername("priya.nair@example.com", "Priya.Nair"));
        provisioner.updateTenantUserIdentity("kc-123", "priya.nair@example.com", "Priya.Nair", null, null, true);
        verify(userResource, never()).update(any());
    }

    private UserRepresentation user(String id, String email, String username) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(username);
        return user;
    }
}
