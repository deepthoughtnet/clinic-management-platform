package com.deepthoughtnet.clinic.api.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.help.db.HelpContentEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpContentRepository;
import com.deepthoughtnet.clinic.api.help.db.HelpPageEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpPageRepository;
import com.deepthoughtnet.clinic.api.help.db.HelpSectionEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpSectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class HelpCmsSeederTest {
    @Mock
    private HelpPageRepository pageRepository;

    @Mock
    private HelpSectionRepository sectionRepository;

    @Mock
    private HelpContentRepository contentRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private HelpCmsSeeder seeder;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        seeder = new HelpCmsSeeder(pageRepository, sectionRepository, contentRepository, new ObjectMapper(), jdbcTemplate, transactionManager);
    }

    @Test
    void seedSkipsWhenHelpSchemaIsUnavailable() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(null);

        seeder.seed();

        verify(pageRepository, org.mockito.Mockito.never()).save(any());
        verify(sectionRepository, org.mockito.Mockito.never()).save(any());
        verify(contentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void seedCreatesLaboratoryHelpPageWithDbBackedContent() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn("help_pages");
        when(pageRepository.findByPageKeyIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(pageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sectionRepository.findByPage_IdAndSectionKeyIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(sectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.seed();

        ArgumentCaptor<HelpPageEntity> pageCaptor = ArgumentCaptor.forClass(HelpPageEntity.class);
        verify(pageRepository, atLeastOnce()).save(pageCaptor.capture());
        HelpPageEntity labPage = pageCaptor.getAllValues().stream()
                .filter(page -> "LABORATORY".equals(page.getPageKey()))
                .findFirst()
                .orElseThrow();
        assertThat(labPage.getTitle()).isEqualTo("Laboratory");
        assertThat(labPage.getIcon()).isEqualTo("science");

        ArgumentCaptor<HelpSectionEntity> sectionCaptor = ArgumentCaptor.forClass(HelpSectionEntity.class);
        verify(sectionRepository, atLeastOnce()).save(sectionCaptor.capture());
        List<HelpSectionEntity> reportSections = sectionCaptor.getAllValues().stream()
                .filter(section -> section.getPage() != null && "LABORATORY".equals(section.getPage().getPageKey()))
                .toList();

        assertThat(reportSections).extracting(HelpSectionEntity::getSectionType).containsExactly(
                "DESCRIPTION",
                "WORKFLOW",
                "TAB_GUIDE",
                "VALIDATION_RULES",
                "COMMON_ERRORS",
                "BEST_PRACTICES",
                "FAQ",
                "RELATED_PAGES",
                "TIPS",
                "PERMISSIONS",
                "KNOWN_LIMITATIONS"
        );

        ArgumentCaptor<HelpContentEntity> contentCaptor = ArgumentCaptor.forClass(HelpContentEntity.class);
        verify(contentRepository, atLeastOnce()).save(contentCaptor.capture());
        assertThat(contentCaptor.getAllValues().stream().map(HelpContentEntity::getContentJson))
                .anyMatch(json -> json.contains("lab test catalog") && json.contains("doctor review") && json.contains("report generation"));
    }

    @Test
    void seedCreatesBillingHelpPageWithDbBackedContent() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn("help_pages");
        when(pageRepository.findByPageKeyIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(pageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sectionRepository.findByPage_IdAndSectionKeyIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(sectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.seed();

        ArgumentCaptor<HelpPageEntity> pageCaptor = ArgumentCaptor.forClass(HelpPageEntity.class);
        verify(pageRepository, atLeastOnce()).save(pageCaptor.capture());
        HelpPageEntity billingPage = pageCaptor.getAllValues().stream()
                .filter(page -> "BILLING".equals(page.getPageKey()))
                .findFirst()
                .orElseThrow();
        assertThat(billingPage.getTitle()).isEqualTo("Billing");
        assertThat(billingPage.getIcon()).isEqualTo("receipt_long");

        ArgumentCaptor<HelpSectionEntity> sectionCaptor = ArgumentCaptor.forClass(HelpSectionEntity.class);
        verify(sectionRepository, atLeastOnce()).save(sectionCaptor.capture());
        List<HelpSectionEntity> billingSections = sectionCaptor.getAllValues().stream()
                .filter(section -> section.getPage() != null && "BILLING".equals(section.getPage().getPageKey()))
                .toList();

        assertThat(billingSections).extracting(HelpSectionEntity::getSectionType).containsExactly(
                "DESCRIPTION",
                "WORKFLOW",
                "VALIDATION_RULES",
                "COMMON_ERRORS",
                "BEST_PRACTICES",
                "FAQ",
                "RELATED_PAGES",
                "PERMISSIONS",
                "TIPS",
                "KNOWN_LIMITATIONS"
        );

        ArgumentCaptor<HelpContentEntity> contentCaptor = ArgumentCaptor.forClass(HelpContentEntity.class);
        verify(contentRepository, atLeastOnce()).save(contentCaptor.capture());
        String allContent = contentCaptor.getAllValues().stream()
                .map(HelpContentEntity::getContentJson)
                .map(json -> json == null ? "" : json.toLowerCase())
                .collect(Collectors.joining("\n"));
        assertThat(allContent)
                .contains("search and select patient")
                .contains("payment reference")
                .contains("refund");
    }

    @Test
    void seedCreatesClinicHelpPagesWithDbBackedContent() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn("help_pages");
        when(pageRepository.findByPageKeyIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(pageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sectionRepository.findByPage_IdAndSectionKeyIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(sectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.seed();

        ArgumentCaptor<HelpPageEntity> pageCaptor = ArgumentCaptor.forClass(HelpPageEntity.class);
        verify(pageRepository, atLeastOnce()).save(pageCaptor.capture());
        List<String> pageKeys = pageCaptor.getAllValues().stream().map(HelpPageEntity::getPageKey).toList();
        assertThat(pageKeys).contains(
                "CLINIC_DASHBOARD",
                "DAY_BOARD",
                "NOTIFICATIONS",
                "VACCINATIONS",
                "DOCTOR_AVAILABILITY",
                "CLINIC_PROFILE",
                "USERS",
                "CONSULTATION_WORKSPACE",
                "CONSULTATION_PRESCRIPTION",
                "CONSULTATION_HISTORY",
                "CONSULTATION_INVESTIGATIONS",
                "CONSULTATION_LAB_ORDERS",
                "CONSULTATION_AI_ASSIST"
        );
    }

    @Test
    void seedContinuesWhenOnePageFailsBecauseEachPageUsesItsOwnTransaction() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn("help_pages");
        when(pageRepository.findByPageKeyIgnoreCase(anyString())).thenReturn(Optional.empty());
        AtomicInteger pageSaveCalls = new AtomicInteger();
        when(pageRepository.save(any())).thenAnswer(invocation -> {
            if (pageSaveCalls.incrementAndGet() == 1) {
                throw new RuntimeException("boom");
            }
            return invocation.getArgument(0);
        });
        when(sectionRepository.findByPage_IdAndSectionKeyIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(sectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.seed();

        verify(transactionManager, atLeastOnce()).getTransaction(any(TransactionDefinition.class));
        verify(transactionManager, atLeastOnce()).rollback(any());
        verify(transactionManager, atLeastOnce()).commit(any());
        verify(pageRepository, atLeast(2)).save(any());
    }

    @Test
    void seedIsIdempotentWhenRepeatedAgainstExistingData() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn("help_pages");

        Map<String, HelpPageEntity> pages = new ConcurrentHashMap<>();
        Map<String, HelpSectionEntity> sections = new ConcurrentHashMap<>();
        Map<UUID, List<HelpContentEntity>> contentHistory = new ConcurrentHashMap<>();
        AtomicInteger pageSaveCount = new AtomicInteger();
        AtomicInteger sectionSaveCount = new AtomicInteger();
        AtomicInteger contentSaveCount = new AtomicInteger();

        when(pageRepository.findByPageKeyIgnoreCase(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(pages.get(normalize(invocation.getArgument(0, String.class))))
        );
        when(pageRepository.save(any())).thenAnswer(invocation -> {
            HelpPageEntity page = invocation.getArgument(0);
            pages.put(normalize(page.getPageKey()), page);
            pageSaveCount.incrementAndGet();
            return page;
        });
        when(sectionRepository.findByPage_IdAndSectionKeyIgnoreCase(any(), anyString())).thenAnswer(invocation ->
                Optional.ofNullable(sections.get(sectionKey(invocation.getArgument(0, UUID.class), invocation.getArgument(1, String.class))))
        );
        when(sectionRepository.findByPage_IdOrderByDisplayOrderAsc(any())).thenAnswer(invocation -> sections.values().stream()
                .filter(section -> section.getPage() != null && section.getPage().getId().equals(invocation.getArgument(0, UUID.class)))
                .sorted(Comparator.comparingInt(HelpSectionEntity::getDisplayOrder))
                .toList());
        when(sectionRepository.save(any())).thenAnswer(invocation -> {
            HelpSectionEntity section = invocation.getArgument(0);
            sections.put(sectionKey(section.getPage().getId(), section.getSectionKey()), section);
            sectionSaveCount.incrementAndGet();
            return section;
        });
        when(contentRepository.findBySection_IdOrderByVersionDescCreatedAtDesc(any())).thenAnswer(invocation -> {
            UUID sectionId = invocation.getArgument(0, UUID.class);
            return contentHistory.getOrDefault(sectionId, List.of());
        });
        when(contentRepository.save(any())).thenAnswer(invocation -> {
            HelpContentEntity content = invocation.getArgument(0);
            contentHistory.computeIfAbsent(content.getSection().getId(), key -> new ArrayList<>()).add(0, content);
            contentSaveCount.incrementAndGet();
            return content;
        });

        seeder.seed();
        int firstPageSaves = pageSaveCount.get();
        int firstSectionSaves = sectionSaveCount.get();
        int firstContentSaves = contentSaveCount.get();

        seeder.seed();

        assertThat(pageSaveCount.get()).isEqualTo(firstPageSaves);
        assertThat(sectionSaveCount.get()).isEqualTo(firstSectionSaves);
        assertThat(contentSaveCount.get()).isEqualTo(firstContentSaves);
    }

    private static String normalize(String value) {
        return value == null ? null : value.toUpperCase();
    }

    private static String sectionKey(UUID pageId, String sectionKey) {
        return pageId + ":" + normalize(sectionKey);
    }
}
