package com.deepthoughtnet.clinic.api.inventory;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptEntity;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InventoryTransactionViewMapper {
    private final StockRepository stockRepository;
    private final AppUserRepository appUserRepository;
    private final PharmacySaleRepository saleRepository;
    private final PharmacySaleReturnRepository saleReturnRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PharmacyReconciliationRepository reconciliationRepository;

    public InventoryTransactionViewMapper(
            StockRepository stockRepository,
            AppUserRepository appUserRepository,
            PharmacySaleRepository saleRepository,
            PharmacySaleReturnRepository saleReturnRepository,
            GoodsReceiptRepository goodsReceiptRepository,
            PharmacyReconciliationRepository reconciliationRepository
    ) {
        this.stockRepository = stockRepository;
        this.appUserRepository = appUserRepository;
        this.saleRepository = saleRepository;
        this.saleReturnRepository = saleReturnRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.reconciliationRepository = reconciliationRepository;
    }

    public List<InventoryTransactionResponse> toResponses(UUID tenantId, List<InventoryTransactionRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, StockEntity> stockById = stockRepository.findByTenantIdAndIdIn(tenantId, collectIds(rows, InventoryTransactionRecord::stockBatchId))
                .stream()
                .collect(Collectors.toMap(StockEntity::getId, Function.identity()));

        Map<UUID, AppUserEntity> userById = appUserRepository.findByTenantIdAndIdIn(tenantId, new java.util.ArrayList<>(collectIds(rows, InventoryTransactionRecord::createdBy)))
                .stream()
                .collect(Collectors.toMap(AppUserEntity::getId, Function.identity()));

        Map<UUID, PharmacySaleEntity> saleById = saleRepository.findByTenantIdAndIdIn(tenantId, referenceIds(rows, "PHARMACY_SALE"))
                .stream()
                .collect(Collectors.toMap(PharmacySaleEntity::getId, Function.identity()));

        Map<UUID, GoodsReceiptEntity> goodsReceiptById = goodsReceiptRepository.findByTenantIdAndIdIn(tenantId, referenceIds(rows, "GRN"))
                .stream()
                .collect(Collectors.toMap(GoodsReceiptEntity::getId, Function.identity()));

        Map<UUID, PharmacyReconciliationEntity> reconciliationById = reconciliationRepository.findByTenantIdAndIdIn(tenantId, referenceIds(rows, "RECONCILIATION"))
                .stream()
                .collect(Collectors.toMap(PharmacyReconciliationEntity::getId, Function.identity()));

        Map<UUID, String> returnNumberBySaleId = saleReturnRepository.findByTenantIdAndSaleIdInOrderByCreatedAtDesc(tenantId, referenceIds(rows, "PHARMACY_SALE_RETURN"))
                .stream()
                .collect(Collectors.toMap(PharmacySaleReturnEntity::getSaleId, PharmacySaleReturnEntity::getReturnNumber, (first, ignored) -> first, LinkedHashMap::new));

        return rows.stream()
                .map(row -> toResponse(row, stockById.get(row.stockBatchId()), userById.get(row.createdBy()), saleById, returnNumberBySaleId, goodsReceiptById, reconciliationById))
                .toList();
    }

    private InventoryTransactionResponse toResponse(
            InventoryTransactionRecord row,
            StockEntity stock,
            AppUserEntity user,
            Map<UUID, PharmacySaleEntity> saleById,
            Map<UUID, String> returnNumberBySaleId,
            Map<UUID, GoodsReceiptEntity> goodsReceiptById,
            Map<UUID, PharmacyReconciliationEntity> reconciliationById
    ) {
        return new InventoryTransactionResponse(
                row.id(),
                row.tenantId(),
                row.medicineId(),
                row.stockBatchId(),
                row.locationId(),
                row.targetLocationId(),
                row.transactionType(),
                row.quantity(),
                row.beforeQuantity(),
                row.afterQuantity(),
                row.reason(),
                row.referenceType(),
                row.referenceId(),
                row.createdBy(),
                row.notes(),
                row.createdAt(),
                stock == null ? null : stock.getBatchNumber(),
                displayName(user),
                businessReference(row, saleById, returnNumberBySaleId, goodsReceiptById, reconciliationById)
        );
    }

    private String businessReference(
            InventoryTransactionRecord row,
            Map<UUID, PharmacySaleEntity> saleById,
            Map<UUID, String> returnNumberBySaleId,
            Map<UUID, GoodsReceiptEntity> goodsReceiptById,
            Map<UUID, PharmacyReconciliationEntity> reconciliationById
    ) {
        String referenceType = normalize(row.referenceType());
        if (!StringUtils.hasText(referenceType)) {
            return sanitizeNotes(row.notes());
        }

        UUID referenceId = row.referenceId();
        return switch (referenceType) {
            case "PHARMACY_SALE" -> labeled("Sale", referenceId == null ? null : saleNumber(saleById.get(referenceId)));
            case "PHARMACY_SALE_RETURN" -> labeled("Return", referenceId == null ? null : returnNumberBySaleId.get(referenceId));
            case "GRN" -> labeled("GRN", referenceId == null ? null : receiptNumber(goodsReceiptById.get(referenceId)));
            case "RECONCILIATION" -> labeled("Reconciliation", referenceId == null ? null : reconciliationLabel(reconciliationById.get(referenceId)));
            case "PRESCRIPTION" -> "Prescription dispensing";
            case "STOCK_INWARD" -> sanitizeNotes(row.notes());
            default -> StringUtils.hasText(sanitizeNotes(row.notes())) ? sanitizeNotes(row.notes()) : prettifyReferenceType(referenceType);
        };
    }

    private Set<UUID> collectIds(List<InventoryTransactionRecord> rows, Function<InventoryTransactionRecord, UUID> extractor) {
        return rows.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<UUID> referenceIds(List<InventoryTransactionRecord> rows, String referenceType) {
        return rows.stream()
                .filter(row -> referenceType.equalsIgnoreCase(normalize(row.referenceType())))
                .map(InventoryTransactionRecord::referenceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String displayName(AppUserEntity user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName().trim();
        }
        if (StringUtils.hasText(user.getEmail())) {
            return user.getEmail().trim();
        }
        return null;
    }

    private String labeled(String prefix, String value) {
        if (StringUtils.hasText(value)) {
            return prefix + " " + value.trim();
        }
        return prefix;
    }

    private String saleNumber(PharmacySaleEntity sale) {
        return sale == null ? null : sale.getSaleNumber();
    }

    private String receiptNumber(GoodsReceiptEntity receipt) {
        return receipt == null ? null : receipt.getReceiptNumber();
    }

    private String reconciliationLabel(PharmacyReconciliationEntity reconciliation) {
        if (reconciliation == null) {
            return null;
        }
        if (StringUtils.hasText(reconciliation.getSheetFilename())) {
            return reconciliation.getSheetFilename().trim();
        }
        if (StringUtils.hasText(reconciliation.getReason())) {
            return reconciliation.getReason().trim();
        }
        return null;
    }

    private String sanitizeNotes(String notes) {
        if (!StringUtils.hasText(notes)) {
            return null;
        }
        String value = notes.trim();
        return isUuid(value) ? null : value;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String prettifyReferenceType(String referenceType) {
        String[] parts = referenceType.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            String normalized = part.toLowerCase();
            builder.append(Character.toUpperCase(normalized.charAt(0)));
            if (normalized.length() > 1) {
                builder.append(normalized.substring(1));
            }
        }
        return builder.toString();
    }
}
