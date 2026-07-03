package com.deepthoughtnet.clinic.api.lab;

import com.deepthoughtnet.clinic.api.lab.db.LabCategorySettingEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabCategorySettingRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterRepository;
import com.deepthoughtnet.clinic.api.lab.dto.LabCategoryConfigDtos.LabCategoryConfigResponse;
import com.deepthoughtnet.clinic.api.lab.dto.LabCategoryConfigDtos.LabCategoryConfigUpdateRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabTestCatalogueConfigDtos.LabTestCatalogueConfigResponse;
import com.deepthoughtnet.clinic.api.lab.dto.LabTestCatalogueConfigDtos.LabTestCatalogueConfigUpdateRequest;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LabCatalogueConfigService {
    private final LabCategorySettingRepository labCategorySettingRepository;
    private final LabTestMasterRepository labTestMasterRepository;

    public LabCatalogueConfigService(LabCategorySettingRepository labCategorySettingRepository, LabTestMasterRepository labTestMasterRepository) {
        this.labCategorySettingRepository = labCategorySettingRepository;
        this.labTestMasterRepository = labTestMasterRepository;
    }

    @Transactional(readOnly = true)
    public List<LabCategoryConfigResponse> listCategories(UUID tenantId) {
        Map<String, LabCategorySettingEntity> byCode = labCategorySettingRepository.findByTenantIdOrderByDisplayOrderAscCategoryCodeAsc(tenantId).stream()
                .collect(Collectors.toMap(row -> row.getCategoryCode().toUpperCase(), Function.identity(), (a, b) -> a));
        return LabCategoryCatalog.CATEGORY_CODES.stream()
                .map(code -> {
                    LabCategorySettingEntity row = byCode.get(code);
                    return new LabCategoryConfigResponse(
                            code,
                            row == null ? LabCategoryCatalog.displayName(code) : row.getDisplayName(),
                            row == null || row.isActive(),
                            row == null ? LabCategoryCatalog.CATEGORY_CODES.indexOf(code) + 1 : row.getDisplayOrder()
                    );
                })
                .sorted(categoryComparator())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listActiveCategoryCodes(UUID tenantId) {
        return listCategories(tenantId).stream()
                .filter(LabCategoryConfigResponse::active)
                .map(LabCategoryConfigResponse::categoryCode)
                .toList();
    }

    @Transactional
    public LabCategoryConfigResponse updateCategory(UUID tenantId, String code, LabCategoryConfigUpdateRequest request, UUID actorAppUserId) {
        String normalizedCode = LabCategoryCatalog.normalize(code);
        LabCategorySettingEntity entity = labCategorySettingRepository.findByTenantIdAndCategoryCodeIgnoreCase(tenantId, normalizedCode)
                .orElseGet(() -> LabCategorySettingEntity.create(tenantId, normalizedCode, LabCategoryCatalog.displayName(normalizedCode), true, LabCategoryCatalog.CATEGORY_CODES.indexOf(normalizedCode) + 1));
        String displayName = StringUtils.hasText(request.displayName()) ? request.displayName().trim() : LabCategoryCatalog.displayName(normalizedCode);
        entity.update(displayName, request.active() == null || request.active(), request.displayOrder());
        LabCategorySettingEntity saved = labCategorySettingRepository.save(entity);
        return new LabCategoryConfigResponse(saved.getCategoryCode(), saved.getDisplayName(), saved.isActive(), saved.getDisplayOrder());
    }

    @Transactional(readOnly = true)
    public List<LabTestCatalogueConfigResponse> listTests(UUID tenantId) {
        Map<String, Boolean> categoryActive = listCategories(tenantId).stream()
                .collect(Collectors.toMap(LabCategoryConfigResponse::categoryCode, LabCategoryConfigResponse::active, (a, b) -> a));
        return labTestMasterRepository.findByTenantIdOrderByTestNameAsc(tenantId).stream()
                .map(row -> toResponse(row, categoryActive))
                .sorted(Comparator
                        .comparing((LabTestCatalogueConfigResponse row) -> row.displayOrder() == null ? Integer.MAX_VALUE : row.displayOrder())
                        .thenComparing(LabTestCatalogueConfigResponse::testName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public LabTestCatalogueConfigResponse updateTest(UUID tenantId, UUID id, LabTestCatalogueConfigUpdateRequest request, UUID actorAppUserId) {
        LabTestMasterEntity entity = labTestMasterRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Lab test not found"));
        BigDecimal priceOverride = request.tenantPriceOverride();
        String tatOverride = normalizeNullable(request.tenantTatOverride());
        boolean enabled = request.enabled() == null || request.enabled();
        boolean active = request.active() == null || request.active();
        entity.updateCatalogueConfig(enabled, priceOverride, tatOverride, request.displayOrder(), active);
        LabTestMasterEntity saved = labTestMasterRepository.save(entity);
        return toResponse(saved, currentCategoryActiveMap(tenantId));
    }

    @Transactional(readOnly = true)
    public boolean isCategoryActive(UUID tenantId, String category) {
        String normalized = LabCategoryCatalog.normalize(category);
        return listCategories(tenantId).stream()
                .filter(row -> row.categoryCode().equalsIgnoreCase(normalized))
                .findFirst()
                .map(LabCategoryConfigResponse::active)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public List<LabTestMasterEntity> listAvailableTests(UUID tenantId) {
        Map<String, Boolean> categoryActive = currentCategoryActiveMap(tenantId);
        return labTestMasterRepository.findByTenantIdOrderByTestNameAsc(tenantId).stream()
                .filter(row -> row.isActive())
                .filter(row -> row.isEnabled())
                .filter(row -> categoryActive.getOrDefault(LabCategoryCatalog.normalize(row.getCategory()), true))
                .toList();
    }

    private Map<String, Boolean> currentCategoryActiveMap(UUID tenantId) {
        return listCategories(tenantId).stream()
                .collect(Collectors.toMap(LabCategoryConfigResponse::categoryCode, LabCategoryConfigResponse::active, (a, b) -> a));
    }

    private LabTestCatalogueConfigResponse toResponse(LabTestMasterEntity row, Map<String, Boolean> categoryActive) {
        String normalizedCategory = LabCategoryCatalog.normalize(row.getCategory());
        return new LabTestCatalogueConfigResponse(
                row.getId() == null ? null : row.getId().toString(),
                row.getTenantId() == null ? null : row.getTenantId().toString(),
                row.getTestCode(),
                row.getTestName(),
                normalizedCategory,
                row.isEnabled(),
                row.isActive(),
                row.getPrice(),
                row.getTurnaroundTime(),
                row.getTenantPriceOverride(),
                row.getTenantTatOverride(),
                row.getDisplayOrder()
        );
    }

    private Comparator<LabCategoryConfigResponse> categoryComparator() {
        return Comparator
                .comparing((LabCategoryConfigResponse row) -> row.displayOrder() == null ? Integer.MAX_VALUE : row.displayOrder())
                .thenComparing(LabCategoryConfigResponse::displayName, String.CASE_INSENSITIVE_ORDER);
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
