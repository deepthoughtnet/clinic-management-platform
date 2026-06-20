package com.deepthoughtnet.clinic.api.help.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpContentRepository extends JpaRepository<HelpContentEntity, UUID> {
    List<HelpContentEntity> findBySection_IdOrderByVersionDescCreatedAtDesc(UUID sectionId);
    List<HelpContentEntity> findBySection_Page_IdOrderByVersionDescCreatedAtDesc(UUID pageId);
    Optional<HelpContentEntity> findFirstBySection_IdAndLanguageCodeIgnoreCaseAndVersionOrderByCreatedAtDesc(UUID sectionId, String languageCode, int version);
    List<HelpContentEntity> findBySection_Page_PageKeyIgnoreCaseAndVersion(String pageKey, int version);
}
