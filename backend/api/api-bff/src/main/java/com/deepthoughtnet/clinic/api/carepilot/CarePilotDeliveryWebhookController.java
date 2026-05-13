package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignDeliveryWebhookService;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignDeliveryWebhookService.ProviderDeliveryEventCommand;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider webhook ingestion endpoints for CarePilot post-send delivery lifecycle updates.
 */
@RestController
@RequestMapping("/api/carepilot/webhooks")
public class CarePilotDeliveryWebhookController {
    private static final Logger log = LoggerFactory.getLogger(CarePilotDeliveryWebhookController.class);

    private final CampaignDeliveryWebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final String whatsappVerifyToken;
    private final String whatsappAppSecret;
    private final String smsWebhookSecret;

    public CarePilotDeliveryWebhookController(
            CampaignDeliveryWebhookService webhookService,
            ObjectMapper objectMapper,
            @Value("${carepilot.messaging.whatsapp.webhook-verify-token:}") String whatsappVerifyToken,
            @Value("${carepilot.messaging.whatsapp.app-secret:}") String whatsappAppSecret,
            @Value("${carepilot.messaging.sms.webhook-secret:}") String smsWebhookSecret
    ) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.whatsappVerifyToken = whatsappVerifyToken;
        this.whatsappAppSecret = whatsappAppSecret;
        this.smsWebhookSecret = smsWebhookSecret;
    }

    /**
     * Meta webhook verification challenge endpoint.
     */
    @GetMapping("/whatsapp/meta")
    public ResponseEntity<String> verifyWhatsAppWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if (!"subscribe".equalsIgnoreCase(mode)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid mode");
        }
        if (!StringUtils.hasText(whatsappVerifyToken) || !safeEquals(whatsappVerifyToken, verifyToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
        }
        return ResponseEntity.ok(challenge == null ? "" : challenge);
    }

    /**
     * Meta WhatsApp status webhook ingestion.
     */
    @PostMapping("/whatsapp/meta")
    public ResponseEntity<String> ingestWhatsAppWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature
    ) {
        if (StringUtils.hasText(whatsappAppSecret) && !validateMetaSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode entries = root.path("entry");
            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    JsonNode changes = entry.path("changes");
                    if (!changes.isArray()) {
                        continue;
                    }
                    for (JsonNode change : changes) {
                        JsonNode statuses = change.path("value").path("statuses");
                        if (!statuses.isArray()) {
                            continue;
                        }
                        for (JsonNode statusNode : statuses) {
                            String providerMessageId = text(statusNode, "id");
                            if (!StringUtils.hasText(providerMessageId)) {
                                continue;
                            }
                            String externalStatus = text(statusNode, "status");
                            MessageDeliveryStatus internalStatus = mapWhatsAppStatus(externalStatus);
                            String errorDetail = statusNode.path("errors").isArray() && statusNode.path("errors").size() > 0
                                    ? text(statusNode.path("errors").get(0), "message")
                                    : null;
                            String eventType = errorDetail == null ? "WHATSAPP_STATUS" : "WHATSAPP_STATUS_ERROR";
                            webhookService.applyProviderDeliveryEvent(new ProviderDeliveryEventCommand(
                                    "carepilot-whatsapp-meta-cloud-api",
                                    providerMessageId,
                                    ChannelType.WHATSAPP,
                                    externalStatus,
                                    internalStatus,
                                    eventType,
                                    epochSecondsToOffsetDateTime(text(statusNode, "timestamp")),
                                    payload
                            ));
                        }
                    }
                }
            }
            return ResponseEntity.ok("ok");
        } catch (Exception ex) {
            log.warn("CarePilot WhatsApp webhook parsing failed: {}", ex.toString());
            return ResponseEntity.ok("ignored");
        }
    }

    /**
     * Generic SMS provider webhook ingestion.
     */
    @PostMapping("/sms/generic")
    public ResponseEntity<String> ingestSmsWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "X-CarePilot-Webhook-Secret", required = false) String sharedSecret
    ) {
        if (StringUtils.hasText(smsWebhookSecret) && !safeEquals(smsWebhookSecret, sharedSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid secret");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String providerMessageId = firstNonBlank(
                    text(root, "providerMessageId"),
                    text(root, "messageId"),
                    text(root, "id")
            );
            if (!StringUtils.hasText(providerMessageId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("providerMessageId is required");
            }
            String externalStatus = firstNonBlank(text(root, "status"), text(root, "deliveryStatus"));
            webhookService.applyProviderDeliveryEvent(new ProviderDeliveryEventCommand(
                    firstNonBlank(text(root, "providerName"), "carepilot-sms-generic-http"),
                    providerMessageId,
                    ChannelType.SMS,
                    externalStatus,
                    mapSmsStatus(externalStatus),
                    "SMS_STATUS",
                    epochSecondsToOffsetDateTime(firstNonBlank(text(root, "timestamp"), text(root, "eventTimestamp"))),
                    payload
            ));
            return ResponseEntity.ok("ok");
        } catch (Exception ex) {
            log.warn("CarePilot SMS webhook parsing failed: {}", ex.toString());
            return ResponseEntity.ok("ignored");
        }
    }

    private boolean validateMetaSignature(String payload, String headerValue) {
        if (!StringUtils.hasText(headerValue) || !headerValue.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(whatsappAppSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + bytesToHex(digest);
            return safeEquals(expected, headerValue);
        } catch (Exception ex) {
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private OffsetDateTime epochSecondsToOffsetDateTime(String epochSeconds) {
        if (!StringUtils.hasText(epochSeconds)) {
            return OffsetDateTime.now();
        }
        try {
            long value = Long.parseLong(epochSeconds.trim());
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneOffset.UTC);
        } catch (NumberFormatException ex) {
            return OffsetDateTime.now();
        }
    }

    private MessageDeliveryStatus mapWhatsAppStatus(String externalStatus) {
        if (!StringUtils.hasText(externalStatus)) {
            return MessageDeliveryStatus.UNKNOWN;
        }
        return switch (externalStatus.trim().toLowerCase(Locale.ROOT)) {
            case "sent" -> MessageDeliveryStatus.SENT;
            case "delivered" -> MessageDeliveryStatus.DELIVERED;
            case "read" -> MessageDeliveryStatus.READ;
            case "failed" -> MessageDeliveryStatus.FAILED;
            default -> MessageDeliveryStatus.UNKNOWN;
        };
    }

    private MessageDeliveryStatus mapSmsStatus(String externalStatus) {
        if (!StringUtils.hasText(externalStatus)) {
            return MessageDeliveryStatus.UNKNOWN;
        }
        return switch (externalStatus.trim().toLowerCase(Locale.ROOT)) {
            case "queued" -> MessageDeliveryStatus.QUEUED;
            case "sent" -> MessageDeliveryStatus.SENT;
            case "delivered" -> MessageDeliveryStatus.DELIVERED;
            case "read" -> MessageDeliveryStatus.READ;
            case "failed" -> MessageDeliveryStatus.FAILED;
            case "bounced" -> MessageDeliveryStatus.BOUNCED;
            case "undelivered" -> MessageDeliveryStatus.UNDELIVERED;
            default -> MessageDeliveryStatus.UNKNOWN;
        };
    }
}
