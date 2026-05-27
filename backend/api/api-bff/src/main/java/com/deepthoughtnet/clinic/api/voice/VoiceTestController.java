package com.deepthoughtnet.clinic.api.voice;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice")
public class VoiceTestController {
    private final VoiceOrchestratorService voiceOrchestratorService;

    public VoiceTestController(VoiceOrchestratorService voiceOrchestratorService) {
        this.voiceOrchestratorService = voiceOrchestratorService;
    }

    @GetMapping("/status")
    @PreAuthorize("@permissionChecker.hasPermission('ai.voice.test')")
    public VoiceStatusResponse status(@RequestParam(value = "warmup", defaultValue = "false") boolean warmup) {
        return voiceOrchestratorService.status(warmup);
    }

    @GetMapping("/live-status")
    @PreAuthorize("@permissionChecker.hasPermission('ai.voice.test')")
    public VoiceLiveStatusResponse liveStatus() {
        return voiceOrchestratorService.liveStatus();
    }

    @PostMapping("/test")
    @PreAuthorize("@permissionChecker.hasPermission('ai.voice.test')")
    public VoiceTestResponse test(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "context", required = false) String context,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "workflowMode", required = false) String workflowMode
    ) {
        return voiceOrchestratorService.processAudio(audio, context, language, workflowMode);
    }

    @PostMapping("/debug/stt")
    @PreAuthorize("@permissionChecker.hasPermission('ai.voice.test')")
    public VoiceSttDebugResponse debugStt(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "language", required = false) String language
    ) {
        return voiceOrchestratorService.debugStt(audio, language);
    }
}
