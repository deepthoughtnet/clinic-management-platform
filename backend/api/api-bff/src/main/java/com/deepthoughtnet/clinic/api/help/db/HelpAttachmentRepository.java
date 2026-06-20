package com.deepthoughtnet.clinic.api.help.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpAttachmentRepository extends JpaRepository<HelpAttachmentEntity, UUID> {
    List<HelpAttachmentEntity> findBySection_IdOrderByDisplayOrderAsc(UUID sectionId);
    List<HelpAttachmentEntity> findBySection_Page_IdOrderByDisplayOrderAsc(UUID pageId);
}
