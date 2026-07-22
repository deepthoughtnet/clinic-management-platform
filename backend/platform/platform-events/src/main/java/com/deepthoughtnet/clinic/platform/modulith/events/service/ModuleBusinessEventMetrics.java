package com.deepthoughtnet.clinic.platform.modulith.events.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ModuleBusinessEventMetrics {
    private static final String PREFIX = "jeevanam.module_events";
    private final MeterRegistry meterRegistry;
    private final AtomicLong pendingGauge = new AtomicLong();
    private final AtomicLong deadLetteredGauge = new AtomicLong();

    public ModuleBusinessEventMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        if (this.meterRegistry != null) {
            this.meterRegistry.gauge(PREFIX + ".listener.pending", pendingGauge, AtomicLong::get);
            this.meterRegistry.gauge(PREFIX + ".listener.dead_lettered", deadLetteredGauge, AtomicLong::get);
        }
    }

    public void published(String eventType) {
        counter("published", eventType);
    }

    public void listenerAttempt(String listenerName, String eventType) {
        counter("listener.executions", eventType, listenerName);
    }

    public void listenerFailure(String listenerName, String eventType, String status) {
        counter("listener.failures", eventType, listenerName, status);
    }

    public void listenerRetry(String listenerName, String eventType) {
        counter("listener.retries", eventType, listenerName);
    }

    public Timer.Sample startTimer() {
        return meterRegistry == null ? null : Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample, String eventType, String listenerName, String outcome) {
        if (meterRegistry == null || sample == null) {
            return;
        }
        sample.stop(Timer.builder(PREFIX + ".listener.duration")
                .description("Module business event listener execution duration")
                .tag("eventType", safe(eventType))
                .tag("listener", safe(listenerName))
                .tag("outcome", safe(outcome))
                .register(meterRegistry));
    }

    public void gaugePending(long pendingCount) {
        pendingGauge.set(Math.max(0, pendingCount));
    }

    public void gaugeDeadLettered(long deadLetteredCount) {
        deadLetteredGauge.set(Math.max(0, deadLetteredCount));
    }

    private void counter(String name, String... tags) {
        if (meterRegistry == null) {
            return;
        }
        Counter.Builder builder = Counter.builder(PREFIX + "." + name).description("Module business event metric");
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], safe(tags[i + 1]));
        }
        builder.register(meterRegistry).increment();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
