package com.deepthoughtnet.clinic.api.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageRecord;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageSummary;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpSearchResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HelpControllerSecurityTest {
    @Mock
    private HelpCmsService helpCmsService;

    private HelpController helpController;
    private PlatformHelpController platformHelpController;

    @BeforeEach
    void setUp() {
        helpController = new HelpController(helpCmsService);
        platformHelpController = new PlatformHelpController(helpCmsService);
    }

    @Test
    void helpControllerDelegatesPageLookup() {
        HelpPageRecord page = samplePage();
        when(helpCmsService.getPublicPage("PHARMACY_DISPENSING", "en")).thenReturn(page);

        HelpPageRecord result = helpController.getPage("PHARMACY_DISPENSING", "en");

        assertThat(result.pageKey()).isEqualTo("PHARMACY_DISPENSING");
        verify(helpCmsService).getPublicPage("PHARMACY_DISPENSING", "en");
    }

    @Test
    void helpControllerDelegatesSearch() {
        HelpSearchResult resultItem = sampleSearchResult();
        when(helpCmsService.search("batch", "en")).thenReturn(List.of(resultItem));

        List<HelpSearchResult> result = helpController.search("batch", "en");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pageKey()).isEqualTo("PHARMACY_DISPENSING");
        verify(helpCmsService).search("batch", "en");
    }

    @Test
    void platformHelpControllerDelegatesPageLookup() {
        HelpPageRecord page = samplePage();
        when(helpCmsService.getAdminPage("PHARMACY_DISPENSING")).thenReturn(page);

        HelpPageRecord result = platformHelpController.getPage("PHARMACY_DISPENSING");

        assertThat(result.pageKey()).isEqualTo("PHARMACY_DISPENSING");
        verify(helpCmsService).getAdminPage("PHARMACY_DISPENSING");
    }

    @Test
    void platformHelpControllerDelegatesListPages() {
        HelpPageSummary summary = sampleSummary();
        when(helpCmsService.listPages()).thenReturn(List.of(summary));

        List<HelpPageSummary> result = platformHelpController.listPages();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pageKey()).isEqualTo("PHARMACY_DISPENSING");
        verify(helpCmsService).listPages();
    }

    private static HelpPageRecord samplePage() {
        return new HelpPageRecord(
                UUID.randomUUID(),
                "PHARMACY",
                "PHARMACY_DISPENSING",
                "Dispensing",
                "pill",
                "PUBLISHED",
                1,
                true,
                UUID.randomUUID(),
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-06-19T00:00:00Z"),
                OffsetDateTime.parse("2026-06-19T00:00:00Z"),
                List.of(1),
                List.of()
        );
    }

    private static HelpPageSummary sampleSummary() {
        return new HelpPageSummary(
                UUID.randomUUID(),
                "PHARMACY",
                "PHARMACY_DISPENSING",
                "Dispensing",
                "pill",
                "PUBLISHED",
                1,
                true,
                OffsetDateTime.parse("2026-06-19T00:00:00Z"),
                OffsetDateTime.parse("2026-06-19T00:00:00Z")
        );
    }

    private static HelpSearchResult sampleSearchResult() {
        return new HelpSearchResult(
                "PHARMACY_DISPENSING",
                "Dispensing",
                "PHARMACY",
                "DESCRIPTION",
                "DESCRIPTION",
                "Dispensing manages medicine issue workflows.",
                "en"
        );
    }
}
