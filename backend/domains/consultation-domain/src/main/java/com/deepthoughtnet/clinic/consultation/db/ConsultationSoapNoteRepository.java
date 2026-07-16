package com.deepthoughtnet.clinic.consultation.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationSoapNoteRepository extends JpaRepository<ConsultationSoapNoteEntity, UUID> {
    Optional<ConsultationSoapNoteEntity> findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(UUID tenantId, UUID consultationId);

    List<ConsultationSoapNoteEntity> findByTenantIdAndConsultationIdOrderByVersionNumberDesc(UUID tenantId, UUID consultationId);
}
