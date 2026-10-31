package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class VoiceInboundExecutorConfigurationTest {
    @Test
    void workerPoolIsBoundedNamedAndRejectsWithoutRunningCallerTask() throws Exception {
        VoiceInboundConcurrencyProperties properties = new VoiceInboundConcurrencyProperties();
        properties.setWorkerCoreSize(1);
        properties.setWorkerMaxSize(1);
        properties.setWorkerQueueCapacity(1);
        ThreadPoolExecutor executor = new VoiceInboundExecutorConfiguration().dotVoiceInboundWorker(properties);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch queuedTaskRan = new CountDownLatch(1);
        CountDownLatch rejectedTaskRan = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("dotvoice-inbound-worker-1");
                firstStarted.countDown();
                try { assertThat(releaseFirst.await(2, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            });
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            executor.execute(queuedTaskRan::countDown);

            assertThatThrownBy(() -> executor.execute(rejectedTaskRan::countDown))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(rejectedTaskRan.getCount()).isEqualTo(1);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(1);
            assertThat(executor.getQueue().size()).isEqualTo(1);
        } finally {
            releaseFirst.countDown();
            assertThat(queuedTaskRan.await(2, TimeUnit.SECONDS)).isTrue();
            executor.shutdownNow();
        }
    }
}
