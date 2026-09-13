package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Runs read-only skills off the request thread while keeping result application
 * behind the supplied current-execution guard.
 */
final class PatientPortalCareAiAsyncSkillExecutor implements AutoCloseable {
    private final PatientPortalCareAiExecutionTracker tracker;
    private final PatientPortalCareAiWaitingPolicy waitingPolicy;
    private final ExecutorService workers;
    private final ScheduledExecutorService timers;

    PatientPortalCareAiAsyncSkillExecutor(PatientPortalCareAiExecutionTracker tracker) {
        this(tracker, Executors.newCachedThreadPool(), Executors.newSingleThreadScheduledExecutor());
    }

    PatientPortalCareAiAsyncSkillExecutor(PatientPortalCareAiExecutionTracker tracker,
                                          ExecutorService workers,
                                          ScheduledExecutorService timers) {
        this.tracker = tracker;
        this.waitingPolicy = new PatientPortalCareAiWaitingPolicy();
        this.workers = workers;
        this.timers = timers;
    }

    <T> PatientPortalCareAiAsyncSkillExecution submit(
            PatientPortalCareAiExecutionIdentity identity,
            Callable<PatientPortalCareAiSkillResult<T>> work,
            Consumer<PatientPortalCareAiSkillProgressEvent> progressConsumer,
            BiConsumer<PatientPortalCareAiExecutionIdentity, PatientPortalCareAiSkillResult<T>> resultConsumer
    ) {
        AtomicBoolean progressDelivered = new AtomicBoolean();
        Future<?> future = workers.submit(() -> {
            PatientPortalCareAiSkillResult<T> result;
            try {
                result = work.call();
            } catch (Exception ex) {
                result = PatientPortalCareAiSkillResult.failed("Skill execution failed.");
            }
            PatientPortalCareAiSkillResult<T> guarded = tracker.isCurrent(identity)
                    ? result
                    : PatientPortalCareAiSkillResult.stale("Skill result is no longer current.");
            resultConsumer.accept(identity, guarded);
        });
        timers.schedule(() -> {
            if (!future.isDone() && tracker.isCurrent(identity) && progressDelivered.compareAndSet(false, true)
                    && waitingPolicy.shouldAcknowledge(PatientPortalCareAiWaitingPolicy.ACKNOWLEDGEMENT_THRESHOLD)) {
                progressConsumer.accept(new PatientPortalCareAiSkillProgressEvent(
                        identity,
                        PatientPortalCareAiSkillOutcome.RUNNING.name(),
                        progressKey(identity.skillId()),
                        waitingPolicy.acknowledgement(identity.skillId())
                ));
            }
        }, PatientPortalCareAiWaitingPolicy.ACKNOWLEDGEMENT_THRESHOLD.toMillis(), TimeUnit.MILLISECONDS);
        return new PatientPortalCareAiAsyncSkillExecution(tracker, identity, future);
    }

    private String progressKey(String skillId) {
        return switch (skillId) {
            case "doctor.find" -> "checking-doctors";
            case "availability.check" -> "checking-availability";
            case "appointment.check" -> "checking-appointments";
            default -> "checking-request";
        };
    }

    @Override
    public void close() {
        workers.shutdownNow();
        timers.shutdownNow();
    }
}
