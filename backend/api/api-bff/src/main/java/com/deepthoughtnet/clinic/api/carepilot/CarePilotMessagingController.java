package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderTestSendRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderTestSendResponse;
import com.deepthoughtnet.clinic.carepilot.messaging.exception.MessageDispatchException;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CarePilot messaging provider visibility and diagnostics APIs.
 */
@RestController
@RequestMapping("/api/carepilot/messaging/providers")
public class CarePilotMessagingController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9][0-9()\\-\\s]{6,19}$");
    private final CarePilotMessagingStatusService messagingStatusService;
    private final MessageOrchestratorService messageOrchestratorService;

    public CarePilotMessagingController(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService
    ) {
        this.messagingStatusService = messagingStatusService;
        this.messageOrchestratorService = messageOrchestratorService;
    }

    @GetMapping("/status")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<ProviderStatusResponse> status() {
        RequestContextHolder.requireTenantId();
        return messagingStatusService.providerStatuses();
    }

    @PostMapping("/{channel}/test-send")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ProviderTestSendResponse testSend(
            @PathVariable MessageChannel channel,
            @Valid @RequestBody ProviderTestSendRequest request
    ) {
        if (channel != MessageChannel.EMAIL && channel != MessageChannel.SMS && channel != MessageChannel.WHATSAPP) {
            throw new IllegalArgumentException("Test send is supported only for EMAIL, SMS, or WHATSAPP");
        }
        validateTestSendPayload(channel, request);
        UUID tenantId = RequestContextHolder.requireTenantId();
        MessageResult result;
        try {
            result = messageOrchestratorService.send(new MessageRequest(
                    tenantId,
                    channel,
                    new MessageRecipient(request.recipient().trim(), null),
                    channel == MessageChannel.EMAIL ? request.subject().trim() : null,
                    request.body().trim(),
                    null,
                    "carepilot-provider-test",
                    null,
                    UUID.randomUUID(),
                    Map.of("testSend", "true")
            ));
        } catch (MessageDispatchException ex) {
            result = new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    "carepilot-orchestrator",
                    null,
                    "DISPATCH_EXCEPTION",
                    ex.getMessage(),
                    null
            );
        }

        return new ProviderTestSendResponse(
                channel,
                result.success(),
                result.status(),
                result.providerName(),
                result.providerMessageId(),
                result.errorCode(),
                result.errorMessage(),
                result.sentAt() == null ? OffsetDateTime.now() : result.sentAt()
        );
    }

    private void validateTestSendPayload(MessageChannel channel, ProviderTestSendRequest request) {
        String recipient = request.recipient() == null ? "" : request.recipient().trim();
        if (channel == MessageChannel.EMAIL) {
            if (!EMAIL_PATTERN.matcher(recipient).matches()) {
                throw new IllegalArgumentException("Recipient must be a valid email address for EMAIL test sends");
            }
            if (request.subject() == null || request.subject().isBlank()) {
                throw new IllegalArgumentException("Subject is required for EMAIL test sends");
            }
        } else if (!PHONE_PATTERN.matcher(recipient).matches()) {
            throw new IllegalArgumentException("Recipient must be a valid phone number for SMS/WHATSAPP test sends");
        }
        if (request.body() == null || request.body().isBlank()) {
            throw new IllegalArgumentException("Body is required for provider test sends");
        }
    }
}
