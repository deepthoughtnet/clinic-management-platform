package com.deepthoughtnet.clinic.platform.modulith.events.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ModuleBusinessEventDispatcher {
    private final ModuleBusinessEventProcessingService processingService;

    public ModuleBusinessEventDispatcher(ModuleBusinessEventProcessingService processingService) {
        this.processingService = processingService;
    }

    @Scheduled(fixedDelayString = "${jeevanam.platform.events.dispatch-fixed-delay:PT10S}")
    public void dispatch() {
        processingService.dispatchRunnable(Integer.MAX_VALUE);
    }
}
