package com.deepthoughtnet.clinic.api.help;

import com.deepthoughtnet.clinic.api.help.HelpModels.HelpContentStatus;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageStatus;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpSectionType;
import com.deepthoughtnet.clinic.api.help.db.HelpContentEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpContentRepository;
import com.deepthoughtnet.clinic.api.help.db.HelpPageEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpPageRepository;
import com.deepthoughtnet.clinic.api.help.db.HelpSectionEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpSectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HelpCmsSeeder {
    private static final Logger log = LoggerFactory.getLogger(HelpCmsSeeder.class);

    private final HelpPageRepository pageRepository;
    private final HelpSectionRepository sectionRepository;
    private final HelpContentRepository contentRepository;
    private final ObjectMapper objectMapper;

    public HelpCmsSeeder(
            HelpPageRepository pageRepository,
            HelpSectionRepository sectionRepository,
            HelpContentRepository contentRepository,
            ObjectMapper objectMapper
    ) {
        this.pageRepository = pageRepository;
        this.sectionRepository = sectionRepository;
        this.contentRepository = contentRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        for (HelpPageSeed seed : seeds()) {
            try {
                HelpPageEntity page = pageRepository.findByPageKeyIgnoreCase(seed.pageKey())
                        .orElseGet(() -> {
                            HelpPageEntity created = HelpPageEntity.create(
                                    seed.moduleKey(),
                                    seed.pageKey(),
                                    seed.title(),
                                    seed.icon(),
                                    HelpPageStatus.PUBLISHED.name(),
                                    true,
                                    null
                            );
                            created.setVersion(1);
                            return pageRepository.save(created);
                        });
                if (!seed.title().equals(page.getTitle()) || !seed.icon().equals(page.getIcon()) || !seed.moduleKey().equals(page.getModuleKey()) || !page.isActive() || !HelpPageStatus.PUBLISHED.name().equals(page.getStatus())) {
                    page.update(seed.moduleKey(), seed.title(), seed.icon(), HelpPageStatus.PUBLISHED.name(), true, null);
                    pageRepository.save(page);
                }
                for (HelpSectionSeed sectionSeed : seed.sections()) {
                    if (sectionRepository.findByPage_IdAndSectionKeyIgnoreCase(page.getId(), sectionSeed.sectionKey()).isPresent()) {
                        continue;
                    }
                    HelpSectionEntity section = HelpSectionEntity.create(
                            page,
                            sectionSeed.sectionKey(),
                            sectionSeed.sectionType(),
                            sectionSeed.displayOrder(),
                            sectionSeed.collapsible(),
                            sectionSeed.active()
                    );
                    section = sectionRepository.save(section);
                    contentRepository.save(HelpContentEntity.create(
                            section,
                            "en",
                            json(sectionSeed.content()),
                            1,
                            HelpContentStatus.PUBLISHED.name(),
                            null
                    ));
                }
                if ("BILLING".equalsIgnoreCase(seed.pageKey())) {
                    deactivateObsoleteBillingSections(page, seed);
                }
                if ("PHARMACY_INVENTORY".equalsIgnoreCase(seed.pageKey()) || "PHARMACY_DISPENSING".equalsIgnoreCase(seed.pageKey()) || "PHARMACY_DASHBOARD".equalsIgnoreCase(seed.pageKey()) || "REPORTS".equalsIgnoreCase(seed.pageKey()) || "BILLING".equalsIgnoreCase(seed.pageKey())) {
                    boolean refreshed = refreshSeedContent(page, seed);
                    if (refreshed) {
                        page.setVersion(page.getVersion() + 1);
                        pageRepository.save(page);
                    }
                }
                log.info("Seeded help page {}", seed.pageKey());
            } catch (Exception ex) {
                log.warn("Skipping help seed {}: {}", seed.pageKey(), ex.toString());
            }
        }
    }

    private boolean refreshSeedContent(HelpPageEntity page, HelpPageSeed seed) throws JsonProcessingException {
        boolean updated = false;
        for (HelpSectionSeed sectionSeed : seed.sections()) {
            var sectionOpt = sectionRepository.findByPage_IdAndSectionKeyIgnoreCase(page.getId(), sectionSeed.sectionKey());
            if (sectionOpt.isEmpty()) {
                continue;
            }
            HelpSectionEntity section = sectionOpt.get();
            String desiredJson = json(sectionSeed.content());
            List<HelpContentEntity> history = contentRepository.findBySection_IdOrderByVersionDescCreatedAtDesc(section.getId());
            HelpContentEntity latest = history.isEmpty() ? null : history.get(0);
            if (latest != null && desiredJson.equals(latest.getContentJson())) {
                continue;
            }
            int nextVersion = latest == null ? 1 : latest.getVersion() + 1;
            contentRepository.save(HelpContentEntity.create(
                    section,
                    "en",
                    desiredJson,
                    nextVersion,
                    HelpContentStatus.PUBLISHED.name(),
                    null
            ));
            updated = true;
        }
        return updated;
    }

    private void deactivateObsoleteBillingSections(HelpPageEntity page, HelpPageSeed seed) {
        List<String> desiredSectionKeys = seed.sections().stream().map(HelpSectionSeed::sectionKey).map(String::toUpperCase).toList();
        boolean changed = false;
        for (HelpSectionEntity section : sectionRepository.findByPage_IdOrderByDisplayOrderAsc(page.getId())) {
            if (!desiredSectionKeys.contains(section.getSectionKey().toUpperCase()) && section.isActive()) {
                section.update(section.getSectionType(), section.getDisplayOrder(), section.isCollapsible(), false);
                sectionRepository.save(section);
                changed = true;
            }
        }
        if (changed) {
            page.setVersion(page.getVersion() + 1);
            pageRepository.save(page);
        }
    }

    private List<HelpPageSeed> seeds() {
        return List.of(
                page(
                        "PHARMACY", "MEDICINE_MASTER", "Medicine Master", "pill",
                        "Manage medicine catalog entries used across dispensing, inventory, POS, and billing.",
                        roles("PHARMACY_ADMIN", "PHARMACY_INVENTORY_MANAGER", "CLINIC_ADMIN"),
                        steps(
                                step("Create medicine", "Add name, type, strength, and optional commercial fields."),
                                step("Set defaults", "Configure dosage, frequency, duration, price, and tax."),
                                step("Use everywhere", "The catalog feeds inventory, dispensing, and POS.")
                        ),
                        fields(
                                field("Medicine name", true, "Primary medicine label shown in workflows.", "Paracetamol", 60, "Required, 2-60 characters, must contain a letter or number", "Text"),
                                field("Type", true, "Medicine form such as tablet or syrup.", "Tablet", 60, "Required", "Enum"),
                                field("Status", true, "Active or inactive catalog state.", "Active", 30, "Required", "Enum"),
                                field("Strength", true, "Dosage strength such as 500 mg or 60K IU.", "500 mg", 60, "Required, must contain a letter or number", "Text"),
                                field("Barcode", false, "Optional machine-readable code.", "ABC-123", 60, "Letters, numbers, dash, underscore, slash only", "Text"),
                                field("Default price", false, "Optional catalog price.", "12.50", 0, "Numeric, >= 0, max 2 decimals", "Amount"),
                                field("Tax %", false, "Optional tax rate.", "12", 0, "Numeric, 0-100, max 2 decimals", "Percentage")
                        ),
                        validations(
                                validation("Medicine name", "Trimmed, required, 2-60 characters.", true, "Text", 60, "Paracetamol"),
                                validation("Type", "Required.", true, "Enum", null, "Tablet"),
                                validation("Status", "Required.", true, "Enum", null, "Active"),
                                validation("Strength", "Trimmed, required, max 60 characters, non-symbolic.", true, "Text", 60, "500 mg"),
                                validation("Default price", "Optional; numeric 0-999999 with up to 2 decimals.", false, "Amount", null, "12.50"),
                                validation("Tax %", "Optional; numeric 0-100 with up to 2 decimals.", false, "Percentage", null, "12")
                        ),
                        errors(
                                error("Duplicate medicine", "Same normalized name, strength, and type already exists.", "Update the existing medicine or use a distinct strength/type."),
                                error("Invalid name", "Blank or symbol-only medicine names are not accepted.", "Enter a name containing letters or numbers.")
                        ),
                        bestPractices(
                                note("Standardize naming", "Use a consistent naming convention across catalog entries."),
                                note("Review defaults", "Keep default dosage and frequency aligned with prescribing habits.")
                        ),
                        faq(
                                faqItem("How do I create a medicine?", "Open Medicine Master, add the medicine name, type, strength, and save."),
                                faqItem("Can I keep price blank?", "Yes. Default price is optional and can be set later.")
                        ),
                        related(
                                relatedItem("Inventory", "INVENTORY", "Use medicine records while creating stock batches."),
                                relatedItem("Dispensing", "DISPENSING", "Dispensing uses the catalog when selecting medicines."),
                                relatedItem("POS", "POS", "Medicine prices appear in the POS workflow.")
                        ),
                        tips(
                                note("Keep strengths consistent", "Use the same formatting for repeated strengths."),
                                note("Prefer simple defaults", "Set default instructions only when the clinic uses them consistently.")
                        ),
                        limitations(
                                note("Manual duplicate checks", "The frontend prevents common duplicates; the backend remains authoritative.")
                        ),
                        links(
                                link("Inventory", "/inventory", "Open the inventory help page."),
                                link("Dispensing", "/pharmacy/dispensing", "Open dispensing help.")
                        ),
                        audit(
                                note("Catalog change", "Every save should be reviewed in the audit trail.")
                        )
                ),
                new HelpPageSeed(
                        "PHARMACY",
                        "PHARMACY_DASHBOARD",
                        "Pharmacy Dashboard",
                        "dashboard",
                        List.of(
                                section("DESCRIPTION", "DESCRIPTION", 1, true, true, Map.of(
                                        "title", "Pharmacy Dashboard",
                                        "description", "Pharmacy Dashboard provides a real-time operational overview for pharmacists. It summarizes prescriptions waiting for dispensing, stock health, expiring medicines, reconciliation activity, and recent inventory movements. Use it as the starting point of every shift.",
                                        "searchTags", List.of("pharmacy dashboard", "ready to dispense", "pending dispense", "low stock", "stock health", "reconciliation", "inventory overview", "pharmacy overview")
                                )),
                                section("WORKFLOW", "WORKFLOW", 2, true, true, Map.of(
                                        "steps", steps(
                                                step("Review Ready to Dispense prescriptions.", "Review prescriptions waiting for pharmacist action."),
                                                step("Check Pending Dispense items.", "Identify partially completed prescriptions that still need attention."),
                                                step("Monitor Low Stock and Expiring Soon cards.", "Watch for medicines that need replenishment or batch review."),
                                                step("Open Dispensing to issue medicines.", "Move directly into the dispensing workflow when stock is ready."),
                                                step("Review recent stock movement activity.", "Check what changed in stock since your last review."),
                                                step("Check reconciliation tasks requiring attention.", "Follow up on pending stock review items."),
                                                step("Navigate to Inventory or Medicine Master for corrections.", "Use the source pages to fix stock or catalogue issues.")
                                        )
                                )),
                                section("QUICK_ACTIONS", "QUICK_ACTIONS", 3, true, true, Map.of(
                                        "items", List.of(
                                                link("Open Dispensing", "/pharmacy/dispensing", "Issue medicines and complete prescriptions."),
                                                link("Open Inventory", "/inventory", "Manage stock batches and quantities."),
                                                link("Open Stock Movements", "/pharmacy/stock-movements", "Review inventory transaction history."),
                                                link("Open Medicine Master", "/pharmacy/medicines", "Maintain medicine catalogue."),
                                                link("Open Reconciliation", "/pharmacy/operations?tab=reconciliation", "Review stock adjustment requests.")
                                        )
                                )),
                                section("DASHBOARD_CARDS", "DASHBOARD_CARDS", 4, true, true, Map.of(
                                        "items", List.of(
                                                note("Ready to Dispense", "Prescriptions waiting for pharmacist action."),
                                                note("Pending Dispense", "Partially completed prescriptions."),
                                                note("Today Dispensed", "Completed dispensing transactions today."),
                                                note("Low Stock", "Medicines below reorder level."),
                                                note("Expiring Soon", "Batches approaching expiry."),
                                                note("Reconciliation", "Pending stock review activities.")
                                        )
                                )),
                                section("BEST_PRACTICES", "BEST_PRACTICES", 5, true, true, Map.of(
                                        "items", List.of(
                                                note("Start every shift from this dashboard.", "Use the dashboard first to understand the current pharmacy workload."),
                                                note("Process Ready to Dispense queue first.", "Move active prescriptions forward before doing lower-priority review work."),
                                                note("Review low stock medicines daily.", "Keep the queue moving by replenishing medicines early."),
                                                note("Monitor expiring batches before stock becomes unusable.", "Reduce avoidable write-offs and dispensing interruptions."),
                                                note("Investigate unusual stock movement activity.", "Confirm that stock changes match operational events."),
                                                note("Review reconciliation items regularly.", "Close review gaps before they become posting issues.")
                                        )
                                )),
                                section("COMMON_ISSUES", "COMMON_ISSUES", 6, true, true, Map.of(
                                        "items", List.of(
                                                error("Counts appear outdated", "The dashboard cache or browser session has not refreshed.", "Use Refresh."),
                                                error("No recent stock movements", "No inventory activity has occurred in the current time window.", "This is expected when there has been no stock activity."),
                                                error("Low stock not appearing", "Threshold values may not be configured.", "Configure reorder thresholds for the medicines you want to track."),
                                                error("Reconciliation count is zero", "No pending review actions exist.", "Open Reconciliation when a review is required.")
                                        )
                                )),
                                section("FAQ", "FAQ", 7, true, true, Map.of(
                                        "items", List.of(
                                                faqItem("Can stock be edited here?", "No. Use Inventory."),
                                                faqItem("Can medicines be dispensed from this page?", "Open Dispensing."),
                                                faqItem("Are completed prescriptions shown?", "No."),
                                                faqItem("Can I see historical stock movement?", "Open Stock Movements.")
                                        )
                                )),
                                section("PERMISSIONS", "PERMISSIONS", 8, true, true, Map.of("permissions", roles("PLATFORM_ADMIN", "CLINIC_ADMIN", "PHARMACY_ADMIN", "PHARMACIST"))),
                                section("RELATED_PAGES", "RELATED_PAGES", 9, true, true, Map.of(
                                        "items", List.of(
                                                relatedItem("Dispensing", "PHARMACY_DISPENSING", "Issue medicines and close prescriptions."),
                                                relatedItem("Inventory", "PHARMACY_INVENTORY", "Review batches, quantities, and expiry."),
                                                relatedItem("Stock Movements", "STOCK_MOVEMENTS", "Inspect inventory transactions."),
                                                relatedItem("Medicine Master", "MEDICINE_MASTER", "Maintain the medicine catalogue."),
                                                relatedItem("Reconciliation", "PHARMACY_OPERATIONS", "Review stock adjustment requests."),
                                                relatedItem("Pharmacy POS", "PHARMACY_POS", "Process retail medicine sales.")
                                        )
                                ))
                        )
                ),
                page(
                        "PHARMACY", "PHARMACY_INVENTORY", "Inventory", "inventory",
                        "Inventory manages pharmacy stock batches, quantities, expiry dates, low-stock alerts, physical counts, returns, write-offs, and stock movement audit.",
                        roles("PHARMACY_ADMIN", "PHARMACY_INVENTORY_MANAGER", "CLINIC_ADMIN"),
                        roles("PHARMACY_POS_USER"),
                        steps(
                                step("Medicine Master", "Create or update medicines before adding stock."),
                                step("Add batch", "Create a batch with medicine, location, batch number, expiry, and quantity."),
                                step("Monitor stock", "Review quantity, reorder level, and expiry warning states."),
                                step("Dispensing / POS", "Stock reduces through dispensing and POS sale workflows."),
                                step("Physical count", "Compare system quantity with counted quantity and record the variance."),
                                step("Expiry review", "Review low-expiry and expired batches weekly."),
                                step("Returns and write-offs", "Process customer returns, vendor returns, and unusable stock adjustments.")
                        ),
                        fields(
                                field("Medicine", true, "Active medicine master record linked to stock.", "Paracetamol", 60, "Required", "Text"),
                                field("Location", true, "Valid inventory location or pharmacy workspace.", "Main Pharmacy", 60, "Required", "Text"),
                                field("Batch Number", true, "Supplier/manufacturer batch printed on the pack.", "BATCH-001", 60, "Required, 3-30 characters, letters, numbers, dash, underscore, slash", "Text"),
                                field("Expiry Date", true, "Future expiry date for sellable medicines.", "2026-12-31", 0, "Required for sellable stock", "Date"),
                                field("Quantity on hand", true, "Available stock quantity.", "100", 0, "Required whole number greater than zero", "Integer"),
                                field("Status", true, "Active or inactive batch state.", "ACTIVE", 20, "Required", "Enum"),
                                field("Purchase Rate", false, "Purchase cost per unit.", "12.50", 0, "Numeric, >= 0", "Amount"),
                                field("MRP", false, "Maximum retail price used for POS and billing.", "15.00", 0, "Numeric, >= purchase rate", "Amount"),
                                field("Remarks", false, "Optional remarks for audit trail.", "Received from supplier", 250, "Max 250 characters", "Text")
                        ),
                        validations(
                                validation("Medicine", "Required and must be active.", true, "Text", 60, "Paracetamol"),
                                validation("Location", "Required and must be a valid location.", true, "Text", 60, "Main Pharmacy"),
                                validation("Batch Number", "Required, 3-30 characters, letters/numbers/dash/underscore/slash only.", true, "Text", 30, "BATCH-001"),
                                validation("Expiry Date", "Required for sellable medicines and cannot be in the past for active stock.", true, "Date", null, "2026-12-31"),
                                validation("Quantity on hand", "Required whole number greater than zero.", true, "Integer", null, "100"),
                                validation("Status", "Required and limited to ACTIVE or INACTIVE.", true, "Enum", null, "ACTIVE"),
                                validation("Purchase Rate", "Optional; must be numeric and at least zero.", false, "Amount", null, "12.50"),
                                validation("MRP", "Optional; must be numeric, at least purchase rate, and not negative.", false, "Amount", null, "15.00")
                        ),
                        errors(
                                error("Duplicate batch", "The same medicine, location, and batch combination already exists.", "Use another batch number."),
                                error("Invalid quantity", "Quantity must be a positive whole number.", "Enter a whole number greater than zero."),
                                error("Past expiry", "Expired or past-due batches cannot be active sellable stock.", "Choose a valid future expiry date or mark the batch inactive."),
                                error("MRP below purchase rate", "Selling price cannot be lower than purchase cost.", "Increase MRP or reduce purchase rate."),
                                error("Inactive medicine", "Stock cannot be added for an inactive medicine.", "Activate the medicine first."),
                                error("Expired stock", "Expired batches cannot be dispensed or sold.", "Return, write off, or mark inactive."),
                                error("Invalid code", "Barcode, QR code, and external code must be unique when entered.", "Use a different code or leave the field blank.")
                        ),
                        bestPractices(
                                note("Review weekly", "Check expiry and low stock every week."),
                                note("Use FEFO", "Dispense the earliest non-expired batch first."),
                                note("Keep supplier references", "Record invoice numbers for auditability."),
                                note("Use exact batch numbers", "Enter supplier batch numbers exactly as printed.")
                        ),
                        faq(
                                faqItem("How do I add stock?", "Open Inventory and add a batch with medicine, location, batch number, expiry, and quantity."),
                                faqItem("How do I perform physical count?", "Use the physical count tab to compare system quantity with the counted quantity."),
                                faqItem("How do I process returns?", "Use the returns tab for customer return, vendor return, or write-off."),
                                faqItem("Why is low stock showing?", "Quantity is at or below the reorder level."),
                                faqItem("Can I sell expired stock?", "No. Expired stock must be returned to the vendor or written off.")
                        ),
                        related(
                                relatedItem("Medicine Master", "MEDICINE_MASTER", "Maintain the medicine catalog used by inventory."),
                                relatedItem("Dispensing", "DISPENSING", "Dispensing reduces batch stock."),
                                relatedItem("POS", "POS", "Point of sale shares inventory stock."),
                                relatedItem("Stock Movements", "STOCK_MOVEMENTS", "Review stock movement history."),
                                relatedItem("Reconciliation", "RECONCILIATION", "Compare physical count and system stock."),
                                relatedItem("Pharmacy Dashboard", "PHARMACY_DASHBOARD", "Monitor inventory alerts and movement summaries.")
                        ),
                        tips(
                                note("Avoid expired stock", "Review expiry before dispensing any batch."),
                                note("Use barcode search", "Use barcode or QR scanning to find stock quickly."),
                                note("Set reorder levels", "Set reorder levels for fast-moving medicines."),
                                note("Split batches by expiry", "Store separate batches for different expiry dates."),
                                note("Use write-off sparingly", "Write off only unusable stock.")
                        ),
                        limitations(
                                note("No manual overdraw", "The system should never dispense beyond available stock."),
                                note("Language switching is pending", "Help content is English-first for R2.")
                        ),
                        links(
                                link("Stock movements", "/pharmacy/stock-movements", "Review stock movement history."),
                                link("Dispensing", "/pharmacy/dispensing", "Open dispensing help."),
                                link("Medicine Master", "/pharmacy/medicines", "Open the medicine catalog."),
                                link("POS", "/pharmacy/pos", "Review the sales workflow.")
                        ),
                        audit(
                                note("Inventory change", "Every stock adjustment should be auditable."),
                                note("Movement trail", "Store medicine, batch, location, before quantity, after quantity, delta, reason, notes, user, and timestamp.")
                        )
                ),
                page(
                        "PHARMACY", "PHARMACY_OPERATIONS", "Pharmacy Operations", "inventory_2",
                        "Pharmacy Operations manages stock inward, supplier records, vendor reconciliation, procurement records, and operational analytics for pharmacy stock control.",
                        roles("CLINIC_ADMIN", "PHARMACY_ADMIN", "PHARMACY_INVENTORY_MANAGER"),
                        roles("PHARMACY_POS_USER", "DOCTOR", "RECEPTIONIST", "BILLING_USER"),
                        steps(
                                step("Create or select supplier", "Keep supplier records up to date before receiving stock or creating procurement records."),
                                step("Record inward stock or purchase order", "Capture medicine, location, invoice, batch, expiry, quantity, and prices."),
                                step("Review vendor sheet", "Upload vendor sheets for review; OCR extraction is assistive only."),
                                step("Post reviewed reconciliation", "Approve and post only after manual verification."),
                                step("Monitor analytics", "Watch stock value, low-stock, near-expiry, and fast-moving items.")
                        ),
                        fields(
                                field("Medicine", true, "Active medicine master record linked to inward or procurement rows.", "Paracetamol", 60, "Required", "Text"),
                                field("Location", true, "Inventory location for the inward or reconciliation action.", "Main Pharmacy", 60, "Required", "Text"),
                                field("Supplier", false, "Optional supplier on inward; required for procurement records.", "Apex Pharma", 100, "Active supplier if selected", "Text"),
                                field("Invoice number", false, "Optional inward invoice reference.", "INV-1001", 60, "Letters, numbers, dashes, slashes, underscores, and spaces", "Text"),
                                field("GRN number", false, "Optional receipt or goods-receipt reference.", "GRN-1001", 60, "Letters, numbers, dashes, underscores, slashes", "Text"),
                                field("Inward date", true, "Date stock was received.", "2026-06-19", 0, "Required, cannot be future dated", "Date"),
                                field("Expiry date", true, "Expiry for sellable stock.", "2027-06-19", 0, "Required for sellable medicines; cannot be past dated", "Date"),
                                field("Qty", true, "Whole number quantity received or reconciled.", "100", 0, "Required whole number greater than zero", "Integer"),
                                field("Threshold", false, "Optional low-stock threshold.", "10", 0, "Integer, 0 or greater", "Integer"),
                                field("Unit cost", false, "Optional purchase cost.", "12.50", 0, "Numeric, 0 or greater, max 2 decimals", "Amount"),
                                field("Selling price", false, "Optional stock sale price.", "15.00", 0, "Numeric, 0 or greater, max 2 decimals, not less than unit cost", "Amount")
                        ),
                        validations(
                                validation("Medicine", "Required, active, and must exist in Medicine Master.", true, "Text", 60, "Paracetamol"),
                                validation("Location", "Required and must be a valid inventory location.", true, "Text", 60, "Main Pharmacy"),
                                validation("Inward date", "Required and cannot be in the future.", true, "Date", null, "2026-06-19"),
                                validation("Expiry date", "Required for sellable medicines and cannot be in the past for active stock.", true, "Date", null, "2027-06-19"),
                                validation("Qty", "Required whole number greater than zero.", true, "Integer", null, "100"),
                                validation("Threshold", "Optional integer 0 or greater.", false, "Integer", null, "10"),
                                validation("Unit cost", "Optional numeric 0 or greater, max 2 decimals.", false, "Amount", null, "12.50"),
                                validation("Selling price", "Optional numeric 0 or greater, max 2 decimals, and must not be less than unit cost.", false, "Amount", null, "15.00")
                        ),
                        errors(
                                error("Duplicate batch", "The same medicine, location, and batch already exists.", "Use the existing batch or choose a different batch identifier."),
                                error("Inactive supplier", "Inactive suppliers cannot be used for new inward or procurement records.", "Select an active supplier."),
                                error("Invalid price", "Selling price is lower than unit cost.", "Increase selling price or lower unit cost."),
                                error("Past expiry", "Expired or past-dated stock cannot be active sellable inventory.", "Choose a valid future expiry date."),
                                error("Vendor sheet not ready", "Uploaded vendor sheets must be reviewed before posting.", "Review unresolved rows before approving or posting.")
                        ),
                        bestPractices(
                                note("Receive against documents", "Cross-check inward stock against invoice and GRN before saving."),
                                note("Keep suppliers active", "Deactivate suppliers only when they should no longer be selectable."),
                                note("Use FEFO", "Dispense the earliest non-expired batch first."),
                                note("Review low stock daily", "Use analytics to monitor stock thresholds and expiry risk.")
                        ),
                        faq(
                                faqItem("Why can't I save stock inward?", "A required field may be missing, or the medicine, location, supplier, or price may be invalid."),
                                faqItem("Can a vendor sheet auto-post stock?", "No. Vendor reconciliation must be manually reviewed before posting."),
                                faqItem("What happens if selling price is lower than unit cost?", "The save is rejected to protect stock valuation.")
                        ),
                        related(
                                relatedItem("Inventory", "PHARMACY_INVENTORY", "Use batch-level inventory controls and audit trails."),
                                relatedItem("Medicine Master", "PHARMACY_MEDICINE_MASTER", "Manage active medicines before inward stock."),
                                relatedItem("Stock Movements", "STOCK_MOVEMENTS", "Review inward, adjustment, and return movements."),
                                relatedItem("Dispensing", "DISPENSING", "Dispensing consumes inventory stock."),
                                relatedItem("Pharmacy POS", "POS", "POS sales follow the same stock rules."),
                                relatedItem("Pharmacy Dashboard", "PHARMACY_DASHBOARD", "Review stock value and operational counters.")
                        ),
                        tips(
                                note("Keep supplier records current", "Update supplier details before procurement or inward entries."),
                                note("Use invoice/GRN references", "Store purchase references for traceability."),
                                note("Keep batch numbers exact", "Use the supplier or manufacturer batch identifier as printed."),
                                note("Review reconciliation drafts", "Always inspect OCR-extracted rows before posting.")
                        ),
                        limitations(
                                note("OCR review is assistive", "Vendor sheet extraction helps review, but it does not auto-post stock."),
                                note("Language switching is pending", "Help content is English-first for R2."),
                                note("Advanced approvals may expand later", "Procurement approval workflows may be enhanced in a later release.")
                        ),
                        links(
                                link("Inventory", "/inventory", "Open inventory batch and movement workspace."),
                                link("Medicine Master", "/pharmacy/medicines", "Manage medicine catalogue entries."),
                                link("Stock Movements", "/pharmacy/stock-movements", "Review stock change history."),
                                link("Pharmacy POS", "/pharmacy/pos", "Open the sales workflow."),
                                link("Dispensing", "/pharmacy/dispensing", "Open dispense workflow help.")
                        ),
                        audit(
                                note("Stock inward audit", "Record medicine, location, batch, quantity, prices, supplier, and timestamp."),
                                note("Reconciliation audit", "Store selected reason, final remarks, reviewer, and posting timestamp.")
                        )
                ),
                page(
                        "PHARMACY", "DISPENSING", "Dispensing", "medication",
                        "Dispensing is used to issue medicines against finalized prescriptions using live stock availability. It supports full dispense, partial dispense, out-of-stock handling, patient-declined workflows, bought-outside closure, billing, and stock audit.",
                        roles("CLINIC_ADMIN", "PHARMACY_ADMIN", "PHARMACY_POS_USER", "PHARMACIST"),
                        roles("RECEPTIONIST", "BILLING_USER"),
                        steps(
                                step("Doctor finalizes prescription", "The finalized prescription becomes available to pharmacy."),
                                step("Prescription appears in queue", "Open the dispensing queue and filter by active prescriptions."),
                                step("Open prescription", "Review patient, doctor, medicines, and stock availability."),
                                step("Check stock and expiry", "System checks live stock and expiry before dispensing."),
                                step("Dispense full or partial", "Confirm the batch and quantity for a sellable medicine line."),
                                step("Handle out-of-stock cases", "Mark unavailable, bought outside, patient declined, cancelled, or expired when dispensing cannot proceed."),
                                step("Record stock movement", "Only actual dispensing reduces stock and creates a movement."),
                                step("Generate bill", "Create a medicine bill only after dispense lines exist.")
                        ),
                        fields(
                                field("Dispense quantity", true, "Quantity to dispense for the selected medicine line.", "1", 0, "Integer, >= 1, <= pending quantity and stock", "Integer"),
                                field("Closure reason", true, "Reason used when closing or declining a prescription.", "Patient declined", 60, "Required for closures", "Enum"),
                                field("Batch override", false, "Optional manual batch selection.", "BATCH-001", 60, "Must match an active available batch when used", "Text"),
                                field("Remarks", false, "Optional audit remarks.", "Patient bought outside", 250, "Max 250 characters", "Text")
                        ),
                        validations(
                                validation("Dispense quantity", "Required for full and partial dispense; integer only; positive and within pending quantity and stock.", true, "Integer", null, "1"),
                                validation("Closure reason", "Required for closure actions and max 60 characters.", true, "Enum", 60, "Patient declined"),
                                validation("Remarks", "Optional and max 250 characters.", false, "Text", 250, "Patient bought outside"),
                                validation("Batch override", "Optional; max 60 characters and must match an active available batch when used.", false, "Text", 60, "BATCH-001")
                        ),
                        errors(
                                error("Out of stock", "No sellable stock is available for the selected medicine.", "Add inventory, mark unavailable, or bought outside."),
                                error("Expired batch", "Selected batch is expired.", "Select a valid non-expired batch."),
                                error("Inactive batch", "Inactive batches cannot be dispensed.", "Choose an active batch."),
                                error("Duplicate dispense", "The same line cannot be dispensed twice.", "Close or refresh the queue item."),
                                error("Terminal prescription", "Closed prescriptions cannot be dispensed.", "Use the active queue only."),
                                error("Duplicate bill", "A bill cannot be generated twice for the same dispensed lines.", "Open the existing bill instead.")
                        ),
                        bestPractices(
                                note("Verify before confirming", "Check patient, doctor, stock, and batch before saving."),
                                note("Follow FEFO", "Use the earliest non-expired batch first."),
                                note("Capture closure reasons", "Keep reasons and remarks short, clear, and auditable."),
                                note("Generate bill after dispense", "Only bill medicines that were actually dispensed."),
                                note("Review movements", "Confirm stock movements after every dispense.")
                        ),
                        faq(
                                faqItem("Can I dispense out-of-stock medicine?", "No. You can mark it unavailable or bought outside."),
                                faqItem("Does bought outside reduce stock?", "No. It only closes the pharmacy queue item."),
                                faqItem("Can I partially dispense?", "Yes, when some quantity is available."),
                                faqItem("Can expired stock be dispensed?", "No."),
                                faqItem("When is bill generated?", "After full or partial dispensing of billable medicines.")
                        ),
                        related(
                                relatedItem("Inventory", "INVENTORY", "Dispensing consumes inventory stock."),
                                relatedItem("Stock Movements", "STOCK_MOVEMENTS", "Review the resulting stock movement."),
                                relatedItem("Pharmacy POS", "POS", "POS uses the same stock pool."),
                                relatedItem("Billing", "BILLING", "Bill generation follows dispense."),
                                relatedItem("Pharmacy Dashboard", "PHARMACY_DASHBOARD", "Review queue and stock health.")
                        ),
                        tips(
                                note("Use batch scan", "Scan the batch when a manual override is allowed."),
                                note("Keep remarks short", "Short remarks are easier to audit."),
                                note("Refresh after stock updates", "Reload the queue after replenishment."),
                                note("Add stock before dispensing", "Use Inventory to replenish sellable stock.")
                        ),
                        limitations(
                                note("Backend remains authoritative", "The frontend validates common cases; server rules still apply."),
                                note("Manual batch override may be tightened later", "Override approval can be expanded in a future release."),
                                note("Language switching is planned", "Help content is English-first for now.")
                        ),
                        links(
                                link("Inventory", "/inventory", "Review stock and batch availability."),
                                link("Medicine Master", "/pharmacy/medicines", "Maintain the medicine catalog."),
                                link("Stock Movements", "/pharmacy/stock-movements", "Review movement history."),
                                link("Pharmacy POS", "/pharmacy/pos", "Open the sales workflow."),
                                link("Billing", "/billing", "Open billing and payment records.")
                        ),
                        audit(
                                note("Dispense audit", "Store prescription ID, line ID, quantity, batch override, reason, remarks, user, and timestamp."),
                                note("Closure audit", "Record the selected closure reason and final remarks when a prescription is closed.")
                        )
                ),
                page(
                        "PHARMACY", "PHARMACY_POS", "Pharmacy POS", "point_of_sale",
                        "Pharmacy POS is used for walk-in medicine sales, prescription-linked sales, barcode scanning, cart checkout, payment recording, invoice generation, and stock deduction.",
                        roles("PHARMACY_POS_USER", "PHARMACY_ADMIN", "CLINIC_ADMIN"),
                        steps(
                                step("Search or scan medicine", "Find medicines by name, generic name, barcode, QR code, external code, or batch."),
                                step("Add available stock to cart", "FEFO chooses the earliest non-expired stock by default."),
                                step("Select customer details", "Use a patient when linked to a prescription or enter walk-in details when needed."),
                                step("Attach prescription if required", "Upload a PDF or image when a prescription is needed."),
                                step("Review pricing", "Confirm quantity, rate, discount, tax, and total before checkout."),
                                step("Enter payment details", "Record paid amount and payment mode before completing the sale."),
                                step("Complete sale", "Successful checkout deducts stock and creates a sale movement."),
                                step("Review receipt", "Generate or print the invoice or receipt after sale."),
                                step("Review recent sales", "Use the recent sales drawer for audit and follow-up.")
                        ),
                        fields(
                                field("Medicine", true, "Medicine sold at POS.", "Paracetamol", 60, "Required", "Text"),
                                field("Batch", true, "Selected sellable batch.", "PCM2401", 30, "Required when manually selected", "Text"),
                                field("Quantity", true, "Sale quantity.", "1", 0, "Whole number greater than 0 and within available stock", "Integer"),
                                field("Rate", false, "Unit sale rate.", "15.00", 0, "Numeric, zero or greater", "Amount"),
                                field("Discount", false, "Optional line discount.", "0.00", 0, "Cannot exceed line amount", "Amount"),
                                field("Tax", false, "Optional tax amount or percent depending on workflow.", "5.00", 0, "0-100 where applicable", "Amount"),
                                field("Payment mode", true, "Payment method used for settlement.", "Cash", 30, "Required when payment is entered", "Enum"),
                                field("Paid amount", true, "Amount collected for the sale.", "50.00", 0, "Required when grand total is greater than zero", "Amount"),
                                field("Reference", false, "Reference for non-cash payment.", "UPI12345", 60, "Required for UPI, card, and insurance payments where applicable", "Text")
                        ),
                        validations(
                                validation("Search text", "Trimmed and limited to 60 characters.", false, "Text", 60, "Paracetamol"),
                                validation("Quantity", "Whole number greater than 0 and within available stock.", true, "Integer", null, "1"),
                                validation("Discount", "Cannot exceed line amount.", false, "Amount", null, "0.00"),
                                validation("Tax", "Must be between 0 and 100.", false, "Amount or percentage", 100, "5.00"),
                                validation("Paid amount", "Required when grand total is greater than zero and must cover the sale total.", true, "Amount", null, "100.00"),
                                validation("Reference", "Required for non-cash payment modes when applicable.", false, "Text", 60, "UPI12345"),
                                validation("Prescription file", "PDF, JPG, JPEG, and PNG only; OCR is assistive and requires pharmacist review.", false, "File", 10 * 1024, "prescription.pdf")
                        ),
                        errors(
                                error("Out of stock", "Requested medicine has no sellable stock.", "Restock or choose another medicine."),
                                error("Expired batch", "Selected batch has expired.", "Use a fresh batch or write off expired stock."),
                                error("Quantity exceeds stock", "Requested quantity exceeds available stock.", "Reduce quantity or split the sale."),
                                error("Paid amount insufficient", "Paid amount is less than the grand total.", "Enter the full amount before completing sale."),
                                error("Missing payment reference", "Digital payment requires a reference number.", "Enter the UPI, card, or insurance reference."),
                                error("No open shift", "POS payment requires an open cashier shift.", "Open a shift before collecting payment.")
                        ),
                        bestPractices(
                                note("Use FEFO", "Sell the earliest non-expired batch first."),
                                note("Scan for speed", "Barcode and QR scanning reduce checkout time."),
                                note("Keep receipts", "Print or share the receipt after every successful sale."),
                                note("Review stock mismatch", "Refresh stock if a sale fails due to changed availability.")
                        ),
                        faq(
                                faqItem("Can I sell medicine without a prescription?", "Yes for OTC medicines. Prescription-linked sales should use the prescription workflow when required."),
                                faqItem("What happens after Complete Sale?", "Stock is deducted, a sale movement is created, payment is recorded, and a receipt is generated."),
                                faqItem("Can I sell expired stock?", "No. Expired stock must be written off or returned to the vendor."),
                                faqItem("Does Hold Cart reduce stock?", "No. Stock is reduced only after checkout."),
                                faqItem("What is FEFO?", "First Expiry First Out. The earliest non-expired batch is used first.")
                        ),
                        related(
                                relatedItem("Medicine Master", "PHARMACY_MEDICINE_MASTER", "Maintain the medicine catalog used by POS."),
                                relatedItem("Inventory", "PHARMACY_INVENTORY", "Track sellable stock batches and expiry."),
                                relatedItem("Dispensing", "PHARMACY_DISPENSING", "Prescription dispensing uses the same stock pool."),
                                relatedItem("Stock Movements", "STOCK_MOVEMENTS", "Review the resulting stock movement."),
                                relatedItem("Billing", "BILLING", "Review overall clinic billing and payments.")
                        ),
                        tips(
                                note("Repeated scan increases quantity", "Scanning the same medicine again should increment quantity if stock is available."),
                                note("Attach prescriptions when needed", "Upload the document before checkout when a prescription is required."),
                                note("Keep payment references", "Save UPI, card, or insurance references for audit."),
                                note("Hold carts sparingly", "Use Hold Cart only when the customer is expected to return soon.")
                        ),
                        limitations(
                                note("OCR is assistive", "Prescription scan should always be reviewed by a pharmacist."),
                                note("Shift policy may vary", "Shift enforcement depends on clinic configuration and backend policy."),
                                note("Credit sale may be limited", "Credit workflows may be introduced later if enabled by the clinic.")
                        ),
                        links(
                                link("Medicine Master", "/pharmacy/medicine-master", "Open the medicine catalog."),
                                link("Inventory", "/inventory", "Review stock and batch availability."),
                                link("Stock Movements", "/pharmacy/stock-movements", "Review sale stock deductions."),
                                link("Dispensing", "/pharmacy/dispensing", "Open dispense workflow help.")
                        ),
                        audit(
                                note("Sale audit", "Store sale number, cart line IDs, batch, quantity, payment mode, paid amount, reference number, cashier, and timestamp.")
                        )
                ),
                page(
                        "PHARMACY", "STOCK_MOVEMENTS", "Stock Movements", "swap_horiz",
                        "Stock movements track every add, dispense, adjustment, return, and write-off action.",
                        roles("PHARMACY_ADMIN", "PHARMACY_INVENTORY_MANAGER", "CLINIC_ADMIN"),
                        steps(
                                step("Movement recorded", "Stock changes are captured when inventory is updated."),
                                step("Filter history", "Search by medicine, location, batch, or movement type."),
                                step("Audit review", "Use the list to reconcile stock changes.")
                        ),
                        fields(
                                field("Movement type", true, "Type of stock movement.", "DISPENSED", 40, "Required", "Enum"),
                                field("Quantity", true, "Quantity moved in or out.", "1", 0, "Integer", "Integer"),
                                field("Batch number", false, "Batch associated with the movement.", "BATCH-001", 60, "Optional", "Text")
                        ),
                        validations(
                                validation("Movement type", "Required.", true, "Enum", null, "DISPENSED"),
                                validation("Quantity", "Integer quantity recorded for the movement.", true, "Integer", null, "1")
                        ),
                        errors(
                                error("Duplicate movement", "Same movement may already be logged.", "Refresh and review the latest entry.")
                        ),
                        bestPractices(
                                note("Review movement history", "Check anomalies weekly.")
                        ),
                        faq(
                                faqItem("Can I edit a movement?", "Movement records should generally remain immutable and auditable.")
                        ),
                        related(
                                relatedItem("Inventory", "INVENTORY", "Source stock batches live here."),
                                relatedItem("Dispensing", "DISPENSING", "Dispensing creates a movement.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "PATIENTS", "Patients", "people",
                        "Patients supports registration, search, demographics, and contact management.",
                        roles("DOCTOR", "RECEPTIONIST", "CLINIC_ADMIN"),
                        steps(
                                step("Search or register", "Find an existing patient or create a new record."),
                                step("Update demographics", "Keep mobile, age, address, and identifiers current."),
                                step("Use in care flows", "Patient records feed appointments, billing, and consultation.")
                        ),
                        fields(
                                field("Name", true, "Patient name.", "Asha Kumar", 60, "Required", "Text"),
                                field("Mobile", true, "Primary Indian mobile number.", "9876543210", 10, "Required Indian mobile", "Phone"),
                                field("Email", false, "Optional email address.", "asha@example.com", 120, "Valid email if present", "Email"),
                                field("Gender", false, "Optional gender selection.", "Female", 20, "Enum when used", "Enum")
                        ),
                        validations(
                                validation("Name", "Required and trimmed.", true, "Text", 60, "Asha Kumar"),
                                validation("Mobile", "Required Indian mobile.", true, "Phone", null, "9876543210"),
                                validation("Email", "Optional valid email.", false, "Email", null, "asha@example.com")
                        ),
                        errors(
                                error("Duplicate patient", "A patient with the same mobile may already exist.", "Search before creating a new record.")
                        ),
                        bestPractices(
                                note("Use mobile-first lookup", "Start with mobile number for the quickest search."),
                                note("Keep contacts current", "Update phone and email after each visit.")
                        ),
                        faq(
                                faqItem("How do I find a patient?", "Search by patient number, mobile, or name.")
                        ),
                        related(
                                relatedItem("Appointments", "APPOINTMENTS", "Book or manage appointments."),
                                relatedItem("Billing", "BILLING", "Patient records feed billing.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "PATIENT_DETAILS", "Patient Details", "person",
                        "Patient details show demographics, history, documents, and linked care flows.",
                        roles("DOCTOR", "RECEPTIONIST", "CLINIC_ADMIN"),
                        steps(
                                step("Review patient", "Open a patient to see demographics and history."),
                                step("Check documents", "Review attached clinical documents and notes."),
                                step("Proceed to care", "Use the record in appointments, consultation, and billing.")
                        ),
                        fields(
                                field("Notes", false, "Optional internal notes.", "Allergy to penicillin", 250, "Optional text", "Text"),
                                field("Address", false, "Optional address lines.", "Sector 21", 120, "Optional text", "Text")
                        ),
                        validations(
                                validation("Notes", "Optional and free text.", false, "Text", 250, "Allergy to penicillin")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Check history", "Review allergies and chronic conditions before prescribing.")
                        ),
                        faq(
                                faqItem("Can I edit every field?", "Some fields may be read-only depending on role and record state.")
                        ),
                        related(
                                relatedItem("Patients", "PATIENTS", "Search and register patients."),
                                relatedItem("Appointments", "APPOINTMENTS", "Create or reschedule visits.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "DOCTORS", "Doctors", "badge",
                        "Doctors manages practitioner profiles, contact details, and public listing settings.",
                        roles("DOCTOR", "CLINIC_ADMIN", "RECEPTIONIST"),
                        steps(
                                step("Open doctor", "Search a doctor profile."),
                                step("Update contact", "Maintain mobile, specialization, and qualification."),
                                step("Manage public listing", "Control whether the doctor appears in discovery.")
                        ),
                        fields(
                                field("Name", true, "Doctor name.", "Dr. Meera", 80, "Required", "Text"),
                                field("Specialization", true, "Clinical specialization.", "Cardiology", 80, "Required", "Text"),
                                field("Mobile", false, "Optional contact mobile.", "9876543210", 10, "Indian mobile if provided", "Phone"),
                                field("Registration Number", false, "Optional medical registration number.", "MMC/12345", 60, "Optional", "Text")
                        ),
                        validations(
                                validation("Name", "Required.", true, "Text", 80, "Dr. Meera"),
                                validation("Specialization", "Required.", true, "Text", 80, "Cardiology")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Keep registration current", "Review registration details periodically.")
                        ),
                        faq(
                                faqItem("How is a doctor linked to appointments?", "The doctor profile feeds scheduling and consultation flows.")
                        ),
                        related(
                                relatedItem("Doctor Availability", "DOCTOR_AVAILABILITY", "Set schedules and availability."),
                                relatedItem("Appointments", "APPOINTMENTS", "Use doctors in bookings.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "DOCTOR_AVAILABILITY", "Doctor Availability", "event_available",
                        "Doctor availability manages schedules, slots, and clinic timing rules.",
                        roles("DOCTOR", "RECEPTIONIST", "CLINIC_ADMIN"),
                        steps(
                                step("Set working hours", "Configure availability windows."),
                                step("Define slots", "Break availability into bookable slots."),
                                step("Review bookings", "Appointments consume the slots.")
                        ),
                        fields(
                                field("Slot length", true, "Appointment slot duration.", "15", 0, "Integer minutes", "Integer"),
                                field("Working day", true, "Day of week or date pattern.", "Monday", 20, "Required", "Enum")
                        ),
                        validations(
                                validation("Slot length", "Integer minutes.", true, "Integer", null, "15")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Keep slot length consistent", "Use a single slot pattern when possible.")
                        ),
                        faq(
                                faqItem("Why is a slot not available?", "The doctor may already be fully booked or the slot may be blocked.")
                        ),
                        related(
                                relatedItem("Appointments", "APPOINTMENTS", "Availability drives bookings."),
                                relatedItem("Queue", "QUEUE", "Check in and waiting list flows use the same schedule.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "APPOINTMENTS", "Appointments", "calendar_month",
                        "Appointments manages booking, reschedule, waitlist, and check-in flows.",
                        roles("DOCTOR", "RECEPTIONIST", "CLINIC_ADMIN"),
                        steps(
                                step("Find patient", "Search an existing patient or register a new one."),
                                step("Choose doctor", "Pick a doctor and an available slot."),
                                step("Book or reschedule", "Create, reschedule, or cancel appointments."),
                                step("Move to queue", "Check-in moves the patient into the active queue.")
                        ),
                        fields(
                                field("Appointment type", true, "Scheduled, follow-up, vaccination, or walk-in.", "Scheduled", 20, "Required enum", "Enum"),
                                field("Queue status", true, "Current queue state.", "Waiting", 20, "Required enum", "Enum"),
                                field("Visit type", false, "Optional visit classification.", "Follow-up", 20, "Optional enum", "Enum")
                        ),
                        validations(
                                validation("Appointment type", "Required enum.", true, "Enum", null, "Scheduled"),
                                validation("Queue status", "Required enum.", true, "Enum", null, "Waiting")
                        ),
                        errors(
                                error("Slot unavailable", "Selected slot is already blocked or booked.", "Choose another slot."),
                                error("Duplicate appointment", "The same patient may already have an appointment in this window.", "Review existing bookings.")
                        ),
                        bestPractices(
                                note("Confirm patient contact", "Verify mobile before booking."),
                                note("Keep schedule fresh", "Reconcile availability before each clinic day.")
                        ),
                        faq(
                                faqItem("How do I create an appointment?", "Search patient, choose doctor, select date/time, and save."),
                                faqItem("How do I reschedule?", "Open the appointment and choose a new doctor/date/time.")
                        ),
                        related(
                                relatedItem("Queue", "QUEUE", "Waiting patients are managed here."),
                                relatedItem("Billing", "BILLING", "Consultation billing starts from appointments."),
                                relatedItem("Patients", "PATIENTS", "Appointments depend on patient records.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "QUEUE", "Queue", "queue",
                        "Queue tracks waiting, in-consultation, and completed patient flow.",
                        roles("DOCTOR", "RECEPTIONIST", "CLINIC_ADMIN"),
                        steps(
                                step("Check in", "Move booked appointments into the queue."),
                                step("Call patient", "Start consultation from the waiting list."),
                                step("Complete visit", "Mark consultation complete when finished.")
                        ),
                        fields(
                                field("Queue status", true, "Waiting, in consultation, or completed.", "Waiting", 20, "Required enum", "Enum"),
                                field("Visit type", false, "Consultation visit category.", "Follow-up", 20, "Optional enum", "Enum")
                        ),
                        validations(
                                validation("Queue status", "Required enum.", true, "Enum", null, "Waiting")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Keep queue moving", "Update status promptly to avoid stale waiting lists.")
                        ),
                        faq(
                                faqItem("Why is the queue empty?", "There may be no booked or checked-in patients for the selected date.")
                        ),
                        related(
                                relatedItem("Appointments", "APPOINTMENTS", "Queue is fed by booked appointments."),
                                relatedItem("Consultation", "CONSULTATION", "In consultation patients move here.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "CONSULTATION", "Consultation", "medical_services",
                        "Consultation captures clinical notes, diagnosis, prescription, and follow-up details.",
                        roles("DOCTOR", "CLINIC_ADMIN"),
                        steps(
                                step("Open visit", "Start with the queued patient."),
                                step("Record findings", "Enter chief complaint, diagnosis, and notes."),
                                step("Prescribe or follow up", "Save prescriptions and follow-up instructions.")
                        ),
                        fields(
                                field("Chief complaint", false, "Primary symptom or concern.", "Fever", 120, "Optional text", "Text"),
                                field("Diagnosis", false, "Clinical impression or diagnosis.", "Viral fever", 250, "Optional text", "Text"),
                                field("Follow-up date", false, "Optional follow-up date.", "2026-07-01", 0, "Optional date", "Date"),
                                field("Notes", false, "Additional consultation notes.", "Monitor temperature", 250, "Optional text", "Text")
                        ),
                        validations(
                                validation("Chief complaint", "Optional text.", false, "Text", 120, "Fever"),
                                validation("Follow-up date", "Optional date.", false, "Date", null, "2026-07-01")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Keep notes concise", "Use structured clinical language.")
                        ),
                        faq(
                                faqItem("Can I save without a diagnosis?", "Yes, depending on workflow and role."),
                                faqItem("Can I create a prescription here?", "Yes, the consultation workflow can create prescription data.")
                        ),
                        related(
                                relatedItem("Patients", "PATIENTS", "Open patient context."),
                                relatedItem("Appointments", "APPOINTMENTS", "Consultation starts from appointment.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                new HelpPageSeed(
                        "CLINIC",
                        "BILLING",
                        "Billing",
                        "receipt_long",
                        List.of(
                                section("DESCRIPTION", "DESCRIPTION", 1, true, true, Map.of(
                                        "title", "Billing",
                                        "description", "Billing is used to create patient bills, add consultation fees, lab tests, medicines, procedures, discounts, taxes, collect payments, print invoices, and track dues from the bill ledger.",
                                        "searchTags", List.of("billing", "discount", "payment reference", "invoice", "ledger", "refund", "payments", "patient billing", "cashier billing")
                                )),
                                section("WORKFLOW", "WORKFLOW", 2, true, true, Map.of(
                                        "steps", steps(
                                                step("Search and select patient.", "Choose the patient before creating the bill."),
                                                step("Confirm bill date and source.", "Keep the bill aligned with the current visit or billing context."),
                                                step("Add consultation fee or bill line items.", "Add all billable services, medicines, and charges."),
                                                step("Apply discount if required.", "Capture a clear reason whenever a discount is applied."),
                                                step("Review subtotal, tax, grand total, paid, and due.", "Confirm the amounts before saving."),
                                                step("Create bill or create and collect payment.", "Save the bill draft or settle it immediately when required."),
                                                step("Print invoice after bill creation.", "Use the created bill record to print the invoice."),
                                                step("Review bill in ledger.", "Use the bill ledger to find and manage historical bills."),
                                                step("Use Payments or Refunds for follow-up financial actions.", "Keep later financial actions in their respective modules.")
                                        )
                                )),
                                section("VALIDATION_RULES", "VALIDATION_RULES", 3, true, true, Map.of(
                                        "rules", List.of(
                                                validation("Patient", "Required before creating bill.", true, "Lookup", null, "Rahul Sharma"),
                                                validation("Bill date", "Cannot be in the future.", true, "Date", null, "2026-06-20"),
                                                validation("At least one line item", "Required before creating bill.", true, "Collection", null, "Consultation fee"),
                                                validation("Item name", "Must be filled for every bill line.", true, "Text", 100, "Consultation fee"),
                                                validation("Type", "Required for every bill line.", true, "Enum", null, "CONSULTATION"),
                                                validation("Quantity", "Must be greater than zero.", true, "Number", null, "1"),
                                                validation("Unit amount", "Cannot be negative.", true, "Amount", null, "500.00"),
                                                validation("Discount", "Cannot exceed subtotal.", false, "Amount", null, "50.00"),
                                                validation("Percentage discount", "Must be between 0 and 100.", false, "Percentage", null, "10"),
                                                validation("Tax", "Must be between 0 and 100.", false, "Percentage", null, "5"),
                                                validation("Discount reason", "Required when discount is applied.", false, "Text", 60, "Approved by clinic admin"),
                                                validation("Payment amount", "Required when collecting payment.", false, "Amount", null, "500.00"),
                                                validation("Payment mode", "Required when collecting payment.", false, "Enum", null, "CASH"),
                                                validation("Reference number", "Required for non-cash payments.", false, "Text", 60, "UPI123456"),
                                                validation("Invoice", "Can be printed only after bill is created.", true, "Action", null, "Print Invoice")
                                        )
                                )),
                                section("COMMON_ERRORS", "COMMON_ERRORS", 4, true, true, Map.of(
                                        "items", List.of(
                                                error("Create Bill disabled", "Patient or line item is missing.", "Select a patient and add a valid bill line."),
                                                error("Invalid discount", "Discount exceeds subtotal or percentage is above 100.", "Correct the discount value."),
                                                error("Payment reference required", "UPI, card, insurance, or bank transfer selected without reference.", "Enter the payment reference."),
                                                error("Future bill date", "Bill date is later than today.", "Select today or a past date."),
                                                error("Empty bill", "No line items exist.", "Add consultation fee or another bill item.")
                                        )
                                )),
                                section("BEST_PRACTICES", "BEST_PRACTICES", 5, true, true, Map.of(
                                        "items", List.of(
                                                note("Select patient before entering bill items.", "This keeps the bill linked to the correct patient."),
                                                note("Use quick add for common charges.", "Quick add speeds up cashier workflows."),
                                                note("Keep discount reason clear.", "Explain why a discount was approved."),
                                                note("Verify total before collecting payment.", "Confirm the grand total before taking payment."),
                                                note("Print or share invoice after creating bill.", "Keep the invoice available for the patient."),
                                                note("Use ledger filters to find historical bills.", "Ledger filters help with audit and reconciliation."),
                                                note("Use Refunds page for controlled refund processing.", "Keep refunds in the dedicated workflow.")
                                        )
                                )),
                                section("FAQ", "FAQ", 6, true, true, Map.of(
                                        "items", List.of(
                                                faqItem("Can I create a bill without a patient?", "No. Billing must be linked to a patient."),
                                                faqItem("Can I create a bill without payment?", "Yes, if pending dues are supported."),
                                                faqItem("Can I apply discount?", "Yes, but discount reason is required when discount is greater than zero."),
                                                faqItem("Where do I print invoice?", "After bill creation, use Print Invoice."),
                                                faqItem("Where do I process refunds?", "Use the Refunds page or bill refund action if available.")
                                        )
                                )),
                                section("RELATED_PAGES", "RELATED_PAGES", 7, true, true, Map.of(
                                        "items", List.of(
                                                relatedItem("Patients", "PATIENTS", "Patient context drives billing."),
                                                relatedItem("Appointments", "APPOINTMENTS", "Consultation billing often starts from appointments."),
                                                relatedItem("Consultations", "CONSULTATION", "Consultation bills and fees are linked here."),
                                                relatedItem("Payments", "PAYMENTS", "Payments are recorded after bill creation."),
                                                relatedItem("Refunds", "REFUNDS", "Refunds and reversals are handled separately."),
                                                relatedItem("Reports", "REPORTS", "Billing totals feed finance reports."),
                                                relatedItem("Pharmacy POS", "PHARMACY_POS", "Pharmacy sales may contribute to billing."),
                                                relatedItem("Laboratory", "LAB", "Lab charges and orders may generate bill items.")
                                        )
                                )),
                                section("PERMISSIONS", "PERMISSIONS", 8, true, true, Map.of(
                                        "permissions", roles("PLATFORM_ADMIN", "CLINIC_ADMIN", "BILLING_USER", "AUDITOR")
                                )),
                                section("TIPS", "TIPS", 9, true, true, Map.of(
                                        "items", List.of(
                                                note("Use patient search by name or mobile.", "Searching by patient details reduces lookup time."),
                                                note("Use consultation fee quick add for faster billing.", "Quick add is best for repeat fee workflows."),
                                                note("Use ledger search to find old bills.", "Ledger search is the fastest way to locate a bill."),
                                                note("Keep non-cash payment reference.", "Always capture a reference for traceability."),
                                                note("Do not expose internal UUIDs on invoice.", "Use readable bill numbers and references.")
                                        )
                                )),
                                section("KNOWN_LIMITATIONS", "KNOWN_LIMITATIONS", 10, true, true, Map.of(
                                        "items", List.of(
                                                note("Advanced approval workflow for discounts may be added later.", "Discount approval is still simple."),
                                                note("Installment or credit workflows may be enhanced later.", "Split payment workflows may evolve."),
                                                note("Language switching is planned.", "Help content currently follows the active help language."),
                                                note("AIVA billing help is planned.", "Automated bill explanations are not yet available.")
                                        )
                                ))
                        )
                ),
                page(
                        "CLINIC", "LAB", "Lab", "science",
                        "Lab manages tests, orders, samples, results, and review workflows.",
                        roles("LAB_TECHNICIAN", "LAB_MANAGER", "CLINIC_ADMIN"),
                        steps(
                                step("Test master", "Maintain tests and parameters."),
                                step("Create orders", "Order tests for a patient or consultation."),
                                step("Collect sample", "Track sample collection and sample IDs."),
                                step("Enter results", "Record result values and comments."),
                                step("Review", "Doctor reviews and finalizes results.")
                        ),
                        fields(
                                field("Test name", true, "Lab test name.", "CBC", 120, "Required", "Text"),
                                field("Result value", false, "Observed result value.", "13.4", 80, "Optional depending on workflow", "Text"),
                                field("Sample ID", false, "Sample identifier.", "SMP-001", 60, "Optional", "Text"),
                                field("Payment mode", false, "Payment method for lab charges.", "Cash", 20, "Optional", "Enum")
                        ),
                        validations(
                                validation("Test name", "Required.", true, "Text", 120, "CBC"),
                                validation("Result value", "Optional unless workflow requires it.", false, "Text", 80, "13.4")
                        ),
                        errors(
                                error("Invalid result", "Result entry is not complete or not in range.", "Review values before saving."),
                                error("Missing sample", "Sample ID is not available.", "Collect the sample before marking results.")
                        ),
                        bestPractices(
                                note("Capture samples consistently", "Use the same sample naming pattern across orders.")
                        ),
                        faq(
                                faqItem("Why is a test rejected?", "The sample may be insufficient or rejected for quality reasons.")
                        ),
                        related(
                                relatedItem("Reports", "REPORTS", "Lab reports can be reviewed here."),
                                relatedItem("Patients", "PATIENTS", "Lab orders are patient-based.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                new HelpPageSeed(
                        "CLINIC",
                        "REPORTS",
                        "Reports",
                        "assessment",
                        List.of(
                                section("DESCRIPTION", "DESCRIPTION", 1, true, true, Map.of(
                                        "title", "Reports",
                                        "description", "Reports provide tenant-scoped operational, clinical, pharmacy, laboratory, and financial reporting. Users can switch report tabs, apply compact filters, refresh results, and export available rows to CSV for audit, finance, and daily operations.",
                                        "searchTags", List.of("reports", "csv export", "patient visits", "doctor consultations", "revenue", "daily sales", "medicine sales", "payment modes", "cashier shifts", "pending dues", "vaccinations due", "follow ups", "low stock", "lab operations", "prescriptions", "tenant scoped report", "financial report", "clinical report", "pharmacy report")
                                )),
                                section("WORKFLOW", "WORKFLOW", 2, true, true, Map.of(
                                        "steps", steps(
                                                step("Open Reports.", "Open the reports page from the sidebar or help navigation."),
                                                step("Select the required report tab.", "Choose the business area you want to review."),
                                                step("Choose date range.", "Set the reporting window before loading results."),
                                                step("Apply optional filters such as doctor, patient, status, payment mode, or source.", "Use only the filters supported by the selected report tab."),
                                                step("Click Refresh.", "Reload the report with the selected filters."),
                                                step("Review report rows.", "Check the values before using them for business decisions."),
                                                step("Export CSV if data is available.", "Download the current report snapshot when rows are present."),
                                                step("Use related modules for corrections when report values look incorrect.", "Reports are read-only, so fix source data in the originating module.")
                                        )
                                )),
                                section("REPORT_TYPES", "REPORT_TYPES", 3, true, true, Map.of(
                                        "items", List.of(
                                                note("Patient Visits", "Shows patient visit records, doctor, visit date, appointment status, and appointment reference."),
                                                note("Doctor Consultations", "Shows completed consultations by doctor, patient, consultation status, and consultation date."),
                                                note("Revenue", "Shows clinic revenue, pharmacy revenue, gross revenue, discounts, tax, refunds, and net revenue."),
                                                note("Daily Sales", "Shows daily gross sales, discounts, tax, refunds, net sales, and payment split."),
                                                note("Medicine Sales", "Shows medicine-wise pharmacy sale details when pharmacy sales exist."),
                                                note("Payment Modes", "Shows payment collection grouped by payment mode and source."),
                                                note("Cashier Shifts", "Shows cashier shift activity where shift records exist."),
                                                note("Pending Dues", "Shows pending payment dues where credit or partial payment flow exists."),
                                                note("Vaccinations Due", "Shows due vaccination records when vaccination schedules exist."),
                                                note("Follow-ups", "Shows follow-up consultations and due dates."),
                                                note("Low Stock", "Shows low-stock medicines based on reorder level."),
                                                note("Lab Operations", "Shows lab order, sample, result, report, turnaround, and revenue summaries."),
                                                note("Prescriptions", "Shows printed or finalized prescription records by patient and doctor.")
                                        )
                                )),
                                section("FILTERS", "FILTERS", 4, true, true, Map.of(
                                        "items", List.of(
                                                note("Common filters", "From date, To date, Doctor, Patient, Status, Payment mode, and Source are available depending on tab."),
                                                note("Date range", "Date range is mandatory and From date cannot be after To date."),
                                                note("Reset", "Reset clears filters back to the default state."),
                                                note("Refresh", "Refresh reloads report data with the current filters."),
                                                note("Export CSV", "Export uses the currently filtered report data.")
                                        )
                                )),
                                section("EXPORT_CSV", "EXPORT_CSV", 5, true, true, Map.of(
                                        "items", List.of(
                                                note("Current tab only", "Exports the active report tab using the current filters and date range."),
                                                note("No rows", "Export is disabled when there are no rows."),
                                                note("Readable columns", "Use business-friendly column names and avoid internal UUIDs unless users need them."),
                                                note("Tenant scope", "Exports stay within the selected tenant boundary.")
                                        )
                                )),
                                section("VALIDATION_RULES", "VALIDATION_RULES", 6, true, true, Map.of(
                                        "rules", List.of(
                                                validation("From date", "Required.", true, "Date", null, "2026-06-01"),
                                                validation("To date", "Required.", true, "Date", null, "2026-06-30"),
                                                validation("From date", "Cannot be after To date.", true, "Date range", null, "2026-06-01 to 2026-06-30"),
                                                validation("Date range", "Limit the range if performance protection exists.", true, "Date range", null, "Last 30 days"),
                                                validation("Export CSV", "Disabled when no report rows exist.", false, "Action", null, "Enabled when rows are present"),
                                                validation("Doctor filter", "Must use a valid doctor.", false, "Lookup", null, "Dr Neha Mehta"),
                                                validation("Patient filter", "Must use a valid patient.", false, "Lookup", null, "Rahul Sharma"),
                                                validation("Status filter", "Must match the selected report type.", false, "Enum", null, "Completed"),
                                                validation("Payment mode filter", "Applies only to financial reports.", false, "Enum", null, "Cash"),
                                                validation("Source filter", "Applies only to revenue, sales, and payment reports.", false, "Enum", null, "Clinic"),
                                                validation("Reports", "Read-only and must not modify source records.", true, "Access", null, "View only"),
                                                validation("Tenant scope", "All reports must remain tenant-scoped.", true, "Scope", null, "Current tenant")
                                        )
                                )),
                                section("COMMON_ERRORS", "COMMON_ERRORS", 7, true, true, Map.of(
                                        "items", List.of(
                                                error("No report rows found", "No matching data exists for the selected filters.", "Widen the date range, clear filters, or switch report tab."),
                                                error("Export CSV disabled", "The report has no rows.", "Refresh with a wider date range or different filters."),
                                                error("Revenue looks incorrect", "Payments, refunds, or discounts may not be posted.", "Check Billing, Payments, Refunds, and Pharmacy POS."),
                                                error("Low stock missing", "Reorder level may not be configured.", "Update the reorder level in Inventory."),
                                                error("Lab operations empty", "No lab orders exist in the selected date range.", "Check the Laboratory module."),
                                                error("Prescription ID is blank", "Source record may not expose linked internal ID.", "Use prescription number, patient, date, and doctor for business tracking.")
                                        )
                                )),
                                section("BEST_PRACTICES", "BEST_PRACTICES", 8, true, true, Map.of(
                                        "items", List.of(
                                                note("Use reports after completing daily operational entries.", "Reports are most useful after source modules have been updated."),
                                                note("Start with a smaller date range for faster loading.", "Narrow windows reduce load time."),
                                                note("Export CSV for audit or reconciliation.", "Use exports when you need a reviewable snapshot."),
                                                note("Use Revenue and Daily Sales for finance review.", "These tabs are the main finance views."),
                                                note("Use Low Stock report before placing procurement orders.", "Check inventory before replenishment."),
                                                note("Use Lab Operations report to monitor sample and report turnaround.", "Review lab flow alongside operational queues."),
                                                note("Use Follow-ups report for patient recall planning.", "Track upcoming patient follow-ups."),
                                                note("Verify source modules before treating report numbers as final.", "Reports summarize source data and do not replace it."),
                                                note("Do not use reports for data correction.", "Update the originating module instead.")
                                        )
                                )),
                                section("FAQ", "FAQ", 9, true, true, Map.of(
                                        "items", List.of(
                                                faqItem("Why do I see no report rows?", "No data matches the selected date range and filters. Try widening the date range or clearing filters."),
                                                faqItem("Why is Export CSV disabled?", "CSV export is available only when rows are present."),
                                                faqItem("Are reports tenant-scoped?", "Yes. Reports show data only for the selected tenant or clinic context."),
                                                faqItem("Can I edit records from Reports?", "No. Reports are read-only. Use the relevant module to edit records."),
                                                faqItem("Why do some ID columns show \"Mapped in source record\"?", "Some business reports hide internal IDs and display source-mapped information for readability."),
                                                faqItem("Which report should finance use?", "Revenue, Daily Sales, Payment Modes, Cashier Shifts, Pending Dues, and refund-related reports."),
                                                faqItem("Which report should pharmacy use?", "Medicine Sales, Low Stock, Daily Sales, Payment Modes, and pharmacy-related source reports."),
                                                faqItem("Which report should operations use?", "Patient Visits, Doctor Consultations, Follow-ups, Vaccinations Due, and Lab Operations.")
                                        )
                                )),
                                section("RELATED_PAGES", "RELATED_PAGES", 10, true, true, Map.of(
                                        "items", List.of(
                                                relatedItem("Dashboard", "PHARMACY_DASHBOARD", "Use the dashboard for operational overview."),
                                                relatedItem("Patients", "PATIENTS", "Patient data contributes to clinical and operational reports."),
                                                relatedItem("Appointments", "APPOINTMENTS", "Appointment records feed visit reports."),
                                                relatedItem("Consultations", "CONSULTATION", "Consultation data drives clinical reporting."),
                                                relatedItem("Billing", "BILLING", "Billing feeds revenue and payment reporting."),
                                                relatedItem("Payments", "PAYMENTS", "Payment records feed finance reports."),
                                                relatedItem("Refunds", "REFUNDS", "Refunds affect revenue and daily sales reporting."),
                                                relatedItem("Pharmacy POS", "PHARMACY_POS", "Retail sales feed pharmacy reports."),
                                                relatedItem("Inventory", "PHARMACY_INVENTORY", "Inventory values affect low stock and stock-related reports."),
                                                relatedItem("Laboratory", "LAB", "Lab activity feeds lab reporting."),
                                                relatedItem("Prescriptions", "PRESCRIPTIONS", "Prescription activity feeds clinical and pharmacy reporting.")
                                        )
                                )),
                                section("PERMISSIONS", "PERMISSIONS", 11, true, true, Map.of(
                                        "permissions", roles("PLATFORM_ADMIN", "CLINIC_ADMIN", "BILLING_USER", "PHARMACY_ADMIN", "PHARMACIST", "AUDITOR")
                                )),
                                section("TIPS", "TIPS", 12, true, true, Map.of(
                                        "items", List.of(
                                                note("Click Reset when filters become confusing.", "Reset is the fastest way to return to the default query."),
                                                note("Use Refresh after changing dates or filters.", "Always reload data after adjusting the report controls."),
                                                note("Export CSV after confirming the correct report tab is selected.", "This avoids exporting the wrong data set."),
                                                note("For revenue mismatches, check Payments and Refunds first.", "Those source modules usually explain the gap."),
                                                note("For pharmacy mismatches, check POS and Stock Movements.", "Confirm sales and stock transactions before assuming an error."),
                                                note("For lab mismatches, check Lab Orders and report status.", "The report reflects source record status.")
                                        )
                                )),
                                section("KNOWN_LIMITATIONS", "KNOWN_LIMITATIONS", 13, true, true, Map.of(
                                        "items", List.of(
                                                note("Advanced charts are not yet available.", "Use tabular reporting until charting is added."),
                                                note("Scheduled report email delivery is planned later.", "Email distribution is not part of the current workflow."),
                                                note("Report column customization may be added later.", "Column layout is currently fixed."),
                                                note("Some report IDs may be source-mapped instead of exposing internal IDs.", "This is intentional for readability."),
                                                note("Language switching is planned for future release.", "Reports currently use the active help language."),
                                                note("AIVA report explanation is planned for future release.", "Automated explanations are not yet available.")
                                        )
                                ))
                        )
                ),
                page(
                        "CLINIC", "LEADS", "Leads", "lead",
                        "Leads manages incoming prospects, follow-up, and conversion tracking.",
                        roles("SALES", "CLINIC_ADMIN", "RECEPTIONIST"),
                        steps(
                                step("Capture lead", "Add lead source, mobile, and contact details."),
                                step("Follow up", "Record next action and follow-up date."),
                                step("Convert or lose", "Track final outcome and reason.")
                        ),
                        fields(
                                field("Lead name", false, "Lead contact name.", "Rahul", 80, "Optional or required depending on workflow", "Text"),
                                field("Mobile", false, "Optional Indian mobile.", "9876543210", 10, "Valid Indian mobile if present", "Phone"),
                                field("Email", false, "Optional email address.", "rahul@example.com", 120, "Valid email if present", "Email"),
                                field("Lead source", false, "Optional source of the lead.", "Referral", 40, "Optional enum", "Enum")
                        ),
                        validations(
                                validation("Mobile", "Optional Indian mobile if present.", false, "Phone", null, "9876543210"),
                                validation("Email", "Optional valid email.", false, "Email", null, "rahul@example.com")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Record the source", "Keep the lead source current for conversion analysis.")
                        ),
                        faq(
                                faqItem("How do I mark a lead lost?", "Choose a lost reason and save the final remarks.")
                        ),
                        related(
                                relatedItem("Campaigns", "CAMPAIGNS", "Campaigns often feed new leads.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "TENANT_MANAGEMENT", "Tenant Management", "domain",
                        "Tenant Management creates and updates clinic tenants and their platform settings.",
                        roles("PLATFORM_ADMIN"),
                        steps(
                                step("Create tenant", "Add the tenant name, code, and contact details."),
                                step("Configure access", "Link modules and services to the tenant."),
                                step("Publish settings", "Save the tenant configuration for clinic users.")
                        ),
                        fields(
                                field("Tenant name", true, "Display name for the clinic tenant.", "Arogia Clinic", 120, "Required", "Text"),
                                field("Contact email", false, "Optional tenant contact email.", "support@clinic.com", 120, "Valid email if present", "Email"),
                                field("Mobile", false, "Optional Indian mobile.", "9876543210", 10, "Valid Indian mobile if present", "Phone")
                        ),
                        validations(
                                validation("Tenant name", "Required.", true, "Text", 120, "Arogia Clinic")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Keep tenant code stable", "Do not rename tenant codes after production onboarding.")
                        ),
                        faq(
                                faqItem("Who can edit tenants?", "Only platform admins can manage tenant records.")
                        ),
                        related(
                                relatedItem("Platform Admin", "PLATFORM_ADMIN", "Manage platform CMS and configuration.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "CLINIC_PROFILE", "Clinic Profile", "store",
                        "Clinic profile stores the clinic's main contact, address, and publication settings.",
                        roles("CLINIC_ADMIN", "RECEPTIONIST"),
                        steps(
                                step("Edit profile", "Update the clinic name and contacts."),
                                step("Review address", "Keep address, country, state, city, and pincode accurate."),
                                step("Publish", "Save the clinic profile for public and internal use.")
                        ),
                        fields(
                                field("Clinic name", true, "Primary clinic name.", "Arogia Clinic", 120, "Required", "Text"),
                                field("Country", false, "Optional country.", "India", 60, "Optional", "Text"),
                                field("State", false, "Optional state.", "Maharashtra", 60, "Optional", "Text"),
                                field("City", false, "Optional city.", "Pune", 60, "Optional", "Text"),
                                field("Pincode", false, "Optional pincode when country is India.", "411001", 10, "Indian pincode if provided", "Text")
                        ),
                        validations(
                                validation("Clinic name", "Required.", true, "Text", 120, "Arogia Clinic"),
                                validation("Email", "Valid email if present.", false, "Email", null, "clinic@example.com"),
                                validation("Mobile", "Valid Indian mobile if present.", false, "Phone", null, "9876543210")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Keep contact details current", "Update address and mobile when the clinic moves.")
                        ),
                        faq(
                                faqItem("Does clinic profile affect public pages?", "Yes, publication fields may be used by the front office and patient portal.")
                        ),
                        related(
                                relatedItem("Tenant Management", "TENANT_MANAGEMENT", "Platform admins manage tenant setup.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                ),
                page(
                        "CLINIC", "USERS", "Users / Admins", "group",
                        "Users and admins manage role-based access for clinic operations.",
                        roles("CLINIC_ADMIN", "PLATFORM_ADMIN"),
                        steps(
                                step("Create user", "Add a staff member and assign roles."),
                                step("Activate access", "Set status and tenant permissions."),
                                step("Review permissions", "Keep access aligned with job responsibilities.")
                        ),
                        fields(
                                field("Name", true, "Staff member name.", "Anita", 80, "Required", "Text"),
                                field("Email", true, "User email.", "anita@example.com", 120, "Required valid email", "Email"),
                                field("Role", true, "Role assigned to the user.", "Receptionist", 60, "Required", "Enum")
                        ),
                        validations(
                                validation("Name", "Required.", true, "Text", 80, "Anita"),
                                validation("Email", "Valid email.", true, "Email", null, "anita@example.com"),
                                validation("Role", "Required.", true, "Enum", null, "Receptionist")
                        ),
                        errors(List.of()),
                        bestPractices(
                                note("Apply least privilege", "Assign only the permissions needed for the role.")
                        ),
                        faq(
                                faqItem("Who can create users?", "Platform and clinic admins, depending on tenant policy.")
                        ),
                        related(
                                relatedItem("Tenant Management", "TENANT_MANAGEMENT", "Access and tenant setup.")
                        ),
                        tips(List.of()),
                        limitations(List.of()),
                        links(List.of()),
                        audit(List.of())
                )
        );
    }

    private HelpPageSeed page(
            String moduleKey,
            String pageKey,
            String title,
            String icon,
            String description,
            List<String> allowedRoles,
            List<Map<String, Object>> workflow,
            List<Map<String, Object>> fields,
            List<Map<String, Object>> validationRules,
            List<Map<String, Object>> commonErrors,
            List<Map<String, Object>> bestPractices,
            List<Map<String, Object>> faqs,
            List<Map<String, Object>> relatedPages,
            List<Map<String, Object>> tips,
            List<Map<String, Object>> limitations,
            List<Map<String, Object>> links,
            List<Map<String, Object>> audit
    ) {
        return page(moduleKey, pageKey, title, icon, description, allowedRoles, List.of(), workflow, fields, validationRules, commonErrors, bestPractices, faqs, relatedPages, tips, limitations, links, audit);
    }

    private HelpPageSeed page(
            String moduleKey,
            String pageKey,
            String title,
            String icon,
            String description,
            List<String> allowedRoles,
            List<String> notIntendedFor,
            List<Map<String, Object>> workflow,
            List<Map<String, Object>> fields,
            List<Map<String, Object>> validationRules,
            List<Map<String, Object>> commonErrors,
            List<Map<String, Object>> bestPractices,
            List<Map<String, Object>> faqs,
            List<Map<String, Object>> relatedPages,
            List<Map<String, Object>> tips,
            List<Map<String, Object>> limitations,
            List<Map<String, Object>> links,
            List<Map<String, Object>> audit
    ) {
        List<HelpSectionSeed> sections = new ArrayList<>();
        sections.add(section("DESCRIPTION", "DESCRIPTION", 1, true, true, Map.of("title", title, "description", description)));
        sections.add(section("WORKFLOW", "WORKFLOW", 2, true, true, Map.of("steps", workflow)));
        sections.add(section("FIELD_TABLE", "FIELD_TABLE", 3, true, true, Map.of("fields", fields)));
        sections.add(section("VALIDATION_RULES", "VALIDATION_RULES", 4, true, true, Map.of("rules", validationRules)));
        sections.add(section("PERMISSIONS", "PERMISSIONS", 5, true, true, Map.of("permissions", allowedRoles)));
        sections.add(section("ROLES", "ROLES", 6, true, true, Map.of("description", "Who should use this page", "allowedRoles", allowedRoles, "notIntendedFor", notIntendedFor)));
        sections.add(section("COMMON_ERRORS", "COMMON_ERRORS", 7, true, true, Map.of("items", nonEmpty(commonErrors, error("No common errors documented yet.", "This section will be expanded as the workflow matures.", "Use the current workflow guidance and report issues to admins.")))));
        sections.add(section("BEST_PRACTICES", "BEST_PRACTICES", 8, true, true, Map.of("items", nonEmpty(bestPractices, note("No best practices documented yet.", "Add operational guidance when the workflow stabilizes.")))));
        sections.add(section("FAQ", "FAQ", 9, true, true, Map.of("items", nonEmpty(faqs, faqItem("No FAQ yet.", "This page does not have frequently asked questions documented yet.")))));
        sections.add(section("RELATED_PAGES", "RELATED_PAGES", 10, true, true, Map.of("items", nonEmpty(relatedPages, relatedItem("No related pages documented yet.", null, "Add cross-links when adjacent flows are available.")))));
        sections.add(section("TIPS", "TIPS", 11, true, true, Map.of("items", nonEmpty(tips, note("No tips documented yet.", "Add tips when common workflow shortcuts are identified.")))));
        sections.add(section("KNOWN_LIMITATIONS", "KNOWN_LIMITATIONS", 12, true, true, Map.of("items", nonEmpty(limitations, note("No known limitations documented yet.", "Document constraints when they become relevant.")))));
        sections.add(section("LINKS", "LINKS", 13, true, true, Map.of("items", nonEmpty(links, link("No links documented yet.", "", "Add external references or SOP links when available.")))));
        sections.add(section("AUDIT", "AUDIT", 14, true, true, Map.of("items", nonEmpty(audit, note("No audit notes documented yet.", "Add audit fields when the workflow requires traceability.")))));
        return new HelpPageSeed(moduleKey, pageKey, title, icon, sections);
    }

    private HelpSectionSeed section(String sectionKey, String sectionType, int order, boolean collapsible, boolean active, Object content) {
        return new HelpSectionSeed(sectionKey, sectionType, order, collapsible, active, content);
    }

    private List<Map<String, Object>> steps(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> steps(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> fields(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> fields(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> validations(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> validations(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> errors(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> errors(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> bestPractices(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> bestPractices(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> faq(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> faq(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> related(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> related(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> tips(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> tips(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> limitations(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> limitations(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> links(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> links(List<?> ignored) {
        return List.of();
    }

    private List<Map<String, Object>> audit(Map<String, Object>... items) {
        return List.of(items);
    }

    private List<Map<String, Object>> audit(List<?> ignored) {
        return List.of();
    }

    private Map<String, Object> step(String title, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("description", description);
        return row;
    }

    private Map<String, Object> field(String fieldName, boolean required, String description, String example, Integer maxLength, String rule, String type) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fieldName", fieldName);
        row.put("required", required);
        row.put("description", description);
        row.put("example", example);
        row.put("maxLength", maxLength);
        row.put("rule", rule);
        row.put("type", type);
        return row;
    }

    private Map<String, Object> validation(String field, String rule, boolean required, String type, Integer maxLength, String example) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("field", field);
        row.put("rule", rule);
        row.put("required", required);
        row.put("type", type);
        row.put("maxLength", maxLength);
        row.put("example", example);
        return row;
    }

    private Map<String, Object> faqItem(String question, String answer) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question", question);
        row.put("answer", answer);
        return row;
    }

    private Map<String, Object> error(String error, String cause, String resolution) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("error", error);
        row.put("cause", cause);
        row.put("resolution", resolution);
        return row;
    }

    private Map<String, Object> note(String title, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("description", description);
        return row;
    }

    private Map<String, Object> relatedItem(String title, String pageKey, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("pageKey", pageKey);
        row.put("description", description);
        return row;
    }

    private List<Map<String, Object>> nonEmpty(List<Map<String, Object>> items, Map<String, Object> placeholder) {
        return items == null || items.isEmpty() ? List.of(placeholder) : items;
    }

    private Map<String, Object> link(String title, String url, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "LINK");
        row.put("title", title);
        row.put("url", url);
        row.put("description", description);
        return row;
    }

    private List<String> roles(String... roles) {
        return List.of(roles);
    }

    private String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private record HelpPageSeed(String moduleKey, String pageKey, String title, String icon, List<HelpSectionSeed> sections) {
    }

    private record HelpSectionSeed(String sectionKey, String sectionType, int displayOrder, boolean collapsible, boolean active, Object content) {
    }
}
