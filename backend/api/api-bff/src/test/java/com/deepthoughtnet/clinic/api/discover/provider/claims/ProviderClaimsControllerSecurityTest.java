package com.deepthoughtnet.clinic.api.discover.provider.claims;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class ProviderClaimsControllerSecurityTest {
    @Test
    void providerClaimsControllerUsesExpectedRoutesAndGuards() throws Exception {
        RequestMapping requestMapping = ProviderClaimsController.class.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/provider/claims");

        Method review = ProviderClaimsController.class.getMethod("review", org.springframework.security.core.Authentication.class, String.class);
        Method submit = ProviderClaimsController.class.getMethod("submit", org.springframework.security.core.Authentication.class, String.class, ProviderClaimsController.ProviderClaimSubmissionRequest.class);

        assertThat(ProviderClaimsController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('PROVIDER')");
        assertThat(review.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(submit.getAnnotation(PreAuthorize.class)).isNull();
    }
}
