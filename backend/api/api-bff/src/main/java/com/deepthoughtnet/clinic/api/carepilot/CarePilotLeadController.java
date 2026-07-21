package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadActivityListResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadActivityResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadAnalyticsResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadConversionResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadCsvImportResponse;
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
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadConvertedMetadataCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatusUpdateCommand;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.fasterxml.jackson.databind.JsonNode;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;

/** CarePilot lead intake, lifecycle, conversion, and analytics APIs. */
@RestController
@RequestMapping("/api/carepilot/leads")
public class CarePilotLeadController {
    private static final String UUID_PATH = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final LeadService leadService;
    private final LeadConversionService conversionService;
    private final LeadAnalyticsService analyticsService;
    private final LeadActivityService activityService;
    private final CarePilotLeadCsvService leadCsvService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final TenantSubscriptionService tenantSubscriptionService;
    private final PermissionChecker permissionChecker;

    public CarePilotLeadController(
            LeadService leadService,
            LeadConversionService conversionService,
            LeadAnalyticsService analyticsService,
            LeadActivityService activityService,
            CarePilotLeadCsvService leadCsvService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            TenantSubscriptionService tenantSubscriptionService,
            PermissionChecker permissionChecker
    ) {
        this.leadService = leadService;
        this.conversionService = conversionService;
        this.analyticsService = analyticsService;
        this.activityService = activityService;
        this.leadCsvService = leadCsvService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.lead.view','engage.lead.view.all','engage.lead.view.audit')")
    public LeadListResponse list(
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus status,
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource source,
            @RequestParam(required = false) UUID assignedToAppUserId,
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") boolean followUpDue,
            @RequestParam(required = false, defaultValue = "false") boolean pipelineOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID viewerAppUserId = RequestContextHolder.require().appUserId();
        boolean viewAll = canViewAllLeads();
        if (!viewAll && assignedToAppUserId != null && !assignedToAppUserId.equals(viewerAppUserId)) {
            throw new AccessDeniedException("Lead is not visible");
        }
        ZoneId tenantZone = clinicTimeZoneResolver.resolve(tenantId);
        Page<LeadRecord> result = leadService.search(tenantId, tenantZone,
                new LeadSearchCriteria(status, source, assignedToAppUserId, priority, search, followUpDue, pipelineOnly, createdFrom, createdTo),
                page, size, viewerAppUserId, viewAll);
        return new LeadListResponse(result.getNumber(), result.getSize(), result.getTotalElements(), result.getContent().stream().map(this::toResponse).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('engage.lead.create')")
    public LeadResponse create(@Valid @RequestBody LeadUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return toResponse(leadService.create(tenantId, toCommand(request), actor));
    }

    @PostMapping(value = "/import-csv", consumes = "multipart/form-data")
    @PreAuthorize("@permissionChecker.hasPermission('engage.lead.import')")
    public LeadCsvImportResponse importCsv(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        return leadCsvService.importCsv(tenantId, file.getBytes(), actor);
    }

    @GetMapping(value = "/import-template", produces = "text/csv")
    @PreAuthorize("@permissionChecker.hasPermission('engage.lead.import')")
    public ResponseEntity<byte[]> importTemplate() throws java.io.IOException {
        return csvResponse("carepilot-leads-import-template.csv", leadCsvService.importTemplateCsv());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("@permissionChecker.hasPermission('engage.lead.export')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus status,
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource source,
            @RequestParam(required = false) UUID assignedToAppUserId,
            @RequestParam(required = false) com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority priority,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") boolean followUpDue,
            @RequestParam(required = false, defaultValue = "false") boolean pipelineOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo
    ) throws java.io.IOException {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID viewerAppUserId = RequestContextHolder.require().appUserId();
        boolean viewAll = canViewAllLeads();
        if (!viewAll && assignedToAppUserId != null && !assignedToAppUserId.equals(viewerAppUserId)) {
            throw new AccessDeniedException("Lead is not visible");
        }
        ZoneId tenantZone = clinicTimeZoneResolver.resolve(tenantId);
        return csvResponse(
                "carepilot-leads-export.csv",
                leadCsvService.exportCsv(
                        tenantId,
                        tenantZone,
                        new LeadSearchCriteria(status, source, assignedToAppUserId, priority, search, followUpDue, pipelineOnly, createdFrom, createdTo),
                        viewerAppUserId,
                        viewAll
                )
        );
    }

    @GetMapping("/{id:" + UUID_PATH + "}")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.lead.view','engage.lead.view.all','engage.lead.view.audit')")
    public LeadResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID viewerAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(leadService.requireVisibleLead(tenantId, id, viewerAppUserId, canViewAllLeads()));
    }

    @PutMapping("/{id:" + UUID_PATH + "}")
    @PreAuthorize("@permissionChecker.hasPermission('engage.lead.edit')")
    public LeadResponse update(@PathVariable UUID id, @Valid @RequestBody LeadUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        leadService.assertVisible(tenantId, id, actor, canViewAllLeads());
        return toResponse(leadService.update(tenantId, id, toCommand(request), actor));
    }

    @PutMapping("/{id:" + UUID_PATH + "}/converted-metadata")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.lead.edit','engage.lead.assign')")
    public LeadResponse updateConvertedMetadata(@PathVariable UUID id, @RequestBody(required = false) JsonNode request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        LeadRecord current = leadService.requireVisibleLead(tenantId, id, actor, canViewAllLeads());
        requireConvertedLeadMetadataPermission(request);
        return toResponse(leadService.updateConvertedMetadata(tenantId, id, new LeadConvertedMetadataCommand(
                readText(request, "notes", current.notes()),
                readText(request, "tags", current.tags()),
                readText(request, "sourceDetails", current.sourceDetails()),
                readUuid(request, "campaignId", current.campaignId()),
                readUuid(request, "assignedToAppUserId", current.assignedToAppUserId())
        ), actor));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/status")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.lead.edit','engage.lead.assign','engage.lead.follow.up')")
    public LeadResponse updateStatus(@PathVariable UUID id, @RequestBody LeadStatusUpdateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        leadService.assertVisible(tenantId, id, actor, canViewAllLeads());
        return toResponse(leadService.updateStatus(tenantId, id, new LeadStatusUpdateCommand(
                request.status(), request.priority(), request.assignedToAppUserId(), request.lastContactedAt(), request.nextFollowUpAt(), request.comment()
        ), actor));
    }

    @GetMapping("/{leadId:" + UUID_PATH + "}/activities")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.lead.view','engage.lead.view.all','engage.lead.view.audit')")
    public LeadActivityListResponse activities(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID viewerAppUserId = RequestContextHolder.require().appUserId();
        leadService.assertVisible(tenantId, leadId, viewerAppUserId, canViewAllLeads());
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

    @PostMapping("/{leadId:" + UUID_PATH + "}/notes")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.lead.edit','engage.lead.follow.up')")
    public LeadResponse addNote(@PathVariable UUID leadId, @RequestBody LeadNoteRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        leadService.assertVisible(tenantId, leadId, actor, canViewAllLeads());
        return toResponse(leadService.addNote(tenantId, leadId, request == null ? null : request.note(), actor));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/convert")
    @PreAuthorize("@permissionChecker.hasAnyPermission('engage.lead.convert','engage.lead.book.appointment')")
    public LeadConversionResponse convert(@PathVariable UUID id, @RequestBody(required = false) LeadConvertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actor = RequestContextHolder.require().appUserId();
        leadService.assertVisible(tenantId, id, actor, canViewAllLeads());
        boolean book = request != null && request.bookAppointment();
        LeadAppointmentBookingCommand booking = null;
        if (book && request.appointment() == null) {
            throw new IllegalArgumentException("appointment details are required when bookAppointment is true");
        }
        requireLeadConversionPermission();
        if (book) {
            requireLeadBookAppointmentPermission();
        }
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
        LeadConversionResult result = conversionService.convert(tenantId, id, actor, booking, allowOverbooking, actor, canViewAllLeads());
        return new LeadConversionResponse(result.leadId(), result.patientId(), result.newlyCreated(), result.appointmentId(), result.appointmentError());
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize("@permissionChecker.hasPermission('engage.analytics.view')")
    public LeadAnalyticsResponse analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ZoneId tenantZone = clinicTimeZoneResolver.resolve(tenantId);
        var row = analyticsService.summary(tenantId, tenantZone, startDate, endDate);
        return new LeadAnalyticsResponse(
                row.totalLeads(), row.newLeads(), row.qualifiedLeads(), row.convertedLeads(), row.lostLeads(), row.followUpsDue(),
                row.followUpsDueToday(), row.overdueFollowUps(), row.conversionRate(), row.sourceBreakdown(), row.staleLeads(),
                row.highPriorityActiveLeads(), row.conversionsWithAppointment(), row.avgHoursToConversion()
        );
    }

    private boolean canViewAllLeads() {
        return permissionChecker.hasPermission("engage.lead.view.all");
    }

    private void requireLeadConversionPermission() {
        if (!permissionChecker.hasPermission("engage.lead.convert")) {
            throw new AccessDeniedException("Lead conversion permission is required");
        }
    }

    private void requireLeadBookAppointmentPermission() {
        if (!permissionChecker.hasPermission("engage.lead.book.appointment")) {
            throw new AccessDeniedException("Lead appointment booking permission is required");
        }
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
                row.notes(), row.tags(), canExposePatientNavigation(row.tenantId()) ? row.convertedPatientId() : null, row.bookedAppointmentId(), row.lastContactedAt(), row.nextFollowUpAt(), row.lastActivityAt(),
                row.createdBy(), row.updatedBy(), row.createdAt(), row.updatedAt()
        );
    }

    private void requireConvertedLeadMetadataPermission(JsonNode request) {
        boolean hasMarketingUpdate = request != null && (
                request.has("notes")
                || request.has("tags")
                || request.has("sourceDetails")
                || request.has("campaignId")
        );
        boolean hasAssigneeUpdate = request != null && request.has("assignedToAppUserId");
        if (hasMarketingUpdate && !permissionChecker.hasPermission("engage.lead.edit")) {
            throw new AccessDeniedException("You do not have permission to update converted lead marketing details");
        }
        if (hasAssigneeUpdate && !permissionChecker.hasPermission("engage.lead.assign")) {
            throw new AccessDeniedException("You do not have permission to change the assignee");
        }
    }

    private String readText(JsonNode request, String field, String fallback) {
        if (request == null || !request.has(field)) {
            return fallback;
        }
        JsonNode node = request.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null ? null : value;
    }

    private UUID readUuid(JsonNode request, String field, UUID fallback) {
        if (request == null || !request.has(field)) {
            return fallback;
        }
        JsonNode node = request.get(field);
        if (node == null || node.isNull() || !StringUtils.hasText(node.asText())) {
            return null;
        }
        return UUID.fromString(node.asText());
    }

    private boolean canExposePatientNavigation(UUID tenantId) {
        if (!permissionChecker.hasPermission("patient.read")) {
            return false;
        }
        return tenantSubscriptionService.isModuleEnabled(tenantId, "APPOINTMENTS")
                || tenantSubscriptionService.isModuleEnabled(tenantId, "CONSULTATION");
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
