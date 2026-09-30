package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.deepthoughtnet.clinic.inventory.db.RefillRequestEntity;
import com.deepthoughtnet.clinic.inventory.db.RefillRequestRepository;
import com.deepthoughtnet.clinic.inventory.service.RefillRequestService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefillRequestServiceTest {
    @Test
    void existingRefillCycleIsReturnedInsteadOfDuplicated() {
        RefillRequestRepository repository = mock(RefillRequestRepository.class);
        RefillRequestEntity existing = RefillRequestEntity.requested(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "cycle-1", "Medicine", LocalDate.now(), "ENGAGE_REFILL_REMINDER");
        when(repository.findByTenantIdAndPatientIdAndPrescriptionIdAndRefillCycle(any(), any(), any(), eq("cycle-1")))
                .thenReturn(Optional.of(existing));
        RefillRequestService service = new RefillRequestService(repository);

        var result = service.request(existing.getTenantId(), existing.getPatientId(), existing.getPrescriptionId(), "cycle-1", "Medicine", existing.getDueDate(), "ENGAGE_REFILL_REMINDER");

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(repository, never()).save(any());
    }
}
