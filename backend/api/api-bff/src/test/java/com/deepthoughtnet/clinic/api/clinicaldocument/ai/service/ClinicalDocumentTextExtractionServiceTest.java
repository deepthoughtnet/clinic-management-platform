package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentTextExtractionResult;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ClinicalDocumentTextExtractionServiceTest {

    @Test
    void returnsUnavailableWhenNoOcrProviderConfigured() {
        @SuppressWarnings("unchecked")
        ObjectProvider<OcrProvider> providerRef = mock(ObjectProvider.class);
        when(providerRef.getIfAvailable()).thenReturn(null);
        ClinicalDocumentTextExtractionService service = new ClinicalDocumentTextExtractionService(providerRef);

        ClinicalDocumentTextExtractionResult result = service.extract(imageDocument(), "image-data".getBytes());

        assertThat(result.provider()).isEqualTo("NONE");
        assertThat(result.status()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void returnsFriendlyFailedStatusWhenOcrThrows() {
        OcrProvider provider = mock(OcrProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OcrProvider> providerRef = mock(ObjectProvider.class);
        when(providerRef.getIfAvailable()).thenReturn(provider);
        when(provider.providerName()).thenReturn("TESSERACT");
        org.mockito.Mockito.doThrow(new IllegalStateException("tesseract binary missing"))
                .when(provider)
                .validateReady();
        ClinicalDocumentTextExtractionService service = new ClinicalDocumentTextExtractionService(providerRef);

        ClinicalDocumentTextExtractionResult result = service.extract(imageDocument(), "image-data".getBytes());

        assertThat(result.provider()).isEqualTo("TESSERACT");
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.text()).isEmpty();
    }

    private ClinicalDocumentEntity imageDocument() {
        ClinicalDocumentEntity entity = ClinicalDocumentEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                ClinicalDocumentType.OTHER,
                "scan.png",
                "image/png",
                64,
                "hash",
                "storage-key",
                null,
                null,
                null,
                null
        );
        try {
            Field idField = ClinicalDocumentEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, UUID.randomUUID());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }
}
