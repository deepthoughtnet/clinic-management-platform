package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;

public final class CommunicationTestDtos {
    private CommunicationTestDtos() {}

    public enum TestMode { PROVIDER_ONLY, REMINDER_SIMULATION, INTERACTIVE_RESPONSE }

    public enum Scenario { YES_NO_CONFIRMATION, APPOINTMENT_REMINDER, FOLLOW_UP_CONSULTATION, MEDICATION_REFILL_REVIEW, LAB_REMINDER, LAB_REPORT_READY }

    public record InboundVoiceTurnDiagnostic(
            int turnNumber,
            String callerTranscript,
            String sttProvider,
            String detectedIntent,
            String selectedTool,
            String toolInputSpeciality,
            int toolResultCount,
            String toolResultSummary,
            String toolExecutionStatus,
            String policyDecision,
            String aivaResponseText,
            String ttsText,
            String ttsProvider,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            boolean closing,
            Map<String, Object> runtimeDiagnostics
    ) {
        public InboundVoiceTurnDiagnostic(int turnNumber, String callerTranscript, String sttProvider,
                String detectedIntent, String selectedTool, String toolInputSpeciality, int toolResultCount,
                String toolResultSummary, String toolExecutionStatus, String policyDecision, String aivaResponseText,
                String ttsText, String ttsProvider, OffsetDateTime startedAt, OffsetDateTime completedAt,
                boolean closing) {
            this(turnNumber, callerTranscript, sttProvider, detectedIntent, selectedTool, toolInputSpeciality,
                    toolResultCount, toolResultSummary, toolExecutionStatus, policyDecision, aivaResponseText,
                    ttsText, ttsProvider, startedAt, completedAt, closing, Map.of());
        }

        public InboundVoiceTurnDiagnostic {
            if (runtimeDiagnostics == null || runtimeDiagnostics.isEmpty()) runtimeDiagnostics = Map.of();
            else {
                java.util.Map<String, Object> safe = new java.util.LinkedHashMap<>(runtimeDiagnostics);
                safe.entrySet().removeIf(entry -> entry.getValue() == null);
                runtimeDiagnostics = Map.copyOf(safe);
            }
        }
    }

    public record TestRequest(
            @NotBlank String recipient,
            String subject,
            @NotBlank String message,
            String language,
            TestMode mode,
            Scenario scenario,
            String provider
    ) {
        public TestRequest(String recipient, String subject, String message, String language, TestMode mode, Scenario scenario) {
            this(recipient, subject, message, language, mode, scenario, null);
        }
    }

    public record HealthResponse(List<ChannelHealth> channels, String correlationId, List<CapabilityHealth> capabilities) {
        public HealthResponse(List<ChannelHealth> channels, String correlationId) {
            this(channels, correlationId, List.of());
        }
    }

    public record CapabilityHealth(String capability, String provider, String status, String message) {}

    public record ChannelHealth(
            String channel,
            String provider,
            String status,
            String message,
            boolean configured,
            boolean providerAvailable,
            boolean supportsTest,
            String senderIdentity,
            List<String> missingConfigurationKeys,
            OffsetDateTime checkedAt
    ) {}

    public record Result(
            String requestId,
            String correlationId,
            String channel,
            String provider,
            String providerRequestId,
            String status,
            boolean success,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            String failureCategory,
            String failureMessage,
            String testExecutionId,
            String sttProvider,
            String rawTranscript,
            String detectedLanguage,
            Double sttConfidence,
            String normalizedIntent,
            Integer attemptNumber,
            String wouldExecuteAction,
            OffsetDateTime answeredAt,
            String hangupCause
    ) {
        public Result(String requestId, String correlationId, String channel, String provider,
                      String providerRequestId, String status, boolean success,
                      OffsetDateTime startedAt, OffsetDateTime completedAt,
                      String failureCategory, String failureMessage) {
            this(requestId, correlationId, channel, provider, providerRequestId, status, success,
                    startedAt, completedAt, failureCategory, failureMessage, requestId, null, null,
                    null, null, null, null, null, null, null);
        }
    }

    public record InboundVoiceResult(
            String callId,
            String callerPhoneNumber,
            String didNumber,
            String direction,
            String status,
            String language,
            String sttProvider,
            String firstTranscript,
            OffsetDateTime receivedAt,
            OffsetDateTime answeredAt,
            OffsetDateTime capturedAt,
            OffsetDateTime endedAt,
            String hangupCause,
            String websocketStatus,
            String normalizedDid,
            UUID communicationChannelId,
            UUID tenantId,
            UUID clinicId,
            UUID locationId,
            String provider,
            String routingStatus,
            String routingFailureReason,
            String normalizedCallerPhone,
            String patientIdentificationStatus,
            UUID identifiedPatientId,
            String identifiedPatientName,
            int candidateCount,
            String identificationSource,
            UUID interactionId,
            String conversationPartyType,
            String aivaSessionId,
            String conversationMode,
            int turnCount,
            String lastDetectedIntent,
            String lastTool,
            String lastAivaResponse,
            List<InboundVoiceTurnDiagnostic> conversationTurns
    ) {
        public InboundVoiceResult {
            conversationTurns = conversationTurns == null ? List.of() : List.copyOf(conversationTurns);
        }

        /** Safe diagnostic marker; it is present only after the shared V2 voice runtime handles a turn. */
        @JsonProperty("aivaRuntime")
        public String aivaRuntime() {
            return aivaSessionId == null ? null : "AIVA_V2";
        }

        @JsonProperty("aivaBuildIdentity")
        public Map<String, String> aivaBuildIdentity() {
            return AivaVoiceBuildIdentity.current();
        }

        public InboundVoiceResult(String callId, String callerPhoneNumber, String didNumber, String direction,
                String status, String language, String sttProvider, String firstTranscript,
                OffsetDateTime receivedAt, OffsetDateTime answeredAt, OffsetDateTime capturedAt,
                OffsetDateTime endedAt, String hangupCause, String websocketStatus, String normalizedDid,
                UUID communicationChannelId, UUID tenantId, UUID clinicId, UUID locationId, String provider,
                String routingStatus, String routingFailureReason, String normalizedCallerPhone,
                String patientIdentificationStatus, UUID identifiedPatientId, String identifiedPatientName,
                int candidateCount, String identificationSource, UUID interactionId, String conversationPartyType) {
            this(callId, callerPhoneNumber, didNumber, direction, status, language, sttProvider, firstTranscript,
                    receivedAt, answeredAt, capturedAt, endedAt, hangupCause, websocketStatus, normalizedDid,
                    communicationChannelId, tenantId, clinicId, locationId, provider, routingStatus,
                    routingFailureReason, normalizedCallerPhone, patientIdentificationStatus, identifiedPatientId,
                    identifiedPatientName, candidateCount, identificationSource, interactionId, conversationPartyType,
                    null, "AIVA_READ_ONLY", 0, null, null, null, List.of());
        }

        public InboundVoiceResult(String callId, String callerPhoneNumber, String didNumber, String direction,
                String status, String language, String sttProvider, String firstTranscript,
                OffsetDateTime receivedAt, OffsetDateTime answeredAt, OffsetDateTime capturedAt,
                OffsetDateTime endedAt, String hangupCause, String websocketStatus, String normalizedDid,
                UUID communicationChannelId, UUID tenantId, UUID clinicId, UUID locationId, String provider,
                String routingStatus, String routingFailureReason) {
            this(callId, callerPhoneNumber, didNumber, direction, status, language, sttProvider, firstTranscript,
                    receivedAt, answeredAt, capturedAt, endedAt, hangupCause, websocketStatus, normalizedDid,
                    communicationChannelId, tenantId, clinicId, locationId, provider, routingStatus,
                    routingFailureReason, null, "NOT_CHECKED", null, null, 0, "CALLER_PHONE", null, "GUEST_CALLER",
                    null, "AIVA_READ_ONLY", 0, null, null, null, List.of());
        }

        public InboundVoiceResult(String callId, String callerPhoneNumber, String didNumber, String direction,
                String status, String language, String sttProvider, String firstTranscript,
                OffsetDateTime receivedAt, OffsetDateTime answeredAt, OffsetDateTime capturedAt,
                OffsetDateTime endedAt, String hangupCause, String websocketStatus) {
            this(callId, callerPhoneNumber, didNumber, direction, status, language, sttProvider, firstTranscript,
                    receivedAt, answeredAt, capturedAt, endedAt, hangupCause, websocketStatus,
                    null, null, null, null, null, null, null, null,
                    null, "NOT_CHECKED", null, null, 0, "CALLER_PHONE", null, "GUEST_CALLER",
                    null, "AIVA_READ_ONLY", 0, null, null, null, List.of());
        }

        public InboundVoiceResult withLifecycle(String nextStatus, String nextSttProvider, String nextTranscript,
                OffsetDateTime nextReceivedAt, OffsetDateTime nextAnsweredAt, OffsetDateTime nextCapturedAt,
                OffsetDateTime nextEndedAt, String nextHangupCause) {
            return new InboundVoiceResult(callId, callerPhoneNumber, didNumber, direction, nextStatus, language,
                    nextSttProvider, nextTranscript, nextReceivedAt, nextAnsweredAt, nextCapturedAt, nextEndedAt,
                    nextHangupCause, websocketStatus, normalizedDid, communicationChannelId, tenantId, clinicId,
                    locationId, provider, routingStatus, routingFailureReason, normalizedCallerPhone,
                    patientIdentificationStatus, identifiedPatientId, identifiedPatientName, candidateCount,
                    identificationSource, interactionId, conversationPartyType, aivaSessionId, conversationMode,
                    turnCount, lastDetectedIntent, lastTool, lastAivaResponse, conversationTurns);
        }

        public InboundVoiceResult withAivaTurn(String sessionId, int nextTurnCount, String intent,
                String tool, String response) {
            return withAivaTurn(sessionId, nextTurnCount, intent, tool, response,
                    firstTranscript, sttProvider, "UNKNOWN", tool == null ? "NOT_EXECUTED" : "EXECUTED");
        }

        public InboundVoiceResult withAivaTurn(String sessionId, int nextTurnCount, String intent,
                String tool, String response, String callerTranscript, String turnSttProvider,
                String policyDecision, String toolExecutionStatus) {
            return withAivaTurn(sessionId, nextTurnCount, intent, tool, response, callerTranscript,
                    turnSttProvider, policyDecision, toolExecutionStatus, null, 0, null);
        }

        public InboundVoiceResult withAivaTurn(String sessionId, int nextTurnCount, String intent,
                String tool, String response, String callerTranscript, String turnSttProvider,
                String policyDecision, String toolExecutionStatus, String toolInputSpeciality,
                int toolResultCount, String toolResultSummary) {
            return withAivaTurn(sessionId, nextTurnCount, intent, tool, response, callerTranscript,
                    turnSttProvider, policyDecision, toolExecutionStatus, toolInputSpeciality,
                    toolResultCount, toolResultSummary, Map.of());
        }

        public InboundVoiceResult withAivaTurn(String sessionId, int nextTurnCount, String intent,
                String tool, String response, String callerTranscript, String turnSttProvider,
                String policyDecision, String toolExecutionStatus, String toolInputSpeciality,
                int toolResultCount, String toolResultSummary, Map<String, Object> runtimeDiagnostics) {
            return new InboundVoiceResult(callId, callerPhoneNumber, didNumber, direction, status, language,
                    sttProvider, firstTranscript, receivedAt, answeredAt, capturedAt, endedAt, hangupCause,
                    websocketStatus, normalizedDid, communicationChannelId, tenantId, clinicId, locationId,
                    provider, routingStatus, routingFailureReason, normalizedCallerPhone,
                    patientIdentificationStatus, identifiedPatientId, identifiedPatientName, candidateCount,
                    identificationSource, interactionId, conversationPartyType, sessionId,
                    String.valueOf(runtimeDiagnostics == null ? "READ_ONLY_DISCOVERY"
                            : runtimeDiagnostics.getOrDefault("transactionMode", "READ_ONLY_DISCOVERY")),
                    nextTurnCount, intent, tool, response, appendTurn(new InboundVoiceTurnDiagnostic(
                            nextTurnCount, callerTranscript, turnSttProvider, intent, tool,
                            toolInputSpeciality, toolResultCount, toolResultSummary, toolExecutionStatus, policyDecision, response,
                            null, null, OffsetDateTime.now(), null, false, runtimeDiagnostics)));
        }

        public InboundVoiceResult withAivaRuntimeDiagnostics(Map<String, Object> diagnostics) {
            if (conversationTurns.isEmpty()) return this;
            List<InboundVoiceTurnDiagnostic> updated = new java.util.ArrayList<>(conversationTurns);
            InboundVoiceTurnDiagnostic prior = updated.get(updated.size() - 1);
            updated.set(updated.size() - 1, new InboundVoiceTurnDiagnostic(prior.turnNumber(), prior.callerTranscript(),
                    prior.sttProvider(), prior.detectedIntent(), prior.selectedTool(), prior.toolInputSpeciality(),
                    prior.toolResultCount(), prior.toolResultSummary(), prior.toolExecutionStatus(),
                    prior.policyDecision(), prior.aivaResponseText(), prior.ttsText(), prior.ttsProvider(),
                    prior.startedAt(), prior.completedAt(), prior.closing(), diagnostics));
            return new InboundVoiceResult(callId, callerPhoneNumber, didNumber, direction, status, language,
                    sttProvider, firstTranscript, receivedAt, answeredAt, capturedAt, endedAt, hangupCause,
                    websocketStatus, normalizedDid, communicationChannelId, tenantId, clinicId, locationId,
                    provider, routingStatus, routingFailureReason, normalizedCallerPhone,
                    patientIdentificationStatus, identifiedPatientId, identifiedPatientName, candidateCount,
                    identificationSource, interactionId, conversationPartyType, aivaSessionId, conversationMode,
                    turnCount, lastDetectedIntent, lastTool, lastAivaResponse, List.copyOf(updated));
        }

        public InboundVoiceResult withWebsocketStatus(String nextWebsocketStatus) {
            return new InboundVoiceResult(callId, callerPhoneNumber, didNumber, direction, status, language,
                    sttProvider, firstTranscript, receivedAt, answeredAt, capturedAt, endedAt, hangupCause,
                    nextWebsocketStatus, normalizedDid, communicationChannelId, tenantId, clinicId, locationId,
                    provider, routingStatus, routingFailureReason, normalizedCallerPhone, patientIdentificationStatus,
                    identifiedPatientId, identifiedPatientName, candidateCount, identificationSource, interactionId,
                    conversationPartyType, aivaSessionId, conversationMode, turnCount, lastDetectedIntent,
                    lastTool, lastAivaResponse, conversationTurns);
        }

        public InboundVoiceResult withInteraction(UUID nextInteractionId) {
            return new InboundVoiceResult(callId, callerPhoneNumber, didNumber, direction, status, language,
                    sttProvider, firstTranscript, receivedAt, answeredAt, capturedAt, endedAt, hangupCause,
                    websocketStatus, normalizedDid, communicationChannelId, tenantId, clinicId, locationId,
                    provider, routingStatus, routingFailureReason, normalizedCallerPhone, patientIdentificationStatus,
                    identifiedPatientId, identifiedPatientName, candidateCount, identificationSource,
                    nextInteractionId, conversationPartyType, aivaSessionId, conversationMode, turnCount,
                    lastDetectedIntent, lastTool, lastAivaResponse, conversationTurns);
        }

        public InboundVoiceResult withTtsDetails(String ttsText, String ttsProvider, boolean closing) {
            if (conversationTurns.isEmpty()) return this;
            List<InboundVoiceTurnDiagnostic> updated = new java.util.ArrayList<>(conversationTurns);
            InboundVoiceTurnDiagnostic prior = updated.get(updated.size() - 1);
            updated.set(updated.size() - 1, new InboundVoiceTurnDiagnostic(prior.turnNumber(), prior.callerTranscript(),
                    prior.sttProvider(), prior.detectedIntent(), prior.selectedTool(), prior.toolInputSpeciality(),
                    prior.toolResultCount(), prior.toolResultSummary(), prior.toolExecutionStatus(),
                    prior.policyDecision(), prior.aivaResponseText(), ttsText, ttsProvider, prior.startedAt(),
                    OffsetDateTime.now(), closing, prior.runtimeDiagnostics()));
            return new InboundVoiceResult(callId, callerPhoneNumber, didNumber, direction, status, language,
                    sttProvider, firstTranscript, receivedAt, answeredAt, capturedAt, endedAt, hangupCause,
                    websocketStatus, normalizedDid, communicationChannelId, tenantId, clinicId, locationId,
                    provider, routingStatus, routingFailureReason, normalizedCallerPhone,
                    patientIdentificationStatus, identifiedPatientId, identifiedPatientName, candidateCount,
                    identificationSource, interactionId, conversationPartyType, aivaSessionId, conversationMode,
                    turnCount, lastDetectedIntent, lastTool, lastAivaResponse, List.copyOf(updated));
        }

        public InboundVoiceResult withTurnPolicy(String policyDecision, String toolExecutionStatus) {
            if (conversationTurns.isEmpty()) return this;
            List<InboundVoiceTurnDiagnostic> updated = new java.util.ArrayList<>(conversationTurns);
            InboundVoiceTurnDiagnostic prior = updated.get(updated.size() - 1);
            updated.set(updated.size() - 1, new InboundVoiceTurnDiagnostic(prior.turnNumber(), prior.callerTranscript(),
                    prior.sttProvider(), prior.detectedIntent(), prior.selectedTool(), prior.toolInputSpeciality(),
                    prior.toolResultCount(), prior.toolResultSummary(), toolExecutionStatus,
                    policyDecision, prior.aivaResponseText(), prior.ttsText(), prior.ttsProvider(), prior.startedAt(),
                    prior.completedAt(), prior.closing(), prior.runtimeDiagnostics()));
            return new InboundVoiceResult(callId, callerPhoneNumber, didNumber, direction, status, language,
                    sttProvider, firstTranscript, receivedAt, answeredAt, capturedAt, endedAt, hangupCause,
                    websocketStatus, normalizedDid, communicationChannelId, tenantId, clinicId, locationId,
                    provider, routingStatus, routingFailureReason, normalizedCallerPhone,
                    patientIdentificationStatus, identifiedPatientId, identifiedPatientName, candidateCount,
                    identificationSource, interactionId, conversationPartyType, aivaSessionId, conversationMode,
                    turnCount, lastDetectedIntent, lastTool, lastAivaResponse, List.copyOf(updated));
        }

        private List<InboundVoiceTurnDiagnostic> appendTurn(InboundVoiceTurnDiagnostic turn) {
            List<InboundVoiceTurnDiagnostic> updated = new java.util.ArrayList<>(conversationTurns);
            updated.add(turn);
            return List.copyOf(updated);
        }
    }
}
