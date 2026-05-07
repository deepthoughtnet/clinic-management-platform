package com.deepthoughtnet.clinic.api.platform;

import com.deepthoughtnet.clinic.api.platform.service.PlatformTenantService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformTenantController {

    private final PlatformTenantService platformTenantService;

    public PlatformTenantController(PlatformTenantService platformTenantService) {
        this.platformTenantService = platformTenantService;
    }

    @GetMapping("/tenants")
    public List<PlatformTenantRecord> listTenants() {
        return platformTenantService.listTenants();
    }

    @GetMapping("/tenants/{tenantId}")
    public PlatformTenantService.PlatformTenantDetail getTenant(@PathVariable UUID tenantId) {
        return platformTenantService.getTenant(tenantId);
    }

    @PostMapping("/tenants")
    public PlatformTenantService.PlatformTenantDetail createTenant(@RequestBody CreateTenantRequest request) {
        return platformTenantService.createTenant(new PlatformTenantService.CreateTenantCommand(
                request.clinicName(),
                request.tenantCode(),
                request.displayName(),
                request.city(),
                request.state(),
                request.country(),
                request.postalCode(),
                request.phone(),
                request.clinicEmail(),
                request.addressLine1(),
                request.addressLine2(),
                request.planId(),
                request.modules(),
                request.adminEmail(),
                request.adminFirstName(),
                request.adminLastName(),
                request.tempPassword()
        ));
    }

    @PatchMapping("/tenants/{tenantId}/status")
    public PlatformTenantRecord updateStatus(@PathVariable UUID tenantId, @RequestBody TenantStatusRequest request) {
        return platformTenantService.updateStatus(tenantId, request.active());
    }

    @PutMapping("/tenants/{tenantId}/plan")
    public PlatformTenantRecord updatePlan(@PathVariable UUID tenantId, @RequestBody TenantPlanUpdateRequest request) {
        return platformTenantService.updatePlan(tenantId, request.planId());
    }

    @PutMapping("/tenants/{tenantId}/modules")
    public PlatformTenantRecord updateModules(@PathVariable UUID tenantId, @RequestBody Map<String, Boolean> modules) {
        return platformTenantService.updateModules(tenantId, modules);
    }

    @PostMapping("/tenants/{tenantId}/admin-user")
    public TenantUserRecord createAdminUser(@PathVariable UUID tenantId, @RequestBody TenantAdminUserRequest request) {
        return platformTenantService.createAdminUser(
                tenantId,
                new PlatformTenantService.CreateAdminUserCommand(
                        request.email(),
                        request.firstName(),
                        request.lastName(),
                        request.tempPassword()
                )
        );
    }

    @GetMapping("/plans")
    public List<PlatformTenantService.PlanResponse> listPlans() {
        return platformTenantService.listPlans();
    }

    public record TenantStatusRequest(boolean active) {}

    public record TenantPlanUpdateRequest(String planId) {}

    public record TenantAdminUserRequest(String email, String firstName, String lastName, String tempPassword) {}

    public record CreateTenantRequest(
            String clinicName,
            String tenantCode,
            String displayName,
            String city,
            String state,
            String country,
            String postalCode,
            String phone,
            String clinicEmail,
            String addressLine1,
            String addressLine2,
            String planId,
            Map<String, Boolean> modules,
            String adminEmail,
            String adminFirstName,
            String adminLastName,
            String tempPassword
    ) {}
}
