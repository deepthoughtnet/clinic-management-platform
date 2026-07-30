package com.deepthoughtnet.clinic.discover.reference.db;

import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverReferenceOptionRepository extends JpaRepository<DiscoverReferenceOptionEntity, java.util.UUID> {
    List<DiscoverReferenceOptionEntity> findByCategoryAndActiveTrueOrderByDisplayOrderAscDisplayNameAsc(DiscoverReferenceCategory category);
}
