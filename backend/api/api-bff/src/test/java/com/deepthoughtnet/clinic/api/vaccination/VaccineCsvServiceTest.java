package com.deepthoughtnet.clinic.api.vaccination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineCsvImportResponse;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineMasterRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VaccineCsvServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VACCINE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private VaccinationService vaccinationService;

    private VaccineCsvService service;

    @BeforeEach
    void setUp() {
        service = new VaccineCsvService(vaccinationService);
    }

    @Test
    void importsValidRowsAndReportsDuplicates() {
        when(vaccinationService.createVaccine(eq(TENANT_ID), any(VaccineUpsertCommand.class), eq(ACTOR_ID)))
                .thenReturn(sampleRecord());

        String csv = String.join("\n",
                "vaccineName,description,manufacturer,brandName,vaccineGroup,doseNumber,route,administrationSite,storageTemperature,ndcBarcode,scheduleType,ageGroup,minAgeDays,recommendedAgeDays,maxAgeDays,gapDays,boosterGapDays,boosterRules,isRecurring,recurrenceDays,recommendationPolicy,catchUpPolicy,catchUpMaxAgeDays,applicableAgeGroup,clinicalIndications,defaultPrice,active",
                "Hepatitis B,First dose,Acme,Brand,HBV,1,IM,Deltoid,2-8 C,123,CLINIC_CUSTOM,Infants,0,30,90,30,60,Annual,false,365,STANDARD_CHILDHOOD,NONE,,INFANT,,120.50,true",
                "Hepatitis B,Duplicate entry,Acme,Brand,HBV,1,IM,Deltoid,2-8 C,123,CLINIC_CUSTOM,Infants,0,30,90,30,60,Annual,false,365,STANDARD_CHILDHOOD,NONE,,INFANT,,120.50,true"
        );

        VaccineCsvImportResponse result = service.importCsv(TENANT_ID, csv.getBytes(), ACTOR_ID);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rows()).extracting("status").containsExactly("CREATED", "FAILED");
        assertThat(result.rows().get(1).message()).contains("Duplicate vaccine name");

        ArgumentCaptor<VaccineUpsertCommand> captor = ArgumentCaptor.forClass(VaccineUpsertCommand.class);
        verify(vaccinationService, times(1)).createVaccine(eq(TENANT_ID), captor.capture(), eq(ACTOR_ID));
        assertThat(captor.getValue().vaccineName()).isEqualTo("Hepatitis B");
        assertThat(captor.getValue().recommendedGapDays()).isEqualTo(30);
        assertThat(captor.getValue().route()).isEqualTo("IM");
        assertThat(captor.getValue().scheduleType()).isEqualTo("CLINIC_CUSTOM");
        assertThat(captor.getValue().recommendationPolicy()).isEqualTo("STANDARD_CHILDHOOD");
        assertThat(captor.getValue().catchUpPolicy()).isEqualTo("NONE");
        assertThat(captor.getValue().applicableAgeGroup()).isEqualTo("INFANT");
        assertThat(captor.getValue().defaultPrice()).isEqualByComparingTo("120.50");
        assertThat(captor.getValue().active()).isTrue();
    }

    @Test
    void rejectsInvalidCsvValuesAsRowErrors() {
        String csv = String.join("\n",
                "vaccineName,description,manufacturer,brandName,vaccineGroup,doseNumber,route,administrationSite,storageTemperature,ndcBarcode,scheduleType,ageGroup,minAgeDays,recommendedAgeDays,maxAgeDays,gapDays,boosterGapDays,boosterRules,isRecurring,recurrenceDays,recommendationPolicy,catchUpPolicy,catchUpMaxAgeDays,applicableAgeGroup,clinicalIndications,defaultPrice,active",
                "Broken Vaccine,Description,Acme,Brand,HBV,-1,INVALID,Site,Temp,Barcode,UNKNOWN,Adults,-1,-1,-1,-1,-1,Rules,maybe,-1,INVALID,INVALID,-1,UNKNOWN,notes,-5,falsee"
        );

        VaccineCsvImportResponse result = service.importCsv(TENANT_ID, csv.getBytes(), ACTOR_ID);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.createdCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rows().get(0).status()).isEqualTo("FAILED");
        assertThat(result.rows().get(0).message()).containsAnyOf("doseNumber", "gapDays", "defaultPrice", "active", "route", "scheduleType", "recommendationPolicy", "catchUpPolicy", "applicableAgeGroup");
    }

    @Test
    void templateAndExportCsvIncludeExpectedColumns() {
        when(vaccinationService.listVaccines(TENANT_ID)).thenReturn(List.of(sampleRecord()));

        String template = service.importTemplateCsv();
        String export = service.exportCsv(TENANT_ID);

        assertThat(template).contains("vaccineName,description,manufacturer,brandName,vaccineGroup,doseNumber,route,administrationSite,storageTemperature,ndcBarcode,scheduleType,ageGroup,minAgeDays,recommendedAgeDays,maxAgeDays,gapDays,boosterGapDays,boosterRules,isRecurring,recurrenceDays,recommendationPolicy,catchUpPolicy,catchUpMaxAgeDays,applicableAgeGroup,clinicalIndications,defaultPrice,active");
        assertThat(template).contains("COVID Booster");
        assertThat(export).contains("vaccineName,description,manufacturer,brandName,vaccineGroup,doseNumber,route,administrationSite,storageTemperature,ndcBarcode,scheduleType,ageGroup,minAgeDays,recommendedAgeDays,maxAgeDays,gapDays,boosterGapDays,boosterRules,isRecurring,recurrenceDays,recommendationPolicy,catchUpPolicy,catchUpMaxAgeDays,applicableAgeGroup,clinicalIndications,defaultPrice,active");
        assertThat(export).contains("Hepatitis B");
        assertThat(export).contains("120.50");
    }

    @Test
    void importsLegacyCsvWithoutPolicyColumns() {
        String csv = String.join("\n",
                "vaccineName,description,ageGroup,gapDays,defaultPrice,active",
                "Influenza,Annual flu,Adult,365,100.00,true"
        );

        VaccineCsvImportResponse result = service.importCsv(TENANT_ID, csv.getBytes(), ACTOR_ID);

        assertThat(result.failedCount()).isZero();
        assertThat(result.createdCount()).isEqualTo(1);
        ArgumentCaptor<VaccineUpsertCommand> captor = ArgumentCaptor.forClass(VaccineUpsertCommand.class);
        verify(vaccinationService, times(1)).createVaccine(eq(TENANT_ID), captor.capture(), eq(ACTOR_ID));
        assertThat(captor.getValue().recommendationPolicy()).isEqualTo("CLINIC_CUSTOM");
        assertThat(captor.getValue().catchUpPolicy()).isEqualTo("NONE");
    }

    private VaccineMasterRecord sampleRecord() {
        return new VaccineMasterRecord(
                VACCINE_ID,
                TENANT_ID,
                "Hepatitis B",
                "Infant series",
                "Acme",
                "Brand",
                "HBV",
                1,
                "IM",
                "Deltoid",
                "2-8 C",
                "123",
                null,
                null,
                false,
                "CLINIC_CUSTOM",
                "Infants",
                0,
                30,
                90,
                30,
                30,
                60,
                "Annual",
                false,
                365,
                "STANDARD_CHILDHOOD",
                "NONE",
                null,
                "INFANT",
                "travel,occupational",
                BigDecimal.valueOf(120.5),
                true,
                OffsetDateTime.parse("2026-07-05T10:15:30Z"),
                OffsetDateTime.parse("2026-07-05T10:15:30Z")
        );
    }
}
