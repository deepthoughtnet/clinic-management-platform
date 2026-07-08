package com.deepthoughtnet.clinic.api.vaccination.document;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaccinationCertificateService {
    private final VaccinationDocumentService delegate;

    public VaccinationCertificateService(VaccinationDocumentService delegate) {
        this.delegate = delegate;
    }

    @Transactional
    public GeneratedVaccinationDocumentResponse generateCertificate(UUID tenantId, UUID patientId, VaccinationCertificateRequest request, UUID actorAppUserId) {
        return delegate.generateCertificate(tenantId, patientId, request, actorAppUserId);
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(UUID tenantId, UUID documentId) {
        return delegate.downloadPdf(tenantId, documentId);
    }

    @Transactional
    public void sendDocument(UUID tenantId, UUID documentId, String channel, UUID actorAppUserId) {
        delegate.sendDocument(tenantId, documentId, channel, actorAppUserId);
    }
}
