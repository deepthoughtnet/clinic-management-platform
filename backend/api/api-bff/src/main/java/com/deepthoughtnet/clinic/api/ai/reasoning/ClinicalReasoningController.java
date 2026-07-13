package com.deepthoughtnet.clinic.api.ai.reasoning;

import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResponse;
import com.deepthoughtnet.clinic.api.ai.service.AiStatusService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultations/{consultationId}/clinical-reasoning")
public class ClinicalReasoningController {
    private static final Logger log = LoggerFactory.getLogger(ClinicalReasoningController.class);
    private static final String AI_COPILOT_RUN_ACCESS = """
            @permissionChecker.hasPermission('ai_copilot.clinic.run') or
            @permissionChecker.hasPermission('ai_copilot.run')
            """;

    private final ClinicalReasoningService clinicalReasoningService;
    private final AiStatusService aiStatusService;

    public ClinicalReasoningController(ClinicalReasoningService clinicalReasoningService,
                                       AiStatusService aiStatusService) {
        this.clinicalReasoningService = clinicalReasoningService;
        this.aiStatusService = aiStatusService;
    }

    @PostMapping("/generate")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public ClinicalReasoningResponse generate(@PathVariable UUID consultationId,
                                              @RequestParam(name = "debug", defaultValue = "false") boolean debug,
                                              @RequestBody(required = false) ClinicalReasoningRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        String correlationId = RequestContextHolder.require().correlationId();
        log.info("[AI-REASONING-TRACE] tenantId={} consultationId={} requestId={} correlationId={} stage=REQUEST_RECEIVED debug={}",
                tenantId, consultationId, correlationId, correlationId, debug);
        aiStatusService.requireProviderReady(tenantId);
        return clinicalReasoningService.generate(tenantId, consultationId, request, debug);
    }

    @GetMapping
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public ClinicalReasoningResponse get(@PathVariable UUID consultationId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return clinicalReasoningService.get(tenantId, consultationId);
    }
}
