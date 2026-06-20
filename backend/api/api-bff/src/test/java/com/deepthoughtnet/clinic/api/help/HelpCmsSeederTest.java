package com.deepthoughtnet.clinic.api.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
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
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HelpCmsSeederTest {
    @Mock
    private HelpPageRepository pageRepository;

    @Mock
    private HelpSectionRepository sectionRepository;

    @Mock
    private HelpContentRepository contentRepository;

    private HelpCmsSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new HelpCmsSeeder(pageRepository, sectionRepository, contentRepository, new ObjectMapper());
    }

    @Test
    void seedCreatesLaboratoryHelpPageWithDbBackedContent() {
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
}
