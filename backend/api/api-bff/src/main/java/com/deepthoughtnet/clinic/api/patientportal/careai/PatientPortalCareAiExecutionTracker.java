package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Guards asynchronous or delayed skill results from mutating a newer turn. */
final class PatientPortalCareAiExecutionTracker {
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, PatientPortalCareAiExecutionIdentity> active = new ConcurrentHashMap<>();

    PatientPortalCareAiExecutionIdentity begin(String conversationId, String turnId, String skillId, boolean readOnly) {
        PatientPortalCareAiExecutionIdentity identity = new PatientPortalCareAiExecutionIdentity(
                conversationId,
                turnId,
                "skill-execution-" + sequence.incrementAndGet(),
                skillId,
                readOnly
        );
        active.put(conversationId, identity);
        return identity;
    }

    boolean isCurrent(PatientPortalCareAiExecutionIdentity identity) {
        return identity != null && identity.equals(active.get(identity.conversationId()));
    }

    void invalidate(PatientPortalCareAiExecutionIdentity identity) {
        if (identity != null) {
            active.remove(identity.conversationId(), identity);
        }
    }

    void invalidateReadOnly(String conversationId) {
        active.computeIfPresent(conversationId, (ignored, identity) -> identity.readOnly() ? null : identity);
    }

    void invalidateConversation(String conversationId) {
        active.remove(conversationId);
    }
}
