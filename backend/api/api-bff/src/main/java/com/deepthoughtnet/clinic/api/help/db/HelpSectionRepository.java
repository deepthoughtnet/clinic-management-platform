package com.deepthoughtnet.clinic.api.help.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpSectionRepository extends JpaRepository<HelpSectionEntity, UUID> {
    Optional<HelpSectionEntity> findByPage_IdAndSectionKeyIgnoreCase(UUID pageId, String sectionKey);
    List<HelpSectionEntity> findByPage_IdOrderByDisplayOrderAsc(UUID pageId);
    List<HelpSectionEntity> findByPage_PageKeyIgnoreCaseOrderByDisplayOrderAsc(String pageKey);
}
