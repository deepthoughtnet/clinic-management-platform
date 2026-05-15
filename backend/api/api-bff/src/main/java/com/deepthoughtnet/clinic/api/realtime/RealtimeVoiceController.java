package com.deepthoughtnet.clinic.api.realtime;

import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.CreateVoiceSessionRequest;
import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.RealtimeVoiceSummaryResponse;
import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceSessionEventResponse;
import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceSessionEventsResponse;
import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceSessionResponse;
import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceSessionsResponse;
import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceTranscriptsResponse;
import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceTurnRequest;
import com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceTurnResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.realtime.voice.session.RealtimeVoiceSessionService;
import com.deepthoughtnet.clinic.realtime.voice.session.RealtimeVoiceStatusService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-safe realtime voice gateway admin APIs. */
@RestController
@RequestMapping("/api/realtime-ai")
public class RealtimeVoiceController {
    private final RealtimeVoiceSessionService sessionService;
    private final RealtimeVoiceStatusService statusService;

    public RealtimeVoiceController(RealtimeVoiceSessionService sessionService, RealtimeVoiceStatusService statusService) {
        this.sessionService = sessionService;
        this.statusService = statusService;
    }

    @PostMapping("/sessions")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN')")
    public VoiceSessionResponse createSession(@RequestBody CreateVoiceSessionRequest request) {
        var ctx = RequestContextHolder.require();
        return new VoiceSessionResponse(sessionService.createSession(
                ctx.tenantId().value(),
                request.sessionType(),
                request.patientId(),
                request.leadId(),
                request.metadataJson(),
                ctx.correlationId()
        ));
    }

    @GetMapping("/sessions")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST')")
    public VoiceSessionsResponse sessions() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return new VoiceSessionsResponse(sessionService.listSessions(tenantId));
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST')")
    public VoiceSessionResponse session(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return new VoiceSessionResponse(sessionService.getSession(tenantId, id));
    }

    @PostMapping("/sessions/{id}/turns")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST')")
    public VoiceTurnResponse processTurn(@PathVariable UUID id, @RequestBody VoiceTurnRequest request) {
        var ctx = RequestContextHolder.require();
        var result = sessionService.processUserText(
                ctx.tenantId().value(),
                id,
                ctx.appUserId(),
                request.text(),
                request.promptKey(),
                request.patientContextJson(),
                ctx.correlationId()
        );
        return new VoiceTurnResponse(result.userTranscript(), result.aiTranscript(), result.escalationReason(), result.aiProvider(), result.aiLatencyMs());
    }

    @GetMapping("/sessions/{id}/events")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST')")
    public VoiceSessionEventsResponse events(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return new VoiceSessionEventsResponse(sessionService.sessionEvents(tenantId, id).stream().map(e -> new VoiceSessionEventResponse(
                e.getId(), e.getSessionId(), e.getEventType(), e.getEventTimestamp(), e.getSequenceNumber(), e.getPayloadSummary(), e.getCorrelationId()
        )).toList());
    }

    @GetMapping("/sessions/{id}/transcripts")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST')")
    public VoiceTranscriptsResponse transcripts(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return new VoiceTranscriptsResponse(sessionService.transcripts(tenantId, id));
    }

    @PostMapping("/sessions/{id}/complete")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('RECEPTIONIST')")
    public VoiceSessionResponse complete(@PathVariable UUID id) {
        var ctx = RequestContextHolder.require();
        return new VoiceSessionResponse(sessionService.completeSession(ctx.tenantId().value(), id, ctx.correlationId()));
    }

    @GetMapping("/summary")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST')")
    public RealtimeVoiceSummaryResponse summary() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = statusService.summary(tenantId);
        return new RealtimeVoiceSummaryResponse(
                row.activeSessions(),
                row.escalationCount(),
                row.failedSessions(),
                row.avgAiLatencyMs(),
                row.avgSttLatencyMs(),
                row.avgTtsLatencyMs(),
                row.avgTranscriptLatencyMs(),
                row.websocketDisconnects(),
                row.sttFailures(),
                row.ttsFailures(),
                row.interruptionCount(),
                row.sttProviders().stream().map(p -> new com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.ProviderStatus(p.providerName(), p.ready())).toList(),
                row.ttsProviders().stream().map(p -> new com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.ProviderStatus(p.providerName(), p.ready())).toList(),
                new com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.RuntimeStatus(
                        row.runtimeStatus().status(),
                        row.runtimeStatus().sttReady(),
                        row.runtimeStatus().ttsReady(),
                        row.runtimeStatus().modelReady(),
                        row.runtimeStatus().activeSessions(),
                        row.runtimeStatus().uptimeSeconds(),
                        row.runtimeStatus().error()
                )
        );
    }
}
