package com.deepthoughtnet.clinic.billing.db;

import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import java.util.List;
import java.util.UUID;

public interface BillRepositoryCustom {
    List<BillEntity> search(UUID tenantId, BillingSearchCriteria criteria);
}
