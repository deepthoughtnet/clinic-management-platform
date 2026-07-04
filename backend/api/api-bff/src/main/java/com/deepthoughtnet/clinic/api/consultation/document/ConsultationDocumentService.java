package com.deepthoughtnet.clinic.api.consultation.document;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.DocumentDownloadUrlResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentUploadCommand;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConsultationDocumentService {
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 48f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);

    private final ConsultationService consultationService;
    private final ClinicalDocumentService clinicalDocumentService;

    public ConsultationDocumentService(ConsultationService consultationService, ClinicalDocumentService clinicalDocumentService) {
        this.consultationService = consultationService;
        this.clinicalDocumentService = clinicalDocumentService;
    }

    @Transactional
    public GeneratedConsultationDocumentResponse generate(UUID tenantId, UUID consultationId, ConsultationGeneratedDocumentRequest request, UUID actorAppUserId) {
        ConsultationRecord consultation = consultationService.findById(tenantId, consultationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
        ClinicalDocumentType documentType = parseDocumentType(request.documentType());
        String title = normalizeRequired(request.title(), "Title is required");
        String body = normalizeRequired(request.body(), "Body is required");
        String fileName = safeFilename(title) + ".pdf";
        byte[] pdf = renderPdf(
                title,
                request.language(),
                request.notes(),
                consultation,
                body
        );
        ClinicalDocumentRecord record = toRecord(clinicalDocumentService.upload(new ClinicalDocumentUploadCommand(
                tenantId,
                consultation.patientId(),
                consultationId,
                actorAppUserId,
                documentType,
                title,
                LocalDate.now(ZoneOffset.UTC),
                "DOCTOR",
                "CONSULTATION",
                consultationId.toString(),
                normalizeVisibility(request.visibility()),
                fileName,
                "application/pdf",
                pdf,
                request.notes()
        )));
        DocumentDownloadUrlResponse download = new DocumentDownloadUrlResponse(clinicalDocumentService.downloadUrl(tenantId, record.id(), DOWNLOAD_TTL), String.valueOf(DOWNLOAD_TTL.toSeconds()));
        return new GeneratedConsultationDocumentResponse(
                record.id().toString(),
                download.url(),
                download.expiresInSeconds(),
                fileName,
                title,
                documentType.name()
        );
    }

    private ClinicalDocumentRecord toRecord(ClinicalDocumentRecord record) {
        return record;
    }

    private byte[] renderPdf(String title, String language, String notes, ConsultationRecord consultation, String body) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PdfCursor cursor = new PdfCursor(document, page)) {
                drawTitle(cursor, title);
                drawMeta(cursor, consultation, language, notes);
                drawBody(cursor, body);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to render consultation document PDF", ex);
        }
    }

    private void drawTitle(PdfCursor cursor, String title) throws IOException {
        cursor.ensureSpace(40);
        writeLine(cursor, title, 17f, cursor.margin(), cursor.y(), boldFont());
        cursor.y(cursor.y() - 18f);
        cursor.content().setStrokingColor(new Color(220, 227, 233));
        cursor.content().moveTo(cursor.margin(), cursor.y());
        cursor.content().lineTo(cursor.margin() + CONTENT_WIDTH, cursor.y());
        cursor.content().stroke();
        cursor.y(cursor.y() - 16f);
    }

    private void drawMeta(PdfCursor cursor, ConsultationRecord consultation, String language, String notes) throws IOException {
        List<String> meta = new ArrayList<>();
        meta.add("Patient: " + safe(consultation.patientName()));
        meta.add("Doctor: " + safe(consultation.doctorName()));
        meta.add("Consultation: " + safe(consultation.id() == null ? null : consultation.id().toString()));
        meta.add("Language: " + safe(StringUtils.hasText(language) ? language : "English"));
        if (StringUtils.hasText(notes)) {
            meta.add("Notes: " + notes.trim());
        }
        for (String line : meta) {
            cursor.writeWrapped(line, 9.2f, regularFont(), false);
        }
        cursor.y(cursor.y() - 8f);
    }

    private void drawBody(PdfCursor cursor, String body) throws IOException {
        for (String paragraph : body.split("\\r?\\n")) {
            if (!StringUtils.hasText(paragraph)) {
                cursor.y(cursor.y() - 4f);
                continue;
            }
            cursor.writeWrapped(paragraph.trim(), 9.3f, regularFont(), false);
        }
    }

    private ClinicalDocumentType parseDocumentType(String value) {
        if (!StringUtils.hasText(value)) {
            return ClinicalDocumentType.ATTACHMENT;
        }
        try {
            return ClinicalDocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ClinicalDocumentType.ATTACHMENT;
        }
    }

    private String normalizeVisibility(String visibility) {
        if (!StringUtils.hasText(visibility)) {
            return "INTERNAL_ONLY";
        }
        String normalized = visibility.trim().toUpperCase(Locale.ROOT);
        return "PATIENT_VISIBLE".equals(normalized) ? "PATIENT_VISIBLE" : "INTERNAL_ONLY";
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String safeFilename(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
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
                ensureSpace(16f);
                writeLine(this, line, size, MARGIN, y, bold ? boldFont() : font);
                y -= 12f;
            }
        }

        private List<String> wrap(String text, PDFont font, float size, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            String normalized = text == null ? "" : text.replace('\t', ' ').trim();
            if (normalized.isBlank()) {
                return List.of("");
            }
            StringBuilder current = new StringBuilder();
            for (String word : normalized.split("\\s+")) {
                String trial = current.isEmpty() ? word : current + " " + word;
                if (font.getStringWidth(trial) / 1000f * size <= width) {
                    current.setLength(0);
                    current.append(trial);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                    }
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        private float margin() {
            return MARGIN;
        }

        private float y() {
            return y;
        }

        private void y(float nextY) {
            y = nextY;
        }

        private PDPageContentStream content() {
            return content;
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                content.close();
            }
        }
    }
}
