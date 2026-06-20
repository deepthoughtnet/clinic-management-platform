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
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final String ACTION_FULL = "FULL_DISPENSE";
    private static final String ACTION_PARTIAL = "PARTIAL_DISPENSE";
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
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public PrescriptionDispensingService(
            PrescriptionService prescriptionService,
            PrescriptionMedicineRepository prescriptionMedicineRepository,
            PrescriptionDispensationRepository dispensationRepository,
            PrescriptionDispenseItemRepository dispenseItemRepository,
            MedicineRepository medicineRepository,
            InventoryLocationRepository locationRepository,
            StockRepository stockRepository,
            InventoryService inventoryService,
            BillingService billingService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
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
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
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
        String dispensingStatus = resolvePrescriptionDispensingStatusFromLines(disp, responseLines);

        return new PrescriptionDispenseResponse(
                prescription.id(),
                prescription.prescriptionNumber(),
                prescription.patientId(),
                prescription.patientName(),
                prescription.doctorName(),
                prescription.finalizedAt() == null ? prescription.createdAt() : prescription.finalizedAt(),
                dispensingStatus,
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

        String action = normalizeAction(request.action());
        boolean closureAction = isClosureAction(action);
        PrescriptionDispensationEntity disp = dispensationRepository.findByTenantIdAndPrescriptionId(tenantId, prescriptionId)
                .orElseGet(() -> dispensationRepository.save(PrescriptionDispensationEntity.create(tenantId, prescriptionId, prescription.patientId())));
        if (isTerminalDispensingStatus(disp.getDispensingStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This prescription has already been closed.");
        }

        List<PrescriptionMedicineEntity> prescriptionLines = prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescriptionId);
        List<PrescriptionDispenseItemEntity> items = new ArrayList<>(dispenseItemRepository.findByTenantIdAndPrescriptionIdOrderByPrescribedSortOrderAsc(tenantId, prescriptionId));

        if (closureAction) {
            String closureStatus = terminalStatusForAction(action);
            String reason = normalizeNullable(request.reason());
            String remarks = normalizeNullable(request.remarks());
            if (reason == null) {
                throw new IllegalArgumentException("closure reason is required");
            }
            if (request.medicineLineId() != null) {
                PrescriptionDispenseItemEntity target = items.stream()
                        .filter(item -> request.medicineLineId().equals(item.getId()))
                        .findFirst()
                        .orElse(null);
                if (target != null) {
                    if (isTerminalDispensingStatus(target.getStatus()) || "DISPENSED".equalsIgnoreCase(target.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "This medicine line has already been closed.");
                    }
                    target.markTerminal(closureStatus);
                    dispenseItemRepository.save(target);
                }
            }
            disp.updateDispensingStatus(closureStatus, reason, remarks);
            dispensationRepository.save(disp);
            audit(tenantId, prescriptionId, action, actorAppUserId, null, closureStatus, reason, remarks, Map.of("prescriptionNumber", prescription.prescriptionNumber()));
            return view(tenantId, prescriptionId);
        }

        MedicineEntity medicine = resolveMedicine(tenantId, request);
        PrescriptionMedicineEntity matched = resolvePrescriptionLine(prescriptionLines, items, request);
        PrescriptionDispenseItemEntity item = resolveTargetItem(tenantId, disp, prescriptionId, prescriptionLines, items, request);
        if (item != null && (isTerminalDispensingStatus(item.getStatus()) || "DISPENSED".equalsIgnoreCase(item.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This medicine line has already been fully dispensed.");
        }
        if (item == null) {
            item = dispenseItemRepository.save(PrescriptionDispenseItemEntity.create(
                    tenantId,
                    disp.getId(),
                    prescriptionId,
                    matched.getId(),
                    medicine.getId(),
                    matched.getMedicineName(),
                    matched.getSortOrder(),
                    Math.max(1, resolvePrescribedQuantity(matched))
            ));
            items.add(item);
        }

        int pendingQuantity = Math.max(1, item.getPendingQuantity() > 0 ? item.getPendingQuantity() : resolvePrescribedQuantity(matched));
        int requestedQuantity = ACTION_FULL.equals(action) ? pendingQuantity : normalizeQuantity(request.quantity());
        if (requestedQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "quantity must be positive");
        }
        if (requestedQuantity > pendingQuantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested quantity exceeds pending quantity.");
        }

        List<StockEntity> activeStockRows = allStockBatches(tenantId, medicine.getId());
        List<StockEntity> fefo = activeStockRows.stream()
                .filter(stock -> stock.getQuantityOnHand() > 0)
                .filter(stock -> !isExpired(stock))
                .toList();
        if (fefo.isEmpty()) {
            if (activeStockRows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No inventory batch exists for this medicine. The prescription is still valid; add stock before dispensing.");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This medicine has no sellable stock. The prescription is still valid; replenish stock before dispensing.");
        }

        List<StockEntity> targetBatches = fefo;
        if (request.batchOverride() != null && !request.batchOverride().isBlank()) {
            if (!(canOverrideBatch && request.allowBatchOverride())) {
                throw new IllegalArgumentException("Manual batch override is not allowed for this role");
            }
            if (normalizeNullable(request.reason()) == null) {
                throw new IllegalArgumentException("batch override reason is required");
            }
            StockEntity selectedBatch = resolveBatchOverride(tenantId, medicine.getId(), request.batchOverride());
            if (selectedBatch == null || !selectedBatch.isActive() || selectedBatch.getQuantityOnHand() <= 0 || isExpired(selectedBatch)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected batch is unavailable or expired.");
            }
            targetBatches = List.of(selectedBatch);
        }

        int availableQuantity = targetBatches.stream().mapToInt(StockEntity::getQuantityOnHand).sum();
        if (availableQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This medicine has no quantity on hand for dispensing. The prescription is still valid; replenish stock before dispensing.");
        }
        if (requestedQuantity > availableQuantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested quantity exceeds available stock.");
        }

        int remaining = requestedQuantity;
        UUID lastBatch = null;
        for (StockEntity batch : targetBatches) {
            if (remaining <= 0) {
                break;
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

        int dispensed = requestedQuantity - remaining;
        if (dispensed <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No quantity could be dispensed");
        }

        item.addDispense(dispensed, lastBatch);
        dispenseItemRepository.save(item);
        disp.updateDispensingStatus(resolvePrescriptionDispensingStatusFromItems(disp, items), null, null);
        dispensationRepository.save(disp);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("prescriptionNumber", prescription.prescriptionNumber());
        details.put("medicineLineId", item.getId().toString());
        details.put("medicineName", item.getPrescribedMedicineName());
        if (normalizeNullable(request.batchOverride()) != null) {
            details.put("batchOverride", normalizeNullable(request.batchOverride()));
        }
        if (normalizeNullable(request.reason()) != null) {
            details.put("reason", normalizeNullable(request.reason()));
        }
        audit(tenantId, prescriptionId, action, actorAppUserId, dispensed, null, null, null, details);
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
        UUID medicineId = request.medicineId();
        if (medicineId != null) {
            return medicineRepository.findByTenantIdAndId(tenantId, medicineId)
                    .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        }
        return medicineRepository.findByTenantIdAndMedicineNameIgnoreCase(tenantId, request.prescribedMedicineName())
                .orElseThrow(() -> new IllegalArgumentException("Mapped medicine not found in master"));
    }

    private PrescriptionMedicineEntity resolvePrescriptionLine(List<PrescriptionMedicineEntity> prescriptionLines, List<PrescriptionDispenseItemEntity> items, DispenseRequest request) {
        if (request.medicineLineId() != null) {
            PrescriptionDispenseItemEntity current = items.stream()
                    .filter(item -> request.medicineLineId().equals(item.getId()))
                    .findFirst()
                    .orElse(null);
            if (current != null) {
                return prescriptionLines.stream()
                        .filter(line -> current.getPrescriptionMedicineId() != null && current.getPrescriptionMedicineId().equals(line.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Prescribed medicine line not found"));
            }
            return prescriptionLines.stream()
                    .filter(line -> request.medicineLineId().equals(line.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Prescribed medicine line not found"));
        }
        return prescriptionLines.stream()
                .filter(line -> normalize(line.getMedicineName()).equals(normalize(request.prescribedMedicineName())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prescribed medicine line not found"));
    }

    private PrescriptionDispenseItemEntity resolveTargetItem(UUID tenantId, PrescriptionDispensationEntity disp, UUID prescriptionId, List<PrescriptionMedicineEntity> prescriptionLines, List<PrescriptionDispenseItemEntity> items, DispenseRequest request) {
        if (request.medicineLineId() != null) {
            return items.stream()
                    .filter(item -> request.medicineLineId().equals(item.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        PrescriptionMedicineEntity line = prescriptionLines.stream()
                                .filter(candidate -> request.medicineLineId().equals(candidate.getId()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Prescribed medicine line not found"));
                        return dispenseItemRepository.save(PrescriptionDispenseItemEntity.create(
                                tenantId,
                                disp.getId(),
                                prescriptionId,
                                line.getId(),
                                resolveMedicineIdForLine(tenantId, request, line),
                                line.getMedicineName(),
                                line.getSortOrder(),
                                Math.max(1, resolvePrescribedQuantity(line))
                        ));
                    });
        }
        return items.stream()
                .filter(item -> normalize(item.getPrescribedMedicineName()).equals(normalize(request.prescribedMedicineName())))
                .findFirst()
                .orElse(null);
    }

    private PrescriptionDispenseItemEntity resolveLineItem(UUID tenantId, PrescriptionDispensationEntity disp, UUID prescriptionId, PrescriptionMedicineEntity line, List<PrescriptionDispenseItemEntity> items) {
        return items.stream()
                .filter(item -> line.getId().equals(item.getPrescriptionMedicineId()))
                .findFirst()
                .orElse(null);
    }

    private UUID resolveMedicineIdForLine(UUID tenantId, DispenseRequest request, PrescriptionMedicineEntity line) {
        if (request.medicineId() != null) {
            return request.medicineId();
        }
        return medicineRepository.findByTenantIdAndMedicineNameIgnoreCase(tenantId, line.getMedicineName())
                .map(MedicineEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("Mapped medicine not found in master"));
    }

    private int resolvePrescribedQuantity(PrescriptionMedicineEntity line) {
        return 1;
    }

    private int normalizeQuantity(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }

    private StockEntity resolveBatchOverride(UUID tenantId, UUID medicineId, String batchOverride) {
        UUID locationId = resolveDefaultLocationId(tenantId);
        return stockRepository.findByTenantIdAndLocationIdAndBatchNumberIgnoreCase(tenantId, locationId, batchOverride.trim())
                .filter(stock -> stock.getMedicineId().equals(medicineId))
                .orElse(null);
    }

    private String resolvePrescriptionDispensingStatusFromLines(PrescriptionDispensationEntity disp, List<PrescriptionDispenseLineResponse> responseLines) {
        if (disp != null && isTerminalDispensingStatus(disp.getDispensingStatus())) {
            return disp.getDispensingStatus();
        }
        if (responseLines.isEmpty()) {
            return "NOT_DISPENSED";
        }
        boolean anyDispensed = responseLines.stream().anyMatch(line -> line.dispensedQuantity() > 0);
        boolean allDispensed = responseLines.stream().allMatch(line -> line.dispensedQuantity() > 0 && "DISPENSED".equalsIgnoreCase(line.status()));
        boolean anyTerminal = responseLines.stream().anyMatch(line -> isTerminalDispensingStatus(line.status()));
        boolean anyActive = responseLines.stream().anyMatch(line -> isActiveDispensingStatus(line.status()));
        boolean anyAvailable = responseLines.stream().anyMatch(line -> (line.availableQuantity() != null && line.availableQuantity() > 0) && !isTerminalDispensingStatus(line.status()));
        if (allDispensed && anyDispensed) {
            return "FULLY_DISPENSED";
        }
        if (anyTerminal && !anyActive) {
            return responseLines.stream()
                    .map(PrescriptionDispenseLineResponse::status)
                    .filter(this::isTerminalDispensingStatus)
                    .findFirst()
                    .orElse("NOT_DISPENSED");
        }
        if (anyDispensed || anyTerminal) {
            return "PARTIALLY_DISPENSED";
        }
        if (anyAvailable) {
            return "READY_FOR_DISPENSE";
        }
        return "NOT_DISPENSED";
    }

    private String resolvePrescriptionDispensingStatusFromItems(PrescriptionDispensationEntity disp, List<PrescriptionDispenseItemEntity> items) {
        if (disp != null && isTerminalDispensingStatus(disp.getDispensingStatus())) {
            return disp.getDispensingStatus();
        }
        if (items.isEmpty()) {
            return "NOT_DISPENSED";
        }
        boolean allDispensed = items.stream().allMatch(item -> "DISPENSED".equalsIgnoreCase(item.getStatus()) && item.getDispensedQuantity() > 0);
        if (allDispensed) {
            return "FULLY_DISPENSED";
        }
        boolean anyTerminal = items.stream().anyMatch(item -> isTerminalDispensingStatus(item.getStatus()));
        boolean anyDispensed = items.stream().anyMatch(item -> item.getDispensedQuantity() > 0);
        boolean anyActive = items.stream().anyMatch(item -> isActiveDispensingStatus(item.getStatus()));
        if (anyTerminal && !anyActive) {
            return items.stream()
                    .map(PrescriptionDispenseItemEntity::getStatus)
                    .filter(this::isTerminalDispensingStatus)
                    .findFirst()
                    .orElse("NOT_DISPENSED");
        }
        if (anyDispensed || anyTerminal) {
            return "PARTIALLY_DISPENSED";
        }
        return "NOT_DISPENSED";
    }

    private void audit(UUID tenantId, UUID entityId, String action, UUID actorAppUserId, Integer quantity, String terminalStatus, String reason, String remarks, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>(details == null ? Map.of() : details);
        if (quantity != null) {
            payload.put("quantity", quantity);
        }
        if (terminalStatus != null) {
            payload.put("status", terminalStatus);
        }
        if (reason != null) {
            payload.put("reason", reason);
        }
        if (remarks != null) {
            payload.put("remarks", remarks);
        }
        try {
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    "PRESCRIPTION_DISPENSING",
                    entityId,
                    action,
                    actorAppUserId,
                    OffsetDateTime.now(),
                    "Processed prescription dispensing action",
                    objectMapper.writeValueAsString(payload)
            ));
        } catch (JsonProcessingException ex) {
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    "PRESCRIPTION_DISPENSING",
                    entityId,
                    action,
                    actorAppUserId,
                    OffsetDateTime.now(),
                    "Processed prescription dispensing action",
                    "{}"
            ));
        }
    }

    private boolean isTerminalDispensingStatus(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "FULLY_DISPENSED", "BOUGHT_EXTERNALLY", "PATIENT_DECLINED", "UNAVAILABLE_CLOSED", "CANCELLED", "EXPIRED" -> true;
            default -> false;
        };
    }

    private boolean isActiveDispensingStatus(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "NOT_DISPENSED", "READY_FOR_DISPENSE", "PARTIALLY_DISPENSED" -> true;
            default -> false;
        };
    }

    private boolean isClosureAction(String action) {
        return switch (action) {
            case "BUY_OUTSIDE", "PATIENT_DECLINED", "UNAVAILABLE_CLOSED", "MARK_UNAVAILABLE", "CANCEL_PRESCRIPTION", "EXPIRED" -> true;
            default -> false;
        };
    }

    private String terminalStatusForAction(String action) {
        return switch (action) {
            case "BUY_OUTSIDE" -> "BOUGHT_EXTERNALLY";
            case "PATIENT_DECLINED" -> "PATIENT_DECLINED";
            case "UNAVAILABLE_CLOSED", "MARK_UNAVAILABLE" -> "UNAVAILABLE_CLOSED";
            case "CANCEL_PRESCRIPTION" -> "CANCELLED";
            case "EXPIRED" -> "EXPIRED";
            default -> "UNAVAILABLE_CLOSED";
        };
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "PARTIAL_DISPENSE";
        }
        return action.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private record StockAvailability(int availableQuantity, String status, String expiryStatus, LocalDate nearestExpiryDate) {
    }
}
