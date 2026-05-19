package com.deepthoughtnet.clinic.billing.db;

import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class BillRepositoryImpl implements BillRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<BillEntity> search(UUID tenantId, BillingSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BillEntity> cq = cb.createQuery(BillEntity.class);
        Root<BillEntity> bill = cq.from(BillEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(bill.get("tenantId"), tenantId));

        BillingSearchCriteria safe = criteria == null ? new BillingSearchCriteria(null, null, null, null, null, null) : criteria;
        if (safe.patientId() != null) {
            predicates.add(cb.equal(bill.get("patientId"), safe.patientId()));
        }
        if (safe.appointmentId() != null) {
            predicates.add(cb.equal(bill.get("appointmentId"), safe.appointmentId()));
        }
        if (safe.status() != null) {
            predicates.add(cb.equal(bill.get("status"), safe.status()));
        }
        LocalDate fromDate = safe.fromDate();
        if (fromDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(bill.get("billDate"), fromDate));
        }
        LocalDate toDate = safe.toDate();
        if (toDate != null) {
            predicates.add(cb.lessThanOrEqualTo(bill.get("billDate"), toDate));
        }
        PaymentMode paymentMode = safe.paymentMode();
        if (paymentMode != null) {
            Subquery<UUID> paymentExists = cq.subquery(UUID.class);
            Root<PaymentEntity> payment = paymentExists.from(PaymentEntity.class);
            paymentExists.select(payment.get("id")).where(
                    cb.equal(payment.get("tenantId"), tenantId),
                    cb.equal(payment.get("billId"), bill.get("id")),
                    cb.equal(payment.get("paymentMode"), paymentMode)
            );
            predicates.add(cb.exists(paymentExists));
        }

        cq.select(bill)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.desc(bill.get("billDate")), cb.desc(bill.get("createdAt")));
        return entityManager.createQuery(cq).getResultList();
    }
}
