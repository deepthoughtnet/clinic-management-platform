package com.deepthoughtnet.clinic.api.platform.communicationtest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clinic.carepilot.voice.inbound")
public class VoiceInboundConcurrencyProperties {
    private int workerCoreSize = 4;
    private int workerMaxSize = 8;
    private int workerQueueCapacity = 16;
    private int timerThreads = 2;

    public int getWorkerCoreSize() { return workerCoreSize; }
    public void setWorkerCoreSize(int value) { workerCoreSize = value; }
    public int getWorkerMaxSize() { return workerMaxSize; }
    public void setWorkerMaxSize(int value) { workerMaxSize = value; }
    public int getWorkerQueueCapacity() { return workerQueueCapacity; }
    public void setWorkerQueueCapacity(int value) { workerQueueCapacity = value; }
    public int getTimerThreads() { return timerThreads; }
    public void setTimerThreads(int value) { timerThreads = value; }
}
