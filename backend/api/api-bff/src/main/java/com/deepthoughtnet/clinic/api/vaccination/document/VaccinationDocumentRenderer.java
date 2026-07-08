package com.deepthoughtnet.clinic.api.vaccination.document;

import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VaccinationDocumentRenderer {
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 44f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a", Locale.ENGLISH);

    public byte[] renderPassport(
            ClinicProfileRecord clinic,
            PatientVaccinationDocumentContext context,
            List<PatientVaccinationRecord> history,
            PassportSummary summary,
            String passportNumber,
            UUID generatedBy,
            String generatedByName
    ) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PdfCursor cursor = new PdfCursor(document, page)) {
                header(cursor, clinic, "Immunization Passport", passportNumber);
                patientBlock(cursor, context, generatedByName);
                summaryBlock(cursor, summary);
                timelineBlock(cursor, history);
                footer(cursor, clinic, generatedByName);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to render vaccination passport PDF", ex);
        }
    }

    public byte[] renderCertificate(
            ClinicProfileRecord clinic,
            PatientVaccinationDocumentContext context,
            PatientVaccinationRecord vaccination,
            List<PatientVaccinationRecord> history,
            String certificateTitle,
            String certificateNumber,
            String route,
            String administrationSite,
            UUID generatedBy,
            String generatedByName
    ) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PdfCursor cursor = new PdfCursor(document, page)) {
                header(cursor, clinic, certificateTitle, certificateNumber);
                patientBlock(cursor, context, generatedByName);
                if (vaccination != null) {
                    vaccinationBlock(cursor, vaccination, route, administrationSite);
                }
                historyBlock(cursor, history);
                signatureBlock(cursor, generatedByName);
                footer(cursor, clinic, generatedByName);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to render vaccination certificate PDF", ex);
        }
    }

    private void header(PdfCursor cursor, ClinicProfileRecord clinic, String title, String documentNumber) throws IOException {
        cursor.ensureSpace(90f);
        writeLine(cursor, safe(clinic == null ? null : clinic.displayName(), "Clinic"), 18f, cursor.margin(), cursor.y(), boldFont());
        cursor.y(cursor.y() - 16f);
        writeLine(cursor, safe(clinic == null ? null : clinic.clinicName(), null), 10f, cursor.margin(), cursor.y(), regularFont());
        cursor.y(cursor.y() - 8f);
        drawLine(cursor, 1f, new Color(215, 223, 230));
        cursor.y(cursor.y() - 18f);
        writeLine(cursor, title, 16f, cursor.margin(), cursor.y(), boldFont());
        cursor.y(cursor.y() - 16f);
        writeLine(cursor, "Document No: " + safe(documentNumber, "—"), 9.5f, cursor.margin(), cursor.y(), regularFont());
        cursor.y(cursor.y() - 6f);
    }

    private void patientBlock(PdfCursor cursor, PatientVaccinationDocumentContext context, String generatedByName) throws IOException {
        cursor.ensureSpace(120f);
        writeSectionTitle(cursor, "Patient Details");
        List<String> lines = new ArrayList<>();
        lines.add("Patient: " + safe(context.patientName(), "—"));
        lines.add("Patient Number: " + safe(context.patientNumber(), "—"));
        lines.add("DOB: " + safe(context.dateOfBirth() == null ? null : DATE.format(context.dateOfBirth()), "—"));
        lines.add("Age: " + safe(context.ageLabel(), "—"));
        lines.add("Gender: " + safe(context.gender(), "—"));
        lines.add("Mobile: " + safe(context.mobile(), "—"));
        lines.add("Blood Group: " + safe(context.bloodGroup(), "—"));
        lines.add("Completion: " + context.completionPercent() + "%");
        lines.add("Current Schedule: " + safe(context.scheduleType(), "Standard"));
        lines.add("Generated Date: " + DATE_TIME.format(OffsetDateTime.now()));
        lines.add("Generated By: " + safe(generatedByName, "System"));
        for (String line : lines) {
            cursor.writeWrapped(line, 9.4f, regularFont(), false);
        }
    }

    private void summaryBlock(PdfCursor cursor, PassportSummary summary) throws IOException {
        if (summary == null) {
            return;
        }
        cursor.ensureSpace(90f);
        writeSectionTitle(cursor, "Summary");
        cursor.writeWrapped("Completed Vaccines: " + summary.completed(), 9.2f, regularFont(), false);
        cursor.writeWrapped("Applicable Vaccines: " + summary.applicable(), 9.2f, regularFont(), false);
        cursor.writeWrapped("Upcoming: " + summary.upcoming(), 9.2f, regularFont(), false);
        cursor.writeWrapped("Due: " + summary.due(), 9.2f, regularFont(), false);
        cursor.writeWrapped("Overdue: " + summary.overdue(), 9.2f, regularFont(), false);
        cursor.writeWrapped("Next Due Vaccine: " + safe(summary.nextDueVaccine(), "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Next Due Date: " + safe(summary.nextDueDate() == null ? null : DATE.format(summary.nextDueDate()), "—"), 9.2f, regularFont(), false);
    }

    private void timelineBlock(PdfCursor cursor, List<PatientVaccinationRecord> history) throws IOException {
        cursor.ensureSpace(120f);
        writeSectionTitle(cursor, "Vaccination History");
        if (history == null || history.isEmpty()) {
            cursor.writeWrapped("No vaccination records available.", 9.2f, regularFont(), false);
            return;
        }
        for (PatientVaccinationRecord vaccination : history.stream().limit(10).toList()) {
            cursor.writeWrapped(
                    vaccination.vaccineName() + " • " + safe(vaccination.givenDate() == null ? null : DATE.format(vaccination.givenDate()), "—") + " • " + safe(vaccination.verifiedStatus(), "—"),
                    9.1f,
                    regularFont(),
                    false
            );
        }
    }

    private void vaccinationBlock(PdfCursor cursor, PatientVaccinationRecord vaccination, String route, String administrationSite) throws IOException {
        cursor.ensureSpace(120f);
        writeSectionTitle(cursor, "Vaccine Details");
        cursor.writeWrapped("Vaccine: " + safe(vaccination.vaccineName(), "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Dose: " + safe(vaccination.doseNumber() == null ? null : vaccination.doseNumber().toString(), "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Manufacturer: " + safe(vaccination.inventoryBatchManufacturer(), "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Brand: " + safe(vaccination.vaccineName(), "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Lot / Batch: " + safe(vaccination.batchNumber(), vaccination.inventoryBatchNumber()), 9.2f, regularFont(), false);
        cursor.writeWrapped("Expiry: " + safe(vaccination.inventoryBatchExpiryDate() == null ? null : DATE.format(vaccination.inventoryBatchExpiryDate()), "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Administration Site: " + safe(administrationSite, "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Route: " + safe(route, "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Given Date: " + safe(vaccination.givenDate() == null ? null : DATE.format(vaccination.givenDate()), "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Administered By: " + safe(vaccination.administeredByUserName(), "—"), 9.2f, regularFont(), false);
        cursor.writeWrapped("Verification Status: " + safe(vaccination.verifiedStatus(), "—"), 9.2f, regularFont(), false);
    }

    private void historyBlock(PdfCursor cursor, List<PatientVaccinationRecord> history) throws IOException {
        cursor.ensureSpace(100f);
        writeSectionTitle(cursor, "Vaccination History Snapshot");
        if (history == null || history.isEmpty()) {
            cursor.writeWrapped("No historical vaccination entries found.", 9.2f, regularFont(), false);
            return;
        }
        history.stream().limit(8).forEach(record -> {
            try {
                cursor.writeWrapped(record.vaccineName() + " • " + safe(record.givenDate() == null ? null : DATE.format(record.givenDate()), "—") + " • " + safe(record.verifiedStatus(), "—"), 9.1f, regularFont(), false);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private void signatureBlock(PdfCursor cursor, String generatedByName) throws IOException {
        cursor.ensureSpace(100f);
        writeSectionTitle(cursor, "Doctor / Clinic Signature");
        cursor.writeWrapped("______________________________", 10f, regularFont(), false);
        cursor.writeWrapped("Authorized signatory", 9f, regularFont(), false);
        cursor.writeWrapped("Generated by: " + safe(generatedByName, "System"), 9f, regularFont(), false);
        cursor.writeWrapped("QR Code: [placeholder]", 9f, regularFont(), false);
    }

    private void footer(PdfCursor cursor, ClinicProfileRecord clinic, String generatedByName) throws IOException {
        cursor.y(MARGIN + 10f);
        drawLine(cursor, 1f, new Color(215, 223, 230));
        cursor.y(cursor.y() - 12f);
        cursor.writeWrapped(
                safe(clinic == null ? null : clinic.clinicName(), "Clinic") + " • Generated by " + safe(generatedByName, "System"),
                8.5f,
                regularFont(),
                false
        );
    }

    private void writeSectionTitle(PdfCursor cursor, String title) throws IOException {
        cursor.ensureSpace(22f);
        writeLine(cursor, title, 11.2f, cursor.margin(), cursor.y(), boldFont());
        cursor.y(cursor.y() - 7f);
    }

    private void drawLine(PdfCursor cursor, float thickness, Color color) throws IOException {
        cursor.content().setStrokingColor(color);
        cursor.content().setLineWidth(thickness);
        cursor.content().moveTo(cursor.margin(), cursor.y());
        cursor.content().lineTo(cursor.margin() + CONTENT_WIDTH, cursor.y());
        cursor.content().stroke();
    }

    private String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private PDFont regularFont() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private PDFont boldFont() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    private void writeLine(PdfCursor cursor, String text, float size, float x, float y, PDFont font) throws IOException {
        cursor.content().beginText();
        cursor.content().setFont(font, size);
        cursor.content().newLineAtOffset(x, y);
        cursor.content().showText(text == null ? "" : text);
        cursor.content().endText();
    }

    public record PatientVaccinationDocumentContext(
            UUID patientId,
            String patientName,
            String patientNumber,
            LocalDate dateOfBirth,
            String ageLabel,
            String gender,
            String mobile,
            String bloodGroup,
            String scheduleType,
            int completionPercent
    ) {}

    public record PassportSummary(
            int completed,
            int applicable,
            int upcoming,
            int due,
            int overdue,
            String nextDueVaccine,
            LocalDate nextDueDate,
            int completionPercent
    ) {}

    private final class PdfCursor implements AutoCloseable {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream content;
        private float y = PAGE_HEIGHT - MARGIN;

        private PdfCursor(PDDocument document, PDPage page) throws IOException {
            this.document = document;
            this.page = page;
            this.content = new PDPageContentStream(document, page);
        }

        private void ensureSpace(float required) throws IOException {
            if (y - required > MARGIN) {
                return;
            }
            content.close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        private void writeWrapped(String text, float size, PDFont font, boolean bold) throws IOException {
            for (String line : wrap(text, font, size, CONTENT_WIDTH)) {
                ensureSpace(size + 3f);
                writeLine(this, line, size, margin(), y, font);
                y -= size + 2.5f;
            }
        }

        private List<String> wrap(String text, PDFont font, float size, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            String remaining = text == null ? "" : text.trim();
            if (remaining.isEmpty()) {
                lines.add("");
                return lines;
            }
            while (!remaining.isEmpty()) {
                int cut = remaining.length();
                while (cut > 0 && font.getStringWidth(remaining.substring(0, cut)) / 1000f * size > width) {
                    cut--;
                }
                if (cut <= 0) {
                    cut = Math.min(remaining.length(), 80);
                }
                int space = remaining.lastIndexOf(' ', cut);
                if (space > 0 && cut < remaining.length()) {
                    cut = space;
                }
                lines.add(remaining.substring(0, cut).trim());
                remaining = remaining.substring(Math.min(cut, remaining.length())).trim();
            }
            return lines;
        }

        private float margin() {
            return MARGIN;
        }

        private float y() {
            return y;
        }

        private void y(float value) {
            y = value;
        }

        private PDPageContentStream content() {
            return content;
        }

        @Override
        public void close() throws IOException {
            content.close();
        }
    }
}
