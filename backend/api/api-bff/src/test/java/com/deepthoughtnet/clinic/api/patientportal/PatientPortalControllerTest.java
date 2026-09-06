package com.deepthoughtnet.clinic.api.patientportal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiStateResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAuthorizedClinicResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalClinicResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorAvailabilityResponse;
import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalAccessLoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientPortalControllerTest {
    private PatientPortalService patientPortalService;
    private PatientPortalCareAiService patientPortalCareAiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        patientPortalService = mock(PatientPortalService.class);
        patientPortalCareAiService = mock(PatientPortalCareAiService.class);
        PatientPortalController controller = new PatientPortalController(patientPortalService, patientPortalCareAiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
    }

    @Test
    void careAiMessageDelegatesToSharedService() throws Exception {
        when(patientPortalCareAiService.message(new PatientPortalCareAiMessageRequest("hello", "en")))
                .thenReturn(new PatientPortalCareAiMessageResponse(
                        "Hello. I can help with appointments.",
                        new PatientPortalCareAiStateResponse(
                                "en",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                false,
                                false,
                                null,
                                null,
                                null,
                                null,
                                false,
                                null,
                                java.util.List.of(),
                                java.util.List.of(),
                                java.util.List.of()
                        )
                ));

        mockMvc.perform(post("/api/patient-portal/careai/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "hello",
                                  "language": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage").value("Hello. I can help with appointments."));

        verify(patientPortalCareAiService).message(new PatientPortalCareAiMessageRequest("hello", "en"));
    }

    @Test
    void clinicEndpointDelegatesToSharedService() throws Exception {
        when(patientPortalService.clinic()).thenReturn(new PatientPortalClinicResponse(
                "clinic-id",
                "tenant-id",
                "Sunrise Clinic",
                "Sunrise Clinic",
                "9999999999",
                "clinic@example.com",
                "Line 1",
                null,
                "Mumbai",
                "MH",
                "India",
                "400001",
                true,
                true,
                "sunrise-clinic"
        ));

        mockMvc.perform(get("/api/patient-portal/clinic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("Sunrise Clinic"))
                .andExpect(jsonPath("$.bookable").value(true));

        verify(patientPortalService).clinic();
    }

    @Test
    void clinicsEndpointDelegatesToSharedService() throws Exception {
        when(patientPortalService.clinics()).thenReturn(java.util.List.of(new PatientPortalAuthorizedClinicResponse(
                java.util.UUID.randomUUID(),
                "demo-clinic",
                "Demo Clinic",
                java.util.UUID.randomUUID(),
                "NewAuto Lab",
                true,
                true,
                "PATIENT_PORTAL_ACCESS_REQUEST"
        )));

        mockMvc.perform(get("/api/patient-portal/clinics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clinicName").value("Demo Clinic"))
                .andExpect(jsonPath("$[0].selected").value(true));

        verify(patientPortalService).clinics();
    }

    @Test
    void clinicSwitchEndpointDelegatesToSharedService() throws Exception {
        when(patientPortalService.switchClinic(java.util.UUID.fromString("720be1ee-f2ae-4dd9-b477-e05e3f97441a")))
                .thenReturn(new PatientPortalAccessLoginResponse(
                        true,
                        "Patient portal clinic context switched.",
                        "720be1ee-f2ae-4dd9-b477-e05e3f97441a",
                        "automation-lab",
                        "NewAuto Lab",
                        "session-token"
                ));

        mockMvc.perform(post("/api/patient-portal/clinics/720be1ee-f2ae-4dd9-b477-e05e3f97441a/switch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("720be1ee-f2ae-4dd9-b477-e05e3f97441a"))
                .andExpect(jsonPath("$.patientDisplayName").value("NewAuto Lab"));

        verify(patientPortalService).switchClinic(java.util.UUID.fromString("720be1ee-f2ae-4dd9-b477-e05e3f97441a"));
    }

    @Test
    void doctorAvailabilityEndpointDelegatesToSharedService() throws Exception {
        java.util.UUID doctorUserId = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(patientPortalService.doctorAvailability(null, doctorUserId.toString(), null, null, null, java.time.LocalDate.parse("2026-09-06")))
                .thenReturn(new PatientPortalDoctorAvailabilityResponse(
                        java.time.LocalDate.parse("2026-09-06"),
                        java.util.List.of(),
                        java.util.List.of()
                ));

        mockMvc.perform(get("/api/patient-portal/doctors/{publicDoctorId}/slots/next", doctorUserId)
                        .param("date", "2026-09-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedDate").value(contains(2026, 9, 6)))
                .andExpect(jsonPath("$.nextAvailable").isArray());

        verify(patientPortalService).doctorAvailability(null, doctorUserId.toString(), null, null, null, java.time.LocalDate.parse("2026-09-06"));
    }
}
