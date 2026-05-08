package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentTextExtractionResult;
import com.deepthoughtnet.clinic.ocr.spi.OcrDocument;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ClinicalDocumentTextExtractionService {
    private final ObjectProvider<OcrProvider> ocrProvider;

    public ClinicalDocumentTextExtractionService(ObjectProvider<OcrProvider> ocrProvider) {
        this.ocrProvider = ocrProvider;
    }

    public ClinicalDocumentTextExtractionResult extract(ClinicalDocumentEntity document, byte[] bytes) {
        if (document == null || bytes == null || bytes.length == 0) {
            return new ClinicalDocumentTextExtractionResult(null, "NONE", "");
        }

        String mediaType = normalize(document.getMediaType());
        if (mediaType.equals("application/pdf")) {
            String extracted = extractPdfText(bytes);
            if (looksUsable(extracted)) {
                return new ClinicalDocumentTextExtractionResult("PDFBOX", "COMPLETED", extracted);
            }
            return ocr(bytes, document.getOriginalFilename(), document.getMediaType());
        }
        if (mediaType.startsWith("image/")) {
            return ocr(bytes, document.getOriginalFilename(), document.getMediaType());
        }
        return new ClinicalDocumentTextExtractionResult("NONE", "UNSUPPORTED", "");
    }

    private ClinicalDocumentTextExtractionResult ocr(byte[] bytes, String filename, String mediaType) {
        OcrProvider provider = ocrProvider == null ? null : ocrProvider.getIfAvailable();
        if (provider == null) {
            return new ClinicalDocumentTextExtractionResult("NONE", "UNAVAILABLE", "");
        }
        try {
            provider.validateReady();
            return new ClinicalDocumentTextExtractionResult(
                    provider.providerName(),
                    "COMPLETED",
                    normalizeWhitespace(provider.extractText(new OcrDocument(filename, mediaType, bytes)).text())
            );
        } catch (RuntimeException ex) {
            return new ClinicalDocumentTextExtractionResult(provider.providerName(), "FAILED", "");
        }
    }

    private String extractPdfText(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return normalizeWhitespace(stripper.getText(pdf));
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean looksUsable(String text) {
        return text != null && text.trim().length() >= 40;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', '\n').replaceAll("\n{3,}", "\n\n").trim();
    }
}
