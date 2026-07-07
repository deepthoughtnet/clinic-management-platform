package com.deepthoughtnet.clinic.api.vaccination;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationRecommendationService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccinationRecommendationSummary;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VaccinationControllerRouteTest {
    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    private PatientVaccinationRecord vaccinationRecord(
            UUID id,
            UUID tenantId,
            UUID patientId,
            String patientNumber,
            String patientName,
            String patientMobile,
            Integer patientAgeYears,
            String patientGender,
            String patientAllergies,
            UUID vaccineId,
            String vaccineName,
            Integer doseNumber,
            LocalDate givenDate,
            LocalDate nextDueDate,
            String batchNumber,
            String notes,
            UUID administeredByUserId,
            String administeredByUserName,
            UUID billId,
            String billNumber,
            String billStatus,
            UUID recordedByUserId,
            String recordedByUserName,
            OffsetDateTime createdAt
    ) {
        return new PatientVaccinationRecord(
                id,
                tenantId,
                patientId,
                patientNumber,
                patientName,
                patientMobile,
                patientAgeYears,
                patientGender,
                patientAllergies,
                vaccineId,
                vaccineName,
                doseNumber,
                givenDate,
                nextDueDate,
                batchNumber,
                notes,
                administeredByUserId,
                administeredByUserName,
                recordedByUserId,
                recordedByUserName,
                recordedByUserId,
                recordedByUserName,
                createdAt,
                billId,
                billNumber,
                billStatus,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                recordedByUserId,
                recordedByUserName,
                createdAt
        );
    }

    @Test
    void historyEndpointFiltersRowsByPatientVaccineDateAndDueStatus() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID vaccineId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        LocalDate today = LocalDate.now();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        VaccinationService vaccinationService = mock(VaccinationService.class);
        when(vaccinationService.listHistory(tenantId)).thenReturn(List.of(
                vaccinationRecord(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        tenantId,
                        patientId,
                        "PAT-0001",
                        "Asha Rao",
                        "9999999999",
                        6,
                        "FEMALE",
                        "Penicillin",
                        vaccineId,
                        "MMR",
                        2,
                        today.minusDays(7),
                        today.plusDays(7),
                        "BATCH-1",
                        "Notes",
                        actorId,
                        "Nurse Joy",
                        UUID.fromString("66666666-6666-6666-6666-666666666666"),
                        "BILL-1001",
                        "PAID",
                        actorId,
                        "Nurse Joy",
                        OffsetDateTime.parse("2026-07-01T10:00:00Z")
                ),
                vaccinationRecord(
                        UUID.fromString("77777777-7777-7777-7777-777777777777"),
                        tenantId,
                        UUID.fromString("88888888-8888-8888-8888-888888888888"),
                        "PAT-0002",
                        "Ravi Kumar",
                        "8888888888",
                        8,
                        "M",
                        null,
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        "Polio",
                        1,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 20),
                        "BATCH-2",
                        null,
                        actorId,
                        "Nurse Joy",
                        null,
                        null,
                        null,
                        actorId,
                        "Nurse Joy",
                        OffsetDateTime.parse("2026-06-01T10:00:00Z")
                )
        ));

        VaccinationRecommendationService recommendationService = mock(VaccinationRecommendationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VaccinationController(vaccinationService, recommendationService)).build();

        mockMvc.perform(get("/api/vaccinations/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].patientName").value("Asha Rao"))
                .andExpect(jsonPath("$[0].vaccineName").value("MMR"))
                .andExpect(jsonPath("$[0].billNumber").value("BILL-1001"))
                .andExpect(jsonPath("$[0].recordedByUserName").value("Nurse Joy"));
    }

    @Test
    void recommendationsEndpointReturnsGroupedCategories() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        VaccinationService vaccinationService = mock(VaccinationService.class);
        VaccinationRecommendationService recommendationService = mock(VaccinationRecommendationService.class);
        when(recommendationService.recommend(tenantId, patientId, null)).thenReturn(new VaccinationRecommendationSummary(
                patientId.toString(),
                "ALL",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VaccinationController(vaccinationService, recommendationService)).build();

        mockMvc.perform(get("/api/vaccinations/recommendations").param("patientId", patientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.recommendedToday").isArray())
                .andExpect(jsonPath("$.overdue").isArray())
                .andExpect(jsonPath("$.upcoming").isArray())
                .andExpect(jsonPath("$.completed").isArray())
                .andExpect(jsonPath("$.optionalRiskBased").isArray())
                .andExpect(jsonPath("$.notApplicable").isArray());
    }
}
