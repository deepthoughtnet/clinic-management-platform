package com.deepthoughtnet.clinic.api.discover.landingpage.dto;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LandingPageDtos {
    private LandingPageDtos() {
    }

    public record LandingPageThemeResponse(
            String primaryColor,
            String accentColor,
            String typographyPreset,
            String buttonStyle,
            String borderRadiusPreset
    ) {
    }

    public record LandingPageSectionResponse(
            String key,
            boolean enabled,
            int displayOrder,
            String title,
            String description,
            String visibilityRule,
            Map<String, Object> content
    ) {
    }

    public record LandingPageSnapshotResponse(
            String templateKey,
            int templateVersion,
            LandingPageThemeResponse theme,
            List<LandingPageSectionResponse> sections
    ) {
    }

    public record LandingPageTemplateResponse(
            String templateKey,
            String templateName,
            ProviderType providerType,
            int templateVersion,
            String description,
            List<String> supportedSections,
            List<LandingPageSectionResponse> defaultSections,
            LandingPageThemeResponse defaultTheme
    ) {
    }

    public record LandingPageVersionResponse(
            UUID id,
            int versionNumber,
            String templateKey,
            int templateVersion,
            String changeSummary,
            String versionKind,
            OffsetDateTime publishedAt,
            List<String> sectionKeys,
            LandingPageThemeResponse theme
    ) {
    }

    public record LandingPageCompareResponse(
            int leftVersion,
            int rightVersion,
            boolean templateChanged,
            boolean themeChanged,
            boolean sectionOrderChanged,
            List<String> addedSections,
            List<String> removedSections,
            List<String> changedSections
    ) {
    }

    public record LandingPageResponse(
            UUID providerId,
            ProviderType providerType,
            String displayName,
            String canonicalSlug,
            String publicPath,
            boolean editable,
            boolean published,
            int draftVersionNumber,
            Integer publishedVersionNumber,
            OffsetDateTime publishedAt,
            LandingPageSnapshotResponse draft,
            LandingPageSnapshotResponse publishedSnapshot,
            PublicProviderProfileDetailRecord profile,
            List<LandingPageTemplateResponse> templates,
            List<LandingPageVersionResponse> versions
    ) {
    }

    public record PublicLandingPageResponse(
            UUID providerId,
            ProviderType providerType,
            String displayName,
            String canonicalSlug,
            String publicPath,
            boolean published,
            Integer publishedVersionNumber,
            OffsetDateTime publishedAt,
            LandingPageSnapshotResponse publishedSnapshot,
            PublicProviderProfileDetailRecord profile
    ) {
    }
}
