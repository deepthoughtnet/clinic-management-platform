package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineRepository extends JpaRepository<MedicineEntity, UUID> {
    List<MedicineEntity> findByTenantIdOrderByMedicineNameAsc(UUID tenantId);
    Optional<MedicineEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<MedicineEntity> findByTenantIdAndMedicineNameIgnoreCase(UUID tenantId, String medicineName);
    boolean existsByTenantIdAndMedicineNameIgnoreCaseAndIdNot(UUID tenantId, String medicineName, UUID id);
    Optional<MedicineEntity> findByTenantIdAndBarcodeIgnoreCase(UUID tenantId, String barcode);
    Optional<MedicineEntity> findByTenantIdAndQrCodeIgnoreCase(UUID tenantId, String qrCode);
    Optional<MedicineEntity> findByTenantIdAndExternalCodeIgnoreCase(UUID tenantId, String externalCode);
    boolean existsByTenantIdAndBarcodeIgnoreCaseAndIdNot(UUID tenantId, String barcode, UUID id);
    boolean existsByTenantIdAndQrCodeIgnoreCaseAndIdNot(UUID tenantId, String qrCode, UUID id);
    boolean existsByTenantIdAndExternalCodeIgnoreCaseAndIdNot(UUID tenantId, String externalCode, UUID id);
}
