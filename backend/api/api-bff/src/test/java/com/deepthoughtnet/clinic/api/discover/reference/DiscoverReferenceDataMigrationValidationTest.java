package com.deepthoughtnet.clinic.api.discover.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DiscoverReferenceDataMigrationValidationTest {
    private static final Pattern INSERT_ROW = Pattern.compile(
            "\\('([^']+)',\\s*'([A-Z_]+)',\\s*'([^']+)',\\s*'([^']+)',\\s*'([^']+)',\\s*(\\d+),\\s*(TRUE|FALSE)\\)"
    );

    @Test
    void v132UuidLiteralsAreValidAndUnique() throws Exception {
        String migration = readMigration("db/migration/V132__discover_reference_data_catalog.sql");
        Matcher matcher = INSERT_ROW.matcher(migration);

        Set<UUID> ids = new HashSet<>();
        Set<String> categoryCodes = new HashSet<>();
        int rows = 0;

        while (matcher.find()) {
            rows++;
            UUID id = UUID.fromString(matcher.group(1));
            assertThat(id).isNotNull();
            assertThat(ids.add(id)).as("duplicate id %s", id).isTrue();

            String category = matcher.group(2);
            String code = matcher.group(3);
            assertThat(categoryCodes.add(category + "::" + code))
                    .as("duplicate category/code %s::%s", category, code)
                    .isTrue();
        }

        assertThat(rows).isEqualTo(103);
        assertThat(ids).hasSize(103);
        assertThat(categoryCodes).hasSize(103);
    }

    @Test
    void v132ContainsValidMedicalCouncilParentIdentifiers() throws Exception {
        String migration = readMigration("db/migration/V132__discover_reference_data_catalog.sql");
        assertThat(migration).contains("5a39a56c-3f45-4c7f-86c4-3fd5f8b60001");
        assertThat(migration).contains("5a39a56c-3f45-4c7f-86c4-3fd5f8b60010");
    }

    private static String readMigration(String resourcePath) throws IOException {
        try (InputStream inputStream = DiscoverReferenceDataMigrationValidationTest.class
                .getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertThat(inputStream).as("migration resource %s", resourcePath).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
