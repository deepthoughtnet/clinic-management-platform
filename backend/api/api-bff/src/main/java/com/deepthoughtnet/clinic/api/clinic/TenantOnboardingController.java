package com.deepthoughtnet.clinic.api.clinic;

import com.deepthoughtnet.clinic.api.clinic.dto.TenantOnboardingResponse;
import com.deepthoughtnet.clinic.identity.service.TenantOnboardingService;
import com.deepthoughtnet.clinic.identity.service.model.TenantOnboardingRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinic/onboarding")
public class TenantOnboardingController {
    private final TenantOnboardingService tenantOnboardingService;

    public TenantOnboardingController(TenantOnboardingService tenantOnboardingService) {
        this.tenantOnboardingService = tenantOnboardingService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('clinic.read')")
    public TenantOnboardingResponse getStatus() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(tenantOnboardingService.getStatus(tenantId));
    }

    @PostMapping("/complete")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.update')")
    public TenantOnboardingResponse complete() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(tenantOnboardingService.markCompleted(tenantId));
    }

    @PostMapping("/skip")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.update')")
    public TenantOnboardingResponse skip() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(tenantOnboardingService.markSkipped(tenantId));
    }

    private TenantOnboardingResponse toResponse(TenantOnboardingRecord record) {
        return new TenantOnboardingResponse(
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.completed(),
                record.skipped(),
                record.completedAt(),
                record.skippedAt(),
                record.createdAt(),
                record.updatedAt(),
                record.requiresSetup()
        );
    }
}
