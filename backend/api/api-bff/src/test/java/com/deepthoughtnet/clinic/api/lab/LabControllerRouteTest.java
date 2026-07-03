package com.deepthoughtnet.clinic.api.lab;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.lab.service.LabService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LabControllerRouteTest {

    @Test
    void importTemplateUsesCsvNegotiationAndAttachmentHeaders() throws Exception {
        LabCsvService labCsvService = mock(LabCsvService.class);
        when(labCsvService.importTemplateCsv()).thenReturn("testCode,testName\nCBC,Complete Blood Count\n");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LabController(mock(LabService.class), labCsvService)).build();

        mockMvc.perform(get("/api/lab/tests/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("lab-tests-import-template.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("testCode,testName")));
    }
}
