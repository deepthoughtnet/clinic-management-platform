package com.deepthoughtnet.clinic.api.publicsite;

import com.deepthoughtnet.clinic.api.discover.landingpage.LandingPageDtoMapper;
import com.deepthoughtnet.clinic.api.discover.landingpage.dto.LandingPageDtos.PublicLandingPageResponse;
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
    public PublicLandingPageResponse getBySlug(@PathVariable String slug) {
        return service.findPublicBySlug(slug)
                .map(LandingPageDtoMapper::toPublicResponse)
                .orElseThrow(() -> new IllegalStateException("landing page not found"));
    }
}
