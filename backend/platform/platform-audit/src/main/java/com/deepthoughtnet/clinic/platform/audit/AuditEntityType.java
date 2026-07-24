package com.deepthoughtnet.clinic.platform.audit;

public final class AuditEntityType {
    public static final String CLINIC = "CLINIC";
    public static final String CLINIC_EXTRACTION_JOB = "CLINIC_EXTRACTION_JOB";
    public static final String DOCUMENT = "DOCUMENT";
    public static final String DOCTOR_PROFILE = "DOCTOR_PROFILE";
    public static final String GENERATED_CLINIC = "GENERATED_CLINIC";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String CLINIC_GENERATION_ITEM = "CLINIC_GENERATION_ITEM";
    public static final String RECONCILIATION_BATCH = "RECONCILIATION_BATCH";
    public static final String RECONCILIATION_MATCH = "RECONCILIATION_MATCH";
    public static final String RECONCILIATION_ALLOCATION = "RECONCILIATION_ALLOCATION";
    public static final String RECONCILIATION_EXCEPTION = "RECONCILIATION_EXCEPTION";
    public static final String DECISION_POLICY = "DECISION_POLICY";
    public static final String DECISION_RULE = "DECISION_RULE";
    public static final String DECISION_EXECUTION = "DECISION_EXECUTION";
    public static final String AGENT_INTAKE_SOURCE = "AGENT_INTAKE_SOURCE";
    public static final String AGENT_INTAKE_RUN = "AGENT_INTAKE_RUN";
    public static final String AGENT_INTAKE_ITEM = "AGENT_INTAKE_ITEM";
    public static final String PATIENT = "PATIENT";
    public static final String COMMERCIAL_CAPABILITY = "COMMERCIAL_CAPABILITY";
    public static final String COMMERCIAL_MODULE = "COMMERCIAL_MODULE";
    public static final String COMMERCIAL_FEATURE = "COMMERCIAL_FEATURE";
    public static final String COMMERCIAL_LIMIT_DEFINITION = "COMMERCIAL_LIMIT_DEFINITION";
    public static final String COMMERCIAL_ADDON_OFFER = "COMMERCIAL_ADDON_OFFER";

    private AuditEntityType() {
    }
}
