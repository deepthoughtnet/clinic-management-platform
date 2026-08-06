package com.deepthoughtnet.clinic.api.discover.provider.publicprofile;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class ProviderPublicProfileModerationControllerSecurityTest {

    @Test
    void providerPublicProfileModerationControllerUsesExpectedRouteAndProviderMediaEndpoint() throws Exception {
        RequestMapping requestMapping = ProviderPublicProfileModerationController.class.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/provider/public-profiles");
        assertThat(ProviderPublicProfileModerationController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('PROVIDER')");

        Method moderation = ProviderPublicProfileModerationController.class.getMethod("moderation", org.springframework.security.core.Authentication.class, String.class);
        Method review = ProviderPublicProfileModerationController.class.getMethod("review", org.springframework.security.core.Authentication.class, String.class);
        Method submissionMediaContent = ProviderPublicProfileModerationController.class.getMethod(
                "submissionMediaContent",
                org.springframework.security.core.Authentication.class,
                String.class,
                String.class,
                String.class
        );
        Method submit = ProviderPublicProfileModerationController.class.getMethod("submit", org.springframework.security.core.Authentication.class, String.class);
        Method withdraw = ProviderPublicProfileModerationController.class.getMethod("withdraw", org.springframework.security.core.Authentication.class, String.class, String.class, java.util.Map.class);
        Method feedback = ProviderPublicProfileModerationController.class.getMethod("feedback", org.springframework.security.core.Authentication.class, String.class);

        assertThat(moderation.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(review.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(submissionMediaContent.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(submit.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(withdraw.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(feedback.getAnnotation(PreAuthorize.class)).isNull();
    }
}
