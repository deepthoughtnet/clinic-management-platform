package com.deepthoughtnet.clinic.ocr.tesseract;

import com.deepthoughtnet.clinic.ocr.spi.OcrDocument;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import com.deepthoughtnet.clinic.ocr.spi.OcrResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression(
        "'${clinic.ocr.enabled:true}' == 'true' "
                + "&& '${clinic.ocr.provider:TESSERACT}'.equalsIgnoreCase('TESSERACT')"
)
public class TesseractOcrProvider implements OcrProvider {
    private final String executablePath;
    private final String dataPath;
    private final String language;
    private final int renderDpi;
    private final int pageSegMode;
    private final int ocrEngineMode;
    private final boolean requireDataPath;
    private final int maxPages;

    public TesseractOcrProvider(
            @Value("${clinic.ocr.tesseract.executablePath:tesseract}") String executablePath,
            @Value("${clinic.ocr.tesseract.dataPath:}") String dataPath,
            @Value("${clinic.ocr.tesseract.language:eng}") String language,
            @Value("${clinic.ocr.tesseract.renderDpi:200}") int renderDpi,
            @Value("${clinic.ocr.tesseract.pageSegMode:1}") int pageSegMode,
            @Value("${clinic.ocr.tesseract.ocrEngineMode:1}") int ocrEngineMode,
            @Value("${clinic.ocr.tesseract.requireDataPath:false}") boolean requireDataPath,
            @Value("${clinic.ocr.tesseract.maxPages:10}") int maxPages
    ) {
        this.executablePath = executablePath;
        this.dataPath = dataPath;
        this.language = language;
        this.renderDpi = renderDpi;
        this.pageSegMode = pageSegMode;
        this.ocrEngineMode = ocrEngineMode;
        this.requireDataPath = requireDataPath;
        this.maxPages = maxPages <= 0 ? 10 : maxPages;
    }

    @Override
    public String providerName() {
        return "TESSERACT";
    }

    @Override
    public void validateReady() {
        validateConfiguration();
    }

    @Override
    public OcrResult extractText(OcrDocument document) {
        validateDocument(document);
        validateConfiguration();
        try {
            String mediaType = normalize(document.mediaType());
            String text;
            if (mediaType.equals("application/pdf")) {
                text = extractFromPdf(document.bytes());
            } else if (mediaType.startsWith("image/")) {
                text = extractFromImage(document.bytes());
            } else {
                throw new IllegalArgumentException("Unsupported OCR media type: " + document.mediaType());
            }
            return new OcrResult(providerName(), normalizeWhitespace(text));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Tesseract OCR failed safely. Check document readability and OCR configuration.", ex);
        }
    }

    private void validateDocument(OcrDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document is required");
        }
        if (document.bytes() == null || document.bytes().length == 0) {
            throw new IllegalArgumentException("document bytes are required");
        }
    }

    private void validateConfiguration() {
        if (isBlank(language)) {
            throw new IllegalStateException("Tesseract OCR language is required");
        }
        if (isBlank(executablePath)) {
            throw new IllegalStateException("Tesseract executable path is required: clinic.ocr.tesseract.executablePath");
        }
        if (renderDpi < 72 || renderDpi > 600) {
            throw new IllegalStateException("Tesseract renderDpi must be between 72 and 600");
        }
        if (pageSegMode < 0 || pageSegMode > 13) {
            throw new IllegalStateException("Tesseract pageSegMode must be between 0 and 13");
        }
        if (ocrEngineMode < 0 || ocrEngineMode > 3) {
            throw new IllegalStateException("Tesseract ocrEngineMode must be between 0 and 3");
        }
        if (requireDataPath && isBlank(dataPath)) {
            throw new IllegalStateException("Tesseract tessdata path is required: clinic.ocr.tesseract.dataPath");
        }
        if (!isBlank(dataPath)) {
            Path tessdata = Path.of(dataPath);
            if (!Files.isDirectory(tessdata)) {
                throw new IllegalStateException("Tesseract tessdata path does not exist or is not a directory: " + dataPath);
            }
            Path trainedData = tessdata.resolve(language + ".traineddata");
            if (!Files.isRegularFile(trainedData)) {
                throw new IllegalStateException("Tesseract traineddata file not found for language '" + language + "': " + trainedData);
            }
        }
    }

    private String extractFromPdf(byte[] bytes) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            int pages = pdf.getNumberOfPages();
            if (pages <= 0) {
                throw new IllegalArgumentException("PDF has no pages");
            }
            if (pages > maxPages) {
                throw new IllegalArgumentException("PDF page count exceeds OCR limit: " + pages + " > " + maxPages);
            }
            PDFRenderer renderer = new PDFRenderer(pdf);
            StringBuilder text = new StringBuilder();
            Tesseract tesseract = newTesseract();
            for (int page = 0; page < pages; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, renderDpi);
                String pageText = tesseract.doOCR(image);
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append(pageText == null ? "" : pageText);
            }
            return text.toString();
        }
    }

    private String extractFromImage(byte[] bytes) throws Exception {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalArgumentException("Unable to decode image for OCR");
            }
            return newTesseract().doOCR(image);
        }
    }

    private Tesseract newTesseract() {
        validateConfiguration();
        Tesseract tesseract = new Tesseract();
        if (!isBlank(dataPath)) {
            tesseract.setDatapath(dataPath);
        }
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(pageSegMode);
        tesseract.setOcrEngineMode(ocrEngineMode);
        tesseract.setVariable("user_defined_dpi", String.valueOf(renderDpi));
        tesseract.setVariable("preserve_interword_spaces", "1");
        return tesseract;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', '\n')
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
