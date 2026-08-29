package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MedicineCatalogueMigrationTest {
    @Test
    void v163ContainsDuplicatePrecheckBeforeUniqueConstraint() throws IOException {
        String migration = readResource("db/migration/V163__medicine_catalogue_identity_uniqueness.sql");
        assertThat(migration).contains("RAISE EXCEPTION");
        assertThat(migration).contains("Duplicate medicine catalogue rows exist");
        assertThat(migration).contains("GROUP BY tenant_id, medicine_name, medicine_type, strength");
        assertThat(migration).contains("ADD CONSTRAINT uq_medicine_catalogue_tenant_name_type_strength");
    }

    private String readResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
