package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.concurrent.Future;

final class PatientPortalCareAiAsyncSkillExecution implements AutoCloseable {
    private final PatientPortalCareAiExecutionTracker tracker;
    private final PatientPortalCareAiExecutionIdentity identity;
    private final Future<?> future;

    PatientPortalCareAiAsyncSkillExecution(PatientPortalCareAiExecutionTracker tracker,
                                            PatientPortalCareAiExecutionIdentity identity,
                                            Future<?> future) {
        this.tracker = tracker;
        this.identity = identity;
        this.future = future;
    }

    void cancelReadOnly() {
        if (identity.readOnly()) {
            tracker.invalidate(identity);
            future.cancel(true);
        }
    }

    @Override
    public void close() {
        cancelReadOnly();
    }
}
