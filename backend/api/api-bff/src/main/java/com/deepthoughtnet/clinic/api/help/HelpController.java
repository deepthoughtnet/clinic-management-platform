package com.deepthoughtnet.clinic.api.help;

import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageRecord;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpSearchResult;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/help")
public class HelpController {
    private final HelpCmsService helpCmsService;

    public HelpController(HelpCmsService helpCmsService) {
        this.helpCmsService = helpCmsService;
    }

    @GetMapping("/page/{pageKey}")
    @PreAuthorize("@permissionChecker.hasAnyRole('PLATFORM_ADMIN', 'CLINIC_ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PHARMACIST', 'PHARMACY_ADMIN', 'PHARMACY_INVENTORY_MANAGER', 'PHARMACY_POS_USER', 'BILLING_USER', 'AUDITOR')")
    public HelpPageRecord getPage(@PathVariable String pageKey, @RequestParam(name = "lang", required = false) String lang) {
        return helpCmsService.getPublicPage(pageKey, lang);
    }

    @GetMapping("/search")
    @PreAuthorize("@permissionChecker.hasAnyRole('PLATFORM_ADMIN', 'CLINIC_ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PHARMACIST', 'PHARMACY_ADMIN', 'PHARMACY_INVENTORY_MANAGER', 'PHARMACY_POS_USER', 'BILLING_USER', 'AUDITOR')")
    public List<HelpSearchResult> search(@RequestParam(name = "q", required = false) String query, @RequestParam(name = "lang", required = false) String lang) {
        return helpCmsService.search(query, lang);
    }
}
