package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class PatientPortalCareAiWriteReconciliationPolicy {
    String idempotencyKey(String conversationId, String action, String skillExecutionId) {
        String input = String.join("|", nullToBlank(conversationId), nullToBlank(action), nullToBlank(skillExecutionId));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("careai-");
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for write idempotency", ex);
        }
    }

    PatientPortalCareAiWriteReconciliationStatus reconcile(boolean authoritativeMatch,
                                                           boolean authoritativeNoMatch,
                                                           boolean dependencyStillUnavailable) {
        if (authoritativeMatch) {
            return PatientPortalCareAiWriteReconciliationStatus.SUCCESS;
        }
        if (dependencyStillUnavailable || (!authoritativeNoMatch)) {
            return PatientPortalCareAiWriteReconciliationStatus.STILL_UNKNOWN;
        }
        return PatientPortalCareAiWriteReconciliationStatus.DEFINITELY_NOT_EXECUTED;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
