package com.deepthoughtnet.clinic.api.discover;

import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageCompareRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageRevertRequest;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageSectionRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageSnapshotRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageTemplateRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageThemeRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageUpdateRequest;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageVersionRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageService;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/landing-page")
public class ProviderLandingPageController {
    private static final String TOKEN_HEADER = "X-Provider-Onboarding-Token";
    private final LandingPageService service;

    public ProviderLandingPageController(LandingPageService service) {
        this.service = service;
    }

    @GetMapping
    public LandingPageResponse get(@RequestHeader(TOKEN_HEADER) String token) {
        return toResponse(service.getDraft(token));
    }

    @PutMapping
    public LandingPageResponse update(@RequestHeader(TOKEN_HEADER) String token, @Valid @RequestBody LandingPageUpdateDto request) {
        return toResponse(service.update(token, request.toCommand()));
    }

    @GetMapping("/preview")
    public LandingPageResponse preview(@RequestHeader(TOKEN_HEADER) String token) {
        return toResponse(service.preview(token));
    }

    @PostMapping("/publish")
    public LandingPageResponse publish(@RequestHeader(TOKEN_HEADER) String token) {
        return toResponse(service.publish(token));
    }

    @PostMapping("/revert")
    public LandingPageResponse revert(@RequestHeader(TOKEN_HEADER) String token, @Valid @RequestBody LandingPageRevertDto request) {
        return toResponse(service.revert(token, request.toCommand()));
    }

    @GetMapping("/versions")
    public List<LandingPageVersionResponse> versions(@RequestHeader(TOKEN_HEADER) String token) {
        return service.listVersions(token).stream().map(this::toVersionResponse).toList();
    }

    @GetMapping("/compare/{leftVersion}/{rightVersion}")
    public LandingPageCompareResponse compare(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable int leftVersion,
            @PathVariable int rightVersion
    ) {
        return toResponse(service.compareVersions(token, leftVersion, rightVersion));
    }

    private LandingPageResponse toResponse(LandingPageWorkspaceRecord record) {
        return new LandingPageResponse(
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
                toSnapshotResponse(record.draft()),
                record.publishedSnapshot() == null ? null : toSnapshotResponse(record.publishedSnapshot()),
                record.profile(),
                record.templates().stream().map(this::toTemplateResponse).toList(),
                record.versions().stream().map(this::toVersionResponse).toList()
        );
    }

    private LandingPageSnapshotResponse toSnapshotResponse(LandingPageSnapshotRecord record) {
        return new LandingPageSnapshotResponse(
                record.templateKey(),
                record.templateVersion(),
                toThemeResponse(record.theme()),
                record.sections().stream().map(this::toSectionResponse).toList()
        );
    }

    private LandingPageTemplateResponse toTemplateResponse(LandingPageTemplateRecord record) {
        return new LandingPageTemplateResponse(
                record.templateKey(),
                record.templateName(),
                record.providerType(),
                record.templateVersion(),
                record.description(),
                record.supportedSections(),
                record.defaultSections().stream().map(this::toSectionResponse).toList(),
                toThemeResponse(record.defaultTheme())
        );
    }

    private LandingPageVersionResponse toVersionResponse(LandingPageVersionRecord record) {
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

    private LandingPageThemeResponse toThemeResponse(LandingPageThemeRecord record) {
        return new LandingPageThemeResponse(
                record.primaryColor(),
                record.accentColor(),
                record.typographyPreset(),
                record.buttonStyle(),
                record.borderRadiusPreset()
        );
    }

    private LandingPageSectionResponse toSectionResponse(LandingPageSectionRecord record) {
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

    private LandingPageCompareResponse toResponse(LandingPageCompareRecord record) {
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

    public record LandingPageUpdateDto(
            Long version,
            String templateKey,
            LandingPageThemeRequest theme,
            List<LandingPageSectionRequest> sections
    ) {
        LandingPageUpdateRequest toCommand() {
            return new LandingPageUpdateRequest(
                    version,
                    templateKey,
                    theme == null ? null : theme.toRecord(),
                    sections == null ? null : sections.stream().map(LandingPageSectionRequest::toRecord).toList()
            );
        }
    }

    public record LandingPageRevertDto(@NotNull Integer versionNumber) {
        LandingPageRevertRequest toCommand() {
            return new LandingPageRevertRequest(versionNumber);
        }
    }

    public record LandingPageThemeRequest(
            String primaryColor,
            String accentColor,
            String typographyPreset,
            String buttonStyle,
            String borderRadiusPreset
    ) {
        LandingPageThemeRecord toRecord() {
            return new LandingPageThemeRecord(primaryColor, accentColor, typographyPreset, buttonStyle, borderRadiusPreset);
        }
    }

    public record LandingPageSectionRequest(
            @NotBlank String key,
            boolean enabled,
            int displayOrder,
            String title,
            String description,
            String visibilityRule,
            Map<String, Object> content
    ) {
        LandingPageSectionRecord toRecord() {
            return new LandingPageSectionRecord(key, enabled, displayOrder, title, description, visibilityRule, content);
        }
    }
}
