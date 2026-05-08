package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiClinicalSummaryRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiClinicalSummaryService {
    private final AiDoctorCopilotService copilotService;

    public AiClinicalSummaryService(AiDoctorCopilotService copilotService) {
        this.copilotService = copilotService;
    }

    public AiDraftResponse summarize(AiClinicalSummaryRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("patientId", request.patientId());
        input.put("patientName", request.patientName());
        input.put("historyText", request.historyText());
        input.put("chronicHistory", request.chronicHistory());
        input.put("recentConsultationSummary", request.recentConsultationSummary());
        input.put("recentConsultations", request.recentConsultations());
        input.put("currentMedications", request.currentMedications());
        input.put("allergies", request.allergies());

        return copilotService.draft(
                AiTaskType.CLINICAL_SUMMARY,
                "clinic.clinical.summary.v1",
                "clinical_summary",
                input,
                java.util.List.of()
        );
    }
}
