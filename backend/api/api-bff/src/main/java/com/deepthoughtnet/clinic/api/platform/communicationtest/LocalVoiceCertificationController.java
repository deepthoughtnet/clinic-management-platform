package com.deepthoughtnet.clinic.api.platform.communicationtest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/** Local transcript harness. It derives tenant/patient exclusively through normal inbound resolution. */
@RestController
@RequestMapping("/api/local/voice-certification")
@Profile("docker & voice-certification")
@ConditionalOnProperty(prefix = "aiva.voice.certification-harness", name = "enabled", havingValue = "true")
@PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
public class LocalVoiceCertificationController {
    private final CommunicationTestService communicationTestService;

    public LocalVoiceCertificationController(CommunicationTestService communicationTestService) {
        this.communicationTestService = communicationTestService;
    }

    @PostMapping("/calls")
    public CallStarted start(@Valid @RequestBody StartCallRequest request) {
        CommunicationTestDtos.InboundVoiceResult call;
        try {
            call = communicationTestService.startLocalVoiceCertificationCall(
                    request.callerNumber(), request.didNumber(), RequestContextHolder.requireTenantId());
        } catch (SecurityException tenantMismatch) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Certification DID tenant does not match request tenant context");
        }
        return new CallStarted(call.callId(), call.tenantId(), call.clinicId(), call.routingStatus(),
                call.patientIdentificationStatus(), call.identifiedPatientId() != null);
    }

    @PostMapping("/calls/{callId}/turns")
    public TurnResponse turn(@PathVariable String callId, @Valid @RequestBody TranscriptRequest request) {
        assertCallTenant(callId);
        var result = communicationTestService.submitLocalVoiceCertificationTranscript(callId, request.transcript());
        return new TurnResponse(result.response(), result.intent(), result.tool(), result.mutationBlocked(),
                result.policyDecision(), result.toolExecutionStatus(), result.toolResultCount(),
                stringDiagnostic(result, "responseIntent"), stringDiagnostic(result, "presentationMode"),
                stringDiagnostic(result, "ttsLanguage"));
    }

    @DeleteMapping("/calls/{callId}")
    public void end(@PathVariable String callId) {
        assertCallTenant(callId);
        communicationTestService.endLocalVoiceCertificationCall(callId);
    }

    private void assertCallTenant(String callId) {
        var call = communicationTestService.inboundVoiceResult(callId);
        if (call == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Certification call not found");
        if (!RequestContextHolder.requireTenantId().equals(call.tenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Certification call not found");
        }
    }

    private String stringDiagnostic(VoiceReadOnlyAivaService.TurnResult result, String key) {
        Object value = result.runtimeDiagnostics().get(key);
        return value instanceof String string ? string : null;
    }

    public record StartCallRequest(
            @NotBlank @Pattern(regexp = "\\+?[0-9][0-9()\\-\\s]{6,19}") String callerNumber,
            @NotBlank @Pattern(regexp = "\\+?[0-9][0-9()\\-\\s]{6,19}") String didNumber) {}

    public record TranscriptRequest(@NotBlank @Size(max = 500) String transcript) {}

    public record CallStarted(String callId, java.util.UUID tenantId, java.util.UUID clinicId,
            String routingStatus, String patientIdentificationStatus, boolean uniquePatientMatched) {}

    public record TurnResponse(String response, String intent, String tool, boolean mutationBlocked,
            String policyDecision, String toolExecutionStatus, int toolResultCount, String responseIntent,
            String presentationMode, String ttsLanguage) {}
}
