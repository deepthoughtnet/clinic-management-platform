package com.deepthoughtnet.clinic.carepilot.engagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.billing.db.BillEntity;
import com.deepthoughtnet.clinic.billing.db.BillRepository;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementCohortType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementLevel;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PatientEngagementServiceTest {

    @Test
    void cohortMarksInactivePatientsAsHighRisk() throws Exception {
        PatientRepository patientRepository = Mockito.mock(PatientRepository.class);
        AppointmentRepository appointmentRepository = Mockito.mock(AppointmentRepository.class);
        ConsultationRepository consultationRepository = Mockito.mock(ConsultationRepository.class);
        PrescriptionRepository prescriptionRepository = Mockito.mock(PrescriptionRepository.class);
        PrescriptionMedicineRepository prescriptionMedicineRepository = Mockito.mock(PrescriptionMedicineRepository.class);
        BillRepository billRepository = Mockito.mock(BillRepository.class);
        PatientVaccinationRepository vaccinationRepository = Mockito.mock(PatientVaccinationRepository.class);
        CampaignExecutionRepository executionRepository = Mockito.mock(CampaignExecutionRepository.class);

        PatientEngagementService service = new PatientEngagementService(
                patientRepository,
                appointmentRepository,
                consultationRepository,
                prescriptionRepository,
                prescriptionMedicineRepository,
                billRepository,
                vaccinationRepository,
                executionRepository,
                90,
                2,
                3
        );

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        PatientEntity patient = PatientEntity.create(tenantId, "P-1001");
        patient.update(
                "Jane",
                "Doe",
                PatientGender.FEMALE,
                LocalDate.of(1990, 1, 1),
                36,
                "+10000000000",
                "jane@example.com",
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
                true
        );
        setField(patient, "id", patientId);

        AppointmentEntity staleNoShow = AppointmentEntity.create(tenantId, patientId, UUID.randomUUID());
        staleNoShow.update(
                LocalDate.now().minusDays(120),
                null,
                null,
                "follow-up",
                AppointmentType.FOLLOW_UP,
                AppointmentStatus.NO_SHOW,
                AppointmentPriority.NORMAL
        );

        BillEntity overdueBill = BillEntity.create(tenantId, "B-1", patientId, null, null, LocalDate.now().minusDays(30));
        overdueBill.update(patientId, null, null, LocalDate.now().minusDays(30), null, DiscountType.NONE, BigDecimal.ZERO, null, null, BigDecimal.ZERO);
        overdueBill.markStatus(BillStatus.UNPAID);
        overdueBill.setFinancials(BigDecimal.valueOf(500), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(500), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(500));

        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));
        when(appointmentRepository.findByTenantIdAndPatientIdOrderByAppointmentDateDescAppointmentTimeDescCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(staleNoShow));
        when(consultationRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of());
        when(prescriptionRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)).thenReturn(List.of());
        when(billRepository.findByTenantIdAndPatientIdOrderByBillDateDescCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(overdueBill));
        when(vaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(tenantId, patientId)).thenReturn(List.of());
        when(executionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());

        var overview = service.overview(tenantId);
        var highRisk = service.cohort(tenantId, EngagementCohortType.HIGH_RISK_PATIENTS, 0, 20);
        var inactive = service.cohort(tenantId, EngagementCohortType.INACTIVE_PATIENTS, 0, 20);

        assertThat(overview.totalActivePatients()).isEqualTo(1);
        assertThat(overview.highRiskPatientsCount()).isEqualTo(1);
        assertThat(highRisk).hasSize(1);
        assertThat(inactive).hasSize(1);
        assertThat(highRisk.get(0).engagementLevel()).isIn(EngagementLevel.CRITICAL, EngagementLevel.LOW);
        assertThat(highRisk.get(0).riskReasons()).isNotEmpty();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
