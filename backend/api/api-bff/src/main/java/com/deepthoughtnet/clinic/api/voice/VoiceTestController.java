package com.deepthoughtnet.clinic.api.voice;

import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping("/test")
    @PreAuthorize("@permissionChecker.hasPermission('ai.voice.test')")
    public VoiceTestResponse test(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "context", required = false) String context,
            @RequestParam(value = "language", required = false) String language
    ) {
        return voiceOrchestratorService.processAudio(audio, context, language);
    }
}
