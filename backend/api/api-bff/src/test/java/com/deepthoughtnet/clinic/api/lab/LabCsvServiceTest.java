package com.deepthoughtnet.clinic.api.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterRepository;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LabCsvServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TEST_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private LabService labService;
    @Mock private LabTestMasterRepository labTestMasterRepository;

    private LabCsvService service;

    @BeforeEach
    void setUp() {
        service = new LabCsvService(labService, labTestMasterRepository);
    }

    @Test
    void importsLabTestsFromCsvAndReportsRowErrors() throws Exception {
        when(labTestMasterRepository.findByTenantIdAndTestCodeIgnoreCase(TENANT_ID, "CBC")).thenReturn(Optional.empty());
        when(labTestMasterRepository.findByTenantIdAndTestNameIgnoreCase(TENANT_ID, "Complete Blood Count")).thenReturn(Optional.empty());
        when(labService.createTest(any(), any(), any())).thenReturn(sampleRecord());

        String csv = String.join("\n",
                "testCode,testName,category,sampleType,department,unit,referenceRange,turnaroundTime,price,active,parameters",
                "CBC,Complete Blood Count,HEMATOLOGY,Blood,Hematology,g/dL,13.0-17.0,24 hrs,250.00,true,\"Hemoglobin|g/dL|13.0-17.0|<7.0|1\"",
                ",,HEMATOLOGY,Blood,,,,-1,true,"
        );

        var result = service.importCsv(TENANT_ID, csv.getBytes(), ACTOR_ID);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rowErrors()).hasSize(1);
        assertThat(result.rowErrors().get(0).message()).contains("testName");
    }

    @Test
    void updatesExistingLabTestFromCsv() throws Exception {
        LabTestMasterEntity existing = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        when(labTestMasterRepository.findByTenantIdAndTestCodeIgnoreCase(TENANT_ID, "CBC")).thenReturn(Optional.of(existing));
        when(labService.updateTest(any(), any(), any(), any())).thenReturn(sampleRecord());

        String csv = String.join("\n",
                "testCode,testName,category,sampleType,department,unit,referenceRange,turnaroundTime,price,active,parameters",
                "CBC,Complete Blood Count,HEMATOLOGY,Blood,Hematology,g/dL,13.0-17.0,24 hrs,250.00,true,"
        );

        var result = service.importCsv(TENANT_ID, csv.getBytes(), ACTOR_ID);

        assertThat(result.createdCount()).isZero();
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
    }

    @Test
    void importTemplateIncludesHeaders() throws Exception {
        String csv = service.importTemplateCsv();
        assertThat(csv).contains("testCode,testName,category,sampleType,department,unit,referenceRange,turnaroundTime,price,active,parameters");
        assertThat(csv).contains("Complete Blood Count");
    }

    @Test
    void acceptsExpandedCategoriesAndNormalizesThyroidToEndocrinology() throws Exception {
        when(labTestMasterRepository.findByTenantIdAndTestCodeIgnoreCase(TENANT_ID, "THYROID-1")).thenReturn(Optional.empty());
        when(labTestMasterRepository.findByTenantIdAndTestNameIgnoreCase(TENANT_ID, "Thyroid Panel")).thenReturn(Optional.empty());
        when(labService.createTest(any(), any(), any())).thenReturn(sampleRecord());

        String csv = String.join("\n",
                "testCode,testName,category,sampleType,department,unit,referenceRange,turnaroundTime,price,active,parameters",
                "THYROID-1,Thyroid Panel,THYROID,Blood,Endocrinology,ng/dL,0.4-4.0,24 hrs,250.00,true,"
        );

        var result = service.importCsv(TENANT_ID, csv.getBytes(), ACTOR_ID);

        assertThat(result.failedCount()).isZero();
        ArgumentCaptor<com.deepthoughtnet.clinic.api.lab.service.model.LabTestUpsertCommand> captor = ArgumentCaptor.forClass(com.deepthoughtnet.clinic.api.lab.service.model.LabTestUpsertCommand.class);
        verify(labService).createTest(any(), captor.capture(), any());
        assertThat(captor.getValue().category()).isEqualTo("ENDOCRINOLOGY");
    }

    @Test
    void invalidCategoryMessageIncludesAllowedValues() throws Exception {
        String csv = String.join("\n",
                "testCode,testName,category,sampleType,department,unit,referenceRange,turnaroundTime,price,active,parameters",
                "BAD-1,Invalid Category,BADCATEGORY,Blood,Lab,mg/dL,0-1,24 hrs,100.00,true,"
        );

        var result = service.importCsv(TENANT_ID, csv.getBytes(), ACTOR_ID);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rowErrors().get(0).message()).contains("BADCATEGORY");
        assertThat(result.rowErrors().get(0).message()).contains("HEMATOLOGY");
        assertThat(result.rowErrors().get(0).message()).contains("THYROID");
    }

    @Test
    void duplicateParameterConstraintIsReportedAsRowError() throws Exception {
        LabTestMasterEntity existing = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        when(labTestMasterRepository.findByTenantIdAndTestCodeIgnoreCase(TENANT_ID, "CBC")).thenReturn(Optional.of(existing));
        when(labService.updateTest(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_lab_test_parameters_tenant_test_name\""));

        String csv = String.join("\n",
                "testCode,testName,category,sampleType,department,unit,referenceRange,turnaroundTime,price,active,parameters",
                "CBC,Complete Blood Count,HEMATOLOGY,Blood,Hematology,g/dL,13.0-17.0,24 hrs,250.00,true,\"Hemoglobin|g/dL|13.0-17.0|<7.0|1\""
        );

        var result = service.importCsv(TENANT_ID, csv.getBytes(), ACTOR_ID);

        assertThat(result.createdCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rowErrors().get(0).message()).contains("parameter names must be unique within a test");
    }

    private LabTestRecord sampleRecord() {
        return new LabTestRecord(
                TEST_ID,
                TENANT_ID,
                "CBC",
                "Complete Blood Count",
                "HEMATOLOGY",
                "Hematology",
                "Blood",
                "g/dL",
                "13.0-17.0",
                "24 hrs",
                BigDecimal.valueOf(250),
                true,
                null,
                null,
                null,
                true,
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
