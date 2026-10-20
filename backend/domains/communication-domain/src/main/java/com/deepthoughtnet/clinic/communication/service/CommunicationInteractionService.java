package com.deepthoughtnet.clinic.communication.service;

import com.deepthoughtnet.clinic.communication.db.CommunicationInteractionEntity;
import com.deepthoughtnet.clinic.communication.db.CommunicationInteractionRepository;
import com.deepthoughtnet.clinic.communication.model.ConversationPartyType;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Best-effort durable usage/audit lifecycle for provider interactions. */
@Service
public class CommunicationInteractionService {
    private static final Logger log = LoggerFactory.getLogger(CommunicationInteractionService.class);
    private final CommunicationInteractionRepository repository;

    public CommunicationInteractionService(CommunicationInteractionRepository repository) { this.repository = repository; }

    @Transactional
    public UUID startInbound(String providerCallId, UUID tenantId, UUID channelId, UUID clinicId, UUID locationId,
            String provider, String destinationReference, String callerNumberMasked, String identificationStatus,
            UUID patientId, ConversationPartyType party, int candidateCount, String callStatus, OffsetDateTime startedAt) {
        return safely(providerCallId, () -> {
            CommunicationInteractionEntity row = repository.findByProviderCallId(providerCallId).orElseGet(() ->
                    CommunicationInteractionEntity.create(providerCallId, tenantId, channelId, clinicId, locationId,
                            "VOICE", provider, "INBOUND", destinationReference, callerNumberMasked,
                            identificationStatus, patientId, party, candidateCount, callStatus, startedAt));
            row.attachRouting(tenantId, channelId, clinicId, locationId, destinationReference, callerNumberMasked);
            row.attachIdentification(identificationStatus, patientId, party, candidateCount);
            return repository.save(row).getId();
        });
    }

    @Transactional
    public void updateInbound(String providerCallId, Consumer<CommunicationInteractionEntity> update) {
        safely(providerCallId, () -> {
            repository.findByProviderCallId(providerCallId).ifPresent(row -> {
                update.accept(row);
                repository.save(row);
            });
            return null;
        });
    }

    private <T> T safely(String providerCallId, java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException ex) {
            log.warn("COMMUNICATION_INTERACTION_PERSISTENCE_FAILED providerCallId={} reason={}",
                    providerCallId, ex.getClass().getSimpleName());
            return null;
        }
    }
}
