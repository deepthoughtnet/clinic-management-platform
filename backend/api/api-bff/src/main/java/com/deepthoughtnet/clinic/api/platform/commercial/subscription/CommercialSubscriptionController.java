package com.deepthoughtnet.clinic.api.platform.commercial.subscription;

import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.CreateSubscriptionRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.LifecycleActionRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.PageResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.ReplaceSubscriptionRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionDetailResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionHistoryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionStatusCountsResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.ValidationResultResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/commercial/subscriptions")
@PreAuthorize("@permissionChecker.hasPermission('commercial.subscriptions.view')")
public class CommercialSubscriptionController {
    private final CommercialSubscriptionApiService service;

    public CommercialSubscriptionController(CommercialSubscriptionApiService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SubscriptionSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID planTemplateId,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listSubscriptions(search, tenantId, planTemplateId, status, page, size);
    }

    @GetMapping("/{id}")
    public SubscriptionDetailResponse get(@PathVariable UUID id) {
        return service.getSubscription(id);
    }

    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission('commercial.subscriptions.manage')")
    public SubscriptionDetailResponse create(@RequestBody CreateSubscriptionRequest request) {
        return service.createSubscription(request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.subscriptions.manage')")
    public SubscriptionDetailResponse activate(@PathVariable UUID id, @RequestBody(required = false) LifecycleActionRequest request) {
        return service.activate(id, request);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.subscriptions.manage')")
    public SubscriptionDetailResponse pause(@PathVariable UUID id, @RequestBody(required = false) LifecycleActionRequest request) {
        return service.pause(id, request);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.subscriptions.manage')")
    public SubscriptionDetailResponse resume(@PathVariable UUID id, @RequestBody(required = false) LifecycleActionRequest request) {
        return service.resume(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.subscriptions.manage')")
    public SubscriptionDetailResponse cancel(@PathVariable UUID id, @RequestBody(required = false) LifecycleActionRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/{id}/replace")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.subscriptions.manage')")
    public SubscriptionDetailResponse replace(@PathVariable UUID id, @RequestBody ReplaceSubscriptionRequest request) {
        return service.replace(id, request);
    }

    @GetMapping("/{id}/history")
    public List<SubscriptionHistoryResponse> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @GetMapping("/{id}/validation")
    public ValidationResultResponse validation(@PathVariable UUID id) {
        return service.getSubscription(id).validation();
    }

    @GetMapping("/status-counts")
    public SubscriptionStatusCountsResponse statusCounts() {
        return service.getStatusCounts();
    }
}
