package com.deepthoughtnet.clinic.api.ai.reasoning;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResult;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ReasoningMetadata;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicalReasoningService {
    private static final Logger log = LoggerFactory.getLogger(ClinicalReasoningService.class);

    private final ConsultationRepository consultationRepository;
    private final ClinicalContextService clinicalContextService;
    private final ClinicalReasoningEngine clinicalReasoningEngine;

    public ClinicalReasoningService(ConsultationRepository consultationRepository,
                                    ClinicalContextService clinicalContextService,
                                    ClinicalReasoningEngine clinicalReasoningEngine) {
        this.consultationRepository = consultationRepository;
        this.clinicalContextService = clinicalContextService;
        this.clinicalReasoningEngine = clinicalReasoningEngine;
    }

    public ClinicalReasoningResponse generate(UUID tenantId, UUID consultationId, ClinicalReasoningRequest request, boolean debug) {
        ConsultationEntity consultation = consultationRepository.findByTenantIdAndId(tenantId, consultationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
        if (request != null && request.patientId() != null && !request.patientId().equals(consultation.getPatientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient mismatch for consultation");
        }
        ClinicalContextResponse clinicalContext = clinicalContextService.buildClinicalContext(tenantId, consultation.getPatientId(), consultationId);
        String correlationId = RequestContextHolder.require().correlationId();
        String requestId = correlationId;
        log.info("[AI-REASONING-TRACE] tenantId={} consultationId={} patientId={} requestId={} correlationId={} stage=CONTEXT_READY",
                tenantId, consultationId, consultation.getPatientId(), requestId, correlationId);
        ClinicalReasoningResult reasoningResult = clinicalReasoningEngine.generate(
                new ClinicalReasoningEngine.UUIDContext(tenantId, requestId, correlationId),
                consultation,
                request,
                clinicalContext);
        return buildResponse(consultation, clinicalContext, reasoningResult, debug);
    }

    public ClinicalReasoningResponse generate(UUID tenantId, UUID consultationId, ClinicalReasoningRequest request) {
        return generate(tenantId, consultationId, request, false);
    }

    private ClinicalReasoningResponse buildResponse(ConsultationEntity consultation,
                                                    ClinicalContextResponse clinicalContext,
                                                    ClinicalReasoningResult reasoningResult,
                                                    boolean debug) {
        String consultationVitals = buildConsultationVitals(consultation, clinicalContext);
        String vitalsSource = determineVitalsSource(consultation, clinicalContext);
        ClinicalReasoningResponse.ConsultationSummary consultationSummary = new ClinicalReasoningResponse.ConsultationSummary(
                consultation.getId(),
                consultation.getPatientId(),
                consultation.getStatus() == null ? null : consultation.getStatus().name(),
                consultation.getChiefComplaints(),
                consultation.getSymptoms(),
                consultation.getDiagnosis(),
                consultation.getAdvice(),
                consultation.getClinicalNotes(),
                consultationVitals,
                vitalsSource,
                vitalsSource == null ? null : ("INTAKE".equals(vitalsSource) ? "Latest completed intake" : "Doctor-entered consultation vitals")
        );
        ClinicalReasoningResponse.ClinicalContextSummary clinicalContextSummary = ClinicalReasoningResponse.ClinicalContextSummary.from(clinicalContext, consultationVitals, vitalsSource);
        ReasoningMetadata metadata = reasoningResult == null ? null : reasoningResult.metadata();
        Map<String, Object> debugPayload = null;
        if (debug) {
            debugPayload = new LinkedHashMap<>();
            debugPayload.put("clinicalContext", clinicalContext);
            debugPayload.put("clinicalContextJson", clinicalContext == null ? null : clinicalContext.clinicalContextJson());
            debugPayload.put("aiPromptContext", clinicalContext == null ? null : clinicalContext.aiPromptContext());
            debugPayload.put("aiSummary", clinicalContext == null ? null : clinicalContext.aiSummary());
        }
        return new ClinicalReasoningResponse(consultationSummary, clinicalContextSummary, reasoningResult, metadata, debugPayload);
    }

    private String determineVitalsSource(ConsultationEntity consultation, ClinicalContextResponse clinicalContext) {
        boolean consultationVitalsPresent = consultation != null
                && (consultation.getBloodPressureSystolic() != null
                || consultation.getBloodPressureDiastolic() != null
                || consultation.getPulseRate() != null
                || consultation.getTemperature() != null
                || consultation.getSpo2() != null
                || consultation.getRespiratoryRate() != null
                || consultation.getWeightKg() != null
                || consultation.getHeightCm() != null);
        if (consultationVitalsPresent) {
            return "CONSULTATION";
        }
        return clinicalContext != null && clinicalContext.intakeSummary() != null && clinicalContext.intakeSummary().latestVitals() != null ? "INTAKE" : null;
    }

    private String buildConsultationVitals(ConsultationEntity consultation, ClinicalContextResponse clinicalContext) {
        String consultationVitals = buildVitalsFromConsultation(consultation);
        if (consultationVitals != null) {
            return consultationVitals;
        }
        return buildVitalsFromIntake(clinicalContext);
    }

    private String buildVitalsFromConsultation(ConsultationEntity consultation) {
        if (consultation == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        appendVital(builder, consultation.getBloodPressureSystolic() == null || consultation.getBloodPressureDiastolic() == null ? null : "BP " + consultation.getBloodPressureSystolic() + "/" + consultation.getBloodPressureDiastolic());
        appendVital(builder, consultation.getPulseRate() == null ? null : "Pulse " + consultation.getPulseRate());
        appendVital(builder, consultation.getTemperature() == null ? null : "Temp " + consultation.getTemperature() + (consultation.getTemperatureUnit() == null ? "" : " " + consultation.getTemperatureUnit().name()));
        appendVital(builder, consultation.getSpo2() == null ? null : "SpO2 " + consultation.getSpo2());
        appendVital(builder, consultation.getRespiratoryRate() == null ? null : "RR " + consultation.getRespiratoryRate());
        appendVital(builder, consultation.getWeightKg() == null ? null : "Weight " + consultation.getWeightKg());
        appendVital(builder, consultation.getHeightCm() == null ? null : "Height " + consultation.getHeightCm());
        return builder.length() == 0 ? null : builder.toString();
    }

    private String buildVitalsFromIntake(ClinicalContextResponse clinicalContext) {
        if (clinicalContext == null || clinicalContext.intakeSummary() == null || clinicalContext.intakeSummary().latestVitals() == null) {
            return null;
        }
        ClinicalContextResponse.VitalsSnapshot vitals = clinicalContext.intakeSummary().latestVitals();
        StringBuilder builder = new StringBuilder();
        appendVital(builder, vitals.bloodPressureSystolic() == null || vitals.bloodPressureDiastolic() == null ? null : "BP " + vitals.bloodPressureSystolic() + "/" + vitals.bloodPressureDiastolic());
        appendVital(builder, vitals.pulseRate() == null ? null : "Pulse " + vitals.pulseRate());
        appendVital(builder, vitals.temperature() == null ? null : "Temp " + vitals.temperature() + (vitals.temperatureUnit() == null ? "" : " " + vitals.temperatureUnit()));
        appendVital(builder, vitals.spo2() == null ? null : "SpO2 " + vitals.spo2());
        appendVital(builder, vitals.respiratoryRate() == null ? null : "RR " + vitals.respiratoryRate());
        appendVital(builder, vitals.randomBloodSugar() == null ? null : "RBS " + vitals.randomBloodSugar());
        appendVital(builder, vitals.bmi() == null ? null : "BMI " + String.format(java.util.Locale.ROOT, "%.1f", vitals.bmi()));
        return builder.length() == 0 ? null : "INTAKE " + builder;
    }

    private void appendVital(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(value);
    }
}
