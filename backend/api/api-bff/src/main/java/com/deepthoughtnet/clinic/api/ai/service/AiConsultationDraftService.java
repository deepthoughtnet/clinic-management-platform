package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationNotesRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDiagnosisSuggestionRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiPrescriptionTemplateRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiConsultationDraftService {
    private static final Logger log = LoggerFactory.getLogger(AiConsultationDraftService.class);
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

        log.debug("AI_DOCTOR_COPILOT_REQUEST taskType={} correlationId={} consultationId={} symptomsChars={} findingsChars={} notesChars={}",
                AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT,
                RequestContextHolder.require().correlationId(),
                request.consultationId(),
                request.symptoms() == null ? 0 : request.symptoms().length(),
                request.findings() == null ? 0 : request.findings().length(),
                request.doctorNotes() == null ? 0 : request.doctorNotes().length());

        AiDraftResponse response = copilotService.draft(
                AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT,
                "clinic.consultation.suggest-diagnosis.v1",
                "consultation_suggest_diagnosis",
                input,
                java.util.List.of()
        );
        log.debug("AI_DOCTOR_COPILOT_RESPONSE taskType={} provider={} model={} rawTextLength={} parsedSuggestionsCount={} structuredKeys={}",
                AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT,
                response.provider(),
                response.model(),
                response.draft() == null ? 0 : response.draft().length(),
                suggestionCount(response),
                response.structuredData() == null ? "[]" : response.structuredData().keySet());
        return response;
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

        log.debug("AI_DOCTOR_COPILOT_REQUEST taskType={} correlationId={} consultationId={} diagnosisChars={} symptomsChars={} notesChars={}",
                AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION,
                RequestContextHolder.require().correlationId(),
                request.consultationId(),
                request.diagnosis() == null ? 0 : request.diagnosis().length(),
                request.symptoms() == null ? 0 : request.symptoms().length(),
                request.doctorNotes() == null ? 0 : request.doctorNotes().length());

        AiDraftResponse response = copilotService.draft(
                AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION,
                "clinic.prescription.suggest-template.v1",
                "prescription_template_suggestion",
                input,
                java.util.List.of()
        );
        log.debug("AI_DOCTOR_COPILOT_RESPONSE taskType={} provider={} model={} rawTextLength={} parsedSuggestionsCount={} structuredKeys={}",
                AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION,
                response.provider(),
                response.model(),
                response.draft() == null ? 0 : response.draft().length(),
                suggestionCount(response),
                response.structuredData() == null ? "[]" : response.structuredData().keySet());
        return response;
    }

    private int suggestionCount(AiDraftResponse response) {
        if (response == null || response.structuredData() == null) {
            return 0;
        }
        Object suggestions = response.structuredData().get("suggestions");
        return suggestions instanceof java.util.List<?> list ? list.size() : 0;
    }
}
