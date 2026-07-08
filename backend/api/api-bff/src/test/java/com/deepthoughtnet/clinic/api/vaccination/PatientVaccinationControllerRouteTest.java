package com.deepthoughtnet.clinic.api.vaccination;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.vaccination.dto.PatientVaccinationBillRequest;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientVaccinationControllerRouteTest {
    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void billEndpointLinksExistingVaccinationToBill() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID vaccinationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("RECEPTIONIST"), "RECEPTIONIST", "cid"));

        VaccinationService vaccinationService = mock(VaccinationService.class);
        when(vaccinationService.billVaccination(tenantId, patientId, vaccinationId, null, true, new BigDecimal("75.00"), actorId))
                .thenReturn(record(vaccinationId, tenantId, patientId));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PatientVaccinationController(vaccinationService)).build();

        mockMvc.perform(post("/api/patients/{patientId}/vaccinations/{vaccinationId}/bill", patientId, vaccinationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":null,\"createNewBill\":true,\"billItemUnitPrice\":75.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billNumber").value("BILL-2001"))
                .andExpect(jsonPath("$.billStatus").value("DRAFT"))
                .andExpect(jsonPath("$.patientId").value(patientId.toString()));
    }

    private PatientVaccinationRecord record(UUID id, UUID tenantId, UUID patientId) {
        return new PatientVaccinationRecord(
                id,
                tenantId,
                patientId,
                "PAT-1",
                "Test Patient",
                "9999999999",
                32,
                "MALE",
                null,
                UUID.randomUUID(),
                "COVID-19 Vaccine",
                "INTERNAL",
                null,
                null,
                "VERIFIED",
                null,
                null,
                null,
                1,
                LocalDate.of(2026, 7, 7),
                null,
                "LOT-1",
                null,
                actorId(),
                "Rohit Nair",
                actorId(),
                "Rohit Nair",
                null,
                null,
                null,
                UUID.randomUUID(),
                "BILL-2001",
                "DRAFT",
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.<String>of(),
                actorId(),
                "Rohit Nair",
                OffsetDateTime.parse("2026-07-07T10:00:00Z")
        );
    }

    private UUID actorId() {
        return UUID.fromString("22222222-2222-2222-2222-222222222222");
    }
}
