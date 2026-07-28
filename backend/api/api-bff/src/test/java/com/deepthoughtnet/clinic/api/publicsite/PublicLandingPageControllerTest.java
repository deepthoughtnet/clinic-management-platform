package com.deepthoughtnet.clinic.api.publicsite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PublicLandingPageControllerTest {
    @Test
    void exposesPublicLandingRoute() throws NoSuchMethodException {
        RequestMapping mapping = PublicLandingPageController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactly("/api/public/landing");
        assertThat(PublicLandingPageController.class.getMethod("getBySlug", String.class).getAnnotation(GetMapping.class).value()).containsExactly("/{slug}");
    }
}
