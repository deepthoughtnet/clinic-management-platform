package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationNotesRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDiagnosisSuggestionRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiPrescriptionTemplateRequest;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiConsultationDraftService {
    private static final Logger log = LoggerFactory.getLogger(AiConsultationDraftService.class);
    private static final String SOAP_TEMPLATE_KEY = "clinic.consultation.structure-notes.v1";
    private static final String SOAP_DRAFT_INVALID_MESSAGE = "Unable to generate a meaningful SOAP draft from the available consultation context. Add or verify clinical details and retry.";
    private final AiDoctorCopilotService copilotService;
    private final ClinicalContextService clinicalContextService;
    private boolean soapTraceEnabled;

    public AiConsultationDraftService(AiDoctorCopilotService copilotService, ClinicalContextService clinicalContextService) {
        this.copilotService = copilotService;
        this.clinicalContextService = clinicalContextService;
    }

    @Value("${JEEVANAM_AI_SOAP_TRACE_ENABLED:false}")
    void setSoapTraceEnabled(boolean soapTraceEnabled) {
        this.soapTraceEnabled = soapTraceEnabled;
    }

    public AiDraftResponse structureNotes(AiConsultationNotesRequest request) {
        String traceId = RequestContextHolder.require().correlationId();
        String tenantId = RequestContextHolder.requireTenantId().toString();
        String currentStage = "START";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("patientAgeGender", request.patientAgeGender());
        input.put("chiefComplaint", request.chiefComplaint());
        input.put("allergies", request.allergies());
        input.put("chronicConditions", request.chronicConditions());
        input.put("currentPrescriptionDraft", request.currentPrescriptionDraft());
        input.put("labOrdersSummary", request.labOrdersSummary());
        input.put("doctorNotes", request.doctorNotes());
        input.put("symptoms", request.symptoms());
        input.put("diagnosis", request.diagnosis());
        input.put("advice", request.advice());
        input.put("vitals", request.vitals());
        input.put("observations", request.observations());
        try {
            if (soapTraceEnabled) {
                log.info("SOAP-DRAFT-TRACE stage=START traceId={} tenantId={} consultationId={} patientId={} templateKey={} chiefComplaintPresent={} symptomsPresent={} doctorNotesPresent={} diagnosisPresent={} advicePresent={} vitalsPresent={} observationsPresent={} chiefComplaintChars={} symptomsChars={} doctorNotesChars={} diagnosisChars={} adviceChars={} vitalsChars={} observationsChars={}",
                        traceId,
                        tenantId,
                        request.consultationId(),
                        request.patientId(),
                        SOAP_TEMPLATE_KEY,
                        hasText(request.chiefComplaint()),
                        hasText(request.symptoms()),
                        hasText(request.doctorNotes()),
                        hasText(request.diagnosis()),
                        hasText(request.advice()),
                        hasText(request.vitals()),
                        hasText(request.observations()),
                        length(request.chiefComplaint()),
                        length(request.symptoms()),
                        length(request.doctorNotes()),
                        length(request.diagnosis()),
                        length(request.advice()),
                        length(request.vitals()),
                        length(request.observations()));
            }
            currentStage = "CONTEXT_ENRICHMENT";
            ClinicalContextResponse clinicalContext = clinicalContextService.buildClinicalContext(
                    RequestContextHolder.requireTenantId(),
                    request.patientId(),
                    request.consultationId()
            );
            clinicalContextService.enrichPromptInput(input, clinicalContext);
            String soapClinicalContext = buildSoapClinicalContext(clinicalContext);
            input.put("soapClinicalContext", soapClinicalContext);
            if (soapTraceEnabled) {
                log.info("SOAP-DRAFT-TRACE stage=CONTEXT_ENRICHED traceId={} tenantId={} consultationId={} patientId={} templateKey={} clinicalContextPresent={} clinicalContextChars={} clinicalContextSummaryChars={} clinicalContextJsonChars={} aiPromptContextChars={} soapClinicalContextChars={} latestVitalsPresent={} conditionCount={} labCount={} reportCount={} longitudinalFindingCount={} enrichedInputKeys={}",
                        traceId,
                        tenantId,
                        request.consultationId(),
                        request.patientId(),
                        SOAP_TEMPLATE_KEY,
                        clinicalContext != null,
                        length(clinicalContext == null ? null : clinicalContext.aiPromptContext()),
                        length(clinicalContext == null ? null : clinicalContext.aiSummary()),
                        length(clinicalContext == null ? null : clinicalContext.clinicalContextJson()),
                        length(clinicalContext == null ? null : clinicalContext.aiPromptContext()),
                        length(soapClinicalContext),
                        clinicalContext != null && clinicalContext.intakeSummary() != null && clinicalContext.intakeSummary().latestVitals() != null,
                        countConditions(clinicalContext),
                        countLabEntries(clinicalContext),
                        countReports(clinicalContext),
                        countLongitudinalFindings(clinicalContext),
                        input.keySet());
            }
            currentStage = "PROMPT_RENDERING";
            AiDraftResponse response = copilotService.draft(
                    AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                    SOAP_TEMPLATE_KEY,
                    "consultation_structure_notes",
                    input,
                    java.util.List.of()
            );
            currentStage = "VALIDATION";
            SoapValidationDecision validation = validateSoapDraft(traceId, tenantId, request.consultationId(), request.patientId(), response);
            currentStage = "SERVICE_RESPONSE";
            if (soapTraceEnabled) {
                log.info("SOAP-DRAFT-TRACE stage=SERVICE_RESPONSE traceId={} tenantId={} consultationId={} patientId={} templateKey={} status={} validationResult={} reasonCode={} usableSectionCount={} safeFailureReturned={} structuredDataPresent={} finalCompletionStatus={}",
                        traceId,
                        tenantId,
                        request.consultationId(),
                        request.patientId(),
                        SOAP_TEMPLATE_KEY,
                        validation.safeFailureReturned() ? "REJECTED" : "SUCCESS",
                        validation.validationResult(),
                        validation.reasonCode(),
                        validation.usableSectionCount(),
                        validation.safeFailureReturned(),
                        validation.structuredDataPresent(),
                        validation.response() == null ? null : validation.response().parseStatus());
            }
            return validation.response();
        } catch (RuntimeException ex) {
            if (soapTraceEnabled) {
                log.warn("SOAP-DRAFT-TRACE stage=ERROR traceId={} tenantId={} consultationId={} patientId={} templateKey={} currentStage={} exceptionClass={} message={}",
                        traceId,
                        tenantId,
                        request.consultationId(),
                        request.patientId(),
                        SOAP_TEMPLATE_KEY,
                        currentStage,
                        ex.getClass().getName(),
                        safeMessage(ex));
            }
            throw ex;
        }
    }

    public AiDraftResponse suggestDiagnosis(AiDiagnosisSuggestionRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("patientAgeGender", request.patientAgeGender());
        input.put("vitals", request.vitals());
        input.put("currentPrescriptionDraft", request.currentPrescriptionDraft());
        input.put("labOrdersSummary", request.labOrdersSummary());
        input.put("symptoms", request.symptoms());
        input.put("findings", request.findings());
        input.put("doctorNotes", request.doctorNotes());
        input.put("knownConditions", request.knownConditions());
        input.put("allergies", request.allergies());
        clinicalContextService.enrichPromptInput(input, clinicalContextService.buildClinicalContext(
                RequestContextHolder.requireTenantId(),
                request.patientId(),
                request.consultationId()
        ));

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
        input.put("patientAgeGender", request.patientAgeGender());
        input.put("vitals", request.vitals());
        input.put("currentPrescriptionDraft", request.currentPrescriptionDraft());
        input.put("labOrdersSummary", request.labOrdersSummary());
        input.put("diagnosis", request.diagnosis());
        input.put("symptoms", request.symptoms());
        input.put("allergies", request.allergies());
        input.put("currentMedications", request.currentMedications());
        input.put("doctorNotes", request.doctorNotes());
        clinicalContextService.enrichPromptInput(input, clinicalContextService.buildClinicalContext(
                RequestContextHolder.requireTenantId(),
                request.patientId(),
                request.consultationId()
        ));

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

    private SoapValidationDecision validateSoapDraft(String traceId,
                                                     String tenantId,
                                                     java.util.UUID consultationId,
                                                     java.util.UUID patientId,
                                                     AiDraftResponse response) {
        if (response == null) {
            if (soapTraceEnabled) {
                log.info("SOAP-DRAFT-TRACE stage=VALIDATION traceId={} tenantId={} consultationId={} patientId={} templateKey={} structuredDataPresent=false subjectiveUsable=false objectiveUsable=false assessmentUsable=false planUsable=false usableSectionCount=0 placeholderSectionCount=0 allSectionsBlank=true allSectionsPlaceholder=true validationResult=REJECTED exactReasonCode=NO_PROVIDER_RESPONSE",
                        traceId, tenantId, consultationId, patientId, SOAP_TEMPLATE_KEY);
            }
            return new SoapValidationDecision(invalidSoapResponse(null), "REJECTED", "NO_PROVIDER_RESPONSE", 0, true, false);
        }

        Map<String, Object> structuredData = response.structuredData();
        boolean structuredDataPresent = structuredData != null && !structuredData.isEmpty();
        SoapFieldState subjective = soapFieldState(response, "subjective");
        SoapFieldState objective = soapFieldState(response, "objective");
        SoapFieldState assessment = soapFieldState(response, "assessment");
        SoapFieldState plan = soapFieldState(response, "plan");
        int usableSectionCount = (subjective.usable() ? 1 : 0)
                + (objective.usable() ? 1 : 0)
                + (assessment.usable() ? 1 : 0)
                + (plan.usable() ? 1 : 0);
        int placeholderSectionCount = (subjective.placeholder() ? 1 : 0)
                + (objective.placeholder() ? 1 : 0)
                + (assessment.placeholder() ? 1 : 0)
                + (plan.placeholder() ? 1 : 0);
        boolean allSectionsBlank = subjective.blank() && objective.blank() && assessment.blank() && plan.blank();
        boolean allSectionsPlaceholder = subjective.placeholder() && objective.placeholder() && assessment.placeholder() && plan.placeholder();
        String reasonCode = "SOAP_VALID";
        String validationResult = "ACCEPTED";
        boolean safeFailureReturned = false;

        if (!structuredDataPresent) {
            reasonCode = response.parseStatus() != null && response.parseStatus().equalsIgnoreCase("TRUNCATED")
                    ? "SOAP_JSON_PARSE_FAILED"
                    : response.parseStatus() != null && response.parseStatus().equalsIgnoreCase("FAILED")
                    ? "SOAP_JSON_PARSE_FAILED"
                    : "SOAP_NO_MEANINGFUL_SECTIONS";
            validationResult = "REJECTED";
            safeFailureReturned = true;
        } else if (usableSectionCount == 0) {
            reasonCode = allSectionsPlaceholder
                    ? "SOAP_PLACEHOLDER_ONLY"
                    : allSectionsBlank
                    ? "SOAP_NO_MEANINGFUL_SECTIONS"
                    : response.structuredData().keySet().stream().noneMatch(this::isRecognizedSoapKey)
                    ? "SOAP_KEYS_NOT_RECOGNIZED"
                    : "SOAP_NO_MEANINGFUL_SECTIONS";
            validationResult = "REJECTED";
            safeFailureReturned = true;
        }

        if (structuredDataPresent && usableSectionCount == 0 && !reasonCode.equals("SOAP_NO_MEANINGFUL_SECTIONS") && !reasonCode.equals("SOAP_PLACEHOLDER_ONLY")) {
            reasonCode = response.structuredData().keySet().stream().noneMatch(this::isRecognizedSoapKey) ? "SOAP_KEYS_NOT_RECOGNIZED" : reasonCode;
        }
        if (response.normalizedFinishReason() != null && response.normalizedFinishReason().equalsIgnoreCase("TRUNCATED")) {
            reasonCode = "RESPONSE_TRUNCATED";
            validationResult = "REJECTED";
            safeFailureReturned = true;
        }
        if (soapTraceEnabled) {
            log.info("SOAP-DRAFT-TRACE stage=VALIDATION traceId={} tenantId={} consultationId={} patientId={} templateKey={} structuredDataPresent={} subjectiveUsable={} objectiveUsable={} assessmentUsable={} planUsable={} usableSectionCount={} placeholderSectionCount={} allSectionsBlank={} allSectionsPlaceholder={} validationResult={} exactReasonCode={}",
                    traceId,
                    tenantId,
                    consultationId,
                    patientId,
                    SOAP_TEMPLATE_KEY,
                    structuredDataPresent,
                    subjective.usable(),
                    objective.usable(),
                    assessment.usable(),
                    plan.usable(),
                    usableSectionCount,
                    placeholderSectionCount,
                    allSectionsBlank,
                    allSectionsPlaceholder,
                    validationResult,
                    reasonCode);
        }
        if (!"ACCEPTED".equals(validationResult)) {
            return new SoapValidationDecision(invalidSoapResponse(response), validationResult, reasonCode, usableSectionCount, true, structuredDataPresent);
        }
        return new SoapValidationDecision(response, validationResult, reasonCode, usableSectionCount, false, structuredDataPresent);
    }

    private AiDraftResponse invalidSoapResponse(AiDraftResponse response) {
        String provider = response == null ? null : response.provider();
        String model = response == null ? null : response.model();
        BigDecimal confidence = response == null ? null : response.confidence();
        String finishReason = response == null ? null : response.finishReason();
        String normalizedFinishReason = response == null ? null : response.normalizedFinishReason();
        Integer responseChars = response == null ? null : response.responseChars();
        String rawText = response == null ? SOAP_DRAFT_INVALID_MESSAGE : response.rawText();
        return new AiDraftResponse(
                true,
                false,
                SOAP_DRAFT_INVALID_MESSAGE,
                provider,
                model,
                SOAP_DRAFT_INVALID_MESSAGE,
                Map.of(),
                confidence,
                List.of(),
                List.of(SOAP_DRAFT_INVALID_MESSAGE),
                finishReason,
                normalizedFinishReason,
                responseChars,
                rawText,
                "FAILED"
        );
    }

    private String soapSectionValue(AiDraftResponse response, String key) {
        if (response == null || response.structuredData() == null) {
            return "";
        }
        Object value = response.structuredData().get(key);
        if (value == null) {
            value = response.structuredData().get(Character.toUpperCase(key.charAt(0)) + key.substring(1));
        }
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        String normalized = text.toLowerCase();
        if (text.isBlank() || normalized.equals("-") || normalized.equals("--") || normalized.equals("n/a") || normalized.equals("na") || normalized.equals("not available") || normalized.equals("not documented")) {
            return "";
        }
        return text;
    }

    private String soapRawSectionValue(AiDraftResponse response, String key) {
        if (response == null || response.structuredData() == null) {
            return null;
        }
        Object value = response.structuredData().get(key);
        if (value == null) {
            value = response.structuredData().get(Character.toUpperCase(key.charAt(0)) + key.substring(1));
        }
        return value == null ? null : String.valueOf(value);
    }

    private boolean isSoapPlaceholderValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isBlank()
                || normalized.equals("-")
                || normalized.equals("--")
                || normalized.equals("n/a")
                || normalized.equals("na")
                || normalized.equals("not available")
                || normalized.equals("not documented");
    }

    private boolean isOnlyWhitespace(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotDocumented(String value) {
        return value != null && value.trim().equalsIgnoreCase("not documented");
    }

    private String soapRequestSummary(AiConsultationNotesRequest request) {
        return "chiefComplaintChars=" + length(request.chiefComplaint())
                + " symptomsChars=" + length(request.symptoms())
                + " diagnosisChars=" + length(request.diagnosis())
                + " adviceChars=" + length(request.advice())
                + " vitalsChars=" + length(request.vitals())
                + " observationsChars=" + length(request.observations());
    }

    private String summarizeValue(String value) {
        return value == null || value.isBlank() ? null : trim(value, 120);
    }

    private Integer length(String value) {
        return value == null ? 0 : value.length();
    }

    private String trim(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private String summarizeVitals(String vitals) {
        return vitals == null || vitals.isBlank() ? null : trim(vitals, 240);
    }

    private String summarizeVitalsSnapshot(ClinicalContextResponse.VitalsSnapshot vitals) {
        if (vitals == null) {
            return null;
        }
        List<String> parts = new java.util.ArrayList<>();
        if (vitals.bloodPressureSystolic() != null || vitals.bloodPressureDiastolic() != null) {
            parts.add("BP " + (vitals.bloodPressureSystolic() == null ? "" : vitals.bloodPressureSystolic())
                    + "/"
                    + (vitals.bloodPressureDiastolic() == null ? "" : vitals.bloodPressureDiastolic()));
        }
        if (vitals.pulseRate() != null) {
            parts.add("Pulse " + vitals.pulseRate());
        }
        if (vitals.respiratoryRate() != null) {
            parts.add("Resp " + vitals.respiratoryRate());
        }
        if (vitals.spo2() != null) {
            parts.add("SpO2 " + vitals.spo2() + "%");
        }
        if (vitals.temperature() != null) {
            parts.add("Temp " + trim(String.valueOf(vitals.temperature()), 16)
                    + (vitals.temperatureUnit() == null || vitals.temperatureUnit().isBlank() ? "" : " " + vitals.temperatureUnit()));
        }
        if (vitals.bmi() != null) {
            parts.add("BMI " + trim(String.valueOf(vitals.bmi()), 16));
        }
        if (vitals.randomBloodSugar() != null) {
            parts.add("RBS " + trim(String.valueOf(vitals.randomBloodSugar()), 16));
        }
        if (vitals.painScore() != null) {
            parts.add("Pain " + vitals.painScore() + "/10");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String summarizeList(String value) {
        return value == null || value.isBlank() ? null : trim(value, 240);
    }

    private int countLongitudinalConcepts(ClinicalContextResponse context) {
        if (context == null || context.longitudinalMemory() == null) {
            return 0;
        }
        return (context.longitudinalMemory().knownConditions() == null ? 0 : context.longitudinalMemory().knownConditions().size())
                + (context.longitudinalMemory().longTermMedications() == null ? 0 : context.longitudinalMemory().longTermMedications().size())
                + (context.longitudinalMemory().latestLipidSummary() == null ? 0 : context.longitudinalMemory().latestLipidSummary().size())
                + (context.longitudinalMemory().riskFlags() == null ? 0 : context.longitudinalMemory().riskFlags().size())
                + (context.longitudinalMemory().history() == null ? 0 : context.longitudinalMemory().history().size());
    }

    private int countConditions(ClinicalContextResponse context) {
        if (context == null || context.longitudinalMemory() == null || context.longitudinalMemory().knownConditions() == null) {
            return 0;
        }
        return context.longitudinalMemory().knownConditions().size();
    }

    private int countLabEntries(ClinicalContextResponse context) {
        if (context == null || context.labIntelligence() == null) {
            return 0;
        }
        int count = 0;
        if (context.labIntelligence().latestLabReport() != null && !context.labIntelligence().latestLabReport().isBlank()) count++;
        if (context.labIntelligence().lastHbA1c() != null && !context.labIntelligence().lastHbA1c().isBlank()) count++;
        if (context.labIntelligence().lastCbc() != null && !context.labIntelligence().lastCbc().isBlank()) count++;
        if (context.labIntelligence().lastCreatinine() != null && !context.labIntelligence().lastCreatinine().isBlank()) count++;
        if (context.labIntelligence().latestBloodSugar() != null && !context.labIntelligence().latestBloodSugar().isBlank()) count++;
        if (context.labIntelligence().latestLipidSummary() != null && !context.labIntelligence().latestLipidSummary().isBlank()) count++;
        if (context.labIntelligence().latestBloodPressure() != null && !context.labIntelligence().latestBloodPressure().isBlank()) count++;
        if (context.labIntelligence().latestBmi() != null && !context.labIntelligence().latestBmi().isBlank()) count++;
        return count;
    }

    private int countReports(ClinicalContextResponse context) {
        if (context == null || context.documentIntelligence() == null || context.documentIntelligence().recentReports() == null) {
            return 0;
        }
        return context.documentIntelligence().recentReports().size();
    }

    private int countLongitudinalFindings(ClinicalContextResponse context) {
        if (context == null || context.longitudinalClinicalContext() == null || context.longitudinalClinicalContext().importantHistoricalFindings() == null) {
            return 0;
        }
        return context.longitudinalClinicalContext().importantHistoricalFindings().size();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable == null ? null : throwable.getClass().getSimpleName();
        }
        String message = throwable.getMessage().replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() <= 220 ? message : message.substring(0, 220);
    }

    private List<String> segments(String... values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (hasText(value)) {
                parts.add(value.trim());
            }
        }
        return parts;
    }

    private String joinSegments(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> filtered = values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .toList();
        return filtered.isEmpty() ? null : String.join(" | ", filtered);
    }

    private String joinCompact(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> filtered = values.stream()
                .filter(this::hasText)
                .map(this::compactText)
                .filter(this::hasText)
                .limit(Math.max(1, limit))
                .toList();
        return filtered.isEmpty() ? null : String.join(" • ", filtered);
    }

    private String compactText(String value) {
        return value == null ? null : trim(value, 180);
    }

    private String prefixedLine(String prefix, String value) {
        if (!hasText(value)) {
            return null;
        }
        return prefix + value;
    }

    private String buildSoapClinicalContext(ClinicalContextResponse clinicalContext) {
        if (clinicalContext == null) {
            return null;
        }
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        ClinicalContextResponse.PatientSnapshot patientSummary = clinicalContext.patientSummary();
        ClinicalContextResponse.IntakeSummary intakeSummary = clinicalContext.intakeSummary();

        addSoapLine(lines, "Patient profile: " + joinSegments(segments(
                patientSummary == null ? null : patientSummary.ageYears() == null ? null : patientSummary.ageYears() + "y",
                patientSummary == null ? null : patientSummary.gender(),
                patientSummary == null ? null : summarizeValue(patientSummary.chronicConditions())
        )));
        addSoapLine(lines, prefixedLine("Allergies: ", summarizeValue(patientSummary == null ? null : patientSummary.allergies())));
        addSoapLine(lines, prefixedLine("Current medications: ", joinCompact(patientSummary == null || patientSummary.currentMedications() == null ? List.of() : patientSummary.currentMedications(), 5)));
        addSoapLine(lines, prefixedLine("Current visit: ", joinSegments(segments(
                intakeSummary == null ? null : summarizeValue(intakeSummary.chiefComplaint()),
                intakeSummary == null ? null : summarizeVitalsSnapshot(intakeSummary.latestVitals()),
                intakeSummary == null ? null : summarizeValue(intakeSummary.notes())
        ))));
        addSoapLine(lines, prefixedLine("Latest labs: ", joinSegments(segments(
                clinicalContext.labIntelligence() == null ? null : summarizeValue(clinicalContext.labIntelligence().latestLabReport()),
                clinicalContext.labIntelligence() == null ? null : summarizeValue(clinicalContext.labIntelligence().lastHbA1c()),
                clinicalContext.labIntelligence() == null ? null : summarizeValue(clinicalContext.labIntelligence().latestBloodSugar()),
                clinicalContext.labIntelligence() == null ? null : summarizeValue(clinicalContext.labIntelligence().latestLipidSummary()),
                clinicalContext.labIntelligence() == null ? null : summarizeValue(clinicalContext.labIntelligence().latestBloodPressure()),
                clinicalContext.labIntelligence() == null ? null : summarizeValue(clinicalContext.labIntelligence().latestBmi())
        ))));
        addSoapLine(lines, prefixedLine("Previous visit diagnoses: ", joinCompact(deduplicateValues(
                clinicalContext.previousVisits() == null
                        ? List.of()
                        : clinicalContext.previousVisits().stream()
                        .map(ClinicalContextResponse.VisitSummary::diagnosis)
                        .filter(this::hasText)
                        .map(this::compactText)
                        .toList()
        ), 4)));
        addSoapLine(lines, prefixedLine("Important historical findings: ", joinCompact(deduplicateValues(
                clinicalContext.longitudinalClinicalContext() == null || clinicalContext.longitudinalClinicalContext().importantHistoricalFindings() == null
                        ? List.of()
                        : clinicalContext.longitudinalClinicalContext().importantHistoricalFindings().stream()
                        .map(finding -> joinSegments(segments(
                                finding == null ? null : finding.title(),
                                finding == null ? null : finding.summary(),
                                finding == null ? null : finding.clinicalRelevance()
                        )))
                        .filter(this::hasText)
                        .map(this::compactText)
                        .toList()
        ), 4)));
        addSoapLine(lines, prefixedLine("Medication alerts: ", joinCompact(deduplicateValues(
                clinicalContext.medicationHistory() == null || clinicalContext.medicationHistory().alerts() == null
                        ? List.of()
                        : clinicalContext.medicationHistory().alerts().stream()
                        .filter(this::hasText)
                        .map(this::compactText)
                        .toList()
        ), 4)));
        return String.join("\n", lines);
    }

    private void addSoapLine(LinkedHashSet<String> lines, String line) {
        if (line == null) {
            return;
        }
        String normalized = line.trim();
        if (normalized.isBlank()) {
            return;
        }
        lines.add(normalized.endsWith(":") ? normalized.substring(0, normalized.length() - 1) : normalized);
    }

    private List<String> deduplicateValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            if (!hasText(value)) {
                continue;
            }
            String normalized = compactText(value);
            if (hasText(normalized)) {
                deduped.add(normalized);
            }
        }
        return new ArrayList<>(deduped);
    }

    private boolean isRecognizedSoapKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase();
        return normalized.equals("subjective") || normalized.equals("objective") || normalized.equals("assessment") || normalized.equals("plan");
    }

    private SoapFieldState soapFieldState(AiDraftResponse response, String key) {
        String raw = soapRawSectionValue(response, key);
        String normalized = soapSectionValue(response, key);
        boolean blank = isOnlyWhitespace(raw);
        boolean placeholder = isSoapPlaceholderValue(raw);
        return new SoapFieldState(normalized, !normalized.isBlank(), normalized.length(), placeholder, blank);
    }

    private record SoapValidationDecision(AiDraftResponse response,
                                          String validationResult,
                                          String reasonCode,
                                          int usableSectionCount,
                                          boolean safeFailureReturned,
                                          boolean structuredDataPresent) {}

    private record SoapFieldState(String value,
                                  boolean usable,
                                  int chars,
                                  boolean placeholder,
                                  boolean blank) {}
}
