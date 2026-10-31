package com.deepthoughtnet.clinic.api.platform.communicationtest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VoiceInboundExecutorConfiguration {
    @Bean(name = "dotVoiceInboundWorker", destroyMethod = "shutdown")
    public ThreadPoolExecutor dotVoiceInboundWorker(VoiceInboundConcurrencyProperties properties) {
        int core = Math.max(1, properties.getWorkerCoreSize());
        int max = Math.max(core, properties.getWorkerMaxSize());
        int capacity = Math.max(1, properties.getWorkerQueueCapacity());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(core, max, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), namedFactory("dotvoice-inbound-worker-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    @Bean(name = "dotVoiceInboundTimer", destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor dotVoiceInboundTimer(VoiceInboundConcurrencyProperties properties) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                Math.max(1, properties.getTimerThreads()), namedFactory("dotvoice-inbound-timer-"));
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    private ThreadFactory namedFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
