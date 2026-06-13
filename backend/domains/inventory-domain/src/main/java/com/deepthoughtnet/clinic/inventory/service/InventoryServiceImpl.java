package com.deepthoughtnet.clinic.inventory.service;

import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransferCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.inventory.service.model.LowStockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InventoryServiceImpl implements InventoryService {
    private static final String MEDICINE_ENTITY = "MEDICINE";
    private static final String STOCK_ENTITY = "INVENTORY_STOCK";
    private static final String TRANSACTION_ENTITY = "INVENTORY_TRANSACTION";

    private final MedicineRepository medicineRepository;
    private final StockRepository stockRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryLocationRepository locationRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public InventoryServiceImpl(
            MedicineRepository medicineRepository,
            StockRepository stockRepository,
            InventoryTransactionRepository transactionRepository,
            InventoryLocationRepository locationRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.medicineRepository = medicineRepository;
        this.stockRepository = stockRepository;
        this.transactionRepository = transactionRepository;
        this.locationRepository = locationRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MedicineRecord> listMedicines(UUID tenantId) {
        requireTenant(tenantId);
        return medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MedicineRecord> findMedicine(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return medicineRepository.findByTenantIdAndId(tenantId, id).map(this::toRecord);
    }

    @Override
    @Transactional
    public MedicineRecord createMedicine(UUID tenantId, MedicineUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateMedicine(command);
        ensureUniqueMedicine(tenantId, command, null);
        MedicineEntity entity = MedicineEntity.create(tenantId, normalize(command.medicineName()), normalizeType(command.medicineType()));
        applyMedicine(entity, command);
        MedicineEntity saved = medicineRepository.save(entity);
        auditMedicine(tenantId, saved, "medicine.created", actorAppUserId, "Created medicine");
        return toRecord(saved);
    }

    @Override
    @Transactional
    public MedicineRecord updateMedicine(UUID tenantId, UUID id, MedicineUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validateMedicine(command);
        MedicineEntity entity = medicineRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        ensureUniqueMedicine(tenantId, command, id);
        applyMedicine(entity, command);
        MedicineEntity saved = medicineRepository.save(entity);
        auditMedicine(tenantId, saved, "medicine.updated", actorAppUserId, "Updated medicine");
        return toRecord(saved);
    }

    @Override
    @Transactional
    public MedicineRecord deactivateMedicine(UUID tenantId, UUID id, UUID actorAppUserId) {
        return setActive(tenantId, id, actorAppUserId, false);
    }

    @Override
    @Transactional
    public MedicineRecord activateMedicine(UUID tenantId, UUID id, UUID actorAppUserId) {
        return setActive(tenantId, id, actorAppUserId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listStocks(UUID tenantId) {
        requireTenant(tenantId);
        return mapStocks(tenantId, stockRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listStocks(UUID tenantId, UUID locationId) {
        requireTenant(tenantId);
        if (locationId == null) {
            return listStocks(tenantId);
        }
        return mapStocks(tenantId, stockRepository.findByTenantIdAndLocationIdOrderByUpdatedAtDesc(tenantId, locationId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockRecord> findStock(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return stockRepository.findByTenantIdAndId(tenantId, id).map(stock -> toRecord(stock, medicineRepository.findByTenantIdAndId(tenantId, stock.getMedicineId()).orElse(null)));
    }

    @Override
    @Transactional
    public StockRecord createStock(UUID tenantId, StockUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateStock(tenantId, command);
        UUID locationId = resolveLocationId(tenantId, command.locationId());
        ensureUniqueStockBatch(tenantId, command.medicineId(), locationId, command.batchNumber(), null);
        StockEntity entity = StockEntity.create(tenantId, command.medicineId(), locationId);
        int beforeQuantity = entity.getQuantityOnHand();
        applyStock(entity, command);
        StockEntity saved;
        try {
            saved = stockRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translateStockIntegrityViolation(ex);
        }
        recordStockQuantityAudit(tenantId, saved, beforeQuantity, saved.getQuantityOnHand(), command, actorAppUserId, true);
        auditStock(tenantId, saved, "stock.created", actorAppUserId, "Created stock batch");
        return toRecord(saved, medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null));
    }

    @Override
    @Transactional
    public StockRecord updateStock(UUID tenantId, UUID id, StockUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validateStock(tenantId, command);
        UUID locationId = resolveLocationId(tenantId, command.locationId());
        ensureUniqueStockBatch(tenantId, command.medicineId(), locationId, command.batchNumber(), id);
        StockEntity entity = stockRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));
        int beforeQuantity = entity.getQuantityOnHand();
        applyStock(entity, command);
        StockEntity saved;
        try {
            saved = stockRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translateStockIntegrityViolation(ex);
        }
        recordStockQuantityAudit(tenantId, saved, beforeQuantity, saved.getQuantityOnHand(), command, actorAppUserId, false);
        auditStock(tenantId, saved, "stock.updated", actorAppUserId, "Updated stock batch");
        return toRecord(saved, medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTransactionRecord> listTransactions(UUID tenantId) {
        requireTenant(tenantId);
        return transactionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional
    public InventoryTransactionRecord createTransaction(UUID tenantId, InventoryTransactionCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validateTransaction(tenantId, command);
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, command.medicineId())
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        StockEntity stock = command.stockBatchId() == null ? null : stockRepository.findByTenantIdAndId(tenantId, command.stockBatchId())
                .orElseThrow(() -> new IllegalArgumentException("Stock batch not found"));
        UUID locationId = resolveLocationId(tenantId, command.locationId() != null ? command.locationId() : (stock == null ? null : stock.getLocationId()));

        int delta = Math.abs(command.quantity());
        Integer beforeQuantity = null;
        Integer afterQuantity = null;
        if (stock != null) {
            InventoryTransactionType normalizedType = normalizeTransactionType(command.transactionType());
            if (isExpired(stock) && (normalizedType == InventoryTransactionType.SALE || normalizedType == InventoryTransactionType.DISPENSED)) {
                throw new IllegalArgumentException("Batch expired and cannot be sold or dispensed.");
            }
            int current = stock.getQuantityOnHand();
            int nextQuantity = switch (normalizedType) {
                case DISPENSED, ADJUSTMENT_OUT, EXPIRED, SALE, VENDOR_RETURN_OUT, WRITE_OFF -> current - delta;
                case CUSTOMER_RETURN_NON_SELLABLE -> current;
                case ADJUSTMENT -> current + delta;
                default -> current + delta;
            };
            if (nextQuantity < 0) {
                throw new IllegalArgumentException("Insufficient stock available.");
            }
            beforeQuantity = current;
            afterQuantity = nextQuantity;
            stock.setQuantityOnHand(nextQuantity);
            stockRepository.save(stock);
        }

        InventoryTransactionEntity entity = transactionRepository.save(InventoryTransactionEntity.create(
                tenantId,
                medicine.getId(),
                stock == null ? null : stock.getId(),
                locationId,
                command.targetLocationId(),
                command.transactionType().name(),
                delta,
                beforeQuantity,
                afterQuantity,
                normalizeNullable(command.referenceType()),
                command.referenceId(),
                command.createdBy() == null ? actorAppUserId : command.createdBy(),
                normalizeNullable(command.reason()),
                normalizeNullable(command.notes())
        ));
        auditTransaction(tenantId, entity, "inventory.transaction.created", actorAppUserId, "Created inventory transaction");
        return toRecord(entity);
    }

    @Override
    @Transactional
    public InventoryTransactionRecord transferStock(UUID tenantId, InventoryTransferCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        UUID fromLocationId = resolveLocationId(tenantId, command.fromLocationId());
        UUID toLocationId = resolveLocationId(tenantId, command.toLocationId());
        if (fromLocationId.equals(toLocationId)) {
            throw new IllegalArgumentException("source and destination locations must differ");
        }
        StockEntity source = command.stockBatchId() == null
                ? stockRepository.findByTenantIdAndLocationIdAndBatchNumberIgnoreCase(tenantId, fromLocationId, null).orElse(null)
                : stockRepository.findByTenantIdAndId(tenantId, command.stockBatchId())
                .orElseThrow(() -> new IllegalArgumentException("Stock batch not found"));
        if (source == null || !source.getLocationId().equals(fromLocationId)) {
            throw new IllegalArgumentException("Source stock not found in selected location");
        }
        int quantity = Math.max(0, command.quantity());
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (source.getQuantityOnHand() < quantity) {
            throw new IllegalArgumentException("Insufficient stock available.");
        }
        MedicineEntity medicine = medicineRepository.findByTenantIdAndId(tenantId, command.medicineId())
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        int sourceBefore = source.getQuantityOnHand();
        source.setQuantityOnHand(sourceBefore - quantity);
        stockRepository.save(source);

        StockEntity target = stockRepository.findByTenantIdAndMedicineIdAndLocationId(tenantId, medicine.getId(), toLocationId).stream()
                .filter(stock -> source.getBatchNumber() == null || source.getBatchNumber().equalsIgnoreCase(normalizeNullable(stock.getBatchNumber())))
                .findFirst()
                .orElseGet(() -> StockEntity.create(tenantId, medicine.getId(), toLocationId));
        if (target.getId() == null) {
            // defensive, but StockEntity.create always assigns an id
        }
        target.update(
                target.getLocationId() == null ? toLocationId : target.getLocationId(),
                source.getBarcode(),
                source.getQrCode(),
                source.getExternalCode(),
                source.getBatchNumber(),
                source.getPurchaseReferenceNumber(),
                source.getExpiryDate(),
                source.getPurchaseDate(),
                source.getSupplierName(),
                target.getQuantityReceived() + quantity,
                target.getQuantityOnHand() + quantity,
                target.getLowStockThreshold(),
                target.getUnitCost(),
                target.getPurchasePrice(),
                target.getSellingPrice(),
                true
        );
        StockEntity savedTarget = stockRepository.save(target);

        InventoryTransactionEntity entity = transactionRepository.save(InventoryTransactionEntity.create(
                tenantId,
                medicine.getId(),
                source.getId(),
                fromLocationId,
                toLocationId,
                InventoryTransactionType.TRANSFER_OUT.name(),
                quantity,
                sourceBefore,
                source.getQuantityOnHand(),
                "TRANSFER",
                null,
                actorAppUserId,
                normalizeNullable(command.reason()),
                "Transferred stock to another location"
        ));
        auditTransaction(tenantId, entity, "inventory.transfer.created", actorAppUserId, "Transferred stock between locations");
        return toRecord(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LowStockRecord> listLowStock(UUID tenantId) {
        requireTenant(tenantId);
        return stockRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .filter(stock -> stock.isActive() && stock.getQuantityOnHand() <= effectiveThreshold(stock))
                .map(stock -> toLowStockRecord(stock, medicineRepository.findByTenantIdAndId(tenantId, stock.getMedicineId()).orElse(null)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listExpiredStocks(UUID tenantId) {
        return listStocks(tenantId).stream()
                .filter(stock -> stock.expiryDate() != null && stock.expiryDate().isBefore(today()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listExpiringStocks(UUID tenantId, int days) {
        LocalDate current = today();
        LocalDate end = current.plusDays(Math.max(days, 1));
        return listStocks(tenantId).stream()
                .filter(stock -> stock.expiryDate() != null && !stock.expiryDate().isBefore(current) && !stock.expiryDate().isAfter(end))
                .toList();
    }

    @Override
    @Transactional
    public List<InventoryLocationRecord> listLocations(UUID tenantId) {
        requireTenant(tenantId);
        ensureDefaultLocation(tenantId);
        return locationRepository.findByTenantIdOrderByDefaultLocationDescLocationNameAsc(tenantId).stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional
    public InventoryLocationRecord saveLocation(UUID tenantId, UUID id, InventoryLocationUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        if (command == null || !StringUtils.hasText(command.locationName()) || !StringUtils.hasText(command.locationType())) {
            throw new IllegalArgumentException("location name and type are required");
        }
        InventoryLocationEntity entity = id == null
                ? InventoryLocationEntity.create(tenantId, normalize(command.locationName()), normalizeNullable(command.locationCode()), normalize(command.locationType()), command.defaultLocation())
                : locationRepository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Location not found"));
        if (id != null) {
            entity.update(normalize(command.locationName()), normalizeNullable(command.locationCode()), normalize(command.locationType()), command.defaultLocation(), command.active());
        }
        if (command.defaultLocation()) {
            locationRepository.findByTenantIdOrderByDefaultLocationDescLocationNameAsc(tenantId).stream()
                    .filter(location -> !location.getId().equals(entity.getId()) && location.isDefaultLocation())
                    .findFirst()
                    .ifPresent(previous -> {
                        previous.update(previous.getLocationName(), previous.getLocationCode(), previous.getLocationType(), false, previous.isActive());
                        locationRepository.save(previous);
                    });
        }
        InventoryLocationEntity saved = locationRepository.save(entity);
        return toRecord(saved);
    }

    private InventoryTransactionType normalizeTransactionType(InventoryTransactionType type) {
        if (type == null) {
            return InventoryTransactionType.OPENING;
        }
        return switch (type) {
            case SALE -> InventoryTransactionType.DISPENSED;
            case PURCHASE -> InventoryTransactionType.STOCK_IN;
            default -> type;
        };
    }

    private MedicineRecord setActive(UUID tenantId, UUID id, UUID actorAppUserId, boolean active) {
        requireTenant(tenantId);
        requireId(id, "id");
        MedicineEntity entity = medicineRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        entity.update(entity.getMedicineName(), entity.getMedicineType(), entity.getBarcode(), entity.getQrCode(), entity.getExternalCode(), entity.getGenericName(), entity.getBrandName(), entity.getCategory(), entity.getDosageForm(), entity.getStrength(), entity.getUnit(), entity.getManufacturer(), entity.getDefaultDosage(), entity.getDefaultFrequency(), entity.getDefaultDurationDays(), entity.getDefaultTiming(), entity.getDefaultInstructions(), entity.getDefaultPrice(), entity.getTaxRate(), active);
        MedicineEntity saved = medicineRepository.save(entity);
        auditMedicine(tenantId, saved, active ? "medicine.activated" : "medicine.deactivated", actorAppUserId, active ? "Activated medicine" : "Deactivated medicine");
        return toRecord(saved);
    }

    private MedicineRecord toRecord(MedicineEntity entity) {
        return new MedicineRecord(entity.getId(), entity.getTenantId(), entity.getMedicineName(), entity.getMedicineType(), entity.getBarcode(), entity.getQrCode(), entity.getExternalCode(), entity.getGenericName(), entity.getBrandName(), entity.getCategory(), entity.getDosageForm(), entity.getStrength(), entity.getUnit(), entity.getManufacturer(), entity.getDefaultDosage(), entity.getDefaultFrequency(), entity.getDefaultDurationDays(), entity.getDefaultTiming(), entity.getDefaultInstructions(), entity.getDefaultPrice(), entity.getTaxRate(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private StockRecord toRecord(StockEntity stock, MedicineEntity medicine) {
        InventoryLocationEntity location = stock.getLocationId() == null ? null : locationRepository.findByTenantIdAndId(stock.getTenantId(), stock.getLocationId()).orElse(null);
        return new StockRecord(stock.getId(), stock.getTenantId(), stock.getMedicineId(), stock.getLocationId(), location == null ? null : location.getLocationName(), medicine == null ? null : medicine.getMedicineName(), medicine == null ? null : medicine.getMedicineType(), stock.getBarcode(), stock.getQrCode(), stock.getExternalCode(), stock.getBatchNumber(), stock.getPurchaseReferenceNumber(), stock.getExpiryDate(), stock.getPurchaseDate(), stock.getSupplierName(), stock.getQuantityReceived(), stock.getQuantityOnHand(), stock.getLowStockThreshold(), stock.getUnitCost(), stock.getPurchasePrice(), stock.getSellingPrice(), stock.isActive(), stock.getCreatedAt(), stock.getUpdatedAt());
    }

    private LowStockRecord toLowStockRecord(StockEntity stock, MedicineEntity medicine) {
        return new LowStockRecord(stock.getId(), stock.getMedicineId(), medicine == null ? null : medicine.getMedicineName(), medicine == null ? null : medicine.getMedicineType(), stock.getBatchNumber(), stock.getExpiryDate(), stock.getQuantityOnHand(), stock.getLowStockThreshold(), stock.getSellingPrice());
    }

    private InventoryTransactionRecord toRecord(InventoryTransactionEntity entity) {
        return new InventoryTransactionRecord(entity.getId(), entity.getTenantId(), entity.getMedicineId(), entity.getStockBatchId(), entity.getLocationId(), entity.getTargetLocationId(), InventoryTransactionType.valueOf(entity.getTransactionType()), entity.getQuantity(), entity.getBeforeQuantity(), entity.getAfterQuantity(), entity.getReason(), entity.getReferenceType(), entity.getReferenceId(), entity.getCreatedBy(), entity.getNotes(), entity.getCreatedAt());
    }

    private InventoryLocationRecord toRecord(InventoryLocationEntity entity) {
        return new InventoryLocationRecord(entity.getId(), entity.getTenantId(), entity.getLocationName(), entity.getLocationCode(), entity.getLocationType(), entity.isDefaultLocation(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private List<StockRecord> mapStocks(UUID tenantId, List<StockEntity> stocks) {
        Map<UUID, MedicineEntity> medicines = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .collect(Collectors.toMap(MedicineEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return stocks.stream().map(stock -> toRecord(stock, medicines.get(stock.getMedicineId()))).toList();
    }

    private void applyMedicine(MedicineEntity entity, MedicineUpsertCommand command) {
        entity.update(normalize(command.medicineName()), normalizeType(command.medicineType()), normalizeNullable(command.barcode()), normalizeNullable(command.qrCode()), normalizeNullable(command.externalCode()), normalizeNullable(command.genericName()), normalizeNullable(command.brandName()), normalizeNullable(command.category()), normalizeNullable(command.dosageForm()), normalizeNullable(command.strength()), normalizeNullable(command.unit()), normalizeNullable(command.manufacturer()), normalizeNullable(command.defaultDosage()), normalizeNullable(command.defaultFrequency()), command.defaultDurationDays(), normalizeNullable(command.defaultTiming()), normalizeNullable(command.defaultInstructions()), normalizeMoney(command.defaultPrice()), normalizeMoney(command.taxRate()), command.active());
    }

    private void applyStock(StockEntity entity, StockUpsertCommand command) {
        int quantityOnHand = command.quantityOnHand();
        int quantityReceived = command.quantityReceived() == null ? quantityOnHand : command.quantityReceived();
        entity.update(resolveLocationId(entity.getTenantId(), command.locationId()), normalizeNullable(command.barcode()), normalizeNullable(command.qrCode()), normalizeNullable(command.externalCode()), normalizeNullable(command.batchNumber()), normalizeNullable(command.purchaseReferenceNumber()), command.expiryDate(), command.purchaseDate(), normalizeNullable(command.supplierName()), quantityReceived, quantityOnHand, command.lowStockThreshold(), normalizeMoney(command.unitCost()), normalizeMoney(command.purchasePrice()), normalizeMoney(command.sellingPrice()), command.active());
    }

    private void recordStockQuantityAudit(
            UUID tenantId,
            StockEntity stock,
            int beforeQuantity,
            int afterQuantity,
            StockUpsertCommand command,
            UUID actorAppUserId,
            boolean created
    ) {
        int delta = afterQuantity - beforeQuantity;
        if (delta == 0) {
            return;
        }

        InventoryTransactionType transactionType = delta > 0
                ? stockInTransactionType(command)
                : InventoryTransactionType.ADJUSTMENT_OUT;
        String reason = delta > 0
                ? (created ? "Stock batch created" : "Stock batch quantity increased")
                : "Stock batch quantity reduced";
        String notes = stockAuditReference(command, stock);

        InventoryTransactionEntity entity = transactionRepository.save(InventoryTransactionEntity.create(
                tenantId,
                stock.getMedicineId(),
                stock.getId(),
                stock.getLocationId(),
                null,
                transactionType.name(),
                Math.abs(delta),
                beforeQuantity,
                afterQuantity,
                "STOCK_INWARD",
                null,
                actorAppUserId,
                reason,
                notes
        ));
        auditTransaction(tenantId, entity, "inventory.transaction.created", actorAppUserId, "Created inventory transaction");
    }

    private InventoryTransactionType stockInTransactionType(StockUpsertCommand command) {
        return StringUtils.hasText(command.purchaseReferenceNumber())
                ? InventoryTransactionType.PURCHASE
                : InventoryTransactionType.STOCK_IN;
    }

    private String stockAuditReference(StockUpsertCommand command, StockEntity stock) {
        if (StringUtils.hasText(command.purchaseReferenceNumber())) {
            return command.purchaseReferenceNumber().trim();
        }
        if (StringUtils.hasText(stock.getBatchNumber())) {
            return "Batch " + stock.getBatchNumber().trim();
        }
        return "Inventory stock update";
    }

    private void validateMedicine(MedicineUpsertCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (!StringUtils.hasText(command.medicineName())) throw new IllegalArgumentException("medicineName is required");
        if (!StringUtils.hasText(command.medicineType())) throw new IllegalArgumentException("medicineType is required");
    }

    private void validateStock(UUID tenantId, StockUpsertCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        requireId(command.medicineId(), "medicineId");
        if (medicineRepository.findByTenantIdAndId(tenantId, command.medicineId()).isEmpty()) throw new IllegalArgumentException("Medicine not found");
        if (command.quantityOnHand() < 0) throw new IllegalArgumentException("quantityOnHand cannot be negative");
        if (command.quantityReceived() != null && command.quantityReceived() < 0) throw new IllegalArgumentException("quantityReceived cannot be negative");
    }

    private void ensureUniqueStockBatch(UUID tenantId, UUID medicineId, UUID locationId, String batchNumber, UUID id) {
        if (!StringUtils.hasText(batchNumber)) {
            return;
        }
        boolean duplicate = id == null
                ? stockRepository.findByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCase(tenantId, medicineId, locationId, batchNumber).isPresent()
                : stockRepository.existsByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCaseAndIdNot(tenantId, medicineId, locationId, batchNumber, id);
        if (duplicate) {
            throw new IllegalArgumentException("Stock batch already exists for this medicine and location. Edit existing batch or use a different batch number.");
        }
    }

    private void validateTransaction(UUID tenantId, InventoryTransactionCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        requireId(command.medicineId(), "medicineId");
        if (command.transactionType() == null) throw new IllegalArgumentException("transactionType is required");
        if (command.quantity() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (requiresReason(command.transactionType()) && !StringUtils.hasText(command.reason())) {
            throw new IllegalArgumentException("reason is required");
        }
        if (requiresBatch(command.transactionType()) && command.stockBatchId() == null) {
            throw new IllegalArgumentException("stockBatchId is required");
        }
        if (medicineRepository.findByTenantIdAndId(tenantId, command.medicineId()).isEmpty()) throw new IllegalArgumentException("Medicine not found");
    }

    private boolean requiresReason(InventoryTransactionType type) {
        return switch (type) {
            case ADJUSTMENT_IN, ADJUSTMENT_OUT, ADJUSTMENT, RETURN, CUSTOMER_RETURN_IN, CUSTOMER_RETURN_NON_SELLABLE, VENDOR_RETURN_OUT, WRITE_OFF, EXPIRED, DISPENSED, SALE -> true;
            default -> false;
        };
    }

    private boolean requiresBatch(InventoryTransactionType type) {
        return switch (type) {
            case CUSTOMER_RETURN_IN, CUSTOMER_RETURN_NON_SELLABLE, VENDOR_RETURN_OUT, WRITE_OFF, DISPENSED, SALE, RETURN, EXPIRED, ADJUSTMENT_IN, ADJUSTMENT_OUT, ADJUSTMENT -> true;
            default -> false;
        };
    }

    private void ensureUniqueMedicine(UUID tenantId, MedicineUpsertCommand command, UUID id) {
        if (id == null) {
            if (medicineRepository.findByTenantIdAndMedicineNameIgnoreCase(tenantId, command.medicineName()).isPresent()) throw new IllegalArgumentException("Medicine already exists");
            if (StringUtils.hasText(command.barcode()) && medicineRepository.findByTenantIdAndBarcodeIgnoreCase(tenantId, command.barcode()).isPresent()) throw new IllegalArgumentException("Medicine barcode already exists");
            if (StringUtils.hasText(command.externalCode()) && medicineRepository.findByTenantIdAndExternalCodeIgnoreCase(tenantId, command.externalCode()).isPresent()) throw new IllegalArgumentException("Medicine external code already exists");
            return;
        }
        if (medicineRepository.existsByTenantIdAndMedicineNameIgnoreCaseAndIdNot(tenantId, command.medicineName(), id)) throw new IllegalArgumentException("Medicine already exists");
        if (StringUtils.hasText(command.barcode()) && medicineRepository.existsByTenantIdAndBarcodeIgnoreCaseAndIdNot(tenantId, command.barcode(), id)) throw new IllegalArgumentException("Medicine barcode already exists");
        if (StringUtils.hasText(command.externalCode()) && medicineRepository.existsByTenantIdAndExternalCodeIgnoreCaseAndIdNot(tenantId, command.externalCode(), id)) throw new IllegalArgumentException("Medicine external code already exists");
    }

    private String normalize(String value) { return value == null ? null : value.trim(); }
    private String normalizeNullable(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String normalizeType(String value) { return normalize(value).toUpperCase(Locale.ROOT); }
    private BigDecimal normalizeMoney(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
    private int effectiveThreshold(StockEntity stock) { return stock.getLowStockThreshold() == null ? 5 : Math.max(0, stock.getLowStockThreshold()); }
    private LocalDate today() { return LocalDate.now(); }

    private UUID resolveLocationId(UUID tenantId, UUID requestedLocationId) {
        if (requestedLocationId != null) {
            return locationRepository.findByTenantIdAndId(tenantId, requestedLocationId)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"))
                    .getId();
        }
        return ensureDefaultLocation(tenantId).getId();
    }

    private InventoryLocationEntity ensureDefaultLocation(UUID tenantId) {
        return locationRepository.findByTenantIdAndDefaultLocationTrue(tenantId)
                .orElseGet(() -> locationRepository.findByTenantIdAndLocationNameIgnoreCase(tenantId, "Main Pharmacy")
                        .map(existing -> {
                            if (!existing.isDefaultLocation()) {
                                existing.update(existing.getLocationName(), existing.getLocationCode(), existing.getLocationType(), true, existing.isActive());
                                return locationRepository.save(existing);
                            }
                            return existing;
                        })
                        .orElseGet(() -> locationRepository.save(InventoryLocationEntity.create(tenantId, "Main Pharmacy", "MAIN_PHARMACY", "PHARMACY", true))));
    }

    private void auditMedicine(UUID tenantId, MedicineEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, MEDICINE_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditStock(UUID tenantId, StockEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, STOCK_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditTransaction(UUID tenantId, InventoryTransactionEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, TRANSACTION_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private IllegalArgumentException translateStockIntegrityViolation(DataIntegrityViolationException ex) {
        if (ex != null && ex.getMessage() != null && ex.getMessage().contains("uq_inventory_stocks_tenant_medicine_location_batch")) {
            return new IllegalArgumentException("Stock batch already exists for this medicine and location. Edit existing batch or use a different batch number.", ex);
        }
        return new IllegalArgumentException("Stock could not be saved", ex);
    }

    private String detailsJson(Object entity) {
        try { return objectMapper.writeValueAsString(entity); }
        catch (JsonProcessingException ex) { return "{}"; }
    }

    private void requireTenant(UUID tenantId) { if (tenantId == null) throw new IllegalArgumentException("tenantId is required"); }
    private void requireId(UUID id, String field) { if (id == null) throw new IllegalArgumentException(field + " is required"); }
    private boolean isExpired(StockEntity stock) { return stock != null && stock.getExpiryDate() != null && stock.getExpiryDate().isBefore(today()); }
}
