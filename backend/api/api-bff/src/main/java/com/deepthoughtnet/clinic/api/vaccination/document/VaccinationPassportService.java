package com.deepthoughtnet.clinic.api.vaccination.document;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaccinationPassportService {
    private final VaccinationDocumentService delegate;

    public VaccinationPassportService(VaccinationDocumentService delegate) {
        this.delegate = delegate;
    }

    @Transactional
    public GeneratedVaccinationDocumentResponse generatePassport(UUID tenantId, UUID patientId, UUID actorAppUserId) {
        return delegate.generatePassport(tenantId, patientId, actorAppUserId);
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
