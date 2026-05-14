package com.deepthoughtnet.clinic.carepilot.template.db;

import com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CareTemplateRepository extends JpaRepository<CareTemplateEntity, UUID> {
    Optional<CareTemplateEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndTemplateTypeAndNameIgnoreCase(UUID tenantId, TemplateType templateType, String name);

    @Query("""
            select t
            from CareTemplateEntity t
            where t.tenantId = :tenantId
              and (:templateType is null or t.templateType = :templateType)
              and (:channel is null or t.channel = :channel)
              and (:category is null or t.category = :category)
              and (:active is null or t.active = :active)
            order by t.updatedAt desc, t.createdAt desc
            """)
    List<CareTemplateEntity> searchNoText(
            UUID tenantId,
            TemplateType templateType,
            TemplateChannel channel,
            TemplateCategory category,
            Boolean active
    );

    @Query("""
            select t
            from CareTemplateEntity t
            where t.tenantId = :tenantId
              and (:templateType is null or t.templateType = :templateType)
              and (:channel is null or t.channel = :channel)
              and (:category is null or t.category = :category)
              and (:active is null or t.active = :active)
              and (lower(t.name) like :searchPattern or lower(coalesce(t.description, '')) like :searchPattern)
            order by t.updatedAt desc, t.createdAt desc
            """)
    List<CareTemplateEntity> searchWithText(
            UUID tenantId,
            TemplateType templateType,
            TemplateChannel channel,
            TemplateCategory category,
            Boolean active,
            String searchPattern
    );

    List<CareTemplateEntity> findByTenantIdAndSystemTemplateTrue(UUID tenantId);
}
