package com.deepthoughtnet.clinic.api.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.dashboard.dto.ClinicDashboardResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.notification.service.NotificationCenterService;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportingFacadeClinicDashboardTest {
    @Mock private AppointmentService appointmentService;
    @Mock private ConsultationService consultationService;
    @Mock private BillingService billingService;
    @Mock private VaccinationService vaccinationService;
    @Mock private PrescriptionService prescriptionService;
    @Mock private InventoryService inventoryService;
    @Mock private PatientRepository patientRepository;
    @Mock private NotificationCenterService notificationCenterService;

    private ReportingFacade reportingFacade;

    @BeforeEach
    void setUp() {
        reportingFacade = new ReportingFacade(
                appointmentService,
                consultationService,
                billingService,
                vaccinationService,
                prescriptionService,
                inventoryService,
                patientRepository,
                notificationCenterService
        );
    }

    @Test
    void clinicDashboardReturnsZeroesForEmptyDay() {
        UUID tenantId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 11);
        when(appointmentService.search(eq(tenantId), any(AppointmentSearchCriteria.class))).thenReturn(List.of());
        when(consultationService.list(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());

        ClinicDashboardResponse response = reportingFacade.clinicDashboard(tenantId, date, null);

        assertEquals(0, response.appointmentSummary().totalToday());
        assertEquals(0, response.queueSummary().waiting());
        assertEquals(0, response.consultationSummary().started());
        assertEquals(0, response.billingSummary().billsCreated());
        assertEquals(0, response.followUpSummary().dueInRange());
        assertEquals(0, response.recentActivity().size());
    }

    @Test
    void clinicDashboardAggregatesAppointmentAndBillingTotals() {
        UUID tenantId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 11);

        AppointmentRecord appointment = new AppointmentRecord(
                appointmentId, tenantId, patientId, "PAT-1", "Patient One", "99999",
                doctorId, "Dr. One", null, date, LocalTime.of(10, 0), 7, "Review",
                AppointmentType.SCHEDULED, AppointmentPriority.NORMAL, AppointmentStatus.WAITING,
                OffsetDateTime.parse("2026-05-11T09:50:00Z"), OffsetDateTime.parse("2026-05-11T10:01:00Z")
        );
        ConsultationRecord consultation = new ConsultationRecord(
                UUID.randomUUID(), tenantId, patientId, "PAT-1", "Patient One", doctorId, "Dr. One",
                appointmentId, null, null, null, null, null, date.plusDays(1), ConsultationStatus.DRAFT,
                null, null, null, null, TemperatureUnit.CELSIUS, null, null, null, null, null,
                OffsetDateTime.parse("2026-05-11T10:10:00Z"), OffsetDateTime.parse("2026-05-11T10:10:00Z")
        );
        BillRecord bill = new BillRecord(
                billId, tenantId, "B-1", patientId, "PAT-1", "Patient One", null, appointmentId, date,
                BillStatus.PARTIALLY_PAID, BigDecimal.valueOf(100), DiscountType.NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                BigDecimal.valueOf(100), BigDecimal.valueOf(60), BigDecimal.ZERO, BigDecimal.valueOf(60), BigDecimal.valueOf(40), null, null,
                OffsetDateTime.parse("2026-05-11T10:20:00Z"), OffsetDateTime.parse("2026-05-11T10:20:00Z"), List.of()
        );
        PaymentRecord payment = new PaymentRecord(
                UUID.randomUUID(), tenantId, billId, date, OffsetDateTime.parse("2026-05-11T10:30:00Z"), BigDecimal.valueOf(60), PaymentMode.CASH,
                null, null, null, null, null, null, OffsetDateTime.parse("2026-05-11T10:30:00Z")
        );
        PrescriptionRecord prescription = new PrescriptionRecord(
                UUID.randomUUID(), tenantId, patientId, "PAT-1", "Patient One", doctorId, "Dr. One",
                consultation.id(), appointmentId, "RX-1", 1, null, null, null, null, null, null,
                null, null, null, PrescriptionStatus.FINALIZED, null, null, null, null,
                OffsetDateTime.parse("2026-05-11T11:00:00Z"), OffsetDateTime.parse("2026-05-11T11:00:00Z"),
                List.of(), List.of()
        );

        when(appointmentService.search(eq(tenantId), any(AppointmentSearchCriteria.class))).thenReturn(List.of(appointment));
        when(consultationService.list(tenantId)).thenReturn(List.of(consultation));
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(bill));
        when(billingService.listPayments(tenantId, billId)).thenReturn(List.of(payment));
        when(prescriptionService.list(tenantId)).thenReturn(List.of(prescription));

        ClinicDashboardResponse response = reportingFacade.clinicDashboard(tenantId, date, null);

        assertEquals(1, response.appointmentSummary().totalToday());
        assertEquals(1, response.appointmentSummary().checkedIn());
        assertEquals(1, response.queueSummary().waiting());
        assertEquals(1, response.consultationSummary().activeNow());
        assertEquals(BigDecimal.valueOf(100).setScale(2), response.billingSummary().totalBilled());
        assertEquals(BigDecimal.valueOf(60).setScale(2), response.billingSummary().totalPaid());
        assertEquals(1, response.doctorSummaries().size());
    }

    @Test
    void filterByRoleReturnsFinanceOnlyForBillingUser() {
        UUID tenantId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 11);
        ClinicDashboardResponse full = new ClinicDashboardResponse(
                date,
                date,
                tenantId,
                new ClinicDashboardResponse.AppointmentSummary(1, 1, 0, 0, 0, 0, 0),
                new ClinicDashboardResponse.QueueSummary(0, 0, 0, 0, 0, 0),
                new ClinicDashboardResponse.ConsultationSummary(1, 0, 1, 0),
                new ClinicDashboardResponse.PrescriptionSummary(1, 0, BigDecimal.ONE),
                new ClinicDashboardResponse.BillingSummary(2, 1, 1, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(9)),
                new ClinicDashboardResponse.FollowUpSummary(1, 0, 0),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ClinicDashboardResponse.RecentActivityItem(OffsetDateTime.now(), "PAYMENT", "Payment received", "Paid", null, null))
        );

        ClinicDashboardResponse filtered = reportingFacade.filterByRole(full, Set.of("BILLING_USER"));

        assertEquals(null, filtered.appointmentSummary());
        assertEquals(null, filtered.queueSummary());
        assertEquals(null, filtered.consultationSummary());
        assertEquals(null, filtered.prescriptionSummary());
        assertEquals(2, filtered.billingSummary().billsCreated());
    }

    @Test
    void filterByRoleRemovesFinancialAmountsForReceptionist() {
        UUID tenantId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 11);
        ClinicDashboardResponse full = new ClinicDashboardResponse(
                date,
                date,
                tenantId,
                new ClinicDashboardResponse.AppointmentSummary(1, 1, 0, 0, 0, 0, 0),
                new ClinicDashboardResponse.QueueSummary(0, 0, 0, 0, 0, 0),
                new ClinicDashboardResponse.ConsultationSummary(1, 0, 1, 0),
                new ClinicDashboardResponse.PrescriptionSummary(1, 0, BigDecimal.ONE),
                new ClinicDashboardResponse.BillingSummary(2, 1, 1, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(9)),
                new ClinicDashboardResponse.FollowUpSummary(1, 0, 0),
                List.of(new ClinicDashboardResponse.DoctorSummary(UUID.randomUUID(), "Dr", 1, 0, 0, 0, 0, null, BigDecimal.ONE, 0, 0, BigDecimal.ONE)),
                List.of(),
                List.of(),
                List.of(new ClinicDashboardResponse.RecentActivityItem(OffsetDateTime.now(), "PAYMENT", "Payment received", "Paid", null, null))
        );

        ClinicDashboardResponse filtered = reportingFacade.filterByRole(full, Set.of("RECEPTIONIST"));

        assertEquals(BigDecimal.ZERO.setScale(2), filtered.billingSummary().totalBilled());
        assertEquals(BigDecimal.ZERO.setScale(2), filtered.billingSummary().totalPaid());
        assertEquals(BigDecimal.ZERO.setScale(2), filtered.billingSummary().pendingAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), filtered.doctorSummaries().getFirst().revenue());
        assertEquals(0, filtered.recentActivity().size());
    }

    @Test
    void filterByRoleKeepsClinicalAndDropsBillingForDoctor() {
        UUID tenantId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 11);
        ClinicDashboardResponse full = new ClinicDashboardResponse(
                date,
                date,
                tenantId,
                new ClinicDashboardResponse.AppointmentSummary(1, 1, 0, 0, 0, 0, 0),
                new ClinicDashboardResponse.QueueSummary(1, 0, 0, 0, 0, 0),
                new ClinicDashboardResponse.ConsultationSummary(1, 0, 1, 0),
                new ClinicDashboardResponse.PrescriptionSummary(1, 0, BigDecimal.ONE),
                new ClinicDashboardResponse.BillingSummary(2, 1, 1, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(9)),
                new ClinicDashboardResponse.FollowUpSummary(1, 0, 0),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ClinicDashboardResponse.RecentActivityItem(OffsetDateTime.now(), "CONSULTATION", "Consultation started", "Started", null, null))
        );

        ClinicDashboardResponse filtered = reportingFacade.filterByRole(full, Set.of("DOCTOR"));

        assertNotNull(filtered.appointmentSummary());
        assertNotNull(filtered.queueSummary());
        assertNotNull(filtered.consultationSummary());
        assertEquals(null, filtered.billingSummary());
    }
}
