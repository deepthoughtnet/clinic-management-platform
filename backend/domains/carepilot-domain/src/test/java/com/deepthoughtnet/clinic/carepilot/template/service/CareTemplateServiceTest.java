package com.deepthoughtnet.clinic.carepilot.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.template.db.CareTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CareTemplateRepository;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateType;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CareTemplateSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CareTemplateUpsertCommand;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CareTemplateServiceTest {
    private CareTemplateRepository repository;
    private CareTemplateService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        repository = mock(CareTemplateRepository.class);
        service = new CareTemplateService(repository);
        tenantId = UUID.randomUUID();
    }

    @Test
    void createAndUpdateTemplate() {
        when(repository.searchWithText(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(tenantId, command("A"), UUID.randomUUID());
        assertThat(created.name()).isEqualTo("A");

        CareTemplateEntity existing = CareTemplateEntity.create(
                tenantId, "A", null, TemplateType.GENERAL, TemplateChannel.EMAIL, TemplateCategory.GENERAL,
                null, "body", null, true, false, null
        );
        when(repository.findByTenantIdAndId(tenantId, created.id())).thenReturn(Optional.of(existing));
        when(repository.searchWithText(any(), any(), any(), any(), any(), any())).thenReturn(List.of(existing));

        var updated = service.update(tenantId, created.id(), command("B"), UUID.randomUUID());
        assertThat(updated.name()).isEqualTo("B");
    }

    @Test
    void activateDeactivateAndPreview() {
        CareTemplateEntity existing = CareTemplateEntity.create(
                tenantId, "Reminder", null, TemplateType.REMINDER, TemplateChannel.SMS, TemplateCategory.GENERAL,
                "Hi {{patientName}}", "Body {{clinicName}}", null, true, false, null
        );
        when(repository.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var deactivated = service.deactivate(tenantId, existing.getId(), UUID.randomUUID());
        assertThat(deactivated.active()).isFalse();
        var activated = service.activate(tenantId, existing.getId(), UUID.randomUUID());
        assertThat(activated.active()).isTrue();

        var preview = service.preview(tenantId, existing.getId(), Map.of("patientName", "John", "clinicName", "Sunrise"));
        assertThat(preview.renderedSubject()).contains("John");
        assertThat(preview.renderedBody()).contains("Sunrise");
    }

    @Test
    void systemTemplateCannotBeDeleted() {
        CareTemplateEntity system = CareTemplateEntity.create(
                tenantId, "System", null, TemplateType.GENERAL, TemplateChannel.INTERNAL, TemplateCategory.GENERAL,
                null, "body", null, true, true, null
        );
        when(repository.findByTenantIdAndId(tenantId, system.getId())).thenReturn(Optional.of(system));
        assertThatThrownBy(() -> service.delete(tenantId, system.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("System template");
    }

    @Test
    void listIsTenantScoped() {
        when(repository.findByTenantIdAndSystemTemplateTrue(tenantId)).thenReturn(List.of());
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.searchNoText(any(), any(), any(), any(), any())).thenReturn(List.of());
        assertThat(service.list(tenantId, new CareTemplateSearchCriteria(null, null, null, null, null))).isEmpty();
    }

    @Test
    void listWithoutSearchUsesNoTextQuery() {
        when(repository.findByTenantIdAndSystemTemplateTrue(tenantId)).thenReturn(List.of());
        when(repository.searchNoText(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.list(tenantId, new CareTemplateSearchCriteria(null, null, null, null, null));

        verify(repository, times(1)).searchNoText(eq(tenantId), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void blankSearchBehavesAsNoSearch() {
        when(repository.findByTenantIdAndSystemTemplateTrue(tenantId)).thenReturn(List.of());
        when(repository.searchNoText(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.list(tenantId, new CareTemplateSearchCriteria(null, null, null, null, "   "));

        verify(repository, times(1)).searchNoText(eq(tenantId), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void listWithSearchUsesPatternQuery() {
        when(repository.findByTenantIdAndSystemTemplateTrue(tenantId)).thenReturn(List.of());
        when(repository.searchWithText(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.list(tenantId, new CareTemplateSearchCriteria(null, null, null, null, " Reminder "));

        verify(repository, times(1)).searchWithText(eq(tenantId), eq(null), eq(null), eq(null), eq(null), eq("%reminder%"));
    }

    private CareTemplateUpsertCommand command(String name) {
        return new CareTemplateUpsertCommand(
                name,
                null,
                TemplateType.GENERAL,
                TemplateChannel.EMAIL,
                TemplateCategory.GENERAL,
                "Subject {{patientName}}",
                "Body {{clinicName}}",
                null,
                true
        );
    }
}
