package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos.*;

@RestController
@RequestMapping("/api/platform/communication-test")
@PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
public class CommunicationTestController {
    private final CommunicationTestService service;

    public CommunicationTestController(CommunicationTestService service) { this.service = service; }

    @GetMapping("/health")
    public HealthResponse health() { return service.health(correlationId()); }

    @PostMapping("/email")
    public Result email(@Valid @RequestBody TestRequest request) { return service.email(optionalTenantId(), request, correlationId()); }

    @PostMapping("/voice")
    public Result voice(@Valid @RequestBody TestRequest request) { return service.voice(optionalTenantId(), request, correlationId()); }

    @PostMapping("/whatsapp")
    public Result whatsapp(@Valid @RequestBody TestRequest request) { return service.whatsapp(optionalTenantId(), request, correlationId()); }

    /** Provider certification is platform-scoped; a clinic context is optional. */
    private UUID optionalTenantId() {
        var context = RequestContextHolder.get();
        return context == null || context.tenantId() == null ? null : context.tenantId().value();
    }
    private String correlationId() {
        String value = RequestContextHolder.require().correlationId();
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
