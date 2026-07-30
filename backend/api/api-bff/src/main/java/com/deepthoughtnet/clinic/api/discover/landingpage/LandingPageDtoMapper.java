package com.deepthoughtnet.clinic.api.discover.landingpage;

import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageCompareResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageSectionResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageSnapshotResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageTemplateResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageThemeResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageVersionResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.PublicLandingPageResponse;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageCompareRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageSectionRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageSnapshotRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageTemplateRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageThemeRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageVersionRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageWorkspaceRecord;

public final class LandingPageDtoMapper {
    private LandingPageDtoMapper() {
    }

    public static LandingPageResponse toProviderResponse(LandingPageWorkspaceRecord record) {
        return new LandingPageResponse(
                record.providerId(),
                record.providerType(),
                record.applicationStatus(),
                record.displayName(),
                record.canonicalSlug(),
                record.publicPath(),
                record.editable(),
                record.published(),
                record.draftVersionNumber(),
                record.publishedVersionNumber(),
                record.publishedAt(),
                record.draft() == null ? null : toSnapshotResponse(record.draft()),
                record.publishedSnapshot() == null ? null : toSnapshotResponse(record.publishedSnapshot()),
                record.profile(),
                record.templates().stream().map(LandingPageDtoMapper::toTemplateResponse).toList(),
                record.versions().stream().map(LandingPageDtoMapper::toVersionResponse).toList()
        );
    }

    public static PublicLandingPageResponse toPublicResponse(LandingPageWorkspaceRecord record) {
        return new PublicLandingPageResponse(
                record.providerId(),
                record.providerType(),
                record.displayName(),
                record.canonicalSlug(),
                record.publicPath(),
                record.published(),
                record.publishedVersionNumber(),
                record.publishedAt(),
                record.publishedSnapshot() == null ? null : toSnapshotResponse(record.publishedSnapshot()),
                record.profile()
        );
    }

    public static LandingPageCompareResponse toCompareResponse(LandingPageCompareRecord record) {
        return new LandingPageCompareResponse(
                record.leftVersion(),
                record.rightVersion(),
                record.templateChanged(),
                record.themeChanged(),
                record.sectionOrderChanged(),
                record.addedSections(),
                record.removedSections(),
                record.changedSections()
        );
    }

    private static LandingPageSnapshotResponse toSnapshotResponse(LandingPageSnapshotRecord record) {
        return new LandingPageSnapshotResponse(
                record.templateKey(),
                record.templateVersion(),
                toThemeResponse(record.theme()),
                record.sections().stream().map(LandingPageDtoMapper::toSectionResponse).toList()
        );
    }

    private static LandingPageTemplateResponse toTemplateResponse(LandingPageTemplateRecord record) {
        return new LandingPageTemplateResponse(
                record.templateKey(),
                record.templateName(),
                record.providerType(),
                record.templateVersion(),
                record.description(),
                record.supportedSections(),
                record.defaultSections().stream().map(LandingPageDtoMapper::toSectionResponse).toList(),
                toThemeResponse(record.defaultTheme())
        );
    }

    public static LandingPageVersionResponse toVersionResponse(LandingPageVersionRecord record) {
        return new LandingPageVersionResponse(
                record.id(),
                record.versionNumber(),
                record.templateKey(),
                record.templateVersion(),
                record.changeSummary(),
                record.versionKind(),
                record.publishedAt(),
                record.sectionKeys(),
                toThemeResponse(record.theme())
        );
    }

    private static LandingPageThemeResponse toThemeResponse(LandingPageThemeRecord record) {
        return new LandingPageThemeResponse(
                record.primaryColor(),
                record.accentColor(),
                record.typographyPreset(),
                record.buttonStyle(),
                record.borderRadiusPreset()
        );
    }

    private static LandingPageSectionResponse toSectionResponse(LandingPageSectionRecord record) {
        return new LandingPageSectionResponse(
                record.key(),
                record.enabled(),
                record.displayOrder(),
                record.title(),
                record.description(),
                record.visibilityRule(),
                record.content()
        );
    }
}
