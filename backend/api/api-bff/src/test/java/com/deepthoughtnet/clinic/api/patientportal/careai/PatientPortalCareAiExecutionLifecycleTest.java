package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiExecutionLifecycleTest {
    @Test
    void newerTurnMakesPreviousReadOnlyExecutionStale() {
        PatientPortalCareAiExecutionTracker tracker = new PatientPortalCareAiExecutionTracker();
        PatientPortalCareAiExecutionIdentity generalMedicine = tracker.begin("conversation-1", "turn-1", "doctor.find", true);
        PatientPortalCareAiExecutionIdentity cardiology = tracker.begin("conversation-1", "turn-2", "doctor.find", true);

        assertThat(tracker.isCurrent(generalMedicine)).isFalse();
        assertThat(tracker.isCurrent(cardiology)).isTrue();
    }

    @Test
    void readOnlyExecutionCanBeInvalidatedWithoutCancellingWrites() {
        PatientPortalCareAiExecutionTracker tracker = new PatientPortalCareAiExecutionTracker();
        PatientPortalCareAiExecutionIdentity search = tracker.begin("conversation-1", "turn-1", "doctor.find", true);
        assertThat(tracker.isCurrent(search)).isTrue();

        tracker.invalidateReadOnly("conversation-1");

        assertThat(tracker.isCurrent(search)).isFalse();
    }

    @Test
    void fallbackPolicyDistinguishesNoMatchTimeoutAndWriteTimeout() {
        PatientPortalCareAiFallbackPolicyRegistry policy = new PatientPortalCareAiFallbackPolicyRegistry();

        assertThat(policy.actionFor("availability.check", PatientPortalCareAiSkillOutcome.NO_MATCH))
                .isEqualTo(PatientPortalCareAiFallbackAction.OFFER_ALTERNATIVE);
        assertThat(policy.actionFor("availability.check", PatientPortalCareAiSkillOutcome.TIMEOUT))
                .isEqualTo(PatientPortalCareAiFallbackAction.RETRY_OR_CONTACT);
        assertThat(policy.actionFor("appointment.book", PatientPortalCareAiSkillOutcome.TIMEOUT))
                .isEqualTo(PatientPortalCareAiFallbackAction.RECONCILE_WRITE);
    }

    @Test
    void callToBookDoesNotUseLiveAvailabilityFallback() {
        PatientPortalCareAiFallbackPolicyRegistry policy = new PatientPortalCareAiFallbackPolicyRegistry();

        assertThat(policy.actionFor("doctor.find", PatientPortalCareAiSkillOutcome.NOT_BOOKABLE))
                .isEqualTo(PatientPortalCareAiFallbackAction.CALL_TO_BOOK);
    }

    @Test
    void waitingAcknowledgementIsSuppressedForFastSkills() {
        PatientPortalCareAiWaitingPolicy policy = new PatientPortalCareAiWaitingPolicy();

        assertThat(policy.shouldAcknowledge(Duration.ofMillis(699))).isFalse();
        assertThat(policy.shouldAcknowledge(Duration.ofMillis(700))).isTrue();
        assertThat(policy.acknowledgement("availability.check")).isEqualTo("Let me check available times.");
    }

    @Test
    void skillResultHasExplicitLifecycleOutcomes() {
        assertThat(PatientPortalCareAiSkillResult.timeout("timed out").outcome())
                .isEqualTo(PatientPortalCareAiSkillOutcome.TIMEOUT);
        assertThat(PatientPortalCareAiSkillResult.temporarilyUnavailable("unavailable").outcome())
                .isEqualTo(PatientPortalCareAiSkillOutcome.TEMPORARILY_UNAVAILABLE);
        assertThat(PatientPortalCareAiSkillResult.stale("obsolete").outcome())
                .isEqualTo(PatientPortalCareAiSkillOutcome.STALE);
    }

    @Test
    void slowReadOnlySkillEmitsOneProgressEventAndThenItsResult() throws Exception {
        PatientPortalCareAiExecutionTracker tracker = new PatientPortalCareAiExecutionTracker();
        PatientPortalCareAiExecutionIdentity identity = tracker.begin("conversation-1", "turn-1", "doctor.find", true);
        CountDownLatch completed = new CountDownLatch(1);
        List<PatientPortalCareAiSkillProgressEvent> progress = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<PatientPortalCareAiSkillResult<String>> result = new AtomicReference<>();

        try (PatientPortalCareAiAsyncSkillExecutor executor = new PatientPortalCareAiAsyncSkillExecutor(tracker)) {
            executor.submit(identity,
                    () -> {
                        Thread.sleep(900);
                        return PatientPortalCareAiSkillResult.success("doctors", "found");
                    },
                    progress::add,
                    (ignored, value) -> {
                        result.set(value);
                        completed.countDown();
                    });

            assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(progress).hasSize(1);
            assertThat(progress.getFirst().acknowledgement()).isEqualTo("Let me check available doctors.");
            assertThat(result.get().outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.SUCCESS);
        }
    }

    @Test
    void staleReadOnlyResultIsDeliveredAsStaleAndCannotApply() throws Exception {
        PatientPortalCareAiExecutionTracker tracker = new PatientPortalCareAiExecutionTracker();
        PatientPortalCareAiExecutionIdentity oldSearch = tracker.begin("conversation-1", "turn-1", "doctor.find", true);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<PatientPortalCareAiSkillResult<String>> result = new AtomicReference<>();

        try (PatientPortalCareAiAsyncSkillExecutor executor = new PatientPortalCareAiAsyncSkillExecutor(tracker)) {
            executor.submit(oldSearch,
                    () -> {
                        Thread.sleep(250);
                        return PatientPortalCareAiSkillResult.success("old doctors", "found");
                    },
                    ignored -> { },
                    (ignored, value) -> {
                        result.set(value);
                        completed.countDown();
                    });
            tracker.begin("conversation-1", "turn-2", "doctor.find", true);

            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get().outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.STALE);
        }
    }

    @Test
    void writeIdempotencyKeyIsStableAndUnknownTimeoutIsNeverRetriedBlindly() {
        PatientPortalCareAiWriteReconciliationPolicy policy = new PatientPortalCareAiWriteReconciliationPolicy();
        String first = policy.idempotencyKey("conversation-1", "appointment.book", "skill-execution-1");
        String second = policy.idempotencyKey("conversation-1", "appointment.book", "skill-execution-1");

        assertThat(first).isEqualTo(second);
        assertThat(policy.reconcile(false, false, true))
                .isEqualTo(PatientPortalCareAiWriteReconciliationStatus.STILL_UNKNOWN);
        assertThat(policy.reconcile(true, false, false))
                .isEqualTo(PatientPortalCareAiWriteReconciliationStatus.SUCCESS);
    }
}
