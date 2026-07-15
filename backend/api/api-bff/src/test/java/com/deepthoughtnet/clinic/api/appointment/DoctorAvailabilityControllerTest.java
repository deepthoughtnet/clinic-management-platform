package com.deepthoughtnet.clinic.api.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DoctorAvailabilityControllerTest {
    private AppointmentService appointmentService;
    private DoctorAssignmentSecurityService doctorAssignmentSecurityService;
    private ClinicTimeZoneResolver clinicTimeZoneResolver;
    private MockMvc mockMvc;
    private UUID tenantId;
    private UUID actorId;
    private UUID doctorId;

    @BeforeEach
    void setUp() {
        appointmentService = mock(AppointmentService.class);
        BillingService billingService = mock(BillingService.class);
        doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        DoctorAvailabilityController controller = new DoctorAvailabilityController(
                appointmentService,
                billingService,
                doctorAssignmentSecurityService,
                clinicTimeZoneResolver
        );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .setValidator(validator)
                .build();

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(
                TenantId.of(tenantId),
                actorId,
                "clinic-admin@clinic.test",
                Set.of("CLINIC_ADMIN"),
                "CLINIC_ADMIN",
                "corr-avail"
        ));

        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(java.time.ZoneId.of("UTC"));
        when(doctorAssignmentSecurityService.effectiveDoctorUserId(doctorId)).thenReturn(doctorId);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void createRejectsZeroDurationBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/doctors/{doctorUserId}/availability", doctorId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "dayOfWeek": "MONDAY",
                                  "startTime": "09:00:00",
                                  "endTime": "12:00:00",
                                  "consultationDurationMinutes": 0,
                                  "maxPatientsPerSlot": 1,
                                  "active": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("consultationDurationMinutes: consultationDurationMinutes must be greater than zero."));

        verifyNoInteractions(appointmentService);
    }

    @Test
    void createRejectsEqualStartAndEndTimeBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/doctors/{doctorUserId}/availability", doctorId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "dayOfWeek": "MONDAY",
                                  "startTime": "09:00:00",
                                  "endTime": "09:00:00",
                                  "consultationDurationMinutes": 15,
                                  "maxPatientsPerSlot": 1,
                                  "active": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("validTimeRange: End time must be after start time."));

        verifyNoInteractions(appointmentService);
    }
}
