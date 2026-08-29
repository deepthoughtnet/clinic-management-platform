package com.deepthoughtnet.clinic.api.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.inventory.service.PrescriptionDispensingService;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationRepository;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderEntity;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderRepository;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierEntity;
import com.deepthoughtnet.clinic.inventory.db.SupplierRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierInvoiceRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.PDDocument;

class PharmacyOperationsServicePurchaseOrderTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID MEDICINE_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private PurchaseOrderRepository purchaseOrderRepository;
    private SupplierRepository supplierRepository;
    private MedicineRepository medicineRepository;
    private PharmacyOperationsService service;
    private NotificationHistoryService notificationHistoryService;
    private SupplierEntity supplier;
    private final AtomicReference<PurchaseOrderEntity> savedPurchaseOrder = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        savedPurchaseOrder.set(null);
        InventoryService inventoryService = mock(InventoryService.class);
        medicineRepository = mock(MedicineRepository.class);
        StockRepository stockRepository = mock(StockRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        PharmacyReconciliationRepository reconciliationRepository = mock(PharmacyReconciliationRepository.class);
        InventoryLocationRepository locationRepository = mock(InventoryLocationRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        SupplierInvoiceRepository supplierInvoiceRepository = mock(SupplierInvoiceRepository.class);
        GoodsReceiptRepository goodsReceiptRepository = mock(GoodsReceiptRepository.class);
        PrescriptionDispensingService dispensingService = mock(PrescriptionDispensingService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        PlatformTenantManagementService tenantManagementService = mock(PlatformTenantManagementService.class);
        notificationHistoryService = mock(NotificationHistoryService.class);
        NotificationProvider notificationProvider = mock(NotificationProvider.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OcrProvider> ocrProvider = mock(ObjectProvider.class);
        when(ocrProvider.getIfAvailable()).thenReturn(null);
        when(clinicTimeZoneResolver.resolve(TENANT_ID)).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(tenantManagementService.get(TENANT_ID)).thenReturn(new PlatformTenantRecord(
                TENANT_ID,
                "uat-clinic",
                "UAT Clinic",
                "ENTERPRISE",
                "ACTIVE",
                true,
                new TenantModulesRecord(true, false, false, false, false, false, false, false, false, false),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
        when(notificationHistoryService.queue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(notificationHistoryRecord());
        when(notificationHistoryService.markSent(any(), any())).thenReturn(notificationHistoryRecord());
        when(notificationHistoryService.markFailed(any(), any(), any())).thenReturn(notificationHistoryRecord());

        supplier = mock(SupplierEntity.class);
        when(supplier.getId()).thenReturn(SUPPLIER_ID);
        when(supplier.getSupplierName()).thenReturn("Acme Pharma");
        when(supplier.getEmail()).thenReturn("supplier@example.com");
        when(supplier.getContactPerson()).thenReturn("Supplier Contact");
        when(supplier.getPhone()).thenReturn("9876543210");
        when(supplier.getGstNumber()).thenReturn("22AAAAA0000A1Z5");
        when(supplier.getAddress()).thenReturn("Acme Street, Pune");
        when(supplier.isActive()).thenReturn(true);
        when(supplierRepository.findByTenantIdAndId(TENANT_ID, SUPPLIER_ID)).thenReturn(Optional.of(supplier));

        MedicineEntity medicine = mock(MedicineEntity.class);
        when(medicine.getId()).thenReturn(MEDICINE_ID);
        when(medicine.isActive()).thenReturn(true);
        when(medicine.getMedicineName()).thenReturn("Paracetamol 500");
        when(medicineRepository.findByTenantIdAndId(TENANT_ID, MEDICINE_ID)).thenReturn(Optional.of(medicine));

        when(purchaseOrderRepository.save(any())).thenAnswer(invocation -> {
            PurchaseOrderEntity entity = invocation.getArgument(0);
            savedPurchaseOrder.set(entity);
            return entity;
        });
        when(purchaseOrderRepository.findByTenantIdAndPoNumberIgnoreCase(eq(TENANT_ID), any())).thenAnswer(invocation -> {
            String poNumber = invocation.getArgument(1);
            PurchaseOrderEntity entity = savedPurchaseOrder.get();
            return entity != null && entity.getPoNumber().equalsIgnoreCase(poNumber)
                    ? Optional.of(entity)
                    : Optional.empty();
        });
        when(purchaseOrderRepository.findByTenantIdAndId(eq(TENANT_ID), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(1);
            PurchaseOrderEntity entity = savedPurchaseOrder.get();
            return entity != null && entity.getId().equals(id)
                    ? Optional.of(entity)
                    : Optional.empty();
        });

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
                auditEventPublisher,
                tenantManagementService,
                notificationHistoryService,
                notificationProvider,
                storageService,
                ocrProvider,
                new ObjectMapper(),
                clinicTimeZoneResolver,
                new NoOpTransactionManager()
        );
    }

    @Test
    void savePurchaseOrderPersistsLineItemsAndDetailReloadUsesStoredItemsJson() {
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                SUPPLIER_ID,
                "PO-2026-000001",
                "2026-07-01",
                "2026-07-10",
                List.of(new ProcurementLineRequest(
                        MEDICINE_ID,
                        "Paracetamol 500",
                        12,
                        new BigDecimal("1.25"),
                        new BigDecimal("1.25"),
                        new BigDecimal("12"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("25.00")
                )),
                "DRAFT",
                "UAT first pharmacy purchase order"
        );

        PurchaseOrderRecord saved = service.savePurchaseOrder(TENANT_ID, request, ACTOR_ID);
        PurchaseOrderRecord reloaded = service.getPurchaseOrder(TENANT_ID, saved.id());

        assertThat(saved.poNumber()).isEqualTo("PO-2026-000001");
        assertThat(saved.approvalNote()).isEqualTo("DRAFT");
        assertThat(saved.notes()).isEqualTo("UAT first pharmacy purchase order");
        assertThat(saved.itemsJson()).contains("\"medicineId\":\"%s\"".formatted(MEDICINE_ID));
        assertThat(saved.itemsJson()).contains("\"unitCost\":1.25");
        assertThat(saved.itemsJson()).contains("\"taxPercent\":12");
        assertThat(saved.itemsJson()).contains("\"discount\":25.00");
        assertThat(reloaded.notes()).isEqualTo("UAT first pharmacy purchase order");
        assertThat(reloaded.itemsJson()).contains("\"discount\":25.00");
    }

    @Test
    void savePurchaseOrderDraftCanBeEditedWithoutLosingNotesOrDiscounts() {
        PurchaseOrderRequest firstRequest = new PurchaseOrderRequest(
                SUPPLIER_ID,
                "PO-2026-000010",
                "2026-07-01",
                "2026-07-10",
                List.of(new ProcurementLineRequest(
                        MEDICINE_ID,
                        "Paracetamol 500",
                        12,
                        new BigDecimal("1.25"),
                        new BigDecimal("1.25"),
                        new BigDecimal("12"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("25.00")
                )),
                "DRAFT",
                "UAT first pharmacy purchase order"
        );

        PurchaseOrderRecord saved = service.savePurchaseOrder(TENANT_ID, firstRequest, ACTOR_ID);
        assertThat(saved.notes()).isEqualTo("UAT first pharmacy purchase order");
        assertThat(saved.itemsJson()).contains("\"discount\":25.00");

        PurchaseOrderRequest editedRequest = new PurchaseOrderRequest(
                SUPPLIER_ID,
                "PO-2026-000010",
                "2026-07-01",
                "2026-07-12",
                List.of(new ProcurementLineRequest(
                        MEDICINE_ID,
                        "Paracetamol 500",
                        10,
                        new BigDecimal("1.25"),
                        new BigDecimal("1.25"),
                        new BigDecimal("12"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("10.00")
                )),
                "DRAFT",
                "Updated UAT pharmacy purchase order"
        );

        PurchaseOrderRecord updated = service.savePurchaseOrder(TENANT_ID, editedRequest, ACTOR_ID);
        PurchaseOrderRecord reloaded = service.getPurchaseOrder(TENANT_ID, updated.id());

        assertThat(updated.notes()).isEqualTo("Updated UAT pharmacy purchase order");
        assertThat(updated.itemsJson()).contains("\"discount\":10.00");
        assertThat(reloaded.notes()).isEqualTo("Updated UAT pharmacy purchase order");
        assertThat(reloaded.itemsJson()).contains("\"discount\":10.00");
    }

    @Test
    void cancelPurchaseOrderMarksRecordCancelled() {
        PurchaseOrderEntityFixture fixture = new PurchaseOrderEntityFixture();
        when(purchaseOrderRepository.findByTenantIdAndId(TENANT_ID, fixture.entity.getId())).thenReturn(Optional.of(fixture.entity));

        PurchaseOrderRecord cancelled = service.cancelPurchaseOrder(TENANT_ID, fixture.entity.getId(), "supplier requested", ACTOR_ID);

        assertThat(cancelled.approvalNote()).isEqualTo("CANCELLED:supplier requested");
        assertThat(cancelled.matchingStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void generatePurchaseOrderPdfIncludesPersistedNotesDiscountAndTotals() throws Exception {
        PurchaseOrderRecord saved = service.savePurchaseOrder(TENANT_ID, generatedRequest("PO-2026-000020", "PO notes for printable document"), ACTOR_ID);

        PurchaseOrderDocumentResponse pdf = service.generatePurchaseOrderPdf(TENANT_ID, saved.id(), ACTOR_ID);

        assertThat(pdf.filename()).isEqualTo("PO-2026-000020.pdf");
        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("UAT Clinic");
            assertThat(text).contains("PURCHASE ORDER");
            assertThat(text).contains("PO-2026-000020");
            assertThat(text).contains("Supplier Contact");
            assertThat(text).contains("Reference / Notes");
            assertThat(text).contains("PO notes for printable document");
            assertThat(text).contains("Grand total");
            assertThat(text).contains("GST total");
        }
    }

    @Test
    void generatePurchaseOrderPdfRendersStatusTimestampInClinicTimezone() throws Exception {
        PurchaseOrderRecord saved = service.savePurchaseOrder(TENANT_ID, generatedRequest("PO-2026-000024", "Timezone test"), ACTOR_ID);
        setUpdatedAt(savedPurchaseOrder.get(), OffsetDateTime.parse("2026-08-28T11:35:00Z"));

        PurchaseOrderDocumentResponse pdf = service.generatePurchaseOrderPdf(TENANT_ID, saved.id(), ACTOR_ID);

        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("28 Aug 2026 05:05 PM");
        }
    }

    @Test
    void sendPurchaseOrderUpdatesStatusAndQueuesNotification() {
        PurchaseOrderRecord saved = service.savePurchaseOrder(TENANT_ID, generatedRequest("PO-2026-000021", "Send me"), ACTOR_ID);

        PurchaseOrderSendResponse response = service.sendPurchaseOrder(TENANT_ID, saved.id(), ACTOR_ID);
        PurchaseOrderRecord reloaded = service.getPurchaseOrder(TENANT_ID, saved.id());

        assertThat(response.sent()).isTrue();
        assertThat(response.recipientEmail()).isEqualTo("supplier@example.com");
        assertThat(response.message()).contains("sent");
        assertThat(reloaded.approvalNote()).isEqualTo("SENT");
        verify(notificationHistoryService).queue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendPurchaseOrderRejectedWithoutSupplierEmail() {
        when(supplier.getEmail()).thenReturn(null);
        PurchaseOrderRecord saved = service.savePurchaseOrder(TENANT_ID, generatedRequest("PO-2026-000022", "No email"), ACTOR_ID);

        assertThatThrownBy(() -> service.sendPurchaseOrder(TENANT_ID, saved.id(), ACTOR_ID))
                .hasMessageContaining("Supplier email is required");
    }

    @Test
    void draftPurchaseOrderPdfIsRejected() {
        PurchaseOrderEntity draft = PurchaseOrderEntity.create(
                TENANT_ID,
                SUPPLIER_ID,
                "PO-2026-000023",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-10"),
                "[{\"medicineId\":\"%s\",\"medicineName\":\"Paracetamol 500\",\"quantity\":12,\"expectedUnitCost\":1.25,\"unitCost\":1.25,\"taxPercent\":12,\"discount\":25.00}]".formatted(MEDICINE_ID),
                null,
                ACTOR_ID
        );
        when(purchaseOrderRepository.findByTenantIdAndId(TENANT_ID, draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.generatePurchaseOrderPdf(TENANT_ID, draft.getId(), ACTOR_ID))
                .hasMessageContaining("Purchase order is not available for printing");
    }

    private PurchaseOrderRequest generatedRequest(String poNumber, String notes) {
        return new PurchaseOrderRequest(
                SUPPLIER_ID,
                poNumber,
                "2026-07-01",
                "2026-07-10",
                List.of(new ProcurementLineRequest(
                        MEDICINE_ID,
                        "Paracetamol 500",
                        12,
                        new BigDecimal("1.25"),
                        new BigDecimal("1.25"),
                        new BigDecimal("12"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("25.00")
                )),
                "GENERATED",
                notes
        );
    }

    private static void setUpdatedAt(PurchaseOrderEntity entity, OffsetDateTime updatedAt) {
        try {
            var field = PurchaseOrderEntity.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(entity, updatedAt);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private NotificationHistoryRecord notificationHistoryRecord() {
        return new NotificationHistoryRecord(
                UUID.randomUUID(),
                TENANT_ID,
                null,
                "PURCHASE_ORDER_SENT",
                "email",
                "supplier@example.com",
                "Purchase Order PO-2026-000021",
                "message",
                "QUEUED",
                null,
                "PURCHASE_ORDER",
                UUID.randomUUID(),
                null,
                null,
                0,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private final class PurchaseOrderEntityFixture {
        private final com.deepthoughtnet.clinic.inventory.db.PurchaseOrderEntity entity =
                com.deepthoughtnet.clinic.inventory.db.PurchaseOrderEntity.create(
                        TENANT_ID,
                        SUPPLIER_ID,
                "PO-2026-000002",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-10"),
                "[{\"medicineId\":\"%s\",\"medicineName\":\"Paracetamol 500\",\"quantity\":12,\"expectedUnitCost\":1.25,\"unitCost\":1.25,\"taxPercent\":12}]".formatted(MEDICINE_ID),
                null,
                ACTOR_ID
        );
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
