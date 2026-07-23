package com.deepthoughtnet.clinic.billing.service;

import com.deepthoughtnet.clinic.billing.db.BillEntity;
import com.deepthoughtnet.clinic.billing.db.BillRepository;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.PaymentReminderState;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Repository-backed payment reminder state reader.
 * <p>
 * This adapter reads bill persistence directly and deliberately avoids the orchestration service so the
 * notification domain can validate freshness without depending on the full billing workflow.
 */
@Service
public class PaymentReminderStateReaderImpl implements PaymentReminderStateReader {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BillRepository billRepository;

    public PaymentReminderStateReaderImpl(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    @Override
    public PaymentReminderState findCurrentState(UUID tenantId, UUID billId) {
        BillEntity bill = billRepository.findByTenantIdAndId(tenantId, billId).orElse(null);
        if (bill == null) {
            return PaymentReminderState.missing();
        }
        BigDecimal outstandingAmount = bill.getDueAmount() == null ? ZERO : bill.getDueAmount();
        boolean reminderEligible = bill.getStatus() != null
                && !isClosed(bill.getStatus())
                && outstandingAmount.compareTo(ZERO) > 0;
        return new PaymentReminderState(
                true,
                reminderEligible,
                outstandingAmount,
                bill.getStatus() == null ? null : bill.getStatus().name(),
                bill.getUpdatedAt(),
                bill.getVersion()
        );
    }

    private boolean isClosed(BillStatus status) {
        return status == BillStatus.PAID
                || status == BillStatus.CANCELLED
                || status == BillStatus.CANCELLED_REFUNDED
                || status == BillStatus.REFUNDED
                || status == BillStatus.PARTIALLY_REFUNDED
                || status == BillStatus.REFUND_PENDING;
    }
}
