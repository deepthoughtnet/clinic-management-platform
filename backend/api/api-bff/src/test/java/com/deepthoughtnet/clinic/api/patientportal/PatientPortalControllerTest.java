package com.deepthoughtnet.clinic.api.patientportal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiStateResponse;
import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientPortalControllerTest {
    private PatientPortalCareAiService patientPortalCareAiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        patientPortalCareAiService = mock(PatientPortalCareAiService.class);
        PatientPortalController controller = new PatientPortalController(mock(PatientPortalService.class), patientPortalCareAiService);
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
}
