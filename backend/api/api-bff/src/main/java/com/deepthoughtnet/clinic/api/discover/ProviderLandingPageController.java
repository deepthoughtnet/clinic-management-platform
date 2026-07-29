package com.deepthoughtnet.clinic.api.discover;

import com.deepthoughtnet.clinic.api.discover.landingpage.LandingPageDtoMapper;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageCompareResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageResponse;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.LandingPageVersionResponse;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageRevertRequest;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageSectionRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageThemeRecord;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageModels.LandingPageUpdateRequest;
import com.deepthoughtnet.clinic.discover.landingpage.LandingPageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
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
        return LandingPageDtoMapper.toProviderResponse(service.getDraft(token));
    }

    @PutMapping
    public LandingPageResponse update(@RequestHeader(TOKEN_HEADER) String token, @Valid @RequestBody LandingPageUpdateDto request) {
        return LandingPageDtoMapper.toProviderResponse(service.update(token, request.toCommand()));
    }

    @GetMapping("/preview")
    public LandingPageResponse preview(@RequestHeader(TOKEN_HEADER) String token) {
        return LandingPageDtoMapper.toProviderResponse(service.preview(token));
    }

    @PostMapping("/publish")
    public LandingPageResponse publish(@RequestHeader(TOKEN_HEADER) String token) {
        return LandingPageDtoMapper.toProviderResponse(service.publish(token));
    }

    @PostMapping("/revert")
    public LandingPageResponse revert(@RequestHeader(TOKEN_HEADER) String token, @Valid @RequestBody LandingPageRevertDto request) {
        return LandingPageDtoMapper.toProviderResponse(service.revert(token, request.toCommand()));
    }

    @GetMapping("/versions")
    public List<LandingPageVersionResponse> versions(@RequestHeader(TOKEN_HEADER) String token) {
        return service.listVersions(token).stream().map(LandingPageDtoMapper::toVersionResponse).toList();
    }

    @GetMapping("/compare/{leftVersion}/{rightVersion}")
    public LandingPageCompareResponse compare(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable int leftVersion,
            @PathVariable int rightVersion
    ) {
        return LandingPageDtoMapper.toCompareResponse(service.compareVersions(token, leftVersion, rightVersion));
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
