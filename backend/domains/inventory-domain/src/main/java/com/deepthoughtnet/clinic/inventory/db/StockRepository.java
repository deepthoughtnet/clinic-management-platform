package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<StockEntity, UUID> {
    List<StockEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    List<StockEntity> findByTenantIdAndLocationIdOrderByUpdatedAtDesc(UUID tenantId, UUID locationId);
    Optional<StockEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<StockEntity> findByTenantIdAndMedicineId(UUID tenantId, UUID medicineId);
    List<StockEntity> findByTenantIdAndMedicineIdAndLocationId(UUID tenantId, UUID medicineId, UUID locationId);
    List<StockEntity> findByTenantIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(UUID tenantId, UUID medicineId, int quantityOnHand);
    List<StockEntity> findByTenantIdAndLocationIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(UUID tenantId, UUID locationId, UUID medicineId, int quantityOnHand);
    Optional<StockEntity> findByTenantIdAndBarcodeIgnoreCase(UUID tenantId, String barcode);
    Optional<StockEntity> findByTenantIdAndQrCodeIgnoreCase(UUID tenantId, String qrCode);
    Optional<StockEntity> findByTenantIdAndExternalCodeIgnoreCase(UUID tenantId, String externalCode);
    Optional<StockEntity> findByTenantIdAndLocationIdAndBatchNumberIgnoreCase(UUID tenantId, UUID locationId, String batchNumber);
    Optional<StockEntity> findByTenantIdAndLocationIdAndPurchaseReferenceNumberIgnoreCase(UUID tenantId, UUID locationId, String purchaseReferenceNumber);
    boolean existsByTenantIdAndBarcodeIgnoreCaseAndIdNot(UUID tenantId, String barcode, UUID id);
    boolean existsByTenantIdAndQrCodeIgnoreCaseAndIdNot(UUID tenantId, String qrCode, UUID id);
    boolean existsByTenantIdAndExternalCodeIgnoreCaseAndIdNot(UUID tenantId, String externalCode, UUID id);
}
