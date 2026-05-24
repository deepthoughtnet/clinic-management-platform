package com.deepthoughtnet.clinic.api.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.RefundRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyCashierShiftEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacyCashierShiftRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleItemEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleItemRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePaymentEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePaymentRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.notification.service.NotificationCenterService;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportingFacadeFinanceReportsTest {
    @Mock private AppointmentService appointmentService;
    @Mock private ConsultationService consultationService;
    @Mock private BillingService billingService;
    @Mock private VaccinationService vaccinationService;
    @Mock private PrescriptionService prescriptionService;
    @Mock private InventoryService inventoryService;
    @Mock private MedicineRepository medicineRepository;
    @Mock private PharmacySaleRepository pharmacySaleRepository;
    @Mock private PharmacySaleItemRepository pharmacySaleItemRepository;
    @Mock private PharmacySalePaymentRepository pharmacySalePaymentRepository;
    @Mock private PharmacySaleReturnRepository pharmacySaleReturnRepository;
    @Mock private PharmacyCashierShiftRepository pharmacyCashierShiftRepository;
    @Mock private AppUserRepository appUserRepository;
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
                medicineRepository,
                pharmacySaleRepository,
                pharmacySaleItemRepository,
                pharmacySalePaymentRepository,
                pharmacySaleReturnRepository,
                pharmacyCashierShiftRepository,
                appUserRepository,
                patientRepository,
                notificationCenterService
        );
    }

    @Test
    void revenueIncludesPharmacySalesAndRefunds() {
        UUID tenantId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 23);
        BillRecord bill = new BillRecord(
                billId, tenantId, "BILL-1", UUID.randomUUID(), "PAT-1", "Clinic Patient", null, null, date,
                BillStatus.PAID, new BigDecimal("120.00"), DiscountType.NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                new BigDecimal("120.00"), new BigDecimal("120.00"), new BigDecimal("20.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, null,
                OffsetDateTime.parse("2026-05-23T09:00:00Z"), OffsetDateTime.parse("2026-05-23T09:00:00Z"), List.of()
        );
        PaymentRecord billPayment = new PaymentRecord(
                UUID.randomUUID(), tenantId, billId, date, OffsetDateTime.parse("2026-05-23T09:05:00Z"), new BigDecimal("120.00"), PaymentMode.CASH,
                null, null, UUID.randomUUID(), null, null, null, OffsetDateTime.parse("2026-05-23T09:05:00Z")
        );
        PharmacySaleEntity sale = PharmacySaleEntity.create(
                tenantId, "PS-1", null, "Walk In", "9999999999", OffsetDateTime.parse("2026-05-23T10:00:00Z"), null,
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), new BigDecimal("50.00"), BigDecimal.ZERO, "PAID", null, UUID.randomUUID()
        );
        PharmacySalePaymentEntity salePayment = PharmacySalePaymentEntity.create(
                tenantId, sale.getId(), null, date, OffsetDateTime.parse("2026-05-23T10:05:00Z"), new BigDecimal("50.00"), "UPI", "UPI-1", "RCPT-1", null, UUID.randomUUID()
        );
        PharmacySaleReturnEntity saleReturn = PharmacySaleReturnEntity.create(
                tenantId, sale.getId(), UUID.randomUUID(), "RET-1", UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"), true, "Damaged", "UPI", "REF-1", null, UUID.randomUUID()
        );
        setField(saleReturn, "createdAt", OffsetDateTime.parse("2026-05-23T10:10:00Z"));
        RefundRecord billRefund = new RefundRecord(
                UUID.randomUUID(), tenantId, billId, billPayment.id(), new BigDecimal("20.00"), "Return", PaymentMode.CASH, null, UUID.randomUUID(),
                OffsetDateTime.parse("2026-05-23T11:00:00Z"), OffsetDateTime.parse("2026-05-23T11:00:00Z")
        );

        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(bill));
        when(billingService.listPayments(tenantId, billId)).thenReturn(List.of(billPayment));
        when(billingService.listRefunds(tenantId, billId)).thenReturn(List.of(billRefund));
        when(pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(sale));
        when(pharmacySalePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, sale.getId())).thenReturn(List.of(salePayment));
        when(pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(saleReturn));

        List<Map<String, Object>> rows = reportingFacade.revenue(tenantId, date, date, null, null, null, "ALL");

        assertEquals(3, rows.size());
        assertEquals("CLINIC", rows.get(0).get("source"));
        assertEquals(new BigDecimal("120.00"), rows.get(0).get("clinicRevenue"));
        assertEquals(new BigDecimal("120.00"), rows.get(0).get("totalGrossRevenue"));
        assertEquals(new BigDecimal("0.00"), rows.get(0).get("discounts"));
        assertEquals(new BigDecimal("0.00"), rows.get(0).get("tax"));
        assertEquals("PHARMACY", rows.get(1).get("source"));
        assertEquals(new BigDecimal("50.00"), rows.get(1).get("pharmacyRevenue"));
        assertEquals(new BigDecimal("50.00"), rows.get(1).get("totalGrossRevenue"));
        assertEquals("TOTAL", rows.get(2).get("source"));
        assertEquals(new BigDecimal("120.00"), rows.get(2).get("clinicRevenue"));
        assertEquals(new BigDecimal("50.00"), rows.get(2).get("pharmacyRevenue"));
        assertEquals(new BigDecimal("170.00"), rows.get(2).get("totalGrossRevenue"));
        assertEquals(new BigDecimal("170.00"), rows.get(2).get("totalRevenue"));
        assertEquals(new BigDecimal("30.00"), rows.get(2).get("refunds"));
        assertEquals(new BigDecimal("140.00"), rows.get(2).get("netRevenue"));
    }

    @Test
    void paymentModesIncludesPharmacyPayments() {
        UUID tenantId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 23);
        BillRecord bill = new BillRecord(
                billId, tenantId, "BILL-1", UUID.randomUUID(), "PAT-1", "Clinic Patient", null, null, date,
                BillStatus.PAID, new BigDecimal("80.00"), DiscountType.NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                new BigDecimal("80.00"), new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("80.00"), BigDecimal.ZERO, null, null,
                OffsetDateTime.parse("2026-05-23T09:00:00Z"), OffsetDateTime.parse("2026-05-23T09:00:00Z"), List.of()
        );
        PaymentRecord billPayment = new PaymentRecord(
                UUID.randomUUID(), tenantId, billId, date, OffsetDateTime.parse("2026-05-23T09:05:00Z"), new BigDecimal("80.00"), PaymentMode.CARD,
                null, null, UUID.randomUUID(), null, null, null, OffsetDateTime.parse("2026-05-23T09:05:00Z")
        );
        RefundRecord billRefund = new RefundRecord(
                UUID.randomUUID(), tenantId, billId, billPayment.id(), new BigDecimal("10.00"), "Return", PaymentMode.CARD, null, UUID.randomUUID(),
                OffsetDateTime.parse("2026-05-23T11:00:00Z"), OffsetDateTime.parse("2026-05-23T11:00:00Z")
        );
        PharmacySalePaymentEntity salePayment = PharmacySalePaymentEntity.create(
                tenantId, UUID.randomUUID(), null, date, OffsetDateTime.parse("2026-05-23T10:05:00Z"), new BigDecimal("50.00"), "UPI", "UPI-1", "RCPT-1", null, UUID.randomUUID()
        );
        PharmacySaleReturnEntity saleReturn = PharmacySaleReturnEntity.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), "RET-1", UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5.00"), true, "Return", "UPI", "REF-1", null, UUID.randomUUID()
        );
        setField(saleReturn, "createdAt", OffsetDateTime.parse("2026-05-23T10:10:00Z"));

        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(bill));
        when(billingService.listPayments(tenantId, billId)).thenReturn(List.of(billPayment));
        when(billingService.listRefunds(tenantId, billId)).thenReturn(List.of(billRefund));
        when(pharmacySalePaymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(salePayment));
        when(pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(saleReturn));

        List<Map<String, Object>> rows = reportingFacade.paymentModes(tenantId, date, date, null, "ALL");

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row ->
                "CLINIC".equals(row.get("source"))
                        && "CARD".equals(row.get("paymentMode"))
                        && Integer.valueOf(1).equals(row.get("count"))
                        && new BigDecimal("80.00").equals(row.get("grossAmount"))
                        && new BigDecimal("10.00").equals(row.get("refundAmount"))
                        && new BigDecimal("70.00").equals(row.get("netAmount"))));
        assertTrue(rows.stream().anyMatch(row ->
                "PHARMACY".equals(row.get("source"))
                        && "UPI".equals(row.get("paymentMode"))
                        && Integer.valueOf(1).equals(row.get("count"))
                        && new BigDecimal("50.00").equals(row.get("grossAmount"))
                        && new BigDecimal("5.00").equals(row.get("refundAmount"))
                        && new BigDecimal("45.00").equals(row.get("netAmount"))));
    }

    @Test
    void medicineSalesUsesBusinessFieldsInsteadOfIds() {
        UUID tenantId = UUID.randomUUID();
        UUID cashierId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID medicineId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 23);

        PharmacySaleEntity sale = PharmacySaleEntity.create(
                tenantId, "PS-1", patientId, null, null, OffsetDateTime.parse("2026-05-23T10:00:00Z"), null,
                new BigDecimal("30.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30.00"), new BigDecimal("30.00"), BigDecimal.ZERO, "PAID", null, cashierId
        );
        PharmacySaleItemEntity item = PharmacySaleItemEntity.create(
                tenantId, sale.getId(), medicineId, UUID.randomUUID(), "BATCH-1", date.plusMonths(6), 2,
                new BigDecimal("15.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30.00")
        );
        PharmacySalePaymentEntity payment = PharmacySalePaymentEntity.create(
                tenantId, sale.getId(), null, date, OffsetDateTime.parse("2026-05-23T10:05:00Z"), new BigDecimal("30.00"), "CASH", null, "RCPT-1", null, cashierId
        );
        MedicineEntity medicine = MedicineEntity.create(tenantId, "Paracetamol 500", "TABLET");
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        patient.update("Asha", "Rao", com.deepthoughtnet.clinic.patient.service.model.PatientGender.FEMALE, null, null, "9999999999", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        AppUserEntity cashier = AppUserEntity.create(tenantId, "sub-1", "cashier@example.com", "Cashier One");

        // align generated IDs with entity relations
        setId(medicine, medicineId);
        setId(patient, patientId);
        setId(cashier, cashierId);

        when(pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(sale));
        when(pharmacySaleItemRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(item));
        when(pharmacySalePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, sale.getId())).thenReturn(List.of(payment));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(appUserRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(cashier));
        when(patientRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(patient));

        List<Map<String, Object>> rows = reportingFacade.medicineSales(tenantId, date, date, null);

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.getFirst();
        assertEquals("PS-1", row.get("saleNumber"));
        assertEquals("Paracetamol 500", row.get("medicine"));
        assertEquals("BATCH-1", row.get("batch"));
        assertEquals("CASH", row.get("paymentMode"));
        assertEquals("Cashier One", row.get("cashier"));
        assertEquals("Asha Rao", row.get("customerPatient"));
        verify(pharmacySaleRepository).findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Test
    void cashierShiftReportUsesTenantScopedShiftRows() {
        UUID tenantId = UUID.randomUUID();
        UUID cashierId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 23);
        PharmacyCashierShiftEntity shift = PharmacyCashierShiftEntity.open(
                tenantId, cashierId, cashierId, new BigDecimal("1000.00"), "opening"
        );
        shift.close(
                cashierId,
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("300.00"),
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("300.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "close"
        );
        AppUserEntity cashier = AppUserEntity.create(tenantId, "sub-1", "cashier@example.com", "Cashier One");
        setId(cashier, cashierId);

        when(pharmacyCashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId)).thenReturn(List.of(shift));
        when(appUserRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(cashier));

        List<Map<String, Object>> rows = reportingFacade.cashierShifts(tenantId, date.minusDays(1), date.plusDays(1));

        assertEquals(1, rows.size());
        assertEquals("Cashier One", rows.getFirst().get("cashier"));
        assertEquals(new BigDecimal("600.00"), rows.getFirst().get("actualTotal"));
    }

    @Test
    void cashCounterLedgerIncludesClinicPharmacyRefundAndReturnRows() {
        UUID tenantId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID cashierId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 23);

        BillRecord bill = new BillRecord(
                billId, tenantId, "INV-1001", UUID.randomUUID(), "PAT-1", "Clinic Patient", null, null, date,
                BillStatus.PAID, new BigDecimal("150.00"), DiscountType.NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                new BigDecimal("150.00"), new BigDecimal("150.00"), BigDecimal.ZERO, new BigDecimal("150.00"), BigDecimal.ZERO, null, null,
                OffsetDateTime.parse("2026-05-23T09:00:00Z"), OffsetDateTime.parse("2026-05-23T09:00:00Z"), List.of()
        );
        PaymentRecord billPayment = new PaymentRecord(
                UUID.randomUUID(), tenantId, billId, date, OffsetDateTime.parse("2026-05-23T09:05:00Z"), new BigDecimal("150.00"), PaymentMode.CASH,
                "CASH-1", null, cashierId, null, "CL-REC-1", null, OffsetDateTime.parse("2026-05-23T09:05:00Z")
        );
        RefundRecord billRefund = new RefundRecord(
                UUID.randomUUID(), tenantId, billId, billPayment.id(), new BigDecimal("25.00"), "Clinic refund", PaymentMode.CASH, null, cashierId,
                OffsetDateTime.parse("2026-05-23T09:15:00Z"), OffsetDateTime.parse("2026-05-23T09:15:00Z")
        );

        PharmacySaleEntity sale = PharmacySaleEntity.create(
                tenantId, "PS-1", null, "Walk In", "9999999999", OffsetDateTime.parse("2026-05-23T10:00:00Z"), null,
                new BigDecimal("75.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("75.00"), new BigDecimal("75.00"), BigDecimal.ZERO, "PAID", null, cashierId
        );
        PharmacySalePaymentEntity salePayment = PharmacySalePaymentEntity.create(
                tenantId, sale.getId(), shiftId, date, OffsetDateTime.parse("2026-05-23T10:05:00Z"), new BigDecimal("75.00"), "UPI", "UPI-1", "POS-REC-1", null, cashierId
        );
        PharmacySaleReturnEntity saleReturn = PharmacySaleReturnEntity.create(
                tenantId, sale.getId(), UUID.randomUUID(), "RET-1", UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"), true, "Damaged", "UPI", "RET-REF-1", null, cashierId
        );
        setField(saleReturn, "createdAt", OffsetDateTime.parse("2026-05-23T10:10:00Z"));

        PharmacyCashierShiftEntity shift = PharmacyCashierShiftEntity.open(
                tenantId, cashierId, cashierId, new BigDecimal("500.00"), "opening"
        );
        setId(shift, shiftId);

        AppUserEntity cashier = AppUserEntity.create(tenantId, "sub-1", "cashier@example.com", "Cashier One");
        setId(cashier, cashierId);

        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(bill));
        when(billingService.listPayments(tenantId, billId)).thenReturn(List.of(billPayment));
        when(billingService.listRefunds(tenantId, billId)).thenReturn(List.of(billRefund));
        when(pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(sale));
        when(pharmacySalePaymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(salePayment));
        when(pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(saleReturn));
        when(pharmacyCashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId)).thenReturn(List.of(shift));
        when(appUserRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(cashier));

        List<Map<String, Object>> rows = reportingFacade.cashCounterLedger(tenantId, date, date, null, "ALL", null);

        assertEquals(4, rows.size());
        assertTrue(rows.stream().anyMatch(row ->
                "Pharmacy POS".equals(row.get("source"))
                        && "PS-1".equals(row.get("businessReference"))
                        && "POS-REC-1".equals(row.get("receiptNumber"))
                        && "Cashier One".equals(row.get("cashier"))
                        && String.valueOf(row.get("shiftReference")).startsWith("Shift ")
                        && new BigDecimal("75.00").equals(row.get("grossAmount"))));
        assertTrue(rows.stream().anyMatch(row ->
                "Clinic Bill".equals(row.get("source"))
                        && "INV-1001".equals(row.get("businessReference"))
                        && "CL-REC-1".equals(row.get("receiptNumber"))
                        && new BigDecimal("150.00").equals(row.get("grossAmount"))));
        assertTrue(rows.stream().anyMatch(row ->
                "Refund".equals(row.get("source"))
                        && "INV-1001".equals(row.get("businessReference"))
                        && "CL-REC-1".equals(row.get("receiptNumber"))
                        && new BigDecimal("25.00").equals(row.get("refundAmount"))
                        && new BigDecimal("-25.00").equals(row.get("netAmount"))));
        assertTrue(rows.stream().anyMatch(row ->
                "Return".equals(row.get("source"))
                        && "RET-1".equals(row.get("businessReference"))
                        && "PS-1".equals(row.get("receiptNumber"))
                        && new BigDecimal("10.00").equals(row.get("refundAmount"))
                        && new BigDecimal("-10.00").equals(row.get("netAmount"))));
    }

    @Test
    void cashCounterSummaryCombinesCollectionsRefundsAndShiftAlerts() {
        UUID tenantId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID cashierId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 23);

        BillRecord bill = new BillRecord(
                billId, tenantId, "INV-1001", UUID.randomUUID(), "PAT-1", "Clinic Patient", null, null, date,
                BillStatus.PAID, new BigDecimal("100.00"), DiscountType.NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), BigDecimal.ZERO, null, null,
                OffsetDateTime.parse("2026-05-23T09:00:00Z"), OffsetDateTime.parse("2026-05-23T09:00:00Z"), List.of()
        );
        PaymentRecord billPayment = new PaymentRecord(
                UUID.randomUUID(), tenantId, billId, date, OffsetDateTime.parse("2026-05-23T09:05:00Z"), new BigDecimal("100.00"), PaymentMode.CASH,
                "CASH-1", null, cashierId, null, "CL-REC-1", null, OffsetDateTime.parse("2026-05-23T09:05:00Z")
        );
        RefundRecord billRefund = new RefundRecord(
                UUID.randomUUID(), tenantId, billId, billPayment.id(), new BigDecimal("20.00"), "Clinic refund", PaymentMode.CASH, null, cashierId,
                OffsetDateTime.parse("2026-05-23T09:15:00Z"), OffsetDateTime.parse("2026-05-23T09:15:00Z")
        );

        PharmacySaleEntity sale = PharmacySaleEntity.create(
                tenantId, "PS-1", null, "Walk In", "9999999999", OffsetDateTime.parse("2026-05-23T10:00:00Z"), null,
                new BigDecimal("80.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("80.00"), new BigDecimal("80.00"), BigDecimal.ZERO, "PAID", null, cashierId
        );
        PharmacySalePaymentEntity salePayment = PharmacySalePaymentEntity.create(
                tenantId, sale.getId(), shiftId, date, OffsetDateTime.parse("2026-05-23T10:05:00Z"), new BigDecimal("80.00"), "CARD", "CARD-1", "POS-REC-1", null, cashierId
        );
        PharmacySaleReturnEntity saleReturn = PharmacySaleReturnEntity.create(
                tenantId, sale.getId(), UUID.randomUUID(), "RET-1", UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5.00"), true, "Damaged", "UPI", "RET-REF-1", null, cashierId
        );
        setField(saleReturn, "createdAt", OffsetDateTime.parse("2026-05-23T10:10:00Z"));

        PharmacyCashierShiftEntity openShift = PharmacyCashierShiftEntity.open(
                tenantId, cashierId, cashierId, new BigDecimal("500.00"), "opening"
        );
        setId(openShift, shiftId);
        setField(openShift, "openedAt", OffsetDateTime.parse("2026-05-23T08:00:00Z"));
        PharmacyCashierShiftEntity closedShift = PharmacyCashierShiftEntity.open(
                tenantId, cashierId, cashierId, new BigDecimal("250.00"), "opening"
        );
        setId(closedShift, UUID.randomUUID());
        setField(closedShift, "openedAt", OffsetDateTime.parse("2026-05-23T07:00:00Z"));
        closedShift.close(
                cashierId,
                new BigDecimal("90.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("-10.00"),
                "short cash"
        );

        AppUserEntity cashier = AppUserEntity.create(tenantId, "sub-1", "cashier@example.com", "Cashier One");
        setId(cashier, cashierId);

        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(bill));
        when(billingService.listPayments(tenantId, billId)).thenReturn(List.of(billPayment));
        when(billingService.listRefunds(tenantId, billId)).thenReturn(List.of(billRefund));
        when(pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(sale));
        when(pharmacySalePaymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(salePayment));
        when(pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(saleReturn));
        when(pharmacyCashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId)).thenReturn(List.of(openShift, closedShift));
        when(appUserRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(cashier));

        Map<String, Object> summary = reportingFacade.cashCounterSummary(tenantId, date, date, null, "ALL", null);

        assertEquals(new BigDecimal("180.00"), summary.get("todayTotalCollected"));
        assertEquals(new BigDecimal("100.00"), summary.get("clinicBillingCollected"));
        assertEquals(new BigDecimal("80.00"), summary.get("pharmacyPosCollected"));
        assertEquals(new BigDecimal("25.00"), summary.get("refundsReturns"));
        assertEquals(new BigDecimal("80.00"), summary.get("netCash"));
        assertEquals(new BigDecimal("-5.00"), summary.get("netUpi"));
        assertEquals(new BigDecimal("80.00"), summary.get("netCard"));
        assertEquals(Long.valueOf(1), summary.get("openCashierShifts"));
        assertEquals(Long.valueOf(1), summary.get("varianceAlerts"));
    }

    @Test
    void financeReportsMatchDeterministicExpectedTotals() {
        UUID tenantId = UUID.randomUUID();
        UUID cashierId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID saleUpiId = UUID.randomUUID();
        UUID saleCardId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 23);

        BillRecord clinicBill = new BillRecord(
                billId, tenantId, "INV-1000", UUID.randomUUID(), "PAT-1000", "Clinic Patient", null, null, date,
                BillStatus.PAID, new BigDecimal("1000.00"), DiscountType.NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("900.00"), BigDecimal.ZERO, null, null,
                OffsetDateTime.parse("2026-05-23T09:00:00Z"), OffsetDateTime.parse("2026-05-23T09:00:00Z"), List.of()
        );
        PaymentRecord clinicPayment = new PaymentRecord(
                UUID.randomUUID(), tenantId, billId, date, OffsetDateTime.parse("2026-05-23T09:05:00Z"), new BigDecimal("1000.00"), PaymentMode.CASH,
                "CASH-1000", null, cashierId, null, "CL-REC-1000", null, OffsetDateTime.parse("2026-05-23T09:05:00Z")
        );
        RefundRecord clinicRefund = new RefundRecord(
                UUID.randomUUID(), tenantId, billId, clinicPayment.id(), new BigDecimal("100.00"), "Clinic refund", PaymentMode.CASH, null, cashierId,
                OffsetDateTime.parse("2026-05-23T09:20:00Z"), OffsetDateTime.parse("2026-05-23T09:20:00Z")
        );

        PharmacySaleEntity upiSale = PharmacySaleEntity.create(
                tenantId, "PS-UPI-1", null, "Walk In", "9999999999", OffsetDateTime.parse("2026-05-23T10:00:00Z"), null,
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO, "PAID", null, cashierId
        );
        setId(upiSale, saleUpiId);
        PharmacySalePaymentEntity upiPayment = PharmacySalePaymentEntity.create(
                tenantId, saleUpiId, shiftId, date, OffsetDateTime.parse("2026-05-23T10:05:00Z"), new BigDecimal("500.00"), "UPI", "UPI-500", "POS-REC-UPI", null, cashierId
        );
        PharmacySaleReturnEntity upiReturn = PharmacySaleReturnEntity.create(
                tenantId, saleUpiId, UUID.randomUUID(), "RET-50", UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), true, "Return", "UPI", "RET-UPI", null, cashierId
        );
        setField(upiReturn, "createdAt", OffsetDateTime.parse("2026-05-23T10:15:00Z"));

        PharmacySaleEntity cardSale = PharmacySaleEntity.create(
                tenantId, "PS-CARD-1", null, "Walk In", "8888888888", OffsetDateTime.parse("2026-05-23T11:00:00Z"), null,
                new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300.00"), new BigDecimal("300.00"), BigDecimal.ZERO, "PAID", null, cashierId
        );
        setId(cardSale, saleCardId);
        PharmacySalePaymentEntity cardPayment = PharmacySalePaymentEntity.create(
                tenantId, saleCardId, shiftId, date, OffsetDateTime.parse("2026-05-23T11:05:00Z"), new BigDecimal("300.00"), "CARD", "CARD-300", "POS-REC-CARD", null, cashierId
        );

        PharmacyCashierShiftEntity shift = PharmacyCashierShiftEntity.open(
                tenantId, cashierId, cashierId, new BigDecimal("1000.00"), "opening"
        );
        setId(shift, shiftId);
        setField(shift, "openedAt", OffsetDateTime.parse("2026-05-23T08:00:00Z"));
        shift.close(
                cashierId,
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("300.00"),
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("300.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "balanced"
        );

        AppUserEntity cashier = AppUserEntity.create(tenantId, "sub-1", "cashier@example.com", "Cashier One");
        setId(cashier, cashierId);

        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(clinicBill));
        when(billingService.listPayments(tenantId, billId)).thenReturn(List.of(clinicPayment));
        when(billingService.listRefunds(tenantId, billId)).thenReturn(List.of(clinicRefund));
        when(pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(cardSale, upiSale));
        when(pharmacySalePaymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(cardPayment, upiPayment));
        when(pharmacySalePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, saleUpiId)).thenReturn(List.of(upiPayment));
        when(pharmacySalePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, saleCardId)).thenReturn(List.of(cardPayment));
        when(pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(upiReturn));
        when(pharmacyCashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId)).thenReturn(List.of(shift));
        when(appUserRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(cashier));

        List<Map<String, Object>> revenueRows = reportingFacade.revenue(tenantId, date, date, null, null, null, "ALL");
        Map<String, Object> totalRevenue = revenueRows.stream().filter(row -> "TOTAL".equals(row.get("source"))).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1800.00"), totalRevenue.get("totalGrossRevenue"));
        assertEquals(new BigDecimal("150.00"), totalRevenue.get("refunds"));
        assertEquals(new BigDecimal("1650.00"), totalRevenue.get("netRevenue"));

        List<Map<String, Object>> dailyRows = reportingFacade.dailySales(tenantId, date, date, null, "ALL");
        Map<String, Object> clinicDaily = dailyRows.stream().filter(row -> "CLINIC".equals(row.get("source"))).findFirst().orElseThrow();
        Map<String, Object> pharmacyDaily = dailyRows.stream().filter(row -> "PHARMACY".equals(row.get("source"))).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1000.00"), clinicDaily.get("grossSales"));
        assertEquals(new BigDecimal("100.00"), clinicDaily.get("refunds"));
        assertEquals(new BigDecimal("900.00"), clinicDaily.get("netSales"));
        assertEquals(new BigDecimal("1000.00"), clinicDaily.get("cash"));
        assertEquals(new BigDecimal("800.00"), pharmacyDaily.get("grossSales"));
        assertEquals(new BigDecimal("50.00"), pharmacyDaily.get("refunds"));
        assertEquals(new BigDecimal("750.00"), pharmacyDaily.get("netSales"));
        assertEquals(new BigDecimal("500.00"), pharmacyDaily.get("upi"));
        assertEquals(new BigDecimal("300.00"), pharmacyDaily.get("card"));

        List<Map<String, Object>> paymentRows = reportingFacade.paymentModes(tenantId, date, date, null, "ALL");
        assertTrue(paymentRows.stream().anyMatch(row -> "CLINIC".equals(row.get("source"))
                && "CASH".equals(row.get("paymentMode"))
                && new BigDecimal("1000.00").equals(row.get("grossAmount"))
                && new BigDecimal("100.00").equals(row.get("refundAmount"))
                && new BigDecimal("900.00").equals(row.get("netAmount"))));
        assertTrue(paymentRows.stream().anyMatch(row -> "PHARMACY".equals(row.get("source"))
                && "UPI".equals(row.get("paymentMode"))
                && new BigDecimal("500.00").equals(row.get("grossAmount"))
                && new BigDecimal("50.00").equals(row.get("refundAmount"))
                && new BigDecimal("450.00").equals(row.get("netAmount"))));
        assertTrue(paymentRows.stream().anyMatch(row -> "PHARMACY".equals(row.get("source"))
                && "CARD".equals(row.get("paymentMode"))
                && new BigDecimal("300.00").equals(row.get("grossAmount"))
                && new BigDecimal("0.00").equals(row.get("refundAmount"))
                && new BigDecimal("300.00").equals(row.get("netAmount"))));

        List<Map<String, Object>> shiftRows = reportingFacade.cashierShifts(tenantId, date, date);
        assertEquals(1, shiftRows.size());
        assertEquals(new BigDecimal("1000.00"), shiftRows.getFirst().get("expectedCash"));
        assertEquals(new BigDecimal("500.00"), shiftRows.getFirst().get("expectedUpi"));
        assertEquals(new BigDecimal("300.00"), shiftRows.getFirst().get("expectedCard"));
        assertEquals(new BigDecimal("1800.00"), shiftRows.getFirst().get("actualTotal"));
        assertEquals(new BigDecimal("0.00"), shiftRows.getFirst().get("variance"));
    }

    @Test
    void reportDateFiltersUseActualRefundDateAndInclusivePaymentDates() {
        UUID tenantId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 5, 23);
        LocalDate to = LocalDate.of(2026, 5, 24);

        BillRecord bill = new BillRecord(
                billId, tenantId, "INV-BOUNDARY", UUID.randomUUID(), "PAT-1", "Boundary Patient", null, null, from,
                BillStatus.PAID, new BigDecimal("200.00"), DiscountType.NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO,
                new BigDecimal("200.00"), new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("200.00"), BigDecimal.ZERO, null, null,
                OffsetDateTime.parse("2026-05-23T08:00:00Z"), OffsetDateTime.parse("2026-05-23T08:00:00Z"), List.of()
        );
        PaymentRecord startPayment = new PaymentRecord(
                UUID.randomUUID(), tenantId, billId, from, OffsetDateTime.parse("2026-05-23T00:00:00Z"), new BigDecimal("120.00"), PaymentMode.CASH,
                null, null, UUID.randomUUID(), null, "REC-START", null, OffsetDateTime.parse("2026-05-23T00:00:00Z")
        );
        PaymentRecord endPayment = new PaymentRecord(
                UUID.randomUUID(), tenantId, billId, to, OffsetDateTime.parse("2026-05-24T23:59:59Z"), new BigDecimal("80.00"), PaymentMode.CASH,
                null, null, UUID.randomUUID(), null, "REC-END", null, OffsetDateTime.parse("2026-05-24T23:59:59Z")
        );
        RefundRecord outsideRefund = new RefundRecord(
                UUID.randomUUID(), tenantId, billId, endPayment.id(), new BigDecimal("25.00"), "Late refund", PaymentMode.CASH, null, UUID.randomUUID(),
                OffsetDateTime.parse("2026-05-25T00:00:00Z"), OffsetDateTime.parse("2026-05-25T00:00:00Z")
        );

        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(bill));
        when(billingService.listPayments(tenantId, billId)).thenReturn(List.of(startPayment, endPayment));
        when(billingService.listRefunds(tenantId, billId)).thenReturn(List.of(outsideRefund));

        List<Map<String, Object>> revenueRows = reportingFacade.revenue(tenantId, from, to, null, null, "CASH", "CLINIC");
        Map<String, Object> clinicRevenue = revenueRows.getFirst();
        assertEquals(new BigDecimal("200.00"), clinicRevenue.get("totalRevenue"));
        assertEquals(new BigDecimal("0.00"), clinicRevenue.get("refunds"));
        assertEquals(new BigDecimal("200.00"), clinicRevenue.get("netRevenue"));

        List<Map<String, Object>> dailyRows = reportingFacade.dailySales(tenantId, from, to, "CASH", "CLINIC");
        assertEquals(1, dailyRows.size());
        assertEquals(new BigDecimal("200.00"), dailyRows.getFirst().get("cash"));
        assertEquals(new BigDecimal("0.00"), dailyRows.getFirst().get("refunds"));

        List<Map<String, Object>> paymentRows = reportingFacade.paymentModes(tenantId, from, to, "CASH", "CLINIC");
        assertEquals(1, paymentRows.size());
        assertEquals(new BigDecimal("200.00"), paymentRows.getFirst().get("grossAmount"));
        assertEquals(new BigDecimal("0.00"), paymentRows.getFirst().get("refundAmount"));
        assertFalse(paymentRows.stream().anyMatch(row -> new BigDecimal("25.00").equals(row.get("refundAmount"))));
    }

    private static void setId(Object target, UUID id) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
