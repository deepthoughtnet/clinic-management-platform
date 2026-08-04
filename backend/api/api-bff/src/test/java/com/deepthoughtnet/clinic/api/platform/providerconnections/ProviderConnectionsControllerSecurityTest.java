package com.deepthoughtnet.clinic.api.platform.providerconnections;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class ProviderConnectionsControllerSecurityTest {

    @Test
    void providerConnectionsControllerUsesExpectedRouteAndPermissionGuards() throws Exception {
        RequestMapping requestMapping = ProviderConnectionsController.class.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/platform/provider-connections");
        assertThat(ProviderConnectionsController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.view')");

        Method overview = ProviderConnectionsController.class.getMethod("overview");
        Method publicProfiles = ProviderConnectionsController.class.getMethod("publicProfiles", PublicProfileType.class, String.class, String.class);
        Method publicProfileLifecycle = ProviderConnectionsController.class.getMethod("publicProfileLifecycle", PublicProfileType.class, String.class, String.class);
        Method platformEntities = ProviderConnectionsController.class.getMethod("platformEntities", String.class, String.class);
        Method links = ProviderConnectionsController.class.getMethod("links", String.class, String.class, String.class);
        Method linkDetail = ProviderConnectionsController.class.getMethod("linkDetail", java.util.UUID.class);
        Method audit = ProviderConnectionsController.class.getMethod("audit", java.util.UUID.class);
        Method auditEvents = ProviderConnectionsController.class.getMethod("auditEvents", String.class, String.class, String.class, String.class, String.class);
        Method suggestions = ProviderConnectionsController.class.getMethod("suggestions", String.class);
        Method conflicts = ProviderConnectionsController.class.getMethod("conflicts");
        Method ownerships = ProviderConnectionsController.class.getMethod("ownerships");
        Method rejectSuggestion = ProviderConnectionsController.class.getMethod("rejectSuggestion", String.class, ProviderConnectionsSuggestionDecisionRequest.class);
        Method approveOwnership = ProviderConnectionsController.class.getMethod("approveOwnership", java.util.UUID.class, ProviderConnectionsOwnershipDecisionRequest.class);
        Method rejectOwnership = ProviderConnectionsController.class.getMethod("rejectOwnership", java.util.UUID.class, ProviderConnectionsOwnershipDecisionRequest.class);
        Method disputeOwnership = ProviderConnectionsController.class.getMethod("disputeOwnership", java.util.UUID.class, ProviderConnectionsOwnershipDecisionRequest.class);
        Method revokeOwnership = ProviderConnectionsController.class.getMethod("revokeOwnership", java.util.UUID.class, ProviderConnectionsOwnershipDecisionRequest.class);
        Method propose = ProviderConnectionsController.class.getMethod("propose", ProviderConnectionsLinkProposalRequest.class);
        Method approve = ProviderConnectionsController.class.getMethod("approve", java.util.UUID.class, ProviderConnectionsLinkUpdateRequest.class);
        Method activate = ProviderConnectionsController.class.getMethod("activate", java.util.UUID.class, ProviderConnectionsLinkUpdateRequest.class);
        Method unlink = ProviderConnectionsController.class.getMethod("unlink", java.util.UUID.class, ProviderConnectionsLinkUpdateRequest.class);
        Method relink = ProviderConnectionsController.class.getMethod("relink", java.util.UUID.class, ProviderConnectionsLinkUpdateRequest.class);
        Method reconcile = ProviderConnectionsController.class.getMethod("reconcile", ProviderConnectionsReconcileRequest.class);

        assertThat(overview.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(publicProfiles.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(publicProfileLifecycle.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(platformEntities.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(links.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(linkDetail.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(audit.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(auditEvents.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(suggestions.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(conflicts.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(ownerships.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(rejectSuggestion.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.reject')");
        assertThat(approveOwnership.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.approve')");
        assertThat(rejectOwnership.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.reject')");
        assertThat(disputeOwnership.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.identity_override')");
        assertThat(revokeOwnership.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.unlink')");
        assertThat(propose.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.propose')");
        assertThat(approve.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.approve')");
        assertThat(activate.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.approve')");
        assertThat(unlink.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.unlink')");
        assertThat(relink.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.propose')");
        assertThat(reconcile.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.reconcile')");
    }
}
