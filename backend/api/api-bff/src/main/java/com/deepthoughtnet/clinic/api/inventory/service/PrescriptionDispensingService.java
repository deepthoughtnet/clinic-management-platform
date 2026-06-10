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
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PrescriptionDispensingService {
    private static final String ACTION_FULL = "FULL";
    private static final String ACTION_PARTIAL = "PARTIAL";
    private static final String ACTION_CANCEL = "CANCEL";
    private static final int EXPIRY_WARNING_DAYS = 30;

    private final PrescriptionService prescriptionService;
    private final PrescriptionMedicineRepository prescriptionMedicineRepository;
    private final PrescriptionDispensationRepository dispensationRepository;
    private final PrescriptionDispenseItemRepository dispenseItemRepository;
    private final MedicineRepository medicineRepository;
    private final InventoryLocationRepository locationRepository;
    private final StockRepository stockRepository;
    private final InventoryService inventoryService;
    private final BillingService billingService;

    public PrescriptionDispensingService(
            PrescriptionService prescriptionService,
            PrescriptionMedicineRepository prescriptionMedicineRepository,
            PrescriptionDispensationRepository dispensationRepository,
            PrescriptionDispenseItemRepository dispenseItemRepository,
            MedicineRepository medicineRepository,
            InventoryLocationRepository locationRepository,
            StockRepository stockRepository,
            InventoryService inventoryService,
            BillingService billingService
    ) {
        this.prescriptionService = prescriptionService;
        this.prescriptionMedicineRepository = prescriptionMedicineRepository;
        this.dispensationRepository = dispensationRepository;
        this.dispenseItemRepository = dispenseItemRepository;
        this.medicineRepository = medicineRepository;
        this.locationRepository = locationRepository;
        this.stockRepository = stockRepository;
        this.inventoryService = inventoryService;
        this.billingService = billingService;
    }

    @Transactional(readOnly = true)
    public List<PrescriptionDispenseResponse> queue(UUID tenantId) {
        return prescriptionService.list(tenantId).stream()
                .filter(p -> p.status() == PrescriptionStatus.FINALIZED || p.status() == PrescriptionStatus.PRINTED || p.status() == PrescriptionStatus.SENT)
                .sorted(Comparator.comparing(PrescriptionRecord::finalizedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(p -> view(tenantId, p.id()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PrescriptionDispenseResponse view(UUID tenantId, UUID prescriptionId) {
        PrescriptionRecord prescription = prescriptionService.findById(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        PrescriptionDispensationEntity disp = dispensationRepository.findByTenantIdAndPrescriptionId(tenantId, prescriptionId).orElse(null);
        List<PrescriptionMedicineEntity> lines = prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescriptionId);
        List<PrescriptionDispenseItemEntity> items = dispenseItemRepository.findByTenantIdAndPrescriptionIdOrderByPrescribedSortOrderAsc(tenantId, prescriptionId);

        Map<String, PrescriptionDispenseItemEntity> byName = items.stream().collect(Collectors.toMap(i -> normalize(i.getPrescribedMedicineName()), Function.identity(), (a, b) -> a));
        Map<String, MedicineEntity> medicineByName = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .collect(Collectors.toMap(m -> normalize(m.getMedicineName()), Function.identity(), (a, b) -> a));

        List<PrescriptionDispenseLineResponse> responseLines = new ArrayList<>();
        for (PrescriptionMedicineEntity line : lines) {
            String key = normalize(line.getMedicineName());
            MedicineEntity medicine = medicineByName.get(key);
            PrescriptionDispenseItemEntity item = byName.get(key);
            StockAvailability availability = stockAvailability(tenantId, medicine);
            int pendingQuantity = item == null ? 1 : item.getPendingQuantity();
            String lineAvailabilityStatus = availability.availableQuantity() <= 0
                    ? "OUT_OF_STOCK"
                    : pendingQuantity > availability.availableQuantity()
                    ? "PARTIAL_AVAILABLE"
                    : availability.status();
            responseLines.add(new PrescriptionDispenseLineResponse(
                    item == null ? null : item.getId(),
                    line.getMedicineName(),
                    medicine == null ? null : medicine.getId(),
                    item == null ? 1 : Math.max(1, item.getPrescribedQuantity()),
                    item == null ? 0 : item.getDispensedQuantity(),
                    pendingQuantity,
                    item == null ? "NOT_DISPENSED" : item.getStatus(),
                    availability.availableQuantity(),
                    lineAvailabilityStatus,
                    availability.expiryStatus(),
                    availability.nearestExpiryDate(),
                    item == null ? null : item.getLastBatchId()
            ));
        }

        return new PrescriptionDispenseResponse(
                prescription.id(),
                prescription.prescriptionNumber(),
                prescription.patientId(),
                prescription.patientName(),
                prescription.doctorName(),
                prescription.finalizedAt() == null ? prescription.createdAt() : prescription.finalizedAt(),
                disp == null ? "NOT_BILLED" : disp.getBillingStatus(),
                disp == null ? null : disp.getBilledBillId(),
                responseLines
        );
    }

    @Transactional
    public PrescriptionDispenseResponse dispense(UUID tenantId, UUID prescriptionId, DispenseRequest request, UUID actorAppUserId, boolean canOverrideBatch) {
        PrescriptionRecord prescription = prescriptionService.findById(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        if (!(prescription.status() == PrescriptionStatus.FINALIZED || prescription.status() == PrescriptionStatus.PRINTED || prescription.status() == PrescriptionStatus.SENT)) {
            throw new IllegalArgumentException("Only finalized prescriptions can be dispensed");
        }

        MedicineEntity medicine = resolveMedicine(tenantId, request);
        PrescriptionDispensationEntity disp = dispensationRepository.findByTenantIdAndPrescriptionId(tenantId, prescriptionId)
                .orElseGet(() -> dispensationRepository.save(PrescriptionDispensationEntity.create(tenantId, prescriptionId, prescription.patientId())));

        List<PrescriptionMedicineEntity> prescriptionLines = prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescriptionId);
        PrescriptionMedicineEntity matched = prescriptionLines.stream()
                .filter(l -> normalize(l.getMedicineName()).equals(normalize(request.prescribedMedicineName())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prescribed medicine line not found"));

        PrescriptionDispenseItemEntity item = dispenseItemRepository.findByTenantIdAndPrescriptionIdOrderByPrescribedSortOrderAsc(tenantId, prescriptionId).stream()
                .filter(i -> normalize(i.getPrescribedMedicineName()).equals(normalize(request.prescribedMedicineName())))
                .findFirst()
                .orElse(null);

        String action = normalizeAction(request.action());
        if (item != null && isClosedStatus(item.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This medicine line has already been closed.");
        }

        if (ACTION_CANCEL.equals(action)) {
            PrescriptionDispenseItemEntity target = item == null
                    ? dispenseItemRepository.save(PrescriptionDispenseItemEntity.create(
                            tenantId,
                            disp.getId(),
                            prescriptionId,
                            matched.getId(),
                            medicine.getId(),
                            matched.getMedicineName(),
                            matched.getSortOrder(),
                            Math.max(1, request.quantity() == null ? 1 : request.quantity())
                    ))
                    : item;
            target.markUnavailable();
            dispenseItemRepository.save(target);
            return view(tenantId, prescriptionId);
        }

        int requestedQuantity = request.quantity() == null ? 0 : request.quantity();
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        int prescribedQuantity = item == null ? requestedQuantity : Math.max(1, item.getPrescribedQuantity());
        int remainingQuantity = item == null ? prescribedQuantity : Math.max(0, item.getPendingQuantity());
        if (remainingQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This medicine line has already been fully dispensed.");
        }

        List<StockEntity> activeStockRows = allStockBatches(tenantId, medicine.getId());
        List<StockEntity> fefo = activeStockRows.stream()
                .filter(stock -> stock.getQuantityOnHand() > 0)
                .filter(stock -> !isExpired(stock))
                .toList();
        int availableQuantity = fefo.stream().mapToInt(StockEntity::getQuantityOnHand).sum();
        if (request.batchId() != null) {
            if (!(canOverrideBatch && request.allowBatchOverride())) {
                throw new IllegalArgumentException("Manual batch override is not allowed for this role");
            }
            fefo = fefo.stream().filter(b -> b.getId().equals(request.batchId())).toList();
            if (fefo.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected batch is unavailable or expired.");
            }
            availableQuantity = fefo.stream().mapToInt(StockEntity::getQuantityOnHand).sum();
        }

        if (availableQuantity <= 0) {
            if (activeStockRows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No inventory batch exists for this medicine. The prescription is still valid; add stock before dispensing.");
            }
            boolean hasExpired = activeStockRows.stream().anyMatch(this::isExpired);
            if (hasExpired) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only expired stock exists for this medicine. The prescription is still valid; replenish with a non-expired batch before dispensing.");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This medicine has no quantity on hand for dispensing. The prescription is still valid; replenish stock before dispensing.");
        }

        int targetQuantity;
        if (ACTION_FULL.equals(action)) {
            if (availableQuantity < remainingQuantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient non-expired stock for full dispense. Use partial dispense or mark unavailable.");
            }
            targetQuantity = remainingQuantity;
        } else {
            targetQuantity = Math.min(requestedQuantity, remainingQuantity);
            if (targetQuantity <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No quantity can be dispensed for this line.");
            }
            if (availableQuantity < targetQuantity) {
                targetQuantity = availableQuantity;
            }
        }

        PrescriptionDispenseItemEntity target = item == null
                ? dispenseItemRepository.save(PrescriptionDispenseItemEntity.create(
                        tenantId,
                        disp.getId(),
                        prescriptionId,
                        matched.getId(),
                        medicine.getId(),
                        matched.getMedicineName(),
                        matched.getSortOrder(),
                        prescribedQuantity
                ))
                : item;

        int remaining = targetQuantity;
        UUID lastBatch = null;
        for (StockEntity batch : fefo) {
            if (remaining <= 0) {
                break;
            }
            if (isExpired(batch)) {
                continue;
            }
            int use = Math.min(remaining, batch.getQuantityOnHand());
            if (use <= 0) {
                continue;
            }
            inventoryService.createTransaction(
                    tenantId,
                    new InventoryTransactionCommand(
                            medicine.getId(),
                            batch.getId(),
                            batch.getLocationId(),
                            null,
                            InventoryTransactionType.DISPENSED,
                            use,
                            "Prescription dispensing",
                            "PRESCRIPTION",
                            prescriptionId,
                            actorAppUserId,
                            "Dispensed from prescription " + prescription.prescriptionNumber()
                    ),
                    actorAppUserId
            );
            remaining -= use;
            lastBatch = batch.getId();
        }

        int dispensed = targetQuantity - remaining;
        if (dispensed <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No quantity could be dispensed");
        }

        target.addDispense(dispensed, lastBatch);
        dispenseItemRepository.save(target);
        return view(tenantId, prescriptionId);
    }

    @Transactional
    public BillRecord createMedicineBill(UUID tenantId, UUID prescriptionId, UUID actorAppUserId) {
        PrescriptionRecord prescription = prescriptionService.findById(tenantId, prescriptionId)
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
                    MedicineEntity med = medicineRepository.findByTenantIdAndId(tenantId, i.getMedicineId()).orElse(null);
                    BigDecimal unit = med == null || med.getDefaultPrice() == null ? BigDecimal.ZERO : med.getDefaultPrice();
                    return new BillLineCommand(BillItemType.MEDICINE, i.getPrescribedMedicineName(), i.getDispensedQuantity(), unit, i.getMedicineId(), i.getPrescribedSortOrder(), BigDecimal.ZERO, null, i.getId());
                })
                .toList();

        BillRecord bill = billingService.createDraft(tenantId, new BillUpsertCommand(
                prescription.patientId(),
                prescription.consultationId(),
                prescription.appointmentId(),
                LocalDate.now(),
                DiscountType.NONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                BigDecimal.ZERO,
                "Medicine bill for prescription " + prescription.prescriptionNumber(),
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

    private StockAvailability stockAvailability(UUID tenantId, MedicineEntity medicine) {
        if (medicine == null) {
            return new StockAvailability(0, "OUT_OF_STOCK", "OUT_OF_STOCK", null);
        }
        UUID locationId = resolveDefaultLocationId(tenantId);
        List<StockEntity> active = stockRepository.findByTenantIdAndLocationIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(tenantId, locationId, medicine.getId(), 0);
        if (active.isEmpty()) {
            return new StockAvailability(0, "NO_INVENTORY", "NONE", null);
        }
        List<StockEntity> usable = active.stream().filter(stock -> !isExpired(stock)).toList();
        int availableQuantity = usable.stream().mapToInt(StockEntity::getQuantityOnHand).sum();
        if (availableQuantity <= 0) {
            boolean hasExpired = active.stream().anyMatch(this::isExpired);
            return new StockAvailability(0, "OUT_OF_STOCK", hasExpired ? "EXPIRED" : "OUT_OF_STOCK", null);
        }
        Integer threshold = usable.stream()
                .map(StockEntity::getLowStockThreshold)
                .filter(value -> value != null)
                .min(Integer::compareTo)
                .orElse(5);
        LocalDate nearestExpiryDate = usable.stream()
                .map(StockEntity::getExpiryDate)
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .orElse(null);
        String status = availableQuantity < threshold ? "LOW_STOCK" : "AVAILABLE";
        String expiryStatus = expiryStatus(nearestExpiryDate);
        return new StockAvailability(availableQuantity, status, expiryStatus, nearestExpiryDate);
    }

    private List<StockEntity> availableBatches(UUID tenantId, UUID medicineId) {
        UUID locationId = resolveDefaultLocationId(tenantId);
        return stockRepository.findByTenantIdAndLocationIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(tenantId, locationId, medicineId, 0)
                .stream()
                .filter(stock -> !isExpired(stock))
                .toList();
    }

    private List<StockEntity> allStockBatches(UUID tenantId, UUID medicineId) {
        UUID locationId = resolveDefaultLocationId(tenantId);
        return stockRepository.findByTenantIdAndMedicineIdAndLocationId(tenantId, medicineId, locationId).stream()
                .filter(StockEntity::isActive)
                .toList();
    }

    private UUID resolveDefaultLocationId(UUID tenantId) {
        return locationRepository.findByTenantIdAndDefaultLocationTrue(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Default pharmacy location not configured"))
                .getId();
    }

    private String expiryStatus(LocalDate expiryDate) {
        if (expiryDate == null) {
            return "NONE";
        }
        LocalDate today = LocalDate.now();
        if (expiryDate.isBefore(today)) {
            return "EXPIRED";
        }
        if (!expiryDate.isAfter(today.plusDays(EXPIRY_WARNING_DAYS))) {
            return "NEAR_EXPIRY";
        }
        return "OK";
    }

    private boolean isExpired(StockEntity stock) {
        return stock != null && stock.getExpiryDate() != null && stock.getExpiryDate().isBefore(LocalDate.now());
    }

    private boolean isClosedStatus(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "DISPENSED", "UNAVAILABLE", "CANCELLED" -> true;
            default -> false;
        };
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return ACTION_PARTIAL;
        }
        return action.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private record StockAvailability(int availableQuantity, String status, String expiryStatus, LocalDate nearestExpiryDate) {
    }
}
