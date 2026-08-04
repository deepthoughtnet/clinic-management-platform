package com.deepthoughtnet.clinic.api.discover;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ProviderLandingPageControllerTest {
    @Test
    void exposesDedicatedProviderLandingPageApi() {
        RequestMapping mapping = ProviderLandingPageController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactly("/api/provider/landing-page");
    }

    @Test
    void exposesDraftPreviewPublishRevertVersionAndCompareRoutes() throws NoSuchMethodException {
        assertThat(ProviderLandingPageController.class.getMethod("get", String.class).getAnnotation(GetMapping.class).value()).isEmpty();
        assertThat(ProviderLandingPageController.class.getMethod("update", String.class, ProviderLandingPageController.LandingPageUpdateDto.class).getAnnotation(PutMapping.class).value()).isEmpty();
        assertThat(ProviderLandingPageController.class.getMethod("preview", String.class).getAnnotation(GetMapping.class).value()).containsExactly("/preview");
        assertThat(ProviderLandingPageController.class.getMethod("publish", String.class).getAnnotation(PostMapping.class).value()).containsExactly("/publish");
        assertThat(ProviderLandingPageController.class.getMethod("revert", String.class, ProviderLandingPageController.LandingPageRevertDto.class).getAnnotation(PostMapping.class).value()).containsExactly("/revert");
        assertThat(ProviderLandingPageController.class.getMethod("versions", String.class).getAnnotation(GetMapping.class).value()).containsExactly("/versions");
        assertThat(ProviderLandingPageController.class.getMethod("compare", String.class, int.class, int.class).getAnnotation(GetMapping.class).value()).containsExactly("/compare/{leftVersion}/{rightVersion}");
    }

    @Test
    void requestDtosPreserveStructuredEditingOnly() throws NoSuchMethodException {
        assertThat(List.of(ProviderLandingPageController.LandingPageThemeRequest.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .containsExactly("primaryColor", "accentColor", "typographyPreset", "buttonStyle", "borderRadiusPreset");
        assertThat(List.of(ProviderLandingPageController.LandingPageSectionRequest.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .containsExactly("key", "enabled", "displayOrder", "title", "description", "visibilityRule", "content");
    }

    @Test
    void responseDtosExposeReadinessAndAllowedActions() {
        assertThat(List.of(com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageResponse.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .contains("pageMode", "publicationReadiness", "allowedActions");
        assertThat(List.of(com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.PublicLandingPageResponse.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .contains("pageMode", "publicationReadiness", "allowedActions");
    }
}
