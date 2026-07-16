package com.deepthoughtnet.clinic.api.consultation.service;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class ConsultationSoapContextHasher {
    private final ObjectMapper objectMapper;

    ConsultationSoapContextHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    String sourceHash(ConsultationRecord consultation, ClinicalContextResponse context) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("consultation", consultationMap(consultation));
            input.put("clinicalContextSummary", context == null ? null : context.aiSummary());
            input.put("clinicalContextJson", context == null ? null : context.clinicalContextJson());
            input.put("aiPromptContext", context == null ? null : context.aiPromptContext());
            input.put("patientSummary", patientSummaryMap(context));
            input.put("diagnosisHistory", diagnosisHistoryMap(context));
            input.put("intakeSummary", intakeSummaryMap(context));
            input.put("labIntelligence", labIntelligenceMap(context));
            input.put("documentIntelligence", documentIntelligenceMap(context));
            input.put("timelineSummary", timelineSummaryMap(context));
            input.put("longitudinalMemory", longitudinalMemoryMap(context));
            input.put("longitudinalClinicalContext", longitudinalClinicalContextMap(context));
            return sha256(objectMapper.writeValueAsString(normalizeValue(input)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to hash SOAP source context", ex);
        }
    }

    private Object normalizeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof UUID || value instanceof Enum<?> || value instanceof java.time.temporal.TemporalAccessor || value instanceof java.util.Date) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> normalized.put(String.valueOf(key), normalizeValue(entryValue)));
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new java.util.ArrayList<>();
            for (Object item : iterable) {
                normalized.add(normalizeValue(item));
            }
            return normalized;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> normalized = new java.util.ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                normalized.add(normalizeValue(java.lang.reflect.Array.get(value, i)));
            }
            return normalized;
        }
        return value.toString();
    }

    private Map<String, Object> consultationMap(ConsultationRecord consultation) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (consultation == null) {
            return map;
        }
        map.put("consultationId", consultation.id());
        map.put("patientId", consultation.patientId());
        map.put("chiefComplaints", consultation.chiefComplaints());
        map.put("symptoms", consultation.symptoms());
        map.put("diagnosis", consultation.diagnosis());
        map.put("clinicalNotes", consultation.clinicalNotes());
        map.put("advice", consultation.advice());
        map.put("followUpDate", consultation.followUpDate());
        map.put("bloodPressureSystolic", consultation.bloodPressureSystolic());
        map.put("bloodPressureDiastolic", consultation.bloodPressureDiastolic());
        map.put("pulseRate", consultation.pulseRate());
        map.put("temperature", consultation.temperature());
        map.put("temperatureUnit", consultation.temperatureUnit());
        map.put("weightKg", consultation.weightKg());
        map.put("heightCm", consultation.heightCm());
        map.put("spo2", consultation.spo2());
        map.put("respiratoryRate", consultation.respiratoryRate());
        return map;
    }

    private Map<String, Object> patientSummaryMap(ClinicalContextResponse context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (context == null || context.patientSummary() == null) {
            return map;
        }
        map.put("ageYears", context.patientSummary().ageYears());
        map.put("gender", context.patientSummary().gender());
        map.put("chronicConditions", context.patientSummary().chronicConditions());
        map.put("allergies", context.patientSummary().allergies());
        map.put("currentMedications", context.patientSummary().currentMedications());
        map.put("lastConsultationDate", context.patientSummary().lastConsultationDate());
        return map;
    }

    private Map<String, Object> diagnosisHistoryMap(ClinicalContextResponse context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (context == null || context.diagnosisHistory() == null) {
            return map;
        }
        map.put("lastVisitDiagnosis", context.diagnosisHistory().lastVisitDiagnosis());
        map.put("previousDiagnoses", context.diagnosisHistory().previousDiagnoses());
        return map;
    }

    private Map<String, Object> intakeSummaryMap(ClinicalContextResponse context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (context == null || context.intakeSummary() == null) {
            return map;
        }
        map.put("chiefComplaint", context.intakeSummary().chiefComplaint());
        map.put("vitalsTrendSummary", context.intakeSummary().vitalsTrendSummary());
        map.put("abnormalVitalsAlerts", context.intakeSummary().abnormalVitalsAlerts());
        map.put("notes", context.intakeSummary().notes());
        map.put("recordedByName", context.intakeSummary().recordedByName());
        map.put("recordedAt", context.intakeSummary().recordedAt());
        map.put("latestVitals", vitalsMap(context.intakeSummary().latestVitals()));
        return map;
    }

    private Map<String, Object> vitalsMap(ClinicalContextResponse.VitalsSnapshot vitals) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (vitals == null) {
            return map;
        }
        map.put("heightCm", vitals.heightCm());
        map.put("weightKg", vitals.weightKg());
        map.put("bmi", vitals.bmi());
        map.put("bmiCategory", vitals.bmiCategory());
        map.put("bloodPressureSystolic", vitals.bloodPressureSystolic());
        map.put("bloodPressureDiastolic", vitals.bloodPressureDiastolic());
        map.put("pulseRate", vitals.pulseRate());
        map.put("temperature", vitals.temperature());
        map.put("temperatureUnit", vitals.temperatureUnit());
        map.put("spo2", vitals.spo2());
        map.put("respiratoryRate", vitals.respiratoryRate());
        map.put("randomBloodSugar", vitals.randomBloodSugar());
        map.put("painScore", vitals.painScore());
        return map;
    }

    private Map<String, Object> labIntelligenceMap(ClinicalContextResponse context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (context == null || context.labIntelligence() == null) {
            return map;
        }
        map.put("latestLabReport", context.labIntelligence().latestLabReport());
        map.put("abnormalValues", context.labIntelligence().abnormalValues());
        map.put("previousTrends", context.labIntelligence().previousTrends());
        map.put("pendingInvestigations", context.labIntelligence().pendingInvestigations());
        map.put("lastHbA1c", context.labIntelligence().lastHbA1c());
        map.put("lastCbc", context.labIntelligence().lastCbc());
        map.put("lastCreatinine", context.labIntelligence().lastCreatinine());
        map.put("latestBloodSugar", context.labIntelligence().latestBloodSugar());
        map.put("latestLipidSummary", context.labIntelligence().latestLipidSummary());
        map.put("latestBloodPressure", context.labIntelligence().latestBloodPressure());
        map.put("latestBmi", context.labIntelligence().latestBmi());
        return map;
    }

    private Map<String, Object> documentIntelligenceMap(ClinicalContextResponse context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (context == null || context.documentIntelligence() == null) {
            return map;
        }
        map.put("recentReports", context.documentIntelligence().recentReports());
        map.put("radiology", context.documentIntelligence().radiology());
        map.put("referrals", context.documentIntelligence().referrals());
        map.put("dischargeSummaries", context.documentIntelligence().dischargeSummaries());
        return map;
    }

    private Map<String, Object> timelineSummaryMap(ClinicalContextResponse context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (context == null || context.timelineSummary() == null) {
            return map;
        }
        map.put("events", context.timelineSummary().events());
        map.put("recentImportantEvents", context.timelineSummary().recentImportantEvents());
        return map;
    }

    private Map<String, Object> longitudinalMemoryMap(ClinicalContextResponse context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (context == null || context.longitudinalMemory() == null) {
            return map;
        }
        map.put("knownConditions", context.longitudinalMemory().knownConditions());
        map.put("longTermMedications", context.longitudinalMemory().longTermMedications());
        map.put("latestHbA1c", context.longitudinalMemory().latestHbA1c());
        map.put("latestBloodSugar", context.longitudinalMemory().latestBloodSugar());
        map.put("latestLipidSummary", context.longitudinalMemory().latestLipidSummary());
        map.put("latestBloodPressure", context.longitudinalMemory().latestBloodPressure());
        map.put("latestBmi", context.longitudinalMemory().latestBmi());
        map.put("riskFlags", context.longitudinalMemory().riskFlags());
        map.put("history", context.longitudinalMemory().history());
        map.put("mostRecentLaboratorySummary", context.longitudinalMemory().mostRecentLaboratorySummary());
        return map;
    }

    private Map<String, Object> longitudinalClinicalContextMap(ClinicalContextResponse context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (context == null || context.longitudinalClinicalContext() == null) {
            return map;
        }
        map.put("labTrends", context.longitudinalClinicalContext().labTrends());
        map.put("imagingHistory", context.longitudinalClinicalContext().imagingHistory());
        map.put("renalContext", context.longitudinalClinicalContext().renalContext());
        map.put("importantHistoricalFindings", context.longitudinalClinicalContext().importantHistoricalFindings());
        map.put("dataQualityWarnings", context.longitudinalClinicalContext().dataQualityWarnings());
        return map;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
