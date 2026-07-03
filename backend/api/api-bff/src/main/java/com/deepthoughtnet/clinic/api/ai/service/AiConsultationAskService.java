package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationAskRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiConsultationAskService {
    private final AiDoctorCopilotService copilotService;

    public AiConsultationAskService(AiDoctorCopilotService copilotService) {
        this.copilotService = copilotService;
    }

    public AiDraftResponse ask(AiConsultationAskRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("prompt", request.prompt());
        input.put("patientAgeGender", request.patientAgeGender());
        input.put("vitals", request.vitals());
        input.put("allergies", request.allergies());
        input.put("chronicConditions", request.chronicConditions());
        input.put("currentPrescriptionDraft", request.currentPrescriptionDraft());
        input.put("labOrdersSummary", request.labOrdersSummary());
        input.put("chiefComplaints", request.chiefComplaints());
        input.put("symptoms", request.symptoms());
        input.put("clinicalNotes", request.clinicalNotes());
        input.put("diagnosis", request.diagnosis());
        input.put("advice", request.advice());

        return copilotService.draft(
                AiTaskType.GENERIC_COPILOT,
                "generic.copilot.v1",
                "consultation.ask",
                input,
                List.of()
        );
    }
}
