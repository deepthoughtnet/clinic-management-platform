package com.deepthoughtnet.clinic.inventory.service;

import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.LowStockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockUpsertCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryService {
    List<MedicineRecord> listMedicines(UUID tenantId);
    Optional<MedicineRecord> findMedicine(UUID tenantId, UUID id);
    MedicineRecord createMedicine(UUID tenantId, MedicineUpsertCommand command, UUID actorAppUserId);
    MedicineRecord updateMedicine(UUID tenantId, UUID id, MedicineUpsertCommand command, UUID actorAppUserId);
    MedicineRecord deactivateMedicine(UUID tenantId, UUID id, UUID actorAppUserId);

    List<StockRecord> listStocks(UUID tenantId);
    Optional<StockRecord> findStock(UUID tenantId, UUID id);
    StockRecord createStock(UUID tenantId, StockUpsertCommand command, UUID actorAppUserId);
    StockRecord updateStock(UUID tenantId, UUID id, StockUpsertCommand command, UUID actorAppUserId);

    List<InventoryTransactionRecord> listTransactions(UUID tenantId);
    InventoryTransactionRecord createTransaction(UUID tenantId, InventoryTransactionCommand command, UUID actorAppUserId);

    List<LowStockRecord> listLowStock(UUID tenantId);
}
