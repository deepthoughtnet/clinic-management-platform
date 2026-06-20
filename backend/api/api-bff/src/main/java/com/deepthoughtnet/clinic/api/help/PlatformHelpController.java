package com.deepthoughtnet.clinic.api.help;

import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageLifecycleRequest;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageRecord;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageSummary;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageUpsertRequest;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/platform/help")
public class PlatformHelpController {
    private final HelpCmsService helpCmsService;

    public PlatformHelpController(HelpCmsService helpCmsService) {
        this.helpCmsService = helpCmsService;
    }

    @GetMapping("/pages")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public List<HelpPageSummary> listPages() {
        return helpCmsService.listPages();
    }

    @GetMapping("/page/{pageKey}")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public HelpPageRecord getPage(@PathVariable String pageKey) {
        return helpCmsService.getAdminPage(pageKey);
    }

    @PostMapping("/page")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public HelpPageRecord createPage(@RequestBody HelpPageUpsertRequest request) {
        return helpCmsService.createPage(request, RequestContextHolder.require().appUserId());
    }

    @PutMapping("/page")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public HelpPageRecord updatePage(@RequestBody HelpPageUpsertRequest request) {
        return helpCmsService.updatePage(request, RequestContextHolder.require().appUserId());
    }

    @PostMapping("/publish")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public HelpPageRecord publish(@RequestBody HelpPageLifecycleRequest request) {
        return helpCmsService.publish(request, RequestContextHolder.require().appUserId());
    }

    @PostMapping("/archive")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public HelpPageRecord archive(@RequestBody HelpPageLifecycleRequest request) {
        return helpCmsService.archive(request, RequestContextHolder.require().appUserId());
    }

    @PostMapping("/rollback")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public HelpPageRecord rollback(@RequestBody HelpPageLifecycleRequest request) {
        return helpCmsService.rollback(request, RequestContextHolder.require().appUserId());
    }
}
