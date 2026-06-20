package com.deepthoughtnet.clinic.api.help.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpPageRepository extends JpaRepository<HelpPageEntity, UUID> {
    Optional<HelpPageEntity> findByPageKeyIgnoreCase(String pageKey);
    List<HelpPageEntity> findByStatusAndActiveTrueOrderByModuleKeyAscTitleAsc(String status);
    List<HelpPageEntity> findAllByOrderByModuleKeyAscTitleAsc();
}
