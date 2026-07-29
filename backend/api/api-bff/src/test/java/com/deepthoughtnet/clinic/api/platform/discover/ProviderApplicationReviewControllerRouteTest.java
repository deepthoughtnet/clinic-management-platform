package com.deepthoughtnet.clinic.api.platform.discover;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ProviderApplicationReviewControllerRouteTest {
    @Test
    void documentContentUsesReviewerContentRouteAndPermission() throws Exception {
        RequestMapping requestMapping = ProviderApplicationReviewController.class.getAnnotation(RequestMapping.class);
        Method documentContent = ProviderApplicationReviewController.class.getMethod("documentContent", String.class, java.util.UUID.class);
        GetMapping getMapping = documentContent.getAnnotation(GetMapping.class);
        PreAuthorize preAuthorize = documentContent.getAnnotation(PreAuthorize.class);

        assertThat(requestMapping.value()).containsExactly("/api/platform/discover/provider-applications");
        assertThat(getMapping.value()).containsExactly("/{referenceNumber}/documents/{documentId}/content");
        assertThat(preAuthorize.value()).isEqualTo("@permissionChecker.hasPermission('discover.provider.application.history.view')");
    }
}
