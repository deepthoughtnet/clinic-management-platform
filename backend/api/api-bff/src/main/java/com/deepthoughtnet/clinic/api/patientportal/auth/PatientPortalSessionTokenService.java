package com.deepthoughtnet.clinic.api.patientportal.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PatientPortalSessionTokenService {
    public static final String SESSION_HEADER = "X-Patient-Session";

    private final PatientPortalAuthProperties properties;
    private final ObjectMapper objectMapper;

    public PatientPortalSessionTokenService(PatientPortalAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String issueToken(String subject, UUID tenantId, UUID patientId, String displayName) {
        try {
            OffsetDateTime issuedAt = OffsetDateTime.now();
            OffsetDateTime expiresAt = issuedAt.plus(properties.getSessionTtl());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", subject);
            payload.put("tenantId", tenantId.toString());
            payload.put("patientId", patientId.toString());
            payload.put("displayName", displayName);
            payload.put("roles", Set.of("PATIENT"));
            payload.put("iat", issuedAt.toString());
            payload.put("exp", expiresAt.toString());

            String encodedPayload = base64Url(objectMapper.writeValueAsBytes(payload));
            String signature = sign(encodedPayload);
            return encodedPayload + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to issue patient portal session token", ex);
        }
    }

    public PatientPortalSessionPrincipal parse(String token) {
        try {
            if (token == null || token.isBlank()) {
                return null;
            }
            String[] parts = token.trim().split("\\.");
            if (parts.length != 2) {
                return null;
            }
            String expectedSignature = sign(parts[0]);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, new TypeReference<>() {});

            OffsetDateTime expiresAt = OffsetDateTime.parse(String.valueOf(payload.get("exp")));
            if (expiresAt.isBefore(OffsetDateTime.now())) {
                return null;
            }

            Set<String> roles = payload.get("roles") instanceof Collection<?> collection
                    ? collection.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet())
                    : Set.of("PATIENT");

            return new PatientPortalSessionPrincipal(
                    String.valueOf(payload.get("sub")),
                    UUID.fromString(String.valueOf(payload.get("tenantId"))),
                    UUID.fromString(String.valueOf(payload.get("patientId"))),
                    String.valueOf(payload.get("displayName")),
                    roles
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getSessionSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64Url(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
