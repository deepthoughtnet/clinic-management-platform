package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiCanonicalEntities(
        String doctor,
        String clinic,
        String speciality,
        String service,
        String location,
        String date,
        String timeWindow,
        String exactTime,
        String slotSelection,
        String dateIssue
) {
    public PatientPortalCareAiCanonicalEntities(
            String doctor, String clinic, String speciality, String service, String location,
            String date, String timeWindow, String exactTime, String slotSelection
    ) {
        this(doctor, clinic, speciality, service, location, date, timeWindow, exactTime, slotSelection, null);
    }
    public boolean isEmpty() {
        return doctor == null && clinic == null && speciality == null && service == null
                && location == null && date == null && timeWindow == null
                && exactTime == null && slotSelection == null;
    }
}
