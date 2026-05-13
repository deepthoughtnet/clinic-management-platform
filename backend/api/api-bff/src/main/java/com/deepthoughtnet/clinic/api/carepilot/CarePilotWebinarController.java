package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarAnalyticsSummaryResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarAttendanceRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarListResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarRegistrationListResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarRegistrationRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarRegistrationResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarStatusUpdateRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarUpsertRequest;
import com.deepthoughtnet.clinic.carepilot.webinar.analytics.WebinarAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRecord;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarAttendanceCommand;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarRegistrationCommand;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarRegistrationService;
import com.deepthoughtnet.clinic.carepilot.webinar.service.WebinarService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API surface for CarePilot webinar/event automation workflows. */
@RestController
@RequestMapping("/api/carepilot/webinars")
public class CarePilotWebinarController {
    private final WebinarService webinarService;
    private final WebinarRegistrationService registrationService;
    private final WebinarAnalyticsService analyticsService;

    public CarePilotWebinarController(
            WebinarService webinarService,
            WebinarRegistrationService registrationService,
            WebinarAnalyticsService analyticsService
    ) {
        this.webinarService = webinarService;
        this.registrationService = registrationService;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarListResponse list(
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus status,
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarType webinarType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledTo,
            @RequestParam(required = false) Boolean upcoming,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var rows = webinarService.search(tenantId, new WebinarSearchCriteria(status, webinarType, scheduledFrom, scheduledTo, upcoming, completed), page, size);
        return new WebinarListResponse(rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getContent().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(webinarService.find(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Webinar not found")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarResponse create(@RequestBody WebinarUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toResponse(webinarService.create(tenantId, toCommand(request), actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarResponse update(@PathVariable UUID id, @RequestBody WebinarUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toResponse(webinarService.update(tenantId, id, toCommand(request), actor));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarResponse updateStatus(@PathVariable UUID id, @RequestBody WebinarStatusUpdateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toResponse(webinarService.updateStatus(tenantId, id, request == null ? null : request.status(), actor));
    }

    @GetMapping("/{id}/registrations")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarRegistrationListResponse registrations(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var rows = registrationService.list(tenantId, id, page, size);
        return new WebinarRegistrationListResponse(rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getContent().stream().map(r -> new WebinarRegistrationResponse(
                r.id(), r.tenantId(), r.webinarId(), r.patientId(), r.leadId(), r.attendeeName(), r.attendeeEmail(), r.attendeePhone(),
                r.registrationStatus(), r.attended(), r.attendedAt(), r.source(), r.notes(), r.createdAt(), r.updatedAt()
        )).toList());
    }

    @PostMapping("/{id}/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarRegistrationResponse register(@PathVariable UUID id, @RequestBody WebinarRegistrationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = registrationService.register(tenantId, id, new WebinarRegistrationCommand(
                request == null ? null : request.patientId(),
                request == null ? null : request.leadId(),
                request == null ? null : request.attendeeName(),
                request == null ? null : request.attendeeEmail(),
                request == null ? null : request.attendeePhone(),
                request == null ? null : request.source(),
                request == null ? null : request.notes()
        ));
        return new WebinarRegistrationResponse(
                row.id(), row.tenantId(), row.webinarId(), row.patientId(), row.leadId(), row.attendeeName(), row.attendeeEmail(), row.attendeePhone(),
                row.registrationStatus(), row.attended(), row.attendedAt(), row.source(), row.notes(), row.createdAt(), row.updatedAt()
        );
    }

    @PostMapping("/{id}/attendance")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarRegistrationResponse attendance(@PathVariable UUID id, @RequestBody WebinarAttendanceRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        if (request == null || request.registrationId() == null) {
            throw new IllegalArgumentException("registrationId is required");
        }
        var row = registrationService.markAttendance(tenantId, id, request.registrationId(), new WebinarAttendanceCommand(request.registrationStatus(), request.notes()));
        return new WebinarRegistrationResponse(
                row.id(), row.tenantId(), row.webinarId(), row.patientId(), row.leadId(), row.attendeeName(), row.attendeeEmail(), row.attendeePhone(),
                row.registrationStatus(), row.attended(), row.attendedAt(), row.source(), row.notes(), row.createdAt(), row.updatedAt()
        );
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public WebinarAnalyticsSummaryResponse analytics() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = analyticsService.summary(tenantId);
        return new WebinarAnalyticsSummaryResponse(
                row.totalWebinars(), row.upcomingWebinars(), row.completedWebinars(), row.totalRegistrations(), row.attendedCount(),
                row.noShowCount(), row.attendanceRate(), row.noShowRate(), row.webinarConversions(), row.registrationsBySource(), row.attendeeEngagementCount()
        );
    }

    private WebinarUpsertCommand toCommand(WebinarUpsertRequest request) {
        if (request == null) {
            return null;
        }
        return new WebinarUpsertCommand(
                request.title(), request.description(), request.webinarType(), request.status(), request.webinarUrl(), request.organizerName(), request.organizerEmail(),
                request.scheduledStartAt(), request.scheduledEndAt(), request.timezone(), request.capacity(), request.registrationEnabled(), request.reminderEnabled(),
                request.followupEnabled(), request.tags()
        );
    }

    private WebinarResponse toResponse(WebinarRecord row) {
        return new WebinarResponse(
                row.id(), row.tenantId(), row.title(), row.description(), row.webinarType(), row.status(), row.webinarUrl(), row.organizerName(), row.organizerEmail(),
                row.scheduledStartAt(), row.scheduledEndAt(), row.timezone(), row.capacity(), row.registrationEnabled(), row.reminderEnabled(), row.followupEnabled(),
                row.tags(), row.createdBy(), row.updatedBy(), row.createdAt(), row.updatedAt()
        );
    }
}
