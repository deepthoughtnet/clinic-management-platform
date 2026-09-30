package com.deepthoughtnet.clinic.inventory.service;

import com.deepthoughtnet.clinic.inventory.db.RefillRequestEntity;
import com.deepthoughtnet.clinic.inventory.db.RefillRequestRepository;
import com.deepthoughtnet.clinic.inventory.service.model.RefillRequestRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class RefillRequestService {
    private final RefillRequestRepository repository;

    public RefillRequestService(RefillRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RefillRequestRecord> list(UUID tenantId, UUID patientId) {
        return repository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId).stream().map(this::record).toList();
    }

    @Transactional
    public RefillRequestRecord request(UUID tenantId, UUID patientId, UUID prescriptionId,
                                       String refillCycle, String medicineSummary, LocalDate dueDate,
                                       String source) {
        if (tenantId == null || patientId == null || prescriptionId == null || refillCycle == null || refillCycle.isBlank()) {
            throw new IllegalArgumentException("Refill request context is incomplete");
        }
        Optional<RefillRequestEntity> existing = repository.findByTenantIdAndPatientIdAndPrescriptionIdAndRefillCycle(tenantId, patientId, prescriptionId, refillCycle);
        if (existing.isPresent()) {
            return record(existing.get());
        }
        try {
            return record(repository.save(RefillRequestEntity.requested(
                    tenantId, patientId, prescriptionId, refillCycle, medicineSummary, dueDate, source)));
        } catch (DataIntegrityViolationException ex) {
            return repository.findByTenantIdAndPatientIdAndPrescriptionIdAndRefillCycle(tenantId, patientId, prescriptionId, refillCycle)
                .map(this::record)
                .orElseThrow(() -> ex);
        }
    }

    private RefillRequestRecord record(RefillRequestEntity e) {
        return new RefillRequestRecord(e.getId(), e.getTenantId(), e.getPatientId(), e.getPrescriptionId(), e.getRefillCycle(),
                e.getMedicineSummary(), e.getDueDate(), e.getStatus(), e.getSource(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
