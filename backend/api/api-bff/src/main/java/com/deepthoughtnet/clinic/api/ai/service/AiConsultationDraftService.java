package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationNotesRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDiagnosisSuggestionRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiPrescriptionTemplateRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiConsultationDraftService {
    private final AiDoctorCopilotService copilotService;

    public AiConsultationDraftService(AiDoctorCopilotService copilotService) {
        this.copilotService = copilotService;
    }

    public AiDraftResponse structureNotes(AiConsultationNotesRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("doctorNotes", request.doctorNotes());
        input.put("symptoms", request.symptoms());
        input.put("vitals", request.vitals());
        input.put("observations", request.observations());

        return copilotService.draft(
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "clinic.consultation.structure-notes.v1",
                "consultation_structure_notes",
                input,
                java.util.List.of()
        );
    }

    public AiDraftResponse suggestDiagnosis(AiDiagnosisSuggestionRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("symptoms", request.symptoms());
        input.put("findings", request.findings());
        input.put("doctorNotes", request.doctorNotes());
        input.put("knownConditions", request.knownConditions());
        input.put("allergies", request.allergies());

        return copilotService.draft(
                AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT,
                "clinic.consultation.suggest-diagnosis.v1",
                "consultation_suggest_diagnosis",
                input,
                java.util.List.of()
        );
    }

    public AiDraftResponse suggestPrescriptionTemplate(AiPrescriptionTemplateRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("diagnosis", request.diagnosis());
        input.put("symptoms", request.symptoms());
        input.put("allergies", request.allergies());
        input.put("currentMedications", request.currentMedications());
        input.put("doctorNotes", request.doctorNotes());

        return copilotService.draft(
                AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION,
                "clinic.prescription.suggest-template.v1",
                "prescription_template_suggestion",
                input,
                java.util.List.of()
        );
    }
}
