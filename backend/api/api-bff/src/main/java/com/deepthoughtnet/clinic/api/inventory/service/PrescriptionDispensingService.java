package com.deepthoughtnet.clinic.api.inventory.service;

import com.deepthoughtnet.clinic.api.inventory.db.PrescriptionDispensationEntity;
import com.deepthoughtnet.clinic.api.inventory.db.PrescriptionDispensationRepository;
import com.deepthoughtnet.clinic.api.inventory.db.PrescriptionDispenseItemEntity;
import com.deepthoughtnet.clinic.api.inventory.db.PrescriptionDispenseItemRepository;
import com.deepthoughtnet.clinic.api.inventory.dto.DispenseRequest;
import com.deepthoughtnet.clinic.api.inventory.dto.PrescriptionDispenseLineResponse;
import com.deepthoughtnet.clinic.api.inventory.dto.PrescriptionDispenseResponse;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillUpsertCommand;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionDispensingService {
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionMedicineRepository prescriptionMedicineRepository;
    private final PrescriptionDispensationRepository dispensationRepository;
    private final PrescriptionDispenseItemRepository dispenseItemRepository;
    private final MedicineRepository medicineRepository;
    private final StockRepository stockRepository;
    private final InventoryService inventoryService;
    private final BillingService billingService;

    public PrescriptionDispensingService(
            PrescriptionRepository prescriptionRepository,
            PrescriptionMedicineRepository prescriptionMedicineRepository,
            PrescriptionDispensationRepository dispensationRepository,
            PrescriptionDispenseItemRepository dispenseItemRepository,
            MedicineRepository medicineRepository,
            StockRepository stockRepository,
            InventoryService inventoryService,
            BillingService billingService
    ) {
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionMedicineRepository = prescriptionMedicineRepository;
        this.dispensationRepository = dispensationRepository;
        this.dispenseItemRepository = dispenseItemRepository;
        this.medicineRepository = medicineRepository;
        this.stockRepository = stockRepository;
        this.inventoryService = inventoryService;
        this.billingService = billingService;
    }

    @Transactional(readOnly = true)
    public List<PrescriptionDispenseResponse> queue(UUID tenantId) {
        return prescriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(p -> p.getStatus() == PrescriptionStatus.FINALIZED || p.getStatus() == PrescriptionStatus.PRINTED || p.getStatus() == PrescriptionStatus.SENT)
                .limit(100)
                .map(p -> view(tenantId, p.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PrescriptionDispenseResponse view(UUID tenantId, UUID prescriptionId) {
        PrescriptionEntity prescription = prescriptionRepository.findByTenantIdAndId(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        PrescriptionDispensationEntity disp = dispensationRepository.findByTenantIdAndPrescriptionId(tenantId, prescriptionId).orElse(null);
        List<PrescriptionMedicineEntity> lines = prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescriptionId);
        List<PrescriptionDispenseItemEntity> items = dispenseItemRepository.findByTenantIdAndPrescriptionIdOrderByPrescribedSortOrderAsc(tenantId, prescriptionId);

        Map<String, PrescriptionDispenseItemEntity> byName = items.stream().collect(Collectors.toMap(i -> normalize(i.getPrescribedMedicineName()), Function.identity(), (a, b) -> a));
        Map<String, MedicineEntity> medicineByName = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream().collect(Collectors.toMap(m -> normalize(m.getMedicineName()), Function.identity(), (a, b) -> a));

        List<PrescriptionDispenseLineResponse> responseLines = new ArrayList<>();
        for (PrescriptionMedicineEntity line : lines) {
            String key = normalize(line.getMedicineName());
            MedicineEntity medicine = medicineByName.get(key);
            PrescriptionDispenseItemEntity item = byName.get(key);
            int available = medicine == null ? 0 : stockRepository.findByTenantIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(tenantId, medicine.getId(), 0)
                    .stream().mapToInt(StockEntity::getQuantityOnHand).sum();
            responseLines.add(new PrescriptionDispenseLineResponse(
                    item == null ? null : item.getId(),
                    line.getMedicineName(),
                    medicine == null ? null : medicine.getId(),
                    item == null ? 0 : item.getPrescribedQuantity(),
                    item == null ? 0 : item.getDispensedQuantity(),
                    item == null ? "NOT_DISPENSED" : item.getStatus(),
                    available,
                    item == null ? null : item.getLastBatchId()
            ));
        }

        return new PrescriptionDispenseResponse(
                prescription.getId(),
                prescription.getPrescriptionNumber(),
                prescription.getPatientId(),
                null,
                disp == null ? "NOT_BILLED" : disp.getBillingStatus(),
                disp == null ? null : disp.getBilledBillId(),
                responseLines
        );
    }

    @Transactional
    public PrescriptionDispenseResponse dispense(UUID tenantId, UUID prescriptionId, DispenseRequest request, UUID actorAppUserId, boolean canOverrideBatch) {
        PrescriptionEntity prescription = prescriptionRepository.findByTenantIdAndId(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        if (!(prescription.getStatus() == PrescriptionStatus.FINALIZED || prescription.getStatus() == PrescriptionStatus.PRINTED || prescription.getStatus() == PrescriptionStatus.SENT)) {
            throw new IllegalArgumentException("Only finalized prescriptions can be dispensed");
        }

        MedicineEntity medicine = resolveMedicine(tenantId, request);
        PrescriptionDispensationEntity disp = dispensationRepository.findByTenantIdAndPrescriptionId(tenantId, prescriptionId)
                .orElseGet(() -> dispensationRepository.save(PrescriptionDispensationEntity.create(tenantId, prescriptionId, prescription.getPatientId())));

        List<PrescriptionMedicineEntity> prescriptionLines = prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescriptionId);
        PrescriptionMedicineEntity matched = prescriptionLines.stream().filter(l -> normalize(l.getMedicineName()).equals(normalize(request.prescribedMedicineName()))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prescribed medicine line not found"));

        PrescriptionDispenseItemEntity item = dispenseItemRepository.findByTenantIdAndPrescriptionIdOrderByPrescribedSortOrderAsc(tenantId, prescriptionId).stream()
                .filter(i -> normalize(i.getPrescribedMedicineName()).equals(normalize(request.prescribedMedicineName())))
                .findFirst()
                .orElseGet(() -> dispenseItemRepository.save(PrescriptionDispenseItemEntity.create(tenantId, disp.getId(), prescriptionId, matched.getId(), medicine.getId(), matched.getMedicineName(), matched.getSortOrder(), 0)));

        int qtyToDispense = request.quantity();
        if (qtyToDispense <= 0) throw new IllegalArgumentException("quantity must be positive");

        List<StockEntity> fefo = stockRepository.findByTenantIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(tenantId, medicine.getId(), 0);
        if (fefo.isEmpty()) throw new IllegalArgumentException("Medicine is out of stock");

        if (request.batchId() != null) {
            if (!(canOverrideBatch && request.allowBatchOverride())) {
                throw new IllegalArgumentException("Manual batch override is not allowed for this role");
            }
            fefo = fefo.stream().filter(b -> b.getId().equals(request.batchId())).toList();
            if (fefo.isEmpty()) throw new IllegalArgumentException("Selected batch unavailable");
        }

        int remaining = qtyToDispense;
        UUID lastBatch = null;
        for (StockEntity batch : fefo) {
            if (remaining <= 0) break;
            int use = Math.min(remaining, batch.getQuantityOnHand());
            if (use <= 0) continue;
            batch.setQuantityOnHand(batch.getQuantityOnHand() - use);
            stockRepository.save(batch);
            inventoryService.createTransaction(tenantId, new InventoryTransactionCommand(medicine.getId(), batch.getId(), InventoryTransactionType.DISPENSED, use, "Prescription dispensing", "PRESCRIPTION", prescriptionId, actorAppUserId, "Dispensed from prescription " + prescription.getPrescriptionNumber()), actorAppUserId);
            remaining -= use;
            lastBatch = batch.getId();
        }
        int dispensed = qtyToDispense - remaining;
        if (dispensed <= 0) throw new IllegalArgumentException("No quantity could be dispensed");

        item.addDispense(dispensed, lastBatch);
        dispenseItemRepository.save(item);
        return view(tenantId, prescriptionId);
    }

    @Transactional
    public BillRecord createMedicineBill(UUID tenantId, UUID prescriptionId, UUID actorAppUserId) {
        PrescriptionEntity prescription = prescriptionRepository.findByTenantIdAndId(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        PrescriptionDispensationEntity disp = dispensationRepository.findByTenantIdAndPrescriptionId(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("No dispensation found"));
        if (!"NOT_BILLED".equalsIgnoreCase(disp.getBillingStatus())) {
            throw new IllegalArgumentException("Dispensation already billed");
        }

        List<PrescriptionDispenseItemEntity> items = dispenseItemRepository.findByTenantIdAndPrescriptionIdOrderByPrescribedSortOrderAsc(tenantId, prescriptionId);
        if (items.stream().noneMatch(i -> i.getDispensedQuantity() > 0)) {
            throw new IllegalArgumentException("No dispensed items available for billing");
        }

        List<BillLineCommand> lines = items.stream()
                .filter(i -> i.getDispensedQuantity() > 0)
                .map(i -> {
                    MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, i.getMedicineId()).orElse(null);
                    BigDecimal unit = medicine == null || medicine.getDefaultPrice() == null ? BigDecimal.ZERO : medicine.getDefaultPrice();
                    return new BillLineCommand(BillItemType.MEDICINE, i.getPrescribedMedicineName(), i.getDispensedQuantity(), unit, i.getMedicineId(), i.getPrescribedSortOrder(), BigDecimal.ZERO, null, i.getId());
                })
                .toList();

        BillRecord bill = billingService.createDraft(tenantId, new BillUpsertCommand(
                prescription.getPatientId(),
                prescription.getConsultationId(),
                prescription.getAppointmentId(),
                LocalDate.now(),
                DiscountType.NONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                BigDecimal.ZERO,
                "Medicine bill for prescription " + prescription.getPrescriptionNumber(),
                lines
        ), actorAppUserId);

        disp.markBilled(bill.id(), bill.status().name().contains("PAID") ? "PAID" : "BILLED");
        dispensationRepository.save(disp);
        return bill;
    }

    private MedicineEntity resolveMedicine(UUID tenantId, DispenseRequest request) {
        if (request.medicineId() != null) {
            return medicineRepository.findByTenantIdAndId(tenantId, request.medicineId())
                    .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        }
        return medicineRepository.findByTenantIdAndMedicineNameIgnoreCase(tenantId, request.prescribedMedicineName())
                .orElseThrow(() -> new IllegalArgumentException("Mapped medicine not found in master"));
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }
}
