package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-portal/aiva-v2")
@PreAuthorize("@permissionChecker.hasRole('PATIENT')")
@ConditionalOnProperty(prefix = "aiva.v2", name = "enabled", havingValue = "true")
class AivaV2Controller {
    private final AivaV2ConversationService conversationService;

    AivaV2Controller(AivaV2ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/message")
    MessageResponse message(@RequestBody MessageRequest request) {
        return conversationService.message(request);
    }
}
