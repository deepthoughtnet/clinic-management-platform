package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadActivityListResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadActivityResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadAnalyticsResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadConversionResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadConvertRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadListResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadNoteRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadStatusUpdateRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadUpsertRequest;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.analytics.LeadAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadAppointmentBookingCommand;
import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadConversionResult;
import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadConversionService;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatusUpdateCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
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

/** CarePilot lead intake, lifecycle, conversion, and analytics APIs. */
@RestController
@RequestMapping("/api/carepilot/leads")
public class CarePilotLeadController {
    private final LeadService leadService;
    private final LeadConversionService conversionService;
    private final LeadAnalyticsService analyticsService;
    private final LeadActivityService activityService;

    public CarePilotLeadController(
            LeadService leadService,
            LeadConversionService conversionService,
            LeadAnalyticsService analyticsService,
            LeadActivityService activityService
    ) {
        this.leadService = leadService;
        this.conversionService = conversionService;
        this.analyticsService = analyticsService;
        this.activityService = activityService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadListResponse list(
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus status,
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource source,
            @RequestParam(required = false) UUID assignedToAppUserId,
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") boolean followUpDue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        Page<LeadRecord> result = leadService.search(tenantId,
                new LeadSearchCriteria(status, source, assignedToAppUserId, priority, search, followUpDue, createdFrom, createdTo),
                page, size);
        return new LeadListResponse(result.getNumber(), result.getSize(), result.getTotalElements(), result.getContent().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(leadService.find(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Lead not found")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadResponse create(@RequestBody LeadUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toResponse(leadService.create(tenantId, toCommand(request), actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadResponse update(@PathVariable UUID id, @RequestBody LeadUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toResponse(leadService.update(tenantId, id, toCommand(request), actor));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadResponse updateStatus(@PathVariable UUID id, @RequestBody LeadStatusUpdateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toResponse(leadService.updateStatus(tenantId, id, new LeadStatusUpdateCommand(
                request.status(), request.priority(), request.assignedToAppUserId(), request.lastContactedAt(), request.nextFollowUpAt(), request.comment()
        ), actor));
    }

    @GetMapping("/{leadId}/activities")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadActivityListResponse activities(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var rows = activityService.list(tenantId, leadId, page, size);
        return new LeadActivityListResponse(
                rows.getNumber(),
                rows.getSize(),
                rows.getTotalElements(),
                rows.getContent().stream().map(row -> new LeadActivityResponse(
                        row.id(), row.tenantId(), row.leadId(), row.activityType(), row.title(), row.description(), row.oldStatus(), row.newStatus(),
                        row.relatedEntityType(), row.relatedEntityId(), row.createdByAppUserId(), row.createdAt()
                )).toList()
        );
    }

    @PostMapping("/{leadId}/notes")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadResponse addNote(@PathVariable UUID leadId, @RequestBody LeadNoteRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toResponse(leadService.addNote(tenantId, leadId, request == null ? null : request.note(), actor));
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadConversionResponse convert(@PathVariable UUID id, @RequestBody(required = false) LeadConvertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        boolean book = request != null && request.bookAppointment();
        LeadAppointmentBookingCommand booking = null;
        if (book && request.appointment() != null) {
            booking = new LeadAppointmentBookingCommand(
                    request.appointment().doctorUserId(),
                    request.appointment().appointmentDate(),
                    request.appointment().appointmentTime(),
                    request.appointment().reason(),
                    request.appointment().notes(),
                    request.appointment().priority()
            );
        }
        boolean allowOverbooking = RequestContextHolder.require().tenantRole() != null
                && RequestContextHolder.require().tenantRole().toUpperCase().contains("CLINIC_ADMIN");
        LeadConversionResult result = conversionService.convert(tenantId, id, actor, booking, allowOverbooking);
        return new LeadConversionResponse(result.leadId(), result.patientId(), result.newlyCreated(), result.appointmentId(), result.appointmentError());
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or (@permissionChecker.hasRole('PLATFORM_ADMIN') and @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT'))")
    public LeadAnalyticsResponse analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = analyticsService.summary(tenantId, startDate, endDate);
        return new LeadAnalyticsResponse(
                row.totalLeads(), row.newLeads(), row.qualifiedLeads(), row.convertedLeads(), row.lostLeads(), row.followUpsDue(),
                row.followUpsDueToday(), row.overdueFollowUps(), row.conversionRate(), row.sourceBreakdown(), row.staleLeads(),
                row.highPriorityActiveLeads(), row.conversionsWithAppointment(), row.avgHoursToConversion()
        );
    }

    private LeadUpsertCommand toCommand(LeadUpsertRequest request) {
        if (request == null) {
            return null;
        }
        return new LeadUpsertCommand(
                request.firstName(), request.lastName(), request.phone(), request.email(), request.gender(), request.dateOfBirth(),
                request.source(), request.sourceDetails(), request.campaignId(), request.assignedToAppUserId(), request.status(),
                request.priority(), request.notes(), request.tags(), request.lastContactedAt(), request.nextFollowUpAt()
        );
    }

    private LeadResponse toResponse(LeadRecord row) {
        return new LeadResponse(
                row.id(), row.tenantId(), row.firstName(), row.lastName(), row.fullName(), row.phone(), row.email(), row.gender(),
                row.dateOfBirth(), row.source(), row.sourceDetails(), row.campaignId(), row.assignedToAppUserId(), row.status(), row.priority(),
                row.notes(), row.tags(), row.convertedPatientId(), row.bookedAppointmentId(), row.lastContactedAt(), row.nextFollowUpAt(), row.lastActivityAt(),
                row.createdBy(), row.updatedBy(), row.createdAt(), row.updatedAt()
        );
    }
}
