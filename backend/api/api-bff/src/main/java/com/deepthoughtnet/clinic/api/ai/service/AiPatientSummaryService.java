package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiPatientInstructionsRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiPatientSummaryRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiPatientSummaryService {
    private final AiDoctorCopilotService copilotService;

    public AiPatientSummaryService(AiDoctorCopilotService copilotService) {
        this.copilotService = copilotService;
    }

    public AiDraftResponse summarizePatient(AiPatientSummaryRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("patientId", request.patientId());
        input.put("patientName", request.patientName());
        input.put("historyText", request.historyText());
        input.put("activeConditions", request.activeConditions());
        input.put("currentMedications", request.currentMedications());
        input.put("allergies", request.allergies());
        input.put("recentVisits", request.recentVisits());

        return copilotService.draft(
                AiTaskType.PATIENT_HISTORY_SUMMARY,
                "clinic.patient.summary.v1",
                "patient_summary",
                input,
                java.util.List.of()
        );
    }

    public AiDraftResponse patientInstructions(AiPatientInstructionsRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("diagnosis", request.diagnosis());
        input.put("prescription", request.prescription());
        input.put("instructionsContext", request.instructionsContext());
        input.put("language", request.language());
        input.put("literacyLevel", request.literacyLevel());
        input.put("allergies", request.allergies());
        input.put("warnings", request.warnings());

        return copilotService.draft(
                AiTaskType.PATIENT_INSTRUCTIONS_DRAFT,
                "clinic.patient.instructions.v1",
                "patient_instructions",
                input,
                java.util.List.of()
        );
    }
}
