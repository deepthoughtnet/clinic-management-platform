package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<StockEntity, UUID> {
    List<StockEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    Optional<StockEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<StockEntity> findByTenantIdAndMedicineId(UUID tenantId, UUID medicineId);
    List<StockEntity> findByTenantIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(UUID tenantId, UUID medicineId, int quantityOnHand);
}
