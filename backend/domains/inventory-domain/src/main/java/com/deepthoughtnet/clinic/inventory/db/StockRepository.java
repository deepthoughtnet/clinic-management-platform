package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<StockEntity, UUID> {
    List<StockEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    List<StockEntity> findByTenantIdAndLocationIdOrderByUpdatedAtDesc(UUID tenantId, UUID locationId);
    Optional<StockEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<StockEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
    List<StockEntity> findByTenantIdAndMedicineId(UUID tenantId, UUID medicineId);
    List<StockEntity> findByTenantIdAndMedicineIdAndLocationId(UUID tenantId, UUID medicineId, UUID locationId);
    List<StockEntity> findByTenantIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(UUID tenantId, UUID medicineId, int quantityOnHand);
    List<StockEntity> findByTenantIdAndLocationIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(UUID tenantId, UUID locationId, UUID medicineId, int quantityOnHand);
    Optional<StockEntity> findByTenantIdAndBarcodeIgnoreCase(UUID tenantId, String barcode);
    Optional<StockEntity> findByTenantIdAndQrCodeIgnoreCase(UUID tenantId, String qrCode);
    Optional<StockEntity> findByTenantIdAndExternalCodeIgnoreCase(UUID tenantId, String externalCode);
    Optional<StockEntity> findByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCase(UUID tenantId, UUID medicineId, UUID locationId, String batchNumber);
    Optional<StockEntity> findByTenantIdAndLocationIdAndBatchNumberIgnoreCase(UUID tenantId, UUID locationId, String batchNumber);
    Optional<StockEntity> findByTenantIdAndLocationIdAndPurchaseReferenceNumberIgnoreCase(UUID tenantId, UUID locationId, String purchaseReferenceNumber);
    boolean existsByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCaseAndIdNot(UUID tenantId, UUID medicineId, UUID locationId, String batchNumber, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select stock
            from StockEntity stock
            where stock.tenantId = :tenantId
              and stock.locationId = :locationId
              and stock.medicineId = :medicineId
              and stock.active = true
              and stock.quantityOnHand > 0
            order by
              case when stock.expiryDate is null then 1 else 0 end asc,
              stock.expiryDate asc,
              stock.updatedAt asc
            """)
    List<StockEntity> findSellableBatchesForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("locationId") UUID locationId,
            @Param("medicineId") UUID medicineId
    );
}
