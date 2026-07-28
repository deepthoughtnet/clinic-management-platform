package com.deepthoughtnet.clinic.discover.landingpage;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LandingPageModels {
    private LandingPageModels() {
    }

    public record LandingPageThemeRecord(
            String primaryColor,
            String accentColor,
            String typographyPreset,
            String buttonStyle,
            String borderRadiusPreset
    ) {
    }

    public record LandingPageSectionRecord(
            String key,
            boolean enabled,
            int displayOrder,
            String title,
            String description,
            String visibilityRule,
            Map<String, Object> content
    ) {
    }

    public record LandingPageTemplateRecord(
            String templateKey,
            String templateName,
            ProviderType providerType,
            int templateVersion,
            String description,
            List<String> supportedSections,
            List<LandingPageSectionRecord> defaultSections,
            LandingPageThemeRecord defaultTheme
    ) {
    }

    public record LandingPageSnapshotRecord(
            String templateKey,
            int templateVersion,
            LandingPageThemeRecord theme,
            List<LandingPageSectionRecord> sections
    ) {
    }

    public record LandingPageVersionRecord(
            UUID id,
            int versionNumber,
            String templateKey,
            int templateVersion,
            String changeSummary,
            String versionKind,
            OffsetDateTime publishedAt,
            List<String> sectionKeys,
            LandingPageThemeRecord theme
    ) {
    }

    public record LandingPageCompareRecord(
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

    public record LandingPageWorkspaceRecord(
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
            LandingPageSnapshotRecord draft,
            LandingPageSnapshotRecord publishedSnapshot,
            PublicProviderProfileDetailRecord profile,
            List<LandingPageTemplateRecord> templates,
            List<LandingPageVersionRecord> versions
    ) {
    }

    public record LandingPageUpdateRequest(
            Long version,
            String templateKey,
            LandingPageThemeRecord theme,
            List<LandingPageSectionRecord> sections
    ) {
    }

    public record LandingPageRevertRequest(
            int versionNumber
    ) {
    }
}
