package com.deepthoughtnet.clinic.api.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClinicalContextResponse(
        UUID tenantId,
        UUID patientId,
        UUID consultationId,
        PatientSnapshot patientSummary,
        List<VisitSummary> previousVisits,
        MedicationSummary medicationHistory,
        DiagnosisSummary diagnosisHistory,
        IntakeSummary intakeSummary,
        LabIntelligence labIntelligence,
        DocumentIntelligence documentIntelligence,
        TimelineSummary timelineSummary,
        String aiSummary,
        String aiPromptContext,
        String clinicalContextJson,
        OffsetDateTime generatedAt
) {
    public record PatientSnapshot(
            String patientName,
            Integer ageYears,
            String gender,
            String chronicConditions,
            String allergies,
            List<String> currentMedications,
            String lastConsultationDate
    ) {}

    public record VisitSummary(
            UUID consultationId,
            String consultationDate,
            String diagnosis,
            String treatmentSummary,
            String advice
    ) {}

    public record MedicationSummary(
            List<String> activeMedicines,
            List<String> discontinuedMedicines,
            List<String> recentAntibiotics,
            List<String> duplicateMedicines,
            List<String> alerts
    ) {}

    public record DiagnosisSummary(
            String lastVisitDiagnosis,
            List<String> previousDiagnoses
    ) {}

    public record IntakeSummary(
            boolean complete,
            String chiefComplaint,
            VitalsSnapshot latestVitals,
            String vitalsTrendSummary,
            List<String> abnormalVitalsAlerts,
            String uploadedDocumentSummary,
            String notes,
            String recordedByName,
            String recordedAt
    ) {}

    public record VitalsSnapshot(
            Double heightCm,
            Double weightKg,
            Double bmi,
            String bmiCategory,
            Integer bloodPressureSystolic,
            Integer bloodPressureDiastolic,
            Integer pulseRate,
            Double temperature,
            String temperatureUnit,
            Integer spo2,
            Integer respiratoryRate,
            Double randomBloodSugar,
            Integer painScore
    ) {}

    public record LabIntelligence(
            String latestLabReport,
            List<String> abnormalValues,
            List<String> previousTrends,
            List<String> pendingInvestigations,
            String lastHbA1c,
            String lastCbc,
            String lastCreatinine
    ) {}

    public record DocumentIntelligence(
            List<String> recentReports,
            List<String> radiology,
            List<String> referrals,
            List<String> dischargeSummaries
    ) {}

    public record TimelineSummary(
            List<TimelineEvent> events,
            String recentImportantEvents
    ) {}

    public record TimelineEvent(
            String occurredOn,
            String title,
            String detail,
            String type
    ) {}
}
