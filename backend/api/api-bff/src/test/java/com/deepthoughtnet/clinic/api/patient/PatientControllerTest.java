package com.deepthoughtnet.clinic.api.patient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientControllerTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private PatientService patientService;
    private AppointmentService appointmentService;
    private ConsultationService consultationService;
    private PrescriptionService prescriptionService;
    private DoctorAssignmentSecurityService doctorAssignmentSecurityService;
    private ClinicTimeZoneResolver clinicTimeZoneResolver;
    private AppUserRepository appUserRepository;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private UUID tenantId;
    private UUID actorId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        patientService = mock(PatientService.class);
        appointmentService = mock(AppointmentService.class);
        consultationService = mock(ConsultationService.class);
        prescriptionService = mock(PrescriptionService.class);
        doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        appUserRepository = mock(AppUserRepository.class);
        permissionChecker = mock(PermissionChecker.class);

        PatientController controller = new PatientController(
                patientService,
                appointmentService,
                consultationService,
                prescriptionService,
                doctorAssignmentSecurityService,
                clinicTimeZoneResolver,
                appUserRepository,
                permissionChecker
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "clinic.admin@clinic.test", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-patient"));

        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(UTC);
        when(permissionChecker.hasPermission("patient.update")).thenReturn(true);
        doNothing().when(doctorAssignmentSecurityService).requirePatientAccess(tenantId, patientId);
        when(appointmentService.listByPatient(tenantId, patientId)).thenReturn(List.<AppointmentRecord>of());
        when(consultationService.listByPatient(tenantId, patientId)).thenReturn(List.<ConsultationRecord>of());
        when(prescriptionService.listByPatient(tenantId, patientId)).thenReturn(List.of());
        when(appUserRepository.findByTenantIdAndId(eq(tenantId), eq(actorId))).thenReturn(Optional.of(mock(AppUserEntity.class)));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void clinicAdminCanEditExistingPatientAllergies() throws Exception {
        when(patientService.findById(tenantId, patientId)).thenReturn(Optional.of(patientRecord("Paracetamol", OffsetDateTime.parse("2026-05-20T10:00:00Z"))));
        when(patientService.update(any(), any(), any(), any(), any(), any(), any())).thenReturn(patientRecord("Penicillin", OffsetDateTime.parse("2026-05-20T10:00:00Z")));

        mockMvc.perform(get("/api/patients/{id}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.canEdit").value(true))
                .andExpect(jsonPath("$.patient.allergies").value("Paracetamol"));

        ArgumentCaptor<PatientUpsertCommand> commandCaptor = ArgumentCaptor.forClass(PatientUpsertCommand.class);
        mockMvc.perform(put("/api/patients/{id}", patientId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Rohan",
                                  "lastName": "Sharma",
                                  "gender": "MALE",
                                  "mobile": "9876543210",
                                  "allergies": "Penicillin",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergies").value("Penicillin"))
                .andExpect(jsonPath("$.canEdit").value(true));

        verify(patientService).update(eq(tenantId), eq(patientId), commandCaptor.capture(), eq(actorId), eq("CLINIC_ADMIN"), eq(UTC), isNull());
        assertThat(commandCaptor.getValue().allergies()).isEqualTo("Penicillin");
    }

    @Test
    void clinicAdminCanPersistExistingConditionsAndLongTermMedicationsOnUpdate() throws Exception {
        when(patientService.findById(tenantId, patientId)).thenReturn(Optional.of(patientRecord("Paracetamol", OffsetDateTime.parse("2026-05-20T10:00:00Z"))));
        when(patientService.update(any(), any(), any(), any(), any(), any(), any())).thenReturn(patientRecord("Penicillin", "Type 2 Diabetes Mellitus", "Metformin 500 mg twice daily", OffsetDateTime.parse("2026-05-20T10:00:00Z")));

        ArgumentCaptor<PatientUpsertCommand> commandCaptor = ArgumentCaptor.forClass(PatientUpsertCommand.class);
        mockMvc.perform(put("/api/patients/{id}", patientId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Rohan",
                                  "lastName": "Sharma",
                                  "gender": "MALE",
                                  "mobile": "9876501234",
                                  "bloodGroup": "B+",
                                  "allergies": "Penicillin",
                                  "existingConditions": "Type 2 Diabetes Mellitus",
                                  "longTermMedications": "Metformin 500 mg twice daily",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingConditions").value("Type 2 Diabetes Mellitus"))
                .andExpect(jsonPath("$.longTermMedications").value("Metformin 500 mg twice daily"));

        verify(patientService).update(eq(tenantId), eq(patientId), commandCaptor.capture(), eq(actorId), eq("CLINIC_ADMIN"), eq(UTC), isNull());
        assertThat(commandCaptor.getValue().existingConditions()).isEqualTo("Type 2 Diabetes Mellitus");
        assertThat(commandCaptor.getValue().longTermMedications()).isEqualTo("Metformin 500 mg twice daily");
    }

    private PatientRecord patientRecord(String allergies, OffsetDateTime timestamp) {
        return patientRecord(allergies, "Diabetes", "Metformin", timestamp);
    }

    private PatientRecord patientRecord(String allergies, String existingConditions, String longTermMedications, OffsetDateTime timestamp) {
        return new PatientRecord(
                patientId,
                tenantId,
                "PAT-1001",
                "Rohan",
                "Sharma",
                PatientGender.MALE,
                LocalDate.of(1984, 1, 15),
                42,
                "9876543210",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "O+",
                allergies,
                existingConditions,
                longTermMedications,
                "Appendectomy",
                null,
                true,
                timestamp,
                timestamp
        );
    }
}
