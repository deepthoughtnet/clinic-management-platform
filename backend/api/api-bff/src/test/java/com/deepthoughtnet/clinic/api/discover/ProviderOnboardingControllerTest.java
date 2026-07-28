package com.deepthoughtnet.clinic.api.discover;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ProviderOnboardingControllerTest {
    @Test
    void exposesProviderRegistrationApiUnderDedicatedPublicRegistrationBoundary() {
        RequestMapping mapping = ProviderOnboardingController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactly("/api/provider-registration/providers");
    }

    @Test
    void exposesDraftResumeUpdatePreviewSubmissionAndDocumentUploadRoutes() throws NoSuchMethodException {
        assertThat(ProviderOnboardingController.class.getMethod("create", ProviderOnboardingController.CreateProviderRequest.class).getAnnotation(PostMapping.class).value()).isEmpty();
        assertThat(ProviderOnboardingController.class.getMethod("me", String.class).getAnnotation(GetMapping.class).value()).containsExactly("/me");
        assertThat(ProviderOnboardingController.class.getMethod("dashboard", String.class).getAnnotation(GetMapping.class).value()).containsExactly("/me/dashboard");
        assertThat(ProviderOnboardingController.class.getMethod("get", java.util.UUID.class, String.class).getAnnotation(GetMapping.class).value()).containsExactly("/{id}");
        assertThat(ProviderOnboardingController.class.getMethod("completion", java.util.UUID.class, String.class).getAnnotation(GetMapping.class).value()).containsExactly("/{id}/completion");
        assertThat(ProviderOnboardingController.class.getMethod("statusHistory", java.util.UUID.class, String.class).getAnnotation(GetMapping.class).value()).containsExactly("/{id}/status-history");
        assertThat(ProviderOnboardingController.class.getMethod("changeRequests", java.util.UUID.class, String.class).getAnnotation(GetMapping.class).value()).containsExactly("/{id}/change-requests");
        assertThat(ProviderOnboardingController.class.getMethod("update", java.util.UUID.class, String.class, ProviderOnboardingController.UpdateProviderRequest.class).getAnnotation(PutMapping.class).value()).containsExactly("/{id}");
        assertThat(ProviderOnboardingController.class.getMethod("submit", java.util.UUID.class, String.class).getAnnotation(PostMapping.class).value()).containsExactly("/{id}/submit");
        assertThat(ProviderOnboardingController.class.getMethod("resubmit", java.util.UUID.class, String.class, ProviderOnboardingController.ResubmitRequest.class).getAnnotation(PostMapping.class).value()).containsExactly("/{id}/resubmit");
        assertThat(ProviderOnboardingController.class.getMethod("preview", java.util.UUID.class, String.class).getAnnotation(GetMapping.class).value()).containsExactly("/{id}/preview");
        assertThat(ProviderOnboardingController.class.getMethod("startReview", java.util.UUID.class, ProviderOnboardingController.ReviewTransitionRequest.class).getAnnotation(PostMapping.class).value()).containsExactly("/{id}/review/start");
        assertThat(ProviderOnboardingController.class.getMethod("requestChanges", java.util.UUID.class, ProviderOnboardingController.ReviewTransitionRequest.class).getAnnotation(PostMapping.class).value()).containsExactly("/{id}/review/changes-requested");
        assertThat(ProviderOnboardingController.class.getMethod("approve", java.util.UUID.class, ProviderOnboardingController.ReviewTransitionRequest.class).getAnnotation(PostMapping.class).value()).containsExactly("/{id}/review/approve");

        PostMapping uploadMapping = Arrays.stream(ProviderOnboardingController.class.getMethods())
                .filter(method -> method.getName().equals("uploadDocument"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(PostMapping.class);
        assertThat(uploadMapping.value()).containsExactly("/{id}/documents");
        assertThat(uploadMapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    @Test
    void reviewerTransitionRoutesRequirePrivilegedPlatformOrAdminRole() throws NoSuchMethodException {
        for (String methodName : java.util.List.of("startReview", "requestChanges", "approve")) {
            PreAuthorize annotation = ProviderOnboardingController.class
                    .getMethod(methodName, java.util.UUID.class, ProviderOnboardingController.ReviewTransitionRequest.class)
                    .getAnnotation(PreAuthorize.class);

            assertThat(annotation.value()).isEqualTo("hasAnyRole('PLATFORM_ADMIN','ADMIN')");
        }
    }
}
