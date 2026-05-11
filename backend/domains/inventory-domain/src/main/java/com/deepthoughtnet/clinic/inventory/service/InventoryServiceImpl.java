package com.deepthoughtnet.clinic.inventory.service;

import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
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
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public InventoryServiceImpl(
            MedicineRepository medicineRepository,
            StockRepository stockRepository,
            InventoryTransactionRepository transactionRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.medicineRepository = medicineRepository;
        this.stockRepository = stockRepository;
        this.transactionRepository = transactionRepository;
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
        ensureUniqueMedicineName(tenantId, command.medicineName(), null);
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
        ensureUniqueMedicineName(tenantId, command.medicineName(), id);
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
        StockEntity entity = StockEntity.create(tenantId, command.medicineId());
        applyStock(entity, command);
        StockEntity saved = stockRepository.save(entity);
        auditStock(tenantId, saved, "stock.created", actorAppUserId, "Created stock batch");
        return toRecord(saved, medicineRepository.findByTenantIdAndId(tenantId, saved.getMedicineId()).orElse(null));
    }

    @Override
    @Transactional
    public StockRecord updateStock(UUID tenantId, UUID id, StockUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validateStock(tenantId, command);
        StockEntity entity = stockRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));
        applyStock(entity, command);
        StockEntity saved = stockRepository.save(entity);
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

        int delta = Math.abs(command.quantity());
        if (stock != null) {
            int current = stock.getQuantityOnHand();
            int nextQuantity = switch (normalizeTransactionType(command.transactionType())) {
                case DISPENSED, ADJUSTMENT_OUT, EXPIRED, SALE -> current - delta;
                case ADJUSTMENT -> current + delta;
                default -> current + delta;
            };
            if (nextQuantity < 0) {
                throw new IllegalArgumentException("quantityAvailable cannot go negative");
            }
            stock.setQuantityOnHand(nextQuantity);
            stockRepository.save(stock);
        }

        InventoryTransactionEntity entity = transactionRepository.save(InventoryTransactionEntity.create(
                tenantId,
                medicine.getId(),
                stock == null ? null : stock.getId(),
                command.transactionType().name(),
                delta,
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
                .filter(stock -> stock.expiryDate() != null && stock.expiryDate().isBefore(LocalDate.now().plusDays(1)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRecord> listExpiringStocks(UUID tenantId, int days) {
        LocalDate end = LocalDate.now().plusDays(Math.max(days, 1));
        return listStocks(tenantId).stream()
                .filter(stock -> stock.expiryDate() != null && !stock.expiryDate().isBefore(LocalDate.now()) && !stock.expiryDate().isAfter(end))
                .toList();
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
        entity.update(entity.getMedicineName(), entity.getMedicineType(), entity.getGenericName(), entity.getBrandName(), entity.getCategory(), entity.getDosageForm(), entity.getStrength(), entity.getUnit(), entity.getManufacturer(), entity.getDefaultDosage(), entity.getDefaultFrequency(), entity.getDefaultDurationDays(), entity.getDefaultTiming(), entity.getDefaultInstructions(), entity.getDefaultPrice(), entity.getTaxRate(), active);
        MedicineEntity saved = medicineRepository.save(entity);
        auditMedicine(tenantId, saved, active ? "medicine.activated" : "medicine.deactivated", actorAppUserId, active ? "Activated medicine" : "Deactivated medicine");
        return toRecord(saved);
    }

    private MedicineRecord toRecord(MedicineEntity entity) {
        return new MedicineRecord(entity.getId(), entity.getTenantId(), entity.getMedicineName(), entity.getMedicineType(), entity.getGenericName(), entity.getBrandName(), entity.getCategory(), entity.getDosageForm(), entity.getStrength(), entity.getUnit(), entity.getManufacturer(), entity.getDefaultDosage(), entity.getDefaultFrequency(), entity.getDefaultDurationDays(), entity.getDefaultTiming(), entity.getDefaultInstructions(), entity.getDefaultPrice(), entity.getTaxRate(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private StockRecord toRecord(StockEntity stock, MedicineEntity medicine) {
        return new StockRecord(stock.getId(), stock.getTenantId(), stock.getMedicineId(), medicine == null ? null : medicine.getMedicineName(), medicine == null ? null : medicine.getMedicineType(), stock.getBatchNumber(), stock.getExpiryDate(), stock.getPurchaseDate(), stock.getSupplierName(), stock.getQuantityReceived(), stock.getQuantityOnHand(), stock.getLowStockThreshold(), stock.getUnitCost(), stock.getPurchasePrice(), stock.getSellingPrice(), stock.isActive(), stock.getCreatedAt(), stock.getUpdatedAt());
    }

    private LowStockRecord toLowStockRecord(StockEntity stock, MedicineEntity medicine) {
        return new LowStockRecord(stock.getId(), stock.getMedicineId(), medicine == null ? null : medicine.getMedicineName(), medicine == null ? null : medicine.getMedicineType(), stock.getBatchNumber(), stock.getExpiryDate(), stock.getQuantityOnHand(), stock.getLowStockThreshold(), stock.getSellingPrice());
    }

    private InventoryTransactionRecord toRecord(InventoryTransactionEntity entity) {
        return new InventoryTransactionRecord(entity.getId(), entity.getTenantId(), entity.getMedicineId(), entity.getStockBatchId(), InventoryTransactionType.valueOf(entity.getTransactionType()), entity.getQuantity(), entity.getReason(), entity.getReferenceType(), entity.getReferenceId(), entity.getCreatedBy(), entity.getNotes(), entity.getCreatedAt());
    }

    private List<StockRecord> mapStocks(UUID tenantId, List<StockEntity> stocks) {
        Map<UUID, MedicineEntity> medicines = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .collect(Collectors.toMap(MedicineEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return stocks.stream().map(stock -> toRecord(stock, medicines.get(stock.getMedicineId()))).toList();
    }

    private void applyMedicine(MedicineEntity entity, MedicineUpsertCommand command) {
        entity.update(normalize(command.medicineName()), normalizeType(command.medicineType()), normalizeNullable(command.genericName()), normalizeNullable(command.brandName()), normalizeNullable(command.category()), normalizeNullable(command.dosageForm()), normalizeNullable(command.strength()), normalizeNullable(command.unit()), normalizeNullable(command.manufacturer()), normalizeNullable(command.defaultDosage()), normalizeNullable(command.defaultFrequency()), command.defaultDurationDays(), normalizeNullable(command.defaultTiming()), normalizeNullable(command.defaultInstructions()), normalizeMoney(command.defaultPrice()), normalizeMoney(command.taxRate()), command.active());
    }

    private void applyStock(StockEntity entity, StockUpsertCommand command) {
        entity.update(normalizeNullable(command.batchNumber()), command.expiryDate(), command.purchaseDate(), normalizeNullable(command.supplierName()), command.quantityReceived() == null ? Math.max(0, command.quantityOnHand()) : Math.max(0, command.quantityReceived()), Math.max(0, command.quantityOnHand()), command.lowStockThreshold(), normalizeMoney(command.unitCost()), normalizeMoney(command.purchasePrice()), normalizeMoney(command.sellingPrice()), command.active());
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
    }

    private void validateTransaction(UUID tenantId, InventoryTransactionCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        requireId(command.medicineId(), "medicineId");
        if (command.transactionType() == null) throw new IllegalArgumentException("transactionType is required");
        if (command.quantity() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if ((command.transactionType() == InventoryTransactionType.ADJUSTMENT_IN || command.transactionType() == InventoryTransactionType.ADJUSTMENT_OUT || command.transactionType() == InventoryTransactionType.ADJUSTMENT) && !StringUtils.hasText(command.reason())) {
            throw new IllegalArgumentException("reason is required for adjustment");
        }
        if (medicineRepository.findByTenantIdAndId(tenantId, command.medicineId()).isEmpty()) throw new IllegalArgumentException("Medicine not found");
    }

    private void ensureUniqueMedicineName(UUID tenantId, String medicineName, UUID id) {
        if (id == null) {
            if (medicineRepository.findByTenantIdAndMedicineNameIgnoreCase(tenantId, medicineName).isPresent()) throw new IllegalArgumentException("Medicine already exists");
            return;
        }
        if (medicineRepository.existsByTenantIdAndMedicineNameIgnoreCaseAndIdNot(tenantId, medicineName, id)) throw new IllegalArgumentException("Medicine already exists");
    }

    private String normalize(String value) { return value == null ? null : value.trim(); }
    private String normalizeNullable(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String normalizeType(String value) { return normalize(value).toUpperCase(Locale.ROOT); }
    private BigDecimal normalizeMoney(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
    private int effectiveThreshold(StockEntity stock) { return stock.getLowStockThreshold() == null ? 5 : Math.max(0, stock.getLowStockThreshold()); }

    private void auditMedicine(UUID tenantId, MedicineEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, MEDICINE_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditStock(UUID tenantId, StockEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, STOCK_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private void auditTransaction(UUID tenantId, InventoryTransactionEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(tenantId, TRANSACTION_ENTITY, entity.getId(), action, actorAppUserId, OffsetDateTime.now(), message, detailsJson(entity)));
    }

    private String detailsJson(Object entity) {
        try { return objectMapper.writeValueAsString(entity); }
        catch (JsonProcessingException ex) { return "{}"; }
    }

    private void requireTenant(UUID tenantId) { if (tenantId == null) throw new IllegalArgumentException("tenantId is required"); }
    private void requireId(UUID id, String field) { if (id == null) throw new IllegalArgumentException(field + " is required"); }
}
