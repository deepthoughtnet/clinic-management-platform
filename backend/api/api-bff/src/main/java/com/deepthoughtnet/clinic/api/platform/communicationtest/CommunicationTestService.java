package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.email.Msg91EmailMessageProvider;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceMediaStreamClient;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.G711MuLawAudioConverter;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallRequest;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos.*;

@Service
public class CommunicationTestService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9()\\-\\s]{6,19}$");

    private final CarePilotMessagingStatusService messagingStatusService;
    private final MessageOrchestratorService messageOrchestratorService;
    private final List<MessageProvider> messageProviders;
    private final List<VoiceCallProvider> voiceProviders;
    private final Msg91EmailMessageProvider msg91EmailMessageProvider;
    private final ReminderVoiceRenderer reminderVoiceRenderer;
    private final DotVoiceMediaStreamClient dotVoiceMediaStreamClient;

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders, null, null, null);
    }

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders,
                msg91EmailMessageProvider, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider,
            ReminderVoiceRenderer reminderVoiceRenderer,
            DotVoiceMediaStreamClient dotVoiceMediaStreamClient
    ) {
        this.messagingStatusService = messagingStatusService;
        this.messageOrchestratorService = messageOrchestratorService;
        this.messageProviders = messageProviders == null ? List.of() : List.copyOf(messageProviders);
        this.voiceProviders = voiceProviders == null ? List.of() : List.copyOf(voiceProviders);
        this.msg91EmailMessageProvider = msg91EmailMessageProvider;
        this.reminderVoiceRenderer = reminderVoiceRenderer;
        this.dotVoiceMediaStreamClient = dotVoiceMediaStreamClient;
    }

    public HealthResponse health(String correlationId) {
        List<ChannelHealth> channels = messagingStatusService.providerStatuses().stream()
                .filter(row -> row.channel() == MessageChannel.EMAIL || row.channel() == MessageChannel.WHATSAPP)
                .filter(row -> !"msg91-email-smtp".equalsIgnoreCase(row.providerName()))
                .map(this::mapHealth)
                .toList();
        channels = new java.util.ArrayList<>(channels);
        if (msg91EmailMessageProvider != null) channels.add(msg91Health());
        VoiceCallProvider operationalVoiceProvider = operationalVoiceProvider();
        if (operationalVoiceProvider == null) {
            channels.add(new ChannelHealth("VOICE", "VOICE_NOT_CONFIGURED", "NOT_CONFIGURED",
                    "No production voice provider is configured.", false, false, false, null,
                    List.of("production voice provider"), OffsetDateTime.now()));
        } else {
            boolean ready = operationalVoiceProvider.isReady();
            channels.add(new ChannelHealth("VOICE", operationalVoiceProvider.providerName(), ready ? "READY" : "PARTIAL",
                    ready ? "Voice provider is ready." : "Voice provider is registered but not ready.", ready, true,
                    ready, null, ready ? List.of() : List.of("provider readiness"), OffsetDateTime.now()));
        }
        List<CapabilityHealth> capabilities = new java.util.ArrayList<>();
        if (reminderVoiceRenderer != null) {
            ReminderVoiceRenderer.Readiness tts = reminderVoiceRenderer.readiness();
            capabilities.add(new CapabilityHealth("REMINDER_TTS", tts.provider(), tts.status(),
                    tts.message() == null ? "Deterministic reminder TTS is ready." : tts.message()));
        }
        capabilities.add(new CapabilityHealth("DOTVOICE_STREAM", "dotvoice",
                dotVoiceMediaStreamClient != null && dotVoiceMediaStreamClient.isReady() ? "READY" : "NOT_CONFIGURED",
                dotVoiceMediaStreamClient != null && dotVoiceMediaStreamClient.isReady()
                        ? "DotVoice media stream is configured." : "DotVoice media streaming is not configured."));
        return new HealthResponse(List.copyOf(channels), correlationId, List.copyOf(capabilities));
    }

    public Result email(UUID tenantId, TestRequest request, String correlationId) {
        validateEmail(request);
        OffsetDateTime started = OffsetDateTime.now();
        String requestId = UUID.randomUUID().toString();
        if (StringUtils.hasText(request.provider()) && !"carepilot-email-smtp".equals(request.provider())) {
            if (!"msg91-email-smtp".equals(request.provider()) || msg91EmailMessageProvider == null || !msg91EmailMessageProvider.isConfigured()) {
                return failed(requestId, correlationId, "EMAIL", StringUtils.hasText(request.provider()) ? request.provider() : "EMAIL_NOT_CONFIGURED", started, "NOT_CONFIGURED", "Selected email provider is not configured.");
            }
        } else if (operationalMessageProvider(MessageChannel.EMAIL) == null) {
            return failed(requestId, correlationId, "EMAIL", "EMAIL_NOT_CONFIGURED", started, "NOT_CONFIGURED", "No production email provider is configured.");
        }
        MessageResult result;
        try {
            MessageRequest messageRequest = new MessageRequest(
                    tenantId, MessageChannel.EMAIL,
                    new MessageRecipient(request.recipient().trim(), null),
                    request.subject() == null ? "Jeevanam Email Test" : request.subject().trim(),
                    request.message().trim(), null, correlationId, null, UUID.fromString(requestId),
                    Map.of("communicationTest", "true", "testMode", mode(request)));
            result = "msg91-email-smtp".equals(request.provider())
                    ? msg91EmailMessageProvider.send(messageRequest)
                    : messageOrchestratorService.send(messageRequest);
        } catch (RuntimeException ex) {
            return failed(requestId, correlationId, "EMAIL", "carepilot-orchestrator", started, "DISPATCH_EXCEPTION", "Email test could not be dispatched.");
        }
        return messageResult(requestId, correlationId, "EMAIL", started, result);
    }

    public Result whatsapp(UUID tenantId, TestRequest request, String correlationId) {
        validatePhone(request.recipient());
        OffsetDateTime started = OffsetDateTime.now();
        String requestId = UUID.randomUUID().toString();
        if (operationalMessageProvider(MessageChannel.WHATSAPP) == null) {
            return failed(requestId, correlationId, "WHATSAPP", "WHATSAPP_NOT_CONFIGURED", started, "NOT_CONFIGURED", "No production WhatsApp provider is configured.");
        }
        MessageResult result;
        try {
            result = messageOrchestratorService.send(new MessageRequest(
                    tenantId, MessageChannel.WHATSAPP,
                    new MessageRecipient(request.recipient().trim(), null), null, request.message().trim(), null,
                    correlationId, null, UUID.fromString(requestId), Map.of("communicationTest", "true", "testMode", mode(request))));
        } catch (RuntimeException ex) {
            return failed(requestId, correlationId, "WHATSAPP", "carepilot-orchestrator", started, "DISPATCH_EXCEPTION", "WhatsApp test could not be dispatched.");
        }
        return messageResult(requestId, correlationId, "WHATSAPP", started, result);
    }

    public Result voice(UUID tenantId, TestRequest request, String correlationId) {
        validatePhone(request.recipient());
        OffsetDateTime started = OffsetDateTime.now();
        String requestId = UUID.randomUUID().toString();
        VoiceCallProvider provider = operationalVoiceProvider(request.provider());
        if (provider == null) {
            return failed(requestId, correlationId, "VOICE", "VOICE_NOT_CONFIGURED", started, "NOT_CONFIGURED", "No production voice provider is configured.");
        }
        if (!provider.isReady()) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "NOT_READY", "Voice provider is not ready for outbound calls.");
        }
        if (request.mode() == TestMode.REMINDER_SIMULATION) {
            return reminderVoice(tenantId, request, correlationId, provider, requestId, started);
        }
        try {
            VoiceCallResult result = provider.placeCall(new VoiceCallRequest(
                    tenantId, null, UUID.fromString(requestId), request.recipient().trim(), request.message().trim(), started,
                    Map.of("communicationTest", "true", "correlationId", correlationId)));
            return new Result(requestId, correlationId, "VOICE", result.providerName(), result.providerCallId(),
                    result.status().name(), result.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.COMPLETED
                            || result.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.QUEUED,
                    result.startedAt() == null ? started : result.startedAt(), result.endedAt() == null ? OffsetDateTime.now() : result.endedAt(),
                    result.failureReason(), sanitize(result.failureReason()));
        } catch (RuntimeException ex) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "PROVIDER_ERROR", "Voice provider test failed.");
        }
    }

    private Result reminderVoice(UUID tenantId, TestRequest request, String correlationId,
                                 VoiceCallProvider provider, String requestId, OffsetDateTime started) {
        if (!"dotvoice".equalsIgnoreCase(provider.providerName()) || reminderVoiceRenderer == null || dotVoiceMediaStreamClient == null) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "PLAYBACK_NOT_CONFIGURED", "Reminder playback is not configured.");
        }
        ReminderVoiceRenderer.RenderedReminder rendered = reminderVoiceRenderer.render(request.message().trim(), request.language());
        if (rendered.audio() == null) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, rendered.failureCategory(), "Reminder audio could not be prepared.");
        }
        List<byte[]> frames;
        try {
            ReminderAudioNormalizer.CanonicalPcm pcm = ReminderAudioNormalizer.normalize(
                    rendered.audio().audioBytes(), rendered.audio().contentType(), rendered.sourceSampleRate());
            frames = G711MuLawAudioConverter.toFramesPcm(pcm.bytes(), pcm.sampleRate(), pcm.channels());
            org.slf4j.LoggerFactory.getLogger(CommunicationTestService.class).info(
                    "REMINDER_AUDIO_PREPARED provider={} inputFormat={} inputBytes={} targetEncoding=mulaw targetSampleRate=8000 frameSize=160 frameCount={} ready=true",
                    rendered.provider(), pcm.inputFormat(), rendered.audio().audioBytes().length, frames.size());
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(CommunicationTestService.class).warn(
                    "REMINDER_AUDIO_PREPARE_FAILED provider={} reason={}", provider.providerName(),
                    ex.getMessage() == null ? "audio_normalization_failed" : ex.getMessage());
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "AUDIO_FORMAT_FAILED", "Reminder audio format could not be prepared.");
        }
        if (!dotVoiceMediaStreamClient.prepare()) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started,
                    "STREAM_READY_NOT_RECEIVED", "DotVoice media stream is not ready; no call was placed.");
        }
        try {
            VoiceCallResult call = provider.placeCall(new VoiceCallRequest(
                    tenantId, null, UUID.fromString(requestId), request.recipient().trim(), request.message().trim(), started,
                    Map.of("communicationTest", "true", "correlationId", correlationId, "reminderPlayback", "true")));
            if (call.providerCallId() == null || call.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.NO_ANSWER
                    || call.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.FAILED) {
                dotVoiceMediaStreamClient.abort();
                return new Result(requestId, correlationId, "VOICE", call.providerName(), call.providerCallId(), call.status().name(), false,
                        started, OffsetDateTime.now(), call.failureReason(), sanitize(call.failureReason()));
            }
            DotVoiceMediaStreamClient.PlaybackResult playback = dotVoiceMediaStreamClient.play(call.providerCallId(), frames);
            return new Result(requestId, correlationId, "VOICE", call.providerName(), call.providerCallId(), playback.status(), playback.success(),
                    started, OffsetDateTime.now(), playback.failureCategory(), sanitize(playback.failureCategory()));
        } catch (RuntimeException ex) {
            dotVoiceMediaStreamClient.abort();
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "PLAYBACK_FAILED", "Reminder playback failed.");
        }
    }

    private ChannelHealth mapHealth(ProviderStatusResponse row) {
        String status = row.status().name();
        if ("DISABLED".equals(status)) status = "NOT_CONFIGURED";
        boolean operational = isOperationalProvider(row.providerName());
        if (!operational) status = "NOT_CONFIGURED";
        String message = operational ? row.message() : "No production " + row.channel().name().toLowerCase(java.util.Locale.ROOT) + " provider is configured.";
        return new ChannelHealth(row.channel().name(), row.providerName(), status, message, operational && row.configured(),
                operational && row.available(), operational && row.supportsTestSend(), row.channel() == MessageChannel.EMAIL && row.fromAddressConfigured() && operational ? "configured" : null,
                operational ? row.missingConfigurationKeys() : List.of("production provider"), row.lastCheckedAt());
    }

    private ChannelHealth msg91Health() {
        boolean ready = msg91EmailMessageProvider.isConfigured();
        List<String> missing = new java.util.ArrayList<>();
        if (!ready) {
            missing.add("clinic.carepilot.messaging.email.providers.msg91.enabled");
            if (!StringUtils.hasText(msg91EmailMessageProvider.host())) missing.add("clinic.carepilot.messaging.email.providers.msg91.host");
            if (msg91EmailMessageProvider.port() <= 0 || msg91EmailMessageProvider.port() > 65535) missing.add("clinic.carepilot.messaging.email.providers.msg91.port");
            if (!StringUtils.hasText(msg91EmailMessageProvider.username())) missing.add("clinic.carepilot.messaging.email.providers.msg91.username");
            if (!msg91EmailMessageProvider.passwordConfigured()) missing.add("clinic.carepilot.messaging.email.providers.msg91.password");
            if (!msg91EmailMessageProvider.fromConfigured()) missing.add("clinic.carepilot.messaging.email.providers.msg91.from");
        }
        return new ChannelHealth("EMAIL", msg91EmailMessageProvider.providerName(), ready ? "READY" : "NOT_CONFIGURED",
                ready ? "MSG91 SMTP provider is configured and ready." : "MSG91 SMTP configuration is incomplete.",
                ready, ready, ready, ready ? "configured" : null, List.copyOf(missing), OffsetDateTime.now());
    }

    private VoiceCallProvider operationalVoiceProvider() {
        return operationalVoiceProvider(null);
    }

    private VoiceCallProvider operationalVoiceProvider(String requestedProvider) {
        return voiceProviders.stream()
                .filter(provider -> isOperationalProvider(provider.providerName()))
                .filter(provider -> !StringUtils.hasText(requestedProvider)
                        || provider.providerName().equalsIgnoreCase(requestedProvider.trim()))
                .findFirst()
                .orElse(null);
    }

    private MessageProvider operationalMessageProvider(MessageChannel channel) {
        return messageProviders.stream()
                .filter(provider -> provider.supports(channel))
                // MSG91 is selected explicitly by the Communication Test request; keep it out
                // of the legacy implicit test-provider path.
                .filter(provider -> !"msg91-email-smtp".equalsIgnoreCase(provider.providerName()))
                .filter(provider -> isOperationalProvider(provider.providerName()))
                .findFirst()
                .orElse(null);
    }

    /** No provider metadata exists in the current SPIs; classify conventional test-only names locally. */
    private boolean isOperationalProvider(String providerName) {
        if (!StringUtils.hasText(providerName)) return false;
        String normalized = providerName.trim().toLowerCase(java.util.Locale.ROOT);
        return !normalized.matches(".*(?:^|[-_:])(mock|test|fake|stub)(?:$|[-_:]).*")
                && !normalized.matches("^(mock|test|fake|stub).*");
    }

    private void validateEmail(TestRequest request) {
        if (request == null || !StringUtils.hasText(request.recipient()) || !EMAIL.matcher(request.recipient().trim()).matches()) {
            throw new IllegalArgumentException("Recipient must be a valid email address");
        }
        if (!StringUtils.hasText(request.message())) throw new IllegalArgumentException("Message is required");
        if (request.subject() != null && request.subject().length() > 200) throw new IllegalArgumentException("Subject is too long");
    }

    private void validatePhone(String value) {
        if (!StringUtils.hasText(value) || !PHONE.matcher(value.trim()).matches()) throw new IllegalArgumentException("Recipient must be a valid phone number");
    }

    private Result messageResult(String requestId, String correlationId, String channel, OffsetDateTime started, MessageResult result) {
        return new Result(requestId, correlationId, channel, result.providerName(), result.providerMessageId(), result.status().name(), result.success(),
                started, OffsetDateTime.now(), result.errorCode(), sanitize(result.errorMessage()));
    }

    private Result failed(String requestId, String correlationId, String channel, String provider, OffsetDateTime started, String category, String message) {
        return new Result(requestId, correlationId, channel, provider, null, MessageDeliveryStatus.FAILED.name(), false, started, OffsetDateTime.now(), category, message);
    }

    private String mode(TestRequest request) { return request.mode() == null ? TestMode.PROVIDER_ONLY.name() : request.mode().name(); }
    private String sanitize(String value) { return value == null ? null : value.replaceAll("(?i)(api[-_ ]?key|auth[-_ ]?key|password|secret)[=:][^,; ]+", "$1=[redacted]"); }
}
