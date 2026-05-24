package com.deepthoughtnet.clinic.api.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.inventory.service.PrescriptionDispensingService;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationRepository;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderRepository;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierInvoiceRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PharmacyOperationsServiceImportTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private InventoryService inventoryService;
    private MedicineRepository medicineRepository;
    private PharmacyOperationsService service;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        medicineRepository = mock(MedicineRepository.class);
        StockRepository stockRepository = mock(StockRepository.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PharmacyReconciliationRepository reconciliationRepository = mock(PharmacyReconciliationRepository.class);
        InventoryLocationRepository locationRepository = mock(InventoryLocationRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        SupplierInvoiceRepository supplierInvoiceRepository = mock(SupplierInvoiceRepository.class);
        GoodsReceiptRepository goodsReceiptRepository = mock(GoodsReceiptRepository.class);
        PrescriptionDispensingService dispensingService = mock(PrescriptionDispensingService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OcrProvider> ocrProvider = mock(ObjectProvider.class);
        when(ocrProvider.getIfAvailable()).thenReturn(null);
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(TENANT_ID)).thenReturn(List.of());
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(OTHER_TENANT_ID)).thenReturn(List.of());

        service = new PharmacyOperationsService(
                inventoryService,
                medicineRepository,
                stockRepository,
                supplierRepository,
                reconciliationRepository,
                locationRepository,
                purchaseOrderRepository,
                supplierInvoiceRepository,
                goodsReceiptRepository,
                dispensingService,
                mock(AuditEventPublisher.class),
                storageService,
                ocrProvider,
                new ObjectMapper(),
                new NoOpTransactionManager()
        );
    }

    @Test
    void validCsvImportsSuccessfully() {
        when(inventoryService.createMedicine(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(medicineRecord("Paracetamol 650"));

        MedicineImportResult result = service.importMedicines(TENANT_ID, csv(
                "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Micro Labs,PARA-650,,PARA-650,25.00,5,true"
        ), ACTOR_ID);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("CREATED");
            assertThat(row.message()).isEqualTo("Created medicine");
        });
    }

    @Test
    void duplicateMedicineRowIsSkippedWithoutCrash() {
        MedicineEntity existing = MedicineEntity.create(TENANT_ID, "Paracetamol 650", "TABLET");
        existing.update("Paracetamol 650", "TABLET", "PARA-650", null, "PARA-650", "Paracetamol", "Dolo", "Analgesic", "Tablet", "650", "mg", "Micro Labs", "1 tablet", "Twice daily", 5, "AFTER_FOOD", "Take after meals", new BigDecimal("25.00"), new BigDecimal("5.00"), true);
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(TENANT_ID)).thenReturn(List.of(existing));

        MedicineImportResult result = service.importMedicines(TENANT_ID, csv(
                "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Micro Labs,PARA-650,,PARA-650,25.00,5,true"
        ), ACTOR_ID);

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("SKIPPED");
            assertThat(row.message()).isEqualTo("Medicine already exists");
        });
        verify(inventoryService, never()).createMedicine(eq(TENANT_ID), any(), eq(ACTOR_ID));
    }

    @Test
    void invalidPriceDoesNotCrashImport() {
        MedicineImportResult result = service.importMedicines(TENANT_ID, csv(
                "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Micro Labs,PARA-650,,PARA-650,not-a-price,5,true"
        ), ACTOR_ID);

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("FAILED");
            assertThat(row.message()).isEqualTo("defaultPrice must be numeric");
        });
    }

    @Test
    void mixedValidAndInvalidRowsReturnPartialResultWithoutRollbackFailure() {
        when(inventoryService.createMedicine(eq(TENANT_ID), any(), eq(ACTOR_ID)))
                .thenReturn(medicineRecord("Paracetamol 650"))
                .thenReturn(medicineRecord("Cetirizine 10"));

        MedicineImportResult result = service.importMedicines(TENANT_ID, csv(
                "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Micro Labs,PARA-650,,PARA-650,25.00,5,true",
                "Ibuprofen 400,Ibuprofen,Brufen,Analgesic,Tablet,400,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Abbott,IBU-400,,IBU-400,bad-price,5,true",
                "Cetirizine 10,Cetirizine,Cetzine,Antihistamine,Tablet,10,mg,1 tablet,Daily,3,AFTER_FOOD,Take after meals,Sun,CTZ-10,,CTZ-10,12.00,5,true"
        ), ACTOR_ID);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedRowsCsv()).contains("Ibuprofen 400");
    }

    @Test
    void createFailureDoesNotStopLaterRows() {
        when(inventoryService.createMedicine(eq(TENANT_ID), any(), eq(ACTOR_ID)))
                .thenThrow(new IllegalArgumentException("Medicine save failed"))
                .thenReturn(medicineRecord("Cetirizine 10"));

        MedicineImportResult result = service.importMedicines(TENANT_ID, csv(
                "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Micro Labs,PARA-650,,PARA-650,25.00,5,true",
                "Cetirizine 10,Cetirizine,Cetzine,Antihistamine,Tablet,10,mg,1 tablet,Daily,3,AFTER_FOOD,Take after meals,Sun,CTZ-10,,CTZ-10,12.00,5,true"
        ), ACTOR_ID);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.rows().get(0).message()).isEqualTo("Medicine save failed");
        assertThat(result.rows().get(1).status()).isEqualTo("CREATED");
    }

    @Test
    void tenantIsolationIsPreservedForDuplicateChecks() {
        MedicineEntity otherTenantMedicine = MedicineEntity.create(OTHER_TENANT_ID, "Paracetamol 650", "TABLET");
        otherTenantMedicine.update("Paracetamol 650", "TABLET", "PARA-650", null, "PARA-650", "Paracetamol", "Dolo", "Analgesic", "Tablet", "650", "mg", "Micro Labs", "1 tablet", "Twice daily", 5, "AFTER_FOOD", "Take after meals", new BigDecimal("25.00"), new BigDecimal("5.00"), true);
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(OTHER_TENANT_ID)).thenReturn(List.of(otherTenantMedicine));
        when(inventoryService.createMedicine(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(medicineRecord("Paracetamol 650"));

        MedicineImportResult result = service.importMedicines(TENANT_ID, csv(
                "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Micro Labs,PARA-650,,PARA-650,25.00,5,true"
        ), ACTOR_ID);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
    }

    private byte[] csv(String... rows) {
        String header = "medicineName,genericName,brandName,category,type,strength,unit,defaultDosage,defaultFrequency,defaultDurationDays,defaultTiming,instructions,manufacturer,barcode,qrCode,externalCode,defaultPrice,taxPercent,active";
        return String.join("\n", header, String.join("\n", rows)).getBytes();
    }

    private MedicineRecord medicineRecord(String medicineName) {
        OffsetDateTime now = OffsetDateTime.now();
        return new MedicineRecord(UUID.randomUUID(), TENANT_ID, medicineName, "TABLET", null, null, null, null, null, null, "Tablet", "650", "mg", null, null, null, null, null, null, new BigDecimal("25.00"), new BigDecimal("5.00"), true, now, now);
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}
