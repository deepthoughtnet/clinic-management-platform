package com.deepthoughtnet.clinic.billing.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = BillRepositorySearchTest.TestApplication.class)
class BillRepositorySearchTest {
    @Autowired
    private BillRepository billRepository;

    @Autowired
    private EntityManager entityManager;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {BillEntity.class, PaymentEntity.class})
    @EnableJpaRepositories(basePackageClasses = BillRepository.class)
    @Import(BillRepositoryImpl.class)
    static class TestApplication {
    }

    @Test
    void searchWithOnlyTenantIdReturnsTenantBillsSortedByDate() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        BillEntity older = persistBill(tenantId, "BILL-001", LocalDate.of(2026, 5, 1), BillStatus.ISSUED);
        BillEntity newer = persistBill(tenantId, "BILL-002", LocalDate.of(2026, 5, 3), BillStatus.PAID);
        persistBill(otherTenantId, "BILL-OTHER", LocalDate.of(2026, 5, 4), BillStatus.ISSUED);

        List<BillEntity> bills = billRepository.search(tenantId, new BillingSearchCriteria(null, null, null, null, null, null));

        assertThat(bills).extracting(BillEntity::getBillNumber).containsExactly(newer.getBillNumber(), older.getBillNumber());
    }

    @Test
    void searchWithNullOptionalFiltersReturnsTenantBills() {
        UUID tenantId = UUID.randomUUID();
        persistBill(tenantId, "BILL-001", LocalDate.of(2026, 4, 1), BillStatus.ISSUED);
        persistBill(tenantId, "BILL-002", LocalDate.of(2026, 4, 2), BillStatus.CANCELLED);

        List<BillEntity> bills = billRepository.search(tenantId, new BillingSearchCriteria(null, null, null, null, null, null));

        assertThat(bills).hasSize(2);
    }

    @Test
    void searchWithPaymentModeReturnsMatchingBillsOnly() {
        UUID tenantId = UUID.randomUUID();
        BillEntity cashBill = persistBill(tenantId, "BILL-CASH", LocalDate.of(2026, 3, 1), BillStatus.PAID);
        BillEntity upiBill = persistBill(tenantId, "BILL-UPI", LocalDate.of(2026, 3, 2), BillStatus.PAID);
        persistPayment(tenantId, cashBill.getId(), PaymentMode.CASH);
        persistPayment(tenantId, upiBill.getId(), PaymentMode.UPI);

        List<BillEntity> bills = billRepository.search(tenantId, new BillingSearchCriteria(null, null, null, null, PaymentMode.UPI, null));

        assertThat(bills).extracting(BillEntity::getBillNumber).containsExactly(upiBill.getBillNumber());
    }

    @Test
    void searchWithBillDateBoundsHandlesNullAndMatchesRange() {
        UUID tenantId = UUID.randomUUID();
        persistBill(tenantId, "BILL-001", LocalDate.of(2026, 2, 1), BillStatus.ISSUED);
        BillEntity inRange = persistBill(tenantId, "BILL-002", LocalDate.of(2026, 2, 10), BillStatus.ISSUED);
        persistBill(tenantId, "BILL-003", LocalDate.of(2026, 2, 20), BillStatus.ISSUED);

        List<BillEntity> bills = billRepository.search(tenantId, new BillingSearchCriteria(null, null, LocalDate.of(2026, 2, 5), LocalDate.of(2026, 2, 15), null, null));

        assertThat(bills).extracting(BillEntity::getBillNumber).containsExactly(inRange.getBillNumber());
    }

    private BillEntity persistBill(UUID tenantId, String billNumber, LocalDate billDate, BillStatus status) {
        BillEntity bill = BillEntity.create(tenantId, billNumber, UUID.randomUUID(), null, null, billDate);
        bill.markStatus(status);
        entityManager.persist(bill);
        entityManager.flush();
        return bill;
    }

    private void persistPayment(UUID tenantId, UUID billId, PaymentMode mode) {
        entityManager.persist(PaymentEntity.create(tenantId, billId, LocalDate.now(), null, BigDecimal.TEN, mode, null, null, null));
        entityManager.flush();
    }
}
