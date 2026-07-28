package com.deepthoughtnet.clinic.api.publicsite;

import com.deepthoughtnet.clinic.api.discover.ProviderLandingPageController.LandingPageResponse;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/landing")
public class PublicLandingPageController {
    private final LandingPageService service;

    public PublicLandingPageController(LandingPageService service) {
        this.service = service;
    }

    @GetMapping("/{slug}")
    public LandingPageResponse getBySlug(@PathVariable String slug) {
        return service.findPublicBySlug(slug)
                .map(record -> new ProviderLandingPageController.LandingPageResponse(
                        record.providerId(),
                        record.providerType(),
                        record.displayName(),
                        record.canonicalSlug(),
                        record.publicPath(),
                        record.editable(),
                        record.published(),
                        record.draftVersionNumber(),
                        record.publishedVersionNumber(),
                        record.publishedAt(),
                        record.draft() == null ? null : new ProviderLandingPageController.LandingPageSnapshotResponse(
                                record.draft().templateKey(),
                                record.draft().templateVersion(),
                                new ProviderLandingPageController.LandingPageThemeResponse(
                                        record.draft().theme().primaryColor(),
                                        record.draft().theme().accentColor(),
                                        record.draft().theme().typographyPreset(),
                                        record.draft().theme().buttonStyle(),
                                        record.draft().theme().borderRadiusPreset()
                                ),
                                record.draft().sections().stream().map(section -> new ProviderLandingPageController.LandingPageSectionResponse(
                                        section.key(),
                                        section.enabled(),
                                        section.displayOrder(),
                                        section.title(),
                                        section.description(),
                                        section.visibilityRule(),
                                        section.content()
                                )).toList()
                        ),
                        record.publishedSnapshot() == null ? null : new ProviderLandingPageController.LandingPageSnapshotResponse(
                                record.publishedSnapshot().templateKey(),
                                record.publishedSnapshot().templateVersion(),
                                new ProviderLandingPageController.LandingPageThemeResponse(
                                        record.publishedSnapshot().theme().primaryColor(),
                                        record.publishedSnapshot().theme().accentColor(),
                                        record.publishedSnapshot().theme().typographyPreset(),
                                        record.publishedSnapshot().theme().buttonStyle(),
                                        record.publishedSnapshot().theme().borderRadiusPreset()
                                ),
                                record.publishedSnapshot().sections().stream().map(section -> new ProviderLandingPageController.LandingPageSectionResponse(
                                        section.key(),
                                        section.enabled(),
                                        section.displayOrder(),
                                        section.title(),
                                        section.description(),
                                        section.visibilityRule(),
                                        section.content()
                                )).toList()
                        ),
                        record.profile(),
                        record.templates().stream().map(template -> new ProviderLandingPageController.LandingPageTemplateResponse(
                                template.templateKey(),
                                template.templateName(),
                                template.providerType(),
                                template.templateVersion(),
                                template.description(),
                                template.supportedSections(),
                                template.defaultSections().stream().map(section -> new ProviderLandingPageController.LandingPageSectionResponse(
                                        section.key(),
                                        section.enabled(),
                                        section.displayOrder(),
                                        section.title(),
                                        section.description(),
                                        section.visibilityRule(),
                                        section.content()
                                )).toList(),
                                new ProviderLandingPageController.LandingPageThemeResponse(
                                        template.defaultTheme().primaryColor(),
                                        template.defaultTheme().accentColor(),
                                        template.defaultTheme().typographyPreset(),
                                        template.defaultTheme().buttonStyle(),
                                        template.defaultTheme().borderRadiusPreset()
                                )
                        )).toList(),
                        record.versions().stream().map(version -> new ProviderLandingPageController.LandingPageVersionResponse(
                                version.id(),
                                version.versionNumber(),
                                version.templateKey(),
                                version.templateVersion(),
                                version.changeSummary(),
                                version.versionKind(),
                                version.publishedAt(),
                                version.sectionKeys(),
                                new ProviderLandingPageController.LandingPageThemeResponse(
                                        version.theme().primaryColor(),
                                        version.theme().accentColor(),
                                        version.theme().typographyPreset(),
                                        version.theme().buttonStyle(),
                                        version.theme().borderRadiusPreset()
                                )
                        )).toList()
                ))
                .orElseThrow(() -> new IllegalStateException("landing page not found"));
    }
}
