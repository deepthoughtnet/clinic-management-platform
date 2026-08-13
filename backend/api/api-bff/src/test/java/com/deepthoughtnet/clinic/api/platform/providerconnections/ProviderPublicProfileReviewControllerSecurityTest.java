package com.deepthoughtnet.clinic.api.platform.providerconnections;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class ProviderPublicProfileReviewControllerSecurityTest {

    @Test
    void providerPublicProfileReviewControllerUsesExpectedRouteAndPermissions() throws Exception {
        RequestMapping requestMapping = ProviderPublicProfileReviewController.class.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/platform/provider-connections/public-profile-reviews");
        assertThat(ProviderPublicProfileReviewController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.view')");

        Method list = ProviderPublicProfileReviewController.class.getMethod("list", String.class, String.class, String.class);
        Method detail = ProviderPublicProfileReviewController.class.getMethod("detail", String.class);
        Method start = ProviderPublicProfileReviewController.class.getMethod("start", String.class, ProviderPublicProfileReviewController.ReviewCommandRequest.class);
        Method requestChanges = ProviderPublicProfileReviewController.class.getMethod("requestChanges", String.class, ProviderPublicProfileReviewController.ReviewCommandRequest.class);
        Method addFinding = ProviderPublicProfileReviewController.class.getMethod("addFinding", String.class, ProviderPublicProfileReviewController.ReviewFindingRequest.class);
        Method approve = ProviderPublicProfileReviewController.class.getMethod("approve", String.class, ProviderPublicProfileReviewController.ReviewCommandRequest.class);
        Method reject = ProviderPublicProfileReviewController.class.getMethod("reject", String.class, ProviderPublicProfileReviewController.ReviewCommandRequest.class);
        Method publish = ProviderPublicProfileReviewController.class.getMethod("publish", String.class, ProviderPublicProfileReviewController.ReviewCommandRequest.class);
        Method unpublish = ProviderPublicProfileReviewController.class.getMethod("unpublish", String.class, ProviderPublicProfileReviewController.ReviewCommandRequest.class);

        assertThat(list.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(detail.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(start.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.approve')");
        assertThat(requestChanges.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.approve')");
        assertThat(addFinding.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.approve')");
        assertThat(approve.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.approve')");
        assertThat(reject.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.reject')");
        assertThat(publish.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.approve')");
        assertThat(unpublish.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('platform.provider_connection.unlink')");
    }
}
